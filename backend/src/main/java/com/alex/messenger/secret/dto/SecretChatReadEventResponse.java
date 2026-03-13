package com.alex.messenger.secret.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SecretChatReadEventResponse(
        UUID secretChatId,
        UUID readByUserId,
        Instant readAt,
        Instant expiresAt,
        List<UUID> messageIds
) {
}
