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
@Table(name = "auth_login_code_challenges")
public class LoginCodeChallengeEntity {

    @Id
    private UUID id;

    @Column(name = "phone_number", nullable = false, length = 32)
    private String phoneNumber;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(name = "device_name", length = 120)
    private String deviceName;

    @Column(name = "platform", length = 32)
    private String platform;

    @Column(name = "app_version", length = 32)
    private String appVersion;

    @Column(name = "code_hash", nullable = false, length = 128)
    private String codeHash;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @Column(name = "requested_by_ip", length = 64)
    private String requestedByIp;

    @Column(name = "requested_by_user_agent", length = 255)
    private String requestedByUserAgent;

    @Column(name = "request_fingerprint_hash", length = 128)
    private String requestFingerprintHash;

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
        if (attemptCount == null) {
            attemptCount = 0;
        }
    }
}
