package com.alex.messenger.lawful;

import com.alex.messenger.message.dto.ChatMessageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LawfulInterceptionService {

    List<ChatMessageResponse> exportDecryptedMessages(UUID userId, Instant fromInclusive, Instant toExclusive);
}
