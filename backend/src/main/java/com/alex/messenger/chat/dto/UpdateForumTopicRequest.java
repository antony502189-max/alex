package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.Size;

public record UpdateForumTopicRequest(
        @Size(max = 255) String title,
        @Size(max = 32) String iconEmoji,
        Boolean closed,
        Boolean hidden
) {
}
