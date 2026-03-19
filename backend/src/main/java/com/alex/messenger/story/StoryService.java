package com.alex.messenger.story;

import com.alex.messenger.media.MediaObjectReference;
import com.alex.messenger.media.MediaProcessingService;
import com.alex.messenger.media.MediaService;
import com.alex.messenger.media.PresignedMediaAccess;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatMemberRepository;
import com.alex.messenger.chat.ChatRepository;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.story.dto.CreateStoryRequest;
import com.alex.messenger.story.dto.CreateStoryAlbumRequest;
import com.alex.messenger.story.dto.CreateStoryHighlightRequest;
import com.alex.messenger.story.dto.CreateStoryLiveCommentRequest;
import com.alex.messenger.story.dto.GoLiveStoryRequest;
import com.alex.messenger.story.dto.StoryFeedItemResponse;
import com.alex.messenger.story.dto.StoryAlbumResponse;
import com.alex.messenger.story.dto.StoryHighlightResponse;
import com.alex.messenger.story.dto.StoryInteractionResponse;
import com.alex.messenger.story.dto.StoryInteractionEventResponse;
import com.alex.messenger.story.dto.StoryInteractionSummaryResponse;
import com.alex.messenger.story.dto.StoryLiveCommentResponse;
import com.alex.messenger.story.dto.StoryLiveSessionResponse;
import com.alex.messenger.story.dto.StoryMentionRequest;
import com.alex.messenger.story.dto.StoryMediaResponse;
import com.alex.messenger.story.dto.StoryReactionRequest;
import com.alex.messenger.story.dto.StoryReplyRequest;
import com.alex.messenger.story.dto.StoryReshareRequest;
import com.alex.messenger.story.dto.StoryResponse;
import com.alex.messenger.story.dto.StorySurfaceResponse;
import com.alex.messenger.story.dto.StoryViewerResponse;
import com.alex.messenger.story.dto.UpdateStoryHighlightStoriesRequest;
import com.alex.messenger.user.ContactRepository;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserPrivacyService;
import com.alex.messenger.user.UserRepository;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private final StoryAlbumRepository storyAlbumRepository;
    private final StoryAlbumItemRepository storyAlbumItemRepository;
    private final StoryLiveSessionRepository storyLiveSessionRepository;
    private final StoryLiveCommentRepository storyLiveCommentRepository;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatService chatService;
    private final UserRepository userRepository;
    private final ContactRepository contactRepository;
    private final UserPrivacyService userPrivacyService;
    private final MediaService mediaService;
    private final MediaProcessingService mediaProcessingService;
    private final StoryInteractionNotificationService storyInteractionNotificationService;

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

        Map<UUID, UserEntity> usersById = getUsers(stories.stream()
                .map(StoryEntity::getOwnerUserId)
                .filter(Objects::nonNull)
                .toList());
        Map<UUID, ChatEntity> chatsById = getChats(stories.stream()
                .map(StoryEntity::getOwnerChatId)
                .filter(Objects::nonNull)
                .toList());
        List<StoryEntity> visibleStories = stories.stream()
                .filter(story -> canViewStory(
                        requesterId,
                        story,
                        usersById.get(story.getOwnerUserId()),
                        chatsById.get(story.getOwnerChatId())
                ))
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

        Map<String, List<StoryResponse>> grouped = visibleStories.stream()
                .map(story -> {
                    UserEntity owner = usersById.get(story.getOwnerUserId());
                    ChatEntity ownerChat = chatsById.get(story.getOwnerChatId());
                    boolean own = isOwnStory(requesterId, story);
                    boolean viewed = own || viewedStoryIds.contains(story.getId());
                    return toResponse(
                            story,
                            owner,
                            ownerChat,
                            viewed,
                            own,
                            viewsCountByStoryId.getOrDefault(story.getId(), 0)
                    );
                })
                .collect(Collectors.groupingBy(this::resolveStoryFeedOwnerKey));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<StoryResponse> ownerStories = entry.getValue().stream()
                            .sorted(Comparator.comparing(StoryResponse::createdAt))
                            .toList();
                    StoryResponse latest = ownerStories.get(ownerStories.size() - 1);
                    boolean hasUnviewed = ownerStories.stream().anyMatch(story -> !story.viewed());
                    return new StoryFeedItemResponse(
                            latest.ownerUserId(),
                            latest.ownerChatId(),
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
    public StorySurfaceResponse getUserSurface(UUID requesterId, UUID ownerUserId) {
        UserEntity owner = getUser(ownerUserId);
        List<StoryEntity> activeStories = storyRepository
                .findAllByOwnerUserIdAndExpiresAtAfterOrderByCreatedAtDesc(ownerUserId, Instant.now())
                .stream()
                .filter(story -> story.getOwnerChatId() == null)
                .filter(story -> canViewStory(requesterId, story, owner, null))
                .toList();
        List<StoryResponse> storyResponses = buildStoryResponses(activeStories, requesterId);
        List<StoryAlbumResponse> albums = storyAlbumRepository
                .findAllByOwnerUserIdOrderByPositionAscCreatedAtAsc(ownerUserId)
                .stream()
                .map(album -> toAlbumResponse(album, requesterId))
                .filter(album -> !album.stories().isEmpty() || ownerUserId.equals(requesterId))
                .toList();
        List<UUID> activeLiveStoryIds = listActiveLiveStoryIds(activeStories);
        return new StorySurfaceResponse(
                ownerUserId,
                null,
                "USER",
                owner.getDisplayName(),
                owner.getUsername(),
                ownerUserId.equals(requesterId),
                "EVERYBODY".equalsIgnoreCase(owner.getStoryPrivacy()),
                storyResponses.size(),
                activeLiveStoryIds.size(),
                albums.size(),
                activeLiveStoryIds,
                storyResponses,
                albums
        );
    }

    @Transactional(readOnly = true)
    public StorySurfaceResponse getChannelSurface(UUID requesterId, UUID chatId) {
        ChatEntity channel = getChat(chatId);
        if (!"CHANNEL".equals(channel.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Story surface is available only for channels");
        }
        if (!canViewChannelStory(requesterId, channel)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Story access denied");
        }
        List<StoryEntity> activeStories = storyRepository
                .findAllByOwnerChatIdAndExpiresAtAfterOrderByCreatedAtDesc(chatId, Instant.now());
        List<StoryResponse> storyResponses = buildStoryResponses(activeStories, requesterId);
        List<StoryAlbumResponse> albums = storyAlbumRepository
                .findAllByOwnerChatIdOrderByPositionAscCreatedAtAsc(chatId)
                .stream()
                .map(album -> toAlbumResponse(album, requesterId))
                .filter(album -> !album.stories().isEmpty() || canManageChannelStories(requesterId, chatId))
                .toList();
        List<UUID> activeLiveStoryIds = listActiveLiveStoryIds(activeStories);
        return new StorySurfaceResponse(
                null,
                chatId,
                "CHANNEL",
                channel.getTitle() != null ? channel.getTitle() : "Channel",
                channel.getPublicUsername(),
                canManageChannelStories(requesterId, chatId),
                channel.getPublicUsername() != null && !channel.getPublicUsername().isBlank(),
                storyResponses.size(),
                activeLiveStoryIds.size(),
                albums.size(),
                activeLiveStoryIds,
                storyResponses,
                albums
        );
    }

    @Transactional(readOnly = true)
    public List<StoryResponse> listArchive(UUID requesterId, UUID ownerChatId) {
        StoryOwnerScope ownerScope = resolveOwnerScope(requesterId, ownerChatId);
        List<StoryEntity> stories = ownerScope.ownerChat() != null
                ? storyRepository.findAllByOwnerChatIdOrderByCreatedAtDesc(ownerScope.ownerChat().getId())
                : storyRepository.findAllByOwnerUserIdOrderByCreatedAtDesc(ownerScope.ownerUser().getId());
        if (stories.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        Map<UUID, Integer> viewsCountByStoryId = getViewsCountByStoryId(stories.stream().map(StoryEntity::getId).toList());
        return stories.stream()
                .filter(story -> !story.getExpiresAt().isAfter(now))
                .map(story -> toResponse(
                        story,
                        ownerScope.ownerUser(),
                        ownerScope.ownerChat(),
                        ownerScope.viewerOwnsSurface(),
                        ownerScope.viewerOwnsSurface(),
                        viewsCountByStoryId.getOrDefault(story.getId(), 0)
                ))
                .toList();
    }

    @Transactional
    public StoryResponse markViewed(UUID requesterId, UUID storyId) {
        StoryEntity story = getAccessibleStory(requesterId, storyId);
        UserEntity owner = story.getOwnerUserId() != null ? getUser(story.getOwnerUserId()) : null;
        ChatEntity ownerChat = story.getOwnerChatId() != null ? getChat(story.getOwnerChatId()) : null;
        if (!isOwnStory(requesterId, story)) {
            StoryViewId id = new StoryViewId(storyId, requesterId);
            if (!storyViewRepository.existsById(id)) {
                StoryViewEntity view = new StoryViewEntity();
                view.setId(id);
                storyViewRepository.save(view);
            }
        }
        int viewsCount = storyViewRepository.findAllByIdStoryId(storyId).size();
        return toResponse(story, owner, ownerChat, true, isOwnStory(requesterId, story), viewsCount);
    }

    @Transactional
    public void delete(UUID requesterId, UUID storyId) {
        StoryEntity story = getOwnedStory(requesterId, storyId);
        deleteStoryMedia(story);
        storyRepository.delete(story);
    }

    @Transactional(readOnly = true)
    public List<StoryViewerResponse> listViewers(UUID requesterId, UUID storyId) {
        StoryEntity story = getOwnedStory(requesterId, storyId);

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

    @Transactional
    public List<StoryInteractionResponse> listInteractions(UUID requesterId, UUID storyId) {
        StoryEntity story = getOwnedStory(requesterId, storyId);
        List<StoryInteractionEntity> interactions = storyInteractionRepository.findAllByStoryIdOrderByCreatedAtDesc(storyId);
        if (interactions == null) {
            interactions = List.of();
        }
        Map<UUID, UserEntity> usersById = getUsers(interactions.stream()
                .flatMap(interaction -> java.util.stream.Stream.of(
                        interaction.getActorUserId(),
                        interaction.getTargetUserId()
                ))
                .filter(Objects::nonNull)
                .toList());
        List<StoryInteractionResponse> responses = interactions.stream()
                .map(interaction -> toInteractionResponse(interaction, usersById))
                .toList();
        markInteractionsSeen(story);
        return responses;
    }

    @Transactional(readOnly = true)
    public StoryInteractionSummaryResponse getInteractionSummary(UUID requesterId, UUID storyId) {
        getOwnedStory(requesterId, storyId);
        return buildInteractionSummary(storyId, requesterId);
    }

    @Transactional
    public StoryInteractionResponse react(UUID requesterId, UUID storyId, StoryReactionRequest request) {
        StoryEntity story = getAccessibleStory(requesterId, storyId);
        UUID targetUserId = resolveStoryInteractionTargetUserId(story);
        StoryInteractionEntity interaction = storyInteractionRepository
                .findFirstByStoryIdAndActorUserIdAndInteractionType(story.getId(), requesterId, "REACTION")
                .orElseGet(StoryInteractionEntity::new);
        interaction.setStoryId(story.getId());
        interaction.setActorUserId(requesterId);
        interaction.setTargetUserId(targetUserId);
        interaction.setInteractionType("REACTION");
        interaction.setReactionCode(normalizeReaction(request.reaction()));
        interaction.setMessageText(null);
        interaction.setSeenAt(resolveInitialSeenAt(story, requesterId, "REACTION"));
        StoryInteractionEntity saved = storyInteractionRepository.save(interaction);
        StoryInteractionResponse response = toInteractionResponse(saved, getUsers(nonNullUserIds(requesterId, targetUserId)));
        publishInteractionEvent(story, response, "INTERACTION_UPSERT");
        return response;
    }

    @Transactional
    public void removeReaction(UUID requesterId, UUID storyId) {
        StoryEntity story = getAccessibleStory(requesterId, storyId);
        long removed = storyInteractionRepository.deleteByStoryIdAndActorUserIdAndInteractionType(storyId, requesterId, "REACTION");
        if (removed > 0) {
            publishInteractionEvent(story, null, "INTERACTION_REMOVED");
        }
    }

    @Transactional
    public StoryInteractionResponse reply(UUID requesterId, UUID storyId, StoryReplyRequest request) {
        StoryEntity story = getAccessibleStory(requesterId, storyId);
        UUID targetUserId = resolveStoryInteractionTargetUserId(story);
        StoryInteractionEntity interaction = new StoryInteractionEntity();
        interaction.setStoryId(story.getId());
        interaction.setActorUserId(requesterId);
        interaction.setTargetUserId(targetUserId);
        interaction.setInteractionType("REPLY");
        interaction.setMessageText(normalizeInteractionText(request.message(), "Reply message is required"));
        interaction.setSeenAt(resolveInitialSeenAt(story, requesterId, "REPLY"));
        StoryInteractionEntity saved = storyInteractionRepository.save(interaction);
        StoryInteractionResponse response = toInteractionResponse(saved, getUsers(nonNullUserIds(requesterId, targetUserId)));
        publishInteractionEvent(story, response, "INTERACTION_UPSERT");
        return response;
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
        interaction.setSeenAt(resolveInitialSeenAt(story, requesterId, "MENTION"));
        StoryInteractionEntity saved = storyInteractionRepository.save(interaction);
        StoryInteractionResponse response = toInteractionResponse(saved, getUsers(List.of(requesterId, targetUser.getId())));
        publishInteractionEvent(story, response, "INTERACTION_UPSERT");
        return response;
    }

    @Transactional
    public StoryInteractionResponse reshare(UUID requesterId, UUID storyId, StoryReshareRequest request) {
        StoryEntity story = getAccessibleStory(requesterId, storyId);
        UUID targetUserId = resolveStoryInteractionTargetUserId(story);
        StoryInteractionEntity interaction = new StoryInteractionEntity();
        interaction.setStoryId(story.getId());
        interaction.setActorUserId(requesterId);
        interaction.setTargetUserId(targetUserId);
        interaction.setInteractionType("RESHARE");
        interaction.setMessageText(normalizeOptionalText(request.note()));
        interaction.setSeenAt(resolveInitialSeenAt(story, requesterId, "RESHARE"));
        StoryInteractionEntity saved = storyInteractionRepository.save(interaction);
        StoryInteractionResponse response = toInteractionResponse(saved, getUsers(nonNullUserIds(requesterId, targetUserId)));
        publishInteractionEvent(story, response, "INTERACTION_UPSERT");
        return response;
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
        List<UUID> storyIds = normalizeRequiredStoryIds(
                request.storyIds(),
                "Story highlight must include at least one story"
        );
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
        if (requestedStoryIds.isEmpty() && request.coverStoryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide storyIds or coverStoryId");
        }
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

    @Transactional(readOnly = true)
    public List<StoryAlbumResponse> listAlbums(UUID requesterId, UUID ownerUserId, UUID ownerChatId) {
        StoryOwnerScope ownerScope = resolveOwnerScopeForAlbums(requesterId, ownerUserId, ownerChatId);
        List<StoryAlbumEntity> albums = ownerScope.ownerChat() != null
                ? storyAlbumRepository.findAllByOwnerChatIdOrderByPositionAscCreatedAtAsc(ownerScope.ownerChat().getId())
                : storyAlbumRepository.findAllByOwnerUserIdOrderByPositionAscCreatedAtAsc(ownerScope.ownerUser().getId());
        return albums.stream()
                .map(album -> toAlbumResponse(album, requesterId))
                .filter(response -> !response.stories().isEmpty() || ownerScope.viewerOwnsSurface())
                .toList();
    }

    @Transactional
    public StoryAlbumResponse createAlbum(UUID requesterId, CreateStoryAlbumRequest request) {
        List<UUID> storyIds = normalizeRequiredStoryIds(request.storyIds(), "Story album must include at least one story");
        StoryOwnerScope ownerScope = resolveChannelOrUserOwner(requesterId, request.ownerChatId());
        loadAlbumStoriesOwnedBy(ownerScope, storyIds);
        int nextPosition = ownerScope.ownerChat() != null
                ? storyAlbumRepository.findAllByOwnerChatIdOrderByPositionAscCreatedAtAsc(ownerScope.ownerChat().getId()).size()
                : storyAlbumRepository.findAllByOwnerUserIdOrderByPositionAscCreatedAtAsc(requesterId).size();

        StoryAlbumEntity album = new StoryAlbumEntity();
        album.setOwnerUserId(requesterId);
        album.setOwnerChatId(ownerScope.ownerChat() != null ? ownerScope.ownerChat().getId() : null);
        album.setTitle(normalizeAlbumTitle(request.title()));
        album.setPosition(normalizeAlbumPosition(request.position(), nextPosition));
        album.setCoverStoryId(resolveCoverStoryId(request.coverStoryId(), storyIds));
        StoryAlbumEntity savedAlbum = storyAlbumRepository.save(album);

        persistAlbumItems(savedAlbum.getId(), storyIds, 0);
        if (savedAlbum.getCoverStoryId() == null) {
            savedAlbum.setCoverStoryId(storyIds.get(0));
            savedAlbum = storyAlbumRepository.save(savedAlbum);
        }
        return toAlbumResponse(savedAlbum, requesterId);
    }

    @Transactional(readOnly = true)
    public StoryLiveSessionResponse getLiveSession(UUID requesterId, UUID storyId) {
        StoryEntity story = getAccessibleStory(requesterId, storyId);
        return toLiveSessionResponse(getActiveLiveSession(story.getId()));
    }

    @Transactional
    public StoryLiveSessionResponse goLive(UUID requesterId, UUID storyId, GoLiveStoryRequest request) {
        StoryEntity story = getOwnedActiveStory(requesterId, storyId);
        storyLiveSessionRepository.findFirstByStoryIdAndStatusOrderByStartedAtDesc(storyId, "ACTIVE")
                .ifPresent(ignored -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Story already has an active live session");
                });

        StoryLiveSessionEntity session = new StoryLiveSessionEntity();
        session.setStoryId(story.getId());
        session.setOwnerUserId(requesterId);
        session.setStatus("ACTIVE");
        session.setDonationsEnabled(request != null && Boolean.TRUE.equals(request.donationsEnabled()));
        session.setDonationProvider(session.isDonationsEnabled()
                ? normalizeDonationProvider(request != null ? request.donationProvider() : null)
                : null);
        session.setDonationCurrency(session.isDonationsEnabled()
                ? normalizeDonationCurrency(request != null ? request.donationCurrency() : null, false)
                : null);
        session.setDonationEventHookUrl(session.isDonationsEnabled()
                ? normalizeDonationEventHookUrl(request != null ? request.donationEventHookUrl() : null)
                : null);
        session.setDonationEventsCount(0L);
        session.setDonationsTotalMinor(0L);
        return toLiveSessionResponse(storyLiveSessionRepository.save(session));
    }

    @Transactional
    public StoryLiveSessionResponse endLive(UUID requesterId, UUID storyId) {
        StoryEntity story = getOwnedActiveStory(requesterId, storyId);
        StoryLiveSessionEntity session = getActiveLiveSession(story.getId());
        if (story.getOwnerChatId() == null && !requesterId.equals(session.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only owner can end a live story");
        }
        session.setStatus("ENDED");
        session.setEndedAt(Instant.now());
        return toLiveSessionResponse(storyLiveSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public List<StoryLiveCommentResponse> listLiveComments(UUID requesterId, UUID storyId) {
        StoryLiveSessionEntity session = getAccessibleActiveLiveSession(requesterId, storyId);
        List<StoryLiveCommentEntity> comments = storyLiveCommentRepository
                .findAllByLiveSessionIdOrderByCreatedAtAsc(session.getId());
        Map<UUID, UserEntity> usersById = getUsers(comments.stream()
                .map(StoryLiveCommentEntity::getAuthorUserId)
                .toList());
        return comments.stream()
                .map(comment -> toLiveCommentResponse(comment, usersById))
                .toList();
    }

    @Transactional
    public StoryLiveCommentResponse commentLive(UUID requesterId, UUID storyId, CreateStoryLiveCommentRequest request) {
        StoryLiveSessionEntity session = getAccessibleActiveLiveSession(requesterId, storyId);
        String normalizedMessage = normalizeOptionalText(request.message());
        Long donationAmountMinor = normalizeDonationAmount(request.donationAmountMinor());
        if (normalizedMessage == null && donationAmountMinor == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Live story comment must include text or donation");
        }
        if (donationAmountMinor != null && !session.isDonationsEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Live story donations are disabled");
        }
        String requestDonationCurrency = normalizeOptionalText(request.donationCurrency());
        if (donationAmountMinor == null && requestDonationCurrency != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Donation currency requires donation amount");
        }
        String donationCurrency = donationAmountMinor != null
                ? resolveCommentDonationCurrency(session, requestDonationCurrency)
                : null;

        StoryLiveCommentEntity comment = new StoryLiveCommentEntity();
        comment.setStoryId(storyId);
        comment.setLiveSessionId(session.getId());
        comment.setAuthorUserId(requesterId);
        comment.setMessageText(normalizedMessage);
        comment.setDonationAmountMinor(donationAmountMinor);
        comment.setDonationCurrency(donationCurrency);
        StoryLiveCommentEntity savedComment = storyLiveCommentRepository.save(comment);

        if (donationAmountMinor != null) {
            session.setDonationEventsCount(session.getDonationEventsCount() + 1);
            session.setDonationsTotalMinor(session.getDonationsTotalMinor() + donationAmountMinor);
            storyLiveSessionRepository.save(session);
        }

        return toLiveCommentResponse(savedComment, getUsers(List.of(requesterId)));
    }

    private StoryEntity getAccessibleStory(UUID requesterId, UUID storyId) {
        StoryEntity story = storyRepository.findByIdAndExpiresAtAfter(storyId, Instant.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Story not found"));
        UserEntity owner = story.getOwnerUserId() != null ? getUser(story.getOwnerUserId()) : null;
        ChatEntity ownerChat = story.getOwnerChatId() != null ? getChat(story.getOwnerChatId()) : null;
        if (canViewStory(requesterId, story, owner, ownerChat)) {
            return story;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Story access denied");
    }

    private StoryEntity getOwnedStory(UUID requesterId, UUID storyId) {
        StoryEntity story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Story not found"));
        if (!isOwnStory(requesterId, story)) {
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
        StoryOwnerScope ownerScope = resolveChannelOrUserOwner(requesterId, request.ownerChatId());
        String normalizedText = normalizeStoryText(request.text());
        if ((normalizedText == null || normalizedText.isBlank()) && (file == null || file.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Story must include text or media");
        }

        String audience = ownerScope.ownerChat() != null ? "EVERYBODY" : normalizeAudience(request.audience());
        Set<UUID> allowedViewerIds = ownerScope.ownerChat() != null
                ? Set.of()
                : validateAllowedViewerIds(requesterId, audience, request.allowedViewerUserIds());

        StoryEntity story = new StoryEntity();
        story.setOwnerUserId(requesterId);
        story.setOwnerChatId(ownerScope.ownerChat() != null ? ownerScope.ownerChat().getId() : null);
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
        return toResponse(saved, ownerScope.ownerUser(), ownerScope.ownerChat(), false, true, 0);
    }

    private StoryResponse toResponse(
            StoryEntity story,
            UserEntity owner,
            ChatEntity ownerChat,
            boolean viewed,
            boolean own,
            int viewsCount
    ) {
        Instant now = Instant.now();
        return new StoryResponse(
                story.getId(),
                story.getOwnerChatId() != null ? null : story.getOwnerUserId(),
                story.getOwnerChatId(),
                ownerChat != null
                        ? (ownerChat.getTitle() != null ? ownerChat.getTitle() : "Channel")
                        : owner != null ? owner.getDisplayName() : "Unknown",
                ownerChat != null ? ownerChat.getPublicUsername() : owner != null ? owner.getUsername() : null,
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
                .filter(story -> highlight.getOwnerUserId().equals(requesterId)
                        || canViewStory(requesterId, story, owner, null))
                .map(story -> toResponse(
                        story,
                        owner,
                        null,
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

    private StoryAlbumResponse toAlbumResponse(StoryAlbumEntity album, UUID requesterId) {
        UserEntity owner = album.getOwnerChatId() == null ? getUser(album.getOwnerUserId()) : null;
        ChatEntity ownerChat = album.getOwnerChatId() != null ? getChat(album.getOwnerChatId()) : null;
        List<StoryAlbumItemEntity> items = storyAlbumItemRepository
                .findAllByAlbumIdOrderByPositionAscCreatedAtAsc(album.getId());
        if (items.isEmpty()) {
            return new StoryAlbumResponse(
                    album.getId(),
                    album.getOwnerChatId() == null ? album.getOwnerUserId() : null,
                    album.getOwnerChatId(),
                    album.getTitle(),
                    album.getCoverStoryId(),
                    album.getPosition(),
                    album.getCreatedAt(),
                    album.getUpdatedAt(),
                    0,
                    List.of()
            );
        }

        List<UUID> storyIds = items.stream().map(StoryAlbumItemEntity::getStoryId).toList();
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
                .filter(story -> isAlbumOwnedByRequester(album, requesterId)
                        || canViewStory(requesterId, story, owner, ownerChat))
                .map(story -> toResponse(
                        story,
                        owner,
                        ownerChat,
                        isAlbumOwnedByRequester(album, requesterId) || viewedStoryIds.contains(story.getId()),
                        isAlbumOwnedByRequester(album, requesterId),
                        viewsCountByStoryId.getOrDefault(story.getId(), 0)
                ))
                .toList();

        UUID coverStoryId = album.getCoverStoryId();
        UUID requestedCoverStoryId = coverStoryId;
        if (requestedCoverStoryId != null
                && stories.stream().noneMatch(story -> requestedCoverStoryId.equals(story.storyId()))) {
            coverStoryId = stories.isEmpty() ? null : stories.get(0).storyId();
        }
        return new StoryAlbumResponse(
                album.getId(),
                album.getOwnerChatId() == null ? album.getOwnerUserId() : null,
                album.getOwnerChatId(),
                album.getTitle(),
                coverStoryId,
                album.getPosition(),
                album.getCreatedAt(),
                album.getUpdatedAt(),
                stories.size(),
                stories
        );
    }

    private StoryLiveSessionResponse toLiveSessionResponse(StoryLiveSessionEntity session) {
        StoryEntity story = storyRepository.findById(session.getStoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Story not found"));
        return new StoryLiveSessionResponse(
                session.getId(),
                session.getStoryId(),
                story.getOwnerChatId() == null ? session.getOwnerUserId() : null,
                story.getOwnerChatId(),
                session.getStatus(),
                session.isDonationsEnabled(),
                session.getDonationProvider(),
                session.getDonationCurrency(),
                session.getDonationEventHookUrl(),
                session.getDonationEventsCount(),
                session.getDonationsTotalMinor(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    private StoryLiveCommentResponse toLiveCommentResponse(
            StoryLiveCommentEntity comment,
            Map<UUID, UserEntity> usersById
    ) {
        UserEntity author = usersById.get(comment.getAuthorUserId());
        return new StoryLiveCommentResponse(
                comment.getId(),
                comment.getStoryId(),
                comment.getLiveSessionId(),
                comment.getAuthorUserId(),
                author != null ? author.getDisplayName() : "Unknown",
                author != null ? author.getUsername() : null,
                comment.getMessageText(),
                comment.getDonationAmountMinor(),
                comment.getDonationCurrency(),
                comment.getCreatedAt()
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

    private boolean canViewStory(UUID requesterId, StoryEntity story, UserEntity owner, ChatEntity ownerChat) {
        if (ownerChat != null) {
            return canViewChannelStory(requesterId, ownerChat);
        }
        if (owner == null) {
            return false;
        }
        if (owner.getId().equals(requesterId)) {
            return true;
        }
        String audience = story.getAudience() != null ? story.getAudience() : "DEFAULT";
        return switch (audience) {
            case "DEFAULT" -> userPrivacyService.canViewStory(requesterId, owner);
            case "EVERYBODY" -> true;
            case "CONTACTS" -> contactRepository.existsByIdOwnerUserIdAndIdContactUserId(owner.getId(), requesterId);
            case "CLOSE_FRIENDS" -> {
                Set<UUID> configuredAudience = deserializeAllowedViewerIds(story.getAllowedViewerUserIds());
                yield configuredAudience.isEmpty()
                        ? userPrivacyService.isCloseFriend(owner.getId(), requesterId)
                        : configuredAudience.contains(requesterId);
            }
            case "CUSTOM" -> deserializeAllowedViewerIds(story.getAllowedViewerUserIds()).contains(requesterId);
            case "NOBODY" -> false;
            default -> true;
        };
    }

    private boolean canViewChannelStory(UUID requesterId, ChatEntity ownerChat) {
        if (!"CHANNEL".equals(ownerChat.getChatType())) {
            return false;
        }
        if (requesterId != null && chatMemberRepository.existsByIdChatIdAndIdUserId(ownerChat.getId(), requesterId)) {
            return true;
        }
        return ownerChat.getPublicUsername() != null && !ownerChat.getPublicUsername().isBlank();
    }

    private Set<UUID> validateAllowedViewerIds(UUID ownerUserId, String audience, List<UUID> allowedViewerUserIds) {
        Set<UUID> viewerIds = allowedViewerUserIds == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(allowedViewerUserIds);
        viewerIds.remove(ownerUserId);
        if (!List.of("CLOSE_FRIENDS", "CUSTOM").contains(audience)) {
            return Set.of();
        }
        if ("CLOSE_FRIENDS".equals(audience) && viewerIds.isEmpty()) {
            viewerIds.addAll(userPrivacyService.getCloseFriendIds(ownerUserId));
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

    private Map<UUID, StoryEntity> loadAlbumStoriesOwnedBy(StoryOwnerScope ownerScope, List<UUID> storyIds) {
        if (storyIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, StoryEntity> storiesById = storyRepository.findAllById(storyIds).stream()
                .collect(Collectors.toMap(StoryEntity::getId, Function.identity()));
        if (storiesById.size() != storyIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more stories were not found");
        }
        for (UUID storyId : storyIds) {
            StoryEntity story = storiesById.get(storyId);
            if (ownerScope.ownerChat() != null) {
                if (!ownerScope.ownerChat().getId().equals(story.getOwnerChatId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Albums can include only stories from the selected channel");
                }
            } else if (!ownerScope.ownerUser().getId().equals(story.getOwnerUserId()) || story.getOwnerChatId() != null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Albums can include only your own stories");
            }
        }
        return storiesById;
    }

    private StoryLiveSessionEntity getActiveLiveSession(UUID storyId) {
        return storyLiveSessionRepository.findFirstByStoryIdAndStatusOrderByStartedAtDesc(storyId, "ACTIVE")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Live story session not found"));
    }

    private StoryLiveSessionEntity getAccessibleActiveLiveSession(UUID requesterId, UUID storyId) {
        StoryEntity story = getAccessibleStory(requesterId, storyId);
        return getActiveLiveSession(story.getId());
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

    private void persistAlbumItems(UUID albumId, List<UUID> storyIds, int initialPosition) {
        if (storyIds.isEmpty()) {
            return;
        }
        List<StoryAlbumItemEntity> items = new ArrayList<>();
        for (int index = 0; index < storyIds.size(); index++) {
            StoryAlbumItemEntity item = new StoryAlbumItemEntity();
            item.setAlbumId(albumId);
            item.setStoryId(storyIds.get(index));
            item.setPosition(initialPosition + index);
            items.add(item);
        }
        storyAlbumItemRepository.saveAll(items);
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

    private Map<UUID, ChatEntity> getChats(Collection<UUID> chatIds) {
        if (chatIds.isEmpty()) {
            return Map.of();
        }
        return chatRepository.findAllById(chatIds).stream()
                .collect(Collectors.toMap(ChatEntity::getId, Function.identity()));
    }

    private List<StoryResponse> buildStoryResponses(List<StoryEntity> stories, UUID requesterId) {
        if (stories.isEmpty()) {
            return List.of();
        }
        Map<UUID, UserEntity> usersById = getUsers(stories.stream()
                .map(StoryEntity::getOwnerUserId)
                .filter(Objects::nonNull)
                .toList());
        Map<UUID, ChatEntity> chatsById = getChats(stories.stream()
                .map(StoryEntity::getOwnerChatId)
                .filter(Objects::nonNull)
                .toList());
        Set<UUID> viewedStoryIds = requesterId != null
                ? storyViewRepository.findAllByIdViewerUserIdAndIdStoryIdIn(
                        requesterId,
                        stories.stream().map(StoryEntity::getId).toList()
                ).stream().map(view -> view.getId().getStoryId()).collect(Collectors.toSet())
                : Set.of();
        Map<UUID, Integer> viewsCountByStoryId = getViewsCountByStoryId(stories.stream().map(StoryEntity::getId).toList());
        return stories.stream()
                .map(story -> {
                    boolean own = isOwnStory(requesterId, story);
                    return toResponse(
                            story,
                            usersById.get(story.getOwnerUserId()),
                            chatsById.get(story.getOwnerChatId()),
                            own || viewedStoryIds.contains(story.getId()),
                            own,
                            viewsCountByStoryId.getOrDefault(story.getId(), 0)
                    );
                })
                .toList();
    }

    private List<UUID> listActiveLiveStoryIds(List<StoryEntity> stories) {
        if (stories.isEmpty()) {
            return List.of();
        }
        Set<UUID> storyIds = stories.stream().map(StoryEntity::getId).collect(Collectors.toSet());
        return storyLiveSessionRepository.findAllByStoryIdInAndStatus(storyIds, "ACTIVE").stream()
                .map(StoryLiveSessionEntity::getStoryId)
                .distinct()
                .toList();
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

    private ChatEntity getChat(UUID chatId) {
        return chatRepository.findById(chatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found"));
    }

    private StoryOwnerScope resolveChannelOrUserOwner(UUID requesterId, UUID ownerChatId) {
        UserEntity ownerUser = getUser(requesterId);
        if (ownerChatId == null) {
            return new StoryOwnerScope(ownerUser, null, true);
        }
        ChatEntity ownerChat = requireChannelStoryOwnership(requesterId, ownerChatId);
        return new StoryOwnerScope(ownerUser, ownerChat, true);
    }

    private StoryOwnerScope resolveOwnerScope(UUID requesterId, UUID ownerChatId) {
        if (ownerChatId == null) {
            return new StoryOwnerScope(getUser(requesterId), null, true);
        }
        ChatEntity ownerChat = getChat(ownerChatId);
        if (!canViewChannelStory(requesterId, ownerChat)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Story access denied");
        }
        return new StoryOwnerScope(null, ownerChat, canManageChannelStories(requesterId, ownerChatId));
    }

    private StoryOwnerScope resolveOwnerScopeForAlbums(UUID requesterId, UUID ownerUserId, UUID ownerChatId) {
        if (ownerChatId != null) {
            ChatEntity ownerChat = getChat(ownerChatId);
            if (!canViewChannelStory(requesterId, ownerChat)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Story access denied");
            }
            return new StoryOwnerScope(null, ownerChat, canManageChannelStories(requesterId, ownerChatId));
        }
        UUID effectiveOwnerUserId = ownerUserId != null ? ownerUserId : requesterId;
        return new StoryOwnerScope(getUser(effectiveOwnerUserId), null, effectiveOwnerUserId.equals(requesterId));
    }

    private ChatEntity requireChannelStoryOwnership(UUID requesterId, UUID chatId) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        if (!"CHANNEL".equals(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Channel stories are available only for channels");
        }
        chatService.ensureCanPost(chat, requesterId);
        return chat;
    }

    private boolean canManageChannelStories(UUID requesterId, UUID chatId) {
        if (requesterId == null) {
            return false;
        }
        try {
            requireChannelStoryOwnership(requesterId, chatId);
            return true;
        } catch (ResponseStatusException exception) {
            return false;
        }
    }

    private boolean isOwnStory(UUID requesterId, StoryEntity story) {
        if (requesterId == null) {
            return false;
        }
        if (story.getOwnerChatId() != null) {
            return canManageChannelStories(requesterId, story.getOwnerChatId());
        }
        return requesterId.equals(story.getOwnerUserId());
    }

    private boolean isAlbumOwnedByRequester(StoryAlbumEntity album, UUID requesterId) {
        if (requesterId == null) {
            return false;
        }
        if (album.getOwnerChatId() != null) {
            return canManageChannelStories(requesterId, album.getOwnerChatId());
        }
        return requesterId.equals(album.getOwnerUserId());
    }

    private String resolveStoryFeedOwnerKey(StoryResponse story) {
        if (story.ownerChatId() != null) {
            return "chat:" + story.ownerChatId();
        }
        return "user:" + story.ownerUserId();
    }

    private int countInteractions(List<StoryInteractionEntity> interactions, String type) {
        return (int) interactions.stream()
                .filter(interaction -> type.equals(interaction.getInteractionType()))
                .count();
    }

    private StoryInteractionSummaryResponse buildInteractionSummary(UUID storyId, UUID viewerUserId) {
        List<StoryInteractionEntity> interactions = storyInteractionRepository.findAllByStoryIdOrderByCreatedAtDesc(storyId);
        if (interactions == null) {
            interactions = List.of();
        }
        String viewerReaction = storyInteractionRepository
                .findFirstByStoryIdAndActorUserIdAndInteractionType(storyId, viewerUserId, "REACTION")
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

    private void publishInteractionEvent(
            StoryEntity story,
            StoryInteractionResponse interaction,
            String eventType
    ) {
        if (story.getOwnerUserId() == null) {
            return;
        }
        StoryInteractionSummaryResponse summary = buildInteractionSummary(story.getId(), story.getOwnerUserId());
        StoryInteractionEventResponse event = new StoryInteractionEventResponse(
                eventType,
                story.getId(),
                story.getOwnerUserId(),
                story.getOwnerChatId(),
                interaction,
                summary,
                countUnreadInteractionsForOwner(story.getOwnerUserId()),
                countUnreadInteractions(story.getId())
        );
        storyInteractionNotificationService.publish(story.getOwnerUserId(), event);
    }

    private void markInteractionsSeen(StoryEntity story) {
        if (story == null || story.getOwnerUserId() == null) {
            return;
        }
        if (storyInteractionRepository.markSeenByStoryId(story.getId(), Instant.now()) > 0) {
            publishInteractionEvent(story, null, "INTERACTIONS_SEEN");
        }
    }

    private int countUnreadInteractions(UUID storyId) {
        return Math.toIntExact(storyInteractionRepository.countByStoryIdAndSeenAtIsNull(storyId));
    }

    private int countUnreadInteractionsForOwner(UUID ownerUserId) {
        List<StoryEntity> stories = storyRepository.findAllByOwnerUserIdAndExpiresAtAfterOrderByCreatedAtDesc(
                ownerUserId,
                Instant.now()
        );
        if (stories == null || stories.isEmpty()) {
            return 0;
        }
        List<UUID> storyIds = stories.stream().map(StoryEntity::getId).toList();
        if (storyIds.isEmpty()) {
            return 0;
        }
        return Math.toIntExact(storyInteractionRepository.countByStoryIdInAndSeenAtIsNull(storyIds));
    }

    private Instant resolveInitialSeenAt(StoryEntity story, UUID actorUserId, String interactionType) {
        if (story == null || story.getOwnerUserId() == null) {
            return Instant.now();
        }
        if (story.getOwnerUserId().equals(actorUserId) || "MENTION".equals(interactionType)) {
            return Instant.now();
        }
        return null;
    }

    private UUID resolveStoryInteractionTargetUserId(StoryEntity story) {
        return story.getOwnerChatId() == null ? story.getOwnerUserId() : null;
    }

    private List<UUID> nonNullUserIds(UUID... userIds) {
        if (userIds == null || userIds.length == 0) {
            return List.of();
        }
        return java.util.Arrays.stream(userIds)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
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

    private String normalizeAlbumTitle(String value) {
        String normalized = value != null ? value.trim() : "";
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album title is required");
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

    private int normalizeAlbumPosition(Integer requestedPosition, int defaultPosition) {
        if (requestedPosition == null) {
            return defaultPosition;
        }
        if (requestedPosition < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album position cannot be negative");
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

    private List<UUID> normalizeRequiredStoryIds(List<UUID> storyIds, String message) {
        List<UUID> normalized = normalizeStoryIds(storyIds);
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private UUID resolveCoverStoryId(UUID coverStoryId, List<UUID> storyIds) {
        if (coverStoryId == null) {
            return null;
        }
        if (!storyIds.contains(coverStoryId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cover story must be included in selected stories");
        }
        return coverStoryId;
    }

    private Long normalizeDonationAmount(Long donationAmountMinor) {
        if (donationAmountMinor == null) {
            return null;
        }
        if (donationAmountMinor <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Donation amount must be positive");
        }
        return donationAmountMinor;
    }

    private String resolveCommentDonationCurrency(StoryLiveSessionEntity session, String requestedCurrency) {
        String normalized = normalizeDonationCurrency(requestedCurrency, true);
        if (normalized != null) {
            return normalized;
        }
        if (session.getDonationCurrency() != null) {
            return session.getDonationCurrency();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Donation currency is required");
    }

    private String normalizeDonationProvider(String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        return normalized.length() > 32 ? normalized.substring(0, 32) : normalized;
    }

    private String normalizeDonationCurrency(String value, boolean allowNull) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            if (allowNull) {
                return null;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Donation currency is required");
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[A-Z0-9]{3,8}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Donation currency is invalid");
        }
        return normalized;
    }

    private String normalizeDonationEventHookUrl(String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            return null;
        }
        if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Donation event hook url is invalid");
        }
        return normalized;
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

    private String normalizeStoryText(String text) {
        String normalized = normalizeOptionalText(text);
        if (normalized != null && normalized.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Story text is too long");
        }
        return normalized;
    }

    private String normalizeAudience(String audience) {
        String normalized = audience != null ? audience.trim().toUpperCase() : "DEFAULT";
        if (normalized.isBlank()) {
            return "DEFAULT";
        }
        if ("SELECTED_USERS".equals(normalized)) {
            return "CUSTOM";
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

    private record StoryOwnerScope(UserEntity ownerUser, ChatEntity ownerChat, boolean viewerOwnsSurface) {
    }
}
