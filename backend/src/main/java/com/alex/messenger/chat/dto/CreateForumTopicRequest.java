package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateForumTopicRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 32) String iconEmoji
) {
}
