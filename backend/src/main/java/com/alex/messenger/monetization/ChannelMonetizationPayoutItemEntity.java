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
@Table(name = "channel_monetization_payout_items")
public class ChannelMonetizationPayoutItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payout_id", nullable = false)
    private UUID payoutId;

    @Column(name = "sponsored_message_id", nullable = false)
    private UUID sponsoredMessageId;

    @Column(name = "settled_units", nullable = false)
    private Long settledUnits = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (settledUnits == null) {
            settledUnits = 0L;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
