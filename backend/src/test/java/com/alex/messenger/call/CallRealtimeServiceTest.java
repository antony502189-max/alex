package com.alex.messenger.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.alex.messenger.call.dto.CallCommentResponse;
import com.alex.messenger.call.dto.CallInboxEventResponse;
import com.alex.messenger.call.dto.CallReactionResponse;
import com.alex.messenger.call.dto.CallSessionResponse;
import com.alex.messenger.call.dto.CallSignalEventResponse;
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
class CallRealtimeServiceTest {

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    private CallRealtimeService callRealtimeService;

    @BeforeEach
    void setUp() {
        callRealtimeService = new CallRealtimeService(simpMessagingTemplate);
    }

    @Test
    void publishSessionEventSendsInboxUpdateToCallsQueue() {
        UUID userId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        CallSessionResponse session = new CallSessionResponse(
                UUID.randomUUID(),
                chatId,
                userId,
                "VOICE",
                "GROUP",
                "RINGING",
                Instant.parse("2026-03-19T18:00:00Z"),
                null,
                null,
                false,
                null,
                true,
                true,
                List.of()
        );

        callRealtimeService.publishSessionEvent(userId, "UPDATED", session);

        ArgumentCaptor<CallInboxEventResponse> eventCaptor = ArgumentCaptor.forClass(CallInboxEventResponse.class);
        verify(simpMessagingTemplate).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq(userId.toString()),
                org.mockito.ArgumentMatchers.eq("/queue/calls"),
                eventCaptor.capture()
        );
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("UPDATED");
        assertThat(eventCaptor.getValue().call()).isEqualTo(session);
        assertThat(eventCaptor.getValue().signal()).isNull();
    }

    @Test
    void publishSignalEventSendsSignalEnvelopeToCallsQueue() {
        UUID userId = UUID.randomUUID();
        CallSignalEventResponse signal = new CallSignalEventResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                userId,
                "OFFER",
                "payload",
                Instant.parse("2026-03-19T18:05:00Z")
        );

        callRealtimeService.publishSignalEvent(userId, signal);

        ArgumentCaptor<CallInboxEventResponse> eventCaptor = ArgumentCaptor.forClass(CallInboxEventResponse.class);
        verify(simpMessagingTemplate).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq(userId.toString()),
                org.mockito.ArgumentMatchers.eq("/queue/calls"),
                eventCaptor.capture()
        );
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("SIGNAL");
        assertThat(eventCaptor.getValue().signal()).isEqualTo(signal);
        assertThat(eventCaptor.getValue().call()).isNull();
    }

    @Test
    void publishCommentAndReactionEventsKeepDedicatedPayloadSlots() {
        UUID userId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        CallCommentResponse comment = new CallCommentResponse(
                UUID.randomUUID(),
                callId,
                chatId,
                userId,
                "Alice",
                null,
                null,
                "hello",
                Instant.parse("2026-03-19T18:10:00Z")
        );
        CallReactionResponse reaction = new CallReactionResponse(
                UUID.randomUUID(),
                callId,
                chatId,
                userId,
                "Alice",
                null,
                null,
                "fire",
                Instant.parse("2026-03-19T18:11:00Z")
        );

        callRealtimeService.publishCommentEvent(userId, comment);
        callRealtimeService.publishReactionEvent(userId, reaction);

        ArgumentCaptor<CallInboxEventResponse> eventCaptor = ArgumentCaptor.forClass(CallInboxEventResponse.class);
        verify(simpMessagingTemplate, org.mockito.Mockito.times(2)).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq(userId.toString()),
                org.mockito.ArgumentMatchers.eq("/queue/calls"),
                eventCaptor.capture()
        );
        assertThat(eventCaptor.getAllValues()).extracting(CallInboxEventResponse::eventType)
                .containsExactly("COMMENT", "REACTION");
        assertThat(eventCaptor.getAllValues().get(0).comment()).isEqualTo(comment);
        assertThat(eventCaptor.getAllValues().get(1).reaction()).isEqualTo(reaction);
    }
}
