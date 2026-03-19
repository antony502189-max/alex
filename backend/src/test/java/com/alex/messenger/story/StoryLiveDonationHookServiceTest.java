package com.alex.messenger.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatRepository;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoryLiveDonationHookServiceTest {

    @Mock
    private StoryLiveCommentRepository storyLiveCommentRepository;

    @Mock
    private StoryLiveSessionRepository storyLiveSessionRepository;

    @Mock
    private StoryRepository storyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatRepository chatRepository;

    private TestStoryLiveDonationHookService storyLiveDonationHookService;

    @BeforeEach
    void setUp() throws Exception {
        storyLiveDonationHookService = new TestStoryLiveDonationHookService(
                storyLiveCommentRepository,
                storyLiveSessionRepository,
                storyRepository,
                userRepository,
                chatRepository,
                new ObjectMapper()
        );
        setField(storyLiveDonationHookService, "requestTimeout", Duration.ofSeconds(10));
        setField(storyLiveDonationHookService, "maxAttempts", 5);
    }

    @Test
    void deliverDonationHookMarksCommentDeliveredOnSuccessfulResponse() {
        UUID commentId = UUID.randomUUID();
        UUID liveSessionId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID ownerChatId = UUID.randomUUID();
        UUID publisherUserId = UUID.randomUUID();
        UUID authorUserId = UUID.randomUUID();

        StoryLiveCommentEntity comment = comment(commentId, liveSessionId, storyId, authorUserId);
        StoryLiveSessionEntity session = session(liveSessionId, storyId, publisherUserId);
        session.setDonationProvider("STRIPE");
        session.setDonationEventHookUrl("https://hooks.example/live-story");
        session.setDonationEventsCount(2L);
        session.setDonationsTotalMinor(1500L);

        StoryEntity story = story(storyId, publisherUserId, ownerChatId);
        UserEntity publisher = user(publisherUserId, "Publisher", "publisher");
        UserEntity author = user(authorUserId, "Viewer", "viewer");
        ChatEntity channel = channel(ownerChatId, "Release Notes", "release_notes");

        HttpResponse<String> response = response(202);
        storyLiveDonationHookService.nextResponse = response;

        when(storyLiveSessionRepository.findById(liveSessionId)).thenReturn(Optional.of(session));
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(userRepository.findAllById(any())).thenReturn(List.of(publisher, author));
        when(chatRepository.findById(ownerChatId)).thenReturn(Optional.of(channel));
        when(storyLiveCommentRepository.save(any(StoryLiveCommentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        storyLiveDonationHookService.deliverDonationHook(comment);

        assertThat(comment.getHookDeliveredAt()).isNotNull();
        assertThat(comment.getLastHookError()).isNull();
        assertThat(comment.getHookDeliveryAttempts()).isEqualTo(1);
        assertThat(storyLiveDonationHookService.lastRequest).isNotNull();
        assertThat(storyLiveDonationHookService.lastRequest.uri().toString()).isEqualTo("https://hooks.example/live-story");
        assertThat(storyLiveDonationHookService.lastRequest.headers().firstValue("X-Alex-Story-Event"))
                .contains("STORY_LIVE_DONATION");
        verify(storyLiveCommentRepository).save(comment);
    }

    @Test
    void deliverDonationHookStoresHttpErrorForNonSuccessResponse() {
        UUID commentId = UUID.randomUUID();
        UUID liveSessionId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID publisherUserId = UUID.randomUUID();
        UUID authorUserId = UUID.randomUUID();

        StoryLiveCommentEntity comment = comment(commentId, liveSessionId, storyId, authorUserId);
        StoryLiveSessionEntity session = session(liveSessionId, storyId, publisherUserId);
        session.setDonationEventHookUrl("https://hooks.example/live-story");
        StoryEntity story = story(storyId, publisherUserId, null);
        UserEntity publisher = user(publisherUserId, "Publisher", "publisher");
        UserEntity author = user(authorUserId, "Viewer", "viewer");

        storyLiveDonationHookService.nextResponse = response(500);

        when(storyLiveSessionRepository.findById(liveSessionId)).thenReturn(Optional.of(session));
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(userRepository.findAllById(any())).thenReturn(List.of(publisher, author));
        when(storyLiveCommentRepository.save(any(StoryLiveCommentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        storyLiveDonationHookService.deliverDonationHook(comment);

        assertThat(comment.getHookDeliveredAt()).isNull();
        assertThat(comment.getLastHookError()).isEqualTo("HTTP 500");
        assertThat(comment.getHookDeliveryAttempts()).isEqualTo(1);
    }

    @Test
    void buildPayloadUsesChannelOwnershipSurfaceForChannelStory() {
        UUID liveSessionId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID ownerChatId = UUID.randomUUID();
        UUID publisherUserId = UUID.randomUUID();
        UUID authorUserId = UUID.randomUUID();

        StoryLiveCommentEntity comment = comment(UUID.randomUUID(), liveSessionId, storyId, authorUserId);
        StoryLiveSessionEntity session = session(liveSessionId, storyId, publisherUserId);
        session.setDonationProvider("XTR");
        session.setDonationEventsCount(4L);
        session.setDonationsTotalMinor(2500L);
        StoryEntity story = story(storyId, publisherUserId, ownerChatId);
        UserEntity publisher = user(publisherUserId, "Publisher", "publisher");
        UserEntity author = user(authorUserId, "Viewer", "viewer");
        ChatEntity channel = channel(ownerChatId, "Channel Surface", "channel_surface");

        StoryLiveDonationEventPayload payload = storyLiveDonationHookService.buildPayload(
                comment,
                session,
                story,
                author,
                publisher,
                channel
        );

        assertThat(payload.storyOwnerType()).isEqualTo("CHANNEL");
        assertThat(payload.ownerUserId()).isNull();
        assertThat(payload.ownerChatId()).isEqualTo(ownerChatId);
        assertThat(payload.ownerDisplayName()).isEqualTo("Channel Surface");
        assertThat(payload.publisherUserId()).isEqualTo(publisherUserId);
        assertThat(payload.authorUserId()).isEqualTo(authorUserId);
        assertThat(payload.donationProvider()).isEqualTo("XTR");
    }

    private static StoryLiveCommentEntity comment(UUID commentId, UUID liveSessionId, UUID storyId, UUID authorUserId) {
        StoryLiveCommentEntity comment = new StoryLiveCommentEntity();
        comment.setId(commentId);
        comment.setLiveSessionId(liveSessionId);
        comment.setStoryId(storyId);
        comment.setAuthorUserId(authorUserId);
        comment.setMessageText("Support");
        comment.setDonationAmountMinor(500L);
        comment.setDonationCurrency("USD");
        comment.setCreatedAt(Instant.parse("2026-03-19T12:00:00Z"));
        return comment;
    }

    private static StoryLiveSessionEntity session(UUID liveSessionId, UUID storyId, UUID ownerUserId) {
        StoryLiveSessionEntity session = new StoryLiveSessionEntity();
        session.setId(liveSessionId);
        session.setStoryId(storyId);
        session.setOwnerUserId(ownerUserId);
        session.setStatus("ACTIVE");
        session.setDonationsEnabled(true);
        session.setDonationCurrency("USD");
        session.setStartedAt(Instant.parse("2026-03-19T11:00:00Z"));
        session.setCreatedAt(Instant.parse("2026-03-19T11:00:00Z"));
        session.setUpdatedAt(Instant.parse("2026-03-19T11:00:00Z"));
        return session;
    }

    private static StoryEntity story(UUID storyId, UUID ownerUserId, UUID ownerChatId) {
        StoryEntity story = new StoryEntity();
        story.setId(storyId);
        story.setOwnerUserId(ownerUserId);
        story.setOwnerChatId(ownerChatId);
        return story;
    }

    private static UserEntity user(UUID userId, String displayName, String username) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setDisplayName(displayName);
        user.setUsername(username);
        return user;
    }

    private static ChatEntity channel(UUID chatId, String title, String username) {
        ChatEntity channel = new ChatEntity();
        channel.setId(chatId);
        channel.setChatType("CHANNEL");
        channel.setTitle(title);
        channel.setPublicUsername(username);
        return channel;
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int statusCode) {
        HttpResponse<String> response = org.mockito.Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        return response;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = StoryLiveDonationHookService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class TestStoryLiveDonationHookService extends StoryLiveDonationHookService {

        private HttpResponse<String> nextResponse;
        private HttpRequest lastRequest;

        private TestStoryLiveDonationHookService(
                StoryLiveCommentRepository storyLiveCommentRepository,
                StoryLiveSessionRepository storyLiveSessionRepository,
                StoryRepository storyRepository,
                UserRepository userRepository,
                ChatRepository chatRepository,
                ObjectMapper objectMapper
        ) {
            super(
                    storyLiveCommentRepository,
                    storyLiveSessionRepository,
                    storyRepository,
                    userRepository,
                    chatRepository,
                    objectMapper
            );
        }

        @Override
        protected HttpResponse<String> execute(HttpRequest request) {
            lastRequest = request;
            return nextResponse;
        }
    }
}
