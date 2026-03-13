package com.alex.messenger.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ImportContactsRequest(
        @NotEmpty List<@Valid ImportedPhoneContactPayload> contacts,
        Boolean persistMatches
) {
}
