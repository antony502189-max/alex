package com.alex.messenger.chat.suggested.dto;

import com.alex.messenger.message.dto.MessageContactCardPayload;
import com.alex.messenger.message.dto.MessageLocationPayload;
import com.alex.messenger.message.dto.MessageTextEntityPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateSuggestedPostRequest(
        @Size(max = 4000) String text,
        @Size(max = 4000) String caption,
        String messageType,
        List<@NotNull @Valid MessageTextEntityPayload> entities,
        @Valid MessageLocationPayload location,
        @Valid MessageContactCardPayload contactCard,
        List<@NotNull UUID> attachmentIds,
        UUID stickerId,
        Boolean silent,
        @Positive Long requestedAmountUnits
) {
}
