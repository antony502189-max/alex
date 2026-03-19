package com.alex.messenger.bot.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record BotApiAnswerPreCheckoutQueryRequest(
        @NotNull UUID preCheckoutQueryId,
        @NotNull Boolean ok,
        @Size(max = 255) String text
) {

    @JsonIgnore
    @AssertTrue(message = "Declined pre-checkout queries require text")
    public boolean hasDeclineReasonWhenRejected() {
        return !Boolean.FALSE.equals(ok) || (text != null && !text.trim().isBlank());
    }
}
