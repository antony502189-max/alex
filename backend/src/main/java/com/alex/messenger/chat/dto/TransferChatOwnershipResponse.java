package com.alex.messenger.chat.dto;

import java.util.UUID;

public record TransferChatOwnershipResponse(
        UUID chatId,
        UUID previousOwnerUserId,
        UUID newOwnerUserId
) {
}
