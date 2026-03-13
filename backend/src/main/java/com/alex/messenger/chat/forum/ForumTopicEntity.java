package com.alex.messenger.chat.forum;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
@Table(name = "forum_topics")
public class ForumTopicEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "icon_emoji", length = 32)
    private String iconEmoji;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "general_topic", nullable = false)
    private Boolean generalTopic = false;

    @Column(name = "closed", nullable = false)
    private Boolean closed = false;

    @Column(name = "hidden", nullable = false)
    private Boolean hidden = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (generalTopic == null) {
            generalTopic = false;
        }
        if (closed == null) {
            closed = false;
        }
        if (hidden == null) {
            hidden = false;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
