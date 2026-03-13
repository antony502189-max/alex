package com.alex.messenger.message;

import com.alex.messenger.attachment.AttachmentEntity;
import com.alex.messenger.attachment.AttachmentRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageSearchCorpusService {

    private final AttachmentRepository attachmentRepository;
    private final MessageTranslationCacheRepository messageTranslationCacheRepository;
    private final MessageContentCodec messageContentCodec;

    @Transactional(readOnly = true)
    public String buildSearchCorpus(UUID messageId, MessageTextContent content, List<UUID> attachmentIds) {
        StringBuilder builder = new StringBuilder();
        append(builder, messageContentCodec.buildSearchText(content));
        append(builder, buildAttachmentMetadata(attachmentIds));
        append(builder, buildTranslationMetadata(messageId, content));
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> buildSearchCorpora(
            Map<UUID, MessageTextContent> contentByMessageId,
            Map<UUID, List<UUID>> attachmentIdsByMessageId
    ) {
        Map<UUID, String> attachmentMetadataByMessageId = attachmentMetadataByMessageId(attachmentIdsByMessageId);
        Map<UUID, String> translationMetadataByMessageId =
                translationMetadataByMessageId(contentByMessageId.keySet(), contentByMessageId);
        Map<UUID, String> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, MessageTextContent> entry : contentByMessageId.entrySet()) {
            UUID messageId = entry.getKey();
            StringBuilder builder = new StringBuilder();
            append(builder, messageContentCodec.buildSearchText(entry.getValue()));
            append(builder, attachmentMetadataByMessageId.get(messageId));
            append(builder, translationMetadataByMessageId.get(messageId));
            result.put(messageId, builder.toString().toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private Map<UUID, String> attachmentMetadataByMessageId(Map<UUID, List<UUID>> attachmentIdsByMessageId) {
        Map<UUID, String> result = new LinkedHashMap<>();
        List<UUID> allAttachmentIds = attachmentIdsByMessageId.values().stream().flatMap(Collection::stream).distinct().toList();
        Map<UUID, AttachmentEntity> attachmentsById = attachmentRepository.findAllByIdIn(allAttachmentIds).stream()
                .collect(java.util.stream.Collectors.toMap(AttachmentEntity::getId, attachment -> attachment));
        for (Map.Entry<UUID, List<UUID>> entry : attachmentIdsByMessageId.entrySet()) {
            StringBuilder builder = new StringBuilder();
            for (UUID attachmentId : entry.getValue()) {
                AttachmentEntity attachment = attachmentsById.get(attachmentId);
                if (attachment == null) {
                    continue;
                }
                append(builder, attachment.getOriginalFileName());
                append(builder, attachment.getKind());
                append(builder, attachment.getContentType());
            }
            result.put(entry.getKey(), builder.toString());
        }
        return result;
    }

    private Map<UUID, String> translationMetadataByMessageId(
            Collection<UUID> messageIds,
            Map<UUID, MessageTextContent> contentByMessageId
    ) {
        Map<UUID, StringBuilder> builders = new LinkedHashMap<>();
        for (MessageTranslationCacheEntity translation : messageTranslationCacheRepository.findAllByMessageIdIn(messageIds)) {
            MessageTextContent currentContent = contentByMessageId.get(translation.getMessageId());
            if (!matchesCurrentContent(translation, currentContent)) {
                continue;
            }
            StringBuilder builder = builders.computeIfAbsent(translation.getMessageId(), ignored -> new StringBuilder());
            append(builder, translation.getTranslatedText());
            append(builder, translation.getTranslatedCaption());
            append(builder, translation.getTargetLanguage());
        }
        return builders.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().toString()
        ));
    }

    private String buildAttachmentMetadata(List<UUID> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (AttachmentEntity attachment : attachmentRepository.findAllByIdIn(attachmentIds)) {
            append(builder, attachment.getOriginalFileName());
            append(builder, attachment.getKind());
            append(builder, attachment.getContentType());
        }
        return builder.toString();
    }

    private String buildTranslationMetadata(UUID messageId, MessageTextContent content) {
        StringBuilder builder = new StringBuilder();
        for (MessageTranslationCacheEntity translation : messageTranslationCacheRepository.findAllByMessageId(messageId)) {
            if (!matchesCurrentContent(translation, content)) {
                continue;
            }
            append(builder, translation.getTranslatedText());
            append(builder, translation.getTranslatedCaption());
            append(builder, translation.getTargetLanguage());
        }
        return builder.toString();
    }

    private boolean matchesCurrentContent(MessageTranslationCacheEntity translation, MessageTextContent content) {
        if (content == null) {
            return false;
        }
        return normalizedEquals(translation.getOriginalText(), content.text())
                && normalizedEquals(translation.getOriginalCaption(), content.caption());
    }

    private boolean normalizedEquals(String left, String right) {
        return java.util.Objects.equals(normalize(left), normalize(right));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private void append(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(value.trim());
    }
}
