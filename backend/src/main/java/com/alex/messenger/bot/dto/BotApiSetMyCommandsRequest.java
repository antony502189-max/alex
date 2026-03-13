package com.alex.messenger.bot.dto;

import jakarta.validation.Valid;
import java.util.List;

public record BotApiSetMyCommandsRequest(
        @Valid List<BotApiCommandRequest> commands
) {
}
