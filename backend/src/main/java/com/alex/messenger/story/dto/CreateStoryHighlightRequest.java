package com.alex.messenger.story.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateStoryHighlightRequest(
        @NotBlank @Size(max = 120) String title,
        UUID coverStoryId,
        @Min(0) Integer position,
        @NotNull @Size(min = 1) List<@NotNull UUID> storyIds
) {

    @JsonIgnore
    @AssertTrue(message = "Cover story must be included in selected stories")
    public boolean hasValidCoverStorySelection() {
        return coverStoryId == null || (storyIds != null && storyIds.contains(coverStoryId));
    }
}
