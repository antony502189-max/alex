package com.alex.messenger.story.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record UpdateStoryHighlightStoriesRequest(
        @Size(min = 1) List<@NotNull UUID> storyIds,
        UUID coverStoryId
) {

    @JsonIgnore
    @AssertTrue(message = "Provide storyIds or coverStoryId")
    public boolean hasUpdate() {
        return (storyIds != null && !storyIds.isEmpty()) || coverStoryId != null;
    }
}
