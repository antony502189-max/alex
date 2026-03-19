package com.alex.messenger.chat.folder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.dto.ChatFolderResponse;
import com.alex.messenger.chat.dto.UpsertChatFolderRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ChatFolderControllerTest {

    @Mock
    private ChatFolderService chatFolderService;

    private ChatFolderController chatFolderController;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        chatFolderController = new ChatFolderController(chatFolderService);
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
    void createUsesAuthenticatedUser() {
        UUID folderId = UUID.randomUUID();
        UpsertChatFolderRequest request = new UpsertChatFolderRequest(
                "Work",
                0,
                null,
                List.of(UUID.randomUUID()),
                List.of(UUID.randomUUID()),
                List.of("DIRECT"),
                true,
                false,
                false,
                false,
                true,
                false,
                true,
                false,
                true
        );
        ChatFolderResponse response = folder(folderId);

        when(chatFolderService.create(currentUserId, request)).thenReturn(response);

        ResponseEntity<ChatFolderResponse> entity = chatFolderController.create(request);

        assertThat(entity.getBody()).isEqualTo(response);
        assertThat(entity.getBody().folderId()).isEqualTo(folderId);
        verify(chatFolderService).create(currentUserId, request);
    }

    @Test
    void deleteUsesAuthenticatedUser() {
        UUID folderId = UUID.randomUUID();
        List<ChatFolderResponse> response = List.of(folder(folderId));

        when(chatFolderService.delete(currentUserId, folderId)).thenReturn(response);

        ResponseEntity<List<ChatFolderResponse>> entity = chatFolderController.delete(folderId);

        assertThat(entity.getBody()).isEqualTo(response);
        verify(chatFolderService).delete(currentUserId, folderId);
    }

    private ChatFolderResponse folder(UUID folderId) {
        return new ChatFolderResponse(
                folderId,
                "Work",
                0,
                List.of(UUID.randomUUID()),
                List.of(UUID.randomUUID()),
                List.of(UUID.randomUUID()),
                List.of("DIRECT"),
                true,
                false,
                false,
                false,
                true,
                false,
                true,
                false,
                true
        );
    }
}
