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
import java.util.stream.Collectors;
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

        AttachmentUploadSessionEntity session = new AttachmentUploadSessionEntity();
        session.setId(sessionId);
        session.setChatId(request.chatId());
        session.setUploaderUserId(requesterId);
        session.setOriginalFileName(safeFileName(request.originalFileName()));
        session.setContentType(normalizeContentType(request.contentType()));
        session.setKind(normalizeKind(request.kind(), request.contentType()));
        session.setTotalSizeBytes(request.totalSizeBytes());
        session.setUploadedBytes(0L);
        session.setDurationMs(request.durationMs());
        session.setWidth(request.width());
        session.setHeight(request.height());
        session.setWaveform(serializeWaveform(request.waveform()));
        session.setAlbumId(request.albumId());
        session.setAlbumItemIndex(request.albumItemIndex());
        session.setStoragePath(storagePath.toString());
        session.setStatus("ACTIVE");
        session.setExpiresAt(Instant.now().plus(sessionTtl));
        session.setLastChunkAt(null);

        return toResponse(attachmentUploadSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public AttachmentUploadSessionResponse getSession(UUID requesterId, UUID sessionId) {
        return toResponse(getOwnedSession(requesterId, sessionId));
    }

    @Transactional
    public AttachmentUploadSessionResponse uploadChunk(
            UUID requesterId,
            UUID sessionId,
            UploadAttachmentChunkRequest request
    ) {
        AttachmentUploadSessionEntity session = getOwnedActiveSession(requesterId, sessionId);
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
        session.setLastChunkAt(Instant.now());
        return toResponse(attachmentUploadSessionRepository.save(session));
    }

    @Transactional
    public MessageAttachmentResponse completeSession(UUID requesterId, UUID sessionId) {
        AttachmentUploadSessionEntity session = getOwnedActiveSession(requesterId, sessionId);
        if (session.getUploadedBytes() != session.getTotalSizeBytes()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload is not complete");
        }

        MessageAttachmentResponse attachmentResponse = attachmentService.uploadFromPath(
                requesterId,
                session.getChatId(),
                session.getKind(),
                session.getDurationMs(),
                session.getWidth(),
                session.getHeight(),
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
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(Instant.now())) {
            session.setStatus("EXPIRED");
            attachmentUploadSessionRepository.save(session);
            deleteSessionFile(session);
            throw new ResponseStatusException(HttpStatus.GONE, "Upload session has expired");
        }
        return session;
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
                session.getAlbumItemIndex()
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

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType.trim().toLowerCase();
    }

    private String normalizeKind(String kind, String contentType) {
        String normalizedKind = kind != null ? kind.trim().toUpperCase() : "";
        String normalizedContentType = normalizeContentType(contentType);
        if (normalizedKind.isBlank()) {
            if ("image/gif".equalsIgnoreCase(normalizedContentType)) {
                return "GIF";
            }
            if (normalizedContentType.startsWith("image/")) {
                return "IMAGE";
            }
            if (normalizedContentType.startsWith("video/")) {
                return "VIDEO";
            }
            if (normalizedContentType.startsWith("audio/")) {
                return "AUDIO";
            }
            return "FILE";
        }
        if (!List.of("FILE", "VOICE", "IMAGE", "VIDEO", "AUDIO", "GIF", "VIDEO_NOTE").contains(normalizedKind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported attachment kind");
        }
        return normalizedKind;
    }

    private String safeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "file";
        }
        String normalized = originalFileName.replace("\\", "_").replace("/", "_").trim();
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private String serializeWaveform(List<Integer> waveform) {
        if (waveform == null || waveform.isEmpty()) {
            return null;
        }
        if (waveform.size() > 96) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Waveform is too large");
        }
        for (Integer sample : waveform) {
            if (sample == null || sample < 0 || sample > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Waveform sample is out of range");
            }
        }
        return waveform.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}
