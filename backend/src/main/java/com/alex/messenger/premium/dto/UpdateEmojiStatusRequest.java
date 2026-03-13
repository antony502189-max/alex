package com.alex.messenger.premium.dto;

import java.util.UUID;

public record UpdateEmojiStatusRequest(
        UUID customEmojiId
) {
}
