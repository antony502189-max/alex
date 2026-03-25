package com.alex.messenger.chat.dto;

import java.util.UUID;

public record ClearHistoryRequest(
        UUID topicId,
        UUID upToMessageId
) {
}
