package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.Size;

public record UpdateChatPublicUsernameRequest(
        @Size(max = 64) String publicUsername
) {
}
