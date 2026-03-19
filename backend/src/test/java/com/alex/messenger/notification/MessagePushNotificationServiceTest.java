package com.alex.messenger.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.attachment.AttachmentService;
import com.alex.messenger.auth.session.PushSessionTarget;
import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatMemberEntity;
import com.alex.messenger.chat.ChatMemberId;
import com.alex.messenger.chat.ChatMemberRepository;
import com.alex.messenger.chat.ChatRepository;
import com.alex.messenger.chat.forum.ForumTopicService;
import com.alex.messenger.message.MessageEvent;
import com.alex.messenger.message.MessageTextContent;
import com.alex.messenger.message.dto.MessageLiveLocationPayload;
import com.alex.messenger.message.dto.MessageServicePayload;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MessagePushNotificationServiceTest {

    @Mock
    private PushNotificationService pushNotificationService;

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatMemberRepository chatMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private ForumTopicService forumTopicService;

    private MessagePushNotificationService messagePushNotificationService;

    @BeforeEach
    void setUp() {
        messagePushNotificationService = new MessagePushNotificationService(
                pushNotificationService,
                userSessionService,
                chatRepository,
                chatMemberRepository,
                userRepository,
                attachmentService,
                forumTopicService
        );
    }

    @Test
    void notifyNewMessageSkipsRecipientWithoutTopicAccess() {
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        MessageEvent event = new MessageEvent(
                chatId,
                messageId,
                null,
                senderId,
                List.of(recipientId),
                null,
                topicId,
                null,
                null,
                null,
                Instant.parse("2026-03-14T16:00:00Z"),
                "ciphertext",
                "nonce",
                1,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "SENT",
                null,
                null,
                null,
                null,
                null
        );

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setForumEnabled(true);
        chat.setTitle("Forum group");

        UserEntity sender = new UserEntity();
        sender.setId(senderId);
        sender.setDisplayName("Alice");

        ChatMemberEntity membership = new ChatMemberEntity();
        membership.setId(new ChatMemberId(chatId, recipientId));

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(membership));
        when(forumTopicService.resolveTopicForRead(chat, recipientId, topicId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found"));

        messagePushNotificationService.notifyNewMessage(
                event,
                new MessageTextContent("hello", List.of()),
                List.of()
        );

        verify(userSessionService, never()).getPushTargets(recipientId);
        verify(pushNotificationService).send(argThat(List::isEmpty));
    }

    @Test
    void notifyNewMessageBuildsLiveLocationPreview() {
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        MessageEvent event = new MessageEvent(
                chatId,
                messageId,
                null,
                senderId,
                List.of(recipientId),
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-03-19T16:00:00Z"),
                "ciphertext",
                "nonce",
                1,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "SENT",
                null,
                null,
                null,
                null,
                null
        );

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setTitle("Travel");

        UserEntity sender = new UserEntity();
        sender.setId(senderId);
        sender.setDisplayName("Alice");

        ChatMemberEntity membership = new ChatMemberEntity();
        membership.setId(new ChatMemberId(chatId, recipientId));

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(membership));
        when(userSessionService.getPushTargets(recipientId)).thenReturn(List.of(
                new PushSessionTarget(UUID.randomUUID(), "EXPO", "ExponentPushToken[live]")
        ));

        messagePushNotificationService.notifyNewMessage(
                event,
                new MessageTextContent(
                        "",
                        List.of(),
                        "LIVE_LOCATION",
                        null,
                        null,
                        new MessageLiveLocationPayload(53.9, 27.56, "Downtown", null, 1800, null, null, null, true),
                        null,
                        null,
                        false
                ),
                List.of()
        );

        ArgumentCaptor<List> commandsCaptor = ArgumentCaptor.forClass(List.class);
        verify(pushNotificationService).send(commandsCaptor.capture());
        PushNotificationCommand command = (PushNotificationCommand) commandsCaptor.getValue().get(0);
        assertThat(command.title()).isEqualTo("Travel");
        assertThat(command.body()).isEqualTo("Alice: is sharing live location: Downtown");
        assertThat(command.data()).containsEntry("chatId", chatId.toString());
        assertThat(command.data()).containsEntry("messageId", messageId.toString());
    }

    @Test
    void notifyNewMessageFallsBackForServiceMessageWithoutText() {
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        MessageEvent event = new MessageEvent(
                chatId,
                messageId,
                null,
                senderId,
                List.of(recipientId),
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-03-19T16:10:00Z"),
                "ciphertext",
                "nonce",
                1,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "SENT",
                null,
                null,
                null,
                null,
                null
        );

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setTitle("Ops");

        UserEntity sender = new UserEntity();
        sender.setId(senderId);
        sender.setDisplayName("Alice");

        ChatMemberEntity membership = new ChatMemberEntity();
        membership.setId(new ChatMemberId(chatId, recipientId));

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(membership));
        when(userSessionService.getPushTargets(recipientId)).thenReturn(List.of(
                new PushSessionTarget(UUID.randomUUID(), "EXPO", "ExponentPushToken[service]")
        ));

        messagePushNotificationService.notifyNewMessage(
                event,
                new MessageTextContent(
                        null,
                        List.of(),
                        "SERVICE_MESSAGE",
                        null,
                        null,
                        null,
                        null,
                        new MessageServicePayload("CALL_ENDED", null),
                        false
                ),
                List.of()
        );

        ArgumentCaptor<List> commandsCaptor = ArgumentCaptor.forClass(List.class);
        verify(pushNotificationService).send(commandsCaptor.capture());
        PushNotificationCommand command = (PushNotificationCommand) commandsCaptor.getValue().get(0);
        assertThat(command.title()).isEqualTo("Ops");
        assertThat(command.body()).isEqualTo("Alice: sent a service update");
    }

    @Test
    void notifyNewMessageUsesGenericLiveLocationPreviewWithoutTitle() {
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        MessageEvent event = new MessageEvent(
                chatId,
                messageId,
                null,
                senderId,
                List.of(recipientId),
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-03-19T16:20:00Z"),
                "ciphertext",
                "nonce",
                1,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "SENT",
                null,
                null,
                null,
                null,
                null
        );

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setTitle("Travel");

        UserEntity sender = new UserEntity();
        sender.setId(senderId);
        sender.setDisplayName("Alice");

        ChatMemberEntity membership = new ChatMemberEntity();
        membership.setId(new ChatMemberId(chatId, recipientId));

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(membership));
        when(userSessionService.getPushTargets(recipientId)).thenReturn(List.of(
                new PushSessionTarget(UUID.randomUUID(), "EXPO", "ExponentPushToken[live-generic]")
        ));

        messagePushNotificationService.notifyNewMessage(
                event,
                new MessageTextContent(
                        "",
                        List.of(),
                        "LIVE_LOCATION",
                        null,
                        null,
                        new MessageLiveLocationPayload(53.9, 27.56, " ", null, 1800, null, null, null, true),
                        null,
                        null,
                        false
                ),
                List.of()
        );

        ArgumentCaptor<List> commandsCaptor = ArgumentCaptor.forClass(List.class);
        verify(pushNotificationService).send(commandsCaptor.capture());
        PushNotificationCommand command = (PushNotificationCommand) commandsCaptor.getValue().get(0);
        assertThat(command.body()).isEqualTo("Alice: is sharing live location");
    }

    @Test
    void notifyNewMessageFallsBackToSomeoneWhenSenderDisplayNameIsBlank() {
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        MessageEvent event = new MessageEvent(
                chatId,
                messageId,
                null,
                senderId,
                List.of(recipientId),
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-03-19T16:25:00Z"),
                "ciphertext",
                "nonce",
                1,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "SENT",
                null,
                null,
                null,
                null,
                null
        );

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("DIRECT");

        UserEntity sender = new UserEntity();
        sender.setId(senderId);
        sender.setDisplayName(" ");

        ChatMemberEntity membership = new ChatMemberEntity();
        membership.setId(new ChatMemberId(chatId, recipientId));

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(membership));
        when(userSessionService.getPushTargets(recipientId)).thenReturn(List.of(
                new PushSessionTarget(UUID.randomUUID(), "EXPO", "ExponentPushToken[direct]")
        ));

        messagePushNotificationService.notifyNewMessage(
                event,
                new MessageTextContent("hello", List.of()),
                List.of()
        );

        ArgumentCaptor<List> commandsCaptor = ArgumentCaptor.forClass(List.class);
        verify(pushNotificationService).send(commandsCaptor.capture());
        PushNotificationCommand command = (PushNotificationCommand) commandsCaptor.getValue().get(0);
        assertThat(command.title()).isEqualTo("Someone");
        assertThat(command.body()).isEqualTo("hello");
    }
}
