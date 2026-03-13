package com.alex.messenger.story;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Embeddable
public class StoryViewId implements Serializable {

    @Column(name = "story_id", nullable = false)
    private UUID storyId;

    @Column(name = "viewer_user_id", nullable = false)
    private UUID viewerUserId;

    public StoryViewId(UUID storyId, UUID viewerUserId) {
        this.storyId = storyId;
        this.viewerUserId = viewerUserId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof StoryViewId that)) {
            return false;
        }
        return Objects.equals(storyId, that.storyId)
                && Objects.equals(viewerUserId, that.viewerUserId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storyId, viewerUserId);
    }
}
