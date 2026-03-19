package com.alex.messenger.story.dto;

import jakarta.validation.constraints.Size;

public record CreateStoryLiveCommentRequest(
        @Size(max = 500) String message,
        Long donationAmountMinor,
        @Size(max = 8) String donationCurrency
) {
}
