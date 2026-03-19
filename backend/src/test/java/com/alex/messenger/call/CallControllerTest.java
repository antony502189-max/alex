package com.alex.messenger.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.call.dto.CallCommentResponse;
import com.alex.messenger.call.dto.CallReactionResponse;
import com.alex.messenger.call.dto.CallJoinLinkResponse;
import com.alex.messenger.call.dto.CallSessionResponse;
import com.alex.messenger.call.dto.CreateCallJoinLinkRequest;
import com.alex.messenger.call.dto.CreateCallCommentRequest;
import com.alex.messenger.call.dto.CreateCallReactionRequest;
import com.alex.messenger.call.dto.StartCallRequest;
import com.alex.messenger.call.dto.UpdateCallParticipantModerationRequest;
import com.alex.messenger.feature.FeatureFlagService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class CallControllerTest {

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private CallService callService;

    @Mock
    private CallRtcConfigService callRtcConfigService;

    private CallController callController;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        callController = new CallController(featureFlagService, callService, callRtcConfigService);
        currentUserId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUserId.toString(), "test")
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void startCallRequiresFlagAndUsesAuthenticatedUser() {
        UUID chatId = UUID.randomUUID();
        StartCallRequest request = new StartCallRequest(chatId, "VOICE", "GROUP", false);
        CallSessionResponse response = new CallSessionResponse(
                UUID.randomUUID(),
                chatId,
                currentUserId,
                "VOICE",
                "GROUP",
                "RINGING",
                Instant.parse("2026-03-19T17:05:00Z"),
                null,
                null,
                false,
                null,
                true,
                true,
                List.of()
        );

        when(callService.startCall(currentUserId, request)).thenReturn(response);

        ResponseEntity<CallSessionResponse> entity = callController.startCall(request);

        assertThat(entity.getBody()).isEqualTo(response);
        verify(featureFlagService).requireCallsEnabled();
        verify(callService).startCall(currentUserId, request);
    }

    @Test
    void createJoinLinkRequiresFlagAndUsesAuthenticatedUser() {
        UUID chatId = UUID.randomUUID();
        CreateCallJoinLinkRequest request = new CreateCallJoinLinkRequest(
                chatId,
                "VOICE",
                "VOICE_CHAT",
                "Townhall",
                Instant.parse("2026-03-20T17:00:00Z")
        );
        CallJoinLinkResponse response = new CallJoinLinkResponse(
                UUID.randomUUID(),
                chatId,
                currentUserId,
                "VOICE",
                "VOICE_CHAT",
                "Townhall",
                "token123",
                "alex://call/token123",
                false,
                0,
                Instant.parse("2026-03-20T17:00:00Z"),
                Instant.parse("2026-03-19T17:00:00Z"),
                null
        );

        when(callService.createJoinLink(currentUserId, request)).thenReturn(response);

        ResponseEntity<CallJoinLinkResponse> entity = callController.createJoinLink(request);

        assertThat(entity.getBody()).isEqualTo(response);
        verify(featureFlagService).requireCallsEnabled();
        verify(callService).createJoinLink(currentUserId, request);
    }

    @Test
    void commentRequiresFlagAndUsesAuthenticatedUser() {
        UUID callId = UUID.randomUUID();
        CreateCallCommentRequest request = new CreateCallCommentRequest("Need backup");
        CallCommentResponse response = new CallCommentResponse(
                UUID.randomUUID(),
                callId,
                UUID.randomUUID(),
                currentUserId,
                "Alice",
                null,
                null,
                "Need backup",
                Instant.parse("2026-03-19T17:10:00Z")
        );

        when(callService.createComment(currentUserId, callId, request)).thenReturn(response);

        ResponseEntity<CallCommentResponse> entity = callController.comment(callId, request);

        assertThat(entity.getBody()).isEqualTo(response);
        verify(featureFlagService).requireCallsEnabled();
        verify(callService).createComment(currentUserId, callId, request);
    }

    @Test
    void reactRequiresFlagAndUsesAuthenticatedUser() {
        UUID callId = UUID.randomUUID();
        CreateCallReactionRequest request = new CreateCallReactionRequest("fire");
        CallReactionResponse response = new CallReactionResponse(
                UUID.randomUUID(),
                callId,
                UUID.randomUUID(),
                currentUserId,
                "Alice",
                null,
                null,
                "fire",
                Instant.parse("2026-03-19T17:15:00Z")
        );

        when(callService.createReaction(currentUserId, callId, request)).thenReturn(response);

        ResponseEntity<CallReactionResponse> entity = callController.react(callId, request);

        assertThat(entity.getBody()).isEqualTo(response);
        verify(featureFlagService).requireCallsEnabled();
        verify(callService).createReaction(currentUserId, callId, request);
    }

    @Test
    void moderateParticipantRequiresFlagAndUsesAuthenticatedUser() {
        UUID callId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UpdateCallParticipantModerationRequest request =
                new UpdateCallParticipantModerationRequest(false, null, null, true, null);
        CallSessionResponse response = new CallSessionResponse(
                callId,
                UUID.randomUUID(),
                currentUserId,
                "VOICE",
                "GROUP",
                "ACTIVE",
                Instant.parse("2026-03-19T17:20:00Z"),
                Instant.parse("2026-03-19T17:20:10Z"),
                null,
                false,
                null,
                true,
                true,
                List.of()
        );

        when(callService.moderateParticipant(currentUserId, callId, targetUserId, request)).thenReturn(response);

        ResponseEntity<CallSessionResponse> entity = callController.moderateParticipant(callId, targetUserId, request);

        assertThat(entity.getBody()).isEqualTo(response);
        verify(featureFlagService).requireCallsEnabled();
        verify(callService).moderateParticipant(currentUserId, callId, targetUserId, request);
    }
}
