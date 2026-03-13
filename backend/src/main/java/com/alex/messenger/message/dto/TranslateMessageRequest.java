package com.alex.messenger.message.dto;

import jakarta.validation.constraints.Size;

public record TranslateMessageRequest(
        @Size(min = 2, max = 16) String targetLanguage,
        @Size(min = 2, max = 16) String sourceLanguage
) {
}
