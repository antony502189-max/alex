package com.alex.messenger.user.dto;

import java.util.UUID;

public record UpsertProfileAudioRequest(
        UUID attachmentId,
        String title,
        String performer,
        String caption
) {
}
