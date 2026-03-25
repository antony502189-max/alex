package com.alex.messenger.abuse;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AbuseProtectionService {

    private static final String ACTION_CHAT_CREATE = "CHAT_CREATE";
    private static final String ACTION_INVITE_LINK_CREATE = "INVITE_LINK_CREATE";
    private static final String ACTION_JOIN_REQUEST_CREATE = "JOIN_REQUEST_CREATE";
    private static final String ACTION_MESSAGE_SEND = "MESSAGE_SEND";
    private static final String ACTION_CHAT_REPORT = "CHAT_REPORT";
    private static final String ACTION_MESSAGE_REPORT = "MESSAGE_REPORT";

    private final AbuseActionEventRepository abuseActionEventRepository;
    private final AbuseProtectionProperties abuseProtectionProperties;

    @Transactional(readOnly = true)
    public void assertChatCreationAllowed(UUID actorUserId) {
        assertSimpleRate(
                ACTION_CHAT_CREATE,
                actorUserId,
                abuseProtectionProperties.getChatCreation(),
                "Too many chats created recently"
        );
    }

    @Transactional
    public void recordChatCreation(UUID actorUserId) {
        recordAction(ACTION_CHAT_CREATE, actorUserId, null);
    }

    @Transactional(readOnly = true)
    public void assertInviteLinkCreationAllowed(UUID actorUserId, UUID chatId) {
        assertChatScopedRate(
                ACTION_INVITE_LINK_CREATE,
                actorUserId,
                chatId,
                abuseProtectionProperties.getInviteLinkCreation(),
                "Too many invite links created recently",
                "Too many invite links created for this chat recently"
        );
    }

    @Transactional
    public void recordInviteLinkCreation(UUID actorUserId, UUID chatId) {
        recordAction(ACTION_INVITE_LINK_CREATE, actorUserId, chatId);
    }

    @Transactional(readOnly = true)
    public void assertJoinRequestCreationAllowed(UUID actorUserId, UUID chatId) {
        assertChatScopedRate(
                ACTION_JOIN_REQUEST_CREATE,
                actorUserId,
                chatId,
                abuseProtectionProperties.getJoinRequestCreation(),
                "Too many join requests created recently",
                "Too many join requests created for this chat recently"
        );
    }

    @Transactional
    public void recordJoinRequestCreation(UUID actorUserId, UUID chatId) {
        recordAction(ACTION_JOIN_REQUEST_CREATE, actorUserId, chatId);
    }

    @Transactional(readOnly = true)
    public void assertMessageSendAllowed(UUID actorUserId, UUID chatId) {
        assertChatScopedRate(
                ACTION_MESSAGE_SEND,
                actorUserId,
                chatId,
                abuseProtectionProperties.getMessageSend(),
                "Too many messages created recently",
                "Too many messages created for this chat recently"
        );
    }

    @Transactional
    public void recordMessageSend(UUID actorUserId, UUID chatId) {
        recordAction(ACTION_MESSAGE_SEND, actorUserId, chatId);
    }

    @Transactional(readOnly = true)
    public void assertChatReportAllowed(UUID actorUserId) {
        assertSimpleRate(
                ACTION_CHAT_REPORT,
                actorUserId,
                abuseProtectionProperties.getChatReport(),
                "Too many chat reports submitted recently"
        );
    }

    @Transactional
    public void recordChatReport(UUID actorUserId, UUID chatId) {
        recordAction(ACTION_CHAT_REPORT, actorUserId, chatId);
    }

    @Transactional(readOnly = true)
    public void assertMessageReportAllowed(UUID actorUserId) {
        assertSimpleRate(
                ACTION_MESSAGE_REPORT,
                actorUserId,
                abuseProtectionProperties.getMessageReport(),
                "Too many message reports submitted recently"
        );
    }

    @Transactional
    public void recordMessageReport(UUID actorUserId, UUID chatId) {
        recordAction(ACTION_MESSAGE_REPORT, actorUserId, chatId);
    }

    private void assertSimpleRate(
            String actionType,
            UUID actorUserId,
            AbuseProtectionProperties.SimpleRate rate,
            String errorMessage
    ) {
        if (actorUserId == null || rate == null || rate.getMax() <= 0 || rate.getWindow() == null || rate.getWindow().isNegative()) {
            return;
        }
        long recentEvents = abuseActionEventRepository.countByActionTypeAndActorUserIdAndCreatedAtAfter(
                normalizeActionType(actionType),
                actorUserId,
                Instant.now().minus(rate.getWindow())
        );
        if (recentEvents >= rate.getMax()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, errorMessage);
        }
    }

    private void assertChatScopedRate(
            String actionType,
            UUID actorUserId,
            UUID chatId,
            AbuseProtectionProperties.ChatScopedRate rate,
            String globalErrorMessage,
            String chatErrorMessage
    ) {
        if (actorUserId == null || rate == null) {
            return;
        }
        if (rate.getGlobalMax() > 0 && rate.getGlobalWindow() != null && !rate.getGlobalWindow().isNegative()) {
            long recentGlobalEvents = abuseActionEventRepository.countByActionTypeAndActorUserIdAndCreatedAtAfter(
                    normalizeActionType(actionType),
                    actorUserId,
                    Instant.now().minus(rate.getGlobalWindow())
            );
            if (recentGlobalEvents >= rate.getGlobalMax()) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, globalErrorMessage);
            }
        }
        if (chatId != null && rate.getChatMax() > 0 && rate.getChatWindow() != null && !rate.getChatWindow().isNegative()) {
            long recentChatEvents = abuseActionEventRepository.countByActionTypeAndActorUserIdAndChatIdAndCreatedAtAfter(
                    normalizeActionType(actionType),
                    actorUserId,
                    chatId,
                    Instant.now().minus(rate.getChatWindow())
            );
            if (recentChatEvents >= rate.getChatMax()) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, chatErrorMessage);
            }
        }
    }

    private void recordAction(String actionType, UUID actorUserId, UUID chatId) {
        if (actorUserId == null) {
            return;
        }
        AbuseActionEventEntity event = new AbuseActionEventEntity();
        event.setActionType(normalizeActionType(actionType));
        event.setActorUserId(actorUserId);
        event.setChatId(chatId);
        abuseActionEventRepository.save(event);
    }

    private String normalizeActionType(String actionType) {
        if (actionType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Abuse action type is required");
        }
        String normalized = actionType.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Abuse action type is blank");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }
}
