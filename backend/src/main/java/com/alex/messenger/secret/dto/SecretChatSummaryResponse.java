package com.alex.messenger.secret.dto;

import java.time.Instant;
import java.util.UUID;

public record SecretChatSummaryResponse(
        UUID secretChatId,
        UUID peerUserId,
        String peerDisplayName,
        String peerPhoneNumber,
        String peerPhotoUrl,
        Instant peerPhotoAccessExpiresAt,
        UUID initiatorSessionId,
        UUID recipientSessionId,
        UUID peerSessionId,
        String peerDeviceName,
        String initiatorPublicKey,
        String recipientPublicKey,
        String sharedKeyFingerprint,
        String status,
        String direction,
        Integer autoDeleteSeconds,
        Instant createdAt,
        Instant acceptedAt,
        Instant closedAt,
        Instant lastMessageAt
) {
}
