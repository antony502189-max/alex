package com.alex.messenger.story.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StoryReactionRequest(
        @NotBlank @Size(max = 64) String reaction
) {
}
