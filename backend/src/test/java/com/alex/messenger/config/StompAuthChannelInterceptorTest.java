package com.alex.messenger.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.auth.JwtService;
import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private ChatService chatService;

    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(jwtService, userSessionService, chatService);
    }

    @Test
    void connectAuthenticatesActiveSession() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtService.extractUserId("token")).thenReturn(userId);
        when(jwtService.extractSessionId("token")).thenReturn(sessionId);
        when(userSessionService.isActive(sessionId, userId)).thenReturn(true);

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isNotNull();
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo(userId.toString());
        verify(userSessionService).touch(sessionId, userId);
    }

    @Test
    void subscribeAllowsOwnedChatTopic() {
        UUID userId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);

        when(chatService.getOwnedChat(userId, chatId)).thenReturn(chat);

        Message<?> result = interceptor.preSend(
                subscribeMessage(userId, "/topic/chats/" + chatId + "/reads"),
                null
        );

        assertThat(result).isNotNull();
    }

    @Test
    void subscribeRejectsForeignChatTopic() {
        UUID userId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        when(chatService.getOwnedChat(userId, chatId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied"));

        Message<?> result = interceptor.preSend(
                subscribeMessage(userId, "/topic/chats/" + chatId + "/pins"),
                null
        );

        assertThat(result).isNull();
    }

    @Test
    void subscribeAllowsAuthenticatedUserQueue() {
        UUID userId = UUID.randomUUID();

        Message<?> result = interceptor.preSend(
                subscribeMessage(userId, "/user/queue/messages"),
                null
        );

        assertThat(result).isNotNull();
    }

    @Test
    void subscribeAllowsAuthenticatedStoryEventsQueue() {
        UUID userId = UUID.randomUUID();

        Message<?> result = interceptor.preSend(
                subscribeMessage(userId, "/user/queue/story-events"),
                null
        );

        assertThat(result).isNotNull();
    }

    @Test
    void subscribeAllowsAuthenticatedCallsQueue() {
        UUID userId = UUID.randomUUID();

        Message<?> result = interceptor.preSend(
                subscribeMessage(userId, "/user/queue/calls"),
                null
        );

        assertThat(result).isNotNull();
    }

    @Test
    void subscribeAllowsAuthenticatedSecretChatsQueue() {
        UUID userId = UUID.randomUUID();

        Message<?> result = interceptor.preSend(
                subscribeMessage(userId, "/user/queue/secret-chats"),
                null
        );

        assertThat(result).isNotNull();
    }

    @Test
    void subscribeRejectsUnknownUserQueue() {
        UUID userId = UUID.randomUUID();

        Message<?> result = interceptor.preSend(
                subscribeMessage(userId, "/user/queue/internal-audit"),
                null
        );

        assertThat(result).isNull();
    }

    private Message<byte[]> subscribeMessage(UUID userId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(userId.toString(), "token"));
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
