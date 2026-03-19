package com.alex.messenger.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "passkey_challenges")
public class PasskeyChallengeEntity {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "flow_type", nullable = false, length = 16)
    private String flowType;

    @Column(name = "challenge_hash", nullable = false, length = 128)
    private String challengeHash;

    @Column(name = "requested_phone_number", length = 32)
    private String requestedPhoneNumber;

    @Column(name = "device_name", length = 120)
    private String deviceName;

    @Column(name = "platform", length = 32)
    private String platform;

    @Column(name = "app_version", length = 32)
    private String appVersion;

    @Column(name = "requested_by_ip", length = 64)
    private String requestedByIp;

    @Column(name = "requested_by_user_agent", length = 255)
    private String requestedByUserAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
