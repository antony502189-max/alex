package com.alex.messenger.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.alex.messenger.secret.dto.SecretChatInboxEventResponse;
import com.alex.messenger.secret.dto.SecretChatMessageResponse;
import com.alex.messenger.secret.dto.SecretChatReadEventResponse;
import com.alex.messenger.secret.dto.SecretChatScreenshotEventResponse;
import com.alex.messenger.secret.dto.SecretChatSummaryResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class SecretChatRealtimeServiceTest {

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    private SecretChatRealtimeService secretChatRealtimeService;

    @BeforeEach
    void setUp() {
        secretChatRealtimeService = new SecretChatRealtimeService(simpMessagingTemplate);
    }

    @Test
    void publishChatUpdateDeduplicatesRecipientsAndUsesSecretChatsQueue() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        SecretChatSummaryResponse chat = new SecretChatSummaryResponse(
                UUID.randomUUID(),
                secondUserId,
                "Bob",
                null,
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Phone",
                "initiator-key",
                "recipient-key",
                "fingerprint",
                "ACTIVE",
                "OUTGOING",
                30,
                Instant.parse("2026-03-19T19:00:00Z"),
                Instant.parse("2026-03-19T19:01:00Z"),
                null,
                Instant.parse("2026-03-19T19:02:00Z")
        );

        secretChatRealtimeService.publishChatUpdate(List.of(firstUserId, secondUserId, firstUserId), "UPDATED", chat);

        ArgumentCaptor<SecretChatInboxEventResponse> eventCaptor =
                ArgumentCaptor.forClass(SecretChatInboxEventResponse.class);
        verify(simpMessagingTemplate, times(2)).convertAndSendToUser(
                org.mockito.ArgumentMatchers.anyString(),
                eq("/queue/secret-chats"),
                eventCaptor.capture()
        );
        assertThat(eventCaptor.getAllValues()).extracting(SecretChatInboxEventResponse::eventType)
                .containsExactly("UPDATED", "UPDATED");
        assertThat(eventCaptor.getAllValues()).allMatch(event -> chat.equals(event.chat()) && event.message() == null);
    }

    @Test
    void publishMessageReadAndScreenshotEventsUseDedicatedPayloadSlots() {
        UUID userId = UUID.randomUUID();
        UUID secretChatId = UUID.randomUUID();
        SecretChatMessageResponse message = new SecretChatMessageResponse(
                secretChatId,
                UUID.randomUUID(),
                userId,
                UUID.randomUUID(),
                "TEXT",
                "ciphertext",
                "nonce",
                Instant.parse("2026-03-19T19:05:00Z"),
                null,
                null
        );
        SecretChatReadEventResponse readEvent = new SecretChatReadEventResponse(
                secretChatId,
                userId,
                Instant.parse("2026-03-19T19:06:00Z"),
                null,
                List.of(UUID.randomUUID())
        );
        SecretChatScreenshotEventResponse screenshotEvent = new SecretChatScreenshotEventResponse(
                secretChatId,
                userId,
                Instant.parse("2026-03-19T19:07:00Z")
        );

        secretChatRealtimeService.publishMessage(List.of(userId), message);
        secretChatRealtimeService.publishReadEvent(List.of(userId), readEvent);
        secretChatRealtimeService.publishScreenshotEvent(List.of(userId), screenshotEvent);

        ArgumentCaptor<SecretChatInboxEventResponse> eventCaptor =
                ArgumentCaptor.forClass(SecretChatInboxEventResponse.class);
        verify(simpMessagingTemplate, times(3)).convertAndSendToUser(
                eq(userId.toString()),
                eq("/queue/secret-chats"),
                eventCaptor.capture()
        );
        assertThat(eventCaptor.getAllValues()).extracting(SecretChatInboxEventResponse::eventType)
                .containsExactly("MESSAGE_CREATED", "MESSAGE_READ", "SCREENSHOT_CAPTURED");
        assertThat(eventCaptor.getAllValues().get(0).message()).isEqualTo(message);
        assertThat(eventCaptor.getAllValues().get(1).read()).isEqualTo(readEvent);
        assertThat(eventCaptor.getAllValues().get(2).screenshot()).isEqualTo(screenshotEvent);
    }
}
