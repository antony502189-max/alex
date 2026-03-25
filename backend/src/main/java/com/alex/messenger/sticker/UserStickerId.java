package com.alex.messenger.sticker;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserStickerId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "sticker_id", nullable = false)
    private UUID stickerId;

    public UserStickerId() {
    }

    public UserStickerId(UUID userId, UUID stickerId) {
        this.userId = userId;
        this.stickerId = stickerId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getStickerId() {
        return stickerId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserStickerId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(stickerId, that.stickerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, stickerId);
    }
}
