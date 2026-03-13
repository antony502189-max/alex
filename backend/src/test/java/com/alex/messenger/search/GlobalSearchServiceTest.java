package com.alex.messenger.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.dto.ChatSummaryResponse;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.search.dto.GlobalSearchResponse;
import com.alex.messenger.user.UserService;
import com.alex.messenger.user.dto.UserSearchResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GlobalSearchServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private ChatService chatService;

    @Mock
    private MessageService messageService;

    private GlobalSearchService globalSearchService;

    @BeforeEach
    void setUp() {
        globalSearchService = new GlobalSearchService(userService, chatService, messageService);
    }

    @Test
    void searchReturnsCombinedGlobalResults() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatSummaryResponse chat = new ChatSummaryResponse(
                chatId,
                "DIRECT",
                "Alex",
                null,
                null,
                UUID.randomUUID(),
                "+375291234567",
                "Alex",
                false,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                false,
                0,
                null,
                null,
                Instant.parse("2026-03-12T12:00:00Z"),
                2,
                null,
                0,
                0,
                0,
                false,
                null,
                null,
                null,
                null,
                false,
                false,
                true,
                false
        );
        ChatMessageResponse message = new ChatMessageResponse(
                chatId,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                "hello alex",
                List.of(),
                "TEXT",
                null,
                false,
                null,
                null,
                null,
                Instant.parse("2026-03-12T12:01:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                "READ",
                null,
                null,
                null,
                null,
                null
        );

        when(chatService.listAllChats(requesterId)).thenReturn(List.of(chat));
        when(userService.search(requesterId, "alex")).thenReturn(List.of(
                new UserSearchResponse(
                        UUID.randomUUID(),
                        "+375291234567",
                        "Alex",
                        "alex",
                        false,
                        null,
                        false,
                        null,
                        null,
                        null,
                        false,
                        null
                )
        ));
        when(messageService.searchGlobalMessages(requesterId, List.of(chatId), "alex", 10)).thenReturn(List.of(message));

        GlobalSearchResponse response = globalSearchService.search(requesterId, "alex", 10);

        assertThat(response.query()).isEqualTo("alex");
        assertThat(response.users()).hasSize(1);
        assertThat(response.chats()).hasSize(1);
        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).chat().chatId()).isEqualTo(chatId);
        assertThat(response.messages().get(0).message().messageId()).isEqualTo(message.messageId());
    }
}
