package com.alex.messenger.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.media.MediaObjectReference;
import com.alex.messenger.media.MediaProcessingService;
import com.alex.messenger.media.MediaService;
import com.alex.messenger.media.PresignedMediaAccess;
import com.alex.messenger.story.dto.CreateStoryRequest;
import com.alex.messenger.story.dto.CreateStoryHighlightRequest;
import com.alex.messenger.story.dto.StoryReactionRequest;
import com.alex.messenger.user.ContactRepository;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private UserRepository userRepository;

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private MediaService mediaService;

    @Mock
    private MediaProcessingService mediaProcessingService;

    private StoryService storyService;

    @BeforeEach
    void setUp() throws Exception {
        storyService = new StoryService(
                storyRepository,
                storyViewRepository,
                storyInteractionRepository,
                storyHighlightRepository,
                storyHighlightItemRepository,
                userRepository,
                contactRepository,
                mediaService,
                mediaProcessingService
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
                        List.of()
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

        var archive = storyService.listArchive(ownerUserId);

        assertThat(archive).hasSize(1);
        assertThat(archive.get(0).storyId()).isEqualTo(expiredStoryId);
        assertThat(archive.get(0).expired()).isTrue();
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

    private UserEntity user(UUID userId, String displayName) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setDisplayName(displayName);
        user.setStoryPrivacy("EVERYBODY");
        return user;
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
