package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record UpsertChatFolderRequest(
        @NotBlank @Size(max = 64) String title,
        @Min(0) Integer position,
        List<@NotNull UUID> chatIds,
        List<@NotNull UUID> includedChatIds,
        List<@NotNull UUID> excludedChatIds,
        List<@NotBlank @Pattern(regexp = "(?i)DIRECT|GROUP|CHANNEL|SAVED") String> includedChatTypes,
        Boolean includeContacts,
        Boolean includeNonContacts,
        Boolean includeBots,
        Boolean includeRead,
        Boolean includeUnread,
        Boolean includeMuted,
        Boolean includeUnmuted,
        Boolean includeArchived,
        Boolean includeNonArchived
) {
}
