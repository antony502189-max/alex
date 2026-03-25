package com.alex.messenger.sync;

import com.alex.messenger.sync.dto.SyncEventResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserSyncService {

    private static final Set<String> UNREAD_RECONCILIATION_EVENT_TYPES = Set.of(
            "MESSAGE_UPSERT",
            "MESSAGE_DELETED",
            "CHAT_HISTORY_CLEARED"
    );
    private static final Set<String> DELIVERY_RECONCILIATION_EVENT_TYPES = Set.of(
            "MESSAGE_UPSERT",
            "MESSAGE_DELETED",
            "CHAT_READ",
            "CHAT_HISTORY_CLEARED"
    );

    public record SyncSlice(
            List<SyncEventResponse> events,
            Long nextCursor,
            boolean hasMore,
            boolean staleCursor,
            Long resetCursor
    ) {
    }

    private final UserSyncEventRepository userSyncEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recordForUsers(
            Collection<UUID> userIds,
            String eventType,
            String entityType,
            UUID entityId,
            UUID chatId,
            Object payload
    ) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload != null ? payload : java.util.Map.of());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize sync payload", exception);
        }

        List<UserSyncEventEntity> entities = userIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(userId -> {
                    UserSyncEventEntity entity = new UserSyncEventEntity();
                    entity.setUserId(userId);
                    entity.setEventType(eventType);
                    entity.setEntityType(entityType);
                    entity.setEntityId(entityId);
                    entity.setChatId(chatId);
                    entity.setPayloadJson(payloadJson);
                    return entity;
                })
                .toList();
        userSyncEventRepository.saveAll(entities);
    }

    @Transactional(readOnly = true)
    public SyncSlice listEvents(UUID userId, Long cursor, int limit, boolean includeLegacy) {
        int normalizedLimit = Math.min(limit, 200);
        List<SyncEventResponse> events = new ArrayList<>();
        List<UserSyncEventEntity> deferredLegacySuffix = List.of();
        UserSyncEventEntity oldestAvailableEvent =
                cursor != null ? userSyncEventRepository.findFirstByUserIdOrderByIdAsc(userId) : null;
        boolean staleCursor = cursor != null
                && oldestAvailableEvent != null
                && oldestAvailableEvent.getId() != null
                && oldestAvailableEvent.getId() > cursor + 1;
        Long resetCursor = staleCursor ? oldestAvailableEvent.getId() - 1 : null;
        Long queryCursor = staleCursor ? resetCursor : cursor;

        while (events.size() <= normalizedLimit) {
            List<UserSyncEventEntity> loaded = queryCursor != null
                    ? userSyncEventRepository.findTop201ByUserIdAndIdGreaterThanOrderByIdAsc(userId, queryCursor)
                    : userSyncEventRepository.findTop201ByUserIdOrderByIdAsc(userId);
            if (loaded.isEmpty() && deferredLegacySuffix.isEmpty()) {
                break;
            }
            if (!loaded.isEmpty()) {
                queryCursor = loaded.get(loaded.size() - 1).getId();
            }

            List<UserSyncEventEntity> combinedBatch;
            if (deferredLegacySuffix.isEmpty()) {
                combinedBatch = loaded;
            } else {
                combinedBatch = new ArrayList<>(deferredLegacySuffix.size() + loaded.size());
                combinedBatch.addAll(deferredLegacySuffix);
                combinedBatch.addAll(loaded);
            }

            int deferredSuffixStart = !includeLegacy && loaded.size() == 201
                    ? resolveDeferredLegacySuffixStart(combinedBatch)
                    : combinedBatch.size();
            deferredLegacySuffix = deferredSuffixStart < combinedBatch.size()
                    ? List.copyOf(combinedBatch.subList(deferredSuffixStart, combinedBatch.size()))
                    : List.of();

            for (int index = 0; index < deferredSuffixStart; index++) {
                UserSyncEventEntity entity = combinedBatch.get(index);
                if (!includeLegacy && shouldSuppressLegacyCompanionEvent(combinedBatch, index)) {
                    continue;
                }
                events.add(toResponse(entity));
                if (events.size() > normalizedLimit) {
                    break;
                }
            }

            if (events.size() > normalizedLimit) {
                break;
            }
            if (loaded.size() < 201) {
                if (!deferredLegacySuffix.isEmpty()) {
                    for (UserSyncEventEntity entity : deferredLegacySuffix) {
                        events.add(toResponse(entity));
                        if (events.size() > normalizedLimit) {
                            break;
                        }
                    }
                    deferredLegacySuffix = List.of();
                }
                break;
            }
        }

        boolean hasMore = events.size() > normalizedLimit;
        if (hasMore) {
            events = new ArrayList<>(events.subList(0, normalizedLimit));
        }
        Long nextCursor = events.isEmpty() ? null : events.get(events.size() - 1).cursor();
        return new SyncSlice(List.copyOf(events), nextCursor, hasMore, staleCursor, resetCursor);
    }

    @Transactional(readOnly = true)
    public List<UUID> listChatIdsForUnreadReconciliation(Instant createdAfter, int limit) {
        if (createdAfter == null || limit <= 0) {
            return List.of();
        }
        int normalizedLimit = Math.min(limit, 500);
        return userSyncEventRepository.findDistinctChatIdsForEventTypesCreatedAfter(
                createdAfter,
                UNREAD_RECONCILIATION_EVENT_TYPES,
                PageRequest.of(0, normalizedLimit)
        );
    }

    @Transactional(readOnly = true)
    public List<UUID> listChatIdsForDeliveryReconciliation(Instant createdAfter, int limit) {
        if (createdAfter == null || limit <= 0) {
            return List.of();
        }
        int normalizedLimit = Math.min(limit, 500);
        return userSyncEventRepository.findDistinctChatIdsForEventTypesCreatedAfter(
                createdAfter,
                DELIVERY_RECONCILIATION_EVENT_TYPES,
                PageRequest.of(0, normalizedLimit)
        );
    }

    public Collection<UUID> participantsIncludingActor(UUID actorId, Collection<UUID> others) {
        LinkedHashSet<UUID> participants = new LinkedHashSet<>();
        if (actorId != null) {
            participants.add(actorId);
        }
        if (others != null) {
            participants.addAll(others);
        }
        return participants;
    }

    @Transactional
    public int deleteExpiredEvents(Instant createdBefore, int limit) {
        if (createdBefore == null || limit <= 0) {
            return 0;
        }
        int normalizedLimit = Math.min(limit, 1_000);
        List<UserSyncEventEntity> expiredEvents = userSyncEventRepository.findByCreatedAtBeforeOrderByCreatedAtAsc(
                createdBefore,
                PageRequest.of(0, normalizedLimit)
        );
        if (expiredEvents.isEmpty()) {
            return 0;
        }
        userSyncEventRepository.deleteAllInBatch(expiredEvents);
        return expiredEvents.size();
    }

    private SyncEventResponse toResponse(UserSyncEventEntity entity) {
        String canonicalEventType = SyncEventTypeCatalog.canonicalEventType(entity.getEventType());
        String legacyEventType = resolveLegacyEventType(entity, canonicalEventType);
        return new SyncEventResponse(
                entity.getId(),
                entity.getEventType(),
                canonicalEventType,
                legacyEventType,
                SyncEventTypeCatalog.isTransitionLegacyEventType(entity.getEventType()),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getChatId(),
                entity.getPayloadJson(),
                entity.getCreatedAt()
        );
    }

    private boolean shouldSuppressLegacyCompanionEvent(List<UserSyncEventEntity> loaded, int index) {
        UserSyncEventEntity legacyEvent = loaded.get(index);
        if (!SyncEventTypeCatalog.isTransitionLegacyEventType(legacyEvent.getEventType())) {
            return false;
        }
        for (int candidateIndex = index + 1; candidateIndex < loaded.size(); candidateIndex++) {
            if (isCanonicalCompanion(legacyEvent, loaded.get(candidateIndex))) {
                return true;
            }
        }
        return false;
    }

    private boolean isCanonicalCompanion(UserSyncEventEntity legacyEvent, UserSyncEventEntity candidate) {
        if (legacyEvent == null || candidate == null) {
            return false;
        }
        if (!SyncEventTypeCatalog.isCanonicalEventType(candidate.getEventType())) {
            return false;
        }
        if (!Objects.equals(legacyEvent.getEntityType(), candidate.getEntityType())
                || !Objects.equals(legacyEvent.getEntityId(), candidate.getEntityId())
                || !Objects.equals(legacyEvent.getChatId(), candidate.getChatId())) {
            return false;
        }
        return Objects.equals(legacyEvent.getEventType(), extractOriginEventType(candidate.getPayloadJson()));
    }

    private int resolveDeferredLegacySuffixStart(List<UserSyncEventEntity> loaded) {
        int suffixStart = loaded.size();
        while (suffixStart > 0) {
            UserSyncEventEntity candidate = loaded.get(suffixStart - 1);
            if (!SyncEventTypeCatalog.isTransitionLegacyEventType(candidate.getEventType())) {
                break;
            }
            suffixStart--;
        }
        return suffixStart;
    }

    private String resolveLegacyEventType(UserSyncEventEntity entity, String canonicalEventType) {
        if (entity == null) {
            return null;
        }
        if (SyncEventTypeCatalog.isTransitionLegacyEventType(entity.getEventType())) {
            return entity.getEventType();
        }
        String originEventType = extractOriginEventType(entity.getPayloadJson());
        if (originEventType == null || originEventType.equals(canonicalEventType)) {
            return null;
        }
        return originEventType;
    }

    private String extractOriginEventType(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            JsonNode payloadNode = objectMapper.readTree(payloadJson);
            JsonNode originEventTypeNode = payloadNode.get("originEventType");
            if (originEventTypeNode == null || originEventTypeNode.isNull()) {
                return null;
            }
            String value = originEventTypeNode.asText();
            return value == null || value.isBlank() ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }
}
