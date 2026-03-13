package com.alex.messenger.media;

import com.alex.messenger.attachment.AttachmentEntity;
import com.alex.messenger.attachment.AttachmentRepository;
import com.alex.messenger.story.StoryEntity;
import com.alex.messenger.story.StoryRepository;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaProcessingService {

    private static final String JOB_TYPE_IMAGE_PREVIEW = "IMAGE_PREVIEW";

    private final MediaProcessingJobRepository mediaProcessingJobRepository;
    private final AttachmentRepository attachmentRepository;
    private final StoryRepository storyRepository;
    private final MediaService mediaService;
    private final int batchSize;
    private final int previewMaxDimension;
    private final int thumbnailMaxDimension;

    public MediaProcessingService(
            MediaProcessingJobRepository mediaProcessingJobRepository,
            AttachmentRepository attachmentRepository,
            StoryRepository storyRepository,
            MediaService mediaService,
            @Value("${alex.media.processing.batch-size}") int batchSize,
            @Value("${alex.media.processing.preview-max-dimension}") int previewMaxDimension,
            @Value("${alex.media.processing.thumbnail-max-dimension}") int thumbnailMaxDimension
    ) {
        this.mediaProcessingJobRepository = mediaProcessingJobRepository;
        this.attachmentRepository = attachmentRepository;
        this.storyRepository = storyRepository;
        this.mediaService = mediaService;
        this.batchSize = batchSize;
        this.previewMaxDimension = previewMaxDimension;
        this.thumbnailMaxDimension = thumbnailMaxDimension;
    }

    @Transactional
    public void enqueueAttachmentPreview(AttachmentEntity attachment) {
        if (!requiresImagePreview(attachment.getKind(), attachment.getContentType())
                || attachment.getBucketName() == null
                || attachment.getObjectKey() == null) {
            attachment.setProcessingStatus("NOT_REQUIRED");
            return;
        }

        attachment.setProcessingStatus("PENDING");
        if (mediaProcessingJobRepository.findByOwnerTypeAndOwnerIdAndJobType(
                "ATTACHMENT",
                attachment.getId(),
                JOB_TYPE_IMAGE_PREVIEW
        ).isPresent()) {
            return;
        }

        MediaProcessingJobEntity job = new MediaProcessingJobEntity();
        job.setOwnerType("ATTACHMENT");
        job.setOwnerId(attachment.getId());
        job.setJobType(JOB_TYPE_IMAGE_PREVIEW);
        job.setSourceBucketName(attachment.getBucketName());
        job.setSourceObjectKey(attachment.getObjectKey());
        job.setStatus("PENDING");
        mediaProcessingJobRepository.save(job);
    }

    @Transactional
    public void enqueueStoryPreview(StoryEntity story) {
        if (!requiresImagePreview(story.getMediaKind(), story.getMediaContentType())
                || story.getMediaBucketName() == null
                || story.getMediaObjectKey() == null) {
            story.setMediaProcessingStatus("NOT_REQUIRED");
            return;
        }

        story.setMediaProcessingStatus("PENDING");
        if (mediaProcessingJobRepository.findByOwnerTypeAndOwnerIdAndJobType(
                "STORY",
                story.getId(),
                JOB_TYPE_IMAGE_PREVIEW
        ).isPresent()) {
            return;
        }

        MediaProcessingJobEntity job = new MediaProcessingJobEntity();
        job.setOwnerType("STORY");
        job.setOwnerId(story.getId());
        job.setJobType(JOB_TYPE_IMAGE_PREVIEW);
        job.setSourceBucketName(story.getMediaBucketName());
        job.setSourceObjectKey(story.getMediaObjectKey());
        job.setStatus("PENDING");
        mediaProcessingJobRepository.save(job);
    }

    @Transactional
    public void processPendingJobs(Instant now) {
        List<MediaProcessingJobEntity> jobs = mediaProcessingJobRepository.findByStatusOrderByCreatedAtAsc(
                "PENDING",
                PageRequest.of(0, batchSize)
        );

        for (MediaProcessingJobEntity job : jobs) {
            processJob(job, now);
        }
    }

    private void processJob(MediaProcessingJobEntity job, Instant now) {
        job.setStatus("PROCESSING");
        job.setAttempts(job.getAttempts() + 1);
        mediaProcessingJobRepository.save(job);

        try {
            if ("ATTACHMENT".equals(job.getOwnerType())) {
                processAttachmentPreview(job);
            } else if ("STORY".equals(job.getOwnerType())) {
                processStoryPreview(job);
            } else {
                throw new IllegalStateException("Unsupported media processing owner type: " + job.getOwnerType());
            }
            job.setStatus("COMPLETED");
            job.setErrorMessage(null);
        } catch (RuntimeException exception) {
            job.setStatus("FAILED");
            job.setErrorMessage(exception.getMessage());
        }

        mediaProcessingJobRepository.save(job);
    }

    private void processAttachmentPreview(MediaProcessingJobEntity job) {
        AttachmentEntity attachment = attachmentRepository.findById(job.getOwnerId())
                .orElseThrow(() -> new IllegalStateException("Attachment not found for media processing job"));
        PreviewRenderResult preview = renderPreview(
                job.getSourceBucketName(),
                job.getSourceObjectKey(),
                previewMaxDimension
        );
        MediaObjectReference previewReference = mediaService.uploadAttachmentPreview(
                attachment.getChatId(),
                attachment.getId(),
                attachment.getOriginalFileName(),
                preview.bytes().length,
                new ByteArrayInputStream(preview.bytes())
        );
        PreviewRenderResult thumbnail = renderPreview(
                job.getSourceBucketName(),
                job.getSourceObjectKey(),
                thumbnailMaxDimension
        );
        MediaObjectReference thumbnailReference = mediaService.uploadAttachmentThumbnail(
                attachment.getChatId(),
                attachment.getId(),
                attachment.getOriginalFileName(),
                thumbnail.bytes().length,
                new ByteArrayInputStream(thumbnail.bytes())
        );

        attachment.setPreviewBucketName(previewReference.bucketName());
        attachment.setPreviewObjectKey(previewReference.objectKey());
        attachment.setThumbnailBucketName(thumbnailReference.bucketName());
        attachment.setThumbnailObjectKey(thumbnailReference.objectKey());
        attachment.setProcessingStatus("READY");
        attachmentRepository.save(attachment);

        job.setDerivativeBucketName(previewReference.bucketName());
        job.setDerivativeObjectKey(previewReference.objectKey());
    }

    private void processStoryPreview(MediaProcessingJobEntity job) {
        StoryEntity story = storyRepository.findById(job.getOwnerId())
                .orElseThrow(() -> new IllegalStateException("Story not found for media processing job"));
        PreviewRenderResult preview = renderPreview(
                job.getSourceBucketName(),
                job.getSourceObjectKey(),
                previewMaxDimension
        );
        MediaObjectReference previewReference = mediaService.uploadStoryPreview(
                story.getOwnerUserId(),
                story.getId(),
                story.getMediaFileName(),
                preview.bytes().length,
                new ByteArrayInputStream(preview.bytes())
        );

        story.setMediaPreviewBucketName(previewReference.bucketName());
        story.setMediaPreviewObjectKey(previewReference.objectKey());
        story.setMediaProcessingStatus("READY");
        storyRepository.save(story);

        job.setDerivativeBucketName(previewReference.bucketName());
        job.setDerivativeObjectKey(previewReference.objectKey());
    }

    private PreviewRenderResult renderPreview(String bucketName, String objectKey, int maxDimension) {
        byte[] sourceBytes = mediaService.downloadObjectBytes(bucketName, objectKey);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(sourceBytes);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            BufferedImage source = ImageIO.read(inputStream);
            if (source == null) {
                throw new IllegalStateException("Unsupported preview image format");
            }

            BufferedImage scaled = scaleImage(source, maxDimension);
            if (!ImageIO.write(scaled, "jpg", outputStream)) {
                throw new IllegalStateException("Unable to encode preview image");
            }
            return new PreviewRenderResult(outputStream.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to render preview image", exception);
        }
    }

    private BufferedImage scaleImage(BufferedImage source, int requestedMaxDimension) {
        int sourceWidth = Math.max(1, source.getWidth());
        int sourceHeight = Math.max(1, source.getHeight());
        int maxDimension = Math.max(96, requestedMaxDimension);

        double scale = Math.min(1d, Math.min(
                (double) maxDimension / sourceWidth,
                (double) maxDimension / sourceHeight
        ));

        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));

        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setBackground(java.awt.Color.WHITE);
            graphics.clearRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private boolean requiresImagePreview(String kind, String contentType) {
        String normalizedKind = kind != null ? kind.trim().toUpperCase() : "";
        String normalizedContentType = contentType != null ? contentType.trim().toLowerCase() : "";
        if ("IMAGE".equals(normalizedKind) || "GIF".equals(normalizedKind)) {
            return true;
        }
        return normalizedContentType.startsWith("image/");
    }

    private record PreviewRenderResult(byte[] bytes) {
    }
}
