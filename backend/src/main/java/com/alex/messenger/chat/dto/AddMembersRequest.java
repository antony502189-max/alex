package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record AddMembersRequest(
        @NotEmpty List<UUID> userIds
) {
}
