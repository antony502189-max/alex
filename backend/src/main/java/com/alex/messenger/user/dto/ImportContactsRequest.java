package com.alex.messenger.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ImportContactsRequest(
        @NotEmpty @Size(max = 1000) List<@NotNull @Valid ImportedPhoneContactPayload> contacts,
        Boolean persistMatches
) {
}
