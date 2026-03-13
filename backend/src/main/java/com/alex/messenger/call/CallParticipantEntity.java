package com.alex.messenger.call;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "call_participants")
public class CallParticipantEntity {

    @EmbeddedId
    private CallParticipantId id;

    @Column(name = "state", nullable = false, length = 16)
    private String state;

    @Column(name = "invited_at", nullable = false, updatable = false)
    private Instant invitedAt;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "audio_publishing_allowed", nullable = false)
    private Boolean audioPublishingAllowed = true;

    @Column(name = "video_publishing_allowed", nullable = false)
    private Boolean videoPublishingAllowed = true;

    @Column(name = "screen_share_allowed", nullable = false)
    private Boolean screenShareAllowed = true;

    @Column(name = "screen_sharing", nullable = false)
    private Boolean screenSharing = false;

    @Column(name = "hand_raised", nullable = false)
    private Boolean handRaised = false;

    @Column(name = "audio_muted", nullable = false)
    private Boolean audioMuted = false;

    @Column(name = "muted_by_moderator", nullable = false)
    private Boolean mutedByModerator = false;

    @Column(name = "muted_by_user_id")
    private java.util.UUID mutedByUserId;

    @Column(name = "muted_at")
    private Instant mutedAt;

    @Column(name = "moderated_by_user_id")
    private java.util.UUID moderatedByUserId;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @PrePersist
    void prePersist() {
        if (invitedAt == null) {
            invitedAt = Instant.now();
        }
        if (audioPublishingAllowed == null) {
            audioPublishingAllowed = true;
        }
        if (videoPublishingAllowed == null) {
            videoPublishingAllowed = true;
        }
        if (screenShareAllowed == null) {
            screenShareAllowed = true;
        }
        if (screenSharing == null) {
            screenSharing = false;
        }
        if (handRaised == null) {
            handRaised = false;
        }
        if (audioMuted == null) {
            audioMuted = false;
        }
        if (mutedByModerator == null) {
            mutedByModerator = false;
        }
    }
}
