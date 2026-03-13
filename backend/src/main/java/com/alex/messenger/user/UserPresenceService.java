package com.alex.messenger.user;

import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.user.dto.UserPresenceResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserPresenceService {

    public record UserPresenceView(boolean online, Instant lastSeenAt) {
    }

    private final UserRepository userRepository;
    private final ContactRepository contactRepository;
    private final UserSessionService userSessionService;

    @Transactional(readOnly = true)
    public List<UserPresenceResponse> listPresence(UUID requesterId, Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllById(userIds).stream()
                .map(user -> toResponse(user, requesterId))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserPresenceResponse getPresence(UUID requesterId, UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toResponse(user, requesterId);
    }

    @Transactional(readOnly = true)
    public UserPresenceView resolvePresence(UUID requesterId, UserEntity user) {
        if (user == null || user.isBot()) {
            return new UserPresenceView(false, null);
        }
        if (!canViewLastSeen(requesterId, user)) {
            return new UserPresenceView(false, null);
        }
        boolean online = userSessionService.isUserOnline(user.getId());
        return new UserPresenceView(online, user.getLastSeenAt());
    }

    private UserPresenceResponse toResponse(UserEntity user, UUID requesterId) {
        UserPresenceView presence = resolvePresence(requesterId, user);
        return new UserPresenceResponse(user.getId(), presence.online(), presence.lastSeenAt());
    }

    private boolean canViewLastSeen(UUID requesterId, UserEntity targetUser) {
        if (requesterId.equals(targetUser.getId())) {
            return true;
        }
        String privacy = targetUser.getLastSeenPrivacy() != null
                ? targetUser.getLastSeenPrivacy().trim().toUpperCase(Locale.ROOT)
                : "EVERYBODY";
        return switch (privacy) {
            case "EVERYBODY" -> true;
            case "CONTACTS" -> contactRepository.existsByIdOwnerUserIdAndIdContactUserId(
                    targetUser.getId(),
                    requesterId
            );
            case "NOBODY" -> false;
            default -> true;
        };
    }
}
