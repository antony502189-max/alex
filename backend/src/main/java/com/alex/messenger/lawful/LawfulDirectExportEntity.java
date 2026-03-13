package com.alex.messenger.lawful;

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
@Table(name = "lawful_direct_exports")
public class LawfulDirectExportEntity {

    @Id
    private UUID id;

    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;

    @Column(name = "operator_id", nullable = false, length = 120)
    private String operatorId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "from_inclusive")
    private Instant fromInclusive;

    @Column(name = "to_exclusive")
    private Instant toExclusive;

    @Column(name = "include_attachments_metadata", nullable = false)
    private boolean includeAttachmentsMetadata;

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(name = "artifact_checksum", nullable = false, length = 128)
    private String artifactChecksum;

    @Column(name = "artifact_location", length = 512)
    private String artifactLocation;

    @Column(name = "exported_at", nullable = false)
    private Instant exportedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (exportedAt == null) {
            exportedAt = Instant.now();
        }
        if (createdAt == null) {
            createdAt = exportedAt;
        }
    }
}
