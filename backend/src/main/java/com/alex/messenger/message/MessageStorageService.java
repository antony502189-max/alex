package com.alex.messenger.message;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageStorageService {

    private final MessageLookupRepository messageLookupRepository;
    private final MessageRepository messageRepository;
    private final MessageTopicRepository messageTopicRepository;
    private final MessageThreadRepository messageThreadRepository;

    public void save(MessageLookupEntity lookup) {
        messageLookupRepository.save(lookup);
        messageRepository.save(toMessageEntity(lookup));
        if (lookup.getTopicId() != null) {
            messageTopicRepository.save(toTopicEntity(lookup));
        }
        if (lookup.getThreadRootMessageId() != null) {
            messageThreadRepository.save(toThreadEntity(lookup));
        }
    }

    private MessageEntity toMessageEntity(MessageLookupEntity lookup) {
        MessageEntity entity = new MessageEntity();
        entity.setKey(new MessagePrimaryKey(lookup.getChatId(), lookup.getMessageId()));
        entity.setCreatedAt(lookup.getCreatedAt());
        entity.setSenderId(lookup.getSenderId());
        entity.setRecipientId(lookup.getRecipientId());
        entity.setViaBotUserId(lookup.getViaBotUserId());
        entity.setTopicId(lookup.getTopicId());
        entity.setThreadRootMessageId(lookup.getThreadRootMessageId());
        entity.setDiscussionChatId(lookup.getDiscussionChatId());
        entity.setDiscussionRootMessageId(lookup.getDiscussionRootMessageId());
        entity.setCiphertext(lookup.getCiphertext());
        entity.setNonce(lookup.getNonce());
        entity.setKeyVersion(lookup.getKeyVersion());
        entity.setReplyToMessageId(lookup.getReplyToMessageId());
        entity.setForwardedFromChatId(lookup.getForwardedFromChatId());
        entity.setForwardedFromMessageId(lookup.getForwardedFromMessageId());
        entity.setPollId(lookup.getPollId());
        entity.setStickerId(lookup.getStickerId());
        entity.setAttachmentIds(lookup.getAttachmentIds());
        entity.setDeliveryStatus(lookup.getDeliveryStatus());
        entity.setDeliveredAt(lookup.getDeliveredAt());
        entity.setReadAt(lookup.getReadAt());
        entity.setExpiresAt(lookup.getExpiresAt());
        entity.setEditedAt(lookup.getEditedAt());
        entity.setDeletedAt(lookup.getDeletedAt());
        return entity;
    }

    private MessageTopicEntity toTopicEntity(MessageLookupEntity lookup) {
        MessageTopicEntity entity = new MessageTopicEntity();
        entity.setKey(new MessageTopicPrimaryKey(lookup.getTopicId(), lookup.getMessageId()));
        entity.setChatId(lookup.getChatId());
        entity.setCreatedAt(lookup.getCreatedAt());
        entity.setSenderId(lookup.getSenderId());
        entity.setRecipientId(lookup.getRecipientId());
        entity.setViaBotUserId(lookup.getViaBotUserId());
        entity.setThreadRootMessageId(lookup.getThreadRootMessageId());
        entity.setDiscussionChatId(lookup.getDiscussionChatId());
        entity.setDiscussionRootMessageId(lookup.getDiscussionRootMessageId());
        entity.setCiphertext(lookup.getCiphertext());
        entity.setNonce(lookup.getNonce());
        entity.setKeyVersion(lookup.getKeyVersion());
        entity.setReplyToMessageId(lookup.getReplyToMessageId());
        entity.setForwardedFromChatId(lookup.getForwardedFromChatId());
        entity.setForwardedFromMessageId(lookup.getForwardedFromMessageId());
        entity.setPollId(lookup.getPollId());
        entity.setStickerId(lookup.getStickerId());
        entity.setAttachmentIds(lookup.getAttachmentIds());
        entity.setDeliveryStatus(lookup.getDeliveryStatus());
        entity.setDeliveredAt(lookup.getDeliveredAt());
        entity.setReadAt(lookup.getReadAt());
        entity.setExpiresAt(lookup.getExpiresAt());
        entity.setEditedAt(lookup.getEditedAt());
        entity.setDeletedAt(lookup.getDeletedAt());
        return entity;
    }

    private MessageThreadEntity toThreadEntity(MessageLookupEntity lookup) {
        MessageThreadEntity entity = new MessageThreadEntity();
        entity.setKey(new MessageThreadPrimaryKey(lookup.getThreadRootMessageId(), lookup.getMessageId()));
        entity.setChatId(lookup.getChatId());
        entity.setCreatedAt(lookup.getCreatedAt());
        entity.setSenderId(lookup.getSenderId());
        entity.setRecipientId(lookup.getRecipientId());
        entity.setViaBotUserId(lookup.getViaBotUserId());
        entity.setTopicId(lookup.getTopicId());
        entity.setDiscussionChatId(lookup.getDiscussionChatId());
        entity.setDiscussionRootMessageId(lookup.getDiscussionRootMessageId());
        entity.setCiphertext(lookup.getCiphertext());
        entity.setNonce(lookup.getNonce());
        entity.setKeyVersion(lookup.getKeyVersion());
        entity.setReplyToMessageId(lookup.getReplyToMessageId());
        entity.setForwardedFromChatId(lookup.getForwardedFromChatId());
        entity.setForwardedFromMessageId(lookup.getForwardedFromMessageId());
        entity.setPollId(lookup.getPollId());
        entity.setStickerId(lookup.getStickerId());
        entity.setAttachmentIds(lookup.getAttachmentIds());
        entity.setDeliveryStatus(lookup.getDeliveryStatus());
        entity.setDeliveredAt(lookup.getDeliveredAt());
        entity.setReadAt(lookup.getReadAt());
        entity.setExpiresAt(lookup.getExpiresAt());
        entity.setEditedAt(lookup.getEditedAt());
        entity.setDeletedAt(lookup.getDeletedAt());
        return entity;
    }
}
