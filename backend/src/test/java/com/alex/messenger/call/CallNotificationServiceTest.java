package com.alex.messenger.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.auth.session.PushSessionTarget;
import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatMemberEntity;
import com.alex.messenger.chat.ChatMemberId;
import com.alex.messenger.chat.ChatMemberRepository;
import com.alex.messenger.chat.ChatRepository;
import com.alex.messenger.notification.PushNotificationCommand;
import com.alex.messenger.notification.PushNotificationService;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CallNotificationServiceTest {

    @Mock
    private PushNotificationService pushNotificationService;

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private CallParticipantRepository callParticipantRepository;

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatMemberRepository chatMemberRepository;

    @Mock
    private UserRepository userRepository;

    private CallNotificationService callNotificationService;

    @BeforeEach
    void setUp() {
        callNotificationService = new CallNotificationService(
                pushNotificationService,
                userSessionService,
                callParticipantRepository,
                chatRepository,
                chatMemberRepository,
                userRepository
        );
    }

    @Test
    void notifyStartedCallSendsPushOnlyToOfflineEligibleParticipants() {
        UUID callId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID offlineUserId = UUID.randomUUID();
        UUID onlineUserId = UUID.randomUUID();

        CallSessionEntity session = new CallSessionEntity();
        session.setId(callId);
        session.setChatId(chatId);
        session.setCreatedByUserId(requesterId);
        session.setKind("VIDEO");
        session.setMode("GROUP");
        session.setStatus("RINGING");
        session.setStartedAt(Instant.parse("2026-03-19T10:00:00Z"));

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setTitle("Town Hall");

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(
                member(chatId, requesterId),
                member(chatId, offlineUserId),
                member(chatId, onlineUserId)
        ));
        when(callParticipantRepository.findAllByIdCallId(callId)).thenReturn(List.of(
                participant(callId, requesterId, "JOINED"),
                participant(callId, offlineUserId, "RINGING"),
                participant(callId, onlineUserId, "RINGING")
        ));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                user(requesterId, "Requester"),
                user(offlineUserId, "Offline"),
                user(onlineUserId, "Online")
        ));
        when(userSessionService.isUserOnline(offlineUserId)).thenReturn(false);
        when(userSessionService.isUserOnline(onlineUserId)).thenReturn(true);
        when(userSessionService.getPushTargets(offlineUserId)).thenReturn(List.of(
                new PushSessionTarget(UUID.randomUUID(), "EXPO", "ExponentPushToken[offline]")
        ));

        callNotificationService.notifyStartedCall(session);

        ArgumentCaptor<List> commandsCaptor = ArgumentCaptor.forClass(List.class);
        verify(pushNotificationService).send(commandsCaptor.capture());
        List<?> commands = commandsCaptor.getValue();
        assertThat(commands).hasSize(1);
        PushNotificationCommand command = (PushNotificationCommand) commands.get(0);
        assertThat(command.title()).isEqualTo("Town Hall");
        assertThat(command.body()).isEqualTo("Requester started a group video call in Town Hall");
        assertThat(command.data()).containsEntry("callId", callId.toString());
        assertThat(command.data()).containsEntry("chatId", chatId.toString());
        verify(userSessionService, never()).getPushTargets(onlineUserId);
    }

    @Test
    void notifyStartedCallSkipsTerminalSessions() {
        CallSessionEntity session = new CallSessionEntity();
        session.setId(UUID.randomUUID());
        session.setChatId(UUID.randomUUID());
        session.setCreatedByUserId(UUID.randomUUID());
        session.setStatus("ENDED");

        callNotificationService.notifyStartedCall(session);

        verify(chatRepository, never()).findById(any());
        verify(pushNotificationService, never()).send(any());
    }

    @Test
    void notifyStartedCallSkipsMalformedSessionsWithoutModeOrChat() {
        CallSessionEntity missingMode = new CallSessionEntity();
        missingMode.setId(UUID.randomUUID());
        missingMode.setChatId(UUID.randomUUID());
        missingMode.setCreatedByUserId(UUID.randomUUID());
        missingMode.setStatus("RINGING");
        missingMode.setMode(null);

        CallSessionEntity missingChat = new CallSessionEntity();
        missingChat.setId(UUID.randomUUID());
        missingChat.setCreatedByUserId(UUID.randomUUID());
        missingChat.setStatus("RINGING");
        missingChat.setMode("DIRECT");

        callNotificationService.notifyStartedCall(missingMode);
        callNotificationService.notifyStartedCall(missingChat);

        verify(chatRepository, never()).findById(any());
        verify(pushNotificationService, never()).send(any());
    }

    @Test
    void notifyStartedDirectCallFallsBackToSomeoneForBlankInitiatorName() {
        UUID callId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();

        CallSessionEntity session = new CallSessionEntity();
        session.setId(callId);
        session.setChatId(chatId);
        session.setCreatedByUserId(requesterId);
        session.setKind("VOICE");
        session.setMode("DIRECT");
        session.setStatus("RINGING");

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("DIRECT");

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(
                member(chatId, requesterId),
                member(chatId, recipientUserId)
        ));
        when(callParticipantRepository.findAllByIdCallId(callId)).thenReturn(List.of(
                participant(callId, requesterId, "JOINED"),
                participant(callId, recipientUserId, "RINGING")
        ));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                user(requesterId, " "),
                user(recipientUserId, "Recipient")
        ));
        when(userSessionService.isUserOnline(recipientUserId)).thenReturn(false);
        when(userSessionService.getPushTargets(recipientUserId)).thenReturn(List.of(
                new PushSessionTarget(UUID.randomUUID(), "EXPO", "ExponentPushToken[direct]")
        ));

        callNotificationService.notifyStartedCall(session);

        ArgumentCaptor<List> commandsCaptor = ArgumentCaptor.forClass(List.class);
        verify(pushNotificationService).send(commandsCaptor.capture());
        PushNotificationCommand command = (PushNotificationCommand) commandsCaptor.getValue().get(0);
        assertThat(command.title()).isEqualTo("Someone");
        assertThat(command.body()).isEqualTo("Incoming voice call");
        assertThat(command.data()).containsEntry("mode", "DIRECT");
    }

    @Test
    void notifyStartedLiveStreamUsesFallbackChatTitle() {
        UUID callId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();

        CallSessionEntity session = new CallSessionEntity();
        session.setId(callId);
        session.setChatId(chatId);
        session.setCreatedByUserId(requesterId);
        session.setKind("VIDEO");
        session.setMode("LIVE_STREAM");
        session.setStatus("ACTIVE");

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("CHANNEL");
        chat.setTitle(" ");

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(
                member(chatId, requesterId),
                member(chatId, recipientUserId)
        ));
        when(callParticipantRepository.findAllByIdCallId(callId)).thenReturn(List.of(
                participant(callId, requesterId, "JOINED"),
                participant(callId, recipientUserId, "INVITED")
        ));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                user(requesterId, "Host"),
                user(recipientUserId, "Viewer")
        ));
        when(userSessionService.isUserOnline(recipientUserId)).thenReturn(false);
        when(userSessionService.getPushTargets(recipientUserId)).thenReturn(List.of(
                new PushSessionTarget(UUID.randomUUID(), "EXPO", "ExponentPushToken[live-stream]")
        ));

        callNotificationService.notifyStartedCall(session);

        ArgumentCaptor<List> commandsCaptor = ArgumentCaptor.forClass(List.class);
        verify(pushNotificationService).send(commandsCaptor.capture());
        PushNotificationCommand command = (PushNotificationCommand) commandsCaptor.getValue().get(0);
        assertThat(command.title()).isEqualTo("Live stream");
        assertThat(command.body()).isEqualTo("Host started a livestream in your channel");
        assertThat(command.data()).containsEntry("mode", "LIVE_STREAM");
        assertThat(command.data()).containsEntry("kind", "VIDEO");
    }

    private ChatMemberEntity member(UUID chatId, UUID userId) {
        ChatMemberEntity member = new ChatMemberEntity();
        member.setId(new ChatMemberId(chatId, userId));
        return member;
    }

    private CallParticipantEntity participant(UUID callId, UUID userId, String state) {
        CallParticipantEntity participant = new CallParticipantEntity();
        participant.setId(new CallParticipantId(callId, userId));
        participant.setState(state);
        participant.setInvitedAt(Instant.parse("2026-03-19T10:00:00Z"));
        return participant;
    }

    private UserEntity user(UUID userId, String displayName) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setDisplayName(displayName);
        return user;
    }
}
