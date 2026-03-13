package com.alex.messenger.user.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BlockUserRequest(
        @NotNull UUID blockedUserId
) {
}
