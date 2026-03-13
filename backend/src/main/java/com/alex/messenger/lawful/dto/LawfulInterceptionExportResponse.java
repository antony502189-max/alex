package com.alex.messenger.lawful.dto;

import com.alex.messenger.message.dto.ChatMessageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LawfulInterceptionExportResponse(
        UUID userId,
        Instant fromInclusive,
        Instant toExclusive,
        Instant exportedAt,
        int messageCount,
        List<ChatMessageResponse> messages
) {
}
