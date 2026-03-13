package com.alex.messenger.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.attachment.AttachmentEntity;
import com.alex.messenger.attachment.AttachmentRepository;
import com.alex.messenger.story.StoryEntity;
import com.alex.messenger.story.StoryRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaProcessingServiceTest {

    @Mock
    private MediaProcessingJobRepository mediaProcessingJobRepository;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private StoryRepository storyRepository;

    @Mock
    private MediaService mediaService;

    private MediaProcessingService mediaProcessingService;

    @BeforeEach
    void setUp() {
        mediaProcessingService = new MediaProcessingService(
                mediaProcessingJobRepository,
                attachmentRepository,
                storyRepository,
                mediaService,
                10,
                512,
                256
        );
    }

    @Test
    void processPendingAttachmentPreviewCreatesDerivativeAndMarksReady() throws IOException {
        UUID attachmentId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        AttachmentEntity attachment = new AttachmentEntity();
        attachment.setId(attachmentId);
        attachment.setChatId(chatId);
        attachment.setOriginalFileName("photo.png");
        attachment.setKind("IMAGE");
        attachment.setContentType("image/png");
        attachment.setBucketName("media");
        attachment.setObjectKey("chats/source/photo.png");
        attachment.setProcessingStatus("PENDING");

        MediaProcessingJobEntity job = new MediaProcessingJobEntity();
        job.setOwnerType("ATTACHMENT");
        job.setOwnerId(attachmentId);
        job.setJobType("IMAGE_PREVIEW");
        job.setSourceBucketName("media");
        job.setSourceObjectKey("chats/source/photo.png");
        job.setStatus("PENDING");
        job.setCreatedAt(Instant.parse("2026-03-12T10:00:00Z"));

        when(mediaProcessingJobRepository.findByStatusOrderByCreatedAtAsc(eq("PENDING"), any()))
                .thenReturn(List.of(job));
        when(mediaProcessingJobRepository.save(any(MediaProcessingJobEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));
        when(attachmentRepository.save(any(AttachmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mediaService.downloadObjectBytes("media", "chats/source/photo.png")).thenReturn(createPng(1600, 900));
        when(mediaService.uploadAttachmentPreview(
                eq(chatId),
                eq(attachmentId),
                eq("photo.png"),
                any(Long.class),
                any()
        )).thenReturn(new MediaObjectReference(
                "media",
                "chats/previews/photo-preview.jpg",
                "s3://media/chats/previews/photo-preview.jpg"
        ));
        when(mediaService.uploadAttachmentThumbnail(
                eq(chatId),
                eq(attachmentId),
                eq("photo.png"),
                any(Long.class),
                any()
        )).thenReturn(new MediaObjectReference(
                "media",
                "chats/thumbnails/photo-thumb.jpg",
                "s3://media/chats/thumbnails/photo-thumb.jpg"
        ));

        mediaProcessingService.processPendingJobs(Instant.parse("2026-03-12T10:05:00Z"));

        assertThat(attachment.getPreviewBucketName()).isEqualTo("media");
        assertThat(attachment.getPreviewObjectKey()).isEqualTo("chats/previews/photo-preview.jpg");
        assertThat(attachment.getThumbnailBucketName()).isEqualTo("media");
        assertThat(attachment.getThumbnailObjectKey()).isEqualTo("chats/thumbnails/photo-thumb.jpg");
        assertThat(attachment.getProcessingStatus()).isEqualTo("READY");
        assertThat(job.getStatus()).isEqualTo("COMPLETED");
        assertThat(job.getDerivativeBucketName()).isEqualTo("media");
        assertThat(job.getDerivativeObjectKey()).isEqualTo("chats/previews/photo-preview.jpg");
        verify(attachmentRepository).save(attachment);
    }

    @Test
    void processPendingStoryPreviewCreatesDerivativeAndMarksReady() throws IOException {
        UUID storyId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();

        StoryEntity story = new StoryEntity();
        story.setId(storyId);
        story.setOwnerUserId(ownerUserId);
        story.setMediaKind("IMAGE");
        story.setMediaFileName("story.png");
        story.setMediaContentType("image/png");
        story.setMediaBucketName("media");
        story.setMediaObjectKey("stories/source/story.png");
        story.setMediaProcessingStatus("PENDING");

        MediaProcessingJobEntity job = new MediaProcessingJobEntity();
        job.setOwnerType("STORY");
        job.setOwnerId(storyId);
        job.setJobType("IMAGE_PREVIEW");
        job.setSourceBucketName("media");
        job.setSourceObjectKey("stories/source/story.png");
        job.setStatus("PENDING");
        job.setCreatedAt(Instant.parse("2026-03-12T11:00:00Z"));

        when(mediaProcessingJobRepository.findByStatusOrderByCreatedAtAsc(eq("PENDING"), any()))
                .thenReturn(List.of(job));
        when(mediaProcessingJobRepository.save(any(MediaProcessingJobEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyRepository.save(any(StoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mediaService.downloadObjectBytes("media", "stories/source/story.png")).thenReturn(createPng(720, 1280));
        when(mediaService.uploadStoryPreview(
                eq(ownerUserId),
                eq(storyId),
                eq("story.png"),
                any(Long.class),
                any()
        )).thenReturn(new MediaObjectReference(
                "media",
                "stories/previews/story-preview.jpg",
                "s3://media/stories/previews/story-preview.jpg"
        ));

        mediaProcessingService.processPendingJobs(Instant.parse("2026-03-12T11:05:00Z"));

        assertThat(story.getMediaPreviewBucketName()).isEqualTo("media");
        assertThat(story.getMediaPreviewObjectKey()).isEqualTo("stories/previews/story-preview.jpg");
        assertThat(story.getMediaProcessingStatus()).isEqualTo("READY");
        assertThat(job.getStatus()).isEqualTo("COMPLETED");
        assertThat(job.getDerivativeBucketName()).isEqualTo("media");
        assertThat(job.getDerivativeObjectKey()).isEqualTo("stories/previews/story-preview.jpg");
        verify(storyRepository).save(story);
    }

    private byte[] createPng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }
}
