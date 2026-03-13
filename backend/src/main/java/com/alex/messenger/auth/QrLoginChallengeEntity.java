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
@Table(name = "auth_qr_login_challenges")
public class QrLoginChallengeEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_by_session_id", nullable = false)
    private UUID createdBySessionId;

    @Column(name = "qr_token_hash", nullable = false, length = 128, unique = true)
    private String qrTokenHash;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "bound_device_name", length = 120)
    private String boundDeviceName;

    @Column(name = "bound_platform", length = 32)
    private String boundPlatform;

    @Column(name = "bound_app_version", length = 32)
    private String boundAppVersion;

    @Column(name = "bound_ip_address", length = 64)
    private String boundIpAddress;

    @Column(name = "bound_user_agent", length = 255)
    private String boundUserAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "bound_at")
    private Instant boundAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by_session_id")
    private UUID approvedBySessionId;

    @Column(name = "declined_at")
    private Instant declinedAt;

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
        if (status == null) {
            status = "NEW";
        }
    }
}
