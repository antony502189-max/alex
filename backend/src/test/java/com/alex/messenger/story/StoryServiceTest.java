package com.alex.messenger.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatMemberRepository;
import com.alex.messenger.chat.ChatRepository;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.media.MediaObjectReference;
import com.alex.messenger.media.MediaProcessingService;
import com.alex.messenger.media.MediaService;
import com.alex.messenger.media.PresignedMediaAccess;
import com.alex.messenger.story.dto.CreateStoryAlbumRequest;
import com.alex.messenger.story.dto.CreateStoryRequest;
import com.alex.messenger.story.dto.CreateStoryHighlightRequest;
import com.alex.messenger.story.dto.CreateStoryLiveCommentRequest;
import com.alex.messenger.story.dto.GoLiveStoryRequest;
import com.alex.messenger.story.dto.StoryReactionRequest;
import com.alex.messenger.user.ContactRepository;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserPrivacyService;
import com.alex.messenger.user.UserRepository;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class StoryServiceTest {

    @Mock
    private StoryRepository storyRepository;

    @Mock
    private StoryViewRepository storyViewRepository;

    @Mock
    private StoryInteractionRepository storyInteractionRepository;

    @Mock
    private StoryHighlightRepository storyHighlightRepository;

    @Mock
    private StoryHighlightItemRepository storyHighlightItemRepository;

    @Mock
    private StoryAlbumRepository storyAlbumRepository;

    @Mock
    private StoryAlbumItemRepository storyAlbumItemRepository;

    @Mock
    private StoryLiveSessionRepository storyLiveSessionRepository;

    @Mock
    private StoryLiveCommentRepository storyLiveCommentRepository;

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatMemberRepository chatMemberRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private UserPrivacyService userPrivacyService;

    @Mock
    private MediaService mediaService;

    @Mock
    private MediaProcessingService mediaProcessingService;

    @Mock
    private StoryInteractionNotificationService storyInteractionNotificationService;

    private StoryService storyService;

    @BeforeEach
    void setUp() throws Exception {
        storyService = new StoryService(
                storyRepository,
                storyViewRepository,
                storyInteractionRepository,
                storyHighlightRepository,
                storyHighlightItemRepository,
                storyAlbumRepository,
                storyAlbumItemRepository,
                storyLiveSessionRepository,
                storyLiveCommentRepository,
                chatRepository,
                chatMemberRepository,
                chatService,
                userRepository,
                contactRepository,
                userPrivacyService,
                mediaService,
                mediaProcessingService,
                storyInteractionNotificationService
        );

        Field field = StoryService.class.getDeclaredField("maxStoryMediaFileSizeBytes");
        field.setAccessible(true);
        field.set(storyService, 25L * 1024L * 1024L);
    }

    @Test
    void listFeedHidesCustomAudienceStoryFromNonSelectedViewer() {
        UUID ownerUserId = UUID.randomUUID();
        UUID allowedViewerId = UUID.randomUUID();
        UUID blockedViewerId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        StoryEntity story = story(
                storyId,
                ownerUserId,
                Instant.parse("2026-03-12T08:00:00Z"),
                Instant.parse("2026-03-12T20:00:00Z")
        );
        story.setAudience("CUSTOM");
        story.setAllowedViewerUserIds(allowedViewerId.toString());

        UserEntity owner = user(ownerUserId, "Owner");

        when(storyRepository.findAllByExpiresAtAfterOrderByCreatedAtDesc(any(Instant.class))).thenReturn(List.of(story));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(owner));
        when(storyViewRepository.findAllByIdViewerUserIdAndIdStoryIdIn(eq(allowedViewerId), anyList())).thenReturn(List.of());
        when(storyViewRepository.findAllByIdStoryId(storyId)).thenReturn(List.of());
        when(userPrivacyService.isCloseFriend(ownerUserId, allowedViewerId)).thenReturn(false);
        when(userPrivacyService.isCloseFriend(ownerUserId, blockedViewerId)).thenReturn(false);

        var allowedFeed = storyService.listFeed(allowedViewerId);
        var blockedFeed = storyService.listFeed(blockedViewerId);

        assertThat(allowedFeed).hasSize(1);
        assertThat(allowedFeed.get(0).stories()).hasSize(1);
        assertThat(blockedFeed).isEmpty();
    }

    @Test
    void createWithMediaStoresImageStoryMedia() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        UserEntity owner = user(ownerUserId, "Media owner");
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);

        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getOriginalFilename()).thenReturn("story.jpg");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(storyRepository.save(any(StoryEntity.class))).thenAnswer(invocation -> {
            StoryEntity story = invocation.getArgument(0);
            if (story.getId() == null) {
                story.setId(storyId);
            }
            if (story.getCreatedAt() == null) {
                story.setCreatedAt(Instant.parse("2026-03-12T09:00:00Z"));
            }
            return story;
        });
        when(mediaService.uploadStoryMedia(
                eq(ownerUserId),
                eq(storyId),
                eq("story.jpg"),
                eq("image/jpeg"),
                eq(1024L),
                any()
        )).thenReturn(new MediaObjectReference("media", "stories/story.jpg", "s3://media/stories/story.jpg"));
        when(mediaService.buildDownloadAccess("media", "stories/story.jpg"))
                .thenReturn(new PresignedMediaAccess(
                        "https://cdn.example/stories/story.jpg",
                        Instant.parse("2026-03-12T10:00:00Z")
                ));

        var response = storyService.createWithMedia(
                ownerUserId,
                new CreateStoryRequest(
                        null,
                        "#0f172a",
                        "#2563eb",
                        "#ffffff",
                        "DEFAULT",
                        List.of(),
                        null
                ),
                null,
                file
        );

        assertThat(response.text()).isNull();
        assertThat(response.media()).isNotNull();
        assertThat(response.media().kind()).isEqualTo("IMAGE");
        assertThat(response.media().downloadUrl()).isEqualTo("https://cdn.example/stories/story.jpg");
        assertThat(response.media().previewUrl()).isEqualTo("https://cdn.example/stories/story.jpg");
        verify(mediaProcessingService).enqueueStoryPreview(any(StoryEntity.class));
    }

    @Test
    void createWithMediaRejectsTextLongerThanFiveHundredCharacters() {
        UUID ownerUserId = UUID.randomUUID();
        UserEntity owner = user(ownerUserId, "Media owner");
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);

        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> storyService.createWithMedia(
                ownerUserId,
                new CreateStoryRequest(
                        "x".repeat(501),
                        "#0f172a",
                        "#2563eb",
                        "#ffffff",
                        "DEFAULT",
                        List.of(),
                        null
                ),
                null,
                file
        ))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(exception -> assertThat(((org.springframework.web.server.ResponseStatusException) exception)
                        .getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));

        verify(storyRepository, never()).save(any(StoryEntity.class));
    }

    @Test
    void createStoryForChannelUsesChannelOwnershipSurface() {
        UUID ownerUserId = UUID.randomUUID();
        UUID ownerChatId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        UserEntity owner = user(ownerUserId, "Publisher");
        ChatEntity channel = channel(ownerChatId, "Release Notes", "release_notes");

        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(chatService.getOwnedChat(ownerUserId, ownerChatId)).thenReturn(channel);
        when(storyRepository.save(any(StoryEntity.class))).thenAnswer(invocation -> {
            StoryEntity story = invocation.getArgument(0);
            if (story.getId() == null) {
                story.setId(storyId);
            }
            if (story.getCreatedAt() == null) {
                story.setCreatedAt(Instant.parse("2026-03-19T10:00:00Z"));
            }
            return story;
        });

        var response = storyService.create(
                ownerUserId,
                new CreateStoryRequest(
                        "Update",
                        "#0f172a",
                        "#2563eb",
                        "#ffffff",
                        null,
                        List.of(),
                        ownerChatId
                )
        );

        assertThat(response.storyId()).isEqualTo(storyId);
        assertThat(response.ownerUserId()).isNull();
        assertThat(response.ownerChatId()).isEqualTo(ownerChatId);
        assertThat(response.ownerDisplayName()).isEqualTo("Release Notes");
        assertThat(response.ownerUsername()).isEqualTo("release_notes");
    }

    @Test
    void createSupportsSelectedUsersAudienceAlias() {
        UUID ownerUserId = UUID.randomUUID();
        UUID allowedViewerId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        UserEntity owner = user(ownerUserId, "Owner");
        AtomicReference<StoryEntity> savedStory = new AtomicReference<>();

        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(contactRepository.existsByIdOwnerUserIdAndIdContactUserId(ownerUserId, allowedViewerId)).thenReturn(true);
        when(storyRepository.save(any(StoryEntity.class))).thenAnswer(invocation -> {
            StoryEntity story = invocation.getArgument(0);
            if (story.getId() == null) {
                story.setId(storyId);
            }
            if (story.getCreatedAt() == null) {
                story.setCreatedAt(Instant.parse("2026-03-19T11:00:00Z"));
            }
            savedStory.set(story);
            return story;
        });
        when(storyViewRepository.findAllByIdStoryId(storyId)).thenReturn(List.of());

        var response = storyService.create(
                ownerUserId,
                new CreateStoryRequest(
                        "Selected",
                        "#0f172a",
                        "#2563eb",
                        "#ffffff",
                        "SELECTED_USERS",
                        List.of(allowedViewerId),
                        null
                )
        );

        assertThat(response.storyId()).isEqualTo(storyId);
        assertThat(savedStory.get()).isNotNull();
        assertThat(savedStory.get().getAudience()).isEqualTo("CUSTOM");
        assertThat(savedStory.get().getAllowedViewerUserIds()).isEqualTo(allowedViewerId.toString());
    }

    @Test
    void createRejectsCustomAudienceWithoutAllowedViewers() {
        UUID ownerUserId = UUID.randomUUID();
        UserEntity owner = user(ownerUserId, "Owner");

        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> storyService.create(
                ownerUserId,
                new CreateStoryRequest(
                        "Selected",
                        "#0f172a",
                        "#2563eb",
                        "#ffffff",
                        "CUSTOM",
                        List.of(),
                        null
                )
        ))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(exception -> assertThat(((org.springframework.web.server.ResponseStatusException) exception)
                        .getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));

        verify(storyRepository, never()).save(any(StoryEntity.class));
    }

    @Test
    void listArchiveReturnsExpiredStoriesOnly() {
        UUID ownerUserId = UUID.randomUUID();
        UUID expiredStoryId = UUID.randomUUID();
        UUID activeStoryId = UUID.randomUUID();

        UserEntity owner = user(ownerUserId, "Archive owner");

        StoryEntity expired = story(
                expiredStoryId,
                ownerUserId,
                Instant.parse("2026-03-11T08:00:00Z"),
                Instant.parse("2026-03-11T20:00:00Z")
        );
        StoryEntity active = story(
                activeStoryId,
                ownerUserId,
                Instant.parse("2026-03-12T08:00:00Z"),
                Instant.parse("2026-03-15T08:00:00Z")
        );

        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(storyRepository.findAllByOwnerUserIdOrderByCreatedAtDesc(ownerUserId)).thenReturn(List.of(active, expired));
        when(storyViewRepository.findAllByIdStoryId(expiredStoryId)).thenReturn(List.of());
        when(storyViewRepository.findAllByIdStoryId(activeStoryId)).thenReturn(List.of());

        var archive = storyService.listArchive(ownerUserId, null);

        assertThat(archive).hasSize(1);
        assertThat(archive.get(0).storyId()).isEqualTo(expiredStoryId);
        assertThat(archive.get(0).expired()).isTrue();
    }

    @Test
    void getChannelSurfaceIncludesStoriesAlbumsAndLiveIds() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();

        ChatEntity channel = channel(chatId, "News", "daily_news");
        UserEntity publisher = user(requesterId, "Publisher");

        StoryEntity story = story(
                storyId,
                requesterId,
                Instant.parse("2026-03-19T08:00:00Z"),
                Instant.parse("2026-03-20T08:00:00Z")
        );
        story.setOwnerChatId(chatId);

        StoryAlbumEntity album = new StoryAlbumEntity();
        album.setId(albumId);
        album.setOwnerUserId(requesterId);
        album.setOwnerChatId(chatId);
        album.setTitle("Top stories");
        album.setPosition(0);
        album.setCreatedAt(Instant.parse("2026-03-19T09:00:00Z"));
        album.setUpdatedAt(Instant.parse("2026-03-19T09:00:00Z"));

        StoryAlbumItemEntity albumItem = new StoryAlbumItemEntity();
        albumItem.setAlbumId(albumId);
        albumItem.setStoryId(storyId);
        albumItem.setPosition(0);

        StoryLiveSessionEntity liveSession = new StoryLiveSessionEntity();
        liveSession.setId(UUID.randomUUID());
        liveSession.setStoryId(storyId);
        liveSession.setOwnerUserId(requesterId);
        liveSession.setStatus("ACTIVE");

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(channel));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(storyRepository.findAllByOwnerChatIdAndExpiresAtAfterOrderByCreatedAtDesc(eq(chatId), any(Instant.class)))
                .thenReturn(List.of(story));
        when(storyAlbumRepository.findAllByOwnerChatIdOrderByPositionAscCreatedAtAsc(chatId)).thenReturn(List.of(album));
        when(storyAlbumItemRepository.findAllByAlbumIdOrderByPositionAscCreatedAtAsc(albumId)).thenReturn(List.of(albumItem));
        when(storyRepository.findAllById(any())).thenReturn(List.of(story));
        when(storyViewRepository.findAllByIdViewerUserIdAndIdStoryIdIn(eq(requesterId), anyCollection())).thenReturn(List.of());
        when(storyViewRepository.findAllByIdStoryId(storyId)).thenReturn(List.of());
        when(storyLiveSessionRepository.findAllByStoryIdInAndStatus(anyCollection(), eq("ACTIVE"))).thenReturn(List.of(liveSession));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(publisher));

        var response = storyService.getChannelSurface(requesterId, chatId);

        assertThat(response.ownerType()).isEqualTo("CHANNEL");
        assertThat(response.ownerChatId()).isEqualTo(chatId);
        assertThat(response.activeStoriesCount()).isEqualTo(1);
        assertThat(response.albumCount()).isEqualTo(1);
        assertThat(response.liveStoriesCount()).isEqualTo(1);
        assertThat(response.activeLiveStoryIds()).containsExactly(storyId);
        assertThat(response.canManage()).isTrue();
    }

    @Test
    void reactUpdatesExistingReactionForViewer() {
        UUID ownerUserId = UUID.randomUUID();
        UUID viewerUserId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID interactionId = UUID.randomUUID();

        StoryEntity story = story(
                storyId,
                ownerUserId,
                Instant.parse("2026-03-12T08:00:00Z"),
                Instant.parse("2026-03-15T08:00:00Z")
        );
        UserEntity owner = user(ownerUserId, "Owner");
        UserEntity viewer = user(viewerUserId, "Viewer");

        StoryInteractionEntity reaction = new StoryInteractionEntity();
        reaction.setId(interactionId);
        reaction.setStoryId(storyId);
        reaction.setActorUserId(viewerUserId);
        reaction.setInteractionType("REACTION");
        reaction.setCreatedAt(Instant.parse("2026-03-12T09:00:00Z"));
        reaction.setUpdatedAt(Instant.parse("2026-03-12T09:00:00Z"));

        when(storyRepository.findByIdAndExpiresAtAfter(eq(storyId), any(Instant.class))).thenReturn(Optional.of(story));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(storyInteractionRepository.findFirstByStoryIdAndActorUserIdAndInteractionType(
                storyId,
                viewerUserId,
                "REACTION"
        )).thenReturn(Optional.of(reaction));
        when(storyInteractionRepository.save(any(StoryInteractionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(owner, viewer));

        var response = storyService.react(viewerUserId, storyId, new StoryReactionRequest("🔥"));

        assertThat(response.storyId()).isEqualTo(storyId);
        assertThat(response.actorUserId()).isEqualTo(viewerUserId);
        assertThat(response.targetUserId()).isEqualTo(ownerUserId);
        assertThat(response.type()).isEqualTo("REACTION");
        assertThat(response.reaction()).isEqualTo("🔥");
    }

    @Test
    void createHighlightUsesArchivedStoriesAndSetsDefaultCover() {
        UUID ownerUserId = UUID.randomUUID();
        UUID highlightId = UUID.randomUUID();
        UUID firstStoryId = UUID.randomUUID();
        UUID secondStoryId = UUID.randomUUID();

        UserEntity owner = user(ownerUserId, "Highlight owner");
        StoryEntity firstStory = story(
                firstStoryId,
                ownerUserId,
                Instant.parse("2026-03-10T08:00:00Z"),
                Instant.parse("2026-03-10T20:00:00Z")
        );
        StoryEntity secondStory = story(
                secondStoryId,
                ownerUserId,
                Instant.parse("2026-03-11T08:00:00Z"),
                Instant.parse("2026-03-11T20:00:00Z")
        );
        StoryHighlightItemEntity firstItem = new StoryHighlightItemEntity();
        firstItem.setHighlightId(highlightId);
        firstItem.setStoryId(firstStoryId);
        firstItem.setPosition(0);
        StoryHighlightItemEntity secondItem = new StoryHighlightItemEntity();
        secondItem.setHighlightId(highlightId);
        secondItem.setStoryId(secondStoryId);
        secondItem.setPosition(1);

        when(storyRepository.findAllById(any())).thenReturn(List.of(firstStory, secondStory));
        when(storyHighlightRepository.findAllByOwnerUserIdOrderByPositionAscCreatedAtAsc(ownerUserId)).thenReturn(List.of());
        when(storyHighlightRepository.save(any(StoryHighlightEntity.class))).thenAnswer(invocation -> {
            StoryHighlightEntity highlight = invocation.getArgument(0);
            highlight.setId(highlightId);
            if (highlight.getCreatedAt() == null) {
                highlight.setCreatedAt(Instant.parse("2026-03-12T09:00:00Z"));
            }
            if (highlight.getUpdatedAt() == null) {
                highlight.setUpdatedAt(Instant.parse("2026-03-12T09:00:00Z"));
            }
            return highlight;
        });
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(storyHighlightItemRepository.findAllByHighlightIdOrderByPositionAscCreatedAtAsc(highlightId))
                .thenReturn(List.of(firstItem, secondItem));
        when(storyViewRepository.findAllByIdViewerUserIdAndIdStoryIdIn(eq(ownerUserId), anyCollection())).thenReturn(List.of());
        when(storyViewRepository.findAllByIdStoryId(firstStoryId)).thenReturn(List.of());
        when(storyViewRepository.findAllByIdStoryId(secondStoryId)).thenReturn(List.of());

        var response = storyService.createHighlight(
                ownerUserId,
                new CreateStoryHighlightRequest("Trips", null, null, List.of(firstStoryId, secondStoryId))
        );

        assertThat(response.highlightId()).isEqualTo(highlightId);
        assertThat(response.coverStoryId()).isEqualTo(firstStoryId);
        assertThat(response.storiesCount()).isEqualTo(2);
        assertThat(response.stories()).extracting(story -> story.storyId())
                .containsExactly(firstStoryId, secondStoryId);
    }

    @Test
    void createHighlightRejectsEmptyStorySelection() {
        UUID ownerUserId = UUID.randomUUID();

        assertThatThrownBy(() -> storyService.createHighlight(
                ownerUserId,
                new CreateStoryHighlightRequest("Trips", null, null, List.of())
        ))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(exception -> assertThat(((org.springframework.web.server.ResponseStatusException) exception)
                        .getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));

        verify(storyHighlightRepository, org.mockito.Mockito.never()).save(any(StoryHighlightEntity.class));
    }

    @Test
    void createHighlightRejectsCoverStoryOutsideSelection() {
        UUID ownerUserId = UUID.randomUUID();
        UUID firstStoryId = UUID.randomUUID();
        UUID secondStoryId = UUID.randomUUID();
        UUID coverStoryId = UUID.randomUUID();

        StoryEntity firstStory = story(
                firstStoryId,
                ownerUserId,
                Instant.parse("2026-03-10T08:00:00Z"),
                Instant.parse("2026-03-10T20:00:00Z")
        );
        StoryEntity secondStory = story(
                secondStoryId,
                ownerUserId,
                Instant.parse("2026-03-11T08:00:00Z"),
                Instant.parse("2026-03-11T20:00:00Z")
        );

        when(storyRepository.findAllById(any())).thenReturn(List.of(firstStory, secondStory));
        when(storyHighlightRepository.findAllByOwnerUserIdOrderByPositionAscCreatedAtAsc(ownerUserId)).thenReturn(List.of());

        assertThatThrownBy(() -> storyService.createHighlight(
                ownerUserId,
                new CreateStoryHighlightRequest("Trips", coverStoryId, null, List.of(firstStoryId, secondStoryId))
        ))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(exception -> assertThat(((org.springframework.web.server.ResponseStatusException) exception)
                        .getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));

        verify(storyHighlightRepository, never()).save(any(StoryHighlightEntity.class));
    }

    @Test
    void addStoriesToHighlightRejectsEmptyUpdate() {
        UUID ownerUserId = UUID.randomUUID();
        UUID highlightId = UUID.randomUUID();

        StoryHighlightEntity highlight = new StoryHighlightEntity();
        highlight.setId(highlightId);
        highlight.setOwnerUserId(ownerUserId);
        highlight.setTitle("Trips");

        when(storyHighlightRepository.findById(highlightId)).thenReturn(Optional.of(highlight));

        assertThatThrownBy(() -> storyService.addStoriesToHighlight(
                ownerUserId,
                highlightId,
                new UpdateStoryHighlightStoriesRequest(null, null)
        ))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(exception -> assertThat(((org.springframework.web.server.ResponseStatusException) exception)
                        .getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));

        verify(storyHighlightItemRepository, never()).findAllByHighlightIdOrderByPositionAscCreatedAtAsc(any());
        verify(storyHighlightRepository, never()).save(any(StoryHighlightEntity.class));
    }

    @Test
    void reactToChannelStoryDoesNotRequireTargetUser() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        StoryEntity story = story(
                storyId,
                requesterId,
                Instant.parse("2026-03-19T08:00:00Z"),
                Instant.parse("2026-03-20T08:00:00Z")
        );
        story.setOwnerChatId(chatId);
        ChatEntity channel = channel(chatId, "News", "daily_news");
        UserEntity requester = user(requesterId, "Viewer");

        when(storyRepository.findByIdAndExpiresAtAfter(eq(storyId), any(Instant.class))).thenReturn(Optional.of(story));
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(channel));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(storyInteractionRepository.findFirstByStoryIdAndActorUserIdAndInteractionType(
                storyId,
                requesterId,
                "REACTION"
        )).thenReturn(Optional.empty());
        when(storyInteractionRepository.save(any(StoryInteractionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(requester));

        var response = storyService.react(requesterId, storyId, new StoryReactionRequest("fire"));

        assertThat(response.storyId()).isEqualTo(storyId);
        assertThat(response.targetUserId()).isNull();
        assertThat(response.reaction()).isEqualTo("fire");
    }

    @Test
    void reactPublishesUnreadCountersForOwner() {
        UUID ownerUserId = UUID.randomUUID();
        UUID viewerUserId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID interactionId = UUID.randomUUID();

        StoryEntity story = story(
                storyId,
                ownerUserId,
                Instant.parse("2026-03-19T08:00:00Z"),
                Instant.parse("2026-03-20T08:00:00Z")
        );
        UserEntity owner = user(ownerUserId, "Owner");
        UserEntity viewer = user(viewerUserId, "Viewer");
        AtomicReference<StoryInteractionEntity> savedInteraction = new AtomicReference<>();

        when(storyRepository.findByIdAndExpiresAtAfter(eq(storyId), any(Instant.class))).thenReturn(Optional.of(story));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(userPrivacyService.canViewStory(viewerUserId, owner)).thenReturn(true);
        when(storyInteractionRepository.findFirstByStoryIdAndActorUserIdAndInteractionType(
                storyId,
                viewerUserId,
                "REACTION"
        )).thenReturn(Optional.empty());
        when(storyInteractionRepository.findFirstByStoryIdAndActorUserIdAndInteractionType(
                storyId,
                ownerUserId,
                "REACTION"
        )).thenReturn(Optional.empty());
        when(storyInteractionRepository.save(any(StoryInteractionEntity.class))).thenAnswer(invocation -> {
            StoryInteractionEntity interaction = invocation.getArgument(0);
            interaction.setId(interactionId);
            if (interaction.getCreatedAt() == null) {
                interaction.setCreatedAt(Instant.parse("2026-03-19T09:00:00Z"));
            }
            savedInteraction.set(interaction);
            return interaction;
        });
        when(storyInteractionRepository.findAllByStoryIdOrderByCreatedAtDesc(storyId)).thenAnswer(invocation -> {
            StoryInteractionEntity interaction = savedInteraction.get();
            return interaction != null ? List.of(interaction) : List.of();
        });
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(owner, viewer));
        when(storyRepository.findAllByOwnerUserIdAndExpiresAtAfterOrderByCreatedAtDesc(eq(ownerUserId), any(Instant.class)))
                .thenReturn(List.of(story));
        when(storyInteractionRepository.countByStoryIdInAndSeenAtIsNull(List.of(storyId))).thenReturn(2L);
        when(storyInteractionRepository.countByStoryIdAndSeenAtIsNull(storyId)).thenReturn(1L);

        var response = storyService.react(viewerUserId, storyId, new StoryReactionRequest("fire"));

        assertThat(response.storyId()).isEqualTo(storyId);
        ArgumentCaptor<com.alex.messenger.story.dto.StoryInteractionEventResponse> eventCaptor =
                ArgumentCaptor.forClass(com.alex.messenger.story.dto.StoryInteractionEventResponse.class);
        verify(storyInteractionNotificationService).publish(eq(ownerUserId), eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("INTERACTION_UPSERT");
        assertThat(eventCaptor.getValue().unreadInteractionsCount()).isEqualTo(2);
        assertThat(eventCaptor.getValue().storyUnreadInteractionsCount()).isEqualTo(1);
        assertThat(eventCaptor.getValue().summary().reactionsCount()).isEqualTo(1);
    }

    @Test
    void listInteractionsMarksSeenAndPublishesSeenEvent() {
        UUID ownerUserId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID interactionId = UUID.randomUUID();

        StoryEntity story = story(
                storyId,
                ownerUserId,
                Instant.parse("2026-03-19T08:00:00Z"),
                Instant.parse("2026-03-20T08:00:00Z")
        );
        StoryInteractionEntity interaction = new StoryInteractionEntity();
        interaction.setId(interactionId);
        interaction.setStoryId(storyId);
        interaction.setActorUserId(actorUserId);
        interaction.setTargetUserId(ownerUserId);
        interaction.setInteractionType("REPLY");
        interaction.setMessageText("Reply");
        interaction.setCreatedAt(Instant.parse("2026-03-19T09:00:00Z"));
        UserEntity owner = user(ownerUserId, "Owner");
        UserEntity actor = user(actorUserId, "Actor");

        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyInteractionRepository.findAllByStoryIdOrderByCreatedAtDesc(storyId)).thenReturn(List.of(interaction));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(owner, actor));
        when(storyInteractionRepository.markSeenByStoryId(eq(storyId), any(Instant.class))).thenReturn(1);
        when(storyInteractionRepository.findFirstByStoryIdAndActorUserIdAndInteractionType(
                storyId,
                ownerUserId,
                "REACTION"
        )).thenReturn(Optional.empty());
        when(storyRepository.findAllByOwnerUserIdAndExpiresAtAfterOrderByCreatedAtDesc(eq(ownerUserId), any(Instant.class)))
                .thenReturn(List.of(story));
        when(storyInteractionRepository.countByStoryIdInAndSeenAtIsNull(List.of(storyId))).thenReturn(0L);
        when(storyInteractionRepository.countByStoryIdAndSeenAtIsNull(storyId)).thenReturn(0L);

        var response = storyService.listInteractions(ownerUserId, storyId);

        assertThat(response).hasSize(1);
        ArgumentCaptor<com.alex.messenger.story.dto.StoryInteractionEventResponse> eventCaptor =
                ArgumentCaptor.forClass(com.alex.messenger.story.dto.StoryInteractionEventResponse.class);
        verify(storyInteractionNotificationService).publish(eq(ownerUserId), eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("INTERACTIONS_SEEN");
        assertThat(eventCaptor.getValue().unreadInteractionsCount()).isZero();
        assertThat(eventCaptor.getValue().storyUnreadInteractionsCount()).isZero();
        assertThat(eventCaptor.getValue().summary().repliesCount()).isEqualTo(1);
    }

    @Test
    void createAlbumUsesOwnedStoriesAndSetsDefaultCover() {
        UUID ownerUserId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();
        UUID firstStoryId = UUID.randomUUID();
        UUID secondStoryId = UUID.randomUUID();

        UserEntity owner = user(ownerUserId, "Album owner");
        StoryEntity firstStory = story(
                firstStoryId,
                ownerUserId,
                Instant.parse("2026-03-12T08:00:00Z"),
                Instant.parse("2026-03-15T08:00:00Z")
        );
        StoryEntity secondStory = story(
                secondStoryId,
                ownerUserId,
                Instant.parse("2026-03-11T08:00:00Z"),
                Instant.parse("2026-03-11T20:00:00Z")
        );
        StoryAlbumItemEntity firstItem = new StoryAlbumItemEntity();
        firstItem.setAlbumId(albumId);
        firstItem.setStoryId(firstStoryId);
        firstItem.setPosition(0);
        StoryAlbumItemEntity secondItem = new StoryAlbumItemEntity();
        secondItem.setAlbumId(albumId);
        secondItem.setStoryId(secondStoryId);
        secondItem.setPosition(1);

        when(storyRepository.findAllById(any())).thenReturn(List.of(firstStory, secondStory));
        when(storyAlbumRepository.findAllByOwnerUserIdOrderByPositionAscCreatedAtAsc(ownerUserId)).thenReturn(List.of());
        when(storyAlbumRepository.save(any(StoryAlbumEntity.class))).thenAnswer(invocation -> {
            StoryAlbumEntity album = invocation.getArgument(0);
            album.setId(albumId);
            if (album.getCreatedAt() == null) {
                album.setCreatedAt(Instant.parse("2026-03-19T09:00:00Z"));
            }
            if (album.getUpdatedAt() == null) {
                album.setUpdatedAt(Instant.parse("2026-03-19T09:00:00Z"));
            }
            return album;
        });
        when(storyAlbumItemRepository.findAllByAlbumIdOrderByPositionAscCreatedAtAsc(albumId))
                .thenReturn(List.of(firstItem, secondItem));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(storyViewRepository.findAllByIdViewerUserIdAndIdStoryIdIn(eq(ownerUserId), anyCollection())).thenReturn(List.of());
        when(storyViewRepository.findAllByIdStoryId(firstStoryId)).thenReturn(List.of());
        when(storyViewRepository.findAllByIdStoryId(secondStoryId)).thenReturn(List.of());

        var response = storyService.createAlbum(
                ownerUserId,
                new CreateStoryAlbumRequest("Spring", null, null, List.of(firstStoryId, secondStoryId), null)
        );

        assertThat(response.albumId()).isEqualTo(albumId);
        assertThat(response.coverStoryId()).isEqualTo(firstStoryId);
        assertThat(response.storiesCount()).isEqualTo(2);
        assertThat(response.stories()).extracting(story -> story.storyId())
                .containsExactly(firstStoryId, secondStoryId);
    }

    @Test
    void createAlbumRejectsCoverStoryOutsideSelection() {
        UUID ownerUserId = UUID.randomUUID();
        UUID firstStoryId = UUID.randomUUID();
        UUID secondStoryId = UUID.randomUUID();
        UUID coverStoryId = UUID.randomUUID();

        UserEntity owner = user(ownerUserId, "Album owner");
        StoryEntity firstStory = story(
                firstStoryId,
                ownerUserId,
                Instant.parse("2026-03-12T08:00:00Z"),
                Instant.parse("2026-03-15T08:00:00Z")
        );
        StoryEntity secondStory = story(
                secondStoryId,
                ownerUserId,
                Instant.parse("2026-03-11T08:00:00Z"),
                Instant.parse("2026-03-11T20:00:00Z")
        );

        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(storyRepository.findAllById(any())).thenReturn(List.of(firstStory, secondStory));
        when(storyAlbumRepository.findAllByOwnerUserIdOrderByPositionAscCreatedAtAsc(ownerUserId)).thenReturn(List.of());

        assertThatThrownBy(() -> storyService.createAlbum(
                ownerUserId,
                new CreateStoryAlbumRequest("Spring", coverStoryId, null, List.of(firstStoryId, secondStoryId), null)
        ))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(exception -> assertThat(((org.springframework.web.server.ResponseStatusException) exception)
                        .getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));

        verify(storyAlbumRepository, never()).save(any(StoryAlbumEntity.class));
    }

    @Test
    void goLiveCreatesActiveSessionWithDonationMetadata() {
        UUID ownerUserId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID liveSessionId = UUID.randomUUID();

        StoryEntity story = story(
                storyId,
                ownerUserId,
                Instant.parse("2026-03-19T08:00:00Z"),
                Instant.parse("2026-03-20T08:00:00Z")
        );

        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyLiveSessionRepository.findFirstByStoryIdAndStatusOrderByStartedAtDesc(storyId, "ACTIVE"))
                .thenReturn(Optional.empty());
        when(storyLiveSessionRepository.save(any(StoryLiveSessionEntity.class))).thenAnswer(invocation -> {
            StoryLiveSessionEntity session = invocation.getArgument(0);
            session.setId(liveSessionId);
            if (session.getStartedAt() == null) {
                session.setStartedAt(Instant.parse("2026-03-19T09:00:00Z"));
            }
            if (session.getCreatedAt() == null) {
                session.setCreatedAt(Instant.parse("2026-03-19T09:00:00Z"));
            }
            if (session.getUpdatedAt() == null) {
                session.setUpdatedAt(Instant.parse("2026-03-19T09:00:00Z"));
            }
            return session;
        });

        var response = storyService.goLive(
                ownerUserId,
                storyId,
                new GoLiveStoryRequest(true, "stars", "xtr", "https://hooks.example/story-live")
        );

        assertThat(response.liveSessionId()).isEqualTo(liveSessionId);
        assertThat(response.storyId()).isEqualTo(storyId);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.donationsEnabled()).isTrue();
        assertThat(response.donationProvider()).isEqualTo("STARS");
        assertThat(response.donationCurrency()).isEqualTo("XTR");
        assertThat(response.donationEventHookUrl()).isEqualTo("https://hooks.example/story-live");
    }

    @Test
    void goLiveRejectsEnabledDonationsWithoutCurrency() {
        UUID ownerUserId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        StoryEntity story = story(
                storyId,
                ownerUserId,
                Instant.parse("2026-03-19T08:00:00Z"),
                Instant.parse("2026-03-20T08:00:00Z")
        );

        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyLiveSessionRepository.findFirstByStoryIdAndStatusOrderByStartedAtDesc(storyId, "ACTIVE"))
                .thenReturn(Optional.empty());

        var exception = catchThrowableOfType(
                () -> storyService.goLive(
                        ownerUserId,
                        storyId,
                        new GoLiveStoryRequest(true, "stars", null, "https://hooks.example/story-live")
                ),
                org.springframework.web.server.ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        verify(storyLiveSessionRepository, never()).save(any(StoryLiveSessionEntity.class));
    }

    @Test
    void commentLiveTracksDonationTotals() {
        UUID ownerUserId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID liveSessionId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        StoryEntity story = story(
                storyId,
                ownerUserId,
                Instant.parse("2026-03-19T08:00:00Z"),
                Instant.parse("2026-03-20T08:00:00Z")
        );
        UserEntity owner = user(ownerUserId, "Live owner");

        StoryLiveSessionEntity session = new StoryLiveSessionEntity();
        session.setId(liveSessionId);
        session.setStoryId(storyId);
        session.setOwnerUserId(ownerUserId);
        session.setStatus("ACTIVE");
        session.setDonationsEnabled(true);
        session.setDonationCurrency("XTR");
        session.setDonationEventsCount(0L);
        session.setDonationsTotalMinor(0L);
        session.setStartedAt(Instant.parse("2026-03-19T09:00:00Z"));

        when(storyRepository.findByIdAndExpiresAtAfter(eq(storyId), any(Instant.class))).thenReturn(Optional.of(story));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(storyLiveSessionRepository.findFirstByStoryIdAndStatusOrderByStartedAtDesc(storyId, "ACTIVE"))
                .thenReturn(Optional.of(session));
        when(storyLiveCommentRepository.save(any(StoryLiveCommentEntity.class))).thenAnswer(invocation -> {
            StoryLiveCommentEntity comment = invocation.getArgument(0);
            comment.setId(commentId);
            if (comment.getCreatedAt() == null) {
                comment.setCreatedAt(Instant.parse("2026-03-19T09:05:00Z"));
            }
            return comment;
        });
        when(storyLiveSessionRepository.save(any(StoryLiveSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(owner));

        var response = storyService.commentLive(
                ownerUserId,
                storyId,
                new CreateStoryLiveCommentRequest("Thanks", 50L, null)
        );

        assertThat(response.commentId()).isEqualTo(commentId);
        assertThat(response.message()).isEqualTo("Thanks");
        assertThat(response.donationAmountMinor()).isEqualTo(50L);
        assertThat(response.donationCurrency()).isEqualTo("XTR");
        assertThat(session.getDonationEventsCount()).isEqualTo(1L);
        assertThat(session.getDonationsTotalMinor()).isEqualTo(50L);
    }

    @Test
    void commentLiveRejectsEmptyPayload() {
        UUID ownerUserId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        StoryEntity story = story(
                storyId,
                ownerUserId,
                Instant.parse("2026-03-19T08:00:00Z"),
                Instant.parse("2026-03-20T08:00:00Z")
        );
        UserEntity owner = user(ownerUserId, "Live owner");

        StoryLiveSessionEntity session = new StoryLiveSessionEntity();
        session.setId(UUID.randomUUID());
        session.setStoryId(storyId);
        session.setOwnerUserId(ownerUserId);
        session.setStatus("ACTIVE");
        session.setDonationsEnabled(true);
        session.setDonationCurrency("XTR");

        when(storyRepository.findByIdAndExpiresAtAfter(eq(storyId), any(Instant.class))).thenReturn(Optional.of(story));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(storyLiveSessionRepository.findFirstByStoryIdAndStatusOrderByStartedAtDesc(storyId, "ACTIVE"))
                .thenReturn(Optional.of(session));

        var exception = catchThrowableOfType(
                () -> storyService.commentLive(
                        ownerUserId,
                        storyId,
                        new CreateStoryLiveCommentRequest("   ", null, null)
                ),
                org.springframework.web.server.ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        verify(storyLiveCommentRepository, never()).save(any(StoryLiveCommentEntity.class));
    }

    @Test
    void commentLiveRejectsCurrencyWithoutDonation() {
        UUID ownerUserId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        StoryEntity story = story(
                storyId,
                ownerUserId,
                Instant.parse("2026-03-19T08:00:00Z"),
                Instant.parse("2026-03-20T08:00:00Z")
        );
        UserEntity owner = user(ownerUserId, "Live owner");

        StoryLiveSessionEntity session = new StoryLiveSessionEntity();
        session.setId(UUID.randomUUID());
        session.setStoryId(storyId);
        session.setOwnerUserId(ownerUserId);
        session.setStatus("ACTIVE");
        session.setDonationsEnabled(true);

        when(storyRepository.findByIdAndExpiresAtAfter(eq(storyId), any(Instant.class))).thenReturn(Optional.of(story));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(storyLiveSessionRepository.findFirstByStoryIdAndStatusOrderByStartedAtDesc(storyId, "ACTIVE"))
                .thenReturn(Optional.of(session));

        var exception = catchThrowableOfType(
                () -> storyService.commentLive(
                        ownerUserId,
                        storyId,
                        new CreateStoryLiveCommentRequest("Hello", null, "XTR")
                ),
                org.springframework.web.server.ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    private UserEntity user(UUID userId, String displayName) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setDisplayName(displayName);
        user.setStoryPrivacy("EVERYBODY");
        return user;
    }

    private ChatEntity channel(UUID chatId, String title, String publicUsername) {
        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("CHANNEL");
        chat.setTitle(title);
        chat.setPublicUsername(publicUsername);
        return chat;
    }

    private StoryEntity story(UUID storyId, UUID ownerUserId, Instant createdAt, Instant expiresAt) {
        StoryEntity story = new StoryEntity();
        story.setId(storyId);
        story.setOwnerUserId(ownerUserId);
        story.setText("hello");
        story.setBackgroundFrom("#0f172a");
        story.setBackgroundTo("#2563eb");
        story.setTextColor("#ffffff");
        story.setAudience("DEFAULT");
        story.setAllowedViewerUserIds("");
        story.setCreatedAt(createdAt);
        story.setExpiresAt(expiresAt);
        return story;
    }
}
