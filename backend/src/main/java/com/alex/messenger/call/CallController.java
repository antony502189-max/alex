package com.alex.messenger.call;

import com.alex.messenger.call.dto.CallHistoryEntryResponse;
import com.alex.messenger.call.dto.CallJoinLinkResponse;
import com.alex.messenger.call.dto.CallCommentResponse;
import com.alex.messenger.call.dto.CallReactionResponse;
import com.alex.messenger.call.dto.CallSessionResponse;
import com.alex.messenger.call.dto.CallRtcConfigResponse;
import com.alex.messenger.call.dto.CallSignalEventResponse;
import com.alex.messenger.call.dto.CallSignalRequest;
import com.alex.messenger.call.dto.CreateCallJoinLinkRequest;
import com.alex.messenger.call.dto.CreateCallCommentRequest;
import com.alex.messenger.call.dto.CreateCallReactionRequest;
import com.alex.messenger.call.dto.StartCallRequest;
import com.alex.messenger.call.dto.UpdateCallParticipantModerationRequest;
import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.shared.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class CallController {

    private final FeatureFlagService featureFlagService;
    private final CallService callService;
    private final CallRtcConfigService callRtcConfigService;

    @GetMapping("/rtc-config")
    public ResponseEntity<CallRtcConfigResponse> getRtcConfig() {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callRtcConfigService.getRtcConfig(CurrentUser.id()));
    }

    @GetMapping("/active")
    public ResponseEntity<List<CallSessionResponse>> getActiveCalls() {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.getActiveCalls(CurrentUser.id()));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<CallHistoryEntryResponse>> getRecentCalls(
            @RequestParam(defaultValue = "50") int limit
    ) {
        int validatedLimit = requireLimit(limit, 100);
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.getRecentCalls(CurrentUser.id(), validatedLimit));
    }

    @GetMapping("/links")
    public ResponseEntity<List<CallJoinLinkResponse>> getJoinLinks(@RequestParam UUID chatId) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.getJoinLinks(CurrentUser.id(), chatId));
    }

    @PostMapping("/links")
    public ResponseEntity<CallJoinLinkResponse> createJoinLink(
            @Valid @RequestBody CreateCallJoinLinkRequest request
    ) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.createJoinLink(CurrentUser.id(), request));
    }

    @PostMapping("/links/{token}/join")
    public ResponseEntity<CallSessionResponse> joinByLink(@PathVariable String token) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.joinByLink(CurrentUser.id(), token));
    }

    @PostMapping
    public ResponseEntity<CallSessionResponse> startCall(@Valid @RequestBody StartCallRequest request) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.startCall(CurrentUser.id(), request));
    }

    @PostMapping("/{callId}/accept")
    public ResponseEntity<CallSessionResponse> acceptCall(@PathVariable UUID callId) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.acceptCall(CurrentUser.id(), callId));
    }

    @PostMapping("/{callId}/decline")
    public ResponseEntity<CallSessionResponse> declineCall(@PathVariable UUID callId) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.declineCall(CurrentUser.id(), callId));
    }

    @PostMapping("/{callId}/leave")
    public ResponseEntity<CallSessionResponse> leaveCall(@PathVariable UUID callId) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.leaveCall(CurrentUser.id(), callId));
    }

    @PostMapping("/{callId}/signal")
    public ResponseEntity<CallSignalEventResponse> sendSignal(
            @PathVariable UUID callId,
            @Valid @RequestBody CallSignalRequest request
    ) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.sendSignal(CurrentUser.id(), callId, request));
    }

    @GetMapping("/{callId}/comments")
    public ResponseEntity<List<CallCommentResponse>> comments(
            @PathVariable UUID callId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        int validatedLimit = requireLimit(limit, 100);
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.listComments(CurrentUser.id(), callId, validatedLimit));
    }

    @PostMapping("/{callId}/comments")
    public ResponseEntity<CallCommentResponse> comment(
            @PathVariable UUID callId,
            @Valid @RequestBody(required = false) CreateCallCommentRequest request
    ) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.createComment(CurrentUser.id(), callId, request));
    }

    @GetMapping("/{callId}/reactions")
    public ResponseEntity<List<CallReactionResponse>> reactions(
            @PathVariable UUID callId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        int validatedLimit = requireLimit(limit, 100);
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.listReactions(CurrentUser.id(), callId, validatedLimit));
    }

    @PostMapping("/{callId}/reactions")
    public ResponseEntity<CallReactionResponse> react(
            @PathVariable UUID callId,
            @Valid @RequestBody(required = false) CreateCallReactionRequest request
    ) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.createReaction(CurrentUser.id(), callId, request));
    }

    @PostMapping("/{callId}/participants/{userId}/moderation")
    public ResponseEntity<CallSessionResponse> moderateParticipant(
            @PathVariable UUID callId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateCallParticipantModerationRequest request
    ) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.moderateParticipant(CurrentUser.id(), callId, userId, request));
    }

    @PostMapping("/{callId}/screen-share/start")
    public ResponseEntity<CallSessionResponse> startScreenShare(@PathVariable UUID callId) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.setScreenSharing(CurrentUser.id(), callId, true));
    }

    @PostMapping("/{callId}/screen-share/stop")
    public ResponseEntity<CallSessionResponse> stopScreenShare(@PathVariable UUID callId) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.setScreenSharing(CurrentUser.id(), callId, false));
    }

    @PostMapping("/{callId}/hand-raise")
    public ResponseEntity<CallSessionResponse> raiseHand(@PathVariable UUID callId) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.setHandRaised(CurrentUser.id(), callId, true));
    }

    @PostMapping("/{callId}/hand-lower")
    public ResponseEntity<CallSessionResponse> lowerHand(@PathVariable UUID callId) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.setHandRaised(CurrentUser.id(), callId, false));
    }

    @PostMapping("/{callId}/mute")
    public ResponseEntity<CallSessionResponse> mute(@PathVariable UUID callId) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.setAudioMuted(CurrentUser.id(), callId, true));
    }

    @PostMapping("/{callId}/unmute")
    public ResponseEntity<CallSessionResponse> unmute(@PathVariable UUID callId) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.setAudioMuted(CurrentUser.id(), callId, false));
    }

    @PostMapping("/{callId}/recording/start")
    public ResponseEntity<CallSessionResponse> startRecording(@PathVariable UUID callId) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.setRecording(CurrentUser.id(), callId, true));
    }

    @PostMapping("/{callId}/recording/stop")
    public ResponseEntity<CallSessionResponse> stopRecording(@PathVariable UUID callId) {
        featureFlagService.requireCallsEnabled();
        return ResponseEntity.ok(callService.setRecording(CurrentUser.id(), callId, false));
    }

    private int requireLimit(int limit, int max) {
        if (limit < 1 || limit > max) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and " + max
            );
        }
        return limit;
    }
}
