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
@Table(name = "sponsored_message_events")
public class SponsoredMessageEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sponsored_message_id", nullable = false)
    private UUID sponsoredMessageId;

    @Column(name = "viewer_user_id", nullable = false)
    private UUID viewerUserId;

    @Column(name = "event_type", nullable = false, length = 16)
    private String eventType;

    @Column(name = "cost_units", nullable = false)
    private Long costUnits = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (costUnits == null) {
            costUnits = 0L;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
