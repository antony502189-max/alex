package com.alex.messenger.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.MessageLiveLocationPayload;
import com.alex.messenger.message.dto.UpdateLiveLocationRequest;
import com.alex.messenger.message.dto.VotePollRequest;
import java.time.Instant;
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
class MessageControllerTest {

    @Mock
    private MessageService messageService;

    @Mock
    private MessageTranslationService messageTranslationService;

    private MessageController messageController;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        messageController = new MessageController(messageService, messageTranslationService);
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
    void updateLiveLocationUsesAuthenticatedUser() {
        UUID messageId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UpdateLiveLocationRequest request = new UpdateLiveLocationRequest(54.1, 27.7, "Updated point", "New address");
        ChatMessageResponse response = liveLocationResponse(chatId, messageId, currentUserId, true);

        when(messageService.updateLiveLocation(currentUserId, messageId, request)).thenReturn(response);

        ResponseEntity<ChatMessageResponse> entity = messageController.updateLiveLocation(messageId, request);

        assertThat(entity.getBody()).isEqualTo(response);
        assertThat(entity.getBody().liveLocation()).isNotNull();
        assertThat(entity.getBody().liveLocation().title()).isEqualTo("Updated point");
        verify(messageService).updateLiveLocation(currentUserId, messageId, request);
    }

    @Test
    void stopLiveLocationUsesAuthenticatedUser() {
        UUID messageId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatMessageResponse response = liveLocationResponse(chatId, messageId, currentUserId, false);

        when(messageService.stopLiveLocation(currentUserId, messageId)).thenReturn(response);

        ResponseEntity<ChatMessageResponse> entity = messageController.stopLiveLocation(messageId);

        assertThat(entity.getBody()).isEqualTo(response);
        assertThat(entity.getBody().liveLocation()).isNotNull();
        assertThat(entity.getBody().liveLocation().active()).isFalse();
        verify(messageService).stopLiveLocation(currentUserId, messageId);
    }

    @Test
    void votePollUsesAuthenticatedUser() {
        UUID messageId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        VotePollRequest request = new VotePollRequest(List.of(UUID.randomUUID()));
        ChatMessageResponse response = liveLocationResponse(chatId, messageId, currentUserId, false);

        when(messageService.votePoll(currentUserId, messageId, request)).thenReturn(response);

        ResponseEntity<ChatMessageResponse> entity = messageController.votePoll(messageId, request);

        assertThat(entity.getBody()).isEqualTo(response);
        verify(messageService).votePoll(currentUserId, messageId, request);
    }

    private ChatMessageResponse liveLocationResponse(UUID chatId, UUID messageId, UUID senderId, boolean active) {
        return new ChatMessageResponse(
                chatId,
                messageId,
                null,
                senderId,
                "Alice",
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
                "",
                List.of(),
                "LIVE_LOCATION",
                null,
                false,
                null,
                new MessageLiveLocationPayload(
                        54.1,
                        27.7,
                        "Updated point",
                        "New address",
                        1_800,
                        Instant.parse("2999-01-01T00:00:00Z"),
                        Instant.parse("2026-03-19T16:05:00Z"),
                        active ? null : Instant.parse("2026-03-19T16:07:00Z"),
                        active
                ),
                null,
                null,
                Instant.parse("2026-03-19T16:00:00Z"),
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
                Instant.parse("2026-03-19T16:05:00Z"),
                null
        );
    }
}
