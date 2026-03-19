package com.alex.messenger.story;

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
@Table(name = "story_live_sessions")
public class StoryLiveSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "story_id", nullable = false)
    private UUID storyId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "donations_enabled", nullable = false)
    private boolean donationsEnabled;

    @Column(name = "donation_provider", length = 32)
    private String donationProvider;

    @Column(name = "donation_currency", length = 8)
    private String donationCurrency;

    @Column(name = "donation_event_hook_url", length = 500)
    private String donationEventHookUrl;

    @Column(name = "donation_events_count", nullable = false)
    private long donationEventsCount;

    @Column(name = "donations_total_minor", nullable = false)
    private long donationsTotalMinor;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (status == null) {
            status = "ACTIVE";
        }
        if (startedAt == null) {
            startedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
