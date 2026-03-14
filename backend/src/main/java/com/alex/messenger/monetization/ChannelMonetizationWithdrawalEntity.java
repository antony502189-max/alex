package com.alex.messenger.monetization;

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
@Table(name = "channel_monetization_withdrawals")
public class ChannelMonetizationWithdrawalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "channel_chat_id", nullable = false)
    private UUID channelChatId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    @Column(name = "amount_units", nullable = false)
    private Long amountUnits;

    @Column(name = "currency_code", nullable = false, length = 16)
    private String currencyCode = "XTR";

    @Column(name = "destination_type", nullable = false, length = 32)
    private String destinationType;

    @Column(name = "destination_label", nullable = false, length = 255)
    private String destinationLabel;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(name = "provider_status", length = 32)
    private String providerStatus;

    @Column(name = "provider_updated_at")
    private Instant providerUpdatedAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "processing_at")
    private Instant processingAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @PrePersist
    void prePersist() {
        if (amountUnits == null) {
            amountUnits = 0L;
        }
        if (currencyCode == null) {
            currencyCode = "XTR";
        }
        if (status == null) {
            status = "PENDING";
        }
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }
}
