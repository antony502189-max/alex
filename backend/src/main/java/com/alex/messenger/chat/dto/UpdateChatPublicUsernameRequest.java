package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateChatPublicUsernameRequest(
        @Size(max = 65)
        @Pattern(regexp = "(^\\s*$)|(^@?[A-Za-z0-9_]{5,64}$)")
        String publicUsername
) {
}
