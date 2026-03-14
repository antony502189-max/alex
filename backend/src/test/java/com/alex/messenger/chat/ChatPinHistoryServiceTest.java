package com.alex.messenger.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.dto.PinnedMessageHistoryResponse;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ChatPinHistoryServiceTest {

    @Mock
    private ChatService chatService;

    @Mock
    private ChatPinEventRepository chatPinEventRepository;

    @Mock
    private MessageService messageService;

    @Mock
    private UserRepository userRepository;

    private ChatPinHistoryService chatPinHistoryService;

    @BeforeEach
    void setUp() {
        chatPinHistoryService = new ChatPinHistoryService(
                chatService,
                chatPinEventRepository,
                messageService,
                userRepository
        );
    }

    @Test
    void listPinnedMessagesSkipsInaccessiblePinsAndReturnsAccessiblePreviews() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID activeMessageId = UUID.randomUUID();
        UUID archivedMessageId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);

        ChatPinEventEntity activePin = pinEvent(chatId, activeMessageId, adminUserId, true, null);
        ChatPinEventEntity archivedPin = pinEvent(
                chatId,
                archivedMessageId,
                adminUserId,
                false,
                Instant.parse("2026-03-12T11:59:30Z")
        );

        UserEntity admin = new UserEntity();
        admin.setId(adminUserId);
        admin.setDisplayName("Moderator");

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(chatPinEventRepository.findAllByChatIdOrderByPinnedAtDesc(
                any(UUID.class),
                any(org.springframework.data.domain.Pageable.class)
        ))
                .thenReturn(List.of(activePin, archivedPin));
        when(userRepository.findAllById(List.of(adminUserId))).thenReturn(List.of(admin));
        when(messageService.getMessage(requesterId, activeMessageId)).thenReturn(message(chatId, activeMessageId, "Current pin"));
        when(messageService.getMessage(requesterId, archivedMessageId)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found")
        );

        List<PinnedMessageHistoryResponse> response = chatPinHistoryService.listPinnedMessages(requesterId, chatId, 20);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).active()).isTrue();
        assertThat(response.get(0).message()).isNotNull();
        assertThat(response.get(0).message().text()).isEqualTo("Current pin");
        assertThat(response.get(0).pinnedByDisplayName()).isEqualTo("Moderator");
    }

    @Test
    void listPinnedMessagesBackfillsPastFilteredFirstPage() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        UUID visibleMessageId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);

        List<ChatPinEventEntity> firstPage = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> pinEvent(chatId, UUID.randomUUID(), adminUserId, false, Instant.parse("2026-03-12T11:59:30Z")))
                .toList();
        List<ChatPinEventEntity> secondPage = List.of(pinEvent(chatId, visibleMessageId, adminUserId, true, null));

        UserEntity admin = new UserEntity();
        admin.setId(adminUserId);
        admin.setDisplayName("Moderator");

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(chatPinEventRepository.findAllByChatIdOrderByPinnedAtDesc(eq(chatId), eq(org.springframework.data.domain.PageRequest.of(0, 6))))
                .thenReturn(firstPage);
        when(chatPinEventRepository.findAllByChatIdOrderByPinnedAtDesc(eq(chatId), eq(org.springframework.data.domain.PageRequest.of(1, 6))))
                .thenReturn(secondPage);
        when(userRepository.findAllById(List.of(adminUserId))).thenReturn(List.of(admin));
        for (ChatPinEventEntity hiddenPin : firstPage) {
            when(messageService.getMessage(requesterId, hiddenPin.getMessageId())).thenThrow(
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found")
            );
        }
        when(messageService.getMessage(requesterId, visibleMessageId)).thenReturn(message(chatId, visibleMessageId, "Older visible pin"));

        List<PinnedMessageHistoryResponse> response = chatPinHistoryService.listPinnedMessages(requesterId, chatId, 1);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).message().messageId()).isEqualTo(visibleMessageId);
        assertThat(response.get(0).message().text()).isEqualTo("Older visible pin");
        verify(chatPinEventRepository).findAllByChatIdOrderByPinnedAtDesc(eq(chatId), eq(org.springframework.data.domain.PageRequest.of(1, 6)));
    }

    private ChatPinEventEntity pinEvent(
            UUID chatId,
            UUID messageId,
            UUID pinnedByUserId,
            boolean active,
            Instant unpinnedAt
    ) {
        ChatPinEventEntity pinEvent = new ChatPinEventEntity();
        pinEvent.setId(UUID.randomUUID());
        pinEvent.setChatId(chatId);
        pinEvent.setMessageId(messageId);
        pinEvent.setPinnedByUserId(pinnedByUserId);
        pinEvent.setPinnedAt(Instant.parse(active ? "2026-03-12T12:00:00Z" : "2026-03-12T11:59:00Z"));
        pinEvent.setActive(active);
        pinEvent.setUnpinnedAt(unpinnedAt);
        return pinEvent;
    }

    private ChatMessageResponse message(UUID chatId, UUID messageId, String text) {
        return new ChatMessageResponse(
                chatId,
                messageId,
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
                text,
                List.of(),
                "TEXT",
                null,
                false,
                null,
                null,
                null,
                Instant.parse("2026-03-12T12:00:00Z"),
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
    }
}
