package com.alex.messenger.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatSummaryResponse(
        UUID chatId,
        String chatType,
        String title,
        String photoUrl,
        Instant photoAccessExpiresAt,
        UUID peerUserId,
        String peerPhoneNumber,
        String peerDisplayName,
        boolean peerOnline,
        Instant peerLastSeenAt,
        boolean peerIsBot,
        boolean peerBotSupportsInline,
        String peerBotWebAppUrl,
        String publicUsername,
        String about,
        Integer autoDeleteSeconds,
        Integer slowModeSeconds,
        boolean forumEnabled,
        long topicCount,
        UUID linkedDiscussionChatId,
        String linkedDiscussionChatTitle,
        Instant lastMessageAt,
        long memberCount,
        Instant lastReadAt,
        int unreadCount,
        int mentionCount,
        int replyCount,
        boolean archived,
        String draftText,
        Instant draftUpdatedAt,
        Instant mutedUntil,
        java.util.UUID pinnedMessageId,
        boolean joinRequiresApproval,
        boolean commentsEnabled,
        boolean reactionsEnabled,
        boolean crossPostingEnabled
) {
}
