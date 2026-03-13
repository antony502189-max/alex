package com.alex.messenger.poll.dto;

import java.util.UUID;

public record PollOptionResponse(
        UUID optionId,
        String text,
        int voteCount,
        boolean selectedByMe
) {
}
