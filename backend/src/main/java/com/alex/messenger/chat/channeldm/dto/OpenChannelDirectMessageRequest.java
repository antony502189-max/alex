package com.alex.messenger.chat.channeldm.dto;

import java.util.UUID;

public record OpenChannelDirectMessageRequest(
        UUID participantUserId
) {
}
