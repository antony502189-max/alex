package com.alex.messenger.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record UpdateForumTopicRequest(
        @Size(max = 255) String title,
        @Size(max = 32) String iconEmoji,
        Boolean closed,
        Boolean hidden
) {
    @JsonIgnore
    @AssertTrue(message = "Provide title, iconEmoji, closed or hidden")
    public boolean hasUpdate() {
        return (title != null && !title.isBlank())
                || iconEmoji != null
                || closed != null
                || hidden != null;
    }
}
