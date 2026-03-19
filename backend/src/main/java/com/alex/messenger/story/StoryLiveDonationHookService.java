package com.alex.messenger.story;

import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatRepository;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StoryLiveDonationHookService {

    private final StoryLiveCommentRepository storyLiveCommentRepository;
    private final StoryLiveSessionRepository storyLiveSessionRepository;
    private final StoryRepository storyRepository;
    private final UserRepository userRepository;
    private final ChatRepository chatRepository;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder().build();

    @Value("${alex.stories.live-donations.request-timeout:PT10S}")
    private Duration requestTimeout;

    @Value("${alex.stories.live-donations.max-attempts:10}")
    private int maxAttempts;

    public List<StoryLiveCommentEntity> lockPendingDeliveryBatch(int batchSize) {
        return storyLiveCommentRepository.lockDonationHookDeliveryBatch(
                Math.max(1, batchSize),
                Math.max(1, maxAttempts)
        );
    }

    public void deliverDonationHook(StoryLiveCommentEntity comment) {
        Instant attemptedAt = Instant.now();
        comment.setHookDeliveryAttempts((comment.getHookDeliveryAttempts() != null ? comment.getHookDeliveryAttempts() : 0) + 1);
        comment.setLastHookDeliveryAttemptAt(attemptedAt);

        try {
            StoryLiveSessionEntity session = storyLiveSessionRepository.findById(comment.getLiveSessionId()).orElse(null);
            StoryEntity story = storyRepository.findById(comment.getStoryId()).orElse(null);
            if (session == null || story == null) {
                comment.setLastHookError(truncateError("Live story donation context is no longer available"));
                storyLiveCommentRepository.save(comment);
                return;
            }

            String hookUrl = session.getDonationEventHookUrl();
            if (hookUrl == null || hookUrl.isBlank()) {
                comment.setLastHookError(truncateError("Donation hook url is missing"));
                storyLiveCommentRepository.save(comment);
                return;
            }

            Map<UUID, UserEntity> usersById = getUsers(comment.getAuthorUserId(), session.getOwnerUserId());
            UserEntity author = usersById.get(comment.getAuthorUserId());
            UserEntity publisher = usersById.get(session.getOwnerUserId());
            ChatEntity ownerChat = story.getOwnerChatId() != null
                    ? chatRepository.findById(story.getOwnerChatId()).orElse(null)
                    : null;

            StoryLiveDonationEventPayload payload = buildPayload(comment, session, story, author, publisher, ownerChat);
            byte[] serializedPayload = objectMapper.writeValueAsBytes(payload);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(hookUrl))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("X-Alex-Story-Event", payload.eventType());
            if (session.getDonationProvider() != null && !session.getDonationProvider().isBlank()) {
                requestBuilder.header("X-Alex-Story-Provider", session.getDonationProvider());
            }

            HttpResponse<String> response = execute(
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(serializedPayload)).build()
            );
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                comment.setHookDeliveredAt(attemptedAt);
                comment.setLastHookError(null);
            } else {
                comment.setLastHookError(truncateError("HTTP " + response.statusCode()));
            }
        } catch (Exception exception) {
            String message = exception.getMessage() != null
                    ? exception.getMessage()
                    : exception.getClass().getSimpleName();
            comment.setLastHookError(truncateError(message));
        }

        storyLiveCommentRepository.save(comment);
    }

    StoryLiveDonationEventPayload buildPayload(
            StoryLiveCommentEntity comment,
            StoryLiveSessionEntity session,
            StoryEntity story,
            UserEntity author,
            UserEntity publisher,
            ChatEntity ownerChat
    ) {
        boolean channelOwned = story.getOwnerChatId() != null;
        return new StoryLiveDonationEventPayload(
                "STORY_LIVE_DONATION",
                comment.getId(),
                session.getId(),
                story.getId(),
                channelOwned ? "CHANNEL" : "USER",
                channelOwned ? null : story.getOwnerUserId(),
                story.getOwnerChatId(),
                channelOwned
                        ? ownerChat != null && ownerChat.getTitle() != null ? ownerChat.getTitle() : "Channel"
                        : publisher != null ? publisher.getDisplayName() : "Unknown",
                channelOwned
                        ? ownerChat != null ? ownerChat.getPublicUsername() : null
                        : publisher != null ? publisher.getUsername() : null,
                session.getOwnerUserId(),
                publisher != null ? publisher.getDisplayName() : "Unknown",
                publisher != null ? publisher.getUsername() : null,
                comment.getAuthorUserId(),
                author != null ? author.getDisplayName() : "Unknown",
                author != null ? author.getUsername() : null,
                comment.getMessageText(),
                comment.getDonationAmountMinor(),
                comment.getDonationCurrency(),
                session.getDonationProvider(),
                session.getDonationEventsCount(),
                session.getDonationsTotalMinor(),
                session.getStartedAt(),
                comment.getCreatedAt()
        );
    }

    protected HttpResponse<String> execute(HttpRequest request) throws Exception {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Map<UUID, UserEntity> getUsers(UUID... userIds) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (UUID userId : userIds) {
            if (userId != null) {
                ids.add(userId);
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(UserEntity::getId, user -> user));
    }

    private String truncateError(String value) {
        if (value == null || value.isBlank()) {
            return "Hook delivery failed";
        }
        return value.length() <= 255 ? value : value.substring(0, 255);
    }
}
