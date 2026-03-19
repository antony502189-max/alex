package com.alex.messenger.user;

import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.user.dto.UserPresenceResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserPresenceService {

    public record UserPresenceView(boolean online, Instant lastSeenAt, String visibility, String statusText) {
    }

    private final UserRepository userRepository;
    private final UserSessionService userSessionService;
    private final UserPrivacyService userPrivacyService;

    @Transactional(readOnly = true)
    public List<UserPresenceResponse> listPresence(UUID requesterId, Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        if (userIds.size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "presence lookup supports up to 100 users");
        }
        return userRepository.findAllById(userIds).stream()
                .filter(user -> user.getDeletedAt() == null)
                .map(user -> toResponse(user, requesterId))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserPresenceResponse getPresence(UUID requesterId, UUID userId) {
        UserEntity user = requireActiveUser(userId);
        return toResponse(user, requesterId);
    }

    @Transactional(readOnly = true)
    public UserPresenceView resolvePresence(UUID requesterId, UserEntity user) {
        if (user == null || user.isBot()) {
            return new UserPresenceView(false, null, "HIDDEN", "hidden");
        }
        if (!userPrivacyService.canViewLastSeen(requesterId, user)) {
            return new UserPresenceView(false, null, "HIDDEN", "hidden");
        }
        boolean online = userSessionService.isUserOnline(user.getId());
        Instant lastSeenAt = user.getLastSeenAt();
        if (online) {
            return new UserPresenceView(true, lastSeenAt, "ONLINE", "online");
        }
        if (lastSeenAt == null) {
            return new UserPresenceView(false, null, "LAST_SEEN", "last seen a long time ago");
        }
        Duration sinceLastSeen = Duration.between(lastSeenAt, Instant.now());
        if (sinceLastSeen.compareTo(Duration.ofMinutes(5)) <= 0) {
            return new UserPresenceView(false, lastSeenAt, "LAST_SEEN", "last seen just now");
        }
        if (sinceLastSeen.compareTo(Duration.ofDays(3)) <= 0) {
            return new UserPresenceView(false, lastSeenAt, "RECENTLY", "last seen recently");
        }
        if (sinceLastSeen.compareTo(Duration.ofDays(7)) <= 0) {
            return new UserPresenceView(false, lastSeenAt, "WITHIN_A_WEEK", "last seen within a week");
        }
        if (sinceLastSeen.compareTo(Duration.ofDays(30)) <= 0) {
            return new UserPresenceView(false, lastSeenAt, "WITHIN_A_MONTH", "last seen within a month");
        }
        return new UserPresenceView(false, lastSeenAt, "LONG_AGO", "last seen a long time ago");
    }

    private UserPresenceResponse toResponse(UserEntity user, UUID requesterId) {
        UserPresenceView presence = resolvePresence(requesterId, user);
        return new UserPresenceResponse(
                user.getId(),
                presence.online(),
                presence.lastSeenAt(),
                presence.visibility(),
                presence.statusText()
        );
    }

    private UserEntity requireActiveUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.GONE, "User is deleted");
        }
        return user;
    }
}
