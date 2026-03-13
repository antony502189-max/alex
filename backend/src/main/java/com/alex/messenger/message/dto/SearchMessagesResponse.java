package com.alex.messenger.message.dto;

import java.util.List;

public record SearchMessagesResponse(
        String query,
        List<ChatMessageResponse> messages
) {
}
