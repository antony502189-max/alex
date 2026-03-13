package com.alex.messenger.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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
        List<ChatMemberEntity> members = List.of(
                member(chatId, requesterId),
                member(chatId, secondUserId),
                member(chatId, thirdUserId)
        );
        AtomicReference<List<CallParticipantEntity>> savedParticipants = new AtomicReference<>(new ArrayList<>());

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
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
        when(chatMemberRepository.findById(any())).thenReturn(Optional.of(member(chatId, requesterId)));
        when(profilePhotoService.buildPhotoAccess(any(), any(), any())).thenReturn(new PhotoAccess(null, null));

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

    private ChatEntity chat(UUID chatId, String type) {
        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType(type);
        chat.setTitle("Call chat");
        return chat;
    }

    private ChatMemberEntity member(UUID chatId, UUID userId) {
        ChatMemberEntity member = new ChatMemberEntity();
        member.setId(new ChatMemberId(chatId, userId));
        member.setRole("MEMBER");
        member.setCanManageMessages(false);
        member.setCanManageInviteLinks(false);
        return member;
    }

    private UserEntity user(UUID userId, String displayName) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setDisplayName(displayName);
        return user;
    }
}
