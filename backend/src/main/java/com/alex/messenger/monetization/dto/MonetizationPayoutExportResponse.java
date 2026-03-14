package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationPayoutExportResponse(
        UUID channelChatId,
        String format,
        String fileName,
        int rowCount,
        long totalUnits,
        Instant generatedAt,
        String content
) {
}
