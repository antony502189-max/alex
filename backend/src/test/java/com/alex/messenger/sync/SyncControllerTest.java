package com.alex.messenger.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.sync.dto.SyncEventResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class SyncControllerTest {

    @Mock
    private UserSyncService userSyncService;

    private SyncController syncController;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        SyncProperties syncProperties = new SyncProperties();
        SyncProperties.Retention retention = new SyncProperties.Retention();
        retention.setTtl(Duration.ofHours(6));
        syncProperties.setRetention(retention);
        syncController = new SyncController(userSyncService, syncProperties);
        currentUserId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUserId.toString(), "test")
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listEventsUsesCanonicalContractByDefault() {
        SyncEventResponse event = new SyncEventResponse(
                42L,
                "CHAT_UPSERT",
                "CHAT_UPSERT",
                "CHAT_UPDATED",
                false,
                "CHAT",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "{\"chatId\":\"123\"}",
                Instant.parse("2026-03-25T03:00:00Z")
        );
        when(userSyncService.listEvents(currentUserId, 10L, 50, false))
                .thenReturn(new UserSyncService.SyncSlice(List.of(event), 42L, false, false, null));

        ResponseEntity<List<SyncEventResponse>> response = syncController.listEvents(10L, 50, false);

        assertThat(response.getBody()).containsExactly(event);
        assertThat(response.getHeaders().getFirst("X-Sync-Include-Legacy")).isEqualTo("false");
        assertThat(response.getHeaders().getFirst("X-Sync-Event-Contract")).isEqualTo("canonical-v1");
        assertThat(response.getHeaders().getFirst("X-Sync-Cursor-Stale")).isEqualTo("false");
        assertThat(response.getHeaders().getFirst("X-Sync-Next-Cursor")).isEqualTo("42");
        verify(userSyncService).listEvents(currentUserId, 10L, 50, false);
    }

    @Test
    void listEventsCanIncludeLegacyTransitionRows() {
        when(userSyncService.listEvents(currentUserId, null, 25, true))
                .thenReturn(new UserSyncService.SyncSlice(List.of(), null, false, false, null));

        ResponseEntity<List<SyncEventResponse>> response = syncController.listEvents(null, 25, true);

        assertThat(response.getHeaders().getFirst("X-Sync-Include-Legacy")).isEqualTo("true");
        verify(userSyncService).listEvents(currentUserId, null, 25, true);
    }

    @Test
    void listEventsExposesStaleCursorHeaders() {
        when(userSyncService.listEvents(currentUserId, 50L, 25, false))
                .thenReturn(new UserSyncService.SyncSlice(List.of(), 101L, false, true, 99L));

        ResponseEntity<List<SyncEventResponse>> response = syncController.listEvents(50L, 25, false);

        assertThat(response.getHeaders().getFirst("X-Sync-Cursor-Stale")).isEqualTo("true");
        assertThat(response.getHeaders().getFirst("X-Sync-Reset-Cursor")).isEqualTo("99");
        verify(userSyncService).listEvents(currentUserId, 50L, 25, false);
    }
}
