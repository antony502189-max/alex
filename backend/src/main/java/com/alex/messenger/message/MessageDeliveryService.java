package com.alex.messenger.message;

import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatMemberEntity;
import com.alex.messenger.chat.ChatMemberRepository;
import com.alex.messenger.chat.ChatRepository;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.message.dto.AcknowledgeMessageDeliveryRequest;
import com.alex.messenger.message.dto.AcknowledgeMessageDeliveryResponse;
import com.alex.messenger.sync.UserSyncService;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MessageDeliveryService {

    private static final int BOUNDARY_SCAN_PAGE_SIZE = 500;

    private record ReadBoundary(
            UUID messageId,
            Instant messageCreatedAt,
            Instant readAt
    ) {
    }

    private final MessageLookupRepository messageLookupRepository;
    private final MessageRepository messageRepository;
    private final MessageStorageService messageStorageService;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatService chatService;
    private final UserSessionService userSessionService;
    private final UserSyncService userSyncService;
    private final MessageDeliveryProperties messageDeliveryProperties;
    private final MessageRealtimeService messageRealtimeService;

    @Transactional
    public AcknowledgeMessageDeliveryResponse acknowledgeDelivery(
            UUID requesterId,
            UUID sessionId,
            AcknowledgeMessageDeliveryRequest request
    ) {
        userSessionService.requireOwnedSession(sessionId, requesterId);
        Instant acknowledgedAt = Instant.now();
        List<UUID> deliveredMessageIds = request.messageIds() != null && !request.messageIds().isEmpty()
                ? acknowledgeExplicitMessageIds(requesterId, sessionId, request.messageIds(), acknowledgedAt)
                : acknowledgeChatBoundary(requesterId, sessionId, request.chatId(), request.upToMessageId(), acknowledgedAt);
        return new AcknowledgeMessageDeliveryResponse(
                sessionId,
                request.chatId(),
                request.upToMessageId(),
                deliveredMessageIds,
                acknowledgedAt
        );
    }

    @Transactional
    public void markReadUpTo(UUID requesterId, UUID chatId, UUID messageId) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        if (!"DIRECT".equals(chat.getChatType())) {
            return;
        }

        MessageLookupEntity target = messageLookupRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        if (!target.getChatId().equals(chatId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message belongs to another chat");
        }

        Instant readAt = Instant.now();
        forEachMessageAtOrBeforeBoundary(chatId, target.getMessageId(), message -> {
            if (message == null || message.getKey() == null || message.getKey().getMessageId() == null) {
                return;
            }
            if (message.getRecipientId() == null || !message.getRecipientId().equals(requesterId)) {
                return;
            }
            MessageLookupEntity lookup = messageLookupRepository.findById(message.getKey().getMessageId())
                    .orElse(null);
            if (lookup == null) {
                return;
            }
            if ("READ".equals(lookup.getDeliveryStatus()) && lookup.getDeliveredAt() != null && lookup.getReadAt() != null) {
                return;
            }
            lookup.setDeliveryStatus("READ");
            if (lookup.getDeliveredAt() == null) {
                lookup.setDeliveredAt(readAt);
            }
            lookup.setReadAt(readAt);
            persist(lookup);
            recordSyncUpsert(lookup, null);
            publishRealtimeUpsert(lookup);
        });
    }

    @Transactional
    public int reconcileRecentDirectChats() {
        if (!messageDeliveryProperties.getReconciliation().isEnabled()) {
            return 0;
        }
        Instant createdAfter = Instant.now().minus(messageDeliveryProperties.getReconciliation().getLookback());
        List<UUID> chatIds = userSyncService.listChatIdsForDeliveryReconciliation(
                createdAfter,
                messageDeliveryProperties.getReconciliation().getChatBatchSize()
        );
        int updatedMessages = 0;
        for (UUID chatId : chatIds) {
            updatedMessages += reconcileChatDeliveryState(
                    chatId,
                    createdAfter,
                    messageDeliveryProperties.getReconciliation().getMessageBatchSize(),
                    messageDeliveryProperties.getReconciliation().getDeliveryGracePeriod()
            );
        }
        return updatedMessages;
    }

    @Transactional
    public int reconcileChatDeliveryState(
            UUID chatId,
            Instant messageCreatedAfter,
            int messageLimit,
            Duration deliveryGracePeriod
    ) {
        if (chatId == null || messageLimit <= 0) {
            return 0;
        }
        ChatEntity chat = chatRepository.findById(chatId).orElse(null);
        if (chat == null || !"DIRECT".equals(chat.getChatType())) {
            return 0;
        }

        List<ChatMemberEntity> memberships = chatMemberRepository.findAllByIdChatId(chatId);
        if (memberships.isEmpty()) {
            return 0;
        }
        Map<UUID, ChatMemberEntity> membershipByUserId = memberships.stream()
                .collect(Collectors.toMap(member -> member.getId().getUserId(), Function.identity(), (left, right) -> left));
        Map<UUID, ReadBoundary> readBoundariesByUserId = buildReadBoundaries(memberships);

        Instant now = Instant.now();
        Instant eligibleCreatedBefore = resolveEligibleCreatedBefore(now, deliveryGracePeriod);
        int updatedMessages = 0;
        for (MessageEntity message : loadMessagesForDeliveryReconciliation(chatId, messageCreatedAfter, messageLimit)) {
            if (message == null || message.getDeletedAt() != null || message.getRecipientId() == null) {
                continue;
            }
            Instant messageCreatedAt = resolveMessageCreatedAt(message);
            if (eligibleCreatedBefore != null && messageCreatedAt != null && messageCreatedAt.isAfter(eligibleCreatedBefore)) {
                continue;
            }

            MessageLookupEntity lookup = messageLookupRepository.findById(message.getKey().getMessageId()).orElse(null);
            if (lookup == null || lookup.getDeletedAt() != null) {
                continue;
            }

            ChatMemberEntity recipientMembership = membershipByUserId.get(lookup.getRecipientId());
            if (recipientMembership == null) {
                continue;
            }

            ReadBoundary readBoundary = readBoundariesByUserId.get(lookup.getRecipientId());
            boolean changed = false;
            if (isReadByBoundary(lookup, readBoundary)) {
                Instant resolvedReadAt = resolveReadAt(readBoundary, now);
                if (!"READ".equals(lookup.getDeliveryStatus())) {
                    lookup.setDeliveryStatus("READ");
                    changed = true;
                }
                if (lookup.getDeliveredAt() == null || lookup.getDeliveredAt().isAfter(resolvedReadAt)) {
                    lookup.setDeliveredAt(resolvedReadAt);
                    changed = true;
                }
                if (lookup.getReadAt() == null || lookup.getReadAt().isAfter(resolvedReadAt)) {
                    lookup.setReadAt(resolvedReadAt);
                    changed = true;
                }
            }

            if (!changed) {
                continue;
            }
            persist(lookup);
            recordSyncUpsert(lookup, null);
            publishRealtimeUpsert(lookup);
            updatedMessages++;
        }
        return updatedMessages;
    }

    private List<MessageEntity> loadMessagesForDeliveryReconciliation(
            UUID chatId,
            Instant messageCreatedAfter,
            int messageLimit
    ) {
        int normalizedLimit = Math.max(messageLimit, 1);
        List<MessageEntity> messages = new ArrayList<>();
        UUID beforeMessageId = null;

        while (messages.size() < normalizedLimit) {
            int pageSize = Math.min(normalizedLimit - messages.size(), 500);
            List<MessageEntity> batch = beforeMessageId == null
                    ? messageRepository.findRecentByChatId(chatId, pageSize)
                    : messageRepository.findRecentByChatIdBeforeMessageId(chatId, beforeMessageId, pageSize);
            if (batch.isEmpty()) {
                break;
            }

            boolean reachedOlderThanRange = false;
            for (MessageEntity message : batch) {
                Instant createdAt = resolveMessageCreatedAt(message);
                if (messageCreatedAfter != null && createdAt != null && createdAt.isBefore(messageCreatedAfter)) {
                    reachedOlderThanRange = true;
                    continue;
                }
                messages.add(message);
                if (messages.size() == normalizedLimit) {
                    break;
                }
            }

            UUID nextBeforeMessageId = batch.stream()
                    .map(MessageEntity::getKey)
                    .filter(Objects::nonNull)
                    .map(MessagePrimaryKey::getMessageId)
                    .filter(Objects::nonNull)
                    .reduce((first, second) -> second)
                    .orElse(null);
            if (messages.size() == normalizedLimit
                    || reachedOlderThanRange
                    || batch.size() < pageSize
                    || nextBeforeMessageId == null) {
                break;
            }
            beforeMessageId = nextBeforeMessageId;
        }

        return List.copyOf(messages);
    }

    private void persist(MessageLookupEntity lookup) {
        messageStorageService.save(lookup);
    }

    private Map<UUID, ReadBoundary> buildReadBoundaries(List<ChatMemberEntity> memberships) {
        Map<UUID, ReadBoundary> boundaries = new LinkedHashMap<>();
        for (ChatMemberEntity membership : memberships) {
            UUID userId = membership.getId().getUserId();
            UUID lastReadMessageId = membership.getLastReadMessageId();
            if (userId == null || lastReadMessageId == null) {
                continue;
            }
            MessageLookupEntity lastReadLookup = messageLookupRepository.findById(lastReadMessageId).orElse(null);
            boundaries.put(
                    userId,
                    new ReadBoundary(
                            lastReadMessageId,
                            resolveBoundaryMessageCreatedAt(lastReadMessageId, lastReadLookup, membership.getLastReadAt()),
                            membership.getLastReadAt()
                    )
            );
        }
        return boundaries;
    }

    private boolean isReadByBoundary(MessageLookupEntity lookup, ReadBoundary boundary) {
        if (lookup == null || boundary == null) {
            return false;
        }
        if (lookup.getMessageId() != null && lookup.getMessageId().equals(boundary.messageId())) {
            return true;
        }
        Instant messageCreatedAt = lookup.getCreatedAt() != null
                ? lookup.getCreatedAt()
                : lookup.getMessageId() != null ? Instant.ofEpochMilli(Uuids.unixTimestamp(lookup.getMessageId())) : null;
        if (messageCreatedAt == null || boundary.messageCreatedAt() == null) {
            return false;
        }
        return !messageCreatedAt.isAfter(boundary.messageCreatedAt());
    }

    private Instant resolveReadAt(ReadBoundary boundary, Instant fallbackNow) {
        if (boundary != null && boundary.readAt() != null) {
            return boundary.readAt();
        }
        return fallbackNow;
    }

    private Instant resolveBoundaryMessageCreatedAt(
            UUID lastReadMessageId,
            MessageLookupEntity lastReadLookup,
            Instant fallbackReadAt
    ) {
        if (lastReadLookup != null && lastReadLookup.getCreatedAt() != null) {
            return lastReadLookup.getCreatedAt();
        }
        if (lastReadMessageId != null && lastReadMessageId.version() == 1) {
            return Instant.ofEpochMilli(Uuids.unixTimestamp(lastReadMessageId));
        }
        return fallbackReadAt;
    }

    private Instant resolveMessageCreatedAt(MessageEntity message) {
        if (message == null) {
            return null;
        }
        if (message.getCreatedAt() != null) {
            return message.getCreatedAt();
        }
        if (message.getKey() != null && message.getKey().getMessageId() != null) {
            return Instant.ofEpochMilli(Uuids.unixTimestamp(message.getKey().getMessageId()));
        }
        return null;
    }

    private Instant resolveEligibleCreatedBefore(Instant now, Duration deliveryGracePeriod) {
        if (now == null || deliveryGracePeriod == null || deliveryGracePeriod.isNegative() || deliveryGracePeriod.isZero()) {
            return null;
        }
        return now.minus(deliveryGracePeriod);
    }

    private void recordSyncUpsert(MessageLookupEntity lookup, UUID sessionId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chatId", lookup.getChatId());
        payload.put("messageId", lookup.getMessageId());
        payload.put("senderId", lookup.getSenderId());
        payload.put("recipientId", lookup.getRecipientId());
        payload.put("deliveryStatus", lookup.getDeliveryStatus());
        payload.put("deliveredAt", lookup.getDeliveredAt());
        payload.put("readAt", lookup.getReadAt());
        payload.put("createdAt", lookup.getCreatedAt());
        if (sessionId != null) {
            payload.put("sessionId", sessionId);
        }
        userSyncService.recordForUsers(
                userSyncService.participantsIncludingActor(
                        lookup.getSenderId(),
                        lookup.getRecipientId() != null ? List.of(lookup.getRecipientId()) : List.of()
                ),
                "MESSAGE_UPSERT",
                "MESSAGE",
                lookup.getMessageId(),
                lookup.getChatId(),
                payload
        );
    }

    private void publishRealtimeUpsert(MessageLookupEntity lookup) {
        if (lookup == null) {
            return;
        }
        messageRealtimeService.publishMessageUpsert(
                lookup.getMessageId(),
                lookup.getRecipientId() != null
                        ? List.of(lookup.getSenderId(), lookup.getRecipientId())
                        : List.of(lookup.getSenderId())
        );
    }

    private List<UUID> acknowledgeExplicitMessageIds(
            UUID requesterId,
            UUID sessionId,
            List<UUID> messageIds,
            Instant deliveredAt
    ) {
        Map<UUID, Boolean> accessCache = new HashMap<>();
        List<UUID> deliveredMessageIds = new ArrayList<>();
        for (UUID messageId : new LinkedHashSet<>(messageIds)) {
            MessageLookupEntity lookup = messageLookupRepository.findById(messageId).orElse(null);
            if (markDeliveredIfEligible(lookup, requesterId, sessionId, deliveredAt, accessCache)) {
                deliveredMessageIds.add(messageId);
            }
        }
        return List.copyOf(deliveredMessageIds);
    }

    private List<UUID> acknowledgeChatBoundary(
            UUID requesterId,
            UUID sessionId,
            UUID chatId,
            UUID upToMessageId,
            Instant deliveredAt
    ) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        if (!"DIRECT".equals(chat.getChatType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Delivery boundary acknowledgement is available only in direct chats"
            );
        }
        MessageLookupEntity target = messageLookupRepository.findById(upToMessageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        if (!chatId.equals(target.getChatId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message belongs to another chat");
        }
        Map<UUID, Boolean> accessCache = new HashMap<>();
        accessCache.put(chatId, true);
        List<UUID> deliveredMessageIds = new ArrayList<>();
        forEachMessageAtOrBeforeBoundary(chatId, target.getMessageId(), message -> {
            if (message == null || message.getDeletedAt() != null || message.getKey() == null) {
                return;
            }
            UUID messageId = message.getKey().getMessageId();
            if (messageId == null) {
                return;
            }
            MessageLookupEntity lookup = messageLookupRepository.findById(messageId).orElse(null);
            if (markDeliveredIfEligible(lookup, requesterId, sessionId, deliveredAt, accessCache)) {
                deliveredMessageIds.add(messageId);
            }
        });
        return List.copyOf(deliveredMessageIds);
    }

    private void forEachMessageAtOrBeforeBoundary(UUID chatId, UUID boundaryMessageId, Consumer<MessageEntity> consumer) {
        UUID beforeMessageId = boundaryMessageId;
        boolean firstPage = true;
        while (beforeMessageId != null) {
            List<MessageEntity> messages = firstPage
                    ? messageRepository.findRecentByChatIdAtOrBeforeMessageId(chatId, beforeMessageId, BOUNDARY_SCAN_PAGE_SIZE)
                    : messageRepository.findRecentByChatIdBeforeMessageId(chatId, beforeMessageId, BOUNDARY_SCAN_PAGE_SIZE);
            if (messages.isEmpty()) {
                break;
            }
            for (MessageEntity message : messages) {
                consumer.accept(message);
            }
            if (messages.size() < BOUNDARY_SCAN_PAGE_SIZE) {
                break;
            }
            beforeMessageId = messages.stream()
                    .map(MessageEntity::getKey)
                    .filter(Objects::nonNull)
                    .map(MessagePrimaryKey::getMessageId)
                    .filter(Objects::nonNull)
                    .reduce((first, second) -> second)
                    .orElse(null);
            firstPage = false;
        }
    }

    private boolean markDeliveredIfEligible(
            MessageLookupEntity lookup,
            UUID requesterId,
            UUID sessionId,
            Instant deliveredAt,
            Map<UUID, Boolean> accessCache
    ) {
        if (lookup == null || lookup.getMessageId() == null || lookup.getDeletedAt() != null) {
            return false;
        }
        if (!hasRequesterAccess(requesterId, lookup.getChatId(), accessCache)) {
            return false;
        }
        if (lookup.getRecipientId() == null || !lookup.getRecipientId().equals(requesterId)) {
            return false;
        }
        if ("READ".equals(lookup.getDeliveryStatus())) {
            if (lookup.getDeliveredAt() != null) {
                return false;
            }
            lookup.setDeliveredAt(lookup.getReadAt() != null ? lookup.getReadAt() : deliveredAt);
        } else {
            if ("DELIVERED".equals(lookup.getDeliveryStatus()) && lookup.getDeliveredAt() != null) {
                return false;
            }
            lookup.setDeliveryStatus("DELIVERED");
            if (lookup.getDeliveredAt() == null || lookup.getDeliveredAt().isAfter(deliveredAt)) {
                lookup.setDeliveredAt(deliveredAt);
            }
        }
        persist(lookup);
        recordSyncUpsert(lookup, sessionId);
        publishRealtimeUpsert(lookup);
        return true;
    }

    private boolean hasRequesterAccess(UUID requesterId, UUID chatId, Map<UUID, Boolean> accessCache) {
        if (chatId == null) {
            return false;
        }
        Boolean cached = accessCache.get(chatId);
        if (cached != null) {
            return cached;
        }
        boolean hasAccess;
        try {
            chatService.getOwnedChat(requesterId, chatId);
            hasAccess = true;
        } catch (ResponseStatusException exception) {
            hasAccess = false;
        }
        accessCache.put(chatId, hasAccess);
        return hasAccess;
    }
}
