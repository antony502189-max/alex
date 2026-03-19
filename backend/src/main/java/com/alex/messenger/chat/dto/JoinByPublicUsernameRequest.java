package com.alex.messenger.chat.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record JoinByPublicUsernameRequest(
        @JsonAlias("token")
        @NotBlank
        @Size(max = 65)
        @Pattern(regexp = "^\\s*@?[A-Za-z0-9_]{5,64}\\s*$")
        String username
) {
}
