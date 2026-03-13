package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinByInviteLinkRequest(
        @NotBlank String token
) {
}
