package com.alex.messenger.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.alex.messenger.auth.session.UserSessionService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserPresenceServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private UserSessionService userSessionService;

    private UserPresenceService userPresenceService;

    @BeforeEach
    void setUp() {
        userPresenceService = new UserPresenceService(
                userRepository,
                contactRepository,
                userSessionService
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

        UserPresenceService.UserPresenceView presence = userPresenceService.resolvePresence(requesterId, user);

        assertThat(presence.online()).isTrue();
        assertThat(presence.lastSeenAt()).isEqualTo(Instant.parse("2026-03-12T12:00:00Z"));
    }

    @Test
    void resolvePresenceHidesLastSeenWhenPrivacyDeniesAccess() {
        UUID requesterId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(targetUserId);
        user.setLastSeenPrivacy("NOBODY");
        user.setLastSeenAt(Instant.parse("2026-03-12T12:00:00Z"));

        UserPresenceService.UserPresenceView presence = userPresenceService.resolvePresence(requesterId, user);

        assertThat(presence.online()).isFalse();
        assertThat(presence.lastSeenAt()).isNull();
    }
}
