package com.alex.messenger.bot.dto;

import com.alex.messenger.message.dto.MessageTextEntityPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BotApiEditMessageTextRequest(
        @NotNull UUID messageId,
        @NotBlank @Size(max = 4000) String text,
        @Valid List<MessageTextEntityPayload> entities
) {
}
