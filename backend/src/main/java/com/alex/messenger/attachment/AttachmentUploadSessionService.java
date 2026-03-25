package com.alex.messenger.attachment;

import com.alex.messenger.attachment.dto.AttachmentUploadSessionResponse;
import com.alex.messenger.attachment.dto.CreateAttachmentUploadSessionRequest;
import com.alex.messenger.attachment.dto.UploadAttachmentChunkRequest;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.message.dto.MessageAttachmentResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttachmentUploadSessionService {

    private final AttachmentUploadSessionRepository attachmentUploadSessionRepository;
    private final AttachmentService attachmentService;
    private final ChatService chatService;
    private final Path sessionStorageDirectory;
    private final int chunkSizeBytes;
    private final Duration sessionTtl;
    private final int cleanupBatchSize;
    private final long maxFileSizeBytes;

    public AttachmentUploadSessionService(
            AttachmentUploadSessionRepository attachmentUploadSessionRepository,
            AttachmentService attachmentService,
            ChatService chatService,
            @Value("${alex.storage.root}") String storageRoot,
            @Value("${alex.storage.attachments.resumable.chunk-size-bytes}") int chunkSizeBytes,
            @Value("${alex.storage.attachments.resumable.session-ttl}") Duration sessionTtl,
            @Value("${alex.storage.attachments.resumable.cleanup-batch-size}") int cleanupBatchSize,
            @Value("${alex.storage.attachments.max-file-size-bytes}") long maxFileSizeBytes
    ) {
        this.attachmentUploadSessionRepository = attachmentUploadSessionRepository;
        this.attachmentService = attachmentService;
        this.chatService = chatService;
        this.sessionStorageDirectory = Path.of(storageRoot).toAbsolutePath().resolve("attachment-upload-sessions");
        this.chunkSizeBytes = chunkSizeBytes;
        this.sessionTtl = sessionTtl;
        this.cleanupBatchSize = cleanupBatchSize;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Transactional
    public AttachmentUploadSessionResponse createSession(
            UUID requesterId,
            CreateAttachmentUploadSessionRequest request
    ) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, request.chatId());
        chatService.ensureCanPost(chat, requesterId);

        if (request.totalSizeBytes() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment is empty");
        }
        if (request.totalSizeBytes() > maxFileSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Attachment is too large");
        }
        if (request.durationMs() != null && request.durationMs() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment duration is invalid");
        }
        if (request.width() != null && request.width() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment width is invalid");
        }
        if (request.height() != null && request.height() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment height is invalid");
        }
        if (request.albumItemIndex() != null && request.albumItemIndex() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album item index is invalid");
        }
        if (request.albumItemIndex() != null && request.albumId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album item index requires album id");
        }

        UUID sessionId = UUID.randomUUID();
        Path storagePath = sessionStorageDirectory.resolve(sessionId + ".part");
        ensureSessionDirectoryExists();
        try {
            Files.deleteIfExists(storagePath);
            Files.createFile(storagePath);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to initialize upload session", exception);
        }

        AttachmentMetadataSupport.UploadMetadata metadata = AttachmentMetadataSupport.prepareResumableUpload(
                request.originalFileName(),
                request.contentType(),
                request.kind(),
                request.durationMs(),
                request.width(),
                request.height(),
                request.hdPhoto(),
                request.waveform(),
                request.albumId(),
                request.albumItemIndex()
        );
        AttachmentUploadSessionEntity session = new AttachmentUploadSessionEntity();
        session.setId(sessionId);
        session.setChatId(request.chatId());
        session.setUploaderUserId(requesterId);
        session.setOriginalFileName(metadata.originalFileName());
        session.setContentType(metadata.contentType());
        session.setKind(metadata.kind());
        session.setTotalSizeBytes(request.totalSizeBytes());
        session.setUploadedBytes(0L);
        session.setDurationMs(metadata.durationMs());
        session.setWidth(metadata.width());
        session.setHeight(metadata.height());
        session.setHdPhoto(metadata.hdPhoto());
        session.setWaveform(AttachmentMetadataSupport.serializeWaveform(metadata.waveformSamples()));
        session.setAlbumId(metadata.albumId());
        session.setAlbumItemIndex(metadata.albumItemIndex());
        session.setStoragePath(storagePath.toString());
        session.setStatus("ACTIVE");
        session.setExpiresAt(Instant.now().plus(sessionTtl));
        session.setLastChunkAt(null);

        return toResponse(attachmentUploadSessionRepository.save(session));
    }

    @Transactional
    public AttachmentUploadSessionResponse getSession(UUID requesterId, UUID sessionId) {
        AttachmentUploadSessionEntity session = getOwnedSession(requesterId, sessionId);
        expireSessionIfNecessary(session, Instant.now());
        return toResponse(session);
    }

    @Transactional
    public AttachmentUploadSessionResponse uploadChunk(
            UUID requesterId,
            UUID sessionId,
            UploadAttachmentChunkRequest request
    ) {
        AttachmentUploadSessionEntity session = getOwnedActiveSession(requesterId, sessionId);
        ensureStagedFileConsistency(session);
        if (request.offset() != session.getUploadedBytes()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chunk offset does not match uploaded bytes");
        }
        if (request.base64Chunk() == null || request.base64Chunk().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chunk payload is empty");
        }

        byte[] chunkBytes;
        try {
            chunkBytes = Base64.getDecoder().decode(request.base64Chunk());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chunk payload is invalid", exception);
        }

        if (chunkBytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chunk payload is empty");
        }
        if (chunkBytes.length > chunkSizeBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chunk payload exceeds max chunk size");
        }

        long nextUploadedBytes = session.getUploadedBytes() + chunkBytes.length;
        if (nextUploadedBytes > session.getTotalSizeBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chunk exceeds declared attachment size");
        }

        try {
            Files.write(
                    Path.of(session.getStoragePath()),
                    chunkBytes,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to write upload chunk", exception);
        }

        session.setUploadedBytes(nextUploadedBytes);
        Instant now = Instant.now();
        session.setLastChunkAt(now);
        session.setExpiresAt(now.plus(sessionTtl));
        return toResponse(attachmentUploadSessionRepository.save(session));
    }

    @Transactional
    public MessageAttachmentResponse completeSession(UUID requesterId, UUID sessionId) {
        AttachmentUploadSessionEntity session = getOwnedActiveSession(requesterId, sessionId);
        if (session.getUploadedBytes() != session.getTotalSizeBytes()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload is not complete");
        }
        ensureStagedFileConsistency(session);

        MessageAttachmentResponse attachmentResponse = attachmentService.uploadFromPath(
                requesterId,
                session.getChatId(),
                session.getKind(),
                session.getDurationMs(),
                session.getWidth(),
                session.getHeight(),
                session.getHdPhoto(),
                session.getWaveform(),
                session.getAlbumId(),
                session.getAlbumItemIndex(),
                session.getOriginalFileName(),
                session.getContentType(),
                session.getTotalSizeBytes(),
                Path.of(session.getStoragePath())
        );

        session.setStatus("COMPLETED");
        session.setCompletedAttachmentId(attachmentResponse.attachmentId());
        attachmentUploadSessionRepository.save(session);
        deleteSessionFile(session);
        return attachmentResponse;
    }

    @Transactional
    public void abortSession(UUID requesterId, UUID sessionId) {
        AttachmentUploadSessionEntity session = getOwnedSession(requesterId, sessionId);
        session.setStatus("ABORTED");
        attachmentUploadSessionRepository.save(session);
        deleteSessionFile(session);
    }

    @Transactional
    public void deleteExpiredSessions(Instant now) {
        List<AttachmentUploadSessionEntity> expiredSessions =
                attachmentUploadSessionRepository.findByStatusInAndExpiresAtBeforeOrderByExpiresAtAsc(
                        List.of("ACTIVE"),
                        now,
                        PageRequest.of(0, cleanupBatchSize)
                );

        for (AttachmentUploadSessionEntity session : expiredSessions) {
            session.setStatus("EXPIRED");
            attachmentUploadSessionRepository.save(session);
            deleteSessionFile(session);
        }
    }

    private AttachmentUploadSessionEntity getOwnedSession(UUID requesterId, UUID sessionId) {
        return attachmentUploadSessionRepository.findByIdAndUploaderUserId(sessionId, requesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload session not found"));
    }

    private AttachmentUploadSessionEntity getOwnedActiveSession(UUID requesterId, UUID sessionId) {
        AttachmentUploadSessionEntity session = getOwnedSession(requesterId, sessionId);
        if (!"ACTIVE".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload session is not active");
        }
        if (expireSessionIfNecessary(session, Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Upload session has expired");
        }
        return session;
    }

    private boolean expireSessionIfNecessary(AttachmentUploadSessionEntity session, Instant now) {
        if (!"ACTIVE".equals(session.getStatus())) {
            return false;
        }
        if (session.getExpiresAt() == null || !session.getExpiresAt().isBefore(now)) {
            return false;
        }
        session.setStatus("EXPIRED");
        attachmentUploadSessionRepository.save(session);
        deleteSessionFile(session);
        return true;
    }

    private void ensureStagedFileConsistency(AttachmentUploadSessionEntity session) {
        Path storagePath = Path.of(session.getStoragePath());
        long stagedFileSize;
        try {
            if (!Files.exists(storagePath)) {
                abortInconsistentSession(session);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload session data is missing; restart upload");
            }
            stagedFileSize = Files.size(storagePath);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to inspect upload session data",
                    exception
            );
        }

        if (stagedFileSize != session.getUploadedBytes()) {
            abortInconsistentSession(session);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload session data is inconsistent; restart upload");
        }
    }

    private void abortInconsistentSession(AttachmentUploadSessionEntity session) {
        session.setStatus("ABORTED");
        attachmentUploadSessionRepository.save(session);
        deleteSessionFile(session);
    }

    private AttachmentUploadSessionResponse toResponse(AttachmentUploadSessionEntity session) {
        return new AttachmentUploadSessionResponse(
                session.getId(),
                session.getChatId(),
                session.getOriginalFileName(),
                session.getContentType(),
                session.getKind(),
                session.getTotalSizeBytes(),
                session.getUploadedBytes(),
                chunkSizeBytes,
                session.getStatus(),
                session.getUploadedBytes() >= session.getTotalSizeBytes(),
                session.getExpiresAt(),
                session.getCompletedAttachmentId(),
                session.getAlbumId(),
                session.getAlbumItemIndex(),
                Boolean.TRUE.equals(session.getHdPhoto())
        );
    }

    private void ensureSessionDirectoryExists() {
        try {
            Files.createDirectories(sessionStorageDirectory);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to initialize attachment upload directory",
                    exception
            );
        }
    }

    private void deleteSessionFile(AttachmentUploadSessionEntity session) {
        try {
            Files.deleteIfExists(Path.of(session.getStoragePath()));
        } catch (IOException ignored) {
            // Non-fatal after status transition.
        }
    }

}
