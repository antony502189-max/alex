package com.alex.messenger.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alex.messenger.sync.dto.SyncEventResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class UserSyncServiceTest {

    @Mock
    private UserSyncEventRepository userSyncEventRepository;

    private UserSyncService userSyncService;

    @BeforeEach
    void setUp() {
        userSyncService = new UserSyncService(userSyncEventRepository, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void listChatIdsForUnreadReconciliationUsesExpectedEventTypesAndBatchSize() {
        Instant createdAfter = Instant.parse("2026-03-25T01:00:00Z");
        UUID chatId = UUID.randomUUID();
        when(userSyncEventRepository.findDistinctChatIdsForEventTypesCreatedAfter(eq(createdAfter), any(), any(Pageable.class)))
                .thenReturn(List.of(chatId));

        List<UUID> response = userSyncService.listChatIdsForUnreadReconciliation(createdAfter, 25);

        assertThat(response).containsExactly(chatId);

        ArgumentCaptor<Collection<String>> eventTypesCaptor = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userSyncEventRepository).findDistinctChatIdsForEventTypesCreatedAfter(
                eq(createdAfter),
                eventTypesCaptor.capture(),
                pageableCaptor.capture()
        );
        assertThat(eventTypesCaptor.getValue()).containsExactlyInAnyOrder(
                "MESSAGE_UPSERT",
                "MESSAGE_DELETED",
                "CHAT_HISTORY_CLEARED"
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(25);
    }

    @Test
    void listChatIdsForDeliveryReconciliationUsesExpectedEventTypesAndBatchSize() {
        Instant createdAfter = Instant.parse("2026-03-25T01:00:00Z");
        UUID chatId = UUID.randomUUID();
        when(userSyncEventRepository.findDistinctChatIdsForEventTypesCreatedAfter(eq(createdAfter), any(), any(Pageable.class)))
                .thenReturn(List.of(chatId));

        List<UUID> response = userSyncService.listChatIdsForDeliveryReconciliation(createdAfter, 25);

        assertThat(response).containsExactly(chatId);

        ArgumentCaptor<Collection<String>> eventTypesCaptor = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userSyncEventRepository).findDistinctChatIdsForEventTypesCreatedAfter(
                eq(createdAfter),
                eventTypesCaptor.capture(),
                pageableCaptor.capture()
        );
        assertThat(eventTypesCaptor.getValue()).containsExactlyInAnyOrder(
                "MESSAGE_UPSERT",
                "MESSAGE_DELETED",
                "CHAT_READ",
                "CHAT_HISTORY_CLEARED"
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(25);
    }

    @Test
    void deleteExpiredEventsDeletesOldestBatch() {
        UserSyncEventEntity first = new UserSyncEventEntity();
        first.setId(1L);
        UserSyncEventEntity second = new UserSyncEventEntity();
        second.setId(2L);
        Instant cutoff = Instant.parse("2026-03-25T01:00:00Z");

        when(userSyncEventRepository.findByCreatedAtBeforeOrderByCreatedAtAsc(eq(cutoff), any(Pageable.class)))
                .thenReturn(List.of(first, second));

        int deleted = userSyncService.deleteExpiredEvents(cutoff, 50);

        assertThat(deleted).isEqualTo(2);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userSyncEventRepository).findByCreatedAtBeforeOrderByCreatedAtAsc(eq(cutoff), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
        verify(userSyncEventRepository).deleteAllInBatch(List.of(first, second));
    }

    @Test
    void listEventsSuppressesLegacyCompanionRowsByDefault() {
        UUID userId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        UserSyncEventEntity legacy = syncEvent(10L, "CHAT_UPDATED", "CHAT", chatId, chatId, "{\"chatId\":\"" + chatId + "\"}");
        UserSyncEventEntity canonical = syncEvent(
                11L,
                "CHAT_UPSERT",
                "CHAT",
                chatId,
                chatId,
                "{\"chatId\":\"" + chatId + "\",\"originEventType\":\"CHAT_UPDATED\"}"
        );
        UserSyncEventEntity read = syncEvent(12L, "CHAT_READ", "MESSAGE", UUID.randomUUID(), chatId, "{\"chatId\":\"" + chatId + "\"}");
        when(userSyncEventRepository.findTop201ByUserIdOrderByIdAsc(userId)).thenReturn(List.of(legacy, canonical, read));

        UserSyncService.SyncSlice slice = userSyncService.listEvents(userId, null, 10, false);

        assertThat(slice.hasMore()).isFalse();
        assertThat(slice.nextCursor()).isEqualTo(12L);
        assertThat(slice.staleCursor()).isFalse();
        assertThat(slice.resetCursor()).isNull();
        assertThat(slice.events()).hasSize(2);
        assertThat(slice.events().get(0).eventType()).isEqualTo("CHAT_UPSERT");
        assertThat(slice.events().get(0).canonicalEventType()).isEqualTo("CHAT_UPSERT");
        assertThat(slice.events().get(0).legacyEventType()).isEqualTo("CHAT_UPDATED");
        assertThat(slice.events().get(0).transitionLegacyEvent()).isFalse();
        assertThat(slice.events().get(1).eventType()).isEqualTo("CHAT_READ");
        assertThat(slice.events().get(1).canonicalEventType()).isEqualTo("CHAT_READ");
    }

    @Test
    void listEventsSuppressesLegacyCompanionRowsAcrossBatchBoundary() {
        UUID userId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        List<UserSyncEventEntity> firstBatch = new ArrayList<>();
        long id = 1L;
        for (int pair = 0; pair < 99; pair++) {
            UUID pairEntityId = UUID.randomUUID();
            firstBatch.add(syncEvent(
                    id++,
                    "CHAT_UPDATED",
                    "CHAT",
                    pairEntityId,
                    chatId,
                    "{\"chatId\":\"" + chatId + "\"}"
            ));
            firstBatch.add(syncEvent(
                    id++,
                    "CHAT_UPSERT",
                    "CHAT",
                    pairEntityId,
                    chatId,
                    "{\"chatId\":\"" + chatId + "\",\"originEventType\":\"CHAT_UPDATED\"}"
            ));
        }
        firstBatch.add(syncEvent(id++, "CHAT_READ", "CHAT", UUID.randomUUID(), chatId, "{\"chatId\":\"" + chatId + "\"}"));
        firstBatch.add(syncEvent(id++, "CHAT_READ", "CHAT", UUID.randomUUID(), chatId, "{\"chatId\":\"" + chatId + "\"}"));
        firstBatch.add(syncEvent(id, "CHAT_UPDATED", "CHAT", chatId, chatId, "{\"chatId\":\"" + chatId + "\"}"));

        UserSyncEventEntity canonical = syncEvent(
                id + 1,
                "CHAT_UPSERT",
                "CHAT",
                chatId,
                chatId,
                "{\"chatId\":\"" + chatId + "\",\"originEventType\":\"CHAT_UPDATED\"}"
        );

        when(userSyncEventRepository.findTop201ByUserIdOrderByIdAsc(userId)).thenReturn(firstBatch);
        when(userSyncEventRepository.findTop201ByUserIdAndIdGreaterThanOrderByIdAsc(userId, id))
                .thenReturn(List.of(canonical));

        UserSyncService.SyncSlice slice = userSyncService.listEvents(userId, null, 200, false);

        assertThat(slice.hasMore()).isFalse();
        assertThat(slice.nextCursor()).isEqualTo(id + 1);
        assertThat(slice.events()).hasSize(102);
        assertThat(slice.events()).extracting(SyncEventResponse::cursor).doesNotContain(id);
        assertThat(slice.events().get(101).eventType()).isEqualTo("CHAT_UPSERT");
        assertThat(slice.events().get(101).legacyEventType()).isEqualTo("CHAT_UPDATED");
    }

    @Test
    void listEventsKeepsLegacyRowsWhenExplicitlyRequested() {
        UUID userId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        UserSyncEventEntity legacy = syncEvent(20L, "CHAT_CREATED", "CHAT", chatId, chatId, "{\"chatId\":\"" + chatId + "\"}");
        when(userSyncEventRepository.findTop201ByUserIdOrderByIdAsc(userId)).thenReturn(List.of(legacy));

        UserSyncService.SyncSlice slice = userSyncService.listEvents(userId, null, 10, true);

        assertThat(slice.events()).hasSize(1);
        assertThat(slice.staleCursor()).isFalse();
        assertThat(slice.resetCursor()).isNull();
        assertThat(slice.events().get(0).eventType()).isEqualTo("CHAT_CREATED");
        assertThat(slice.events().get(0).canonicalEventType()).isEqualTo("CHAT_UPSERT");
        assertThat(slice.events().get(0).legacyEventType()).isEqualTo("CHAT_CREATED");
        assertThat(slice.events().get(0).transitionLegacyEvent()).isTrue();
    }

    @Test
    void listEventsMarksCursorStaleWhenRetentionSkippedOlderRows() {
        UUID userId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UserSyncEventEntity oldest = syncEvent(100L, "CHAT_UPSERT", "CHAT", chatId, chatId, "{\"chatId\":\"" + chatId + "\"}");
        UserSyncEventEntity next = syncEvent(101L, "CHAT_READ", "MESSAGE", UUID.randomUUID(), chatId, "{\"chatId\":\"" + chatId + "\"}");

        when(userSyncEventRepository.findFirstByUserIdOrderByIdAsc(userId)).thenReturn(oldest);
        when(userSyncEventRepository.findTop201ByUserIdAndIdGreaterThanOrderByIdAsc(userId, 99L))
                .thenReturn(List.of(oldest, next));

        UserSyncService.SyncSlice slice = userSyncService.listEvents(userId, 50L, 10, false);

        assertThat(slice.staleCursor()).isTrue();
        assertThat(slice.resetCursor()).isEqualTo(99L);
        assertThat(slice.nextCursor()).isEqualTo(101L);
        assertThat(slice.events()).extracting(SyncEventResponse::cursor).containsExactly(100L, 101L);
    }

    private UserSyncEventEntity syncEvent(
            long id,
            String eventType,
            String entityType,
            UUID entityId,
            UUID chatId,
            String payloadJson
    ) {
        UserSyncEventEntity entity = new UserSyncEventEntity();
        entity.setId(id);
        entity.setUserId(UUID.randomUUID());
        entity.setEventType(eventType);
        entity.setEntityType(entityType);
        entity.setEntityId(entityId);
        entity.setChatId(chatId);
        entity.setPayloadJson(payloadJson);
        entity.setCreatedAt(Instant.parse("2026-03-25T02:00:00Z").plusSeconds(id));
        return entity;
    }
}
