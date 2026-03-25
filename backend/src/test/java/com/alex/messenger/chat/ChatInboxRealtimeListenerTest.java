package com.alex.messenger.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.dto.ChatSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ChatInboxRealtimeListenerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private ChatRealtimeInboxService chatRealtimeInboxService;

    private ChatInboxRealtimeListener listener;

    @BeforeEach
    void setUp() {
        listener = new ChatInboxRealtimeListener(chatService, chatRealtimeInboxService);
    }

    @Test
    void onChatInboxFanoutPublishesSummaryUpdatesAndRemovalEvents() {
        UUID chatId = UUID.randomUUID();
        UUID activeUserId = UUID.randomUUID();
        UUID removedUserId = UUID.randomUUID();
        ChatSummaryResponse summary = new ChatSummaryResponse(
                chatId,
                "GROUP",
                "Team",
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                false,
                0,
                null,
                null,
                null,
                2,
                null,
                0,
                0,
                0,
                false,
                null,
                null,
                null,
                false,
                null,
                null,
                false,
                true,
                true,
                false,
                null,
                false
        );
        when(chatService.getChatSummary(activeUserId, chatId)).thenReturn(summary);

        listener.onChatInboxFanout(
                new ChatInboxFanoutEvent("CHAT_MEMBER_LEFT", chatId, List.of(activeUserId), List.of(removedUserId))
        );

        verify(chatRealtimeInboxService).publishChatUpdate(List.of(activeUserId), "CHAT_MEMBER_LEFT", summary);
        verify(chatRealtimeInboxService).publishChatRemoval(List.of(removedUserId), chatId);
    }

    @Test
    void onChatInboxFanoutSkipsUsersWithoutAccess() {
        UUID chatId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(chatService.getChatSummary(userId, chatId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied"));

        listener.onChatInboxFanout(
                new ChatInboxFanoutEvent("CHAT_UPDATED", chatId, List.of(userId), List.of())
        );

        verify(chatRealtimeInboxService, never()).publishChatUpdate(eq(List.of(userId)), eq("CHAT_UPDATED"), any());
        verify(chatRealtimeInboxService, never()).publishChatRemoval(any(), eq(chatId));
    }
}
