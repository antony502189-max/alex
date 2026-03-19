package com.alex.messenger.story.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record CreateStoryRequest(
        @Size(max = 500) String text,
        @Size(max = 16) String backgroundFrom,
        @Size(max = 16) String backgroundTo,
        @Size(max = 16) String textColor,
        @Pattern(regexp = "(?i)DEFAULT|EVERYBODY|CONTACTS|NOBODY|CLOSE_FRIENDS|CUSTOM|SELECTED_USERS")
        @Size(max = 16) String audience,
        List<@NotNull UUID> allowedViewerUserIds,
        UUID ownerChatId
) {

    @JsonIgnore
    @AssertTrue(message = "Selected audience requires at least one contact")
    public boolean hasRequiredAudienceSelection() {
        String normalizedAudience = audience != null ? audience.trim().toUpperCase(Locale.ROOT) : "DEFAULT";
        if (!"CUSTOM".equals(normalizedAudience) && !"SELECTED_USERS".equals(normalizedAudience)) {
            return true;
        }
        return allowedViewerUserIds != null && !allowedViewerUserIds.isEmpty();
    }
}
