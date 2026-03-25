package com.alex.messenger.message;

import com.alex.messenger.message.dto.ChatMessageResponse;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MessageRealtimeService {

    private final MessageService messageService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public void publishMessageUpsert(UUID messageId, Collection<UUID> userIds) {
        if (messageId == null || userIds == null || userIds.isEmpty()) {
            return;
        }
        for (UUID userId : new LinkedHashSet<>(userIds)) {
            if (userId == null) {
                continue;
            }
            try {
                ChatMessageResponse response = messageService.getMessage(userId, messageId);
                simpMessagingTemplate.convertAndSendToUser(userId.toString(), "/queue/messages", response);
            } catch (ResponseStatusException exception) {
                // Skip fanout for users that no longer have access to the message.
            }
        }
    }
}
