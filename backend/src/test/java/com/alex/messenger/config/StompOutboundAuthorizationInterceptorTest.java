package com.alex.messenger.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.dto.ChatReadEventResponse;
import com.alex.messenger.chat.dto.TypingEventResponse;
import com.alex.messenger.chat.forum.ForumTopicService;
import com.alex.messenger.message.MessageLookupEntity;
import com.alex.messenger.message.MessageLookupRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StompOutboundAuthorizationInterceptorTest {

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private ChatService chatService;

    @Mock
    private ForumTopicService forumTopicService;

    @Mock
    private MessageLookupRepository messageLookupRepository;

    private StompOutboundAuthorizationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompOutboundAuthorizationInterceptor(
                userSessionService,
                chatService,
                forumTopicService,
                messageLookupRepository
        );
    }

    @Test
    void outboundChatTopicMessageAllowsOwnedChat() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);

        when(userSessionService.isActive(sessionId, userId)).thenReturn(true);
        when(chatService.getOwnedChat(userId, chatId)).thenReturn(chat);

        Message<?> result = interceptor.preSend(
                outboundMessage(userId, sessionId, "/topic/chats/" + chatId + "/typing", new byte[0]),
                null
        );

        assertThat(result).isNotNull();
    }

    @Test
    void outboundChatTopicMessageRejectsForeignChat() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        when(userSessionService.isActive(sessionId, userId)).thenReturn(true);
        when(chatService.getOwnedChat(userId, chatId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied"));

        Message<?> result = interceptor.preSend(
                outboundMessage(userId, sessionId, "/topic/chats/" + chatId + "/reads", new byte[0]),
                null
        );

        assertThat(result).isNull();
    }

    @Test
    void outboundUserQueueAllowsActiveSession() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        when(userSessionService.isActive(sessionId, userId)).thenReturn(true);

        Message<?> result = interceptor.preSend(
                outboundMessage(userId, sessionId, "/user/queue/messages", new byte[0]),
                null
        );

        assertThat(result).isNotNull();
    }

    @Test
    void outboundUserQueueRejectsRevokedSession() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        when(userSessionService.isActive(sessionId, userId)).thenReturn(false);

        Message<?> result = interceptor.preSend(
                outboundMessage(userId, sessionId, "/user/queue/messages", new byte[0]),
                null
        );

        assertThat(result).isNull();
    }

    @Test
    void outboundStoryEventsQueueAllowsActiveSession() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        when(userSessionService.isActive(sessionId, userId)).thenReturn(true);

        Message<?> result = interceptor.preSend(
                outboundMessage(userId, sessionId, "/user/queue/story-events", new byte[0]),
                null
        );

        assertThat(result).isNotNull();
    }

    @Test
    void outboundCallsQueueRejectsRevokedSession() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        when(userSessionService.isActive(sessionId, userId)).thenReturn(false);

        Message<?> result = interceptor.preSend(
                outboundMessage(userId, sessionId, "/user/queue/calls", new byte[0]),
                null
        );

        assertThat(result).isNull();
    }

    @Test
    void outboundSecretChatsQueueAllowsActiveSession() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        when(userSessionService.isActive(sessionId, userId)).thenReturn(true);

        Message<?> result = interceptor.preSend(
                outboundMessage(userId, sessionId, "/user/queue/secret-chats", new byte[0]),
                null
        );

        assertThat(result).isNotNull();
    }

    @Test
    void outboundUnknownUserQueueIsRejected() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        when(userSessionService.isActive(sessionId, userId)).thenReturn(true);

        Message<?> result = interceptor.preSend(
                outboundMessage(userId, sessionId, "/user/queue/internal-audit", new byte[0]),
                null
        );

        assertThat(result).isNull();
    }

    @Test
    void outboundOtherDestinationPassesThrough() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Message<?> result = interceptor.preSend(
                outboundMessage(userId, sessionId, "/topic/system/health", new byte[0]),
                null
        );

        assertThat(result).isNotNull();
    }

    @Test
    void outboundTypingTopicMessageRejectsHiddenTopic() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setForumEnabled(true);

        when(userSessionService.isActive(sessionId, userId)).thenReturn(true);
        when(chatService.getOwnedChat(userId, chatId)).thenReturn(chat);
        when(forumTopicService.resolveTopicForRead(chat, userId, topicId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found"));

        Message<?> result = interceptor.preSend(
                outboundMessage(
                        userId,
                        sessionId,
                        "/topic/chats/" + chatId + "/typing",
                        new TypingEventResponse(chatId, userId, true, topicId, Instant.parse("2026-03-14T17:00:00Z"))
                ),
                null
        );

        assertThat(result).isNull();
    }

    @Test
    void outboundReadMessageRejectsHiddenTopic() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setForumEnabled(true);

        MessageLookupEntity lookup = new MessageLookupEntity();
        lookup.setMessageId(messageId);
        lookup.setChatId(chatId);
        lookup.setTopicId(topicId);

        when(userSessionService.isActive(sessionId, userId)).thenReturn(true);
        when(chatService.getOwnedChat(userId, chatId)).thenReturn(chat);
        when(messageLookupRepository.findById(messageId)).thenReturn(java.util.Optional.of(lookup));
        when(forumTopicService.resolveTopicForRead(chat, userId, topicId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found"));

        Message<?> result = interceptor.preSend(
                outboundMessage(
                        userId,
                        sessionId,
                        "/topic/chats/" + chatId + "/reads",
                        new ChatReadEventResponse(chatId, userId, messageId, Instant.parse("2026-03-14T17:05:00Z"))
                ),
                null
        );

        assertThat(result).isNull();
    }

    private Message<?> outboundMessage(UUID userId, UUID sessionId, String destination, Object payload) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setDestination(destination);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId.toString(), "token");
        authentication.setDetails(sessionId.toString());
        accessor.setUser(authentication);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
    }
}
