package com.alex.messenger.story;

import com.alex.messenger.auth.session.PushSessionTarget;
import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.notification.PushNotificationCommand;
import com.alex.messenger.notification.PushNotificationService;
import com.alex.messenger.story.dto.StoryInteractionEventResponse;
import com.alex.messenger.story.dto.StoryInteractionResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StoryInteractionNotificationService {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final PushNotificationService pushNotificationService;
    private final UserSessionService userSessionService;

    public void publish(UUID recipientUserId, StoryInteractionEventResponse event) {
        simpMessagingTemplate.convertAndSendToUser(
                recipientUserId.toString(),
                "/queue/story-events",
                event
        );
        maybeSendPush(recipientUserId, event);
    }

    private void maybeSendPush(UUID recipientUserId, StoryInteractionEventResponse event) {
        StoryInteractionResponse interaction = event.interaction();
        if (interaction == null || interaction.actorUserId() == null || recipientUserId.equals(interaction.actorUserId())) {
            return;
        }
        if (!"INTERACTION_UPSERT".equals(event.eventType())) {
            return;
        }
        if (!List.of("REACTION", "REPLY", "MENTION").contains(interaction.type())) {
            return;
        }
        if (userSessionService.isUserOnline(recipientUserId)) {
            return;
        }

        String title = "Story activity";
        String body = buildPushBody(interaction);
        List<PushNotificationCommand> commands = new ArrayList<>();
        for (PushSessionTarget target : userSessionService.getPushTargets(recipientUserId)) {
            commands.add(new PushNotificationCommand(
                    target.provider(),
                    target.pushToken(),
                    title,
                    body,
                    Map.of(
                            "storyId", event.storyId().toString(),
                            "ownerUserId", event.ownerUserId() != null ? event.ownerUserId().toString() : "",
                            "ownerChatId", event.ownerChatId() != null ? event.ownerChatId().toString() : "",
                            "interactionType", interaction.type(),
                            "interactionId", interaction.interactionId() != null ? interaction.interactionId().toString() : ""
                    )
            ));
        }
        if (!commands.isEmpty()) {
            pushNotificationService.send(commands);
        }
    }

    private String buildPushBody(StoryInteractionResponse interaction) {
        String actorName = interaction.actorDisplayName() != null && !interaction.actorDisplayName().isBlank()
                ? interaction.actorDisplayName().trim()
                : "Someone";
        return switch (interaction.type()) {
            case "REACTION" -> actorName + " reacted to your story";
            case "REPLY" -> interaction.message() != null && !interaction.message().isBlank()
                    ? actorName + ": " + trimPreview(interaction.message())
                    : actorName + " replied to your story";
            case "MENTION" -> actorName + " added a story mention";
            default -> actorName + " interacted with your story";
        };
    }

    private String trimPreview(String value) {
        String normalized = value.trim();
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }
}
