package com.alex.messenger.bot.dto;

public record BotCommandResponse(
        String command,
        String description
) {
}
