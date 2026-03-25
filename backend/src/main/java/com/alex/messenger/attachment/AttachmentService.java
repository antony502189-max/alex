package com.alex.messenger.attachment;

import com.alex.messenger.attachment.dto.ModerateAttachmentRequest;
import com.alex.messenger.attachment.dto.TrimAttachmentRequest;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.crypto.ChatEncryptionService;
import com.alex.messenger.media.MediaProcessingService;
import com.alex.messenger.media.MediaObjectReference;
import com.alex.messenger.media.MediaService;
import com.alex.messenger.media.PresignedMediaAccess;
import com.alex.messenger.message.dto.MessageAttachmentResponse;
import com.alex.messenger.sticker.StickerService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final ChatService chatService;
    private final ChatEncryptionService chatEncryptionService;
    private final MediaService mediaService;
    private final MediaProcessingService mediaProcessingService;
    private final StickerService stickerService;
    private final AttachmentAccessTokenService attachmentAccessTokenService;
    private final long maxFileSizeBytes;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            ChatService chatService,
            ChatEncryptionService chatEncryptionService,
            MediaService mediaService,
            MediaProcessingService mediaProcessingService,
            StickerService stickerService,
            AttachmentAccessTokenService attachmentAccessTokenService,
            @Value("${alex.storage.attachments.max-file-size-bytes}") long maxFileSizeBytes
    ) {
        this.attachmentRepository = attachmentRepository;
        this.chatService = chatService;
        this.chatEncryptionService = chatEncryptionService;
        this.mediaService = mediaService;
        this.mediaProcessingService = mediaProcessingService;
        this.stickerService = stickerService;
        this.attachmentAccessTokenService = attachmentAccessTokenService;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Transactional
    public MessageAttachmentResponse upload(
            UUID requesterId,
            UUID chatId,
            String kind,
            Long durationMs,
            Integer width,
            Integer height,
            Boolean hdPhoto,
            String waveform,
            UUID albumId,
            Integer albumItemIndex,
            MultipartFile file
    ) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        chatService.ensureCanPost(chat, requesterId);

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment is empty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Attachment is too large");
        }

        AttachmentMetadataSupport.UploadMetadata metadata = AttachmentMetadataSupport.prepareMultipartUpload(
                file.getOriginalFilename(),
                file.getContentType(),
                kind,
                durationMs,
                width,
                height,
                hdPhoto,
                waveform,
                albumId,
                albumItemIndex
        );
        ImageDimensions imageDimensions = resolveImageDimensions(
                file,
                metadata.kind(),
                metadata.width(),
                metadata.height()
        );

        UUID attachmentId = UUID.randomUUID();
        MediaObjectReference mediaObjectReference;
        try (var inputStream = file.getInputStream()) {
            mediaObjectReference = mediaService.upload(
                    chatId,
                    attachmentId,
                    metadata.originalFileName(),
                    metadata.contentType(),
                    file.getSize(),
                    inputStream
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read attachment", exception);
        }

        return saveUploadedAttachment(
                requesterId,
                chatId,
                attachmentId,
                metadata.originalFileName(),
                metadata.contentType(),
                metadata.kind(),
                file.getSize(),
                metadata.durationMs(),
                imageDimensions,
                metadata.hdPhoto(),
                metadata.waveformSamples(),
                metadata.albumId(),
                metadata.albumItemIndex(),
                mediaObjectReference
        );
    }

    @Transactional
    public MessageAttachmentResponse uploadFromPath(
            UUID requesterId,
            UUID chatId,
            String kind,
            Long durationMs,
            Integer width,
            Integer height,
            Boolean hdPhoto,
            String waveform,
            UUID albumId,
            Integer albumItemIndex,
            String originalFileName,
            String contentType,
            long fileSizeBytes,
            Path sourcePath
    ) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        chatService.ensureCanPost(chat, requesterId);

        if (fileSizeBytes <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment is empty");
        }
        if (fileSizeBytes > maxFileSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Attachment is too large");
        }

        AttachmentMetadataSupport.UploadMetadata metadata = AttachmentMetadataSupport.prepareMultipartUpload(
                originalFileName,
                contentType,
                kind,
                durationMs,
                width,
                height,
                hdPhoto,
                waveform,
                albumId,
                albumItemIndex
        );
        ImageDimensions imageDimensions = resolveImageDimensions(
                sourcePath,
                metadata.kind(),
                metadata.width(),
                metadata.height()
        );

        UUID attachmentId = UUID.randomUUID();
        MediaObjectReference mediaObjectReference;
        try (var inputStream = Files.newInputStream(sourcePath)) {
            mediaObjectReference = mediaService.upload(
                    chatId,
                    attachmentId,
                    metadata.originalFileName(),
                    metadata.contentType(),
                    fileSizeBytes,
                    inputStream
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read attachment", exception);
        }

        return saveUploadedAttachment(
                requesterId,
                chatId,
                attachmentId,
                metadata.originalFileName(),
                metadata.contentType(),
                metadata.kind(),
                fileSizeBytes,
                metadata.durationMs(),
                imageDimensions,
                metadata.hdPhoto(),
                metadata.waveformSamples(),
                metadata.albumId(),
                metadata.albumItemIndex(),
                mediaObjectReference
        );
    }

    @Transactional(readOnly = true)
    public void assertUsableAttachments(UUID requesterId, UUID chatId, List<UUID> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }

        chatService.getOwnedChat(requesterId, chatId);
        Map<UUID, AttachmentEntity> attachmentsById = findAllOrdered(attachmentIds);
        if (attachmentsById.size() != new LinkedHashSet<>(attachmentIds).size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more attachments were not found");
        }

        for (UUID attachmentId : attachmentIds) {
            AttachmentEntity attachment = attachmentsById.get(attachmentId);
            if (attachment == null || !attachment.getChatId().equals(chatId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment belongs to another chat");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<MessageAttachmentResponse> getResponses(List<UUID> attachmentIds) {
        return getResponses(currentAuthenticatedUserId().orElse(null), attachmentIds);
    }

    @Transactional(readOnly = true)
    public List<MessageAttachmentResponse> listRecentGifs(UUID requesterId) {
        return getResponses(requesterId, stickerService.listRecentGifIds(requesterId));
    }

    @Transactional(readOnly = true)
    public List<MessageAttachmentResponse> getResponses(UUID requesterId, List<UUID> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, AttachmentEntity> attachmentsById = findAllOrdered(attachmentIds);
        List<AttachmentEntity> attachments = attachmentIds.stream()
                .map(attachmentsById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        assertRequesterCanAccessAttachments(requesterId, attachments);
        return attachments.stream()
                .map(attachment -> toResponse(attachment, requesterId))
                .toList();
    }

    @Transactional
    public List<UUID> cloneAttachmentsToChat(UUID requesterId, UUID targetChatId, List<UUID> attachmentIds) {
        return cloneAttachmentsToChat(requesterId, targetChatId, attachmentIds, true, false);
    }

    @Transactional
    public List<UUID> cloneAttachmentsToChatAsAlbum(UUID requesterId, UUID targetChatId, List<UUID> attachmentIds) {
        List<UUID> normalizedAttachmentIds = attachmentIds == null ? List.of() : List.copyOf(new LinkedHashSet<>(attachmentIds));
        if (normalizedAttachmentIds.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media group must include at least two attachments");
        }
        if (normalizedAttachmentIds.size() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media group cannot exceed ten attachments");
        }
        return cloneAttachmentsToChat(requesterId, targetChatId, normalizedAttachmentIds, true, true);
    }

    @Transactional
    public List<UUID> cloneAttachmentsToChatForSystem(UUID requesterId, UUID targetChatId, List<UUID> attachmentIds) {
        return cloneAttachmentsToChat(requesterId, targetChatId, attachmentIds, false, false);
    }

    @Transactional
    private List<UUID> cloneAttachmentsToChat(
            UUID requesterId,
            UUID targetChatId,
            List<UUID> attachmentIds,
            boolean requireTargetMembership,
            boolean forceAlbumGrouping
    ) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }

        if (requireTargetMembership) {
            chatService.getOwnedChat(requesterId, targetChatId);
        } else {
            chatService.getChat(targetChatId);
        }
        Map<UUID, AttachmentEntity> attachmentsById = findAllOrdered(attachmentIds);
        if (attachmentsById.size() != new LinkedHashSet<>(attachmentIds).size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more attachments were not found");
        }

        List<AttachmentEntity> sourceAttachments = attachmentIds.stream()
                .map(attachmentsById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        assertRequesterCanAccessAttachments(requesterId, sourceAttachments);
        if (forceAlbumGrouping) {
            validateAlbumSourceAttachments(sourceAttachments);
        }

        Map<UUID, UUID> clonedAlbumIds = new LinkedHashMap<>();
        UUID forcedAlbumId = forceAlbumGrouping ? UUID.randomUUID() : null;
        List<UUID> clonedAttachmentIds = new ArrayList<>(sourceAttachments.size());
        int forcedAlbumItemIndex = 0;
        for (AttachmentEntity source : sourceAttachments) {
            UUID clonedAttachmentId = UUID.randomUUID();
            byte[] sourceBytes = loadCloneSourceBytes(source);
            MediaObjectReference mediaObjectReference = mediaService.upload(
                    targetChatId,
                    clonedAttachmentId,
                    source.getOriginalFileName(),
                    source.getContentType(),
                    sourceBytes.length,
                    new ByteArrayInputStream(sourceBytes)
            );

            AttachmentEntity clone = new AttachmentEntity();
            clone.setId(clonedAttachmentId);
            clone.setChatId(targetChatId);
            clone.setUploaderUserId(requesterId);
            clone.setOriginalFileName(source.getOriginalFileName());
            clone.setContentType(source.getContentType());
            clone.setKind(source.getKind());
            clone.setFileSizeBytes(source.getFileSizeBytes());
            clone.setDurationMs(source.getDurationMs());
            clone.setWidth(source.getWidth());
            clone.setHeight(source.getHeight());
            clone.setWaveform(source.getWaveform());
            clone.setAlbumId(forceAlbumGrouping
                    ? forcedAlbumId
                    : source.getAlbumId() != null
                    ? clonedAlbumIds.computeIfAbsent(source.getAlbumId(), ignored -> UUID.randomUUID())
                    : null);
            clone.setAlbumItemIndex(forceAlbumGrouping ? forcedAlbumItemIndex++ : source.getAlbumItemIndex());
            clone.setSourceAttachmentId(null);
            clone.setTrimStartMs(source.getTrimStartMs());
            clone.setTrimEndMs(source.getTrimEndMs());
            clone.setHdPhoto(source.isHdPhoto());
            clone.setPreviewBucketName(null);
            clone.setPreviewObjectKey(null);
            clone.setThumbnailBucketName(null);
            clone.setThumbnailObjectKey(null);
            clone.setProcessingStatus("NOT_REQUIRED");
            clone.setModerationStatus(source.getModerationStatus());
            clone.setModerationReason(source.getModerationReason());
            clone.setModerationSensitive(source.isModerationSensitive());
            clone.setModerationReviewedByUserId(source.getModerationReviewedByUserId());
            clone.setModerationReviewedAt(source.getModerationReviewedAt());
            clone.setStorageProvider("S3");
            clone.setBucketName(mediaObjectReference.bucketName());
            clone.setObjectKey(mediaObjectReference.objectKey());
            clone.setStoragePath(mediaObjectReference.storagePath());
            clone.setNonce(null);
            clone.setKeyVersion(null);

            AttachmentEntity saved = attachmentRepository.save(clone);
            mediaProcessingService.enqueueAttachmentPreview(saved);
            clonedAttachmentIds.add(saved.getId());
        }
        return List.copyOf(clonedAttachmentIds);
    }

    @Transactional(readOnly = true)
    public AttachmentAccessResponse getAccess(UUID requesterId, UUID attachmentId) {
        AttachmentEntity attachment = getOwnedAttachment(requesterId, attachmentId);
        ensureModerationAccessAllowed(requesterId, attachment);
        AttachmentResolvedAccess access = resolveAccess(attachment, requesterId);
        return new AttachmentAccessResponse(
                access.downloadUrl(),
                access.previewUrl(),
                access.accessExpiresAt(),
                access.requiresAuthorization()
        );
    }

    @Transactional(readOnly = true)
    public AttachmentDownloadResult download(UUID requesterId, UUID attachmentId) {
        return download(requesterId, attachmentId, null);
    }

    @Transactional(readOnly = true)
    public AttachmentDownloadResult download(UUID requesterId, UUID attachmentId, Instant accessExpiresAt) {
        return accessMedia(requesterId, attachmentId, accessExpiresAt, AttachmentMediaVariant.DOWNLOAD);
    }

    @Transactional(readOnly = true)
    public AttachmentDownloadResult preview(UUID requesterId, UUID attachmentId, Instant accessExpiresAt) {
        return accessMedia(requesterId, attachmentId, accessExpiresAt, AttachmentMediaVariant.PREVIEW);
    }

    @Transactional(readOnly = true)
    public AttachmentDownloadResult thumbnail(UUID requesterId, UUID attachmentId, Instant accessExpiresAt) {
        return accessMedia(requesterId, attachmentId, accessExpiresAt, AttachmentMediaVariant.THUMBNAIL);
    }

    private AttachmentDownloadResult accessMedia(
            UUID requesterId,
            UUID attachmentId,
            Instant accessExpiresAt,
            AttachmentMediaVariant variant
    ) {
        AttachmentEntity attachment = getOwnedAttachment(requesterId, attachmentId);
        if (variant == AttachmentMediaVariant.DOWNLOAD) {
            ensureModerationAccessAllowed(requesterId, attachment);
        } else {
            ensurePreviewAccessAllowed(requesterId, attachment);
        }

        RedirectTarget redirectTarget = resolveRedirectTarget(attachment, variant, accessExpiresAt);
        if (redirectTarget != null) {
            return new AttachmentDownloadResult(redirectTarget.downloadUrl(), null);
        }

        DownloadedAttachment downloadedAttachment = loadVariantBytes(
                attachment,
                variant
        );
        return new AttachmentDownloadResult(null, downloadedAttachment);
    }

    @Transactional(readOnly = true)
    public List<MessageAttachmentResponse> listAlbum(UUID requesterId, UUID albumId) {
        if (albumId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album id is required");
        }
        List<AttachmentEntity> attachments = attachmentRepository.findAllByAlbumIdOrderByAlbumItemIndexAscCreatedAtAsc(albumId);
        if (attachments.isEmpty()) {
            return List.of();
        }
        UUID chatId = attachments.get(0).getChatId();
        boolean multipleChats = attachments.stream().anyMatch(attachment -> !chatId.equals(attachment.getChatId()));
        if (multipleChats) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album attachments must belong to one chat");
        }
        chatService.getOwnedChat(requesterId, chatId);
        return attachments.stream().map(attachment -> toResponse(attachment, requesterId)).toList();
    }

    @Transactional
    public MessageAttachmentResponse reviewModeration(
            UUID requesterId,
            UUID attachmentId,
            ModerateAttachmentRequest request
    ) {
        AttachmentEntity attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
        chatService.getOwnedChat(requesterId, attachment.getChatId());
        if (!chatService.hasMessageModerationPermission(requesterId, attachment.getChatId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attachment moderation is not allowed for this member");
        }

        String status = normalizeModerationStatus(request.status());
        attachment.setModerationStatus(status);
        attachment.setModerationReason(normalizeOptional(request.reason(), 255));
        attachment.setModerationSensitive(Boolean.TRUE.equals(request.sensitiveContent()));
        attachment.setModerationReviewedByUserId(requesterId);
        attachment.setModerationReviewedAt(Instant.now());
        if ("APPROVED".equals(status)) {
            attachment.setModerationReason(null);
        }

        return toResponse(attachmentRepository.save(attachment), requesterId);
    }

    @Transactional
    public MessageAttachmentResponse trim(UUID requesterId, UUID attachmentId, TrimAttachmentRequest request) {
        AttachmentEntity source = getOwnedAttachment(requesterId, attachmentId);
        ensureTrimSupported(source);
        ensureModerationAccessAllowed(requesterId, source);

        TrimWindow trimWindow = normalizeTrimWindow(source, request);
        AttachmentEntity trimmed = new AttachmentEntity();
        trimmed.setId(UUID.randomUUID());
        trimmed.setChatId(source.getChatId());
        trimmed.setUploaderUserId(requesterId);
        trimmed.setOriginalFileName(source.getOriginalFileName());
        trimmed.setContentType(source.getContentType());
        trimmed.setKind(source.getKind());
        trimmed.setFileSizeBytes(source.getFileSizeBytes());
        trimmed.setDurationMs(trimWindow.durationMs());
        trimmed.setWidth(source.getWidth());
        trimmed.setHeight(source.getHeight());
        trimmed.setWaveform(source.getWaveform());
        trimmed.setAlbumId(null);
        trimmed.setAlbumItemIndex(null);
        trimmed.setSourceAttachmentId(source.getId());
        trimmed.setTrimStartMs(trimWindow.startMs());
        trimmed.setTrimEndMs(trimWindow.endMs());
        trimmed.setHdPhoto(source.isHdPhoto());
        trimmed.setPreviewBucketName(source.getPreviewBucketName());
        trimmed.setPreviewObjectKey(source.getPreviewObjectKey());
        trimmed.setThumbnailBucketName(source.getThumbnailBucketName());
        trimmed.setThumbnailObjectKey(source.getThumbnailObjectKey());
        trimmed.setProcessingStatus(source.getProcessingStatus());
        trimmed.setModerationStatus(source.getModerationStatus());
        trimmed.setModerationReason(source.getModerationReason());
        trimmed.setModerationSensitive(source.isModerationSensitive());
        trimmed.setModerationReviewedByUserId(source.getModerationReviewedByUserId());
        trimmed.setModerationReviewedAt(source.getModerationReviewedAt());
        trimmed.setStorageProvider(source.getStorageProvider());
        trimmed.setBucketName(source.getBucketName());
        trimmed.setObjectKey(source.getObjectKey());
        trimmed.setStoragePath(
                source.getStoragePath()
                        + "#trim:"
                        + trimWindow.startMs()
                        + "-"
                        + trimWindow.endMs()
                        + ":"
                        + trimmed.getId()
        );
        trimmed.setNonce(source.getNonce());
        trimmed.setKeyVersion(source.getKeyVersion());

        return toResponse(attachmentRepository.save(trimmed), requesterId);
    }

    private AttachmentEntity getOwnedAttachment(UUID requesterId, UUID attachmentId) {
        AttachmentEntity attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
        chatService.getOwnedChat(requesterId, attachment.getChatId());
        return attachment;
    }

    private Map<UUID, AttachmentEntity> findAllOrdered(List<UUID> attachmentIds) {
        Map<UUID, AttachmentEntity> attachmentsById = new LinkedHashMap<>();
        for (AttachmentEntity attachment : attachmentRepository.findAllByIdIn(new LinkedHashSet<>(attachmentIds))) {
            attachmentsById.put(attachment.getId(), attachment);
        }
        return attachmentsById;
    }

    private AttachmentEntity resolveBinarySource(AttachmentEntity attachment) {
        AttachmentEntity current = attachment;
        Set<UUID> visited = new LinkedHashSet<>();
        while (current.getSourceAttachmentId() != null && visited.add(current.getId())) {
            AttachmentEntity next = attachmentRepository.findById(current.getSourceAttachmentId()).orElse(null);
            if (next == null) {
                break;
            }
            current = next;
        }
        return current;
    }
    private void assertRequesterCanAccessAttachments(UUID requesterId, List<AttachmentEntity> attachments) {
        if (requesterId == null || attachments == null || attachments.isEmpty()) {
            return;
        }
        for (UUID chatId : attachments.stream().map(AttachmentEntity::getChatId).distinct().toList()) {
            chatService.getOwnedChat(requesterId, chatId);
        }
    }

    private MessageAttachmentResponse toResponse(AttachmentEntity attachment, UUID requesterId) {
        boolean blockedByModeration = isBlockedByModeration(requesterId, attachment);
        boolean hidePreviewForSensitive = shouldHidePreviewForSensitiveContent(requesterId, attachment);
        AttachmentResolvedAccess access = requesterId == null || blockedByModeration
                ? authorizationRequiredAccess()
                : resolveAccess(attachment, requesterId);
        String previewUrl = requesterId == null || hidePreviewForSensitive ? null : access.previewUrl();
        String thumbnailUrl = requesterId == null || hidePreviewForSensitive ? null : access.thumbnailUrl();
        Instant accessExpiresAt = requesterId == null || blockedByModeration ? null : access.accessExpiresAt();
        return new MessageAttachmentResponse(
                attachment.getId(),
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getKind(),
                attachment.getFileSizeBytes(),
                attachment.getDurationMs(),
                access.downloadUrl(),
                previewUrl,
                thumbnailUrl,
                attachment.getWidth(),
                attachment.getHeight(),
                parseWaveform(attachment.getWaveform()),
                accessExpiresAt,
                access.requiresAuthorization(),
                isStreamingSupported(attachment),
                isVoiceNote(attachment),
                isRoundMessage(attachment),
                attachment.getAlbumId(),
                attachment.getAlbumItemIndex(),
                attachment.getModerationStatus(),
                attachment.getModerationReason(),
                attachment.isModerationSensitive(),
                blockedByModeration,
                attachment.getSourceAttachmentId(),
                attachment.getTrimStartMs(),
                attachment.getTrimEndMs(),
                attachment.isHdPhoto()
        );
    }

    private AttachmentResolvedAccess resolveAccess(AttachmentEntity attachment, UUID requesterId) {
        AttachmentAccessTokenService.IssuedAttachmentAccessToken accessToken =
                attachmentAccessTokenService.issue(requesterId, attachment.getId());
        return new AttachmentResolvedAccess(
                buildAccessUrl(attachment.getId(), "download", accessToken.token()),
                isPreviewableAttachment(attachment) ? buildAccessUrl(attachment.getId(), "preview", accessToken.token()) : null,
                isPreviewableAttachment(attachment) ? buildAccessUrl(attachment.getId(), "thumbnail", accessToken.token()) : null,
                accessToken.expiresAt(),
                false
        );
    }

    private AttachmentResolvedAccess authorizationRequiredAccess() {
        return new AttachmentResolvedAccess(null, null, null, null, true);
    }

    private String buildAccessUrl(UUID attachmentId, String action, String accessToken) {
        return "/api/attachments/" + attachmentId + "/" + action + "?"
                + AttachmentAccessTokenService.QUERY_PARAMETER + "=" + accessToken;
    }

    private RedirectTarget resolveRedirectTarget(
            AttachmentEntity attachment,
            AttachmentMediaVariant variant,
            Instant accessExpiresAt
    ) {
        StoredObjectReference objectReference = switch (variant) {
            case DOWNLOAD -> isS3Stored(attachment)
                    ? new StoredObjectReference(attachment.getBucketName(), attachment.getObjectKey())
                    : null;
            case PREVIEW -> resolvePreviewObjectReference(attachment);
            case THUMBNAIL -> resolveThumbnailObjectReference(attachment);
        };
        if (objectReference == null) {
            return null;
        }
        PresignedMediaAccess access = accessExpiresAt == null
                ? mediaService.buildDownloadAccess(objectReference.bucketName(), objectReference.objectKey())
                : mediaService.buildDownloadAccess(
                        objectReference.bucketName(),
                        objectReference.objectKey(),
                        Duration.between(Instant.now(), accessExpiresAt)
                );
        return new RedirectTarget(access.downloadUrl());
    }

    private StoredObjectReference resolvePreviewObjectReference(AttachmentEntity attachment) {
        if (!isPreviewableAttachment(attachment)) {
            return null;
        }
        if (attachment.getPreviewBucketName() != null && attachment.getPreviewObjectKey() != null) {
            return new StoredObjectReference(attachment.getPreviewBucketName(), attachment.getPreviewObjectKey());
        }
        if (isS3Stored(attachment)) {
            return new StoredObjectReference(attachment.getBucketName(), attachment.getObjectKey());
        }
        return null;
    }

    private StoredObjectReference resolveThumbnailObjectReference(AttachmentEntity attachment) {
        if (!isPreviewableAttachment(attachment)) {
            return null;
        }
        if (attachment.getThumbnailBucketName() != null && attachment.getThumbnailObjectKey() != null) {
            return new StoredObjectReference(attachment.getThumbnailBucketName(), attachment.getThumbnailObjectKey());
        }
        StoredObjectReference previewReference = resolvePreviewObjectReference(attachment);
        if (previewReference != null) {
            return previewReference;
        }
        if (isS3Stored(attachment)) {
            return new StoredObjectReference(attachment.getBucketName(), attachment.getObjectKey());
        }
        return null;
    }

    private DownloadedAttachment loadVariantBytes(AttachmentEntity attachment, AttachmentMediaVariant variant) {
        if (variant != AttachmentMediaVariant.DOWNLOAD && !isPreviewableAttachment(attachment)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment preview is unavailable");
        }
        AttachmentEntity binarySource = resolveBinarySource(attachment);
        byte[] ciphertext;
        try {
            ciphertext = Files.readAllBytes(Path.of(binarySource.getStoragePath()));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read attachment", exception);
        }

        byte[] plaintext = chatEncryptionService.decryptBytes(
                binarySource.getChatId(),
                ciphertext,
                binarySource.getNonce(),
                binarySource.getKeyVersion()
        );
        return new DownloadedAttachment(
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                plaintext
        );
    }

    private void ensurePreviewAccessAllowed(UUID requesterId, AttachmentEntity attachment) {
        ensureModerationAccessAllowed(requesterId, attachment);
        if (!isPreviewableAttachment(attachment)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment preview is unavailable");
        }
        if (shouldHidePreviewForSensitiveContent(requesterId, attachment)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attachment preview is restricted");
        }
    }

    private boolean isS3Stored(AttachmentEntity attachment) {
        return "S3".equalsIgnoreCase(attachment.getStorageProvider())
                && attachment.getBucketName() != null
                && attachment.getObjectKey() != null;
    }

    private byte[] loadCloneSourceBytes(AttachmentEntity attachment) {
        if (isS3Stored(attachment)) {
            return mediaService.downloadObjectBytes(attachment.getBucketName(), attachment.getObjectKey());
        }
        if (attachment.getStoragePath() == null || attachment.getStoragePath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Attachment source media is unavailable");
        }
        byte[] storedBytes;
        try {
            storedBytes = Files.readAllBytes(Path.of(attachment.getStoragePath()));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read attachment", exception);
        }
        if (attachment.getNonce() != null && attachment.getKeyVersion() != null) {
            return chatEncryptionService.decryptBytes(
                    attachment.getChatId(),
                    storedBytes,
                    attachment.getNonce(),
                    attachment.getKeyVersion()
            );
        }
        return storedBytes;
    }

    private boolean isImageAttachment(AttachmentEntity attachment) {
        return "IMAGE".equalsIgnoreCase(attachment.getKind())
                || attachment.getContentType().toLowerCase().startsWith("image/");
    }

    private boolean isPreviewableAttachment(AttachmentEntity attachment) {
        return isImageAttachment(attachment) || "GIF".equalsIgnoreCase(attachment.getKind());
    }

    private boolean isVoiceNote(AttachmentEntity attachment) {
        return "VOICE".equalsIgnoreCase(attachment.getKind());
    }

    private boolean isRoundMessage(AttachmentEntity attachment) {
        return "VIDEO_NOTE".equalsIgnoreCase(attachment.getKind());
    }

    private boolean isStreamingSupported(AttachmentEntity attachment) {
        return List.of("VOICE", "AUDIO", "VIDEO", "VIDEO_NOTE").contains(attachment.getKind());
    }

    private void validateAlbumSourceAttachments(List<AttachmentEntity> attachments) {
        for (AttachmentEntity attachment : attachments) {
            if (attachment == null || !List.of("IMAGE", "VIDEO", "GIF", "VIDEO_NOTE").contains(attachment.getKind())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media groups support only visual attachments");
            }
        }
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value is too long");
        }
        return normalized;
    }

    private void ensureTrimSupported(AttachmentEntity attachment) {
        if (!List.of("VOICE", "AUDIO", "VIDEO", "VIDEO_NOTE").contains(attachment.getKind())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Attachment trimming is supported only for audio and video attachments"
            );
        }
        if (attachment.getDurationMs() == null || attachment.getDurationMs() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attachment duration is unavailable for trimming");
        }
    }

    private TrimWindow normalizeTrimWindow(AttachmentEntity attachment, TrimAttachmentRequest request) {
        Long startMs = request != null ? request.startMs() : null;
        Long endMs = request != null ? request.endMs() : null;
        if (startMs == null || endMs == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trim start and end are required");
        }
        if (startMs < 0 || endMs <= 0 || endMs <= startMs) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trim range is invalid");
        }
        if (endMs > attachment.getDurationMs()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trim end exceeds attachment duration");
        }
        return new TrimWindow(startMs, endMs, endMs - startMs);
    }

    private String normalizeModerationStatus(String value) {
        String normalized = normalizeOptional(value, 16);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Moderation status is required");
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!List.of("APPROVED", "FLAGGED", "REJECTED").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported moderation status");
        }
        return normalized;
    }

    private ImageDimensions resolveImageDimensions(
            MultipartFile file,
            String kind,
            Integer width,
            Integer height
    ) {
        if (width != null && height != null) {
            return new ImageDimensions(width, height);
        }
        if (!List.of("IMAGE", "GIF").contains(kind)) {
            return new ImageDimensions(width, height);
        }
        try (var inputStream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                return new ImageDimensions(width, height);
            }
            return new ImageDimensions(
                    width != null ? width : image.getWidth(),
                    height != null ? height : image.getHeight()
            );
        } catch (IOException exception) {
            return new ImageDimensions(width, height);
        }
    }

    private ImageDimensions resolveImageDimensions(
            Path sourcePath,
            String kind,
            Integer width,
            Integer height
    ) {
        if (width != null && height != null) {
            return new ImageDimensions(width, height);
        }
        if (!List.of("IMAGE", "GIF").contains(kind)) {
            return new ImageDimensions(width, height);
        }
        try (var inputStream = Files.newInputStream(sourcePath)) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                return new ImageDimensions(width, height);
            }
            return new ImageDimensions(
                    width != null ? width : image.getWidth(),
                    height != null ? height : image.getHeight()
            );
        } catch (IOException exception) {
            return new ImageDimensions(width, height);
        }
    }

    private List<Integer> parseWaveform(String waveform, String kind) {
        List<Integer> parsed = parseWaveform(waveform);
        if (parsed.isEmpty()) {
            return List.of();
        }
        if (!List.of("VOICE", "AUDIO", "VIDEO_NOTE").contains(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Waveform is supported only for audio-style attachments");
        }
        return parsed;
    }

    private List<Integer> parseWaveform(String waveform) {
        return AttachmentMetadataSupport.parseWaveformString(waveform);
    }

    private String serializeWaveform(List<Integer> waveformSamples) {
        return AttachmentMetadataSupport.serializeWaveform(waveformSamples);
    }

    private MessageAttachmentResponse saveUploadedAttachment(
            UUID requesterId,
            UUID chatId,
            UUID attachmentId,
            String originalFileName,
            String contentType,
            String kind,
            long fileSizeBytes,
            Long durationMs,
            ImageDimensions imageDimensions,
            boolean hdPhoto,
            List<Integer> waveformSamples,
            UUID albumId,
            Integer albumItemIndex,
            MediaObjectReference mediaObjectReference
    ) {
        AttachmentEntity entity = new AttachmentEntity();
        entity.setId(attachmentId);
        entity.setChatId(chatId);
        entity.setUploaderUserId(requesterId);
        entity.setOriginalFileName(originalFileName);
        entity.setContentType(contentType);
        entity.setKind(kind);
        entity.setFileSizeBytes(fileSizeBytes);
        entity.setDurationMs(durationMs);
        entity.setWidth(imageDimensions.width());
        entity.setHeight(imageDimensions.height());
        entity.setWaveform(serializeWaveform(waveformSamples));
        entity.setAlbumId(albumId);
        entity.setAlbumItemIndex(albumItemIndex);
        entity.setSourceAttachmentId(null);
        entity.setTrimStartMs(null);
        entity.setTrimEndMs(null);
        entity.setHdPhoto(hdPhoto);
        entity.setStorageProvider("S3");
        entity.setBucketName(mediaObjectReference.bucketName());
        entity.setObjectKey(mediaObjectReference.objectKey());
        entity.setStoragePath(mediaObjectReference.storagePath());
        entity.setNonce(null);
        entity.setKeyVersion(null);
        entity.setProcessingStatus("NOT_REQUIRED");
        entity.setModerationStatus("APPROVED");

        AttachmentEntity saved = attachmentRepository.save(entity);
        mediaProcessingService.enqueueAttachmentPreview(saved);
        return toResponse(saved, requesterId);
    }

    private void ensureModerationAccessAllowed(UUID requesterId, AttachmentEntity attachment) {
        if (isBlockedByModeration(requesterId, attachment)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attachment is blocked by moderation");
        }
    }

    private boolean shouldHidePreviewForSensitiveContent(UUID requesterId, AttachmentEntity attachment) {
        return attachment.isModerationSensitive() && !canBypassModeration(requesterId, attachment);
    }

    private boolean isBlockedByModeration(UUID requesterId, AttachmentEntity attachment) {
        return "REJECTED".equalsIgnoreCase(attachment.getModerationStatus())
                && !canBypassModeration(requesterId, attachment);
    }

    private boolean canBypassModeration(UUID requesterId, AttachmentEntity attachment) {
        if (requesterId == null) {
            return true;
        }
        if (requesterId.equals(attachment.getUploaderUserId())) {
            return true;
        }
        try {
            return chatService.hasMessageModerationPermission(requesterId, attachment.getChatId());
        } catch (ResponseStatusException exception) {
            return false;
        }
    }

    private Optional<UUID> currentAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(authentication.getName()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private record AttachmentResolvedAccess(
            String downloadUrl,
            String previewUrl,
            String thumbnailUrl,
            Instant accessExpiresAt,
            boolean requiresAuthorization
    ) {
    }

    private record RedirectTarget(String downloadUrl) {
    }

    private record StoredObjectReference(String bucketName, String objectKey) {
    }

    private record ImageDimensions(Integer width, Integer height) {
    }

    private record TrimWindow(Long startMs, Long endMs, Long durationMs) {
    }

    private enum AttachmentMediaVariant {
        DOWNLOAD,
        PREVIEW,
        THUMBNAIL
    }
}
