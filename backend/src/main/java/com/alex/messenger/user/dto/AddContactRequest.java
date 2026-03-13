package com.alex.messenger.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AddContactRequest(
        @NotNull UUID contactUserId,
        @Size(min = 1, max = 120) String contactName
) {
}
