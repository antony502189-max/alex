package com.alex.messenger.lawful;

import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.lawful.dto.DirectLawfulExportRequest;
import com.alex.messenger.lawful.dto.DirectLawfulExportResponse;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.MessageAttachmentResponse;
import com.alex.messenger.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LawfulDirectExportService {

    private final LawfulDirectExportRepository lawfulDirectExportRepository;
    private final LawfulInterceptionService lawfulInterceptionService;
    private final LawfulExportChecksumService checksumService;
    private final UserRepository userRepository;
    private final FeatureFlagService featureFlagService;
    private final LawfulProperties lawfulProperties;

    @Transactional
    public DirectLawfulExportResponse export(String operatorId, DirectLawfulExportRequest request) {
        featureFlagService.requireLawfulDirectExportEnabled();
        String normalizedOperatorId = normalizeRequired(operatorId, "Operator id", 120);
        validateRange(request.fromInclusive(), request.toExclusive());
        if (!userRepository.existsById(request.targetUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user not found");
        }

        boolean includeAttachmentsMetadata = Boolean.TRUE.equals(request.includeAttachmentsMetadata());
        List<ChatMessageResponse> rawMessages = lawfulInterceptionService.exportDecryptedMessages(
                request.targetUserId(),
                request.fromInclusive(),
                request.toExclusive()
        );
        List<ChatMessageResponse> messages = rawMessages.stream()
                .map(message -> sanitizeMessage(message, includeAttachmentsMetadata))
                .toList();

        LawfulDirectExportEntity export = new LawfulDirectExportEntity();
        export.setTargetUserId(request.targetUserId());
        export.setOperatorId(normalizedOperatorId);
        export.setReason(normalizeRequired(request.reason(), "Reason", 500));
        export.setFromInclusive(request.fromInclusive());
        export.setToExclusive(request.toExclusive());
        export.setIncludeAttachmentsMetadata(includeAttachmentsMetadata);
        export.setMessageCount(messages.size());
        export.setArtifactLocation(null);
        export.setExportedAt(Instant.now());

        UUID exportId = UUID.randomUUID();
        export.setId(exportId);
        export.setArtifactChecksum(checksumService.computeDirectExportChecksum(
                exportId,
                request.targetUserId(),
                normalizedOperatorId,
                export.getReason(),
                request.fromInclusive(),
                request.toExclusive(),
                includeAttachmentsMetadata,
                messages
        ));

        LawfulDirectExportEntity saved = lawfulDirectExportRepository.save(export);
        List<ChatMessageResponse> inlineMessages =
                messages.size() <= Math.max(0, lawfulProperties.getDirectExport().getInlineMessageLimit())
                        ? messages
                        : List.of();
        return new DirectLawfulExportResponse(
                saved.getId(),
                saved.getTargetUserId(),
                saved.getOperatorId(),
                saved.getReason(),
                saved.getFromInclusive(),
                saved.getToExclusive(),
                saved.isIncludeAttachmentsMetadata(),
                saved.getExportedAt(),
                saved.getMessageCount(),
                saved.getArtifactChecksum(),
                saved.getArtifactLocation(),
                inlineMessages
        );
    }

    private ChatMessageResponse sanitizeMessage(ChatMessageResponse message, boolean includeAttachmentsMetadata) {
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
                includeAttachmentsMetadata
                        ? message.attachments().stream().map(this::sanitizeAttachmentMetadata).toList()
                        : List.of(),
                message.reactions(),
                message.deliveryStatus(),
                message.deliveredAt(),
                message.readAt(),
                message.expiresAt(),
                message.editedAt(),
                message.deletedAt()
        );
    }

    private MessageAttachmentResponse sanitizeAttachmentMetadata(MessageAttachmentResponse attachment) {
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

    private void validateRange(Instant fromInclusive, Instant toExclusive) {
        if (fromInclusive != null && toExclusive != null && !toExclusive.isAfter(fromInclusive)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "toExclusive must be later than fromInclusive");
        }
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
}
