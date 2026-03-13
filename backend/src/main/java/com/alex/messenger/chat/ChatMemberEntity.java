package com.alex.messenger.chat;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "chat_members")
public class ChatMemberEntity {

    @EmbeddedId
    private ChatMemberId id;

    @Column(name = "role", nullable = false, length = 16)
    private String role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "last_read_message_id")
    private UUID lastReadMessageId;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "last_sent_message_at")
    private Instant lastSentMessageAt;

    @Column(name = "unread_count", nullable = false)
    private Integer unreadCount;

    @Column(name = "mention_count", nullable = false)
    private Integer mentionCount;

    @Column(name = "reply_count", nullable = false)
    private Integer replyCount;

    @Column(name = "archived", nullable = false)
    private Boolean archived;

    @Column(name = "muted_until")
    private Instant mutedUntil;

    @Column(name = "can_send_messages", nullable = false)
    private Boolean canSendMessages;

    @Column(name = "can_manage_members", nullable = false)
    private Boolean canManageMembers;

    @Column(name = "can_manage_invite_links", nullable = false)
    private Boolean canManageInviteLinks;

    @Column(name = "can_manage_messages", nullable = false)
    private Boolean canManageMessages;

    @Column(name = "can_pin_messages", nullable = false)
    private Boolean canPinMessages;

    @Column(name = "can_approve_join_requests", nullable = false)
    private Boolean canApproveJoinRequests;

    @Column(name = "can_post_messages", nullable = false)
    private Boolean canPostMessages;

    @Column(name = "anonymous_admin", nullable = false)
    private Boolean anonymousAdmin;

    @Column(name = "restricted_until")
    private Instant restrictedUntil;

    @Column(name = "restriction_reason", length = 255)
    private String restrictionReason;

    @Column(name = "restricted_by_user_id")
    private UUID restrictedByUserId;

    @PrePersist
    void prePersist() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
        if (role == null) {
            role = "MEMBER";
        }
        if (unreadCount == null) {
            unreadCount = 0;
        }
        if (mentionCount == null) {
            mentionCount = 0;
        }
        if (replyCount == null) {
            replyCount = 0;
        }
        if (archived == null) {
            archived = false;
        }
        if (canSendMessages == null) {
            canSendMessages = true;
        }
        if (canManageMembers == null) {
            canManageMembers = false;
        }
        if (canManageInviteLinks == null) {
            canManageInviteLinks = false;
        }
        if (canManageMessages == null) {
            canManageMessages = false;
        }
        if (canPinMessages == null) {
            canPinMessages = false;
        }
        if (canApproveJoinRequests == null) {
            canApproveJoinRequests = false;
        }
        if (canPostMessages == null) {
            canPostMessages = true;
        }
        if (anonymousAdmin == null) {
            anonymousAdmin = false;
        }
    }
}
