package com.alex.messenger.search.dto;

import com.alex.messenger.chat.dto.ChatSummaryResponse;
import com.alex.messenger.message.dto.ChatMessageResponse;

public record GlobalMessageSearchResult(
        ChatSummaryResponse chat,
        ChatMessageResponse message
) {
}
