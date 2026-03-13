package com.alex.messenger.story.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record StoryMentionRequest(
        @NotNull UUID targetUserId,
        @Size(max = 500) String message
) {
}
