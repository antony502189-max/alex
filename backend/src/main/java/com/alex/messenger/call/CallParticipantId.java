package com.alex.messenger.call;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CallParticipantId implements Serializable {

    @Column(name = "call_id", nullable = false)
    private UUID callId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    public CallParticipantId() {
    }

    public CallParticipantId(UUID callId, UUID userId) {
        this.callId = callId;
        this.userId = userId;
    }

    public UUID getCallId() {
        return callId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CallParticipantId that)) {
            return false;
        }
        return Objects.equals(callId, that.callId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(callId, userId);
    }
}
