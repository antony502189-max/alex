package com.alex.messenger.chat.dto;

import java.util.List;
import java.util.UUID;

public record ChatFolderResponse(
        UUID folderId,
        String title,
        int position,
        List<UUID> chatIds
) {
}
