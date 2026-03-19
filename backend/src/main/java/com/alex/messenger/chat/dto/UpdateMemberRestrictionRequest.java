package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateMemberRestrictionRequest(
        Boolean canSendMessages,
        @Future Instant restrictedUntil,
        @Size(max = 255) String restrictionReason
) {
}
