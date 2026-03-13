package com.alex.messenger.secret.dto;

public record UpdateSecretChatTimerRequest(
        Integer autoDeleteSeconds
) {
}
