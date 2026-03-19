package com.alex.messenger.story.dto;

import jakarta.validation.constraints.Size;

public record GoLiveStoryRequest(
        Boolean donationsEnabled,
        @Size(max = 32) String donationProvider,
        @Size(max = 8) String donationCurrency,
        @Size(max = 500) String donationEventHookUrl
) {
}
