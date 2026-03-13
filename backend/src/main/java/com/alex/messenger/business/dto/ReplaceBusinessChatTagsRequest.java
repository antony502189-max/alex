package com.alex.messenger.business.dto;

import jakarta.validation.Valid;
import java.util.List;

public record ReplaceBusinessChatTagsRequest(
        @Valid List<BusinessChatTagPayload> tags
) {
}
