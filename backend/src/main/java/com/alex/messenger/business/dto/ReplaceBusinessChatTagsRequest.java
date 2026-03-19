package com.alex.messenger.business.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReplaceBusinessChatTagsRequest(
        List<@NotNull @Valid BusinessChatTagPayload> tags
) {
}
