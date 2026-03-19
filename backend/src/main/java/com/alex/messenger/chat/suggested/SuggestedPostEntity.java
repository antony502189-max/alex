package com.alex.messenger.chat.suggested;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "suggested_posts")
public class SuggestedPostEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "submitted_by_user_id", nullable = false)
    private UUID submittedByUserId;

    @Column(name = "ciphertext", nullable = false, columnDefinition = "text")
    private String ciphertext;

    @Column(name = "nonce", nullable = false, columnDefinition = "text")
    private String nonce;

    @Column(name = "key_version", nullable = false)
    private Integer keyVersion;

    @Column(name = "sticker_id")
    private UUID stickerId;

    @Column(name = "attachment_ids", nullable = false, columnDefinition = "text")
    private String attachmentIds;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "SUBMITTED";

    @Column(name = "payment_amount_units")
    private Long paymentAmountUnits;

    @Column(name = "payment_currency_code", length = 16)
    private String paymentCurrencyCode;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "published_message_id")
    private UUID publishedMessageId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "declined_at")
    private Instant declinedAt;

    @Column(name = "decline_reason", length = 500)
    private String declineReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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
        if (status == null) {
            status = "SUBMITTED";
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
