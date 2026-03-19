package com.alex.messenger.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

import com.alex.messenger.auth.session.UserSessionService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserPresenceServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private UserPrivacyService userPrivacyService;

    private UserPresenceService userPresenceService;

    @BeforeEach
    void setUp() {
        userPresenceService = new UserPresenceService(
                userRepository,
                userSessionService,
                userPrivacyService
        );
    }

    @Test
    void resolvePresenceReturnsOnlineWhenPrivacyAllows() {
        UUID requesterId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(targetUserId);
        user.setLastSeenPrivacy("EVERYBODY");
        user.setLastSeenAt(Instant.parse("2026-03-12T12:00:00Z"));

        when(userSessionService.isUserOnline(targetUserId)).thenReturn(true);
        when(userPrivacyService.canViewLastSeen(requesterId, user)).thenReturn(true);

        UserPresenceService.UserPresenceView presence = userPresenceService.resolvePresence(requesterId, user);

        assertThat(presence.online()).isTrue();
        assertThat(presence.lastSeenAt()).isEqualTo(Instant.parse("2026-03-12T12:00:00Z"));
        assertThat(presence.visibility()).isEqualTo("ONLINE");
    }

    @Test
    void resolvePresenceHidesLastSeenWhenPrivacyDeniesAccess() {
        UUID requesterId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(targetUserId);
        user.setLastSeenPrivacy("NOBODY");
        user.setLastSeenAt(Instant.parse("2026-03-12T12:00:00Z"));
        when(userPrivacyService.canViewLastSeen(requesterId, user)).thenReturn(false);

        UserPresenceService.UserPresenceView presence = userPresenceService.resolvePresence(requesterId, user);

        assertThat(presence.online()).isFalse();
        assertThat(presence.lastSeenAt()).isNull();
        assertThat(presence.visibility()).isEqualTo("HIDDEN");
    }

    @Test
    void listPresenceRejectsTooManyUsers() {
        UUID requesterId = UUID.randomUUID();
        var userIds = new ArrayList<UUID>();
        for (int index = 0; index < 101; index++) {
            userIds.add(UUID.randomUUID());
        }

        ResponseStatusException exception = catchThrowableOfType(
                () -> userPresenceService.listPresence(requesterId, userIds),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
