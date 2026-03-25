package com.alex.messenger.message;

import com.alex.messenger.attachment.AttachmentService;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.forum.ForumTopicService;
import com.alex.messenger.crypto.ChatEncryptionService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.notification.MessagePushNotificationService;
import com.alex.messenger.poll.PollService;
import com.alex.messenger.sticker.StickerService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class MessageKafkaListener {

    private final ChatEncryptionService chatEncryptionService;
    private final MessageContentCodec messageContentCodec;
    private final MessageReactionService messageReactionService;
    private final AttachmentService attachmentService;
    private final ChatService chatService;
    private final MessagePushNotificationService messagePushNotificationService;
    private final PollService pollService;
    private final StickerService stickerService;
    private final MessageLookupRepository messageLookupRepository;
    private final MessageThreadRepository messageThreadRepository;
    private final ForumTopicService forumTopicService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @KafkaListener(topics = "${alex.kafka.chat-messages-topic}", containerFactory = "kafkaListenerContainerFactory")
    public void listen(MessageEvent event) {
        MessageTextContent content = event.deletedAt() != null
                ? new MessageTextContent("", List.of())
                : messageContentCodec.decode(chatEncryptionService.decrypt(
                        event.chatId(),
                        event.ciphertext(),
                        event.nonce(),
                        event.keyVersion()
                ));

        Set<UUID> recipients = new LinkedHashSet<>();
        if (canDeliverToUser(event.senderId(), event.chatId(), event.topicId())) {
            recipients.add(event.senderId());
        }
        event.recipientIds().stream()
                .filter(userId -> canDeliverToUser(userId, event.chatId(), event.topicId()))
                .forEach(recipients::add);
        List<com.alex.messenger.message.dto.MessageAttachmentResponse> rawAttachments =
                attachmentService.getResponses(event.attachmentIds());

        for (UUID userId : recipients) {
            List<com.alex.messenger.message.dto.MessageAttachmentResponse> attachments =
                    attachmentService.getResponses(userId, event.attachmentIds());
            ChatService.MessageAuthorView author = chatService.resolveMessageAuthor(
                    userId,
                    event.chatId(),
                    event.senderId()
            );
            VisibleMessageReferences references = resolveVisibleReferences(userId, event);
            ChatMessageResponse response = new ChatMessageResponse(
                    event.chatId(),
                    event.messageId(),
                    userId.equals(event.senderId()) ? event.clientMessageId() : null,
                    author.senderId(),
                    author.displayName(),
                    author.photoUrl(),
                    author.photoAccessExpiresAt(),
                    author.anonymous(),
                    event.recipientIds().size() == 1 ? event.recipientIds().get(0) : null,
                    event.viaBotUserId(),
                    event.topicId(),
                    references.threadRootMessageId(),
                    references.discussionChatId(),
                    references.discussionRootMessageId(),
                    countComments(references.discussionRootMessageId()),
                    content.text(),
                    content.entities(),
                    resolveResponseMessageType(content, event.pollId(), event.stickerId(), rawAttachments),
                    content.caption(),
                    content.silent(),
                    content.location(),
                    resolveLiveLocationPayload(content),
                    content.contactCard(),
                    content.serviceMessage(),
                    event.createdAt(),
                    references.replyToMessageId(),
                    references.forwardedFromChatId(),
                    references.forwardedFromMessageId(),
                    pollService.getPollResponse(event.pollId(), userId),
                    stickerService.getStickerResponse(event.stickerId()),
                    attachments,
                    messageReactionService.getSummaries(event.messageId()),
                    event.deliveryStatus(),
                    event.deliveredAt(),
                    event.readAt(),
                    event.expiresAt(),
                    event.editedAt(),
                    event.deletedAt()
            );
            simpMessagingTemplate.convertAndSendToUser(userId.toString(), "/queue/messages", response);
        }

        messagePushNotificationService.notifyNewMessage(event, content, rawAttachments);
    }

    private com.alex.messenger.message.dto.MessageLiveLocationPayload resolveLiveLocationPayload(MessageTextContent content) {
        if (content == null || content.liveLocation() == null) {
            return null;
        }
        com.alex.messenger.message.dto.MessageLiveLocationPayload liveLocation = content.liveLocation();
        boolean active = liveLocation.stoppedAt() == null
                && liveLocation.expiresAt() != null
                && liveLocation.expiresAt().isAfter(java.time.Instant.now());
        return new com.alex.messenger.message.dto.MessageLiveLocationPayload(
                liveLocation.latitude(),
                liveLocation.longitude(),
                liveLocation.title(),
                liveLocation.address(),
                liveLocation.livePeriodSeconds(),
                liveLocation.expiresAt(),
                liveLocation.lastUpdatedAt(),
                liveLocation.stoppedAt(),
                active
        );
    }

    private VisibleMessageReferences resolveVisibleReferences(UUID requesterId, MessageEvent event) {
        UUID visibleReplyToMessageId = resolveVisibleMessageReferenceId(requesterId, event.replyToMessageId());
        UUID visibleThreadRootMessageId = resolveVisibleMessageReferenceId(requesterId, event.threadRootMessageId());
        UUID visibleDiscussionRootMessageId = resolveVisibleMessageReferenceId(requesterId, event.discussionRootMessageId());
        UUID visibleDiscussionChatId = visibleDiscussionRootMessageId != null
                ? event.discussionChatId()
                : resolveVisibleChatReferenceId(requesterId, event.discussionChatId());
        UUID visibleForwardedFromMessageId = resolveVisibleForwardedMessageReferenceId(
                requesterId,
                event.forwardedFromChatId(),
                event.forwardedFromMessageId()
        );
        UUID visibleForwardedFromChatId = visibleForwardedFromMessageId != null
                ? event.forwardedFromChatId()
                : resolveVisibleChatReferenceId(requesterId, event.forwardedFromChatId());
        return new VisibleMessageReferences(
                visibleReplyToMessageId,
                visibleThreadRootMessageId,
                visibleDiscussionChatId,
                visibleDiscussionRootMessageId,
                visibleForwardedFromChatId,
                visibleForwardedFromMessageId
        );
    }

    private boolean canDeliverToUser(UUID userId, UUID chatId, UUID topicId) {
        if (userId == null) {
            return false;
        }
        try {
            ChatEntity chat = chatService.getOwnedChat(userId, chatId);
            ensureMessageVisibleToRequester(chat, userId, topicId);
            return true;
        } catch (ResponseStatusException exception) {
            return false;
        }
    }

    private UUID resolveVisibleMessageReferenceId(UUID requesterId, UUID messageId) {
        if (messageId == null) {
            return null;
        }
        MessageLookupEntity reference = messageLookupRepository.findById(messageId).orElse(null);
        if (reference == null || reference.getDeletedAt() != null) {
            return null;
        }
        try {
            ChatEntity chat = chatService.getOwnedChat(requesterId, reference.getChatId());
            ensureMessageVisibleToRequester(chat, requesterId, reference.getTopicId());
            return messageId;
        } catch (ResponseStatusException exception) {
            return null;
        }
    }

    private UUID resolveVisibleForwardedMessageReferenceId(UUID requesterId, UUID forwardedFromChatId, UUID forwardedFromMessageId) {
        if (forwardedFromMessageId == null) {
            return null;
        }
        MessageLookupEntity reference = messageLookupRepository.findById(forwardedFromMessageId).orElse(null);
        if (reference == null || reference.getDeletedAt() != null) {
            return null;
        }
        if (forwardedFromChatId != null && !forwardedFromChatId.equals(reference.getChatId())) {
            return null;
        }
        try {
            ChatEntity chat = chatService.getOwnedChat(requesterId, reference.getChatId());
            ensureMessageVisibleToRequester(chat, requesterId, reference.getTopicId());
            return forwardedFromMessageId;
        } catch (ResponseStatusException exception) {
            return null;
        }
    }

    private UUID resolveVisibleChatReferenceId(UUID requesterId, UUID chatId) {
        if (chatId == null) {
            return null;
        }
        try {
            chatService.getOwnedChat(requesterId, chatId);
            return chatId;
        } catch (ResponseStatusException exception) {
            return null;
        }
    }

    private void ensureMessageVisibleToRequester(ChatEntity chat, UUID requesterId, UUID topicId) {
        if (chat == null || topicId == null || !Boolean.TRUE.equals(chat.getForumEnabled())) {
            return;
        }
        forumTopicService.resolveTopicForRead(chat, requesterId, topicId);
    }

    private int countComments(UUID discussionRootMessageId) {
        if (discussionRootMessageId == null) {
            return 0;
        }
        return (int) messageThreadRepository.findAllByThreadRootMessageId(discussionRootMessageId).stream()
                .filter(message -> !discussionRootMessageId.equals(message.getKey().getMessageId()))
                .filter(message -> message.getDeletedAt() == null)
                .count();
    }

    private String resolveResponseMessageType(
            MessageTextContent content,
            UUID pollId,
            UUID stickerId,
            List<com.alex.messenger.message.dto.MessageAttachmentResponse> attachments
    ) {
        if (content.messageType() != null && !content.messageType().isBlank()) {
            return content.messageType();
        }
        if (pollId != null) {
            return "POLL";
        }
        if (stickerId != null) {
            return "STICKER";
        }
        if (attachments != null && !attachments.isEmpty()) {
            if (attachments.size() > 1) {
                return "ALBUM";
            }
            return attachments.get(0).kind();
        }
        return "TEXT";
    }

    private record VisibleMessageReferences(
            UUID replyToMessageId,
            UUID threadRootMessageId,
            UUID discussionChatId,
            UUID discussionRootMessageId,
            UUID forwardedFromChatId,
            UUID forwardedFromMessageId
    ) {
    }
}
