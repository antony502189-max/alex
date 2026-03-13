package com.alex.messenger.call.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CallSessionResponse(
        UUID callId,
        UUID chatId,
        UUID createdByUserId,
        String kind,
        String mode,
        String status,
        Instant startedAt,
        Instant answeredAt,
        Instant endedAt,
        boolean recordingEnabled,
        Instant recordingStartedAt,
        boolean viewerCanModerate,
        boolean viewerCanManageLinks,
        List<CallParticipantResponse> participants
) {
}
