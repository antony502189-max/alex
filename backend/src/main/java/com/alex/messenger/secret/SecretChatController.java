package com.alex.messenger.secret;

import com.alex.messenger.attachment.AttachmentAccessResponse;
import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.secret.dto.AcceptSecretChatRequest;
import com.alex.messenger.secret.dto.CreateSecretChatRequest;
import com.alex.messenger.secret.dto.SecretAttachmentUploadResponse;
import com.alex.messenger.secret.dto.SecretChatMessageResponse;
import com.alex.messenger.secret.dto.SecretChatReadEventResponse;
import com.alex.messenger.secret.dto.SecretChatScreenshotEventResponse;
import com.alex.messenger.secret.dto.SecretChatSummaryResponse;
import com.alex.messenger.secret.dto.SendSecretChatMessageRequest;
import com.alex.messenger.secret.dto.UpdateSecretChatTimerRequest;
import com.alex.messenger.shared.CurrentSession;
import com.alex.messenger.shared.CurrentUser;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/secret-chats")
@RequiredArgsConstructor
public class SecretChatController {

    private final FeatureFlagService featureFlagService;
    private final SecretChatService secretChatService;
    private final SecretAttachmentService secretAttachmentService;

    @GetMapping
    public ResponseEntity<List<SecretChatSummaryResponse>> listSecretChats() {
        featureFlagService.requireSecretChatsEnabled();
        return ResponseEntity.ok(secretChatService.listChats(CurrentUser.id(), CurrentSession.id()));
    }

    @PostMapping
    public ResponseEntity<SecretChatSummaryResponse> createSecretChat(
            @Valid @RequestBody CreateSecretChatRequest request
    ) {
        featureFlagService.requireSecretChatsEnabled();
        return ResponseEntity.ok(secretChatService.createChat(CurrentUser.id(), CurrentSession.id(), request));
    }

    @PostMapping("/{secretChatId}/accept")
    public ResponseEntity<SecretChatSummaryResponse> acceptSecretChat(
            @PathVariable UUID secretChatId,
            @Valid @RequestBody AcceptSecretChatRequest request
    ) {
        featureFlagService.requireSecretChatsEnabled();
        return ResponseEntity.ok(secretChatService.acceptChat(CurrentUser.id(), CurrentSession.id(), secretChatId, request));
    }

    @PostMapping("/{secretChatId}/decline")
    public ResponseEntity<SecretChatSummaryResponse> declineSecretChat(@PathVariable UUID secretChatId) {
        featureFlagService.requireSecretChatsEnabled();
        return ResponseEntity.ok(secretChatService.declineChat(CurrentUser.id(), CurrentSession.id(), secretChatId));
    }

    @PostMapping("/{secretChatId}/close")
    public ResponseEntity<SecretChatSummaryResponse> closeSecretChat(@PathVariable UUID secretChatId) {
        featureFlagService.requireSecretChatsEnabled();
        return ResponseEntity.ok(secretChatService.closeChat(CurrentUser.id(), CurrentSession.id(), secretChatId));
    }

    @PatchMapping("/{secretChatId}/timer")
    public ResponseEntity<SecretChatSummaryResponse> updateSecretChatTimer(
            @PathVariable UUID secretChatId,
            @Valid @RequestBody UpdateSecretChatTimerRequest request
    ) {
        featureFlagService.requireSecretChatsEnabled();
        return ResponseEntity.ok(secretChatService.updateTimer(CurrentUser.id(), CurrentSession.id(), secretChatId, request));
    }

    @GetMapping("/{secretChatId}/messages")
    public ResponseEntity<List<SecretChatMessageResponse>> getSecretChatMessages(
            @PathVariable UUID secretChatId,
            @RequestParam(required = false) Instant before,
            @RequestParam(defaultValue = "50") int limit
    ) {
        int validatedLimit = requireLimit(limit, 100);
        featureFlagService.requireSecretChatsEnabled();
        return ResponseEntity.ok(
                secretChatService.getMessages(CurrentUser.id(), CurrentSession.id(), secretChatId, before, validatedLimit)
        );
    }

    @PostMapping("/{secretChatId}/attachments/upload")
    public ResponseEntity<SecretAttachmentUploadResponse> uploadSecretAttachment(
            @PathVariable UUID secretChatId,
            @RequestParam(required = false) String kind,
            @RequestParam("file") MultipartFile file
    ) {
        String validatedKind = requireAttachmentKind(kind);
        featureFlagService.requireSecretChatsEnabled();
        return ResponseEntity.ok(
                secretAttachmentService.upload(CurrentUser.id(), CurrentSession.id(), secretChatId, validatedKind, file)
        );
    }

    @GetMapping("/attachments/{attachmentId}/access")
    public ResponseEntity<AttachmentAccessResponse> getSecretAttachmentAccess(@PathVariable UUID attachmentId) {
        featureFlagService.requireSecretChatsEnabled();
        return ResponseEntity.ok(
                secretAttachmentService.getAccess(CurrentUser.id(), CurrentSession.id(), attachmentId)
        );
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteSecretAttachment(@PathVariable UUID attachmentId) {
        featureFlagService.requireSecretChatsEnabled();
        secretAttachmentService.removePendingAttachment(CurrentUser.id(), CurrentSession.id(), attachmentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{secretChatId}/read")
    public ResponseEntity<SecretChatReadEventResponse> markSecretChatRead(@PathVariable UUID secretChatId) {
        featureFlagService.requireSecretChatsEnabled();
        return ResponseEntity.ok(secretChatService.markRead(CurrentUser.id(), CurrentSession.id(), secretChatId));
    }

    @PostMapping("/{secretChatId}/screenshot")
    public ResponseEntity<SecretChatScreenshotEventResponse> reportScreenshot(@PathVariable UUID secretChatId) {
        featureFlagService.requireSecretChatsEnabled();
        return ResponseEntity.ok(secretChatService.reportScreenshot(CurrentUser.id(), CurrentSession.id(), secretChatId));
    }

    @PostMapping("/{secretChatId}/messages")
    public ResponseEntity<SecretChatMessageResponse> sendSecretChatMessage(
            @PathVariable UUID secretChatId,
            @Valid @RequestBody SendSecretChatMessageRequest request
    ) {
        featureFlagService.requireSecretChatsEnabled();
        return ResponseEntity.ok(secretChatService.sendMessage(CurrentUser.id(), CurrentSession.id(), secretChatId, request));
    }

    private int requireLimit(int limit, int max) {
        if (limit < 1 || limit > max) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and " + max
            );
        }
        return limit;
    }

    private String requireAttachmentKind(String kind) {
        if (kind == null) {
            return null;
        }
        String normalizedKind = kind.trim();
        if (normalizedKind.isBlank()) {
            return null;
        }
        if (!List.of("FILE", "IMAGE", "VOICE", "VIDEO").contains(normalizedKind.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported secret attachment kind");
        }
        return normalizedKind;
    }
}
