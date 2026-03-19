package com.alex.messenger.call.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

public record UpdateCallParticipantModerationRequest(
        Boolean audioPublishingAllowed,
        Boolean videoPublishingAllowed,
        Boolean screenShareAllowed,
        Boolean audioMuted,
        Boolean removeParticipant
) {

    @AssertTrue(message = "No moderation changes were provided")
    @JsonIgnore
    public boolean isChangeRequested() {
        return audioPublishingAllowed != null
                || videoPublishingAllowed != null
                || screenShareAllowed != null
                || audioMuted != null
                || Boolean.TRUE.equals(removeParticipant);
    }
}
