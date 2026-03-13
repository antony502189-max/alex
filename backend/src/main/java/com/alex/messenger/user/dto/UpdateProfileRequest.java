package com.alex.messenger.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 1, max = 120) String displayName,
        @Size(min = 3, max = 64) String username,
        @Size(max = 255) String about
) {
}
