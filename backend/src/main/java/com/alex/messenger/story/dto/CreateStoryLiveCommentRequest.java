package com.alex.messenger.story.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateStoryLiveCommentRequest(
        @Size(max = 500) String message,
        @Positive Long donationAmountMinor,
        @Pattern(regexp = "(?i)^[A-Z0-9]{3,8}$") @Size(max = 8) String donationCurrency
) {

    @JsonIgnore
    @AssertTrue(message = "Live story comment must include text or donation")
    public boolean hasMessageOrDonation() {
        return (message != null && !message.trim().isBlank()) || donationAmountMinor != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Donation currency requires donation amount")
    public boolean hasValidDonationCurrencyUsage() {
        return donationAmountMinor != null
                || donationCurrency == null
                || donationCurrency.trim().isBlank();
    }
}
