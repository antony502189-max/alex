package com.alex.messenger.story;

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
@Table(name = "story_views")
public class StoryViewEntity {

    @EmbeddedId
    private StoryViewId id;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

    @PrePersist
    void prePersist() {
        if (viewedAt == null) {
            viewedAt = Instant.now();
        }
    }
}
