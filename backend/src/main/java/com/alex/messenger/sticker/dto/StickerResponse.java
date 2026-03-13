package com.alex.messenger.sticker.dto;

import java.util.UUID;

public record StickerResponse(
        UUID stickerId,
        UUID packId,
        String packTitle,
        String emoji,
        String label,
        String backgroundFrom,
        String backgroundTo,
        String textColor
) {
}
