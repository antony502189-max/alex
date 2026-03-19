package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record UpdateChatProfileRequest(
        @Size(max = 255) String title,
        @Size(max = 500) String about,
        @Positive @Max(31_536_000) Integer autoDeleteSeconds,
        @Positive @Max(86_400) Integer slowModeSeconds,
        Boolean forumEnabled,
        Boolean joinRequiresApproval,
        Boolean commentsEnabled,
        Boolean reactionsEnabled,
        Boolean crossPostingEnabled,
        UUID linkedDiscussionChatId
) {
}
