package com.alex.messenger.bot.dto;

public record BotMessageActionTriggerResponse(
        BotMessageActionResponse action,
        BotCallbackQueryResponse callbackQuery,
        BotWebAppLaunchResponse webAppLaunch,
        BotPreCheckoutQueryResponse preCheckoutQuery,
        String targetUrl
) {
}
