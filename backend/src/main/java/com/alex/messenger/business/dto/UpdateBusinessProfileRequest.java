package com.alex.messenger.business.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateBusinessProfileRequest(
        Boolean greetingEnabled,
        @Size(max = 1000) String greetingMessage,
        Boolean awayEnabled,
        @Size(max = 1000) String awayMessage,
        @Valid List<BusinessHourSlotPayload> businessHours,
        @Size(max = 64) String timeZone
) {
}
