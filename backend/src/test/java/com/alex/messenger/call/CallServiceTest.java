package com.alex.messenger.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.call.dto.CreateCallCommentRequest;
import com.alex.messenger.call.dto.CreateCallReactionRequest;
import com.alex.messenger.call.dto.CallSignalRequest;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CallServiceTest {

    @Mock
    private CallSessionRepository callSessionRepository;

    @Mock
    private CallParticipantRepository callParticipantRepository;

    @Mock
    private CallJoinLinkRepository callJoinLinkRepository;

    @Mock
    private CallCommentRepository callCommentRepository;

    @Mock
    private CallReactionRepository callReactionRepository;

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private ChatMemberRepository chatMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfilePhotoService profilePhotoService;

    @Mock
    private CallRealtimeService callRealtimeService;

    private CallService callService;

    @BeforeEach
    void setUp() {
        callService = new CallService(
                callSessionRepository,
                callParticipantRepository,
                callJoinLinkRepository,
                callCommentRepository,
                callReactionRepository,
                chatRepository,
                chatService,
                chatMemberRepository,
                userRepository,
                profilePhotoService,
                callRealtimeService
        );
    }

    @Test
    void startCallSupportsLiveStreamModeAndRecording() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID thirdUserId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        UserEntity requester = user(requesterId, "Requester");
        UserEntity secondUser = user(secondUserId, "Second");
        UserEntity thirdUser = user(thirdUserId, "Third");
        ChatMemberEntity requesterMembership = member(chatId, requesterId);
        requesterMembership.setRole("ADMIN");
        requesterMembership.setCanManageInviteLinks(true);
        List<ChatMemberEntity> members = List.of(
                requesterMembership,
                member(chatId, secondUserId),
                member(chatId, thirdUserId)
        );
        AtomicReference<List<CallParticipantEntity>> savedParticipants = new AtomicReference<>(new ArrayList<>());

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(chatService.getMembership(chatId, requesterId)).thenReturn(requesterMembership);
        when(callSessionRepository.existsByChatIdAndStatusIn(eq(chatId), anyCollection())).thenReturn(false);
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(members);
        when(callSessionRepository.save(any(CallSessionEntity.class))).thenAnswer(invocation -> {
            CallSessionEntity session = invocation.getArgument(0);
            session.setId(callId);
            session.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
            return session;
        });
        when(callParticipantRepository.saveAll(any())).thenAnswer(invocation -> {
            List<CallParticipantEntity> participants = new ArrayList<>();
            for (Object item : invocation.getArgument(0, Iterable.class)) {
                participants.add((CallParticipantEntity) item);
            }
            savedParticipants.set(participants);
            return participants;
        });
        when(callParticipantRepository.findAllByIdCallId(callId)).thenAnswer(invocation -> savedParticipants.get());
        when(userRepository.findAllById(any())).thenReturn(List.of(requester, secondUser, thirdUser));
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));

        var response = callService.startCall(
                requesterId,
                new com.alex.messenger.call.dto.StartCallRequest(chatId, "VIDEO", "LIVE_STREAM", true)
        );

        assertThat(response.callId()).isEqualTo(callId);
        assertThat(response.mode()).isEqualTo("LIVE_STREAM");
        assertThat(response.recordingEnabled()).isTrue();
        assertThat(response.recordingStartedAt()).isNotNull();
        assertThat(response.participants()).hasSize(3);
        assertThat(response.participants())
                .extracting(com.alex.messenger.call.dto.CallParticipantResponse::state)
                .containsExactlyInAnyOrder("JOINED", "INVITED", "INVITED");
    }

    @Test
    void startCallRejectsLiveStreamWithoutCallManagementPermission() {
        UUID requesterId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        ChatMemberEntity requesterMembership = member(chatId, requesterId);
        List<ChatMemberEntity> members = List.of(
                requesterMembership,
                member(chatId, secondUserId)
        );

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(chatService.getMembership(chatId, requesterId)).thenReturn(requesterMembership);
        when(callSessionRepository.existsByChatIdAndStatusIn(eq(chatId), anyCollection())).thenReturn(false);
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(members);

        assertThatThrownBy(() -> callService.startCall(
                requesterId,
                new com.alex.messenger.call.dto.StartCallRequest(chatId, "VIDEO", "LIVE_STREAM", false)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(callSessionRepository, never()).save(any(CallSessionEntity.class));
    }

    @Test
    void startCallRejectsGroupModeInChannelChat() {
        UUID requesterId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "CHANNEL");
        ChatMemberEntity requesterMembership = member(chatId, requesterId);
        requesterMembership.setCanManageInviteLinks(true);
        List<ChatMemberEntity> members = List.of(
                requesterMembership,
                member(chatId, secondUserId)
        );

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(callSessionRepository.existsByChatIdAndStatusIn(eq(chatId), anyCollection())).thenReturn(false);
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(members);

        assertThatThrownBy(() -> callService.startCall(
                requesterId,
                new com.alex.messenger.call.dto.StartCallRequest(chatId, "VOICE", "GROUP", false)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(callSessionRepository, never()).save(any(CallSessionEntity.class));
    }

    @Test
    void startCallDefaultsChannelVideoToLiveStream() {
        UUID requesterId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "CHANNEL");
        UserEntity requester = user(requesterId, "Requester");
        UserEntity secondUser = user(secondUserId, "Second");
        ChatMemberEntity requesterMembership = member(chatId, requesterId);
        requesterMembership.setRole("OWNER");
        requesterMembership.setCanManageInviteLinks(true);
        List<ChatMemberEntity> members = List.of(
                requesterMembership,
                member(chatId, secondUserId)
        );
        AtomicReference<List<CallParticipantEntity>> savedParticipants = new AtomicReference<>(new ArrayList<>());

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(chatService.getMembership(chatId, requesterId)).thenReturn(requesterMembership);
        when(callSessionRepository.existsByChatIdAndStatusIn(eq(chatId), anyCollection())).thenReturn(false);
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(members);
        when(callSessionRepository.save(any(CallSessionEntity.class))).thenAnswer(invocation -> {
            CallSessionEntity session = invocation.getArgument(0);
            session.setId(callId);
            session.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
            return session;
        });
        when(callParticipantRepository.saveAll(any())).thenAnswer(invocation -> {
            List<CallParticipantEntity> participants = new ArrayList<>();
            for (Object item : invocation.getArgument(0, Iterable.class)) {
                participants.add((CallParticipantEntity) item);
            }
            savedParticipants.set(participants);
            return participants;
        });
        when(callParticipantRepository.findAllByIdCallId(callId)).thenAnswer(invocation -> savedParticipants.get());
        when(userRepository.findAllById(any())).thenReturn(List.of(requester, secondUser));
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(members);

        var response = callService.startCall(
                requesterId,
                new com.alex.messenger.call.dto.StartCallRequest(chatId, "VIDEO", null, false)
        );

        assertThat(response.callId()).isEqualTo(callId);
        assertThat(response.mode()).isEqualTo("LIVE_STREAM");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void createJoinLinkDefaultsChannelVoiceToVoiceChat() {
        UUID requesterId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "CHANNEL");
        ChatMemberEntity requesterMembership = member(chatId, requesterId);
        requesterMembership.setCanManageInviteLinks(true);
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(chatService.getMembership(chatId, requesterId)).thenReturn(requesterMembership);
        when(callJoinLinkRepository.save(any(CallJoinLinkEntity.class))).thenAnswer(invocation -> {
            CallJoinLinkEntity link = invocation.getArgument(0);
            link.setId(UUID.randomUUID());
            link.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
            return link;
        });

        var response = callService.createJoinLink(
                requesterId,
                new com.alex.messenger.call.dto.CreateCallJoinLinkRequest(chatId, "VOICE", null, "Townhall", null)
        );

        assertThat(response.mode()).isEqualTo("VOICE_CHAT");
        assertThat(response.shareUrl()).startsWith("alex://call/");
    }

    @Test
    void createJoinLinkDefaultsGroupVideoToLiveStream() {
        UUID requesterId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        ChatMemberEntity requesterMembership = member(chatId, requesterId);
        requesterMembership.setCanManageInviteLinks(true);
        List<ChatMemberEntity> members = List.of(
                requesterMembership,
                member(chatId, secondUserId)
        );

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(chatService.getMembership(chatId, requesterId)).thenReturn(requesterMembership);
        when(callJoinLinkRepository.save(any(CallJoinLinkEntity.class))).thenAnswer(invocation -> {
            CallJoinLinkEntity link = invocation.getArgument(0);
            link.setId(UUID.randomUUID());
            link.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
            return link;
        });

        var response = callService.createJoinLink(
                requesterId,
                new com.alex.messenger.call.dto.CreateCallJoinLinkRequest(chatId, "VIDEO", null, null, null)
        );

        assertThat(response.mode()).isEqualTo("LIVE_STREAM");
    }

    @Test
    void createJoinLinkRejectsNonLiveMode() {
        UUID requesterId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        ChatMemberEntity requesterMembership = member(chatId, requesterId);
        requesterMembership.setCanManageInviteLinks(true);
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(chatService.getMembership(chatId, requesterId)).thenReturn(requesterMembership);

        assertThatThrownBy(() -> callService.createJoinLink(
                requesterId,
                new com.alex.messenger.call.dto.CreateCallJoinLinkRequest(chatId, "VOICE", "GROUP", null, null)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(callJoinLinkRepository, never()).save(any(CallJoinLinkEntity.class));
    }

    @Test
    void joinByLinkRejectsLegacyNonLiveMode() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        CallJoinLinkEntity link = new CallJoinLinkEntity();
        link.setChatId(chatId);
        link.setToken("join-token");
        link.setKind("VOICE");
        link.setMode("GROUP");
        link.setRevoked(false);

        when(callJoinLinkRepository.findByToken("join-token")).thenReturn(Optional.of(link));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);

        assertThatThrownBy(() -> callService.joinByLink(requesterId, "join-token"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(callSessionRepository, never()).save(any(CallSessionEntity.class));
    }

    @Test
    void raiseHandRejectedForDirectCall() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        CallSessionEntity session = new CallSessionEntity();
        session.setId(callId);
        session.setChatId(chatId);
        session.setMode("DIRECT");
        session.setStatus("ACTIVE");

        when(callSessionRepository.findById(callId)).thenReturn(Optional.of(session));
        when(callParticipantRepository.existsByIdCallIdAndIdUserId(callId, requesterId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "DIRECT"));

        assertThatThrownBy(() -> callService.setHandRaised(requesterId, callId, true))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void moderateParticipantRejectsEndedCall() {
        UUID requesterId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        CallSessionEntity session = new CallSessionEntity();
        session.setId(callId);
        session.setChatId(chatId);
        session.setCreatedByUserId(requesterId);
        session.setStatus("ENDED");

        when(callSessionRepository.findById(callId)).thenReturn(Optional.of(session));
        when(callParticipantRepository.existsByIdCallIdAndIdUserId(callId, requesterId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "GROUP"));

        assertThatThrownBy(() -> callService.moderateParticipant(
                requesterId,
                callId,
                targetUserId,
                new UpdateCallParticipantModerationRequest(true, null, null, null, null)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void sendSignalRejectsEndedCall() {
        UUID requesterId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        CallSessionEntity session = new CallSessionEntity();
        session.setId(callId);
        session.setChatId(chatId);
        session.setStatus("ENDED");

        when(callSessionRepository.findById(callId)).thenReturn(Optional.of(session));
        when(callParticipantRepository.existsByIdCallIdAndIdUserId(callId, requesterId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "GROUP"));

        assertThatThrownBy(() -> callService.sendSignal(
                requesterId,
                callId,
                new CallSignalRequest(targetUserId, "offer", "{\"sdp\":\"x\"}")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode().value()).isEqualTo(409));

        verify(callRealtimeService, never()).publishSignalEvent(any(), any());
    }

    @Test
    void sendSignalRejectsTerminalParticipants() {
        UUID requesterId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        CallSessionEntity session = new CallSessionEntity();
        session.setId(callId);
        session.setChatId(chatId);
        session.setStatus("ACTIVE");

        CallParticipantEntity sender = participant(callId, requesterId, "JOINED");
        CallParticipantEntity target = participant(callId, targetUserId, "LEFT");

        when(callSessionRepository.findById(callId)).thenReturn(Optional.of(session));
        when(callParticipantRepository.existsByIdCallIdAndIdUserId(callId, requesterId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "GROUP"));
        when(callParticipantRepository.findById(new CallParticipantId(callId, requesterId))).thenReturn(Optional.of(sender));
        when(callParticipantRepository.findById(new CallParticipantId(callId, targetUserId))).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> callService.sendSignal(
                requesterId,
                callId,
                new CallSignalRequest(targetUserId, "candidate", "{\"candidate\":\"x\"}")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode().value()).isEqualTo(409));

        verify(callRealtimeService, never()).publishSignalEvent(any(), any());
    }

    @Test
    void sendSignalRejectsTargetWithoutCurrentChatMembership() {
        UUID requesterId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        CallSessionEntity session = new CallSessionEntity();
        session.setId(callId);
        session.setChatId(chatId);
        session.setStatus("ACTIVE");

        CallParticipantEntity sender = participant(callId, requesterId, "JOINED");
        CallParticipantEntity target = participant(callId, targetUserId, "JOINED");

        when(callSessionRepository.findById(callId)).thenReturn(Optional.of(session));
        when(callParticipantRepository.existsByIdCallIdAndIdUserId(callId, requesterId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "GROUP"));
        when(callParticipantRepository.findById(new CallParticipantId(callId, requesterId))).thenReturn(Optional.of(sender));
        when(callParticipantRepository.findById(new CallParticipantId(callId, targetUserId))).thenReturn(Optional.of(target));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, targetUserId)).thenReturn(false);

        assertThatThrownBy(() -> callService.sendSignal(
                requesterId,
                callId,
                new CallSignalRequest(targetUserId, "candidate", "{\"candidate\":\"x\"}")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode().value()).isEqualTo(404));

        verify(callRealtimeService, never()).publishSignalEvent(any(), any());
    }

    @Test
    void joinByLinkRejectsMismatchedLiveSession() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        CallJoinLinkEntity link = new CallJoinLinkEntity();
        link.setChatId(chatId);
        link.setToken("join-token");
        link.setKind("VIDEO");
        link.setMode("LIVE_STREAM");
        link.setRevoked(false);

        CallSessionEntity activeSession = new CallSessionEntity();
        activeSession.setId(callId);
        activeSession.setChatId(chatId);
        activeSession.setKind("VOICE");
        activeSession.setMode("VOICE_CHAT");
        activeSession.setStatus("ACTIVE");

        when(callJoinLinkRepository.findByToken("join-token")).thenReturn(Optional.of(link));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(callSessionRepository.findFirstByChatIdAndStatusInOrderByStartedAtDesc(eq(chatId), anyCollection()))
                .thenReturn(activeSession);

        assertThatThrownBy(() -> callService.joinByLink(requesterId, "join-token"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void joinByLinkRejectsBootstrappingLiveSessionWithoutCallManagementPermission() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        ChatMemberEntity requesterMembership = member(chatId, requesterId);

        CallJoinLinkEntity link = new CallJoinLinkEntity();
        link.setChatId(chatId);
        link.setToken("join-token");
        link.setKind("VIDEO");
        link.setMode("LIVE_STREAM");
        link.setRevoked(false);

        when(callJoinLinkRepository.findByToken("join-token")).thenReturn(Optional.of(link));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(chatService.getMembership(chatId, requesterId)).thenReturn(requesterMembership);
        when(callSessionRepository.findFirstByChatIdAndStatusInOrderByStartedAtDesc(eq(chatId), anyCollection()))
                .thenReturn(null);

        assertThatThrownBy(() -> callService.joinByLink(requesterId, "join-token"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(callSessionRepository, never()).save(any(CallSessionEntity.class));
    }

    @Test
    void getActiveCallsSkipsCallsForTerminalParticipantStates() {
        UUID requesterId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID activeChatId = UUID.randomUUID();
        UUID staleChatId = UUID.randomUUID();
        UUID activeCallId = UUID.randomUUID();
        UUID staleCallId = UUID.randomUUID();

        CallSessionEntity activeSession = new CallSessionEntity();
        activeSession.setId(activeCallId);
        activeSession.setChatId(activeChatId);
        activeSession.setCreatedByUserId(requesterId);
        activeSession.setKind("VOICE");
        activeSession.setMode("VOICE_CHAT");
        activeSession.setStatus("ACTIVE");
        activeSession.setStartedAt(Instant.parse("2026-03-14T10:00:00Z"));

        CallSessionEntity staleSession = new CallSessionEntity();
        staleSession.setId(staleCallId);
        staleSession.setChatId(staleChatId);
        staleSession.setCreatedByUserId(secondUserId);
        staleSession.setKind("VOICE");
        staleSession.setMode("VOICE_CHAT");
        staleSession.setStatus("ACTIVE");
        staleSession.setStartedAt(Instant.parse("2026-03-14T09:00:00Z"));

        ChatEntity activeChat = chat(activeChatId, "GROUP");
        UserEntity requester = user(requesterId, "Requester");
        UserEntity secondUser = user(secondUserId, "Second");

        when(callSessionRepository.findByParticipantAndStatuses(requesterId, List.of("RINGING", "ACTIVE")))
                .thenReturn(List.of(activeSession, staleSession));
        when(chatMemberRepository.findAllByIdUserId(requesterId))
                .thenReturn(List.of(member(activeChatId, requesterId), member(staleChatId, requesterId)));
        when(callParticipantRepository.findAllByIdUserIdAndIdCallIdIn(
                requesterId,
                List.of(activeCallId, staleCallId)
        )).thenReturn(List.of(
                participant(activeCallId, requesterId, "JOINED"),
                participant(staleCallId, requesterId, "LEFT")
        ));
        when(callParticipantRepository.findAllByIdCallId(activeCallId)).thenReturn(List.of(
                participant(activeCallId, requesterId, "JOINED"),
                participant(activeCallId, secondUserId, "JOINED")
        ));
        when(userRepository.findAllById(any())).thenReturn(List.of(requester, secondUser));
        when(chatRepository.findById(activeChatId)).thenReturn(Optional.of(activeChat));
        when(chatMemberRepository.findAllByIdChatId(activeChatId)).thenReturn(List.of(
                member(activeChatId, requesterId),
                member(activeChatId, secondUserId)
        ));

        var activeCalls = callService.getActiveCalls(requesterId);

        assertThat(activeCalls).hasSize(1);
        assertThat(activeCalls.get(0).callId()).isEqualTo(activeCallId);
        verify(callParticipantRepository, never()).findAllByIdCallId(staleCallId);
    }

    @Test
    void getActiveCallsSkipsChatsWithoutCurrentMembership() {
        UUID requesterId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID visibleChatId = UUID.randomUUID();
        UUID removedChatId = UUID.randomUUID();
        UUID visibleCallId = UUID.randomUUID();
        UUID removedCallId = UUID.randomUUID();

        CallSessionEntity visibleSession = session(visibleCallId, visibleChatId, requesterId, "ACTIVE", "VOICE_CHAT");
        CallSessionEntity removedSession = session(removedCallId, removedChatId, secondUserId, "ACTIVE", "VOICE_CHAT");
        UserEntity requester = user(requesterId, "Requester");
        UserEntity secondUser = user(secondUserId, "Second");

        when(callSessionRepository.findByParticipantAndStatuses(requesterId, List.of("RINGING", "ACTIVE")))
                .thenReturn(List.of(visibleSession, removedSession));
        when(chatMemberRepository.findAllByIdUserId(requesterId))
                .thenReturn(List.of(member(visibleChatId, requesterId)));
        when(callParticipantRepository.findAllByIdUserIdAndIdCallIdIn(
                requesterId,
                List.of(visibleCallId, removedCallId)
        )).thenReturn(List.of(
                participant(visibleCallId, requesterId, "JOINED"),
                participant(removedCallId, requesterId, "JOINED")
        ));
        when(callParticipantRepository.findAllByIdCallId(visibleCallId)).thenReturn(List.of(
                participant(visibleCallId, requesterId, "JOINED"),
                participant(visibleCallId, secondUserId, "JOINED")
        ));
        when(userRepository.findAllById(any())).thenReturn(List.of(requester, secondUser));
        when(chatRepository.findById(visibleChatId)).thenReturn(Optional.of(chat(visibleChatId, "GROUP")));
        when(chatMemberRepository.findAllByIdChatId(visibleChatId)).thenReturn(List.of(
                member(visibleChatId, requesterId),
                member(visibleChatId, secondUserId)
        ));

        var activeCalls = callService.getActiveCalls(requesterId);

        assertThat(activeCalls).extracting(com.alex.messenger.call.dto.CallSessionResponse::callId)
                .containsExactly(visibleCallId);
        verify(callParticipantRepository, never()).findAllByIdCallId(removedCallId);
    }

    @Test
    void getActiveCallsFiltersParticipantsWithoutCurrentMembership() {
        UUID requesterId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID removedUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        CallSessionEntity session = session(callId, chatId, requesterId, "ACTIVE", "VOICE_CHAT");
        UserEntity requester = user(requesterId, "Requester");
        UserEntity secondUser = user(secondUserId, "Second");

        when(callSessionRepository.findByParticipantAndStatuses(requesterId, List.of("RINGING", "ACTIVE")))
                .thenReturn(List.of(session));
        when(chatMemberRepository.findAllByIdUserId(requesterId))
                .thenReturn(List.of(member(chatId, requesterId)));
        when(callParticipantRepository.findAllByIdUserIdAndIdCallIdIn(requesterId, List.of(callId)))
                .thenReturn(List.of(participant(callId, requesterId, "JOINED")));
        when(callParticipantRepository.findAllByIdCallId(callId)).thenReturn(List.of(
                participant(callId, requesterId, "JOINED"),
                participant(callId, secondUserId, "JOINED"),
                participant(callId, removedUserId, "JOINED")
        ));
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat(chatId, "GROUP")));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(
                member(chatId, requesterId),
                member(chatId, secondUserId)
        ));
        when(userRepository.findAllById(any())).thenReturn(List.of(requester, secondUser));
        when(profilePhotoService.buildPhotoAccess(any(), any(), any())).thenReturn(new PhotoAccess(null, null));

        var activeCalls = callService.getActiveCalls(requesterId);

        assertThat(activeCalls).hasSize(1);
        assertThat(activeCalls.get(0).participants())
                .extracting(com.alex.messenger.call.dto.CallParticipantResponse::userId)
                .containsExactlyInAnyOrder(requesterId, secondUserId);
    }

    @Test
    void declineCallDoesNotEndVoiceChatWhileHostRemainsJoined() {
        UUID requesterId = UUID.randomUUID();
        UUID hostUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        UserEntity requester = user(requesterId, "Requester");
        UserEntity host = user(hostUserId, "Host");
        CallSessionEntity session = new CallSessionEntity();
        session.setId(callId);
        session.setChatId(chatId);
        session.setCreatedByUserId(hostUserId);
        session.setKind("VOICE");
        session.setMode("VOICE_CHAT");
        session.setStatus("ACTIVE");
        session.setStartedAt(Instant.parse("2026-03-14T10:00:00Z"));

        AtomicReference<List<CallParticipantEntity>> participantsRef = new AtomicReference<>(new ArrayList<>(List.of(
                participant(callId, hostUserId, "JOINED"),
                participant(callId, requesterId, "INVITED")
        )));

        when(callSessionRepository.findById(callId)).thenReturn(Optional.of(session));
        when(callParticipantRepository.existsByIdCallIdAndIdUserId(callId, requesterId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(callParticipantRepository.findById(new CallParticipantId(callId, requesterId)))
                .thenAnswer(invocation -> participantsRef.get().stream()
                        .filter(participant -> participant.getId().getUserId().equals(requesterId))
                        .findFirst());
        when(callParticipantRepository.save(any(CallParticipantEntity.class))).thenAnswer(invocation -> {
            CallParticipantEntity saved = invocation.getArgument(0);
            List<CallParticipantEntity> updated = new ArrayList<>(participantsRef.get());
            for (int index = 0; index < updated.size(); index++) {
                if (updated.get(index).getId().equals(saved.getId())) {
                    updated.set(index, saved);
                }
            }
            participantsRef.set(updated);
            return saved;
        });
        when(callParticipantRepository.countByIdCallIdAndStateIn(callId, List.of("JOINED"))).thenReturn(1L);
        when(callParticipantRepository.countByIdCallIdAndStateIn(callId, List.of("RINGING"))).thenReturn(0L);
        when(callParticipantRepository.findAllByIdCallId(callId)).thenAnswer(invocation -> participantsRef.get());
        when(userRepository.findAllById(any())).thenReturn(List.of(requester, host));
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(
                member(chatId, requesterId),
                member(chatId, hostUserId)
        ));

        var response = callService.declineCall(requesterId, callId);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(participantsRef.get()).extracting(CallParticipantEntity::getState)
                .containsExactlyInAnyOrder("JOINED", "DECLINED");
    }

    @Test
    void declineCallRejectsJoinedParticipant() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        CallSessionEntity session = session(callId, chatId, UUID.randomUUID(), "ACTIVE", "VOICE_CHAT");
        CallParticipantEntity participant = participant(callId, requesterId, "JOINED");

        when(callSessionRepository.findById(callId)).thenReturn(Optional.of(session));
        when(callParticipantRepository.existsByIdCallIdAndIdUserId(callId, requesterId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "GROUP"));
        when(callParticipantRepository.findById(new CallParticipantId(callId, requesterId))).thenReturn(Optional.of(participant));

        ResponseStatusException exception = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> callService.declineCall(requesterId, callId),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(callParticipantRepository, never()).save(any(CallParticipantEntity.class));
    }

    @Test
    void leaveCallMarksInvitedParticipantsMissedWhenVoiceChatEnds() {
        UUID requesterId = UUID.randomUUID();
        UUID invitedUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        UserEntity requester = user(requesterId, "Requester");
        UserEntity invited = user(invitedUserId, "Invited");
        CallSessionEntity session = new CallSessionEntity();
        session.setId(callId);
        session.setChatId(chatId);
        session.setCreatedByUserId(requesterId);
        session.setKind("VOICE");
        session.setMode("VOICE_CHAT");
        session.setStatus("ACTIVE");
        session.setStartedAt(Instant.parse("2026-03-14T10:00:00Z"));

        AtomicReference<List<CallParticipantEntity>> participantsRef = new AtomicReference<>(new ArrayList<>(List.of(
                participant(callId, requesterId, "JOINED"),
                participant(callId, invitedUserId, "INVITED")
        )));

        when(callSessionRepository.findById(callId)).thenReturn(Optional.of(session));
        when(callParticipantRepository.existsByIdCallIdAndIdUserId(callId, requesterId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(callParticipantRepository.findById(new CallParticipantId(callId, requesterId)))
                .thenAnswer(invocation -> participantsRef.get().stream()
                        .filter(participant -> participant.getId().getUserId().equals(requesterId))
                        .findFirst());
        when(callParticipantRepository.save(any(CallParticipantEntity.class))).thenAnswer(invocation -> {
            CallParticipantEntity saved = invocation.getArgument(0);
            List<CallParticipantEntity> updated = new ArrayList<>(participantsRef.get());
            for (int index = 0; index < updated.size(); index++) {
                if (updated.get(index).getId().equals(saved.getId())) {
                    updated.set(index, saved);
                }
            }
            participantsRef.set(updated);
            return saved;
        });
        when(callParticipantRepository.findAllByIdCallId(callId)).thenAnswer(invocation -> participantsRef.get());
        when(callParticipantRepository.saveAll(any())).thenAnswer(invocation -> {
            List<CallParticipantEntity> updated = new ArrayList<>();
            for (Object item : invocation.getArgument(0, Iterable.class)) {
                updated.add((CallParticipantEntity) item);
            }
            participantsRef.set(updated);
            return updated;
        });
        when(callParticipantRepository.countByIdCallIdAndStateIn(callId, List.of("JOINED"))).thenReturn(0L);
        when(callParticipantRepository.countByIdCallIdAndStateIn(callId, List.of("RINGING"))).thenReturn(0L);
        when(userRepository.findAllById(any())).thenReturn(List.of(requester, invited));
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(
                member(chatId, requesterId),
                member(chatId, invitedUserId)
        ));

        var response = callService.leaveCall(requesterId, callId);

        assertThat(response.status()).isEqualTo("ENDED");
        assertThat(participantsRef.get()).extracting(CallParticipantEntity::getState)
                .containsExactlyInAnyOrder("LEFT", "MISSED");
    }

    @Test
    void leaveCallRejectsParticipantWhoNeverJoined() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        CallSessionEntity session = session(callId, chatId, UUID.randomUUID(), "ACTIVE", "VOICE_CHAT");
        CallParticipantEntity participant = participant(callId, requesterId, "INVITED");

        when(callSessionRepository.findById(callId)).thenReturn(Optional.of(session));
        when(callParticipantRepository.existsByIdCallIdAndIdUserId(callId, requesterId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "GROUP"));
        when(callParticipantRepository.findById(new CallParticipantId(callId, requesterId))).thenReturn(Optional.of(participant));

        ResponseStatusException exception = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> callService.leaveCall(requesterId, callId),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(callParticipantRepository, never()).save(any(CallParticipantEntity.class));
    }

    @Test
    void leaveCallDoesNotMutateEndedCall() {
        UUID requesterId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        CallSessionEntity session = session(callId, chatId, UUID.randomUUID(), "ENDED", "VOICE_CHAT");
        session.setEndedAt(Instant.parse("2026-03-14T10:05:00Z"));

        CallParticipantEntity requesterParticipant = participant(callId, requesterId, "JOINED");
        CallParticipantEntity secondParticipant = participant(callId, secondUserId, "LEFT");
        ChatEntity chat = chat(chatId, "GROUP");
        UserEntity requester = user(requesterId, "Requester");
        UserEntity secondUser = user(secondUserId, "Second");

        when(callSessionRepository.findById(callId)).thenReturn(Optional.of(session));
        when(callParticipantRepository.existsByIdCallIdAndIdUserId(callId, requesterId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(callParticipantRepository.findAllByIdCallId(callId)).thenReturn(List.of(requesterParticipant, secondParticipant));
        when(userRepository.findAllById(any())).thenReturn(List.of(requester, secondUser));
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(
                member(chatId, requesterId),
                member(chatId, secondUserId)
        ));

        var response = callService.leaveCall(requesterId, callId);

        assertThat(response.status()).isEqualTo("ENDED");
        assertThat(response.participants()).extracting(com.alex.messenger.call.dto.CallParticipantResponse::state)
                .containsExactlyInAnyOrder("JOINED", "LEFT");
        verify(callParticipantRepository, never()).findById(new CallParticipantId(callId, requesterId));
        verify(callParticipantRepository, never()).save(any(CallParticipantEntity.class));
    }

    @Test
    void setScreenSharingPublishesUpdatesOnlyToCurrentChatMembers() {
        UUID requesterId = UUID.randomUUID();
        UUID removedUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        CallSessionEntity session = session(callId, chatId, requesterId, "ACTIVE", "VOICE_CHAT");
        CallParticipantEntity requesterParticipant = participant(callId, requesterId, "JOINED");
        ChatEntity chat = chat(chatId, "GROUP");
        UserEntity requester = user(requesterId, "Requester");
        UserEntity removedUser = user(removedUserId, "Removed");

        when(callSessionRepository.findById(callId)).thenReturn(Optional.of(session));
        when(callParticipantRepository.existsByIdCallIdAndIdUserId(callId, requesterId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(callParticipantRepository.findById(new CallParticipantId(callId, requesterId))).thenReturn(Optional.of(requesterParticipant));
        when(callParticipantRepository.save(any(CallParticipantEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(callParticipantRepository.findAllByIdCallId(callId)).thenReturn(List.of(
                requesterParticipant,
                participant(callId, removedUserId, "JOINED")
        ));
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(member(chatId, requesterId)));
        when(userRepository.findAllById(any())).thenReturn(List.of(requester, removedUser));

        var response = callService.setScreenSharing(requesterId, callId, true);

        assertThat(response.participants()).extracting(com.alex.messenger.call.dto.CallParticipantResponse::userId)
                .containsExactly(requesterId);
        verify(callRealtimeService).publishSessionEvent(eq(requesterId), eq("UPDATED"), any());
        verify(callRealtimeService, never()).publishSessionEvent(eq(removedUserId), eq("UPDATED"), any());
    }

    @Test
    void getRecentCallsExcludesCallsThatAreStillActiveForViewer() {
        UUID requesterId = UUID.randomUUID();
        UUID joinedChatId = UUID.randomUUID();
        UUID leftChatId = UUID.randomUUID();
        UUID endedChatId = UUID.randomUUID();
        UUID joinedCallId = UUID.randomUUID();
        UUID leftCallId = UUID.randomUUID();
        UUID endedCallId = UUID.randomUUID();

        CallSessionEntity joinedSession = session(joinedCallId, joinedChatId, requesterId, "ACTIVE", "VOICE_CHAT");
        joinedSession.setStartedAt(Instant.parse("2026-03-14T10:05:00Z"));
        CallSessionEntity leftSession = session(leftCallId, leftChatId, requesterId, "ACTIVE", "VOICE_CHAT");
        leftSession.setStartedAt(Instant.parse("2026-03-14T10:00:00Z"));
        CallSessionEntity endedSession = session(endedCallId, endedChatId, requesterId, "ENDED", "DIRECT");
        endedSession.setEndedAt(Instant.parse("2026-03-14T09:55:00Z"));

        when(callSessionRepository.findRecentByParticipant(eq(requesterId), any())).thenReturn(List.of(
                joinedSession,
                leftSession,
                endedSession
        ));
        when(chatMemberRepository.findAllByIdUserId(requesterId))
                .thenReturn(List.of(
                        member(joinedChatId, requesterId),
                        member(leftChatId, requesterId),
                        member(endedChatId, requesterId)
                ));
        when(callParticipantRepository.findAllByIdCallIdIn(List.of(joinedCallId, leftCallId, endedCallId))).thenReturn(List.of(
                participant(joinedCallId, requesterId, "JOINED"),
                participant(leftCallId, requesterId, "LEFT"),
                participant(endedCallId, requesterId, "MISSED")
        ));
        when(chatRepository.findAllById(List.of(leftChatId, endedChatId))).thenReturn(List.of(
                chat(leftChatId, "GROUP"),
                chat(endedChatId, "GROUP")
        ));
        when(userRepository.findAllById(List.of())).thenReturn(List.of());
        when(profilePhotoService.buildPhotoAccess(any(), any(), any())).thenReturn(new PhotoAccess(null, null));

        var recentCalls = callService.getRecentCalls(requesterId, 2);

        assertThat(recentCalls).extracting(com.alex.messenger.call.dto.CallHistoryEntryResponse::callId)
                .containsExactly(leftCallId, endedCallId);
    }

    @Test
    void getRecentCallsSkipsChatsWithoutCurrentMembership() {
        UUID requesterId = UUID.randomUUID();
        UUID visibleChatId = UUID.randomUUID();
        UUID removedChatId = UUID.randomUUID();
        UUID visibleCallId = UUID.randomUUID();
        UUID removedCallId = UUID.randomUUID();

        CallSessionEntity visibleSession = session(visibleCallId, visibleChatId, requesterId, "ENDED", "DIRECT");
        visibleSession.setEndedAt(Instant.parse("2026-03-14T10:00:00Z"));
        CallSessionEntity removedSession = session(removedCallId, removedChatId, requesterId, "ENDED", "DIRECT");
        removedSession.setEndedAt(Instant.parse("2026-03-14T09:55:00Z"));

        when(callSessionRepository.findRecentByParticipant(eq(requesterId), any())).thenReturn(List.of(
                visibleSession,
                removedSession
        ));
        when(chatMemberRepository.findAllByIdUserId(requesterId))
                .thenReturn(List.of(member(visibleChatId, requesterId)));
        when(callParticipantRepository.findAllByIdCallIdIn(List.of(visibleCallId))).thenReturn(List.of(
                participant(visibleCallId, requesterId, "MISSED")
        ));
        when(chatRepository.findAllById(List.of(visibleChatId))).thenReturn(List.of(chat(visibleChatId, "GROUP")));
        when(userRepository.findAllById(List.of())).thenReturn(List.of());
        when(profilePhotoService.buildPhotoAccess(any(), any(), any())).thenReturn(new PhotoAccess(null, null));

        var recentCalls = callService.getRecentCalls(requesterId, 10);

        assertThat(recentCalls).extracting(com.alex.messenger.call.dto.CallHistoryEntryResponse::callId)
                .containsExactly(visibleCallId);
    }

    @Test
    void setAudioMutedRejectsSelfUnmuteWhenModeratorMutedParticipant() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        CallSessionEntity session = session(callId, chatId, UUID.randomUUID(), "ACTIVE", "VOICE_CHAT");
        CallParticipantEntity participant = participant(callId, requesterId, "JOINED");
        participant.setAudioMuted(true);
        participant.setMutedByModerator(true);
        participant.setMutedByUserId(UUID.randomUUID());
        participant.setMutedAt(Instant.parse("2026-03-14T10:03:00Z"));

        when(callSessionRepository.findById(callId)).thenReturn(Optional.of(session));
        when(callParticipantRepository.existsByIdCallIdAndIdUserId(callId, requesterId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "GROUP"));
        when(callParticipantRepository.findById(new CallParticipantId(callId, requesterId))).thenReturn(Optional.of(participant));

        ResponseStatusException exception = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> callService.setAudioMuted(requesterId, callId, false),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(callParticipantRepository, never()).save(any(CallParticipantEntity.class));
    }

    @Test
    void createCommentPublishesToActiveParticipants() {
        UUID requesterId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        CallSessionEntity session = session(callId, chatId, requesterId, "ACTIVE", "VOICE_CHAT");
        CallParticipantEntity requesterParticipant = participant(callId, requesterId, "JOINED");
        CallParticipantEntity secondParticipant = participant(callId, secondUserId, "JOINED");
        CallCommentEntity savedComment = new CallCommentEntity();
        savedComment.setId(commentId);
        savedComment.setCallId(callId);
        savedComment.setChatId(chatId);
        savedComment.setAuthorUserId(requesterId);
        savedComment.setContent("Need another moderator in the call");
        savedComment.setCreatedAt(Instant.parse("2026-03-19T10:15:00Z"));

        when(callSessionRepository.findById(callId)).thenReturn(Optional.of(session));
        when(callParticipantRepository.existsByIdCallIdAndIdUserId(callId, requesterId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "GROUP"));
        when(callParticipantRepository.findById(new CallParticipantId(callId, requesterId)))
                .thenReturn(Optional.of(requesterParticipant));
        when(callCommentRepository.save(any(CallCommentEntity.class))).thenReturn(savedComment);
        when(callParticipantRepository.findAllByIdCallId(callId)).thenReturn(List.of(requesterParticipant, secondParticipant));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(
                member(chatId, requesterId),
                member(chatId, secondUserId)
        ));
        when(userRepository.findAllById(List.of(requesterId))).thenReturn(List.of(user(requesterId, "Requester")));
        when(profilePhotoService.buildPhotoAccess(any(), any(), any())).thenReturn(new PhotoAccess(null, null));

        var response = callService.createComment(
                requesterId,
                callId,
                new CreateCallCommentRequest("  Need another moderator in the call  ")
        );

        assertThat(response.commentId()).isEqualTo(commentId);
        assertThat(response.content()).isEqualTo("Need another moderator in the call");
        verify(callRealtimeService).publishCommentEvent(requesterId, response);
        verify(callRealtimeService).publishCommentEvent(secondUserId, response);
    }

    @Test
    void createCommentRejectsDirectCallMode() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        CallSessionEntity session = session(callId, chatId, requesterId, "ACTIVE", "DIRECT");
        CallParticipantEntity requesterParticipant = participant(callId, requesterId, "JOINED");

        when(callSessionRepository.findById(callId)).thenReturn(Optional.of(session));
        when(callParticipantRepository.existsByIdCallIdAndIdUserId(callId, requesterId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "DIRECT"));
        when(callParticipantRepository.findById(new CallParticipantId(callId, requesterId)))
                .thenReturn(Optional.of(requesterParticipant));

        assertThatThrownBy(() -> callService.createComment(
                requesterId,
                callId,
                new CreateCallCommentRequest("hello")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(callCommentRepository, never()).save(any(CallCommentEntity.class));
    }

    @Test
    void createReactionPublishesToActiveParticipants() {
        UUID requesterId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();
        UUID reactionId = UUID.randomUUID();

        CallSessionEntity session = session(callId, chatId, requesterId, "ACTIVE", "VOICE_CHAT");
        CallParticipantEntity requesterParticipant = participant(callId, requesterId, "JOINED");
        CallParticipantEntity secondParticipant = participant(callId, secondUserId, "JOINED");
        CallReactionEntity savedReaction = new CallReactionEntity();
        savedReaction.setId(reactionId);
        savedReaction.setCallId(callId);
        savedReaction.setChatId(chatId);
        savedReaction.setAuthorUserId(requesterId);
        savedReaction.setEmoji("🔥");
        savedReaction.setCreatedAt(Instant.parse("2026-03-19T10:20:00Z"));

        when(callSessionRepository.findById(callId)).thenReturn(Optional.of(session));
        when(callParticipantRepository.existsByIdCallIdAndIdUserId(callId, requesterId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "GROUP"));
        when(callParticipantRepository.findById(new CallParticipantId(callId, requesterId)))
                .thenReturn(Optional.of(requesterParticipant));
        when(callReactionRepository.save(any(CallReactionEntity.class))).thenReturn(savedReaction);
        when(callParticipantRepository.findAllByIdCallId(callId)).thenReturn(List.of(requesterParticipant, secondParticipant));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(
                member(chatId, requesterId),
                member(chatId, secondUserId)
        ));
        when(userRepository.findAllById(List.of(requesterId))).thenReturn(List.of(user(requesterId, "Requester")));
        when(profilePhotoService.buildPhotoAccess(any(), any(), any())).thenReturn(new PhotoAccess(null, null));

        var response = callService.createReaction(
                requesterId,
                callId,
                new CreateCallReactionRequest(" 🔥 ")
        );

        assertThat(response.reactionId()).isEqualTo(reactionId);
        assertThat(response.emoji()).isEqualTo("🔥");
        verify(callRealtimeService).publishReactionEvent(requesterId, response);
        verify(callRealtimeService).publishReactionEvent(secondUserId, response);
    }

    @Test
    void createReactionRejectsDirectCallMode() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID callId = UUID.randomUUID();

        CallSessionEntity session = session(callId, chatId, requesterId, "ACTIVE", "DIRECT");
        CallParticipantEntity requesterParticipant = participant(callId, requesterId, "JOINED");

        when(callSessionRepository.findById(callId)).thenReturn(Optional.of(session));
        when(callParticipantRepository.existsByIdCallIdAndIdUserId(callId, requesterId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "DIRECT"));
        when(callParticipantRepository.findById(new CallParticipantId(callId, requesterId)))
                .thenReturn(Optional.of(requesterParticipant));

        assertThatThrownBy(() -> callService.createReaction(
                requesterId,
                callId,
                new CreateCallReactionRequest("🔥")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(callReactionRepository, never()).save(any(CallReactionEntity.class));
    }
    private ChatEntity chat(UUID chatId, String type) {
        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType(type);
        chat.setTitle("Call chat");
        return chat;
    }

    private CallSessionEntity session(UUID callId, UUID chatId, UUID createdByUserId, String status, String mode) {
        CallSessionEntity session = new CallSessionEntity();
        session.setId(callId);
        session.setChatId(chatId);
        session.setCreatedByUserId(createdByUserId);
        session.setKind("VOICE");
        session.setMode(mode);
        session.setStatus(status);
        session.setStartedAt(Instant.parse("2026-03-14T09:00:00Z"));
        return session;
    }

    private ChatMemberEntity member(UUID chatId, UUID userId) {
        ChatMemberEntity member = new ChatMemberEntity();
        member.setId(new ChatMemberId(chatId, userId));
        member.setRole("MEMBER");
        member.setCanManageMessages(false);
        member.setCanManageInviteLinks(false);
        return member;
    }

    private CallParticipantEntity participant(UUID callId, UUID userId, String state) {
        CallParticipantEntity participant = new CallParticipantEntity();
        participant.setId(new CallParticipantId(callId, userId));
        participant.setState(state);
        participant.setInvitedAt(Instant.parse("2026-03-14T10:00:00Z"));
        participant.setJoinedAt("JOINED".equals(state) ? Instant.parse("2026-03-14T10:01:00Z") : null);
        participant.setAudioPublishingAllowed(true);
        participant.setVideoPublishingAllowed(true);
        participant.setScreenShareAllowed(true);
        participant.setScreenSharing(false);
        participant.setHandRaised(false);
        participant.setAudioMuted(false);
        participant.setMutedByModerator(false);
        return participant;
    }

    private UserEntity user(UUID userId, String displayName) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setDisplayName(displayName);
        return user;
    }
}
