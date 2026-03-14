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
@Table(name = "channel_monetization_export_artifacts")
public class ChannelMonetizationExportArtifactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "channel_chat_id", nullable = false)
    private UUID channelChatId;

    @Column(name = "generated_by_user_id")
    private UUID generatedByUserId;

    @Column(name = "artifact_type", nullable = false, length = 32)
    private String artifactType;

    @Column(name = "format", nullable = false, length = 16)
    private String format;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "row_count", nullable = false)
    private Integer rowCount = 0;

    @Column(name = "total_units", nullable = false)
    private Long totalUnits = 0L;

    @Column(name = "checksum", nullable = false, length = 128)
    private String checksum;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (rowCount == null) {
            rowCount = 0;
        }
        if (totalUnits == null) {
            totalUnits = 0L;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
