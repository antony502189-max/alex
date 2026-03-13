package com.alex.messenger.secret;

import com.alex.messenger.secret.dto.SecretChatInboxEventResponse;
import com.alex.messenger.secret.dto.SecretChatMessageResponse;
import com.alex.messenger.secret.dto.SecretChatReadEventResponse;
import com.alex.messenger.secret.dto.SecretChatScreenshotEventResponse;
import com.alex.messenger.secret.dto.SecretChatSummaryResponse;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecretChatRealtimeService {

    private final SimpMessagingTemplate simpMessagingTemplate;

    public void publishChatUpdate(Collection<UUID> userIds, String eventType, SecretChatSummaryResponse chat) {
        for (UUID userId : new LinkedHashSet<>(userIds)) {
            simpMessagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/secret-chats",
                    new SecretChatInboxEventResponse(eventType, chat, null, null, null)
            );
        }
    }

    public void publishMessage(Collection<UUID> userIds, SecretChatMessageResponse message) {
        for (UUID userId : new LinkedHashSet<>(userIds)) {
            simpMessagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/secret-chats",
                    new SecretChatInboxEventResponse("MESSAGE_CREATED", null, message, null, null)
            );
        }
    }

    public void publishReadEvent(Collection<UUID> userIds, SecretChatReadEventResponse readEvent) {
        for (UUID userId : new LinkedHashSet<>(userIds)) {
            simpMessagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/secret-chats",
                    new SecretChatInboxEventResponse("MESSAGE_READ", null, null, readEvent, null)
            );
        }
    }

    public void publishScreenshotEvent(Collection<UUID> userIds, SecretChatScreenshotEventResponse screenshotEvent) {
        for (UUID userId : new LinkedHashSet<>(userIds)) {
            simpMessagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/secret-chats",
                    new SecretChatInboxEventResponse("SCREENSHOT_CAPTURED", null, null, null, screenshotEvent)
            );
        }
    }
}
