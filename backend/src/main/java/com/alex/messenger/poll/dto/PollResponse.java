package com.alex.messenger.poll.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PollResponse(
        UUID pollId,
        String question,
        boolean multipleChoice,
        boolean quiz,
        UUID correctOptionId,
        String explanation,
        boolean anonymousVotes,
        Instant closeAt,
        boolean closed,
        int totalVoters,
        List<PollOptionResponse> options
) {
}
