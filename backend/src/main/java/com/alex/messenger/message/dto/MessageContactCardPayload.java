package com.alex.messenger.message.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record MessageContactCardPayload(
        @Size(max = 64) String firstName,
        @Size(max = 64) String lastName,
        @Size(max = 32) String phoneNumber,
        UUID userId,
        @Size(max = 2048) String vcard
) {
}
