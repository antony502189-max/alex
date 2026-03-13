package com.alex.messenger.call.dto;

public record UpdateCallParticipantModerationRequest(
        Boolean audioPublishingAllowed,
        Boolean videoPublishingAllowed,
        Boolean screenShareAllowed,
        Boolean audioMuted,
        Boolean removeParticipant
) {
}
