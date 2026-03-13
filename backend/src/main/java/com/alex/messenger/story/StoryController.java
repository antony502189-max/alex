package com.alex.messenger.story;

import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.shared.CurrentUser;
import com.alex.messenger.story.dto.CreateStoryRequest;
import com.alex.messenger.story.dto.CreateStoryHighlightRequest;
import com.alex.messenger.story.dto.StoryFeedItemResponse;
import com.alex.messenger.story.dto.StoryHighlightResponse;
import com.alex.messenger.story.dto.StoryInteractionResponse;
import com.alex.messenger.story.dto.StoryInteractionSummaryResponse;
import com.alex.messenger.story.dto.StoryMentionRequest;
import com.alex.messenger.story.dto.StoryReactionRequest;
import com.alex.messenger.story.dto.StoryReplyRequest;
import com.alex.messenger.story.dto.StoryReshareRequest;
import com.alex.messenger.story.dto.StoryResponse;
import com.alex.messenger.story.dto.StoryViewerResponse;
import com.alex.messenger.story.dto.UpdateStoryHighlightStoriesRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class StoryController {

    private final FeatureFlagService featureFlagService;
    private final StoryService storyService;

    @GetMapping("/feed")
    public ResponseEntity<List<StoryFeedItemResponse>> feed() {
        featureFlagService.requireStoriesEnabled();
        return ResponseEntity.ok(storyService.listFeed(CurrentUser.id()));
    }

    @GetMapping("/archive")
    public ResponseEntity<List<StoryResponse>> archive() {
        featureFlagService.requireStoriesEnabled();
        return ResponseEntity.ok(storyService.listArchive(CurrentUser.id()));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StoryResponse> create(@Valid @RequestBody CreateStoryRequest request) {
        featureFlagService.requireStoriesEnabled();
        return ResponseEntity.ok(storyService.create(CurrentUser.id(), request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoryResponse> createWithMedia(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String backgroundFrom,
            @RequestParam(required = false) String backgroundTo,
            @RequestParam(required = false) String textColor,
            @RequestParam(required = false) String audience,
            @RequestParam(required = false) List<UUID> allowedViewerUserIds,
            @RequestParam(required = false) Long durationMs,
            @RequestPart("file") MultipartFile file
    ) {
        featureFlagService.requireStoriesEnabled();
        CreateStoryRequest request = new CreateStoryRequest(
                text,
                backgroundFrom,
                backgroundTo,
                textColor,
                audience,
                allowedViewerUserIds
        );
        return ResponseEntity.ok(storyService.createWithMedia(CurrentUser.id(), request, durationMs, file));
    }

    @PostMapping("/{storyId}/view")
    public ResponseEntity<StoryResponse> view(@PathVariable UUID storyId) {
        featureFlagService.requireStoriesEnabled();
        return ResponseEntity.ok(storyService.markViewed(CurrentUser.id(), storyId));
    }

    @GetMapping("/{storyId}/viewers")
    public ResponseEntity<List<StoryViewerResponse>> viewers(@PathVariable UUID storyId) {
        featureFlagService.requireStoriesEnabled();
        return ResponseEntity.ok(storyService.listViewers(CurrentUser.id(), storyId));
    }

    @GetMapping("/{storyId}/interactions")
    public ResponseEntity<List<StoryInteractionResponse>> interactions(@PathVariable UUID storyId) {
        featureFlagService.requireStoriesEnabled();
        featureFlagService.requireStoryInteractionsEnabled();
        return ResponseEntity.ok(storyService.listInteractions(CurrentUser.id(), storyId));
    }

    @GetMapping("/{storyId}/interactions/summary")
    public ResponseEntity<StoryInteractionSummaryResponse> interactionSummary(@PathVariable UUID storyId) {
        featureFlagService.requireStoriesEnabled();
        featureFlagService.requireStoryInteractionsEnabled();
        return ResponseEntity.ok(storyService.getInteractionSummary(CurrentUser.id(), storyId));
    }

    @PostMapping("/{storyId}/reactions")
    public ResponseEntity<StoryInteractionResponse> react(
            @PathVariable UUID storyId,
            @Valid @RequestBody StoryReactionRequest request
    ) {
        featureFlagService.requireStoriesEnabled();
        featureFlagService.requireStoryInteractionsEnabled();
        return ResponseEntity.ok(storyService.react(CurrentUser.id(), storyId, request));
    }

    @DeleteMapping("/{storyId}/reactions")
    public ResponseEntity<Void> removeReaction(@PathVariable UUID storyId) {
        featureFlagService.requireStoriesEnabled();
        featureFlagService.requireStoryInteractionsEnabled();
        storyService.removeReaction(CurrentUser.id(), storyId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{storyId}/replies")
    public ResponseEntity<StoryInteractionResponse> reply(
            @PathVariable UUID storyId,
            @Valid @RequestBody StoryReplyRequest request
    ) {
        featureFlagService.requireStoriesEnabled();
        featureFlagService.requireStoryInteractionsEnabled();
        return ResponseEntity.ok(storyService.reply(CurrentUser.id(), storyId, request));
    }

    @PostMapping("/{storyId}/mentions")
    public ResponseEntity<StoryInteractionResponse> mention(
            @PathVariable UUID storyId,
            @Valid @RequestBody StoryMentionRequest request
    ) {
        featureFlagService.requireStoriesEnabled();
        featureFlagService.requireStoryInteractionsEnabled();
        return ResponseEntity.ok(storyService.mention(CurrentUser.id(), storyId, request));
    }

    @PostMapping("/{storyId}/reshares")
    public ResponseEntity<StoryInteractionResponse> reshare(
            @PathVariable UUID storyId,
            @Valid @RequestBody StoryReshareRequest request
    ) {
        featureFlagService.requireStoriesEnabled();
        featureFlagService.requireStoryInteractionsEnabled();
        return ResponseEntity.ok(storyService.reshare(CurrentUser.id(), storyId, request));
    }

    @GetMapping("/highlights")
    public ResponseEntity<List<StoryHighlightResponse>> highlights(@RequestParam(required = false) UUID ownerUserId) {
        featureFlagService.requireStoriesEnabled();
        featureFlagService.requireStoryInteractionsEnabled();
        return ResponseEntity.ok(storyService.listHighlights(CurrentUser.id(), ownerUserId));
    }

    @PostMapping("/highlights")
    public ResponseEntity<StoryHighlightResponse> createHighlight(
            @Valid @RequestBody CreateStoryHighlightRequest request
    ) {
        featureFlagService.requireStoriesEnabled();
        featureFlagService.requireStoryInteractionsEnabled();
        return ResponseEntity.ok(storyService.createHighlight(CurrentUser.id(), request));
    }

    @PostMapping("/highlights/{highlightId}/stories")
    public ResponseEntity<StoryHighlightResponse> addStoriesToHighlight(
            @PathVariable UUID highlightId,
            @RequestBody UpdateStoryHighlightStoriesRequest request
    ) {
        featureFlagService.requireStoriesEnabled();
        featureFlagService.requireStoryInteractionsEnabled();
        return ResponseEntity.ok(storyService.addStoriesToHighlight(CurrentUser.id(), highlightId, request));
    }

    @DeleteMapping("/highlights/{highlightId}/stories/{storyId}")
    public ResponseEntity<StoryHighlightResponse> removeStoryFromHighlight(
            @PathVariable UUID highlightId,
            @PathVariable UUID storyId
    ) {
        featureFlagService.requireStoriesEnabled();
        featureFlagService.requireStoryInteractionsEnabled();
        return ResponseEntity.ok(storyService.removeStoryFromHighlight(CurrentUser.id(), highlightId, storyId));
    }

    @DeleteMapping("/{storyId}")
    public ResponseEntity<Void> delete(@PathVariable UUID storyId) {
        featureFlagService.requireStoriesEnabled();
        storyService.delete(CurrentUser.id(), storyId);
        return ResponseEntity.noContent().build();
    }
}
