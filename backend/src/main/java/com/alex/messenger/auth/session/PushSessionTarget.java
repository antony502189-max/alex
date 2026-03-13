package com.alex.messenger.auth.session;

import java.util.UUID;

public record PushSessionTarget(
        UUID sessionId,
        String provider,
        String pushToken
) {
}
