package com.alex.messenger.chat.dto;

import java.util.UUID;

public record MemberMutationResponse(
        UUID chatId,
        UUID userId,
        String role
) {
}
