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
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final long maxFileSizeBytes;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            ChatService chatService,
            ChatEncryptionService chatEncryptionService,
            MediaService mediaService,
            MediaProcessingService mediaProcessingService,
            @Value("${alex.storage.attachments.max-file-size-bytes}") long maxFileSizeBytes
    ) {
        this.attachmentRepository = attachmentRepository;
        this.chatService = chatService;
        this.chatEncryptionService = chatEncryptionService;
        this.mediaService = mediaService;
        this.mediaProcessingService = mediaProcessingService;
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

        String contentType = normalizeContentType(file.getContentType());
        String normalizedKind = normalizeKind(kind, contentType);
        validateAttachmentMetadata(normalizedKind, durationMs, width, height, contentType);
        boolean normalizedHdPhoto = normalizeHdPhoto(hdPhoto, normalizedKind);
        validateAlbumMetadata(normalizedKind, albumId, albumItemIndex);
        List<Integer> waveformSamples = parseWaveform(waveform, normalizedKind);
        ImageDimensions imageDimensions = resolveImageDimensions(file, normalizedKind, width, height);
        String originalFileName = safeFileName(file.getOriginalFilename());

        UUID attachmentId = UUID.randomUUID();
        MediaObjectReference mediaObjectReference;
        try (var inputStream = file.getInputStream()) {
            mediaObjectReference = mediaService.upload(
                    chatId,
                    attachmentId,
                    originalFileName,
                    contentType,
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
                originalFileName,
                contentType,
                normalizedKind,
                file.getSize(),
                durationMs,
                imageDimensions,
                normalizedHdPhoto,
                waveformSamples,
                albumId,
                albumItemIndex,
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

        String normalizedContentType = normalizeContentType(contentType);
        String normalizedKind = normalizeKind(kind, normalizedContentType);
        validateAttachmentMetadata(normalizedKind, durationMs, width, height, normalizedContentType);
        boolean normalizedHdPhoto = normalizeHdPhoto(hdPhoto, normalizedKind);
        validateAlbumMetadata(normalizedKind, albumId, albumItemIndex);
        List<Integer> waveformSamples = parseWaveform(waveform, normalizedKind);
        ImageDimensions imageDimensions = resolveImageDimensions(sourcePath, normalizedKind, width, height);
        String safeOriginalFileName = safeFileName(originalFileName);

        UUID attachmentId = UUID.randomUUID();
        MediaObjectReference mediaObjectReference;
        try (var inputStream = Files.newInputStream(sourcePath)) {
            mediaObjectReference = mediaService.upload(
                    chatId,
                    attachmentId,
                    safeOriginalFileName,
                    normalizedContentType,
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
                safeOriginalFileName,
                normalizedContentType,
                normalizedKind,
                fileSizeBytes,
                durationMs,
                imageDimensions,
                normalizedHdPhoto,
                waveformSamples,
                albumId,
                albumItemIndex,
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
        return buildAccessResponse(attachment);
    }

    @Transactional(readOnly = true)
    public AttachmentDownloadResult download(UUID requesterId, UUID attachmentId) {
        AttachmentEntity attachment = getOwnedAttachment(requesterId, attachmentId);
        ensureModerationAccessAllowed(requesterId, attachment);
        if (isS3Stored(attachment)) {
            AttachmentAccessResponse accessResponse = buildAccessResponse(attachment);
            return new AttachmentDownloadResult(accessResponse.downloadUrl(), null);
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

        return new AttachmentDownloadResult(
                null,
                new DownloadedAttachment(
                        attachment.getOriginalFileName(),
                        attachment.getContentType(),
                        plaintext
                )
        );
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
        AttachmentAccessResponse accessResponse = requesterId == null
                ? new AttachmentAccessResponse(null, null, null, true)
                : blockedByModeration
                        ? new AttachmentAccessResponse(null, null, null, true)
                        : buildAccessResponse(attachment);
        String previewUrl = requesterId == null || hidePreviewForSensitive ? null : accessResponse.previewUrl();
        String thumbnailUrl = requesterId == null || hidePreviewForSensitive ? null : resolveThumbnailUrl(attachment);
        return new MessageAttachmentResponse(
                attachment.getId(),
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getKind(),
                attachment.getFileSizeBytes(),
                attachment.getDurationMs(),
                accessResponse.downloadUrl(),
                previewUrl,
                thumbnailUrl,
                attachment.getWidth(),
                attachment.getHeight(),
                parseWaveform(attachment.getWaveform()),
                accessResponse.accessExpiresAt(),
                accessResponse.requiresAuthorization(),
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

    private AttachmentAccessResponse buildAccessResponse(AttachmentEntity attachment) {
        if (isS3Stored(attachment)) {
            PresignedMediaAccess access = mediaService.buildDownloadAccess(attachment.getBucketName(), attachment.getObjectKey());
            String previewUrl = resolvePreviewUrl(attachment);
            return new AttachmentAccessResponse(
                    access.downloadUrl(),
                    previewUrl,
                    access.expiresAt(),
                    false
            );
        }

        return new AttachmentAccessResponse(
                "/api/attachments/" + attachment.getId() + "/download",
                null,
                null,
                true
        );
    }

    private String resolvePreviewUrl(AttachmentEntity attachment) {
        if (!isPreviewableAttachment(attachment)) {
            return null;
        }
        if (attachment.getPreviewBucketName() != null && attachment.getPreviewObjectKey() != null) {
            return mediaService.buildDownloadAccess(
                    attachment.getPreviewBucketName(),
                    attachment.getPreviewObjectKey()
            ).downloadUrl();
        }
        if (isS3Stored(attachment)) {
            return mediaService.buildDownloadAccess(
                    attachment.getBucketName(),
                    attachment.getObjectKey()
            ).downloadUrl();
        }
        return null;
    }

    private String resolveThumbnailUrl(AttachmentEntity attachment) {
        if (!isPreviewableAttachment(attachment)) {
            return null;
        }
        if (attachment.getThumbnailBucketName() != null && attachment.getThumbnailObjectKey() != null) {
            return mediaService.buildDownloadAccess(
                    attachment.getThumbnailBucketName(),
                    attachment.getThumbnailObjectKey()
            ).downloadUrl();
        }
        return resolvePreviewUrl(attachment);
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

    private String normalizeKind(String kind, String contentType) {
        String normalizedKind = kind != null ? kind.trim().toUpperCase() : "";
        if (normalizedKind.isBlank()) {
            if ("image/gif".equalsIgnoreCase(contentType)) {
                return "GIF";
            }
            if (contentType.toLowerCase().startsWith("image/")) {
                return "IMAGE";
            }
            if (contentType.toLowerCase().startsWith("video/")) {
                return "VIDEO";
            }
            if (contentType.toLowerCase().startsWith("audio/")) {
                return "AUDIO";
            }
            return "FILE";
        }
        if (!List.of("FILE", "VOICE", "IMAGE", "VIDEO", "AUDIO", "GIF", "VIDEO_NOTE").contains(normalizedKind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported attachment kind");
        }
        return normalizedKind;
    }

    private void validateAttachmentMetadata(
            String kind,
            Long durationMs,
            Integer width,
            Integer height,
            String contentType
    ) {
        if ("VOICE".equals(kind)) {
            if (durationMs == null || durationMs <= 0 || durationMs > 60 * 60 * 1000L) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voice attachment duration is invalid");
            }
            if (!contentType.toLowerCase().startsWith("audio/")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voice attachment must be audio");
            }
        } else if ("AUDIO".equals(kind) && !contentType.toLowerCase().startsWith("audio/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audio attachment must be audio");
        } else if ("IMAGE".equals(kind) && !contentType.toLowerCase().startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image attachment must be an image");
        } else if ("GIF".equals(kind) && !"image/gif".equalsIgnoreCase(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GIF attachment must be a GIF image");
        } else if ("VIDEO".equals(kind) && !contentType.toLowerCase().startsWith("video/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video attachment must be a video");
        } else if ("VIDEO_NOTE".equals(kind)) {
            if (durationMs == null || durationMs <= 0 || durationMs > 60 * 60 * 1000L) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video note duration is invalid");
            }
            if (!contentType.toLowerCase().startsWith("video/")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video note attachment must be a video");
            }
        }

        if (width != null && width <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment width is invalid");
        }
        if (height != null && height <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment height is invalid");
        }
        if ((width != null || height != null)
                && !List.of("IMAGE", "GIF", "VIDEO", "VIDEO_NOTE").contains(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dimensions are supported only for visual attachments");
        }
        if ("VIDEO_NOTE".equals(kind)
                && width != null
                && height != null
                && !width.equals(height)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video note must use square dimensions");
        }
    }

    private void validateAlbumMetadata(String kind, UUID albumId, Integer albumItemIndex) {
        if (albumItemIndex != null && albumItemIndex < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album item index is invalid");
        }
        if (albumItemIndex != null && albumId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album item index requires album id");
        }
        if (albumId != null && !List.of("IMAGE", "VIDEO", "GIF", "VIDEO_NOTE").contains(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album attachments must be visual media");
        }
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

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType.trim().toLowerCase();
    }

    private String safeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "file";
        }
        String normalized = originalFileName.replace("\\", "_").replace("/", "_").trim();
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
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

    private boolean normalizeHdPhoto(Boolean hdPhoto, String kind) {
        boolean enabled = Boolean.TRUE.equals(hdPhoto);
        if (enabled && !"IMAGE".equals(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HD photo is supported only for image attachments");
        }
        return enabled;
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
        if (waveform == null || waveform.isBlank()) {
            return List.of();
        }
        String[] parts = waveform.split(",");
        if (parts.length > 96) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Waveform is too large");
        }
        List<Integer> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            String normalized = part.trim();
            if (normalized.isBlank()) {
                continue;
            }
            int value;
            try {
                value = Integer.parseInt(normalized);
            } catch (NumberFormatException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Waveform contains invalid sample", exception);
            }
            if (value < 0 || value > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Waveform sample is out of range");
            }
            values.add(value);
        }
        return values;
    }

    private String serializeWaveform(List<Integer> waveformSamples) {
        if (waveformSamples == null || waveformSamples.isEmpty()) {
            return null;
        }
        return waveformSamples.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
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

    private record ImageDimensions(Integer width, Integer height) {
    }

    private record TrimWindow(Long startMs, Long endMs, Long durationMs) {
    }
}
