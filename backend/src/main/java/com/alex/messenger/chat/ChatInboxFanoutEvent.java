package com.alex.messenger.chat;

import java.util.List;
import java.util.UUID;

public record ChatInboxFanoutEvent(
        String eventType,
        UUID chatId,
        List<UUID> userIds,
        List<UUID> removedUserIds
) {
}
