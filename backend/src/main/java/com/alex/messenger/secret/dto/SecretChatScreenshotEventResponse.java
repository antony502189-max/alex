package com.alex.messenger.secret.dto;

import java.time.Instant;
import java.util.UUID;

public record SecretChatScreenshotEventResponse(
        UUID secretChatId,
        UUID capturedByUserId,
        Instant capturedAt
) {
}
