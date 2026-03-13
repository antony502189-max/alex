package com.alex.messenger.sticker.dto;

import java.util.List;
import java.util.UUID;

public record StickerPackResponse(
        UUID packId,
        String title,
        String slug,
        List<StickerResponse> stickers
) {
}
