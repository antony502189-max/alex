package com.alex.messenger.message.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record VotePollRequest(
        List<@NotNull UUID> optionIds
) {
}
