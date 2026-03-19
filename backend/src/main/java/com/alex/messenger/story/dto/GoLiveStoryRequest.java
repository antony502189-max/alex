package com.alex.messenger.story.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GoLiveStoryRequest(
        Boolean donationsEnabled,
        @Size(max = 32) String donationProvider,
        @Pattern(regexp = "(?i)^[A-Z0-9]{3,8}$") @Size(max = 8) String donationCurrency,
        @Pattern(regexp = "(?i)^https?://.+$") @Size(max = 500) String donationEventHookUrl
) {

    @JsonIgnore
    @AssertTrue(message = "Donation currency is required when donations are enabled")
    public boolean hasRequiredDonationCurrency() {
        return !Boolean.TRUE.equals(donationsEnabled)
                || (donationCurrency != null && !donationCurrency.trim().isBlank());
    }
}
