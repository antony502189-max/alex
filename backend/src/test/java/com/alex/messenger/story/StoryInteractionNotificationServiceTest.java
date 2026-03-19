package com.alex.messenger.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.auth.session.PushSessionTarget;
import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.notification.PushNotificationCommand;
import com.alex.messenger.notification.PushNotificationService;
import com.alex.messenger.story.dto.StoryInteractionEventResponse;
import com.alex.messenger.story.dto.StoryInteractionResponse;
import com.alex.messenger.story.dto.StoryInteractionSummaryResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class StoryInteractionNotificationServiceTest {

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    @Mock
    private PushNotificationService pushNotificationService;

    @Mock
    private UserSessionService userSessionService;

    private StoryInteractionNotificationService storyInteractionNotificationService;

    @BeforeEach
    void setUp() {
        storyInteractionNotificationService = new StoryInteractionNotificationService(
                simpMessagingTemplate,
                pushNotificationService,
                userSessionService
        );
    }

    @Test
    void publishSendsRealtimeAndOfflinePushForInteractionUpsert() {
        UUID recipientUserId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID interactionId = UUID.randomUUID();

        StoryInteractionEventResponse event = new StoryInteractionEventResponse(
                "INTERACTION_UPSERT",
                storyId,
                recipientUserId,
                null,
                new StoryInteractionResponse(
                        interactionId,
                        storyId,
                        "REPLY",
                        actorUserId,
                        "Alice",
                        "alice",
                        recipientUserId,
                        "Owner",
                        "owner",
                        null,
                        "hello from a reply",
                        Instant.parse("2026-03-19T10:00:00Z")
                ),
                new StoryInteractionSummaryResponse(storyId, 0, 1, 0, 0, null),
                3,
                1
        );

        when(userSessionService.isUserOnline(recipientUserId)).thenReturn(false);
        when(userSessionService.getPushTargets(recipientUserId)).thenReturn(List.of(
                new PushSessionTarget(UUID.randomUUID(), "EXPO", "ExponentPushToken[story]")
        ));

        storyInteractionNotificationService.publish(recipientUserId, event);

        verify(simpMessagingTemplate).convertAndSendToUser(
                recipientUserId.toString(),
                "/queue/story-events",
                event
        );
        ArgumentCaptor<List> commandsCaptor = ArgumentCaptor.forClass(List.class);
        verify(pushNotificationService).send(commandsCaptor.capture());
        PushNotificationCommand command = (PushNotificationCommand) commandsCaptor.getValue().get(0);
        assertThat(command.title()).isEqualTo("Story activity");
        assertThat(command.body()).isEqualTo("Alice: hello from a reply");
        assertThat(command.data()).containsEntry("storyId", storyId.toString());
        assertThat(command.data()).containsEntry("interactionId", interactionId.toString());
    }

    @Test
    void publishSkipsPushForOnlineRecipients() {
        UUID recipientUserId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        StoryInteractionEventResponse event = new StoryInteractionEventResponse(
                "INTERACTION_UPSERT",
                storyId,
                recipientUserId,
                null,
                new StoryInteractionResponse(
                        UUID.randomUUID(),
                        storyId,
                        "REACTION",
                        UUID.randomUUID(),
                        "Actor",
                        "actor",
                        recipientUserId,
                        "Owner",
                        "owner",
                        "fire",
                        null,
                        Instant.parse("2026-03-19T10:05:00Z")
                ),
                new StoryInteractionSummaryResponse(storyId, 1, 0, 0, 0, null),
                1,
                1
        );

        when(userSessionService.isUserOnline(recipientUserId)).thenReturn(true);

        storyInteractionNotificationService.publish(recipientUserId, event);

        verify(simpMessagingTemplate).convertAndSendToUser(
                recipientUserId.toString(),
                "/queue/story-events",
                event
        );
        verify(pushNotificationService, never()).send(any());
        verify(userSessionService, never()).getPushTargets(recipientUserId);
    }

    @Test
    void publishSkipsPushForSeenEvents() {
        UUID recipientUserId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        StoryInteractionEventResponse event = new StoryInteractionEventResponse(
                "INTERACTIONS_SEEN",
                storyId,
                recipientUserId,
                null,
                null,
                new StoryInteractionSummaryResponse(storyId, 1, 0, 0, 0, null),
                0,
                0
        );

        storyInteractionNotificationService.publish(recipientUserId, event);

        verify(simpMessagingTemplate).convertAndSendToUser(
                recipientUserId.toString(),
                "/queue/story-events",
                event
        );
        verify(pushNotificationService, never()).send(any());
        verify(userSessionService, never()).isUserOnline(recipientUserId);
    }

    @Test
    void publishSkipsPushForSelfAuthoredInteraction() {
        UUID recipientUserId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        StoryInteractionEventResponse event = new StoryInteractionEventResponse(
                "INTERACTION_UPSERT",
                storyId,
                recipientUserId,
                null,
                new StoryInteractionResponse(
                        UUID.randomUUID(),
                        storyId,
                        "REACTION",
                        recipientUserId,
                        "Owner",
                        "owner",
                        recipientUserId,
                        "Owner",
                        "owner",
                        "fire",
                        null,
                        Instant.parse("2026-03-19T10:10:00Z")
                ),
                new StoryInteractionSummaryResponse(storyId, 1, 0, 0, 0, null),
                0,
                0
        );

        storyInteractionNotificationService.publish(recipientUserId, event);

        verify(simpMessagingTemplate).convertAndSendToUser(
                recipientUserId.toString(),
                "/queue/story-events",
                event
        );
        verify(pushNotificationService, never()).send(any());
        verify(userSessionService, never()).isUserOnline(recipientUserId);
    }
}
