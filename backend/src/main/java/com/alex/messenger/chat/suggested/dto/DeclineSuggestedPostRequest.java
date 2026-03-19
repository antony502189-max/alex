package com.alex.messenger.chat.suggested.dto;

import jakarta.validation.constraints.Size;

public record DeclineSuggestedPostRequest(
        @Size(max = 500) String reason
) {
}
