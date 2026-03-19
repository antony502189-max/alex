package com.alex.messenger.config;

import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.dto.ChatReadEventResponse;
import com.alex.messenger.chat.dto.PinMessageEventResponse;
import com.alex.messenger.chat.dto.TypingEventResponse;
import com.alex.messenger.chat.forum.ForumTopicService;
import com.alex.messenger.message.MessageLookupEntity;
import com.alex.messenger.message.MessageLookupRepository;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class StompOutboundAuthorizationInterceptor implements ChannelInterceptor {

    private static final Pattern CHAT_TOPIC_DESTINATION = Pattern.compile("^/topic/chats/([0-9a-fA-F\\-]{36})/(reads|typing|pins)$");

    private final UserSessionService userSessionService;
    private final ChatService chatService;
    private final ForumTopicService forumTopicService;
    private final MessageLookupRepository messageLookupRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        SimpMessageHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, SimpMessageHeaderAccessor.class);
        if (accessor == null || accessor.getMessageType() != SimpMessageType.MESSAGE) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            return message;
        }

        UUID userId = extractAuthenticatedUserId(accessor);
        if (destination.startsWith("/user/queue/")) {
            if (!StompUserQueueDestinationPolicy.isAllowed(destination)) {
                return null;
            }
            return userId != null && hasActiveSession(accessor, userId) ? message : null;
        }

        Matcher matcher = CHAT_TOPIC_DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            return message;
        }

        if (userId == null || !hasActiveSession(accessor, userId)) {
            return null;
        }

        try {
            UUID chatId = UUID.fromString(matcher.group(1));
            var chat = chatService.getOwnedChat(userId, chatId);
            UUID topicId = resolvePayloadTopicId(message.getPayload());
            if (topicId != null && Boolean.TRUE.equals(chat.getForumEnabled())) {
                forumTopicService.resolveTopicForRead(chat, userId, topicId);
            }
            return message;
        } catch (ResponseStatusException exception) {
            return null;
        }
    }

    private UUID extractAuthenticatedUserId(SimpMessageHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof Authentication authentication) || authentication.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean hasActiveSession(SimpMessageHeaderAccessor accessor, UUID userId) {
        UUID sessionId = extractAuthenticatedSessionId(accessor);
        return sessionId == null || userSessionService.isActive(sessionId, userId);
    }

    private UUID extractAuthenticatedSessionId(SimpMessageHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof Authentication authentication) || authentication.getDetails() == null) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getDetails().toString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private UUID resolvePayloadTopicId(Object payload) {
        if (payload instanceof TypingEventResponse event) {
            return event.topicId();
        }
        if (payload instanceof ChatReadEventResponse event) {
            return resolveMessageTopicId(event.messageId());
        }
        if (payload instanceof PinMessageEventResponse event) {
            return resolveMessageTopicId(event.messageId());
        }
        return null;
    }

    private UUID resolveMessageTopicId(UUID messageId) {
        if (messageId == null) {
            return null;
        }
        MessageLookupEntity lookup = messageLookupRepository.findById(messageId).orElse(null);
        return lookup != null ? lookup.getTopicId() : null;
    }
}
