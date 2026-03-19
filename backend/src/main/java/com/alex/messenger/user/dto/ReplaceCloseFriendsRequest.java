package com.alex.messenger.user.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ReplaceCloseFriendsRequest(
        List<@NotNull UUID> userIds
) {
}
