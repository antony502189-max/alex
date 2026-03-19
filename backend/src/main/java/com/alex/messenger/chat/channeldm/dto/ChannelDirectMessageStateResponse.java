package com.alex.messenger.chat.channeldm.dto;

import java.util.UUID;

public record ChannelDirectMessageStateResponse(
        UUID channelChatId,
        boolean enabled,
        long conversationCount
) {
}
