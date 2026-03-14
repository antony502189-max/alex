package com.alex.messenger.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.alex.messenger.attachment.dto.ModerateAttachmentRequest;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.crypto.ChatEncryptionService;
import com.alex.messenger.media.MediaObjectReference;
import com.alex.messenger.media.MediaProcessingService;
import com.alex.messenger.media.MediaService;
import com.alex.messenger.media.PresignedMediaAccess;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private ChatEncryptionService chatEncryptionService;

    @Mock
    private MediaService mediaService;

    @Mock
    private MediaProcessingService mediaProcessingService;

    private AttachmentService attachmentService;

    @BeforeEach
    void setUp() {
        attachmentService = new AttachmentService(
                attachmentRepository,
                chatService,
                chatEncryptionService,
                mediaService,
                mediaProcessingService,
                25L * 1024L * 1024L
        );
    }

    @Test
    void uploadPersistsProvidedWaveformAndDimensions() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "voice-message.ogg",
                "audio/ogg",
                new byte[]{1, 2, 3, 4}
        );

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(mediaService.upload(
                eq(chatId),
                any(UUID.class),
                eq("voice-message.ogg"),
                eq("audio/ogg"),
                eq(4L),
                any(InputStream.class)
        )).thenReturn(new MediaObjectReference(
                "media",
                "attachments/voice-message.ogg",
                "s3://media/attachments/voice-message.ogg"
        ));
        when(mediaService.buildDownloadAccess("media", "attachments/voice-message.ogg"))
                .thenReturn(new PresignedMediaAccess(
                        "https://cdn.example/attachments/voice-message.ogg",
                        Instant.parse("2026-03-12T12:00:00Z")
                ));
        when(attachmentRepository.save(any(AttachmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = attachmentService.upload(
                requesterId,
                chatId,
                "VOICE",
                1800L,
                null,
                null,
                "12,34,56,78",
                null,
                null,
                file
        );

        ArgumentCaptor<AttachmentEntity> captor = ArgumentCaptor.forClass(AttachmentEntity.class);
        verify(attachmentRepository).save(captor.capture());
        AttachmentEntity saved = captor.getValue();

        verify(mediaProcessingService).enqueueAttachmentPreview(saved);
        assertThat(saved.getKind()).isEqualTo("VOICE");
        assertThat(saved.getDurationMs()).isEqualTo(1800L);
        assertThat(saved.getWaveform()).isEqualTo("12,34,56,78");
        assertThat(response.kind()).isEqualTo("VOICE");
        assertThat(response.waveform()).containsExactly(12, 34, 56, 78);
        assertThat(response.streamingSupported()).isTrue();
        assertThat(response.voiceNote()).isTrue();
        assertThat(response.roundMessage()).isFalse();
        assertThat(response.previewUrl()).isNull();
        assertThat(response.downloadUrl()).isEqualTo("https://cdn.example/attachments/voice-message.ogg");
    }

    @Test
    void uploadAutoDetectsImageDimensionsWhenNotProvided() throws IOException {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);

        byte[] imageBytes = createPng(6, 4);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.png",
                "image/png",
                imageBytes
        );

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(mediaService.upload(
                eq(chatId),
                any(UUID.class),
                eq("photo.png"),
                eq("image/png"),
                eq((long) imageBytes.length),
                any(InputStream.class)
        )).thenReturn(new MediaObjectReference(
                "media",
                "attachments/photo.png",
                "s3://media/attachments/photo.png"
        ));
        when(mediaService.buildDownloadAccess("media", "attachments/photo.png"))
                .thenReturn(new PresignedMediaAccess(
                        "https://cdn.example/attachments/photo.png",
                        Instant.parse("2026-03-12T12:05:00Z")
                ));
        when(attachmentRepository.save(any(AttachmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = attachmentService.upload(
                requesterId,
                chatId,
                "IMAGE",
                null,
                null,
                null,
                null,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                2,
                file
        );

        ArgumentCaptor<AttachmentEntity> captor = ArgumentCaptor.forClass(AttachmentEntity.class);
        verify(attachmentRepository).save(captor.capture());
        AttachmentEntity saved = captor.getValue();

        verify(mediaProcessingService).enqueueAttachmentPreview(saved);
        assertThat(saved.getWidth()).isEqualTo(6);
        assertThat(saved.getHeight()).isEqualTo(4);
        assertThat(saved.getAlbumId()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(saved.getAlbumItemIndex()).isEqualTo(2);
        assertThat(response.width()).isEqualTo(6);
        assertThat(response.height()).isEqualTo(4);
        assertThat(response.albumId()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(response.albumItemIndex()).isEqualTo(2);
        assertThat(response.previewUrl()).isEqualTo("https://cdn.example/attachments/photo.png");
        assertThat(response.thumbnailUrl()).isEqualTo("https://cdn.example/attachments/photo.png");
        assertThat(response.waveform()).isEmpty();
    }

    @Test
    void rejectedModeratedAttachmentIsHiddenFromRegularViewer() {
        UUID moderatorId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);

        AttachmentEntity attachment = new AttachmentEntity();
        attachment.setId(attachmentId);
        attachment.setChatId(chatId);
        attachment.setUploaderUserId(UUID.randomUUID());
        attachment.setOriginalFileName("photo.png");
        attachment.setContentType("image/png");
        attachment.setKind("IMAGE");
        attachment.setFileSizeBytes(10L);
        attachment.setStorageProvider("S3");
        attachment.setBucketName("media");
        attachment.setObjectKey("attachments/photo.png");
        attachment.setPreviewBucketName("media");
        attachment.setPreviewObjectKey("attachments/photo-preview.jpg");
        attachment.setProcessingStatus("READY");
        attachment.setModerationStatus("APPROVED");

        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));
        when(attachmentRepository.findAllByIdIn(any())).thenReturn(List.of(attachment));
        when(attachmentRepository.save(any(AttachmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatService.getOwnedChat(moderatorId, chatId)).thenReturn(chat);
        when(chatService.getOwnedChat(viewerId, chatId)).thenReturn(chat);
        when(chatService.hasMessageModerationPermission(moderatorId, chatId)).thenReturn(true);
        when(chatService.hasMessageModerationPermission(viewerId, chatId)).thenReturn(false);
        when(mediaService.buildDownloadAccess("media", "attachments/photo.png"))
                .thenReturn(new PresignedMediaAccess(
                        "https://cdn.example/attachments/photo.png",
                        Instant.parse("2026-03-12T12:05:00Z")
                ));
        when(mediaService.buildDownloadAccess("media", "attachments/photo-preview.jpg"))
                .thenReturn(new PresignedMediaAccess(
                        "https://cdn.example/attachments/photo-preview.jpg",
                        Instant.parse("2026-03-12T12:05:00Z")
                ));

        var moderated = attachmentService.reviewModeration(
                moderatorId,
                attachmentId,
                new ModerateAttachmentRequest("REJECTED", "Unsafe", true)
        );
        var viewerResponse = attachmentService.getResponses(viewerId, List.of(attachmentId)).get(0);

        assertThat(moderated.moderationStatus()).isEqualTo("REJECTED");
        assertThat(moderated.blockedByModeration()).isFalse();
        assertThat(viewerResponse.moderationStatus()).isEqualTo("REJECTED");
        assertThat(viewerResponse.blockedByModeration()).isTrue();
        assertThat(viewerResponse.downloadUrl()).isNull();
        assertThat(viewerResponse.previewUrl()).isNull();
    }

    @Test
    void getResponsesRejectsAttachmentsFromInaccessibleChat() {
        UUID viewerId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        AttachmentEntity attachment = new AttachmentEntity();
        attachment.setId(attachmentId);
        attachment.setChatId(chatId);

        when(attachmentRepository.findAllByIdIn(any())).thenReturn(List.of(attachment));
        when(chatService.getOwnedChat(viewerId, chatId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied"));

        ResponseStatusException exception = catchThrowableOfType(
                () -> attachmentService.getResponses(viewerId, List.of(attachmentId)),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getResponsesWithoutRequesterDoesNotExposeAccessUrls() {
        UUID attachmentId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        AttachmentEntity attachment = new AttachmentEntity();
        attachment.setId(attachmentId);
        attachment.setChatId(chatId);
        attachment.setOriginalFileName("photo.png");
        attachment.setContentType("image/png");
        attachment.setKind("IMAGE");
        attachment.setFileSizeBytes(10L);
        attachment.setStorageProvider("S3");
        attachment.setBucketName("media");
        attachment.setObjectKey("attachments/photo.png");
        attachment.setPreviewBucketName("media");
        attachment.setPreviewObjectKey("attachments/photo-preview.jpg");

        when(attachmentRepository.findAllByIdIn(any())).thenReturn(List.of(attachment));

        var response = attachmentService.getResponses((UUID) null, List.of(attachmentId)).get(0);

        assertThat(response.downloadUrl()).isNull();
        assertThat(response.previewUrl()).isNull();
        assertThat(response.thumbnailUrl()).isNull();
        assertThat(response.requiresAuthorization()).isTrue();
        verify(mediaService, never()).buildDownloadAccess(anyString(), anyString());
    }

    @Test
    void cloneAttachmentsToChatReuploadsMediaAndRemapsAlbum() {
        UUID requesterId = UUID.randomUUID();
        UUID sourceChatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID firstAttachmentId = UUID.randomUUID();
        UUID secondAttachmentId = UUID.randomUUID();
        UUID sourceAlbumId = UUID.randomUUID();

        ChatEntity sourceChat = new ChatEntity();
        sourceChat.setId(sourceChatId);
        ChatEntity targetChat = new ChatEntity();
        targetChat.setId(targetChatId);

        AttachmentEntity first = new AttachmentEntity();
        first.setId(firstAttachmentId);
        first.setChatId(sourceChatId);
        first.setUploaderUserId(UUID.randomUUID());
        first.setOriginalFileName("one.jpg");
        first.setContentType("image/jpeg");
        first.setKind("IMAGE");
        first.setFileSizeBytes(3L);
        first.setAlbumId(sourceAlbumId);
        first.setAlbumItemIndex(0);
        first.setStorageProvider("S3");
        first.setBucketName("media");
        first.setObjectKey("chats/source/one.jpg");
        first.setStoragePath("s3://media/chats/source/one.jpg");
        first.setModerationStatus("APPROVED");

        AttachmentEntity second = new AttachmentEntity();
        second.setId(secondAttachmentId);
        second.setChatId(sourceChatId);
        second.setUploaderUserId(UUID.randomUUID());
        second.setOriginalFileName("two.jpg");
        second.setContentType("image/jpeg");
        second.setKind("IMAGE");
        second.setFileSizeBytes(4L);
        second.setAlbumId(sourceAlbumId);
        second.setAlbumItemIndex(1);
        second.setStorageProvider("S3");
        second.setBucketName("media");
        second.setObjectKey("chats/source/two.jpg");
        second.setStoragePath("s3://media/chats/source/two.jpg");
        second.setModerationStatus("APPROVED");

        when(chatService.getOwnedChat(requesterId, targetChatId)).thenReturn(targetChat);
        when(chatService.getOwnedChat(requesterId, sourceChatId)).thenReturn(sourceChat);
        when(attachmentRepository.findAllByIdIn(any())).thenReturn(List.of(first, second));
        when(mediaService.downloadObjectBytes("media", "chats/source/one.jpg")).thenReturn(new byte[]{1, 2, 3});
        when(mediaService.downloadObjectBytes("media", "chats/source/two.jpg")).thenReturn(new byte[]{4, 5, 6, 7});
        when(mediaService.upload(
                eq(targetChatId),
                any(UUID.class),
                anyString(),
                anyString(),
                anyLong(),
                any(InputStream.class)
        )).thenAnswer(invocation -> {
            UUID clonedId = invocation.getArgument(1, UUID.class);
            return new MediaObjectReference(
                    "media",
                    "chats/%s/%s".formatted(targetChatId, clonedId),
                    "s3://media/chats/%s/%s".formatted(targetChatId, clonedId)
            );
        });
        when(attachmentRepository.save(any(AttachmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<UUID> clonedIds = attachmentService.cloneAttachmentsToChat(
                requesterId,
                targetChatId,
                List.of(firstAttachmentId, secondAttachmentId)
        );

        ArgumentCaptor<AttachmentEntity> captor = ArgumentCaptor.forClass(AttachmentEntity.class);
        verify(attachmentRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<AttachmentEntity> saved = captor.getAllValues();

        assertThat(clonedIds).hasSize(2).doesNotContain(firstAttachmentId, secondAttachmentId);
        assertThat(saved).extracting(AttachmentEntity::getChatId).containsOnly(targetChatId);
        assertThat(saved).extracting(AttachmentEntity::getUploaderUserId).containsOnly(requesterId);
        assertThat(saved).extracting(AttachmentEntity::getAlbumItemIndex).containsExactly(0, 1);
        assertThat(saved.get(0).getAlbumId()).isNotNull().isEqualTo(saved.get(1).getAlbumId()).isNotEqualTo(sourceAlbumId);
        assertThat(saved).extracting(AttachmentEntity::getStoragePath).allMatch(path -> path.startsWith("s3://media/chats/" + targetChatId));
        verify(mediaService).downloadObjectBytes("media", "chats/source/one.jpg");
        verify(mediaService).downloadObjectBytes("media", "chats/source/two.jpg");
        verify(mediaProcessingService, org.mockito.Mockito.times(2)).enqueueAttachmentPreview(any(AttachmentEntity.class));
    }

    private byte[] createPng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }
}
