package com.alex.messenger.chat.dto;

import java.util.List;
import java.util.UUID;

public record ChatFolderResponse(
        UUID folderId,
        String title,
        int position,
        List<UUID> chatIds,
        List<UUID> includedChatIds,
        List<UUID> excludedChatIds,
        List<String> includedChatTypes,
        boolean includeContacts,
        boolean includeNonContacts,
        boolean includeBots,
        boolean includeRead,
        boolean includeUnread,
        boolean includeMuted,
        boolean includeUnmuted,
        boolean includeArchived,
        boolean includeNonArchived
) {
}
