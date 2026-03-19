package com.alex.messenger.bot.dto;

import com.alex.messenger.shared.HttpUrlValidationSupport;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record BotApiAnswerCallbackQueryRequest(
        @NotNull UUID callbackQueryId,
        @Size(max = 255) String text,
        Boolean showAlert,
        @Size(max = 512) String redirectUrl
) {

    @JsonIgnore
    @AssertTrue(message = "Callback redirect URL must be a valid http(s) URL")
    public boolean hasValidRedirectUrl() {
        return HttpUrlValidationSupport.isValidOptionalHttpUrl(redirectUrl);
    }
}
