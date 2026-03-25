package com.alex.messenger.message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.message.dto.ChatMessageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MessageRealtimeServiceTest {

    @Mock
    private MessageService messageService;

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    private MessageRealtimeService messageRealtimeService;

    @BeforeEach
    void setUp() {
        messageRealtimeService = new MessageRealtimeService(messageService, simpMessagingTemplate);
    }

    @Test
    void publishMessageUpsertSendsQueueEventsForAccessibleUsers() {
        UUID messageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessageResponse response = new ChatMessageResponse(
                UUID.randomUUID(),
                messageId,
                null,
                UUID.randomUUID(),
                "Sender",
                null,
                null,
                false,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                0,
                "hello",
                List.of(),
                "TEXT",
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.parse("2026-03-25T12:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                "DELIVERED",
                Instant.parse("2026-03-25T12:00:05Z"),
                null,
                null,
                null,
                null,
                false,
                null
        );

        when(messageService.getMessage(userId, messageId)).thenReturn(response);

        messageRealtimeService.publishMessageUpsert(messageId, List.of(userId));

        verify(simpMessagingTemplate).convertAndSendToUser(userId.toString(), "/queue/messages", response);
    }

    @Test
    void publishMessageUpsertSkipsUsersWithoutAccess() {
        UUID messageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(messageService.getMessage(userId, messageId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied"));

        messageRealtimeService.publishMessageUpsert(messageId, List.of(userId));

        verify(simpMessagingTemplate, never()).convertAndSendToUser(eq(userId.toString()), eq("/queue/messages"), any());
    }
}
