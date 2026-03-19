package com.alex.messenger.bot.dto;

import com.alex.messenger.shared.HttpUrlValidationSupport;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBotWebhookRequest(
        @NotBlank @Size(max = 512) String webhookUrl,
        @Size(max = 255) String secretToken
) {

    @JsonIgnore
    @AssertTrue(message = "Webhook URL must be a valid http(s) URL")
    public boolean hasValidWebhookUrl() {
        return HttpUrlValidationSupport.isValidRequiredHttpUrl(webhookUrl);
    }
}
