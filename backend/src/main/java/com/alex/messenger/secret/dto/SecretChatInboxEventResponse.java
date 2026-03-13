package com.alex.messenger.secret.dto;

public record SecretChatInboxEventResponse(
        String eventType,
        SecretChatSummaryResponse chat,
        SecretChatMessageResponse message,
        SecretChatReadEventResponse read,
        SecretChatScreenshotEventResponse screenshot
) {
}
