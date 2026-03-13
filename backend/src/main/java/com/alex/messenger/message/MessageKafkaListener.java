package com.alex.messenger.message;

import com.alex.messenger.attachment.AttachmentService;
import com.alex.messenger.chat.ChatService;
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

@Component
@RequiredArgsConstructor
public class MessageKafkaListener {

    private final ChatEncryptionService chatEncryptionService;
    private final MessageContentCodec messageContentCodec;
    private final MessageReactionService messageReactionService;
    private final AttachmentService attachmentService;
    private final ChatService chatService;
    private final MessageDeliveryService messageDeliveryService;
    private final MessagePushNotificationService messagePushNotificationService;
    private final PollService pollService;
    private final StickerService stickerService;
    private final MessageThreadRepository messageThreadRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @KafkaListener(topics = "${alex.kafka.chat-messages-topic}", containerFactory = "kafkaListenerContainerFactory")
    public void listen(MessageEvent event) {
        MessageLookupEntity current = "SENT".equals(event.deliveryStatus())
                ? messageDeliveryService.markDelivered(event.messageId())
                : null;
        MessageTextContent content = event.deletedAt() != null
                ? new MessageTextContent("", List.of())
                : messageContentCodec.decode(chatEncryptionService.decrypt(
                        event.chatId(),
                        event.ciphertext(),
                        event.nonce(),
                        event.keyVersion()
                ));

        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(event.senderId());
        recipients.addAll(event.recipientIds());
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
                    event.threadRootMessageId(),
                    event.discussionChatId(),
                    event.discussionRootMessageId(),
                    event.discussionRootMessageId() != null
                            ? Math.max(0, messageThreadRepository.findAllByThreadRootMessageId(event.discussionRootMessageId()).size() - 1)
                            : 0,
                    content.text(),
                    content.entities(),
                    resolveResponseMessageType(content, event.pollId(), event.stickerId(), rawAttachments),
                    content.caption(),
                    content.silent(),
                    content.location(),
                    content.contactCard(),
                    content.serviceMessage(),
                    event.createdAt(),
                    event.replyToMessageId(),
                    event.forwardedFromChatId(),
                    event.forwardedFromMessageId(),
                    pollService.getPollResponse(event.pollId(), userId),
                    stickerService.getStickerResponse(event.stickerId()),
                    attachments,
                    messageReactionService.getSummaries(event.messageId()),
                    current != null ? current.getDeliveryStatus() : event.deliveryStatus(),
                    current != null ? current.getDeliveredAt() : event.deliveredAt(),
                    current != null ? current.getReadAt() : event.readAt(),
                    event.expiresAt(),
                    event.editedAt(),
                    event.deletedAt()
            );
            simpMessagingTemplate.convertAndSendToUser(userId.toString(), "/queue/messages", response);
        }

        messagePushNotificationService.notifyNewMessage(event, content, rawAttachments);
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
}
