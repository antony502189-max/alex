package com.alex.messenger.compliance;

import com.alex.messenger.compliance.dto.ComplianceCaseApproveRequest;
import com.alex.messenger.compliance.dto.ComplianceCaseArtifactDownloadResponse;
import com.alex.messenger.compliance.dto.ComplianceCaseCreateRequest;
import com.alex.messenger.compliance.dto.ComplianceCaseExportDownloadAuditResponse;
import com.alex.messenger.compliance.dto.ComplianceCaseEventResponse;
import com.alex.messenger.compliance.dto.ComplianceCaseExportArtifactResponse;
import com.alex.messenger.compliance.dto.ComplianceCaseExportResponse;
import com.alex.messenger.compliance.dto.ComplianceCaseResponse;
import com.alex.messenger.lawful.LawfulInterceptionService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.MessageAttachmentResponse;
import com.alex.messenger.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ComplianceService {

    private static final String ARTIFACT_CONTENT_TYPE = "application/vnd.alex.compliance-export+json";
    private static final String SYSTEM_CLEANUP_OPERATOR_ID = "system:compliance-cleanup";

    private final ComplianceCaseRepository complianceCaseRepository;
    private final ComplianceCaseEventRepository complianceCaseEventRepository;
    private final ComplianceCaseExportArtifactRepository complianceCaseExportArtifactRepository;
    private final ComplianceCaseExportDownloadAuditRepository complianceCaseExportDownloadAuditRepository;
    private final LawfulInterceptionService lawfulInterceptionService;
    private final ComplianceExportChecksumService complianceExportChecksumService;
    private final ComplianceExportArtifactStorageService complianceExportArtifactStorageService;
    private final ComplianceProperties complianceProperties;
    private final UserRepository userRepository;

    @Transactional
    public ComplianceCaseResponse createCase(String operatorId, ComplianceCaseCreateRequest request) {
        String normalizedOperatorId = normalizeOperatorId(operatorId);
        validateRange(request.fromInclusive(), request.toExclusive());
        if (!userRepository.existsById(request.targetUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user not found");
        }

        ComplianceCaseEntity complianceCase = new ComplianceCaseEntity();
        complianceCase.setTargetUserId(request.targetUserId());
        complianceCase.setCaseReference(normalizeRequired(request.caseReference(), "Case reference", 120));
        complianceCase.setLegalBasis(normalizeRequired(request.legalBasis(), "Legal basis", 255));
        complianceCase.setReason(normalizeRequired(request.reason(), "Reason", 500));
        complianceCase.setFromInclusive(request.fromInclusive());
        complianceCase.setToExclusive(request.toExclusive());
        complianceCase.setRequestedByOperatorId(normalizedOperatorId);

        ComplianceCaseEntity savedCase = complianceCaseRepository.save(complianceCase);
        recordEvent(
                savedCase.getId(),
                normalizedOperatorId,
                ComplianceCaseEventType.CASE_CREATED,
                "Case created for user %s under reference %s".formatted(savedCase.getTargetUserId(), savedCase.getCaseReference())
        );
        return getCase(savedCase.getId());
    }

    @Transactional
    public ComplianceCaseResponse getCase(String operatorId, UUID caseId) {
        String normalizedOperatorId = normalizeOperatorId(operatorId);
        ComplianceCaseResponse response = getCase(caseId);
        recordCaseAccess(caseId, normalizedOperatorId, "Case viewed");
        return response;
    }

    private ComplianceCaseResponse getCase(UUID caseId) {
        ComplianceCaseEntity complianceCase = getCaseEntity(caseId);
        return toResponse(
                complianceCase,
                complianceCaseEventRepository.findAllByCaseIdOrderByCreatedAtAsc(caseId)
        );
    }

    @Transactional
    public List<ComplianceCaseExportArtifactResponse> listArtifacts(String operatorId, UUID caseId) {
        String normalizedOperatorId = normalizeOperatorId(operatorId);
        List<ComplianceCaseExportArtifactResponse> response = listArtifacts(caseId);
        recordCaseAccess(caseId, normalizedOperatorId, "Export artifacts listed");
        return response;
    }

    private List<ComplianceCaseExportArtifactResponse> listArtifacts(UUID caseId) {
        getCaseEntity(caseId);
        Instant now = Instant.now();
        return complianceCaseExportArtifactRepository.findAllByCaseIdAndDeletedAtIsNullOrderByExportedAtDescCreatedAtDesc(caseId)
                .stream()
                .filter(artifact -> isArtifactAvailableSnapshot(artifact, now))
                .map(this::toArtifactResponse)
                .toList();
    }

    @Transactional
    public ComplianceCaseExportArtifactResponse getLatestArtifactMetadata(String operatorId, UUID caseId) {
        String normalizedOperatorId = normalizeOperatorId(operatorId);
        ComplianceCaseExportArtifactResponse response = getLatestArtifactMetadata(caseId);
        recordCaseAccess(caseId, normalizedOperatorId, "Latest export metadata viewed");
        return response;
    }

    @Transactional
    public ComplianceCaseExportArtifactResponse getArtifactMetadata(String operatorId, UUID caseId, UUID artifactId) {
        String normalizedOperatorId = normalizeOperatorId(operatorId);
        ComplianceCaseExportArtifactResponse response = getArtifactMetadata(caseId, artifactId);
        recordCaseAccess(caseId, normalizedOperatorId, "Export artifact %s metadata viewed".formatted(artifactId));
        return response;
    }

    @Transactional
    public List<ComplianceCaseExportDownloadAuditResponse> listDownloadAudits(String operatorId, UUID caseId) {
        String normalizedOperatorId = normalizeOperatorId(operatorId);
        List<ComplianceCaseExportDownloadAuditResponse> response = listDownloadAudits(caseId);
        recordCaseAccess(caseId, normalizedOperatorId, "Export download audit listed");
        return response;
    }

    private List<ComplianceCaseExportDownloadAuditResponse> listDownloadAudits(UUID caseId) {
        getCaseEntity(caseId);
        return complianceCaseExportDownloadAuditRepository.findAllByCaseIdOrderByDownloadedAtDesc(caseId).stream()
                .map(this::toDownloadAuditResponse)
                .toList();
    }

    @Transactional
    public List<ComplianceCaseExportDownloadAuditResponse> listArtifactDownloadAudits(
            String operatorId,
            UUID caseId,
            UUID artifactId
    ) {
        String normalizedOperatorId = normalizeOperatorId(operatorId);
        List<ComplianceCaseExportDownloadAuditResponse> response = listArtifactDownloadAudits(caseId, artifactId);
        recordCaseAccess(caseId, normalizedOperatorId, "Export artifact %s download audit listed".formatted(artifactId));
        return response;
    }

    private List<ComplianceCaseExportDownloadAuditResponse> listArtifactDownloadAudits(UUID caseId, UUID artifactId) {
        getCaseEntity(caseId);
        complianceCaseExportArtifactRepository.findByIdAndCaseId(artifactId, caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compliance export artifact not found"));
        return complianceCaseExportDownloadAuditRepository.findAllByCaseIdAndArtifactIdOrderByDownloadedAtDesc(caseId, artifactId)
                .stream()
                .map(this::toDownloadAuditResponse)
                .toList();
    }

    private ComplianceCaseExportArtifactResponse getLatestArtifactMetadata(UUID caseId) {
        getCaseEntity(caseId);
        ComplianceCaseExportArtifactEntity artifact = complianceCaseExportArtifactRepository
                .findFirstByCaseIdAndDeletedAtIsNullOrderByExportedAtDescCreatedAtDesc(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compliance export artifact not found"));
        ensureArtifactAvailable(artifact);
        return toArtifactResponse(artifact);
    }

    private ComplianceCaseExportArtifactResponse getArtifactMetadata(UUID caseId, UUID artifactId) {
        getCaseEntity(caseId);
        ComplianceCaseExportArtifactEntity artifact = complianceCaseExportArtifactRepository
                .findByIdAndCaseIdAndDeletedAtIsNull(artifactId, caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compliance export artifact not found"));
        ensureArtifactAvailable(artifact);
        return toArtifactResponse(artifact);
    }

    @Transactional
    public ComplianceCaseResponse approveCase(String operatorId, UUID caseId, ComplianceCaseApproveRequest request) {
        String normalizedOperatorId = normalizeOperatorId(operatorId);
        ComplianceCaseEntity complianceCase = getCaseEntity(caseId);
        if (complianceCase.getStatus() != ComplianceCaseStatus.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Compliance case is no longer pending approval");
        }
        if (normalizedOperatorId.equalsIgnoreCase(complianceCase.getRequestedByOperatorId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Requester cannot approve the same compliance case");
        }

        complianceCase.setApprovedByOperatorId(normalizedOperatorId);
        complianceCase.setApprovedAt(Instant.now());
        complianceCase.setStatus(ComplianceCaseStatus.APPROVED);
        complianceCaseRepository.save(complianceCase);

        String summary = request != null && request.approvalNote() != null && !request.approvalNote().isBlank()
                ? "Case approved: %s".formatted(truncate(request.approvalNote().trim(), 480))
                : "Case approved";
        recordEvent(caseId, normalizedOperatorId, ComplianceCaseEventType.CASE_APPROVED, summary);
        return getCase(caseId);
    }

    @Transactional
    public ComplianceCaseExportResponse exportCase(String operatorId, UUID caseId) {
        String normalizedOperatorId = normalizeOperatorId(operatorId);
        ComplianceCaseEntity complianceCase = getCaseEntity(caseId);
        if (complianceCase.getStatus() == ComplianceCaseStatus.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Compliance case must be approved before export");
        }

        List<ChatMessageResponse> messages = sanitizeMessages(
                lawfulInterceptionService.exportDecryptedMessages(
                        complianceCase.getTargetUserId(),
                        complianceCase.getFromInclusive(),
                        complianceCase.getToExclusive()
                )
        );
        Instant exportedAt = Instant.now();
        ComplianceCaseExportArtifactEntity artifact = buildExportArtifact(
                complianceCase,
                normalizedOperatorId,
                exportedAt,
                messages
        );
        String checksum = artifact.getArtifactChecksum();

        complianceCase.setStatus(ComplianceCaseStatus.EXPORTED);
        complianceCase.setLastExportedAt(exportedAt);
        complianceCase.setLastExportedByOperatorId(normalizedOperatorId);
        complianceCase.setLatestArtifactChecksum(checksum);
        complianceCase.setExportCount((complianceCase.getExportCount() != null ? complianceCase.getExportCount() : 0) + 1);
        complianceCaseRepository.save(complianceCase);

        recordEvent(
                caseId,
                normalizedOperatorId,
                ComplianceCaseEventType.EXPORT_GENERATED,
                "Export artifact %s generated with checksum %s and %d messages"
                        .formatted(artifact.getId(), checksum, messages.size())
        );

        List<ChatMessageResponse> inlineMessages = messages.size() <= complianceProperties.getExport().getMaxInlineMessages()
                ? messages
                : List.of();

        return new ComplianceCaseExportResponse(
                getCase(caseId),
                toArtifactResponse(artifact),
                normalizedOperatorId,
                exportedAt,
                messages.size(),
                checksum,
                inlineMessages
        );
    }

    @Transactional
    public ComplianceCaseArtifactDownloadResponse downloadLatestArtifact(String operatorId, UUID caseId) {
        String normalizedOperatorId = normalizeOperatorId(operatorId);
        ComplianceCaseEntity complianceCase = getCaseEntity(caseId);
        ComplianceCaseExportArtifactEntity artifact = complianceCaseExportArtifactRepository
                .findFirstByCaseIdAndDeletedAtIsNullOrderByExportedAtDescCreatedAtDesc(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compliance export artifact not found"));
        return downloadArtifact(normalizedOperatorId, complianceCase, artifact);
    }

    @Transactional
    public ComplianceCaseArtifactDownloadResponse downloadArtifact(String operatorId, UUID caseId, UUID artifactId) {
        String normalizedOperatorId = normalizeOperatorId(operatorId);
        ComplianceCaseEntity complianceCase = getCaseEntity(caseId);
        ComplianceCaseExportArtifactEntity artifact = complianceCaseExportArtifactRepository.findByIdAndCaseId(artifactId, caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compliance export artifact not found"));
        return downloadArtifact(normalizedOperatorId, complianceCase, artifact);
    }

    @Transactional
    public void deleteExpiredArtifacts(Instant now) {
        List<ComplianceCaseExportArtifactEntity> expiredArtifacts =
                complianceCaseExportArtifactRepository.findByExpiresAtBeforeAndDeletedAtIsNullOrderByExpiresAtAsc(
                        now,
                        PageRequest.of(0, complianceProperties.getExport().getCleanupBatchSize())
                );

        for (ComplianceCaseExportArtifactEntity artifact : expiredArtifacts) {
            artifact.setDeletedAt(now);
            complianceCaseExportArtifactRepository.save(artifact);
            complianceExportArtifactStorageService.deleteArtifact(artifact);
            recordEvent(
                    artifact.getCaseId(),
                    SYSTEM_CLEANUP_OPERATOR_ID,
                    ComplianceCaseEventType.ARTIFACT_EXPIRED,
                    "Export artifact %s expired at %s".formatted(artifact.getId(), now)
            );
        }
    }

    private ComplianceCaseEntity getCaseEntity(UUID caseId) {
        return complianceCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compliance case not found"));
    }

    private ComplianceCaseExportArtifactEntity buildExportArtifact(
            ComplianceCaseEntity complianceCase,
            String operatorId,
            Instant exportedAt,
            List<ChatMessageResponse> messages
    ) {
        ComplianceCaseExportArtifactEntity artifact = new ComplianceCaseExportArtifactEntity();
        artifact.setId(UUID.randomUUID());
        artifact.setCaseId(complianceCase.getId());
        artifact.setExportedByOperatorId(operatorId);
        artifact.setExportedAt(exportedAt);
        artifact.setMessageCount(messages.size());
        artifact.setContentType(ARTIFACT_CONTENT_TYPE);
        artifact.setExpiresAt(exportedAt.plus(complianceProperties.getExport().getArtifactTtl()));
        artifact.setDownloadCount(0);

        ComplianceCaseExportArtifactPayload payload = new ComplianceCaseExportArtifactPayload(
                artifact.getId(),
                complianceCase.getId(),
                complianceCase.getTargetUserId(),
                complianceCase.getCaseReference(),
                complianceCase.getLegalBasis(),
                complianceCase.getReason(),
                complianceCase.getFromInclusive(),
                complianceCase.getToExclusive(),
                complianceCase.getRequestedByOperatorId(),
                complianceCase.getApprovedByOperatorId(),
                operatorId,
                exportedAt,
                messages
        );

        String checksum = complianceExportChecksumService.computeArtifactChecksum(payload);
        ComplianceExportArtifactStorageService.StoredComplianceArtifact storedArtifact =
                complianceExportArtifactStorageService.writeArtifact(payload);

        artifact.setArtifactChecksum(checksum);
        artifact.setStoragePath(storedArtifact.storagePath());
        artifact.setEncryptionIv(storedArtifact.encryptionIv());
        return complianceCaseExportArtifactRepository.save(artifact);
    }

    private ComplianceCaseArtifactDownloadResponse downloadArtifact(
            String operatorId,
            ComplianceCaseEntity complianceCase,
            ComplianceCaseExportArtifactEntity artifact
    ) {
        ensureArtifactAvailable(artifact);
        Instant downloadedAt = Instant.now();
        ComplianceCaseExportArtifactPayload payload;
        try {
            payload = complianceExportArtifactStorageService.readArtifact(artifact);
        } catch (RuntimeException exception) {
            recordFailedDownload(artifact, operatorId, downloadedAt, "Artifact storage read failed");
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to read compliance export artifact",
                    exception
            );
        }

        String recalculatedChecksum;
        try {
            recalculatedChecksum = complianceExportChecksumService.computeArtifactChecksum(payload);
        } catch (RuntimeException exception) {
            recordFailedDownload(artifact, operatorId, downloadedAt, "Artifact checksum verification failed");
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Compliance export checksum verification failed",
                    exception
            );
        }
        if (!artifact.getArtifactChecksum().equals(recalculatedChecksum)) {
            recordFailedDownload(artifact, operatorId, downloadedAt, "Artifact checksum verification failed");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Compliance export checksum verification failed");
        }

        artifact.setDownloadCount(artifact.getDownloadCount() + 1);
        artifact.setLastDownloadedAt(downloadedAt);
        artifact.setLastDownloadedByOperatorId(operatorId);
        complianceCaseExportArtifactRepository.save(artifact);

        recordDownloadAudit(artifact, operatorId, downloadedAt, true);

        recordEvent(
                complianceCase.getId(),
                operatorId,
                ComplianceCaseEventType.ARTIFACT_DOWNLOADED,
                "Export artifact %s downloaded with checksum %s".formatted(
                        artifact.getId(),
                        artifact.getArtifactChecksum()
                )
        );

        return new ComplianceCaseArtifactDownloadResponse(
                getCase(complianceCase.getId()),
                toArtifactResponse(artifact),
                operatorId,
                downloadedAt,
                true,
                sanitizeMessages(payload.messages())
        );
    }

    private List<ChatMessageResponse> sanitizeMessages(List<ChatMessageResponse> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .map(this::sanitizeMessage)
                .toList();
    }

    private ChatMessageResponse sanitizeMessage(ChatMessageResponse message) {
        if (message == null) {
            return null;
        }
        return new ChatMessageResponse(
                message.chatId(),
                message.messageId(),
                message.clientMessageId(),
                message.senderId(),
                message.displaySenderName(),
                null,
                null,
                message.anonymousSender(),
                message.recipientId(),
                message.viaBotUserId(),
                message.topicId(),
                message.threadRootMessageId(),
                message.discussionChatId(),
                message.discussionRootMessageId(),
                message.commentCount(),
                message.text(),
                message.entities(),
                message.messageType(),
                message.caption(),
                message.silent(),
                message.location(),
                message.liveLocation(),
                message.contactCard(),
                message.serviceMessage(),
                message.createdAt(),
                message.replyToMessageId(),
                message.forwardedFromChatId(),
                message.forwardedFromMessageId(),
                message.poll(),
                message.sticker(),
                message.attachments() != null
                        ? message.attachments().stream().map(this::sanitizeAttachment).toList()
                        : List.of(),
                message.reactions(),
                message.deliveryStatus(),
                message.deliveredAt(),
                message.readAt(),
                message.expiresAt(),
                message.editedAt(),
                message.deletedAt(),
                message.disableLinkPreview(),
                message.linkPreview()
        );
    }

    private MessageAttachmentResponse sanitizeAttachment(MessageAttachmentResponse attachment) {
        if (attachment == null) {
            return null;
        }
        return new MessageAttachmentResponse(
                attachment.attachmentId(),
                attachment.originalFileName(),
                attachment.contentType(),
                attachment.kind(),
                attachment.fileSizeBytes(),
                attachment.durationMs(),
                null,
                null,
                null,
                attachment.width(),
                attachment.height(),
                attachment.waveform(),
                null,
                attachment.requiresAuthorization(),
                attachment.streamingSupported(),
                attachment.voiceNote(),
                attachment.roundMessage(),
                attachment.albumId(),
                attachment.albumItemIndex(),
                attachment.moderationStatus(),
                attachment.moderationReason(),
                attachment.sensitiveContent(),
                attachment.blockedByModeration(),
                attachment.sourceAttachmentId(),
                attachment.trimStartMs(),
                attachment.trimEndMs(),
                attachment.hdPhoto()
        );
    }

    private void recordFailedDownload(
            ComplianceCaseExportArtifactEntity artifact,
            String operatorId,
            Instant downloadedAt,
            String reason
    ) {
        recordDownloadAudit(artifact, operatorId, downloadedAt, false);
        recordEvent(
                artifact.getCaseId(),
                operatorId,
                ComplianceCaseEventType.ARTIFACT_DOWNLOAD_FAILED,
                "Export artifact %s download failed: %s".formatted(artifact.getId(), truncate(reason, 400))
        );
    }

    private void recordDownloadAudit(
            ComplianceCaseExportArtifactEntity artifact,
            String operatorId,
            Instant downloadedAt,
            boolean checksumVerified
    ) {
        ComplianceCaseExportDownloadAuditEntity audit = new ComplianceCaseExportDownloadAuditEntity();
        audit.setArtifactId(artifact.getId());
        audit.setCaseId(artifact.getCaseId());
        audit.setOperatorId(operatorId);
        audit.setDownloadedAt(downloadedAt);
        audit.setChecksumVerified(checksumVerified);
        complianceCaseExportDownloadAuditRepository.save(audit);
    }

    private void ensureArtifactAvailable(ComplianceCaseExportArtifactEntity artifact) {
        if (artifact.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.GONE, "Compliance export artifact is no longer available");
        }
        if (artifact.getExpiresAt() != null && !artifact.getExpiresAt().isAfter(Instant.now())) {
            Instant expiredAt = artifact.getExpiresAt();
            artifact.setDeletedAt(Instant.now());
            complianceCaseExportArtifactRepository.save(artifact);
            complianceExportArtifactStorageService.deleteArtifact(artifact);
            recordEvent(
                    artifact.getCaseId(),
                    SYSTEM_CLEANUP_OPERATOR_ID,
                    ComplianceCaseEventType.ARTIFACT_EXPIRED,
                    "Export artifact %s expired at %s".formatted(artifact.getId(), expiredAt)
            );
            throw new ResponseStatusException(HttpStatus.GONE, "Compliance export artifact has expired");
        }
    }

    private ComplianceCaseResponse toResponse(
            ComplianceCaseEntity complianceCase,
            List<ComplianceCaseEventEntity> events
    ) {
        return new ComplianceCaseResponse(
                complianceCase.getId(),
                complianceCase.getTargetUserId(),
                complianceCase.getCaseReference(),
                complianceCase.getLegalBasis(),
                complianceCase.getReason(),
                complianceCase.getFromInclusive(),
                complianceCase.getToExclusive(),
                complianceCase.getStatus().name(),
                complianceCase.getRequestedByOperatorId(),
                complianceCase.getCreatedAt(),
                complianceCase.getApprovedByOperatorId(),
                complianceCase.getApprovedAt(),
                complianceCase.getLastExportedByOperatorId(),
                complianceCase.getLastExportedAt(),
                complianceCase.getExportCount() != null ? complianceCase.getExportCount() : 0,
                complianceCase.getLatestArtifactChecksum(),
                events.stream().map(this::toEventResponse).toList()
        );
    }

    private ComplianceCaseExportArtifactResponse toArtifactResponse(ComplianceCaseExportArtifactEntity artifact) {
        return new ComplianceCaseExportArtifactResponse(
                artifact.getId(),
                artifact.getExportedByOperatorId(),
                artifact.getExportedAt(),
                artifact.getMessageCount(),
                artifact.getArtifactChecksum(),
                artifact.getContentType(),
                artifact.getExpiresAt(),
                artifact.getDownloadCount(),
                artifact.getLastDownloadedAt(),
                artifact.getLastDownloadedByOperatorId()
        );
    }

    private ComplianceCaseEventResponse toEventResponse(ComplianceCaseEventEntity event) {
        return new ComplianceCaseEventResponse(
                event.getId(),
                event.getActorOperatorId(),
                event.getEventType().name(),
                event.getSummary(),
                event.getCreatedAt()
        );
    }

    private ComplianceCaseExportDownloadAuditResponse toDownloadAuditResponse(ComplianceCaseExportDownloadAuditEntity audit) {
        return new ComplianceCaseExportDownloadAuditResponse(
                audit.getId(),
                audit.getArtifactId(),
                audit.getCaseId(),
                audit.getOperatorId(),
                audit.getDownloadedAt(),
                audit.isChecksumVerified()
        );
    }

    private void recordCaseAccess(UUID caseId, String operatorId, String summary) {
        recordEvent(caseId, operatorId, ComplianceCaseEventType.CASE_ACCESSED, summary);
    }

    private void recordEvent(
            UUID caseId,
            String operatorId,
            ComplianceCaseEventType eventType,
            String summary
    ) {
        ComplianceCaseEventEntity event = new ComplianceCaseEventEntity();
        event.setCaseId(caseId);
        event.setActorOperatorId(operatorId);
        event.setEventType(eventType);
        event.setSummary(truncate(summary, 500));
        complianceCaseEventRepository.save(event);
    }

    private void validateRange(Instant fromInclusive, Instant toExclusive) {
        if (fromInclusive != null && toExclusive != null && !toExclusive.isAfter(fromInclusive)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "toExclusive must be later than fromInclusive");
        }
    }

    private boolean isArtifactAvailableSnapshot(ComplianceCaseExportArtifactEntity artifact, Instant now) {
        if (artifact == null || artifact.getDeletedAt() != null) {
            return false;
        }
        return artifact.getExpiresAt() == null || artifact.getExpiresAt().isAfter(now);
    }

    private String normalizeOperatorId(String operatorId) {
        return normalizeRequired(operatorId, "Operator id", 120);
    }

    private String normalizeRequired(String value, String fieldName, int maxLength) {
        String normalized = value != null ? value.trim() : "";
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "%s is required".formatted(fieldName));
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "%s is too long".formatted(fieldName));
        }
        return normalized;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
