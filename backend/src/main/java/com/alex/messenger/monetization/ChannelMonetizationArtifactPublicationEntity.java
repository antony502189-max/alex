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
@Table(name = "channel_monetization_artifact_publications")
public class ChannelMonetizationArtifactPublicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "artifact_id", nullable = false)
    private UUID artifactId;

    @Column(name = "channel_chat_id", nullable = false)
    private UUID channelChatId;

    @Column(name = "target_chat_id", nullable = false)
    private UUID targetChatId;

    @Column(name = "published_by_user_id")
    private UUID publishedByUserId;

    @Column(name = "delivery_mode", nullable = false, length = 32)
    private String deliveryMode;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "published_message_id")
    private UUID publishedMessageId;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    @PrePersist
    void prePersist() {
        if (publishedAt == null) {
            publishedAt = Instant.now();
        }
    }
}
