package com.alex.messenger.sync;

import java.util.Map;
import java.util.Set;

final class SyncEventTypeCatalog {

    private static final Set<String> CANONICAL_EVENT_TYPES = Set.of(
            "CHAT_UPSERT",
            "CHAT_REMOVED",
            "MESSAGE_UPSERT",
            "MESSAGE_DELETED",
            "CHAT_READ",
            "CHAT_HISTORY_CLEARED",
            "MEMBER_STATE_CHANGED"
    );

    private static final Map<String, String> CANONICAL_BY_LEGACY_TYPE = Map.ofEntries(
            Map.entry("CHAT_CREATED", "CHAT_UPSERT"),
            Map.entry("CHAT_UPDATED", "CHAT_UPSERT"),
            Map.entry("CHAT_MARKED_UNREAD", "CHAT_UPSERT"),
            Map.entry("CHAT_PIN_UPDATED", "CHAT_UPSERT"),
            Map.entry("CHAT_MEMBER_ADDED", "MEMBER_STATE_CHANGED"),
            Map.entry("CHAT_MEMBER_UPDATED", "MEMBER_STATE_CHANGED"),
            Map.entry("CHAT_MEMBER_REMOVED", "MEMBER_STATE_CHANGED"),
            Map.entry("CHAT_MEMBER_BANNED", "MEMBER_STATE_CHANGED"),
            Map.entry("CHAT_MEMBER_LEFT", "MEMBER_STATE_CHANGED"),
            Map.entry("CHAT_JOIN_REQUEST_APPROVED", "MEMBER_STATE_CHANGED"),
            Map.entry("CHAT_OWNERSHIP_TRANSFERRED", "MEMBER_STATE_CHANGED")
    );

    private SyncEventTypeCatalog() {
    }

    static boolean isCanonicalEventType(String eventType) {
        return eventType != null && CANONICAL_EVENT_TYPES.contains(eventType);
    }

    static boolean isTransitionLegacyEventType(String eventType) {
        return eventType != null && CANONICAL_BY_LEGACY_TYPE.containsKey(eventType);
    }

    static String canonicalEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return eventType;
        }
        return CANONICAL_BY_LEGACY_TYPE.getOrDefault(eventType, eventType);
    }
}
