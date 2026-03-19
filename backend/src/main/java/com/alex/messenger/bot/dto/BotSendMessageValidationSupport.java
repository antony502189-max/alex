package com.alex.messenger.bot.dto;

import com.alex.messenger.message.dto.MessageContactCardPayload;
import com.alex.messenger.message.dto.MessageLocationPayload;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class BotSendMessageValidationSupport {

    private BotSendMessageValidationSupport() {
    }

    static boolean hasPayload(
            String text,
            String caption,
            MessageLocationPayload location,
            MessageContactCardPayload contactCard,
            List<UUID> attachmentIds,
            UUID stickerId
    ) {
        return hasText(text)
                || hasText(caption)
                || location != null
                || contactCard != null
                || hasAttachments(attachmentIds)
                || stickerId != null;
    }

    static boolean hasAtMostOneStructuredPayload(
            MessageLocationPayload location,
            MessageContactCardPayload contactCard
    ) {
        int structuredPayloadCount = 0;
        structuredPayloadCount += location != null ? 1 : 0;
        structuredPayloadCount += contactCard != null ? 1 : 0;
        return structuredPayloadCount <= 1;
    }

    static boolean isPublicMessageType(String messageType) {
        return !"SERVICE_MESSAGE".equals(normalizeMessageType(messageType));
    }

    static boolean hasValidStructuredPayloadUsage(
            String messageType,
            MessageLocationPayload location,
            MessageContactCardPayload contactCard,
            List<UUID> attachmentIds,
            UUID stickerId
    ) {
        boolean hasStructuredPayload = location != null || contactCard != null;
        if (hasStructuredPayload && (hasAttachments(attachmentIds) || stickerId != null)) {
            return false;
        }

        String normalizedMessageType = normalizeMessageType(messageType);
        if (normalizedMessageType.isBlank()) {
            return true;
        }
        if ("LOCATION".equals(normalizedMessageType)) {
            return location != null;
        }
        if ("CONTACT_CARD".equals(normalizedMessageType)) {
            return contactCard != null;
        }
        if (location != null || contactCard != null) {
            return false;
        }
        return true;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private static boolean hasAttachments(List<UUID> attachmentIds) {
        return attachmentIds != null && !attachmentIds.isEmpty();
    }

    private static String normalizeMessageType(String messageType) {
        return messageType != null ? messageType.trim().toUpperCase(Locale.ROOT) : "";
    }
}
