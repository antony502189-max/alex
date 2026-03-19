package com.alex.messenger.bot.dto;

import com.alex.messenger.shared.HttpUrlValidationSupport;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record UpdateDeveloperBotRequest(
        @Size(max = 120) String displayName,
        @Size(min = 4, max = 64) String username,
        @Size(max = 255) String description,
        @Size(max = 255) String about,
        Boolean supportsInline,
        @Size(max = 512) String webAppUrl
) {

    @JsonIgnore
    @AssertTrue(message = "At least one bot field must be provided")
    public boolean hasChanges() {
        return displayName != null
                || username != null
                || description != null
                || about != null
                || supportsInline != null
                || webAppUrl != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Bot username must end with 'bot' and match [a-z0-9_]{4,64}")
    public boolean hasValidUsername() {
        if (username == null) {
            return true;
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9_]{4,64}") && normalized.endsWith("bot");
    }

    @JsonIgnore
    @AssertTrue(message = "Mini app URL must be a valid http(s) URL")
    public boolean hasValidWebAppUrl() {
        return HttpUrlValidationSupport.isValidOptionalHttpUrl(webAppUrl);
    }
}
