package com.alex.messenger.business;

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
@Table(name = "business_profiles")
public class BusinessProfileEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "greeting_enabled", nullable = false)
    private Boolean greetingEnabled = false;

    @Column(name = "greeting_message", length = 1000)
    private String greetingMessage;

    @Column(name = "away_enabled", nullable = false)
    private Boolean awayEnabled = false;

    @Column(name = "away_message", length = 1000)
    private String awayMessage;

    @Column(name = "business_hours_json", nullable = false, columnDefinition = "text")
    private String businessHoursJson = "[]";

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = "UTC";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (greetingEnabled == null) {
            greetingEnabled = false;
        }
        if (awayEnabled == null) {
            awayEnabled = false;
        }
        if (businessHoursJson == null) {
            businessHoursJson = "[]";
        }
        if (timeZone == null || timeZone.isBlank()) {
            timeZone = "UTC";
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
