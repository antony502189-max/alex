package com.alex.messenger.search;

import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.dto.ChatSummaryResponse;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.search.dto.GlobalMessageSearchResult;
import com.alex.messenger.search.dto.GlobalSearchResponse;
import com.alex.messenger.user.UserService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    private final UserService userService;
    private final ChatService chatService;
    private final MessageService messageService;

    @Transactional(readOnly = true)
    public GlobalSearchResponse search(UUID requesterId, String query, int limit) {
        String normalizedQuery = query.trim();
        if (normalizedQuery.isBlank()) {
            return new GlobalSearchResponse(query, List.of(), List.of(), List.of());
        }

        int normalizedLimit = Math.min(Math.max(limit, 1), 20);
        List<ChatSummaryResponse> allChats = chatService.listAllChats(requesterId);
        Map<UUID, ChatSummaryResponse> chatsById = new LinkedHashMap<>();
        for (ChatSummaryResponse chat : allChats) {
            chatsById.put(chat.chatId(), chat);
        }

        List<ChatSummaryResponse> chatResults = allChats.stream()
                .filter(chat -> matchesChatQuery(chat, normalizedQuery.toLowerCase()))
                .limit(normalizedLimit)
                .toList();
        List<ChatMessageResponse> messageResults = messageService.searchGlobalMessages(
                requesterId,
                allChats.stream().map(ChatSummaryResponse::chatId).toList(),
                normalizedQuery,
                normalizedLimit
        );

        return new GlobalSearchResponse(
                query,
                userService.search(requesterId, normalizedQuery).stream().limit(normalizedLimit).toList(),
                chatResults,
                messageResults.stream()
                        .map(message -> new GlobalMessageSearchResult(chatsById.get(message.chatId()), message))
                        .filter(result -> result.chat() != null)
                        .toList()
        );
    }

    private boolean matchesChatQuery(ChatSummaryResponse chat, String normalizedQuery) {
        return containsIgnoreCase(chat.title(), normalizedQuery)
                || containsIgnoreCase(chat.about(), normalizedQuery)
                || containsIgnoreCase(chat.publicUsername(), normalizedQuery)
                || containsIgnoreCase(chat.peerDisplayName(), normalizedQuery)
                || containsIgnoreCase(chat.peerPhoneNumber(), normalizedQuery)
                || containsIgnoreCase(chat.lastMessage() != null ? chat.lastMessage().previewText() : null, normalizedQuery)
                || containsIgnoreCase(chat.lastMessage() != null ? chat.lastMessage().senderDisplayName() : null, normalizedQuery);
    }

    private boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase().contains(normalizedQuery);
    }
}
