package com.alex.messenger.config;

import com.alex.messenger.auth.JwtService;
import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.chat.ChatService;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern CHAT_TOPIC_DESTINATION = Pattern.compile("^/topic/chats/([0-9a-fA-F\\-]{36})/(reads|typing|pins)$");

    private final JwtService jwtService;
    private final UserSessionService userSessionService;
    private final ChatService chatService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        if (accessor.getCommand() == StompCommand.CONNECT) {
            return authenticateConnect(message, accessor);
        }
        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            return authorizeSubscribe(message, accessor);
        }
        return message;
    }

    private Message<?> authenticateConnect(Message<?> message, StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            UUID userId = jwtService.extractUserId(token);
            UUID sessionId = jwtService.extractSessionId(token);
            if (!userSessionService.isActive(sessionId, userId)) {
                return null;
            }
            userSessionService.touch(sessionId, userId);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId.toString(), token, List.of());
            authentication.setDetails(sessionId.toString());
            accessor.setUser(authentication);
        }

        return message;
    }

    private Message<?> authorizeSubscribe(Message<?> message, StompHeaderAccessor accessor) {
        UUID userId = extractAuthenticatedUserId(accessor);
        if (userId == null) {
            return null;
        }

        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            return null;
        }
        if (destination.startsWith("/user/queue/")) {
            if (!StompUserQueueDestinationPolicy.isAllowed(destination)) {
                return null;
            }
            return message;
        }

        Matcher matcher = CHAT_TOPIC_DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            return null;
        }

        try {
            chatService.getOwnedChat(userId, UUID.fromString(matcher.group(1)));
            return message;
        } catch (ResponseStatusException exception) {
            return null;
        }
    }

    private UUID extractAuthenticatedUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null || accessor.getUser().getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(accessor.getUser().getName());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
