package com.alex.messenger.message.dto;

import java.util.UUID;

public record TranslatedMessageResponse(
        UUID messageId,
        String provider,
        String sourceLanguage,
        String targetLanguage,
        String originalText,
        String translatedText,
        String originalCaption,
        String translatedCaption
) {
}
