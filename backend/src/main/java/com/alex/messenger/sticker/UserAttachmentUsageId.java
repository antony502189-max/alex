package com.alex.messenger.sticker;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserAttachmentUsageId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "attachment_id", nullable = false)
    private UUID attachmentId;

    public UserAttachmentUsageId() {
    }

    public UserAttachmentUsageId(UUID userId, UUID attachmentId) {
        this.userId = userId;
        this.attachmentId = attachmentId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getAttachmentId() {
        return attachmentId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserAttachmentUsageId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(attachmentId, that.attachmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, attachmentId);
    }
}
