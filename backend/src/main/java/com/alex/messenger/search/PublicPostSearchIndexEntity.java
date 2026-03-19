package com.alex.messenger.search;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "public_post_search_index")
public class PublicPostSearchIndexEntity {

    @Id
    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "topic_id")
    private UUID topicId;

    @Column(name = "discussion_chat_id")
    private UUID discussionChatId;

    @Column(name = "discussion_root_message_id")
    private UUID discussionRootMessageId;

    @Column(name = "excerpt", length = 600)
    private String excerpt;

    @Column(name = "search_corpus", nullable = false)
    private String searchCorpus;

    @Column(name = "message_type", nullable = false, length = 32)
    private String messageType;

    @Column(name = "attachment_count", nullable = false)
    private Integer attachmentCount = 0;

    @Column(name = "has_media", nullable = false)
    private Boolean hasMedia = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (attachmentCount == null) {
            attachmentCount = 0;
        }
        if (hasMedia == null) {
            hasMedia = false;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
