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
@Table(name = "channel_monetization_withdrawal_callbacks")
public class ChannelMonetizationWithdrawalCallbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "withdrawal_id", nullable = false)
    private UUID withdrawalId;

    @Column(name = "channel_chat_id", nullable = false)
    private UUID channelChatId;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(name = "callback_type", nullable = false, length = 32)
    private String callbackType;

    @Column(name = "provider_status", nullable = false, length = 32)
    private String providerStatus;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "payload_json")
    private String payloadJson;

    @Column(name = "applied", nullable = false)
    private boolean applied;

    @Column(name = "applied_withdrawal_status", length = 16)
    private String appliedWithdrawalStatus;

    @Column(name = "result_message", length = 255)
    private String resultMessage;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    void prePersist() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }
}
