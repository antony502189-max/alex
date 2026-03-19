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
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
@Validated
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final AttachmentUploadSessionService attachmentUploadSessionService;

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
    public ResponseEntity<?> download(@PathVariable UUID attachmentId) {
        AttachmentDownloadResult downloadResult = attachmentService.download(CurrentUser.id(), attachmentId);
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
                        ContentDisposition.attachment().filename(attachment.originalFileName()).build().toString()
                )
                .contentType(MediaType.parseMediaType(attachment.contentType()))
                .contentLength(attachment.bytes().length)
                .body(resource);
    }
}
