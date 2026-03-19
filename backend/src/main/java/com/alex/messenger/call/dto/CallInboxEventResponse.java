package com.alex.messenger.call.dto;

public record CallInboxEventResponse(
        String eventType,
        CallSessionResponse call,
        CallSignalEventResponse signal,
        CallCommentResponse comment,
        CallReactionResponse reaction
) {
}
