package com.alex.messenger.bot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BotApiSetMyCommandsRequest(
        @Size(max = 100) List<@NotNull @Valid BotApiCommandRequest> commands
) {
}
