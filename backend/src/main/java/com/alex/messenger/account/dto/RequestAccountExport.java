package com.alex.messenger.account.dto;

import java.time.Instant;

public record RequestAccountExport(
        String format,
        Boolean includeAttachmentsMetadata,
        Instant fromInclusive,
        Instant toExclusive
) {
}
