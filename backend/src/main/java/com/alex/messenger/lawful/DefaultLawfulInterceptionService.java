package com.alex.messenger.lawful;

import com.alex.messenger.attachment.AttachmentService;
import com.alex.messenger.chat.ChatMemberEntity;
import com.alex.messenger.chat.ChatMemberRepository;
import com.alex.messenger.crypto.ChatEncryptionService;
import com.alex.messenger.message.MessageContentCodec;
import com.alex.messenger.message.MessageTextContent;
import com.alex.messenger.message.MessageEntity;
import com.alex.messenger.message.MessageRepository;
import com.alex.messenger.message.MessageThreadRepository;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.poll.PollService;
import com.alex.messenger.sticker.StickerService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultLawfulInterceptionService implements LawfulInterceptionService {

    private final ChatMemberRepository chatMemberRepository;
    private final MessageRepository messageRepository;
    private final ChatEncryptionService chatEncryptionService;
    private final MessageContentCodec messageContentCodec;
    private final AttachmentService attachmentService;
    private final PollService pollService;
    private final StickerService stickerService;
    private final MessageThreadRepository messageThreadRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> exportDecryptedMessages(
            UUID userId,
            Instant fromInclusive,
            Instant toExclusive
    ) {
        Instant from = fromInclusive != null ? fromInclusive : Instant.EPOCH;
        Instant to = toExclusive != null ? toExclusive : Instant.parse("9999-12-31T23:59:59Z");

        List<UUID> chatIds = chatMemberRepository.findAllByIdUserId(userId).stream()
                .map(ChatMemberEntity::getId)
                .map(id -> id.getChatId())
                .distinct()
                .toList();

        return chatIds.stream()
                .flatMap(chatId -> messageRepository.findAllByChatIdWithinRange(chatId, from, to).stream())
                .filter(message -> isWithinRange(message, from, to))
                .map(message -> toDecryptedResponse(userId, message))
                .sorted(Comparator.comparing(ChatMessageResponse::createdAt))
                .toList();
    }

    private boolean isWithinRange(MessageEntity message, Instant fromInclusive, Instant toExclusive) {
        Instant createdAt = message.getCreatedAt();
        return !createdAt.isBefore(fromInclusive) && createdAt.isBefore(toExclusive);
    }

    private ChatMessageResponse toDecryptedResponse(UUID userId, MessageEntity message) {
        MessageTextContent content = message.getDeletedAt() != null
                ? new MessageTextContent("", List.of())
                : messageContentCodec.decode(chatEncryptionService.decrypt(
                        message.getKey().getChatId(),
                        message.getCiphertext(),
                        message.getNonce(),
                        message.getKeyVersion()
                ));

        List<com.alex.messenger.message.dto.MessageAttachmentResponse> attachments =
                attachmentService.getResponses(message.getAttachmentIds());
        return new ChatMessageResponse(
                message.getKey().getChatId(),
                message.getKey().getMessageId(),
                null,
                message.getSenderId(),
                null,
                null,
                null,
                false,
                message.getRecipientId(),
                message.getViaBotUserId(),
                message.getTopicId(),
                message.getThreadRootMessageId(),
                message.getDiscussionChatId(),
                message.getDiscussionRootMessageId(),
                message.getDiscussionRootMessageId() != null
                        ? Math.max(0, messageThreadRepository.findAllByThreadRootMessageId(message.getDiscussionRootMessageId()).size() - 1)
                        : 0,
                content.text(),
                content.entities(),
                resolveResponseMessageType(content, message.getPollId(), message.getStickerId(), attachments),
                content.caption(),
                content.silent(),
                content.location(),
                content.contactCard(),
                content.serviceMessage(),
                message.getCreatedAt(),
                message.getReplyToMessageId(),
                message.getForwardedFromChatId(),
                message.getForwardedFromMessageId(),
                pollService.getPollResponse(message.getPollId(), userId),
                stickerService.getStickerResponse(message.getStickerId()),
                attachments,
                java.util.List.of(),
                message.getDeliveryStatus(),
                message.getDeliveredAt(),
                message.getReadAt(),
                message.getExpiresAt(),
                message.getEditedAt(),
                message.getDeletedAt()
        );
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
