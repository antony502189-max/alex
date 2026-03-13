package com.alex.messenger.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "chats")
public class ChatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chat_type", nullable = false, length = 16)
    private String chatType = "DIRECT";

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "public_username", length = 64)
    private String publicUsername;

    @Column(name = "about", length = 500)
    private String about;

    @Column(name = "auto_delete_seconds")
    private Integer autoDeleteSeconds;

    @Column(name = "slow_mode_seconds")
    private Integer slowModeSeconds;

    @Column(name = "forum_enabled", nullable = false)
    private Boolean forumEnabled = false;

    @Column(name = "join_requires_approval", nullable = false)
    private Boolean joinRequiresApproval = false;

    @Column(name = "comments_enabled", nullable = false)
    private Boolean commentsEnabled = true;

    @Column(name = "reactions_enabled", nullable = false)
    private Boolean reactionsEnabled = true;

    @Column(name = "cross_posting_enabled", nullable = false)
    private Boolean crossPostingEnabled = true;

    @Column(name = "linked_discussion_chat_id")
    private UUID linkedDiscussionChatId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "pinned_message_id")
    private UUID pinnedMessageId;

    @Column(name = "participant_low_id")
    private UUID participantLowId;

    @Column(name = "participant_high_id")
    private UUID participantHighId;

    @Column(name = "photo_storage_provider", length = 32)
    private String photoStorageProvider;

    @Column(name = "photo_bucket_name", length = 255)
    private String photoBucketName;

    @Column(name = "photo_object_key", length = 512)
    private String photoObjectKey;

    @Column(name = "photo_content_type", length = 255)
    private String photoContentType;

    @Column(name = "photo_updated_at")
    private Instant photoUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (forumEnabled == null) {
            forumEnabled = false;
        }
        if (joinRequiresApproval == null) {
            joinRequiresApproval = false;
        }
        if (commentsEnabled == null) {
            commentsEnabled = true;
        }
        if (reactionsEnabled == null) {
            reactionsEnabled = true;
        }
        if (crossPostingEnabled == null) {
            crossPostingEnabled = true;
        }
    }
}
