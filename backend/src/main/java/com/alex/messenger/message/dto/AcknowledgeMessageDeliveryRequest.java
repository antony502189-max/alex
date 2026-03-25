package com.alex.messenger.message.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record AcknowledgeMessageDeliveryRequest(
        List<@NotNull UUID> messageIds,
        UUID chatId,
        UUID upToMessageId
) {

    @JsonIgnore
    @AssertTrue(message = "Either messageIds or chatId with upToMessageId is required")
    public boolean hasSupportedTarget() {
        boolean hasMessageIds = messageIds != null && !messageIds.isEmpty();
        boolean hasChatBoundary = chatId != null && upToMessageId != null;
        return hasMessageIds || hasChatBoundary;
    }

    @JsonIgnore
    @AssertTrue(message = "messageIds cannot be combined with chatId or upToMessageId")
    public boolean isSingleAcknowledgementMode() {
        boolean hasMessageIds = messageIds != null && !messageIds.isEmpty();
        boolean hasBoundaryFields = chatId != null || upToMessageId != null;
        return !(hasMessageIds && hasBoundaryFields);
    }
}
