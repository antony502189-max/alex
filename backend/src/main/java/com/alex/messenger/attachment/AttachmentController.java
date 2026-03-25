package com.alex.messenger.attachment;

import com.alex.messenger.attachment.dto.AttachmentUploadSessionResponse;
import com.alex.messenger.attachment.dto.CreateAttachmentUploadSessionRequest;
import com.alex.messenger.attachment.dto.ModerateAttachmentRequest;
import com.alex.messenger.attachment.dto.TrimAttachmentRequest;
import com.alex.messenger.attachment.dto.UploadAttachmentChunkRequest;
import com.alex.messenger.message.dto.MessageAttachmentResponse;
import com.alex.messenger.shared.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
@Validated
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final AttachmentUploadSessionService attachmentUploadSessionService;
    private final AttachmentAccessTokenService attachmentAccessTokenService;

    @PostMapping("/upload")
    public ResponseEntity<MessageAttachmentResponse> upload(
            @RequestParam UUID chatId,
            @RequestParam(required = false)
            @Size(max = 16)
            @Pattern(regexp = "(^\\s*$)|(?i)^\\s*(FILE|VOICE|IMAGE|VIDEO|AUDIO|GIF|VIDEO_NOTE)\\s*$")
            String kind,
            @RequestParam(required = false) @Positive Long durationMs,
            @RequestParam(required = false) @Positive Integer width,
            @RequestParam(required = false) @Positive Integer height,
            @RequestParam(required = false) Boolean hdPhoto,
            @RequestParam(required = false) @Size(max = 383) String waveform,
            @RequestParam(required = false) UUID albumId,
            @RequestParam(required = false) @PositiveOrZero Integer albumItemIndex,
            @RequestParam("file") MultipartFile file
    ) {
        validateUploadRequest(kind, durationMs, width, height, albumItemIndex);
        return ResponseEntity.ok(
                attachmentService.upload(
                        CurrentUser.id(),
                        chatId,
                        kind,
                        durationMs,
                        width,
                        height,
                        hdPhoto,
                        waveform,
                        albumId,
                        albumItemIndex,
                        file
                )
        );
    }

    private void validateUploadRequest(
            String kind,
            Long durationMs,
            Integer width,
            Integer height,
            Integer albumItemIndex
    ) {
        String normalizedKind = kind != null ? kind.trim().toUpperCase() : "";
        if (!normalizedKind.isBlank()
                && !List.of("FILE", "VOICE", "IMAGE", "VIDEO", "AUDIO", "GIF", "VIDEO_NOTE").contains(normalizedKind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported attachment kind");
        }
        if (durationMs != null && durationMs <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment duration is invalid");
        }
        if (width != null && width <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment width is invalid");
        }
        if (height != null && height <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment height is invalid");
        }
        if (albumItemIndex != null && albumItemIndex < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album item index is invalid");
        }
    }

    @PostMapping("/upload-sessions")
    public ResponseEntity<AttachmentUploadSessionResponse> createUploadSession(
            @Valid @org.springframework.web.bind.annotation.RequestBody CreateAttachmentUploadSessionRequest request
    ) {
        return ResponseEntity.ok(attachmentUploadSessionService.createSession(CurrentUser.id(), request));
    }

    @GetMapping("/upload-sessions/{sessionId}")
    public ResponseEntity<AttachmentUploadSessionResponse> getUploadSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(attachmentUploadSessionService.getSession(CurrentUser.id(), sessionId));
    }

    @PostMapping("/upload-sessions/{sessionId}/chunks")
    public ResponseEntity<AttachmentUploadSessionResponse> uploadChunk(
            @PathVariable UUID sessionId,
            @Valid @org.springframework.web.bind.annotation.RequestBody UploadAttachmentChunkRequest request
    ) {
        return ResponseEntity.ok(attachmentUploadSessionService.uploadChunk(CurrentUser.id(), sessionId, request));
    }

    @PostMapping("/upload-sessions/{sessionId}/complete")
    public ResponseEntity<MessageAttachmentResponse> completeUploadSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(attachmentUploadSessionService.completeSession(CurrentUser.id(), sessionId));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/upload-sessions/{sessionId}")
    public ResponseEntity<Void> abortUploadSession(@PathVariable UUID sessionId) {
        attachmentUploadSessionService.abortSession(CurrentUser.id(), sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{attachmentId}/access")
    public ResponseEntity<AttachmentAccessResponse> access(@PathVariable UUID attachmentId) {
        return ResponseEntity.ok(attachmentService.getAccess(CurrentUser.id(), attachmentId));
    }

    @GetMapping("/albums/{albumId}")
    public ResponseEntity<List<MessageAttachmentResponse>> album(@PathVariable UUID albumId) {
        return ResponseEntity.ok(attachmentService.listAlbum(CurrentUser.id(), albumId));
    }

    @GetMapping("/recent-gifs")
    public ResponseEntity<List<MessageAttachmentResponse>> recentGifs() {
        return ResponseEntity.ok(attachmentService.listRecentGifs(CurrentUser.id()));
    }

    @PostMapping("/{attachmentId}/moderation")
    public ResponseEntity<MessageAttachmentResponse> moderate(
            @PathVariable UUID attachmentId,
            @Valid @org.springframework.web.bind.annotation.RequestBody ModerateAttachmentRequest request
    ) {
        return ResponseEntity.ok(attachmentService.reviewModeration(CurrentUser.id(), attachmentId, request));
    }

    @PostMapping("/{attachmentId}/trim")
    public ResponseEntity<MessageAttachmentResponse> trim(
            @PathVariable UUID attachmentId,
            @Valid @org.springframework.web.bind.annotation.RequestBody(required = false) TrimAttachmentRequest request
    ) {
        return ResponseEntity.ok(attachmentService.trim(CurrentUser.id(), attachmentId, request));
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<?> download(
            @PathVariable UUID attachmentId,
            @RequestParam(name = AttachmentAccessTokenService.QUERY_PARAMETER, required = false) String accessToken
    ) {
        AttachmentRequesterAccess requesterAccess = resolveAttachmentRequester(attachmentId, accessToken);
        AttachmentDownloadResult downloadResult = attachmentService.download(
                requesterAccess.requesterId(),
                attachmentId,
                requesterAccess.accessExpiresAt()
        );
        return toAttachmentResponse(downloadResult, false);
    }

    @GetMapping("/{attachmentId}/preview")
    public ResponseEntity<?> preview(
            @PathVariable UUID attachmentId,
            @RequestParam(name = AttachmentAccessTokenService.QUERY_PARAMETER, required = false) String accessToken
    ) {
        AttachmentRequesterAccess requesterAccess = resolveAttachmentRequester(attachmentId, accessToken);
        AttachmentDownloadResult downloadResult = attachmentService.preview(
                requesterAccess.requesterId(),
                attachmentId,
                requesterAccess.accessExpiresAt()
        );
        return toAttachmentResponse(downloadResult, true);
    }

    @GetMapping("/{attachmentId}/thumbnail")
    public ResponseEntity<?> thumbnail(
            @PathVariable UUID attachmentId,
            @RequestParam(name = AttachmentAccessTokenService.QUERY_PARAMETER, required = false) String accessToken
    ) {
        AttachmentRequesterAccess requesterAccess = resolveAttachmentRequester(attachmentId, accessToken);
        AttachmentDownloadResult downloadResult = attachmentService.thumbnail(
                requesterAccess.requesterId(),
                attachmentId,
                requesterAccess.accessExpiresAt()
        );
        return toAttachmentResponse(downloadResult, true);
    }

    private ResponseEntity<?> toAttachmentResponse(AttachmentDownloadResult downloadResult, boolean inline) {
        if (downloadResult.isRedirect()) {
            return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                    .location(URI.create(downloadResult.redirectUrl()))
                    .build();
        }

        DownloadedAttachment attachment = downloadResult.downloadedAttachment();
        ByteArrayResource resource = new ByteArrayResource(attachment.bytes());

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        (inline
                                ? ContentDisposition.inline().filename(attachment.originalFileName())
                                : ContentDisposition.attachment().filename(attachment.originalFileName()))
                                .build()
                                .toString()
                )
                .contentType(MediaType.parseMediaType(attachment.contentType()))
                .contentLength(attachment.bytes().length)
                .body(resource);
    }

    private AttachmentRequesterAccess resolveAttachmentRequester(UUID attachmentId, String accessToken) {
        UUID authenticatedUserId = currentAuthenticatedUserId();
        if (authenticatedUserId != null) {
            return new AttachmentRequesterAccess(authenticatedUserId, null);
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        AttachmentAccessTokenService.ValidatedAttachmentAccessToken validated =
                attachmentAccessTokenService.validate(accessToken, attachmentId);
        return new AttachmentRequesterAccess(validated.userId(), validated.expiresAt());
    }

    private UUID currentAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record AttachmentRequesterAccess(UUID requesterId, Instant accessExpiresAt) {
    }
}
