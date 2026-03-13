package com.alex.messenger.user.dto;

public record UserLanguagePreferencesResponse(
        String preferredLanguage,
        String translationTargetLanguage
) {
}
