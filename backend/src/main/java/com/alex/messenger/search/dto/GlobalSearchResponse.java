package com.alex.messenger.search.dto;

import com.alex.messenger.chat.dto.ChatSummaryResponse;
import com.alex.messenger.user.dto.UserSearchResponse;
import java.util.List;

public record GlobalSearchResponse(
        String query,
        List<UserSearchResponse> users,
        List<ChatSummaryResponse> chats,
        List<GlobalMessageSearchResult> messages
) {
}
