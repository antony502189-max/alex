package com.alex.messenger.message.scheduled;

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
@Table(name = "scheduled_messages")
public class ScheduledMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "client_message_id")
    private UUID clientMessageId;

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
    private String attachmentIds = "";

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "delivered_message_id")
    private UUID deliveredMessageId;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @Column(name = "repeating_rule_id")
    private UUID repeatingRuleId;

    @Column(name = "repeating_occurrence", nullable = false)
    private Integer repeatingOccurrence;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = "PENDING";
        }
        if (attachmentIds == null) {
            attachmentIds = "";
        }
        if (repeatingOccurrence == null) {
            repeatingOccurrence = 0;
        }
    }
}
