package com.alex.messenger.business.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AssignBusinessOperatorRequest(
        @NotNull UUID operatorUserId,
        @Size(max = 255) String note
) {
}
