package com.alex.messenger.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.dto.ChatSummaryResponse;
import com.alex.messenger.chat.dto.ChatMemberResponse;
import com.alex.messenger.chat.dto.ChatLastMessagePreviewResponse;
import com.alex.messenger.chat.dto.JoinByPublicUsernameRequest;
import com.alex.messenger.chat.dto.JoinChatResultResponse;
import com.alex.messenger.chat.dto.UpdateMemberPermissionsRequest;
import com.alex.messenger.chat.folder.ChatFolderService;
import com.alex.messenger.chat.forum.ForumTopicService;
import com.alex.messenger.message.MessageDeliveryService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private ChatPinHistoryService chatPinHistoryService;

    @Mock
    private ChatReadEventPublisher chatReadEventPublisher;

    @Mock
    private ChatPinEventPublisher chatPinEventPublisher;

    @Mock
    private TypingEventPublisher typingEventPublisher;

    @Mock
    private MessageDeliveryService messageDeliveryService;

    @Mock
    private ForumTopicService forumTopicService;

    @Mock
    private ChatFolderService chatFolderService;

    private ChatController chatController;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        chatController = new ChatController(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
        currentUserId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUserId.toString(), "test")
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listChatsByFolderAppliesPaginationHeaders() {
        UUID folderId = UUID.randomUUID();
        UUID firstChatId = UUID.randomUUID();
        UUID secondChatId = UUID.randomUUID();
        UUID thirdChatId = UUID.randomUUID();

        when(chatFolderService.listChats(currentUserId, folderId, null)).thenReturn(List.of(
                chat(firstChatId, false, null),
                chat(secondChatId, false, null),
                chat(thirdChatId, false, null)
        ));

        ResponseEntity<List<ChatSummaryResponse>> response = chatController.listChats(null, folderId, 2, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(ChatSummaryResponse::chatId)
                .containsExactly(firstChatId, secondChatId);
        assertThat(response.getHeaders().getFirst("X-Chat-Has-More")).isEqualTo("true");
        assertThat(response.getHeaders().getFirst("X-Chat-Limit")).isEqualTo("2");
        assertThat(decodeCursor(response.getHeaders().getFirst("X-Chat-Next-Cursor"))).isEqualTo(2);

        verify(chatFolderService).listChats(currentUserId, folderId, null);
    }

    @Test
    void listChatsByFolderRejectsMalformedCursor() {
        UUID folderId = UUID.randomUUID();

        assertThatThrownBy(() -> chatController.listChats(null, folderId, 2, "%%%bad%%%"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(throwable -> ((ResponseStatusException) throwable).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void pinChatToListUsesAuthenticatedUser() {
        UUID chatId = UUID.randomUUID();
        ChatSummaryResponse response = chat(chatId, true, 0);

        when(chatService.pinChatToList(currentUserId, chatId)).thenReturn(response);

        ResponseEntity<ChatSummaryResponse> entity = chatController.pinChatToList(chatId);

        assertThat(entity.getBody()).isEqualTo(response);
        assertThat(entity.getBody().pinned()).isTrue();
        assertThat(entity.getBody().pinOrder()).isZero();
        verify(chatService).pinChatToList(currentUserId, chatId);
    }

    @Test
    void unpinChatFromListUsesAuthenticatedUser() {
        UUID chatId = UUID.randomUUID();
        ChatSummaryResponse response = chat(chatId, false, null);

        when(chatService.unpinChatFromList(currentUserId, chatId)).thenReturn(response);

        ResponseEntity<ChatSummaryResponse> entity = chatController.unpinChatFromList(chatId);

        assertThat(entity.getBody()).isEqualTo(response);
        assertThat(entity.getBody().pinned()).isFalse();
        assertThat(entity.getBody().pinOrder()).isNull();
        verify(chatService).unpinChatFromList(currentUserId, chatId);
    }

    @Test
    void updatePermissionsUsesAuthenticatedUser() {
        UUID chatId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UpdateMemberPermissionsRequest request =
                new UpdateMemberPermissionsRequest(true, false, null, true, null, null, false);
        ChatMemberResponse response = new ChatMemberResponse(
                targetUserId,
                "+123456789",
                "Moderator",
                null,
                null,
                "ADMIN",
                Instant.parse("2026-03-19T09:00:00Z"),
                null,
                null,
                true,
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                null,
                null
        );

        when(chatService.updateMemberPermissions(currentUserId, chatId, targetUserId, request)).thenReturn(response);

        ResponseEntity<ChatMemberResponse> entity = chatController.updatePermissions(chatId, targetUserId, request);

        assertThat(entity.getBody()).isEqualTo(response);
        verify(chatService).updateMemberPermissions(currentUserId, chatId, targetUserId, request);
    }

    @Test
    void joinByUsernameUsesAuthenticatedUser() {
        JoinByPublicUsernameRequest request = new JoinByPublicUsernameRequest("@telegram_like");
        JoinChatResultResponse response = new JoinChatResultResponse(
                "JOINED",
                null,
                UUID.randomUUID(),
                "General",
                "telegram_like",
                null
        );

        when(chatService.joinByPublicUsername(currentUserId, request.username())).thenReturn(response);

        ResponseEntity<JoinChatResultResponse> entity = chatController.joinByUsername(request);

        assertThat(entity.getBody()).isEqualTo(response);
        verify(chatService).joinByPublicUsername(currentUserId, request.username());
    }

    private ChatSummaryResponse chat(UUID chatId, boolean pinned, Integer pinOrder) {
        return new ChatSummaryResponse(
                chatId,
                "GROUP",
                "Chat",
                null,
                null,
                null,
                null,
                null,
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
                Instant.parse("2026-03-19T10:00:00Z"),
                1,
                null,
                0,
                0,
                0,
                false,
                null,
                null,
                null,
                pinned,
                pinOrder,
                null,
                false,
                true,
                true,
                true,
                new ChatLastMessagePreviewResponse(null, null, null, false, false, null, null, null, null, null)
        );
    }

    private int decodeCursor(String cursor) {
        return Integer.parseInt(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
    }
}
