package com.alex.messenger.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.story.dto.CreateStoryHighlightRequest;
import com.alex.messenger.story.dto.StoryHighlightResponse;
import com.alex.messenger.story.dto.StoryInteractionResponse;
import com.alex.messenger.story.dto.StoryMentionRequest;
import com.alex.messenger.story.dto.StoryReactionRequest;
import com.alex.messenger.story.dto.StoryReplyRequest;
import com.alex.messenger.story.dto.UpdateStoryHighlightStoriesRequest;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class StoryControllerTest {

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private StoryService storyService;

    @Mock
    private Validator validator;

    private StoryController storyController;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        storyController = new StoryController(featureFlagService, storyService, validator);
        currentUserId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUserId.toString(), "test")
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reactRequiresFlagsAndUsesAuthenticatedUser() {
        UUID storyId = UUID.randomUUID();
        StoryReactionRequest request = new StoryReactionRequest("fire");
        StoryInteractionResponse response = new StoryInteractionResponse(
                UUID.randomUUID(),
                storyId,
                "REACTION",
                currentUserId,
                "Alice",
                "alice",
                null,
                null,
                null,
                "fire",
                null,
                Instant.parse("2026-03-19T17:00:00Z")
        );

        when(storyService.react(currentUserId, storyId, request)).thenReturn(response);

        ResponseEntity<StoryInteractionResponse> entity = storyController.react(storyId, request);

        assertThat(entity.getBody()).isEqualTo(response);
        verify(featureFlagService).requireStoriesEnabled();
        verify(featureFlagService).requireStoryInteractionsEnabled();
        verify(storyService).react(currentUserId, storyId, request);
    }

    @Test
    void replyRequiresFlagsAndUsesAuthenticatedUser() {
        UUID storyId = UUID.randomUUID();
        StoryReplyRequest request = new StoryReplyRequest("hello");
        StoryInteractionResponse response = new StoryInteractionResponse(
                UUID.randomUUID(),
                storyId,
                "REPLY",
                currentUserId,
                "Alice",
                "alice",
                null,
                null,
                null,
                null,
                "hello",
                Instant.parse("2026-03-19T17:05:00Z")
        );

        when(storyService.reply(currentUserId, storyId, request)).thenReturn(response);

        ResponseEntity<StoryInteractionResponse> entity = storyController.reply(storyId, request);

        assertThat(entity.getBody()).isEqualTo(response);
        verify(featureFlagService).requireStoriesEnabled();
        verify(featureFlagService).requireStoryInteractionsEnabled();
        verify(storyService).reply(currentUserId, storyId, request);
    }

    @Test
    void mentionRequiresFlagsAndUsesAuthenticatedUser() {
        UUID storyId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        StoryMentionRequest request = new StoryMentionRequest(targetUserId, "look here");
        StoryInteractionResponse response = new StoryInteractionResponse(
                UUID.randomUUID(),
                storyId,
                "MENTION",
                currentUserId,
                "Alice",
                "alice",
                targetUserId,
                "Bob",
                "bob",
                null,
                "look here",
                Instant.parse("2026-03-19T17:10:00Z")
        );

        when(storyService.mention(currentUserId, storyId, request)).thenReturn(response);

        ResponseEntity<StoryInteractionResponse> entity = storyController.mention(storyId, request);

        assertThat(entity.getBody()).isEqualTo(response);
        verify(featureFlagService).requireStoriesEnabled();
        verify(featureFlagService).requireStoryInteractionsEnabled();
        verify(storyService).mention(currentUserId, storyId, request);
    }

    @Test
    void createHighlightRequiresOnlyStoriesFlag() {
        UUID highlightId = UUID.randomUUID();
        CreateStoryHighlightRequest request = new CreateStoryHighlightRequest(
                "Trips",
                null,
                null,
                List.of(UUID.randomUUID())
        );
        StoryHighlightResponse response = new StoryHighlightResponse(
                highlightId,
                currentUserId,
                "Trips",
                null,
                0,
                Instant.parse("2026-03-19T17:20:00Z"),
                Instant.parse("2026-03-19T17:20:00Z"),
                0,
                List.of()
        );

        when(storyService.createHighlight(currentUserId, request)).thenReturn(response);

        ResponseEntity<StoryHighlightResponse> entity = storyController.createHighlight(request);

        assertThat(entity.getBody()).isEqualTo(response);
        verify(featureFlagService).requireStoriesEnabled();
        verify(featureFlagService, never()).requireStoryInteractionsEnabled();
        verify(storyService).createHighlight(currentUserId, request);
    }

    @Test
    void removeStoryFromHighlightRequiresOnlyStoriesFlag() {
        UUID highlightId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        StoryHighlightResponse response = new StoryHighlightResponse(
                highlightId,
                currentUserId,
                "Trips",
                null,
                0,
                Instant.parse("2026-03-19T17:25:00Z"),
                Instant.parse("2026-03-19T17:25:00Z"),
                0,
                List.of()
        );

        when(storyService.removeStoryFromHighlight(currentUserId, highlightId, storyId)).thenReturn(response);

        ResponseEntity<StoryHighlightResponse> entity = storyController.removeStoryFromHighlight(highlightId, storyId);

        assertThat(entity.getBody()).isEqualTo(response);
        verify(featureFlagService).requireStoriesEnabled();
        verify(featureFlagService, never()).requireStoryInteractionsEnabled();
        verify(storyService).removeStoryFromHighlight(currentUserId, highlightId, storyId);
    }

    @Test
    void addStoriesToHighlightRequiresOnlyStoriesFlag() {
        UUID highlightId = UUID.randomUUID();
        UpdateStoryHighlightStoriesRequest request = new UpdateStoryHighlightStoriesRequest(
                List.of(UUID.randomUUID()),
                null
        );
        StoryHighlightResponse response = new StoryHighlightResponse(
                highlightId,
                currentUserId,
                "Trips",
                null,
                0,
                Instant.parse("2026-03-19T17:30:00Z"),
                Instant.parse("2026-03-19T17:30:00Z"),
                1,
                List.of()
        );

        when(storyService.addStoriesToHighlight(currentUserId, highlightId, request)).thenReturn(response);

        ResponseEntity<StoryHighlightResponse> entity = storyController.addStoriesToHighlight(highlightId, request);

        assertThat(entity.getBody()).isEqualTo(response);
        verify(featureFlagService).requireStoriesEnabled();
        verify(featureFlagService, never()).requireStoryInteractionsEnabled();
        verify(storyService).addStoriesToHighlight(currentUserId, highlightId, request);
    }

    @Test
    void listHighlightsRequiresOnlyStoriesFlag() {
        UUID ownerUserId = UUID.randomUUID();
        List<StoryHighlightResponse> response = List.of(
                new StoryHighlightResponse(
                        UUID.randomUUID(),
                        ownerUserId,
                        "Trips",
                        null,
                        0,
                        Instant.parse("2026-03-19T17:35:00Z"),
                        Instant.parse("2026-03-19T17:35:00Z"),
                        0,
                        List.of()
                )
        );

        when(storyService.listHighlights(currentUserId, ownerUserId)).thenReturn(response);

        ResponseEntity<List<StoryHighlightResponse>> entity = storyController.highlights(ownerUserId);

        assertThat(entity.getBody()).isEqualTo(response);
        verify(featureFlagService).requireStoriesEnabled();
        verify(featureFlagService, never()).requireStoryInteractionsEnabled();
        verify(storyService).listHighlights(currentUserId, ownerUserId);
    }
}
