package com.alex.messenger.secret;

import com.alex.messenger.attachment.AttachmentAccessResponse;
import com.alex.messenger.media.MediaObjectReference;
import com.alex.messenger.media.MediaService;
import com.alex.messenger.media.PresignedMediaAccess;
import com.alex.messenger.secret.dto.SecretAttachmentUploadResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SecretAttachmentService {

    private final SecretAttachmentRepository secretAttachmentRepository;
    private final SecretChatRepository secretChatRepository;
    private final MediaService mediaService;
    private final long maxFileSizeBytes;

    public SecretAttachmentService(
            SecretAttachmentRepository secretAttachmentRepository,
            SecretChatRepository secretChatRepository,
            MediaService mediaService,
            @Value("${alex.storage.attachments.max-file-size-bytes}") long maxFileSizeBytes
    ) {
        this.secretAttachmentRepository = secretAttachmentRepository;
        this.secretChatRepository = secretChatRepository;
        this.mediaService = mediaService;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Transactional
    public SecretAttachmentUploadResponse upload(
            UUID requesterId,
            UUID sessionId,
            UUID secretChatId,
            String kind,
            MultipartFile file
    ) {
        SecretChatEntity chat = getAccessibleActiveBoundChat(requesterId, sessionId, secretChatId);
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment is empty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Attachment is too large");
        }

        UUID attachmentId = UUID.randomUUID();
        MediaObjectReference mediaObjectReference;
        try (var inputStream = file.getInputStream()) {
            mediaObjectReference = mediaService.uploadSecretAttachment(
                    chat.getId(),
                    attachmentId,
                    safeFileName(file.getOriginalFilename()),
                    normalizeContentType(file.getContentType()),
                    file.getSize(),
                    inputStream
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read attachment", exception);
        }

        SecretAttachmentEntity entity = new SecretAttachmentEntity();
        entity.setId(attachmentId);
        entity.setSecretChatId(chat.getId());
        entity.setUploaderUserId(requesterId);
        entity.setKind(normalizeKind(kind));
        entity.setContentType(normalizeContentType(file.getContentType()));
        entity.setEncryptedFileSizeBytes(file.getSize());
        entity.setStorageProvider("S3");
        entity.setBucketName(mediaObjectReference.bucketName());
        entity.setObjectKey(mediaObjectReference.objectKey());
        entity.setStoragePath(mediaObjectReference.storagePath());

        SecretAttachmentEntity saved = secretAttachmentRepository.save(entity);
        return new SecretAttachmentUploadResponse(
                saved.getId(),
                saved.getKind(),
                saved.getEncryptedFileSizeBytes(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public AttachmentAccessResponse getAccess(UUID requesterId, UUID sessionId, UUID attachmentId) {
        SecretAttachmentEntity attachment = getOwnedAttachment(requesterId, sessionId, attachmentId);
        PresignedMediaAccess access = mediaService.buildDownloadAccess(attachment.getBucketName(), attachment.getObjectKey());
        return new AttachmentAccessResponse(
                access.downloadUrl(),
                null,
                access.expiresAt(),
                false
        );
    }

    @Transactional
    public void removePendingAttachment(UUID requesterId, UUID sessionId, UUID attachmentId) {
        SecretAttachmentEntity attachment = getOwnedAttachment(requesterId, sessionId, attachmentId);
        if (!requesterId.equals(attachment.getUploaderUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only uploader can remove this attachment");
        }
        if (attachment.getSecretMessageId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attachment is already linked to a message");
        }
        mediaService.deleteObject(attachment.getBucketName(), attachment.getObjectKey());
        secretAttachmentRepository.delete(attachment);
    }

    @Transactional(readOnly = true)
    public Map<UUID, SecretAttachmentEntity> assertUsableAttachments(
            UUID requesterId,
            UUID sessionId,
            UUID secretChatId,
            Collection<UUID> attachmentIds
    ) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return Map.of();
        }

        SecretChatEntity chat = getAccessibleActiveBoundChat(requesterId, sessionId, secretChatId);
        Map<UUID, SecretAttachmentEntity> attachmentsById = findAllOrdered(attachmentIds);
        if (attachmentsById.size() != new LinkedHashSet<>(attachmentIds).size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more secret attachments were not found");
        }

        for (UUID attachmentId : attachmentIds) {
            SecretAttachmentEntity attachment = attachmentsById.get(attachmentId);
            if (attachment == null || !chat.getId().equals(attachment.getSecretChatId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Secret attachment belongs to another chat");
            }
            if (!requesterId.equals(attachment.getUploaderUserId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Secret attachment belongs to another user");
            }
            if (attachment.getSecretMessageId() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Secret attachment is already linked");
            }
        }
        return attachmentsById;
    }

    @Transactional
    public void linkAttachmentsToMessage(Collection<SecretAttachmentEntity> attachments, UUID secretMessageId) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }

        for (SecretAttachmentEntity attachment : attachments) {
            attachment.setSecretMessageId(secretMessageId);
        }
        secretAttachmentRepository.saveAll(attachments);
    }

    @Transactional
    public void removeAttachmentsForMessages(Collection<UUID> secretMessageIds) {
        if (secretMessageIds == null || secretMessageIds.isEmpty()) {
            return;
        }

        List<SecretAttachmentEntity> attachments = secretAttachmentRepository.findAllBySecretMessageIdIn(secretMessageIds);
        if (attachments.isEmpty()) {
            return;
        }

        for (SecretAttachmentEntity attachment : attachments) {
            if (attachment.getBucketName() != null && attachment.getObjectKey() != null) {
                mediaService.deleteObject(attachment.getBucketName(), attachment.getObjectKey());
            }
        }
        secretAttachmentRepository.deleteAll(attachments);
    }

    private SecretAttachmentEntity getOwnedAttachment(UUID requesterId, UUID sessionId, UUID attachmentId) {
        SecretAttachmentEntity attachment = secretAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Secret attachment not found"));
        getAccessibleActiveBoundChat(requesterId, sessionId, attachment.getSecretChatId());
        return attachment;
    }

    private Map<UUID, SecretAttachmentEntity> findAllOrdered(Collection<UUID> attachmentIds) {
        Map<UUID, SecretAttachmentEntity> attachmentsById = new LinkedHashMap<>();
        for (SecretAttachmentEntity attachment : secretAttachmentRepository.findAllByIdIn(new LinkedHashSet<>(attachmentIds))) {
            attachmentsById.put(attachment.getId(), attachment);
        }
        return attachmentsById;
    }

    private SecretChatEntity getAccessibleActiveBoundChat(UUID requesterId, UUID sessionId, UUID secretChatId) {
        SecretChatEntity chat = secretChatRepository.findAccessible(secretChatId, requesterId, sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Secret chat not found"));
        boolean initiatorBound = sessionId.equals(chat.getInitiatorSessionId());
        boolean recipientBound = chat.getRecipientSessionId() != null && sessionId.equals(chat.getRecipientSessionId());
        if (!initiatorBound && !recipientBound) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Secret chat is bound to another session");
        }
        if (!"ACTIVE".equals(chat.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Secret chat must be active");
        }
        return chat;
    }

    private String normalizeKind(String kind) {
        String normalizedKind = kind != null ? kind.trim().toUpperCase() : "";
        if (normalizedKind.isBlank()) {
            return "FILE";
        }
        if (!List.of("FILE", "IMAGE", "VOICE", "VIDEO").contains(normalizedKind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported secret attachment kind");
        }
        return normalizedKind;
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
}
