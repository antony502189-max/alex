package com.alex.messenger.secret;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecretChatExpirationDispatcher {

    private final SecretChatService secretChatService;

    @Scheduled(fixedDelayString = "${alex.secret-chats.expiration-interval-ms:10000}")
    void deleteExpiredMessages() {
        secretChatService.deleteExpiredMessages(Instant.now(), 200);
    }
}
