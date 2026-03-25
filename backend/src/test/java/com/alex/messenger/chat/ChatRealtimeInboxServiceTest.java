package com.alex.messenger.chat;

import static org.mockito.Mockito.verify;

import com.alex.messenger.chat.dto.ChatInboxEventResponse;
import com.alex.messenger.chat.dto.ChatSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class ChatRealtimeInboxServiceTest {

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    private ChatRealtimeInboxService chatRealtimeInboxService;

    @BeforeEach
    void setUp() {
        chatRealtimeInboxService = new ChatRealtimeInboxService(simpMessagingTemplate);
    }

    @Test
    void publishChatUpdateSendsSummaryToUserQueue() {
        UUID userId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatSummaryResponse summary = new ChatSummaryResponse(
                chatId,
                "GROUP",
                "Ops room",
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
                3,
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

        chatRealtimeInboxService.publishChatUpdate(List.of(userId), "CHAT_UPDATED", summary);

        verify(simpMessagingTemplate).convertAndSendToUser(
                userId.toString(),
                "/queue/chats",
                new ChatInboxEventResponse("CHAT_UPDATED", chatId, summary)
        );
    }

    @Test
    void publishChatRemovalSendsRemovalEvent() {
        UUID userId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        chatRealtimeInboxService.publishChatRemoval(List.of(userId), chatId);

        verify(simpMessagingTemplate).convertAndSendToUser(
                userId.toString(),
                "/queue/chats",
                new ChatInboxEventResponse("CHAT_REMOVED", chatId, null)
        );
    }
}
