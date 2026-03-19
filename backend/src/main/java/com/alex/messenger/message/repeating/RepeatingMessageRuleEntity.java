package com.alex.messenger.message.repeating;

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
@Table(name = "repeating_message_rules")
public class RepeatingMessageRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "client_rule_id")
    private UUID clientRuleId;

    @Column(name = "topic_id")
    private UUID topicId;

    @Column(name = "thread_root_message_id")
    private UUID threadRootMessageId;

    @Column(name = "discussion_chat_id")
    private UUID discussionChatId;

    @Column(name = "discussion_root_message_id")
    private UUID discussionRootMessageId;

    @Column(name = "ciphertext", nullable = false, columnDefinition = "text")
    private String ciphertext;

    @Column(name = "nonce", nullable = false, columnDefinition = "text")
    private String nonce;

    @Column(name = "key_version", nullable = false)
    private Integer keyVersion;

    @Column(name = "reply_to_message_id")
    private UUID replyToMessageId;

    @Column(name = "sticker_id")
    private UUID stickerId;

    @Column(name = "attachment_ids", nullable = false, columnDefinition = "text")
    private String attachmentIds;

    @Column(name = "interval_minutes", nullable = false)
    private Integer intervalMinutes;

    @Column(name = "max_occurrences")
    private Integer maxOccurrences;

    @Column(name = "emitted_occurrences", nullable = false)
    private Integer emittedOccurrences;

    @Column(name = "last_scheduled_at")
    private Instant lastScheduledAt;

    @Column(name = "next_scheduled_at")
    private Instant nextScheduledAt;

    @Column(name = "last_scheduled_message_id")
    private UUID lastScheduledMessageId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

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
        if (attachmentIds == null) {
            attachmentIds = "";
        }
        if (emittedOccurrences == null) {
            emittedOccurrences = 0;
        }
        if (status == null) {
            status = "ACTIVE";
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
