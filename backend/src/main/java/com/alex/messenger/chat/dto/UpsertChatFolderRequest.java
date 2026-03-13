package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record UpsertChatFolderRequest(
        @NotBlank @Size(max = 64) String title,
        Integer position,
        List<UUID> chatIds
) {
}
