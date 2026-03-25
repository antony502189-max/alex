package com.alex.messenger.chat;

import com.alex.messenger.chat.dto.ChatInboxEventResponse;
import com.alex.messenger.chat.dto.ChatSummaryResponse;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatRealtimeInboxService {

    private final SimpMessagingTemplate simpMessagingTemplate;

    public void publishChatUpdate(Collection<UUID> userIds, String eventType, ChatSummaryResponse chat) {
        if (chat == null) {
            return;
        }
        for (UUID userId : new LinkedHashSet<>(userIds)) {
            if (userId == null) {
                continue;
            }
            simpMessagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/chats",
                    new ChatInboxEventResponse(eventType, chat.chatId(), chat)
            );
        }
    }

    public void publishChatRemoval(Collection<UUID> userIds, UUID chatId) {
        for (UUID userId : new LinkedHashSet<>(userIds)) {
            if (userId == null) {
                continue;
            }
            simpMessagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/chats",
                    new ChatInboxEventResponse("CHAT_REMOVED", chatId, null)
            );
        }
    }
}
