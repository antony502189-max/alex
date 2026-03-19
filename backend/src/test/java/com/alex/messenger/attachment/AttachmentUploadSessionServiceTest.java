package com.alex.messenger.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.attachment.dto.CreateAttachmentUploadSessionRequest;
import com.alex.messenger.attachment.dto.UploadAttachmentChunkRequest;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.message.dto.MessageAttachmentResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AttachmentUploadSessionServiceTest {

    @Mock
    private AttachmentUploadSessionRepository attachmentUploadSessionRepository;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private ChatService chatService;

    @TempDir
    Path tempDirectory;

    private AttachmentUploadSessionService attachmentUploadSessionService;

    @BeforeEach
    void setUp() {
        attachmentUploadSessionService = new AttachmentUploadSessionService(
                attachmentUploadSessionRepository,
                attachmentService,
                chatService,
                tempDirectory.toString(),
                1024,
                Duration.ofHours(6),
                100,
                25L * 1024L * 1024L
        );
    }

    @Test
    void uploadChunkAppendsBytesToStagedFile() throws Exception {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        AtomicReference<AttachmentUploadSessionEntity> storedSession = new AtomicReference<>();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(attachmentUploadSessionRepository.save(any(AttachmentUploadSessionEntity.class))).thenAnswer(invocation -> {
            AttachmentUploadSessionEntity session = invocation.getArgument(0);
            storedSession.set(session);
            return session;
        });
        when(attachmentUploadSessionRepository.findByIdAndUploaderUserId(any(UUID.class), eq(requesterId))).thenAnswer(invocation -> {
            AttachmentUploadSessionEntity session = storedSession.get();
            if (session == null || !session.getId().equals(invocation.getArgument(0))) {
                return Optional.empty();
            }
            return Optional.of(session);
        });

        var session = attachmentUploadSessionService.createSession(
                requesterId,
                new CreateAttachmentUploadSessionRequest(
                        chatId,
                        "photo.png",
                        "image/png",
                        "IMAGE",
                        5L,
                        null,
                        null,
                        null,
                        true,
                        null,
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        0
                )
        );

        var updatedSession = attachmentUploadSessionService.uploadChunk(
                requesterId,
                session.uploadSessionId(),
                new UploadAttachmentChunkRequest(
                        0L,
                        Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8))
                )
        );

        Path stagedFile = Path.of(storedSession.get().getStoragePath());
        assertThat(Files.exists(stagedFile)).isTrue();
        assertThat(Files.readString(stagedFile)).isEqualTo("hello");
        assertThat(updatedSession.uploadedBytes()).isEqualTo(5L);
        assertThat(updatedSession.complete()).isTrue();
        assertThat(updatedSession.albumId()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(updatedSession.albumItemIndex()).isZero();
        assertThat(updatedSession.hdPhoto()).isTrue();
    }

    @Test
    void completeSessionMarksUploadCompletedAndDeletesTempFile() throws Exception {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        Path stagedFile = tempDirectory.resolve("session.part");

        Files.writeString(stagedFile, "data");

        AttachmentUploadSessionEntity session = new AttachmentUploadSessionEntity();
        session.setId(sessionId);
        session.setChatId(chatId);
        session.setUploaderUserId(requesterId);
        session.setOriginalFileName("clip.mp4");
        session.setContentType("video/mp4");
        session.setKind("VIDEO");
        session.setTotalSizeBytes(4L);
        session.setUploadedBytes(4L);
        session.setStoragePath(stagedFile.toString());
        session.setStatus("ACTIVE");
        session.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));

        MessageAttachmentResponse attachmentResponse = new MessageAttachmentResponse(
                attachmentId,
                "clip.mp4",
                "video/mp4",
                "VIDEO",
                4L,
                null,
                "https://cdn.example/clip.mp4",
                null,
                null,
                null,
                null,
                List.of(),
                Instant.parse("2026-03-12T14:00:00Z"),
                false,
                true,
                false,
                false,
                null,
                  null,
                  "APPROVED",
                  null,
                  false,
                  false,
                  null,
                  null,
                  null,
                  false
          );

        when(attachmentUploadSessionRepository.findByIdAndUploaderUserId(sessionId, requesterId)).thenReturn(Optional.of(session));
        when(attachmentUploadSessionRepository.save(any(AttachmentUploadSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(attachmentService.uploadFromPath(
                requesterId,
                chatId,
                "VIDEO",
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                "clip.mp4",
                "video/mp4",
                4L,
                stagedFile
        )).thenReturn(attachmentResponse);

        var response = attachmentUploadSessionService.completeSession(requesterId, sessionId);

        assertThat(response.attachmentId()).isEqualTo(attachmentId);
        assertThat(session.getStatus()).isEqualTo("COMPLETED");
        assertThat(session.getCompletedAttachmentId()).isEqualTo(attachmentId);
        assertThat(Files.exists(stagedFile)).isFalse();
        verify(attachmentService).uploadFromPath(
                requesterId,
                chatId,
                "VIDEO",
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                "clip.mp4",
                "video/mp4",
                4L,
                stagedFile
        );
    }

    @Test
    void createSessionRejectsHdPhotoForNonImageKind() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);

        ResponseStatusException exception = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> attachmentUploadSessionService.createSession(
                        requesterId,
                        new CreateAttachmentUploadSessionRequest(
                                chatId,
                                "clip.mp4",
                                "video/mp4",
                                "VIDEO",
                                5L,
                                null,
                                null,
                                null,
                                true,
                                null,
                                null,
                                null
                        )
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}
