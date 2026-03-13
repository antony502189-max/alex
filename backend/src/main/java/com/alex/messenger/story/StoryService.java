package com.alex.messenger.story;

import com.alex.messenger.media.MediaObjectReference;
import com.alex.messenger.media.MediaProcessingService;
import com.alex.messenger.media.MediaService;
import com.alex.messenger.media.PresignedMediaAccess;
import com.alex.messenger.story.dto.CreateStoryRequest;
import com.alex.messenger.story.dto.CreateStoryHighlightRequest;
import com.alex.messenger.story.dto.StoryFeedItemResponse;
import com.alex.messenger.story.dto.StoryHighlightResponse;
import com.alex.messenger.story.dto.StoryInteractionResponse;
import com.alex.messenger.story.dto.StoryInteractionSummaryResponse;
import com.alex.messenger.story.dto.StoryMentionRequest;
import com.alex.messenger.story.dto.StoryMediaResponse;
import com.alex.messenger.story.dto.StoryReactionRequest;
import com.alex.messenger.story.dto.StoryReplyRequest;
import com.alex.messenger.story.dto.StoryReshareRequest;
import com.alex.messenger.story.dto.StoryResponse;
import com.alex.messenger.story.dto.StoryViewerResponse;
import com.alex.messenger.story.dto.UpdateStoryHighlightStoriesRequest;
import com.alex.messenger.user.ContactRepository;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class StoryService {

    private static final Duration STORY_TTL = Duration.ofHours(24);

    private final StoryRepository storyRepository;
    private final StoryViewRepository storyViewRepository;
    private final StoryInteractionRepository storyInteractionRepository;
    private final StoryHighlightRepository storyHighlightRepository;
    private final StoryHighlightItemRepository storyHighlightItemRepository;
    private final UserRepository userRepository;
    private final ContactRepository contactRepository;
    private final MediaService mediaService;
    private final MediaProcessingService mediaProcessingService;

    @Value("${alex.storage.stories.max-file-size-bytes}")
    private long maxStoryMediaFileSizeBytes;

    @Transactional
    public StoryResponse create(UUID requesterId, CreateStoryRequest request) {
        return createInternal(requesterId, request, null, null);
    }

    @Transactional
    public StoryResponse createWithMedia(
            UUID requesterId,
            CreateStoryRequest request,
            Long durationMs,
            MultipartFile file
    ) {
        return createInternal(requesterId, request, durationMs, file);
    }

    @Transactional(readOnly = true)
    public List<StoryFeedItemResponse> listFeed(UUID requesterId) {
        List<StoryEntity> stories = storyRepository.findAllByExpiresAtAfterOrderByCreatedAtDesc(Instant.now());
        if (stories.isEmpty()) {
            return List.of();
        }

        Map<UUID, UserEntity> usersById = getUsers(stories.stream().map(StoryEntity::getOwnerUserId).toList());
        List<StoryEntity> visibleStories = stories.stream()
                .filter(story -> canViewStory(requesterId, story, usersById.get(story.getOwnerUserId())))
                .toList();
        if (visibleStories.isEmpty()) {
            return List.of();
        }

        Map<UUID, Integer> viewsCountByStoryId =
                getViewsCountByStoryId(visibleStories.stream().map(StoryEntity::getId).toList());
        Set<UUID> viewedStoryIds = storyViewRepository.findAllByIdViewerUserIdAndIdStoryIdIn(
                requesterId,
                visibleStories.stream().map(StoryEntity::getId).toList()
        ).stream().map(view -> view.getId().getStoryId()).collect(Collectors.toSet());

        Map<UUID, List<StoryResponse>> grouped = visibleStories.stream()
                .map(story -> {
                    UserEntity owner = usersById.get(story.getOwnerUserId());
                    boolean own = story.getOwnerUserId().equals(requesterId);
                    boolean viewed = own || viewedStoryIds.contains(story.getId());
                    return toResponse(story, owner, viewed, own, viewsCountByStoryId.getOrDefault(story.getId(), 0));
                })
                .collect(Collectors.groupingBy(StoryResponse::ownerUserId));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<StoryResponse> ownerStories = entry.getValue().stream()
                            .sorted(Comparator.comparing(StoryResponse::createdAt))
                            .toList();
                    StoryResponse latest = ownerStories.get(ownerStories.size() - 1);
                    boolean hasUnviewed = ownerStories.stream().anyMatch(story -> !story.viewed());
                    return new StoryFeedItemResponse(
                            entry.getKey(),
                            latest.ownerDisplayName(),
                            latest.ownerUsername(),
                            latest.own(),
                            hasUnviewed,
                            latest.createdAt(),
                            ownerStories
                    );
                })
                .sorted(Comparator
                        .comparing(StoryFeedItemResponse::own).reversed()
                        .thenComparing(StoryFeedItemResponse::hasUnviewed).reversed()
                        .thenComparing(StoryFeedItemResponse::latestStoryAt).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StoryResponse> listArchive(UUID requesterId) {
        UserEntity owner = getUser(requesterId);
        List<StoryEntity> stories = storyRepository.findAllByOwnerUserIdOrderByCreatedAtDesc(requesterId);
        if (stories.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        Map<UUID, Integer> viewsCountByStoryId = getViewsCountByStoryId(stories.stream().map(StoryEntity::getId).toList());
        return stories.stream()
                .filter(story -> !story.getExpiresAt().isAfter(now))
                .map(story -> toResponse(story, owner, true, true, viewsCountByStoryId.getOrDefault(story.getId(), 0)))
                .toList();
    }

    @Transactional
    public StoryResponse markViewed(UUID requesterId, UUID storyId) {
        StoryEntity story = getAccessibleStory(requesterId, storyId);
        UserEntity owner = getUser(story.getOwnerUserId());
        if (!story.getOwnerUserId().equals(requesterId)) {
            StoryViewId id = new StoryViewId(storyId, requesterId);
            if (!storyViewRepository.existsById(id)) {
                StoryViewEntity view = new StoryViewEntity();
                view.setId(id);
                storyViewRepository.save(view);
            }
        }
        int viewsCount = storyViewRepository.findAllByIdStoryId(storyId).size();
        return toResponse(story, owner, true, story.getOwnerUserId().equals(requesterId), viewsCount);
    }

    @Transactional
    public void delete(UUID requesterId, UUID storyId) {
        StoryEntity story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Story not found"));
        if (!story.getOwnerUserId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only owner can delete story");
        }
        deleteStoryMedia(story);
        storyRepository.delete(story);
    }

    @Transactional(readOnly = true)
    public List<StoryViewerResponse> listViewers(UUID requesterId, UUID storyId) {
        StoryEntity story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Story not found"));
        if (!story.getOwnerUserId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only owner can list viewers");
        }

        List<StoryViewEntity> views = storyViewRepository.findAllByIdStoryId(storyId);
        Map<UUID, UserEntity> usersById = getUsers(
                views.stream().map(view -> view.getId().getViewerUserId()).toList()
        );

        return views.stream()
                .sorted(Comparator.comparing(StoryViewEntity::getViewedAt).reversed())
                .map(view -> {
                    UserEntity viewer = usersById.get(view.getId().getViewerUserId());
                    return new StoryViewerResponse(
                            view.getId().getViewerUserId(),
                            viewer != null ? viewer.getDisplayName() : "Unknown",
                            viewer != null ? viewer.getUsername() : null,
                            view.getViewedAt()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StoryInteractionResponse> listInteractions(UUID requesterId, UUID storyId) {
        getOwnedStory(requesterId, storyId);
        List<StoryInteractionEntity> interactions = storyInteractionRepository.findAllByStoryIdOrderByCreatedAtDesc(storyId);
        Map<UUID, UserEntity> usersById = getUsers(interactions.stream()
                .flatMap(interaction -> java.util.stream.Stream.of(
                        interaction.getActorUserId(),
                        interaction.getTargetUserId()
                ))
                .filter(Objects::nonNull)
                .toList());
        return interactions.stream()
                .map(interaction -> toInteractionResponse(interaction, usersById))
                .toList();
    }

    @Transactional(readOnly = true)
    public StoryInteractionSummaryResponse getInteractionSummary(UUID requesterId, UUID storyId) {
        getOwnedStory(requesterId, storyId);
        List<StoryInteractionEntity> interactions = storyInteractionRepository.findAllByStoryIdOrderByCreatedAtDesc(storyId);
        String viewerReaction = storyInteractionRepository
                .findFirstByStoryIdAndActorUserIdAndInteractionType(storyId, requesterId, "REACTION")
                .map(StoryInteractionEntity::getReactionCode)
                .orElse(null);
        return new StoryInteractionSummaryResponse(
                storyId,
                countInteractions(interactions, "REACTION"),
                countInteractions(interactions, "REPLY"),
                countInteractions(interactions, "MENTION"),
                countInteractions(interactions, "RESHARE"),
                viewerReaction
        );
    }

    @Transactional
    public StoryInteractionResponse react(UUID requesterId, UUID storyId, StoryReactionRequest request) {
        StoryEntity story = getAccessibleStory(requesterId, storyId);
        StoryInteractionEntity interaction = storyInteractionRepository
                .findFirstByStoryIdAndActorUserIdAndInteractionType(story.getId(), requesterId, "REACTION")
                .orElseGet(StoryInteractionEntity::new);
        interaction.setStoryId(story.getId());
        interaction.setActorUserId(requesterId);
        interaction.setTargetUserId(story.getOwnerUserId());
        interaction.setInteractionType("REACTION");
        interaction.setReactionCode(normalizeReaction(request.reaction()));
        interaction.setMessageText(null);
        StoryInteractionEntity saved = storyInteractionRepository.save(interaction);
        return toInteractionResponse(saved, getUsers(List.of(requesterId, story.getOwnerUserId())));
    }

    @Transactional
    public void removeReaction(UUID requesterId, UUID storyId) {
        getAccessibleStory(requesterId, storyId);
        storyInteractionRepository.deleteByStoryIdAndActorUserIdAndInteractionType(storyId, requesterId, "REACTION");
    }

    @Transactional
    public StoryInteractionResponse reply(UUID requesterId, UUID storyId, StoryReplyRequest request) {
        StoryEntity story = getAccessibleStory(requesterId, storyId);
        StoryInteractionEntity interaction = new StoryInteractionEntity();
        interaction.setStoryId(story.getId());
        interaction.setActorUserId(requesterId);
        interaction.setTargetUserId(story.getOwnerUserId());
        interaction.setInteractionType("REPLY");
        interaction.setMessageText(normalizeInteractionText(request.message(), "Reply message is required"));
        StoryInteractionEntity saved = storyInteractionRepository.save(interaction);
        return toInteractionResponse(saved, getUsers(List.of(requesterId, story.getOwnerUserId())));
    }

    @Transactional
    public StoryInteractionResponse mention(UUID requesterId, UUID storyId, StoryMentionRequest request) {
        StoryEntity story = getOwnedActiveStory(requesterId, storyId);
        if (request.targetUserId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Story owner is already associated with the story");
        }
        UserEntity targetUser = getUser(request.targetUserId());

        StoryInteractionEntity interaction = new StoryInteractionEntity();
        interaction.setStoryId(story.getId());
        interaction.setActorUserId(requesterId);
        interaction.setTargetUserId(targetUser.getId());
        interaction.setInteractionType("MENTION");
        interaction.setMessageText(normalizeOptionalText(request.message()));
        StoryInteractionEntity saved = storyInteractionRepository.save(interaction);
        return toInteractionResponse(saved, getUsers(List.of(requesterId, targetUser.getId())));
    }

    @Transactional
    public StoryInteractionResponse reshare(UUID requesterId, UUID storyId, StoryReshareRequest request) {
        StoryEntity story = getAccessibleStory(requesterId, storyId);
        StoryInteractionEntity interaction = new StoryInteractionEntity();
        interaction.setStoryId(story.getId());
        interaction.setActorUserId(requesterId);
        interaction.setTargetUserId(story.getOwnerUserId());
        interaction.setInteractionType("RESHARE");
        interaction.setMessageText(normalizeOptionalText(request.note()));
        StoryInteractionEntity saved = storyInteractionRepository.save(interaction);
        return toInteractionResponse(saved, getUsers(List.of(requesterId, story.getOwnerUserId())));
    }

    @Transactional(readOnly = true)
    public List<StoryHighlightResponse> listHighlights(UUID requesterId, UUID ownerUserId) {
        UUID effectiveOwnerUserId = ownerUserId != null ? ownerUserId : requesterId;
        getUser(effectiveOwnerUserId);
        return storyHighlightRepository.findAllByOwnerUserIdOrderByPositionAscCreatedAtAsc(effectiveOwnerUserId).stream()
                .map(highlight -> toHighlightResponse(highlight, requesterId))
                .filter(response -> !response.stories().isEmpty() || effectiveOwnerUserId.equals(requesterId))
                .toList();
    }

    @Transactional
    public StoryHighlightResponse createHighlight(UUID requesterId, CreateStoryHighlightRequest request) {
        List<UUID> storyIds = normalizeStoryIds(request.storyIds());
        Map<UUID, StoryEntity> storiesById = loadHighlightStoriesOwnedBy(requesterId, storyIds);
        int nextPosition = storyHighlightRepository.findAllByOwnerUserIdOrderByPositionAscCreatedAtAsc(requesterId).size();

        StoryHighlightEntity highlight = new StoryHighlightEntity();
        highlight.setOwnerUserId(requesterId);
        highlight.setTitle(normalizeHighlightTitle(request.title()));
        highlight.setPosition(normalizeHighlightPosition(request.position(), nextPosition));
        highlight.setCoverStoryId(resolveCoverStoryId(request.coverStoryId(), storyIds));
        StoryHighlightEntity savedHighlight = storyHighlightRepository.save(highlight);

        persistHighlightItems(savedHighlight.getId(), storyIds, 0);
        if (savedHighlight.getCoverStoryId() == null && !storyIds.isEmpty()) {
            savedHighlight.setCoverStoryId(storyIds.get(0));
            savedHighlight = storyHighlightRepository.save(savedHighlight);
        }

        return toHighlightResponse(savedHighlight, requesterId);
    }

    @Transactional
    public StoryHighlightResponse addStoriesToHighlight(
            UUID requesterId,
            UUID highlightId,
            UpdateStoryHighlightStoriesRequest request
    ) {
        StoryHighlightEntity highlight = getOwnedHighlight(requesterId, highlightId);
        List<UUID> requestedStoryIds = normalizeStoryIds(request.storyIds());
        Map<UUID, StoryEntity> storiesById = loadHighlightStoriesOwnedBy(requesterId, requestedStoryIds);
        List<StoryHighlightItemEntity> existingItems = storyHighlightItemRepository
                .findAllByHighlightIdOrderByPositionAscCreatedAtAsc(highlightId);
        Set<UUID> existingStoryIds = existingItems.stream()
                .map(StoryHighlightItemEntity::getStoryId)
                .collect(Collectors.toSet());
        List<UUID> newStoryIds = requestedStoryIds.stream()
                .filter(storyId -> !existingStoryIds.contains(storyId))
                .toList();
        if (!newStoryIds.isEmpty()) {
            persistHighlightItems(highlightId, newStoryIds, existingItems.size());
        }

        List<UUID> resultingStoryIds = new ArrayList<>(existingItems.stream()
                .map(StoryHighlightItemEntity::getStoryId)
                .toList());
        resultingStoryIds.addAll(newStoryIds);
        if (request.coverStoryId() != null) {
            highlight.setCoverStoryId(resolveCoverStoryId(request.coverStoryId(), resultingStoryIds));
        } else if (highlight.getCoverStoryId() == null && !resultingStoryIds.isEmpty()) {
            highlight.setCoverStoryId(resultingStoryIds.get(0));
        }
        if (!newStoryIds.isEmpty() || request.coverStoryId() != null) {
            storyHighlightRepository.save(highlight);
        }
        return toHighlightResponse(highlight, requesterId);
    }

    @Transactional
    public StoryHighlightResponse removeStoryFromHighlight(UUID requesterId, UUID highlightId, UUID storyId) {
        StoryHighlightEntity highlight = getOwnedHighlight(requesterId, highlightId);
        StoryHighlightItemEntity item = storyHighlightItemRepository.findFirstByHighlightIdAndStoryId(highlightId, storyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Story highlight item not found"));
        storyHighlightItemRepository.delete(item);

        List<StoryHighlightItemEntity> remainingItems = storyHighlightItemRepository
                .findAllByHighlightIdOrderByPositionAscCreatedAtAsc(highlightId);
        reindexHighlightItems(remainingItems);
        if (storyId.equals(highlight.getCoverStoryId())) {
            highlight.setCoverStoryId(remainingItems.isEmpty() ? null : remainingItems.get(0).getStoryId());
            storyHighlightRepository.save(highlight);
        }
        return toHighlightResponse(highlight, requesterId);
    }

    private StoryEntity getAccessibleStory(UUID requesterId, UUID storyId) {
        StoryEntity story = storyRepository.findByIdAndExpiresAtAfter(storyId, Instant.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Story not found"));
        UserEntity owner = getUser(story.getOwnerUserId());
        if (canViewStory(requesterId, story, owner)) {
            return story;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Story access denied");
    }

    private StoryEntity getOwnedStory(UUID requesterId, UUID storyId) {
        StoryEntity story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Story not found"));
        if (!story.getOwnerUserId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only owner can access story insights");
        }
        return story;
    }

    private StoryEntity getOwnedActiveStory(UUID requesterId, UUID storyId) {
        StoryEntity story = getOwnedStory(requesterId, storyId);
        if (!story.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Story is no longer active");
        }
        return story;
    }

    private StoryResponse createInternal(
            UUID requesterId,
            CreateStoryRequest request,
            Long durationMs,
            MultipartFile file
    ) {
        UserEntity owner = getUser(requesterId);
        String normalizedText = normalizeOptionalText(request.text());
        if ((normalizedText == null || normalizedText.isBlank()) && (file == null || file.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Story must include text or media");
        }

        String audience = normalizeAudience(request.audience());
        Set<UUID> allowedViewerIds = validateAllowedViewerIds(requesterId, audience, request.allowedViewerUserIds());

        StoryEntity story = new StoryEntity();
        story.setOwnerUserId(requesterId);
        story.setText(normalizedText);
        story.setBackgroundFrom(normalizeColor(request.backgroundFrom(), "#0f172a"));
        story.setBackgroundTo(normalizeColor(request.backgroundTo(), "#2563eb"));
        story.setTextColor(normalizeColor(request.textColor(), "#ffffff"));
        story.setAudience(audience);
        story.setAllowedViewerUserIds(serializeAllowedViewerIds(allowedViewerIds));
        story.setExpiresAt(Instant.now().plus(STORY_TTL));

        StoryEntity saved = storyRepository.save(story);
        if (file != null && !file.isEmpty()) {
            storeMedia(saved, durationMs, file);
            saved = storyRepository.save(saved);
        }
        return toResponse(saved, owner, false, true, 0);
    }

    private StoryResponse toResponse(
            StoryEntity story,
            UserEntity owner,
            boolean viewed,
            boolean own,
            int viewsCount
    ) {
        Instant now = Instant.now();
        return new StoryResponse(
                story.getId(),
                story.getOwnerUserId(),
                owner != null ? owner.getDisplayName() : "Unknown",
                owner != null ? owner.getUsername() : null,
                story.getText(),
                buildMediaResponse(story),
                story.getBackgroundFrom(),
                story.getBackgroundTo(),
                story.getTextColor(),
                story.getAudience(),
                story.getCreatedAt(),
                story.getExpiresAt(),
                !story.getExpiresAt().isAfter(now),
                viewed,
                own,
                viewsCount
        );
    }

    private StoryInteractionResponse toInteractionResponse(
            StoryInteractionEntity interaction,
            Map<UUID, UserEntity> usersById
    ) {
        UserEntity actor = usersById.get(interaction.getActorUserId());
        UserEntity target = interaction.getTargetUserId() != null
                ? usersById.get(interaction.getTargetUserId())
                : null;
        return new StoryInteractionResponse(
                interaction.getId(),
                interaction.getStoryId(),
                interaction.getInteractionType(),
                interaction.getActorUserId(),
                actor != null ? actor.getDisplayName() : "Unknown",
                actor != null ? actor.getUsername() : null,
                interaction.getTargetUserId(),
                target != null ? target.getDisplayName() : null,
                target != null ? target.getUsername() : null,
                interaction.getReactionCode(),
                interaction.getMessageText(),
                interaction.getCreatedAt()
        );
    }

    private StoryHighlightResponse toHighlightResponse(StoryHighlightEntity highlight, UUID requesterId) {
        UserEntity owner = getUser(highlight.getOwnerUserId());
        List<StoryHighlightItemEntity> items = storyHighlightItemRepository
                .findAllByHighlightIdOrderByPositionAscCreatedAtAsc(highlight.getId());
        if (items.isEmpty()) {
            return new StoryHighlightResponse(
                    highlight.getId(),
                    highlight.getOwnerUserId(),
                    highlight.getTitle(),
                    highlight.getCoverStoryId(),
                    highlight.getPosition(),
                    highlight.getCreatedAt(),
                    highlight.getUpdatedAt(),
                    0,
                    List.of()
            );
        }

        List<UUID> storyIds = items.stream().map(StoryHighlightItemEntity::getStoryId).toList();
        Map<UUID, StoryEntity> storiesById = storyRepository.findAllById(storyIds).stream()
                .collect(Collectors.toMap(StoryEntity::getId, Function.identity()));
        Set<UUID> viewedStoryIds = requesterId != null
                ? storyViewRepository.findAllByIdViewerUserIdAndIdStoryIdIn(requesterId, storyIds).stream()
                        .map(view -> view.getId().getStoryId())
                        .collect(Collectors.toSet())
                : Set.of();
        Map<UUID, Integer> viewsCountByStoryId = getViewsCountByStoryId(storyIds);

        List<StoryResponse> stories = items.stream()
                .map(item -> storiesById.get(item.getStoryId()))
                .filter(Objects::nonNull)
                .filter(story -> highlight.getOwnerUserId().equals(requesterId) || canViewStory(requesterId, story, owner))
                .map(story -> toResponse(
                        story,
                        owner,
                        highlight.getOwnerUserId().equals(requesterId) || viewedStoryIds.contains(story.getId()),
                        highlight.getOwnerUserId().equals(requesterId),
                        viewsCountByStoryId.getOrDefault(story.getId(), 0)
                ))
                .toList();

        UUID coverStoryId = highlight.getCoverStoryId();
        UUID requestedCoverStoryId = coverStoryId;
        if (requestedCoverStoryId != null
                && stories.stream().noneMatch(story -> requestedCoverStoryId.equals(story.storyId()))) {
            coverStoryId = stories.isEmpty() ? null : stories.get(0).storyId();
        }
        return new StoryHighlightResponse(
                highlight.getId(),
                highlight.getOwnerUserId(),
                highlight.getTitle(),
                coverStoryId,
                highlight.getPosition(),
                highlight.getCreatedAt(),
                highlight.getUpdatedAt(),
                stories.size(),
                stories
        );
    }

    private StoryMediaResponse buildMediaResponse(StoryEntity story) {
        if (!"S3".equalsIgnoreCase(story.getMediaStorageProvider())
                || story.getMediaBucketName() == null
                || story.getMediaObjectKey() == null) {
            return null;
        }
        PresignedMediaAccess access = mediaService.buildDownloadAccess(story.getMediaBucketName(), story.getMediaObjectKey());
        boolean previewable = story.getMediaContentType() != null
                && story.getMediaContentType().toLowerCase().startsWith("image/");
        boolean streamingSupported = story.getMediaContentType() != null
                && story.getMediaContentType().toLowerCase().startsWith("video/");
        String previewUrl = previewable ? resolveStoryPreviewUrl(story) : null;
        return new StoryMediaResponse(
                story.getMediaKind(),
                story.getMediaFileName(),
                story.getMediaContentType(),
                story.getMediaDurationMs(),
                access.downloadUrl(),
                previewUrl,
                access.expiresAt(),
                false,
                streamingSupported
        );
    }

    private void storeMedia(StoryEntity story, Long durationMs, MultipartFile file) {
        validateStoryMedia(file, durationMs);
        String contentType = normalizeContentType(file.getContentType());
        String mediaKind = normalizeMediaKind(contentType);
        try (var inputStream = file.getInputStream()) {
            MediaObjectReference reference = mediaService.uploadStoryMedia(
                    story.getOwnerUserId(),
                    story.getId(),
                    safeFileName(file.getOriginalFilename()),
                    contentType,
                    file.getSize(),
                    inputStream
            );
            story.setMediaKind(mediaKind);
            story.setMediaFileName(safeFileName(file.getOriginalFilename()));
            story.setMediaContentType(contentType);
            story.setMediaDurationMs("VIDEO".equals(mediaKind) ? durationMs : null);
            story.setMediaStorageProvider("S3");
            story.setMediaBucketName(reference.bucketName());
            story.setMediaObjectKey(reference.objectKey());
            story.setMediaProcessingStatus("NOT_REQUIRED");
            mediaProcessingService.enqueueStoryPreview(story);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read story media", exception);
        }
    }

    private boolean canViewStory(UUID requesterId, StoryEntity story, UserEntity owner) {
        if (owner == null) {
            return false;
        }
        if (owner.getId().equals(requesterId)) {
            return true;
        }
        String audience = story.getAudience() != null ? story.getAudience() : "DEFAULT";
        return switch (audience) {
            case "DEFAULT" -> canViewUsingAccountPrivacy(requesterId, owner);
            case "EVERYBODY" -> true;
            case "CONTACTS" -> contactRepository.existsByIdOwnerUserIdAndIdContactUserId(owner.getId(), requesterId);
            case "CLOSE_FRIENDS", "CUSTOM" -> deserializeAllowedViewerIds(story.getAllowedViewerUserIds()).contains(requesterId);
            case "NOBODY" -> false;
            default -> true;
        };
    }

    private boolean canViewUsingAccountPrivacy(UUID requesterId, UserEntity owner) {
        String privacy = owner.getStoryPrivacy() != null ? owner.getStoryPrivacy() : "EVERYBODY";
        return switch (privacy) {
            case "EVERYBODY" -> true;
            case "CONTACTS" -> contactRepository.existsByIdOwnerUserIdAndIdContactUserId(owner.getId(), requesterId);
            case "NOBODY" -> false;
            default -> true;
        };
    }

    private Set<UUID> validateAllowedViewerIds(UUID ownerUserId, String audience, List<UUID> allowedViewerUserIds) {
        Set<UUID> viewerIds = allowedViewerUserIds == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(allowedViewerUserIds);
        viewerIds.remove(ownerUserId);
        if (!List.of("CLOSE_FRIENDS", "CUSTOM").contains(audience)) {
            return Set.of();
        }
        if (viewerIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected audience requires at least one contact");
        }
        boolean hasUnknownViewer = viewerIds.stream()
                .anyMatch(viewerId -> !contactRepository.existsByIdOwnerUserIdAndIdContactUserId(ownerUserId, viewerId));
        if (hasUnknownViewer) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Story audience can include contacts only");
        }
        return Set.copyOf(viewerIds);
    }

    private Map<UUID, StoryEntity> loadHighlightStoriesOwnedBy(UUID ownerUserId, List<UUID> storyIds) {
        if (storyIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, StoryEntity> storiesById = storyRepository.findAllById(storyIds).stream()
                .collect(Collectors.toMap(StoryEntity::getId, Function.identity()));
        if (storiesById.size() != storyIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more stories were not found");
        }
        Instant now = Instant.now();
        for (UUID storyId : storyIds) {
            StoryEntity story = storiesById.get(storyId);
            if (!ownerUserId.equals(story.getOwnerUserId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Highlights can include only your own stories");
            }
            if (story.getExpiresAt().isAfter(now)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Highlights can include archived stories only");
            }
        }
        return storiesById;
    }

    private StoryHighlightEntity getOwnedHighlight(UUID requesterId, UUID highlightId) {
        StoryHighlightEntity highlight = storyHighlightRepository.findById(highlightId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Story highlight not found"));
        if (!requesterId.equals(highlight.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only owner can manage story highlights");
        }
        return highlight;
    }

    private void persistHighlightItems(UUID highlightId, List<UUID> storyIds, int initialPosition) {
        if (storyIds.isEmpty()) {
            return;
        }
        List<StoryHighlightItemEntity> items = new ArrayList<>();
        for (int index = 0; index < storyIds.size(); index++) {
            StoryHighlightItemEntity item = new StoryHighlightItemEntity();
            item.setHighlightId(highlightId);
            item.setStoryId(storyIds.get(index));
            item.setPosition(initialPosition + index);
            items.add(item);
        }
        storyHighlightItemRepository.saveAll(items);
    }

    private void reindexHighlightItems(List<StoryHighlightItemEntity> items) {
        boolean changed = false;
        for (int index = 0; index < items.size(); index++) {
            StoryHighlightItemEntity item = items.get(index);
            if (!Objects.equals(item.getPosition(), index)) {
                item.setPosition(index);
                changed = true;
            }
        }
        if (changed) {
            storyHighlightItemRepository.saveAll(items);
        }
    }

    private Map<UUID, UserEntity> getUsers(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    }

    private Map<UUID, Integer> getViewsCountByStoryId(List<UUID> storyIds) {
        return storyIds.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        storyId -> storyViewRepository.findAllByIdStoryId(storyId).size()
                ));
    }

    private UserEntity getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private int countInteractions(List<StoryInteractionEntity> interactions, String type) {
        return (int) interactions.stream()
                .filter(interaction -> type.equals(interaction.getInteractionType()))
                .count();
    }

    private String normalizeReaction(String value) {
        String normalized = value != null ? value.trim() : "";
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reaction is required");
        }
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private String normalizeInteractionText(String value, String message) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeHighlightTitle(String value) {
        String normalized = value != null ? value.trim() : "";
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Highlight title is required");
        }
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private int normalizeHighlightPosition(Integer requestedPosition, int defaultPosition) {
        if (requestedPosition == null) {
            return defaultPosition;
        }
        if (requestedPosition < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Highlight position cannot be negative");
        }
        return requestedPosition;
    }

    private List<UUID> normalizeStoryIds(List<UUID> storyIds) {
        if (storyIds == null || storyIds.isEmpty()) {
            return List.of();
        }
        return storyIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private UUID resolveCoverStoryId(UUID coverStoryId, List<UUID> storyIds) {
        if (coverStoryId == null) {
            return null;
        }
        if (!storyIds.contains(coverStoryId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cover story must be included in highlight stories");
        }
        return coverStoryId;
    }

    private void validateStoryMedia(MultipartFile file, Long durationMs) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Story media is empty");
        }
        if (file.getSize() > maxStoryMediaFileSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Story media is too large");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (contentType.startsWith("video/")) {
            if (durationMs != null && (durationMs <= 0 || durationMs > 10 * 60 * 1000L)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Story video duration is invalid");
            }
            return;
        }
        if (!contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Story media must be an image or video");
        }
    }

    private void deleteStoryMedia(StoryEntity story) {
        if (!"S3".equalsIgnoreCase(story.getMediaStorageProvider())
                || story.getMediaBucketName() == null
                || story.getMediaObjectKey() == null) {
            return;
        }
        mediaService.deleteObject(story.getMediaBucketName(), story.getMediaObjectKey());
        if (story.getMediaPreviewBucketName() != null && story.getMediaPreviewObjectKey() != null) {
            mediaService.deleteObject(story.getMediaPreviewBucketName(), story.getMediaPreviewObjectKey());
        }
    }

    private String resolveStoryPreviewUrl(StoryEntity story) {
        if (story.getMediaPreviewBucketName() != null && story.getMediaPreviewObjectKey() != null) {
            return mediaService.buildDownloadAccess(
                    story.getMediaPreviewBucketName(),
                    story.getMediaPreviewObjectKey()
            ).downloadUrl();
        }
        return mediaService.buildDownloadAccess(story.getMediaBucketName(), story.getMediaObjectKey()).downloadUrl();
    }

    private String normalizeMediaKind(String contentType) {
        return contentType.startsWith("video/") ? "VIDEO" : "IMAGE";
    }

    private String normalizeOptionalText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeAudience(String audience) {
        String normalized = audience != null ? audience.trim().toUpperCase() : "DEFAULT";
        if (normalized.isBlank()) {
            return "DEFAULT";
        }
        if (!List.of("DEFAULT", "EVERYBODY", "CONTACTS", "NOBODY", "CLOSE_FRIENDS", "CUSTOM").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported story audience");
        }
        return normalized;
    }

    private String serializeAllowedViewerIds(Set<UUID> viewerIds) {
        if (viewerIds == null || viewerIds.isEmpty()) {
            return "";
        }
        return viewerIds.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));
    }

    private Set<UUID> deserializeAllowedViewerIds(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Set.of();
        }
        return List.of(rawValue.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(UUID::fromString)
                .collect(Collectors.toSet());
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType.trim().toLowerCase();
    }

    private String safeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "story";
        }
        String normalized = originalFileName.replace("\\", "_").replace("/", "_").trim();
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private String normalizeColor(String color, String fallback) {
        String normalized = color != null ? color.trim() : fallback;
        if (!normalized.matches("^#[0-9a-fA-F]{6}$")) {
            return fallback;
        }
        return normalized;
    }
}
