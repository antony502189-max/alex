package com.alex.messenger.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record MessageTextEntityPayload(
        @NotBlank
        @Pattern(regexp = "BOLD|ITALIC|UNDERLINE|STRIKETHROUGH|SPOILER|CODE|PRE|URL|TEXT_LINK|MENTION|MENTION_NAME|HASHTAG|BOT_COMMAND|CASHTAG|PHONE|EMAIL|CUSTOM_EMOJI")
        String type,
        @PositiveOrZero int offset,
        @Positive int length,
        String value,
        UUID userId
) {

    public MessageTextEntityPayload(String type, int offset, int length) {
        this(type, offset, length, null, null);
    }
}
