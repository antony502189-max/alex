package com.alex.messenger.chat;

import com.alex.messenger.chat.dto.ChatSummaryResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class ChatInboxRealtimeListener {

    private final ChatService chatService;
    private final ChatRealtimeInboxService chatRealtimeInboxService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatInboxFanout(ChatInboxFanoutEvent event) {
        if (event == null || event.chatId() == null) {
            return;
        }

        Set<UUID> removedUserIds = new LinkedHashSet<>(event.removedUserIds() != null ? event.removedUserIds() : List.of());
        for (UUID userId : new LinkedHashSet<>(event.userIds() != null ? event.userIds() : List.of())) {
            if (userId == null || removedUserIds.contains(userId)) {
                continue;
            }
            try {
                ChatSummaryResponse summary = chatService.getChatSummary(userId, event.chatId());
                chatRealtimeInboxService.publishChatUpdate(List.of(userId), event.eventType(), summary);
            } catch (ResponseStatusException exception) {
                // Skip fanout for users who no longer have access to the chat.
            }
        }

        if (!removedUserIds.isEmpty()) {
            chatRealtimeInboxService.publishChatRemoval(List.copyOf(removedUserIds), event.chatId());
        }
    }
}
