package com.alex.messenger.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateLanguagePreferencesRequest(
        @Size(min = 2, max = 16) String preferredLanguage,
        @Size(min = 2, max = 16) String translationTargetLanguage
) {
}
