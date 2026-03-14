package com.alex.messenger.call;

import com.alex.messenger.call.dto.CallHistoryEntryResponse;
import com.alex.messenger.call.dto.CallJoinLinkResponse;
import com.alex.messenger.call.dto.CallParticipantResponse;
import com.alex.messenger.call.dto.CallSessionResponse;
import com.alex.messenger.call.dto.CallSignalEventResponse;
import com.alex.messenger.call.dto.CallSignalRequest;
import com.alex.messenger.call.dto.CreateCallJoinLinkRequest;
import com.alex.messenger.call.dto.StartCallRequest;
import com.alex.messenger.call.dto.UpdateCallParticipantModerationRequest;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatMemberEntity;
import com.alex.messenger.chat.ChatMemberId;
import com.alex.messenger.chat.ChatMemberRepository;
import com.alex.messenger.chat.ChatRepository;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.media.PhotoAccess;
import com.alex.messenger.media.ProfilePhotoService;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CallService {

    private static final List<String> LIVE_STATUSES = List.of("RINGING", "ACTIVE");
    private static final List<String> TERMINAL_PARTICIPANT_STATES = List.of("LEFT", "DECLINED", "MISSED");

    private final CallSessionRepository callSessionRepository;
    private final CallParticipantRepository callParticipantRepository;
    private final CallJoinLinkRepository callJoinLinkRepository;
    private final ChatRepository chatRepository;
    private final ChatService chatService;
    private final ChatMemberRepository chatMemberRepository;
    private final UserRepository userRepository;
    private final ProfilePhotoService profilePhotoService;
    private final CallRealtimeService callRealtimeService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public List<CallSessionResponse> getActiveCalls(UUID requesterId) {
        List<CallSessionEntity> sessions = callSessionRepository.findByParticipantAndStatuses(requesterId, LIVE_STATUSES);
        if (sessions.isEmpty()) {
            return List.of();
        }
        Set<UUID> joinedChatIds = loadJoinedChatIds(requesterId);

        Map<UUID, CallParticipantEntity> requesterParticipantsByCallId = callParticipantRepository
                .findAllByIdUserIdAndIdCallIdIn(
                        requesterId,
                        sessions.stream().map(CallSessionEntity::getId).toList()
                )
                .stream()
                .collect(Collectors.toMap(participant -> participant.getId().getCallId(), Function.identity()));

        return sessions.stream()
                .filter(session -> joinedChatIds.contains(session.getChatId()))
                .filter(session -> isActiveForViewer(requesterParticipantsByCallId.get(session.getId())))
                .map(session -> toResponse(session, requesterId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CallHistoryEntryResponse> getRecentCalls(UUID requesterId, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        int fetchLimit = Math.min(Math.max(normalizedLimit * 2, normalizedLimit + 10), 200);
        List<CallSessionEntity> sessions = callSessionRepository.findRecentByParticipant(
                requesterId,
                PageRequest.of(0, fetchLimit)
        );
        if (sessions.isEmpty()) {
            return List.of();
        }
        Set<UUID> joinedChatIds = loadJoinedChatIds(requesterId);

        List<CallSessionEntity> visibleSessions = sessions.stream()
                .filter(session -> joinedChatIds.contains(session.getChatId()))
                .toList();
        if (visibleSessions.isEmpty()) {
            return List.of();
        }

        List<UUID> callIds = visibleSessions.stream().map(CallSessionEntity::getId).toList();
        Map<UUID, List<CallParticipantEntity>> participantsByCallId = callParticipantRepository
                .findAllByIdCallIdIn(callIds)
                .stream()
                .collect(Collectors.groupingBy(participant -> participant.getId().getCallId()));
        List<CallSessionEntity> recentSessions = visibleSessions.stream()
                .filter(session -> shouldIncludeInRecent(
                        session,
                        findParticipant(participantsByCallId.getOrDefault(session.getId(), List.of()), requesterId)
                ))
                .limit(normalizedLimit)
                .toList();
        if (recentSessions.isEmpty()) {
            return List.of();
        }

        Map<UUID, ChatEntity> chatsById = chatRepository.findAllById(
                recentSessions.stream().map(CallSessionEntity::getChatId).distinct().toList()
        ).stream().collect(Collectors.toMap(ChatEntity::getId, Function.identity()));
        List<UUID> directPeerIds = chatsById.values().stream()
                .filter(chat -> "DIRECT".equals(chat.getChatType()))
                .map(chat -> resolveDirectPeerId(chat, requesterId))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, UserEntity> directPeersById = userRepository.findAllById(directPeerIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        return recentSessions.stream()
                .map(session -> toHistoryEntry(
                        session,
                        requesterId,
                        chatsById.get(session.getChatId()),
                        participantsByCallId.getOrDefault(session.getId(), List.of()),
                        directPeersById
                ))
                .toList();
    }

    private Set<UUID> loadJoinedChatIds(UUID requesterId) {
        return chatMemberRepository.findAllByIdUserId(requesterId).stream()
                .map(member -> member.getId().getChatId())
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<CallJoinLinkResponse> getJoinLinks(UUID requesterId, UUID chatId) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        ensureCallLinksSupported(chat);
        ensureCanManageCallLinks(chat, requesterId);
        return callJoinLinkRepository.findAllByChatIdOrderByCreatedAtDesc(chatId).stream()
                .map(this::toJoinLinkResponse)
                .toList();
    }

    @Transactional
    public CallJoinLinkResponse createJoinLink(UUID requesterId, CreateCallJoinLinkRequest request) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, request.chatId());
        ensureCallLinksSupported(chat);
        ensureCanManageCallLinks(chat, requesterId);

        Instant expiresAt = request.expiresAt();
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Call link expiration must be in the future");
        }

        CallJoinLinkEntity link = new CallJoinLinkEntity();
        link.setChatId(chat.getId());
        link.setCreatedByUserId(requesterId);
        link.setKind(normalizeKind(request.kind()));
        link.setMode(normalizeJoinLinkMode(chat, request.mode(), request.kind()));
        link.setLabel(normalizeLabel(request.label()));
        link.setToken(generateJoinLinkToken());
        link.setExpiresAt(expiresAt);
        return toJoinLinkResponse(callJoinLinkRepository.save(link));
    }

    @Transactional
    public CallSessionResponse joinByLink(UUID requesterId, String token) {
        String normalizedToken = normalizeJoinLinkToken(token);
        CallJoinLinkEntity link = callJoinLinkRepository.findByToken(normalizedToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Call link not found"));
        validateJoinLink(link);

        ChatEntity chat = chatService.getOwnedChat(requesterId, link.getChatId());
        ensureCallLinksSupported(chat);
        ensureLiveJoinLinkMode(link.getMode());

        Instant now = Instant.now();
        CallSessionEntity session = callSessionRepository
                .findFirstByChatIdAndStatusInOrderByStartedAtDesc(chat.getId(), LIVE_STATUSES);
        if (session == null) {
            ensureCanStartManagedLiveCall(chat, requesterId, link.getMode());
            session = createCallSession(chat, requesterId, link.getKind(), link.getMode(), false, now);
            publishSessionUpdate(session, "STARTED");
        } else {
            ensureJoinLinkMatchesSession(link, session);
            joinParticipantState(session, requesterId, now);
            publishSessionUpdate(session, "UPDATED");
        }

        link.setUsageCount((link.getUsageCount() != null ? link.getUsageCount() : 0) + 1);
        link.setLastUsedAt(now);
        callJoinLinkRepository.save(link);
        return toResponse(session, requesterId);
    }

    @Transactional
    public CallSessionResponse startCall(UUID requesterId, StartCallRequest request) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, request.chatId());
        if ("SAVED".equals(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saved Messages does not support calls");
        }
        if (callSessionRepository.existsByChatIdAndStatusIn(chat.getId(), LIVE_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another live call already exists in this chat");
        }
        int participantCount = chatMemberRepository.findAllByIdChatId(chat.getId()).size();
        String normalizedMode = normalizeMode(chat, request.mode(), request.kind(), participantCount);
        ensureCanStartManagedLiveCall(chat, requesterId, normalizedMode);

        CallSessionEntity savedSession = createCallSession(
                chat,
                requesterId,
                request.kind(),
                request.mode(),
                Boolean.TRUE.equals(request.recordingEnabled()),
                Instant.now()
        );
        publishSessionUpdate(savedSession, "STARTED");
        return toResponse(savedSession, requesterId);
    }

    @Transactional
    public CallSessionResponse acceptCall(UUID requesterId, UUID callId) {
        CallSessionEntity session = getAccessibleCall(requesterId, callId);
        if (!LIVE_STATUSES.contains(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Call is no longer active");
        }

        joinParticipantState(session, requesterId, Instant.now());
        publishSessionUpdate(session, "UPDATED");
        return toResponse(session, requesterId);
    }

    @Transactional
    public CallSessionResponse declineCall(UUID requesterId, UUID callId) {
        CallSessionEntity session = getAccessibleCall(requesterId, callId);
        ensureCallIsLive(session);

        CallParticipantEntity participant = getParticipant(callId, requesterId);
        if (TERMINAL_PARTICIPANT_STATES.contains(participant.getState())) {
            return toResponse(session, requesterId);
        }
        if (!List.of("RINGING", "INVITED").contains(participant.getState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only invited participants can decline the call");
        }
        Instant now = Instant.now();
        participant.setState("DECLINED");
        participant.setLeftAt(now);
        participant.setScreenSharing(false);
        participant.setHandRaised(false);
        callParticipantRepository.save(participant);

        if ("DIRECT".equals(session.getMode()) && countParticipantsByStates(callId, List.of("RINGING", "JOINED")) <= 1) {
            session.setStatus("DECLINED");
            session.setEndedAt(now);
            markRemainingParticipantsMissed(callId, now);
            callSessionRepository.save(session);
        } else {
            reconcileSessionAfterParticipantExit(session, now);
        }

        publishSessionUpdate(session, "UPDATED");
        return toResponse(session, requesterId);
    }

    @Transactional
    public CallSessionResponse leaveCall(UUID requesterId, UUID callId) {
        CallSessionEntity session = getAccessibleCall(requesterId, callId);
        if (!LIVE_STATUSES.contains(session.getStatus())) {
            return toResponse(session, requesterId);
        }
        CallParticipantEntity participant = getParticipant(callId, requesterId);

        if (TERMINAL_PARTICIPANT_STATES.contains(participant.getState())) {
            return toResponse(session, requesterId);
        }
        if (!"JOINED".equals(participant.getState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only joined participants can leave the call");
        }

        participant.setState("LEFT");
        participant.setLeftAt(Instant.now());
        participant.setScreenSharing(false);
        participant.setHandRaised(false);
        callParticipantRepository.save(participant);

        reconcileSessionAfterParticipantExit(session, Instant.now());
        publishSessionUpdate(session, "UPDATED");
        return toResponse(session, requesterId);
    }

    @Transactional
    public CallSessionResponse moderateParticipant(
            UUID requesterId,
            UUID callId,
            UUID userId,
            UpdateCallParticipantModerationRequest request
    ) {
        CallSessionEntity session = getAccessibleCall(requesterId, callId);
        ensureCallIsLive(session);
        ensureCanModerateCall(requesterId, session);
        if (request == null
                || (request.audioPublishingAllowed() == null
                && request.videoPublishingAllowed() == null
                && request.screenShareAllowed() == null
                && request.audioMuted() == null
                && !Boolean.TRUE.equals(request.removeParticipant()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No moderation changes were provided");
        }
        if (requesterId.equals(userId) && Boolean.TRUE.equals(request.removeParticipant())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use leave call to remove yourself");
        }

        CallParticipantEntity participant = getParticipant(callId, userId);
        Instant now = Instant.now();
        boolean changed = false;

        if (request.audioPublishingAllowed() != null
                && !Objects.equals(participant.getAudioPublishingAllowed(), request.audioPublishingAllowed())) {
            participant.setAudioPublishingAllowed(request.audioPublishingAllowed());
            changed = true;
        }
        if (request.videoPublishingAllowed() != null
                && !Objects.equals(participant.getVideoPublishingAllowed(), request.videoPublishingAllowed())) {
            participant.setVideoPublishingAllowed(request.videoPublishingAllowed());
            changed = true;
        }
        if (request.screenShareAllowed() != null
                && !Objects.equals(participant.getScreenShareAllowed(), request.screenShareAllowed())) {
            participant.setScreenShareAllowed(request.screenShareAllowed());
            if (!Boolean.TRUE.equals(request.screenShareAllowed())) {
                participant.setScreenSharing(false);
            }
            changed = true;
        }
        if (request.audioMuted() != null && !Objects.equals(participant.getAudioMuted(), request.audioMuted())) {
            participant.setAudioMuted(request.audioMuted());
            participant.setMutedByModerator(request.audioMuted());
            participant.setMutedByUserId(request.audioMuted() ? requesterId : null);
            participant.setMutedAt(request.audioMuted() ? now : null);
            if (Boolean.TRUE.equals(request.audioMuted())) {
                participant.setHandRaised(false);
            }
            changed = true;
        }
        if (Boolean.TRUE.equals(request.removeParticipant()) && !TERMINAL_PARTICIPANT_STATES.contains(participant.getState())) {
            participant.setState("LEFT");
            participant.setLeftAt(now);
            participant.setScreenSharing(false);
            participant.setHandRaised(false);
            changed = true;
        }

        if (changed) {
            participant.setModeratedByUserId(requesterId);
            participant.setModeratedAt(now);
            callParticipantRepository.save(participant);
            reconcileSessionAfterParticipantExit(session, now);
            publishSessionUpdate(session, "UPDATED");
        }

        return toResponse(session, requesterId);
    }

    @Transactional
    public CallSessionResponse setScreenSharing(UUID requesterId, UUID callId, boolean enabled) {
        CallSessionEntity session = getAccessibleCall(requesterId, callId);
        if (!LIVE_STATUSES.contains(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Call is no longer active");
        }

        CallParticipantEntity participant = getParticipant(callId, requesterId);
        if (!"JOINED".equals(participant.getState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only joined participants can share screen");
        }
        if (enabled && !Boolean.TRUE.equals(participant.getScreenShareAllowed())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Screen sharing is disabled for this participant");
        }
        if (Objects.equals(participant.getScreenSharing(), enabled)) {
            return toResponse(session, requesterId);
        }

        participant.setScreenSharing(enabled);
        callParticipantRepository.save(participant);
        publishSessionUpdate(session, "UPDATED");
        return toResponse(session, requesterId);
    }

    @Transactional
    public CallSessionResponse setHandRaised(UUID requesterId, UUID callId, boolean enabled) {
        CallSessionEntity session = getAccessibleCall(requesterId, callId);
        if (!LIVE_STATUSES.contains(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Call is no longer active");
        }
        if ("DIRECT".equals(session.getMode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hand raise is available only in group call modes");
        }

        CallParticipantEntity participant = getParticipant(callId, requesterId);
        if (!"JOINED".equals(participant.getState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only joined participants can raise hand");
        }
        if (Objects.equals(participant.getHandRaised(), enabled)) {
            return toResponse(session, requesterId);
        }

        participant.setHandRaised(enabled);
        if (enabled) {
            participant.setAudioMuted(true);
        }
        callParticipantRepository.save(participant);
        publishSessionUpdate(session, "UPDATED");
        return toResponse(session, requesterId);
    }

    @Transactional
    public CallSessionResponse setAudioMuted(UUID requesterId, UUID callId, boolean enabled) {
        CallSessionEntity session = getAccessibleCall(requesterId, callId);
        if (!LIVE_STATUSES.contains(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Call is no longer active");
        }

        CallParticipantEntity participant = getParticipant(callId, requesterId);
        if (!"JOINED".equals(participant.getState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only joined participants can change mute state");
        }
        if (!enabled && !Boolean.TRUE.equals(participant.getAudioPublishingAllowed())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Audio publishing is disabled for this participant");
        }
        if (!enabled && Boolean.TRUE.equals(participant.getMutedByModerator())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Audio was muted by a moderator");
        }
        if (Objects.equals(participant.getAudioMuted(), enabled)
                && (!enabled || !Boolean.TRUE.equals(participant.getMutedByModerator()))) {
            return toResponse(session, requesterId);
        }

        participant.setAudioMuted(enabled);
        if (!enabled) {
            participant.setMutedByModerator(false);
            participant.setMutedByUserId(null);
            participant.setMutedAt(null);
        }
        if (!enabled) {
            participant.setHandRaised(false);
        }
        callParticipantRepository.save(participant);
        publishSessionUpdate(session, "UPDATED");
        return toResponse(session, requesterId);
    }

    @Transactional
    public CallSessionResponse setRecording(UUID requesterId, UUID callId, boolean enabled) {
        CallSessionEntity session = getAccessibleCall(requesterId, callId);
        if (!LIVE_STATUSES.contains(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Call is no longer active");
        }
        ensureCanModerateCall(requesterId, session);
        if (Objects.equals(session.getRecordingEnabled(), enabled)) {
            return toResponse(session, requesterId);
        }

        session.setRecordingEnabled(enabled);
        session.setRecordingStartedAt(enabled ? Instant.now() : null);
        callSessionRepository.save(session);
        publishSessionUpdate(session, "UPDATED");
        return toResponse(session, requesterId);
    }

    @Transactional(readOnly = true)
    public CallSignalEventResponse sendSignal(
            UUID requesterId,
            UUID callId,
            CallSignalRequest request
    ) {
        CallSessionEntity session = getAccessibleCall(requesterId, callId);
        ensureCallIsLive(session);
        if (request.toUserId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Signal target must be another participant");
        }
        CallParticipantEntity senderParticipant = getParticipant(callId, requesterId);
        ensureParticipantCanSignal(senderParticipant, "Sender");
        CallParticipantEntity targetParticipant = callParticipantRepository
                .findById(new CallParticipantId(callId, request.toUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Signal target is not in this call"));
        ensureParticipantCanSignal(targetParticipant, "Signal target");
        ensureParticipantIsCurrentChatMember(session.getChatId(), request.toUserId(), "Signal target is not in this call");

        CallSignalEventResponse signal = new CallSignalEventResponse(
                callId,
                requesterId,
                request.toUserId(),
                request.signalType().trim().toUpperCase(),
                request.payload(),
                Instant.now()
        );
        callRealtimeService.publishSignalEvent(request.toUserId(), signal);
        return signal;
    }

    private void publishSessionUpdate(CallSessionEntity session, String eventType) {
        List<CallParticipantEntity> participants = callParticipantRepository.findAllByIdCallId(session.getId());
        Map<UUID, UserEntity> usersById = loadCallUsers(participants);
        ChatEntity chat = chatRepository.findById(session.getChatId()).orElse(null);
        if (chat == null) {
            return;
        }
        Map<UUID, ChatMemberEntity> membershipsByUserId = chatMemberRepository.findAllByIdChatId(session.getChatId()).stream()
                .collect(Collectors.toMap(member -> member.getId().getUserId(), Function.identity()));
        if (membershipsByUserId.isEmpty()) {
            return;
        }
        List<CallParticipantEntity> visibleParticipants = participants.stream()
                .filter(participant -> membershipsByUserId.containsKey(participant.getId().getUserId()))
                .toList();
        for (CallParticipantEntity participant : visibleParticipants) {
            UUID viewerId = participant.getId().getUserId();
            callRealtimeService.publishSessionEvent(
                    viewerId,
                    eventType,
                    buildResponse(session, visibleParticipants, usersById, chat, membershipsByUserId.get(viewerId), viewerId)
            );
        }
    }

    private CallSessionEntity getAccessibleCall(UUID requesterId, UUID callId) {
        CallSessionEntity session = callSessionRepository.findById(callId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Call not found"));
        if (!callParticipantRepository.existsByIdCallIdAndIdUserId(callId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Call access denied");
        }
        chatService.getOwnedChat(requesterId, session.getChatId());
        return session;
    }

    private CallParticipantEntity getParticipant(UUID callId, UUID userId) {
        return callParticipantRepository.findById(new CallParticipantId(callId, userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Call access denied"));
    }

    private long countParticipantsByStates(UUID callId, Collection<String> states) {
        return callParticipantRepository.countByIdCallIdAndStateIn(callId, states);
    }

    private void ensureCallIsLive(CallSessionEntity session) {
        if (!LIVE_STATUSES.contains(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Call is no longer active");
        }
    }

    private boolean isActiveForViewer(CallParticipantEntity participant) {
        return participant != null && !TERMINAL_PARTICIPANT_STATES.contains(participant.getState());
    }

    private boolean shouldIncludeInRecent(CallSessionEntity session, CallParticipantEntity participant) {
        return !LIVE_STATUSES.contains(session.getStatus()) || !isActiveForViewer(participant);
    }

    private CallParticipantEntity findParticipant(List<CallParticipantEntity> participants, UUID userId) {
        if (participants == null || participants.isEmpty()) {
            return null;
        }
        return participants.stream()
                .filter(participant -> participant.getId().getUserId().equals(userId))
                .findFirst()
                .orElse(null);
    }

    private void ensureParticipantCanSignal(CallParticipantEntity participant, String label) {
        if (!isActiveForViewer(participant)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, label + " is no longer in this call");
        }
    }

    private void ensureParticipantIsCurrentChatMember(UUID chatId, UUID userId, String message) {
        if (!chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }
    }

    private void markRemainingParticipantsMissed(UUID callId, Instant endedAt) {
        List<CallParticipantEntity> participants = callParticipantRepository.findAllByIdCallId(callId);
        boolean changed = false;
        for (CallParticipantEntity participant : participants) {
            if (!List.of("RINGING", "INVITED").contains(participant.getState())) {
                continue;
            }
            participant.setState("MISSED");
            participant.setLeftAt(endedAt);
            participant.setScreenSharing(false);
            participant.setHandRaised(false);
            changed = true;
        }
        if (changed) {
            callParticipantRepository.saveAll(participants);
        }
    }

    private String normalizeKind(String kind) {
        String normalized = kind != null ? kind.trim().toUpperCase() : "VOICE";
        if (!List.of("VOICE", "VIDEO").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported call kind");
        }
        return normalized;
    }

    private String normalizeMode(ChatEntity chat, String mode, String kind, int participantCount) {
        String normalizedKind = normalizeKind(kind);
        String normalized = mode != null ? mode.trim().toUpperCase() : defaultMode(chat, participantCount, normalizedKind);
        if ("PRIVATE".equals(normalized)) {
            normalized = "DIRECT";
        }
        if (!List.of("DIRECT", "GROUP", "VOICE_CHAT", "LIVE_STREAM").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported call mode");
        }
        if ("DIRECT".equals(normalized) && !"DIRECT".equals(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Direct mode is available only in direct chats");
        }
        if (!"DIRECT".equals(normalized) && "DIRECT".equals(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Direct chats support private mode only");
        }
        if ("CHANNEL".equals(chat.getChatType()) && !List.of("VOICE_CHAT", "LIVE_STREAM").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Channel chats support live call modes only");
        }
        if ("VOICE_CHAT".equals(normalized) && !"VOICE".equals(normalizedKind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voice chat mode supports voice calls only");
        }
        if (List.of("VOICE_CHAT", "LIVE_STREAM").contains(normalized)
                && !List.of("GROUP", "CHANNEL").contains(chat.getChatType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This call mode is available only in group and channel chats"
            );
        }
        return normalized;
    }

    private String normalizeJoinLinkMode(ChatEntity chat, String mode, String kind) {
        String normalizedKind = normalizeKind(kind);
        String normalizedMode = mode != null
                ? normalizeMode(chat, mode, normalizedKind, 2)
                : defaultJoinLinkMode(normalizedKind);
        ensureLiveJoinLinkMode(normalizedMode);
        return normalizedMode;
    }

    private String defaultMode(ChatEntity chat, int participantCount, String normalizedKind) {
        if ("CHANNEL".equals(chat.getChatType())) {
            return "VIDEO".equals(normalizedKind) ? "LIVE_STREAM" : "VOICE_CHAT";
        }
        return participantCount > 2 || !"DIRECT".equals(chat.getChatType()) ? "GROUP" : "DIRECT";
    }

    private String defaultJoinLinkMode(String normalizedKind) {
        return "VIDEO".equals(normalizedKind) ? "LIVE_STREAM" : "VOICE_CHAT";
    }

    private CallSessionEntity createCallSession(
            ChatEntity chat,
            UUID requesterId,
            String kind,
            String mode,
            boolean recordingEnabled,
            Instant now
    ) {
        List<UUID> participantIds = chatMemberRepository.findAllByIdChatId(chat.getId()).stream()
                .map(member -> member.getId().getUserId())
                .sorted(Comparator.naturalOrder())
                .toList();
        if (participantIds.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Call requires at least two participants");
        }

        CallSessionEntity session = new CallSessionEntity();
        session.setChatId(chat.getId());
        session.setCreatedByUserId(requesterId);
        session.setKind(normalizeKind(kind));
        session.setMode(normalizeMode(chat, mode, kind, participantIds.size()));
        session.setStatus(List.of("VOICE_CHAT", "LIVE_STREAM").contains(session.getMode()) ? "ACTIVE" : "RINGING");
        session.setStartedAt(now);
        session.setRecordingEnabled(recordingEnabled);
        session.setRecordingStartedAt(recordingEnabled ? now : null);
        CallSessionEntity savedSession = callSessionRepository.save(session);

        callParticipantRepository.saveAll(participantIds.stream()
                .map(userId -> newParticipant(
                        savedSession.getId(),
                        userId,
                        userId.equals(requesterId),
                        savedSession.getMode(),
                        now
                ))
                .toList());
        return savedSession;
    }

    private void joinParticipantState(CallSessionEntity session, UUID requesterId, Instant now) {
        CallParticipantEntity participant = callParticipantRepository.findById(new CallParticipantId(session.getId(), requesterId))
                .orElseGet(() -> newParticipant(session.getId(), requesterId, true, session.getMode(), now));
        participant.setState("JOINED");
        participant.setJoinedAt(now);
        participant.setLeftAt(null);
        participant.setHandRaised(false);
        callParticipantRepository.save(participant);

        if (!"ACTIVE".equals(session.getStatus())) {
            session.setStatus("ACTIVE");
        }
        if (session.getAnsweredAt() == null) {
            session.setAnsweredAt(now);
        }
        callSessionRepository.save(session);
    }

    private void reconcileSessionAfterParticipantExit(CallSessionEntity session, Instant now) {
        long joinedCount = countParticipantsByStates(session.getId(), List.of("JOINED"));
        long ringingCount = countParticipantsByStates(session.getId(), List.of("RINGING"));
        if ("DIRECT".equals(session.getMode()) || joinedCount == 0 || ("RINGING".equals(session.getStatus()) && ringingCount == 0)) {
            session.setStatus("ENDED");
            Instant endedAt = session.getEndedAt() != null ? session.getEndedAt() : now;
            session.setEndedAt(endedAt);
            markRemainingParticipantsMissed(session.getId(), endedAt);
            callSessionRepository.save(session);
        }
    }

    private void validateJoinLink(CallJoinLinkEntity link) {
        if (Boolean.TRUE.equals(link.getRevoked())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Call link has been revoked");
        }
        if (link.getExpiresAt() != null && !link.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Call link has expired");
        }
    }

    private void ensureJoinLinkMatchesSession(CallJoinLinkEntity link, CallSessionEntity session) {
        boolean modeMismatch = !Objects.equals(link.getMode(), session.getMode());
        boolean kindMismatch = !Objects.equals(link.getKind(), session.getKind());
        if (modeMismatch || kindMismatch) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Call link does not match the current live call");
        }
    }

    private void ensureCallLinksSupported(ChatEntity chat) {
        if (!List.of("GROUP", "CHANNEL").contains(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Call links are available only for groups and channels");
        }
    }

    private void ensureLiveJoinLinkMode(String mode) {
        if (!List.of("VOICE_CHAT", "LIVE_STREAM").contains(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Call links support live call modes only");
        }
    }

    private void ensureCanManageCallLinks(ChatEntity chat, UUID requesterId) {
        ChatMemberEntity membership = chatService.getMembership(chat.getId(), requesterId);
        if (!canManageCallLinks(chat, membership)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Call link management is not allowed for this member");
        }
    }

    private void ensureCanModerateCall(UUID requesterId, CallSessionEntity session) {
        if (requesterId.equals(session.getCreatedByUserId())) {
            return;
        }
        if (!chatService.hasMessageModerationPermission(requesterId, session.getChatId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Call moderation is not allowed for this member");
        }
    }

    private void ensureCanStartManagedLiveCall(ChatEntity chat, UUID requesterId, String mode) {
        if ("CHANNEL".equals(chat.getChatType()) || List.of("VOICE_CHAT", "LIVE_STREAM").contains(mode)) {
            ensureCanManageCallLinks(chat, requesterId);
        }
    }

    private boolean canManageCallLinks(ChatEntity chat, ChatMemberEntity membership) {
        if (chat == null || membership == null || !List.of("GROUP", "CHANNEL").contains(chat.getChatType())) {
            return false;
        }
        return "OWNER".equals(membership.getRole())
                || Boolean.TRUE.equals(membership.getCanManageInviteLinks())
                || Boolean.TRUE.equals(membership.getCanManageMessages());
    }

    private boolean canModerateCall(CallSessionEntity session, ChatMemberEntity membership, UUID viewerId) {
        return viewerId != null
                && (viewerId.equals(session.getCreatedByUserId())
                || (membership != null && ("OWNER".equals(membership.getRole())
                || Boolean.TRUE.equals(membership.getCanManageMessages()))));
    }

    private String normalizeLabel(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeJoinLinkToken(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("alex://call/")) {
            normalized = normalized.substring("alex://call/".length());
        }
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Call link token is required");
        }
        return normalized;
    }

    private CallParticipantEntity newParticipant(UUID callId, UUID userId, boolean initiator, String mode, Instant now) {
        CallParticipantEntity participant = new CallParticipantEntity();
        participant.setId(new CallParticipantId(callId, userId));
        participant.setState(initiator ? "JOINED" : (List.of("VOICE_CHAT", "LIVE_STREAM").contains(mode) ? "INVITED" : "RINGING"));
        participant.setInvitedAt(now);
        if (initiator) {
            participant.setJoinedAt(now);
        }
        participant.setAudioPublishingAllowed(true);
        participant.setVideoPublishingAllowed(true);
        participant.setScreenShareAllowed(true);
        participant.setScreenSharing(false);
        participant.setHandRaised(false);
        participant.setAudioMuted(!initiator);
        participant.setMutedByModerator(false);
        return participant;
    }

    private String generateJoinLinkToken() {
        byte[] bytes = new byte[18];
        String token;
        do {
            secureRandom.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (callJoinLinkRepository.existsByToken(token));
        return token;
    }

    private CallSessionResponse toResponse(CallSessionEntity session, UUID viewerId) {
        List<CallParticipantEntity> participants = callParticipantRepository.findAllByIdCallId(session.getId());
        Map<UUID, UserEntity> usersById = loadCallUsers(participants);
        ChatEntity chat = chatRepository.findById(session.getChatId()).orElse(null);
        ChatMemberEntity viewerMembership = viewerId == null
                ? null
                : chatMemberRepository.findById(new ChatMemberId(session.getChatId(), viewerId)).orElse(null);
        return buildResponse(session, participants, usersById, chat, viewerMembership, viewerId);
    }

    private Map<UUID, UserEntity> loadCallUsers(List<CallParticipantEntity> participants) {
        return userRepository.findAllById(
                participants.stream().map(participant -> participant.getId().getUserId()).toList()
        ).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    }

    private CallSessionResponse buildResponse(
            CallSessionEntity session,
            List<CallParticipantEntity> participants,
            Map<UUID, UserEntity> usersById,
            ChatEntity chat,
            ChatMemberEntity viewerMembership,
            UUID viewerId
    ) {
        return new CallSessionResponse(
                session.getId(),
                session.getChatId(),
                session.getCreatedByUserId(),
                session.getKind(),
                session.getMode(),
                session.getStatus(),
                session.getStartedAt(),
                session.getAnsweredAt(),
                session.getEndedAt(),
                Boolean.TRUE.equals(session.getRecordingEnabled()),
                session.getRecordingStartedAt(),
                canModerateCall(session, viewerMembership, viewerId),
                canManageCallLinks(chat, viewerMembership),
                participants.stream()
                        .sorted(Comparator.comparing(participant -> participant.getId().getUserId()))
                        .map(participant -> {
                            UserEntity user = usersById.get(participant.getId().getUserId());
                            PhotoAccess photoAccess = user != null
                                    ? profilePhotoService.buildPhotoAccess(
                                            user.getPhotoStorageProvider(),
                                            user.getPhotoBucketName(),
                                            user.getPhotoObjectKey()
                                    )
                                    : null;
                            return new CallParticipantResponse(
                                    participant.getId().getUserId(),
                                    user != null ? user.getDisplayName() : "Unknown",
                                    user != null ? user.getPhoneNumber() : null,
                                    photoAccess != null ? photoAccess.photoUrl() : null,
                                    photoAccess != null ? photoAccess.photoAccessExpiresAt() : null,
                                    participant.getState(),
                                    participant.getInvitedAt(),
                                    participant.getJoinedAt(),
                                    participant.getLeftAt(),
                                    Boolean.TRUE.equals(participant.getAudioPublishingAllowed()),
                                    Boolean.TRUE.equals(participant.getVideoPublishingAllowed()),
                                    Boolean.TRUE.equals(participant.getScreenShareAllowed()),
                                    Boolean.TRUE.equals(participant.getScreenSharing()),
                                    Boolean.TRUE.equals(participant.getHandRaised()),
                                    Boolean.TRUE.equals(participant.getAudioMuted()),
                                    Boolean.TRUE.equals(participant.getMutedByModerator()),
                                    participant.getMutedByUserId(),
                                    participant.getMutedAt(),
                                    participant.getModeratedByUserId(),
                                    participant.getModeratedAt()
                            );
                        })
                        .toList()
        );
    }

    private CallHistoryEntryResponse toHistoryEntry(
            CallSessionEntity session,
            UUID requesterId,
            ChatEntity chat,
            List<CallParticipantEntity> participants,
            Map<UUID, UserEntity> directPeersById
    ) {
        CallParticipantEntity requesterParticipant = participants.stream()
                .filter(participant -> participant.getId().getUserId().equals(requesterId))
                .findFirst()
                .orElse(null);
        String direction = requesterId.equals(session.getCreatedByUserId()) ? "OUTGOING" : "INCOMING";
        boolean missed = "INCOMING".equals(direction)
                && requesterParticipant != null
                && "MISSED".equals(requesterParticipant.getState());

        String chatType = chat != null ? chat.getChatType() : "DIRECT";
        String title;
        String photoUrl = null;
        Instant photoAccessExpiresAt = null;
        if ("DIRECT".equals(chatType) && chat != null) {
            UUID peerUserId = resolveDirectPeerId(chat, requesterId);
            UserEntity peer = directPeersById.get(peerUserId);
            PhotoAccess peerPhotoAccess = peer != null ? buildUserPhotoAccess(peer) : null;
            title = peer != null ? peer.getDisplayName() : "Direct Call";
            if (peerPhotoAccess != null) {
                photoUrl = peerPhotoAccess.photoUrl();
                photoAccessExpiresAt = peerPhotoAccess.photoAccessExpiresAt();
            }
        } else if ("SAVED".equals(chatType)) {
            title = "Saved Messages";
        } else {
            title = chat != null && chat.getTitle() != null
                    ? chat.getTitle()
                    : ("CHANNEL".equals(chatType) ? "Untitled Channel" : "Untitled Group");
            PhotoAccess chatPhotoAccess = chat != null ? buildChatPhotoAccess(chat) : null;
            if (chatPhotoAccess != null) {
                photoUrl = chatPhotoAccess.photoUrl();
                photoAccessExpiresAt = chatPhotoAccess.photoAccessExpiresAt();
            }
        }

        return new CallHistoryEntryResponse(
                session.getId(),
                session.getChatId(),
                chatType,
                title,
                photoUrl,
                photoAccessExpiresAt,
                session.getKind(),
                session.getMode(),
                session.getStatus(),
                direction,
                missed,
                participants.size(),
                session.getStartedAt(),
                session.getAnsweredAt(),
                session.getEndedAt()
        );
    }

    private CallJoinLinkResponse toJoinLinkResponse(CallJoinLinkEntity link) {
        return new CallJoinLinkResponse(
                link.getId(),
                link.getChatId(),
                link.getCreatedByUserId(),
                link.getKind(),
                link.getMode(),
                link.getLabel(),
                link.getToken(),
                "alex://call/" + link.getToken(),
                Boolean.TRUE.equals(link.getRevoked()),
                link.getUsageCount() != null ? link.getUsageCount() : 0,
                link.getExpiresAt(),
                link.getCreatedAt(),
                link.getLastUsedAt()
        );
    }

    private PhotoAccess buildChatPhotoAccess(ChatEntity chat) {
        return profilePhotoService.buildPhotoAccess(
                chat.getPhotoStorageProvider(),
                chat.getPhotoBucketName(),
                chat.getPhotoObjectKey()
        );
    }

    private PhotoAccess buildUserPhotoAccess(UserEntity user) {
        return profilePhotoService.buildPhotoAccess(
                user.getPhotoStorageProvider(),
                user.getPhotoBucketName(),
                user.getPhotoObjectKey()
        );
    }

    private UUID resolveDirectPeerId(ChatEntity chat, UUID requesterId) {
        if (chat.getParticipantLowId() == null || chat.getParticipantHighId() == null) {
            return null;
        }
        return chat.getParticipantLowId().equals(requesterId)
                ? chat.getParticipantHighId()
                : chat.getParticipantLowId();
    }
}
