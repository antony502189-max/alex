package com.alex.messenger.user.dto;

import java.util.List;
import java.util.UUID;

public record ReplaceCloseFriendsRequest(
        List<UUID> userIds
) {
}
