package com.alex.messenger.compliance;

import com.alex.messenger.compliance.dto.ComplianceCaseApproveRequest;
import com.alex.messenger.compliance.dto.ComplianceCaseCreateRequest;
import com.alex.messenger.compliance.dto.ComplianceCaseEventResponse;
import com.alex.messenger.compliance.dto.ComplianceCaseExportResponse;
import com.alex.messenger.compliance.dto.ComplianceCaseResponse;
import com.alex.messenger.lawful.LawfulInterceptionService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ComplianceService {

    private final ComplianceCaseRepository complianceCaseRepository;
    private final ComplianceCaseEventRepository complianceCaseEventRepository;
    private final LawfulInterceptionService lawfulInterceptionService;
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

    @Transactional(readOnly = true)
    public ComplianceCaseResponse getCase(UUID caseId) {
        ComplianceCaseEntity complianceCase = getCaseEntity(caseId);
        return toResponse(
                complianceCase,
                complianceCaseEventRepository.findAllByCaseIdOrderByCreatedAtAsc(caseId)
        );
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

        List<ChatMessageResponse> messages = lawfulInterceptionService.exportDecryptedMessages(
                complianceCase.getTargetUserId(),
                complianceCase.getFromInclusive(),
                complianceCase.getToExclusive()
        );
        Instant exportedAt = Instant.now();
        String checksum = computeChecksum(complianceCase, messages);

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
                "Export generated with checksum %s and %d messages".formatted(checksum, messages.size())
        );

        return new ComplianceCaseExportResponse(
                getCase(caseId),
                normalizedOperatorId,
                exportedAt,
                messages.size(),
                checksum,
                messages
        );
    }

    private ComplianceCaseEntity getCaseEntity(UUID caseId) {
        return complianceCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compliance case not found"));
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

    private ComplianceCaseEventResponse toEventResponse(ComplianceCaseEventEntity event) {
        return new ComplianceCaseEventResponse(
                event.getId(),
                event.getActorOperatorId(),
                event.getEventType().name(),
                event.getSummary(),
                event.getCreatedAt()
        );
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

    private String computeChecksum(ComplianceCaseEntity complianceCase, List<ChatMessageResponse> messages) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            updateDigest(messageDigest, complianceCase.getId());
            updateDigest(messageDigest, complianceCase.getTargetUserId());
            updateDigest(messageDigest, complianceCase.getCaseReference());
            updateDigest(messageDigest, complianceCase.getRequestedByOperatorId());
            updateDigest(messageDigest, complianceCase.getApprovedByOperatorId());
            updateDigest(messageDigest, complianceCase.getFromInclusive());
            updateDigest(messageDigest, complianceCase.getToExclusive());
            for (ChatMessageResponse message : messages) {
                updateDigest(messageDigest, message.chatId());
                updateDigest(messageDigest, message.messageId());
                updateDigest(messageDigest, message.senderId());
                updateDigest(messageDigest, message.recipientId());
                updateDigest(messageDigest, message.createdAt());
                updateDigest(messageDigest, message.deliveryStatus());
                updateDigest(messageDigest, message.text());
                updateDigest(messageDigest, message.messageType());
                updateDigest(messageDigest, message.caption());
                updateDigest(messageDigest, message.location());
                updateDigest(messageDigest, message.contactCard());
                updateDigest(messageDigest, message.serviceMessage());
                updateDigest(messageDigest, message.deletedAt());
            }
            return HexFormat.of().formatHex(messageDigest.digest());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to compute export checksum", exception);
        }
    }

    private void updateDigest(MessageDigest messageDigest, Object value) {
        messageDigest.update((value != null ? value.toString() : "<null>").getBytes(StandardCharsets.UTF_8));
        messageDigest.update((byte) '\n');
    }
}
