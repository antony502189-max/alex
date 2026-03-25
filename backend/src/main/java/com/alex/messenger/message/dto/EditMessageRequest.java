package com.alex.messenger.message.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record EditMessageRequest(
        @Size(max = 4000) String text,
        @Size(max = 4000) String caption,
        List<@jakarta.validation.constraints.NotNull @Valid MessageTextEntityPayload> entities,
        Boolean disableLinkPreview
) {
    public EditMessageRequest(String text, List<MessageTextEntityPayload> entities) {
        this(text, null, entities, null);
    }
}
