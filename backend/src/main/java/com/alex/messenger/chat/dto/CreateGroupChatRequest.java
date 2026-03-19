package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateGroupChatRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 500) String about,
        @Positive Integer autoDeleteSeconds,
        Boolean forumEnabled,
        Boolean joinRequiresApproval,
        @NotEmpty List<@NotNull UUID> memberIds
) {
}
