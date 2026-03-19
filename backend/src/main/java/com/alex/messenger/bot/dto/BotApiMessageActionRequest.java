package com.alex.messenger.bot.dto;

import com.alex.messenger.shared.HttpUrlValidationSupport;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BotApiMessageActionRequest(
        @NotBlank @Size(max = 16) String actionType,
        @NotBlank @Size(max = 64) String buttonText,
        @Size(max = 255) String callbackData,
        @Size(max = 512) String targetUrl,
        @Size(max = 128) String webAppStartParameter
) {

    @JsonIgnore
    @AssertTrue(message = "Unsupported bot message action type")
    public boolean hasSupportedActionType() {
        return actionType == null || isActionType("CALLBACK") || isActionType("URL") || isActionType("WEB_APP");
    }

    @JsonIgnore
    @AssertTrue(message = "Callback actions require callbackData and do not support URLs or mini app parameters")
    public boolean hasValidCallbackConfiguration() {
        if (!isActionType("CALLBACK")) {
            return true;
        }
        return hasText(callbackData) && isBlank(targetUrl) && isBlank(webAppStartParameter);
    }

    @JsonIgnore
    @AssertTrue(message = "URL actions require targetUrl and do not support callbackData or mini app parameters")
    public boolean hasValidUrlConfiguration() {
        if (!isActionType("URL")) {
            return true;
        }
        return isBlank(callbackData)
                && isBlank(webAppStartParameter)
                && HttpUrlValidationSupport.isValidRequiredHttpUrl(targetUrl);
    }

    @JsonIgnore
    @AssertTrue(message = "WEB_APP actions do not support callbackData or targetUrl")
    public boolean hasValidWebAppConfiguration() {
        if (!isActionType("WEB_APP")) {
            return true;
        }
        return isBlank(callbackData) && isBlank(targetUrl) && hasValidStartParameter(webAppStartParameter);
    }

    private boolean isActionType(String value) {
        return actionType != null && value.equalsIgnoreCase(actionType.trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private boolean hasValidStartParameter(String value) {
        return isBlank(value) || value.trim().matches("[A-Za-z0-9_\\-]{1,128}");
    }
}
