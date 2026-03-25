package com.alex.messenger.auth.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.auth.AuthSecurityEventService;
import com.alex.messenger.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserSessionServiceTest {

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthSecurityEventService authSecurityEventService;

    private UserSessionService userSessionService;

    @BeforeEach
    void setUp() {
        userSessionService = new UserSessionService(
                userSessionRepository,
                userRepository,
                authSecurityEventService
        );
    }

    @Test
    void createSessionRecordsSuspiciousLoginWhenDeviceAndIpAreUnfamiliar() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        UserSessionEntity previousSession = new UserSessionEntity();
        previousSession.setId(UUID.randomUUID());
        previousSession.setUserId(userId);
        previousSession.setDeviceName("MacBook");
        previousSession.setPlatform("ios");
        previousSession.setIpAddress("198.51.100.10");
        previousSession.setLastActiveAt(Instant.now().minusSeconds(120));

        when(userSessionRepository.findTop5ByUserIdAndRevokedAtIsNullOrderByLastActiveAtDesc(userId))
                .thenReturn(List.of(previousSession));
        when(userSessionRepository.save(any(UserSessionEntity.class))).thenAnswer(invocation -> {
            UserSessionEntity saved = invocation.getArgument(0);
            saved.setId(sessionId);
            return saved;
        });

        userSessionService.createSession(
                userId,
                new CreateUserSessionCommand(
                        "Pixel 9",
                        "android",
                        "1.0.0",
                        "JUnit",
                        "192.0.2.10",
                        "OTP",
                        "refresh-hash",
                        Instant.now().plusSeconds(3600),
                        false,
                        null
                )
        );

        verify(userRepository).touchLastSeenAt(eq(userId), any(Instant.class));
        verify(authSecurityEventService).recordEvent(
                eq(userId),
                eq(sessionId),
                eq("SUSPICIOUS_LOGIN"),
                eq("WARN"),
                eq("192.0.2.10"),
                eq("JUnit"),
                eq("Pixel 9"),
                eq("android"),
                eq("1.0.0"),
                anyString()
        );
    }

    @Test
    void revokeOthersRecordsSecurityAuditWhenSessionsWereRevoked() {
        UUID userId = UUID.randomUUID();
        UUID currentSessionId = UUID.randomUUID();
        when(userSessionRepository.revokeOthers(eq(userId), eq(currentSessionId), any(Instant.class))).thenReturn(2);

        userSessionService.revokeOthers(userId, currentSessionId);

        verify(authSecurityEventService).recordEvent(
                eq(userId),
                eq(currentSessionId),
                eq("SESSIONS_REVOKED"),
                eq("INFO"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("Revoked 2 other sessions")
        );
    }
}
