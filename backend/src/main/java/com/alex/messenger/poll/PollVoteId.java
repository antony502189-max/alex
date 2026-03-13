package com.alex.messenger.poll;

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
public class PollVoteId implements Serializable {

    @Column(name = "poll_id", nullable = false)
    private UUID pollId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "option_id", nullable = false)
    private UUID optionId;

    public PollVoteId(UUID pollId, UUID userId, UUID optionId) {
        this.pollId = pollId;
        this.userId = userId;
        this.optionId = optionId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PollVoteId that)) {
            return false;
        }
        return Objects.equals(pollId, that.pollId)
                && Objects.equals(userId, that.userId)
                && Objects.equals(optionId, that.optionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pollId, userId, optionId);
    }
}
