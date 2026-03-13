package com.alex.messenger.story.dto;

import jakarta.validation.constraints.Size;

public record StoryReshareRequest(
        @Size(max = 500) String note
) {
}
