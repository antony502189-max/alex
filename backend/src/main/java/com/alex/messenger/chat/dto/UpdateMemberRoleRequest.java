package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateMemberRoleRequest(
        @NotBlank
        @Pattern(regexp = "(?i)^\\s*(ADMIN|MEMBER)\\s*$")
        String role
) {
}
