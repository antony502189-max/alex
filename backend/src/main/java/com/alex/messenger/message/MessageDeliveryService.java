package com.alex.messenger.message;

import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MessageDeliveryService {

    private final MessageLookupRepository messageLookupRepository;
    private final MessageRepository messageRepository;
    private final MessageStorageService messageStorageService;
    private final ChatService chatService;

    @Transactional
    public MessageLookupEntity markDelivered(UUID messageId) {
        MessageLookupEntity lookup = messageLookupRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        if ("READ".equals(lookup.getDeliveryStatus())) {
            return lookup;
        }
        if (lookup.getDeliveredAt() == null) {
            lookup.setDeliveredAt(Instant.now());
        }
        lookup.setDeliveryStatus("DELIVERED");
        persist(lookup);
        return lookup;
    }

    @Transactional
    public void markReadUpTo(UUID requesterId, UUID chatId, UUID messageId) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        if (!"DIRECT".equals(chat.getChatType())) {
            return;
        }

        MessageLookupEntity target = messageLookupRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        if (!target.getChatId().equals(chatId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message belongs to another chat");
        }

        Instant readAt = Instant.now();
        List<MessageEntity> messages = messageRepository.findAllByChatIdUpToMessageId(chatId, target.getMessageId());
        for (MessageEntity message : messages) {
            if (message.getRecipientId() == null || !message.getRecipientId().equals(requesterId)) {
                continue;
            }
            MessageLookupEntity lookup = messageLookupRepository.findById(message.getKey().getMessageId())
                    .orElse(null);
            if (lookup == null) {
                continue;
            }
            lookup.setDeliveryStatus("READ");
            if (lookup.getDeliveredAt() == null) {
                lookup.setDeliveredAt(readAt);
            }
            lookup.setReadAt(readAt);
            persist(lookup);
        }
    }

    private void persist(MessageLookupEntity lookup) {
        messageStorageService.save(lookup);
    }
}
