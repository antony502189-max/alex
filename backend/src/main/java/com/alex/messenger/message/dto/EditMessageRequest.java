package com.alex.messenger.message.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record EditMessageRequest(
        @NotBlank @Size(max = 4000) String text,
        @Valid List<MessageTextEntityPayload> entities
) {
}
