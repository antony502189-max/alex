package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.Size;

public record UpsertChatDraftRequest(
        @Size(max = 4000) String text
) {
}
