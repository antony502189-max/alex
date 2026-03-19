package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateChannelRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 500) String about,
        @Positive Integer autoDeleteSeconds,
        Boolean joinRequiresApproval,
        List<@NotNull UUID> subscriberIds
) {
}
