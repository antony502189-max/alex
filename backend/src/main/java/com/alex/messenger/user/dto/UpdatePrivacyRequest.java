package com.alex.messenger.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePrivacyRequest(
        @NotBlank String phonePrivacy,
        @NotBlank String lastSeenPrivacy,
        @NotBlank String storyPrivacy
) {
}
