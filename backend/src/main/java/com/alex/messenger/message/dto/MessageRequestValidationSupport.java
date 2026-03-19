package com.alex.messenger.message.dto;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class MessageRequestValidationSupport {

    private MessageRequestValidationSupport() {
    }

    static boolean hasTarget(UUID chatId, UUID recipientUserId) {
        return chatId != null || recipientUserId != null;
    }

    static boolean hasPayload(
            String text,
            String caption,
            MessageLocationPayload location,
            MessageLiveLocationPayload liveLocation,
            MessageContactCardPayload contactCard,
            List<UUID> attachmentIds,
            UUID stickerId
    ) {
        return hasText(text)
                || hasText(caption)
                || location != null
                || liveLocation != null
                || contactCard != null
                || hasAttachments(attachmentIds)
                || stickerId != null;
    }

    static boolean hasAtMostOneStructuredPayload(
            MessageLocationPayload location,
            MessageLiveLocationPayload liveLocation,
            MessageContactCardPayload contactCard
    ) {
        int structuredPayloadCount = 0;
        structuredPayloadCount += location != null ? 1 : 0;
        structuredPayloadCount += liveLocation != null ? 1 : 0;
        structuredPayloadCount += contactCard != null ? 1 : 0;
        return structuredPayloadCount <= 1;
    }

    static boolean isPublicMessageType(String messageType) {
        return !"SERVICE_MESSAGE".equals(normalizeMessageType(messageType));
    }

    static boolean hasValidStructuredPayloadUsage(
            String messageType,
            MessageLocationPayload location,
            MessageLiveLocationPayload liveLocation,
            MessageContactCardPayload contactCard,
            List<UUID> attachmentIds,
            UUID stickerId
    ) {
        boolean hasStructuredPayload = location != null || liveLocation != null || contactCard != null;
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
        if ("LIVE_LOCATION".equals(normalizedMessageType)) {
            return liveLocation != null;
        }
        if ("CONTACT_CARD".equals(normalizedMessageType)) {
            return contactCard != null;
        }
        if (location != null || liveLocation != null || contactCard != null) {
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
