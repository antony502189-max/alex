package com.alex.messenger.sync;

import com.alex.messenger.shared.CurrentUser;
import com.alex.messenger.sync.dto.SyncEventResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final UserSyncService userSyncService;
    private final SyncProperties syncProperties;

    @GetMapping("/events")
    public ResponseEntity<List<SyncEventResponse>> listEvents(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "false") boolean includeLegacy
    ) {
        if (cursor != null && cursor < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cursor must be greater than or equal to 0");
        }
        if (limit < 1 || limit > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 200");
        }
        UserSyncService.SyncSlice slice = userSyncService.listEvents(CurrentUser.id(), cursor, limit, includeLegacy);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header("X-Sync-Has-More", String.valueOf(slice.hasMore()))
                .header("X-Sync-Limit", String.valueOf(limit))
                .header("X-Sync-Include-Legacy", String.valueOf(includeLegacy))
                .header("X-Sync-Event-Contract", "canonical-v1")
                .header("X-Sync-Cursor-Stale", String.valueOf(slice.staleCursor()))
                .header(
                        "X-Sync-Retention-Seconds",
                        String.valueOf(syncProperties.getRetention().getTtl().toSeconds())
                );
        if (slice.nextCursor() != null) {
            builder.header("X-Sync-Next-Cursor", String.valueOf(slice.nextCursor()));
        }
        if (slice.resetCursor() != null) {
            builder.header("X-Sync-Reset-Cursor", String.valueOf(slice.resetCursor()));
        }
        return builder.body(slice.events());
    }
}
