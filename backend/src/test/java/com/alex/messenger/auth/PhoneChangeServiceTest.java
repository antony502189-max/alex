package com.alex.messenger.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.auth.dto.AuthResponse;
import com.alex.messenger.auth.dto.VerifyPhoneChangeRequest;
import com.alex.messenger.auth.session.UserSessionEntity;
import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhoneChangeServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PhoneChangeChallengeRepository phoneChangeChallengeRepository;

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthSecurityEventService authSecurityEventService;

    private PhoneChangeService phoneChangeService;

    @BeforeEach
    void setUp() {
        AuthProperties authProperties = new AuthProperties();
        authProperties.getRefresh().setTtl(Duration.ofDays(30));
        phoneChangeService = new PhoneChangeService(
                userRepository,
                phoneChangeChallengeRepository,
                userSessionService,
                jwtService,
                authProperties,
                authSecurityEventService
        );
    }

    @Test
    void verifyCodeUpdatesPhoneAndRotatesCurrentSession() {
        UUID userId = UUID.randomUUID();
        UUID currentSessionId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        Instant accessExpiresAt = Instant.parse("2026-03-19T13:00:00Z");

        PhoneChangeChallengeEntity challenge = new PhoneChangeChallengeEntity();
        challenge.setId(challengeId);
        challenge.setUserId(userId);
        challenge.setSessionId(currentSessionId);
        challenge.setNewPhoneNumber("+375299999999");
        challenge.setCodeHash(hash("+375299999999:123456"));
        challenge.setAttemptCount(0);
        challenge.setMaxAttempts(5);
        challenge.setExpiresAt(Instant.now().plusSeconds(300));

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setPhoneNumber("+375291234567");
        user.setDisplayName("Alex");

        UserSessionEntity rotatedSession = new UserSessionEntity();
        rotatedSession.setId(currentSessionId);
        rotatedSession.setUserId(userId);
        rotatedSession.setTrustedSession(true);

        when(phoneChangeChallengeRepository.findByIdAndConsumedAtIsNull(challengeId)).thenReturn(Optional.of(challenge));
        when(phoneChangeChallengeRepository.save(any(PhoneChangeChallengeEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByPhoneNumber("+375299999999")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userSessionService.rotateRefreshToken(eq(currentSessionId), any(), any(), eq("127.0.0.1"), eq("JUnit")))
                .thenReturn(rotatedSession);
        when(jwtService.issueAccessToken(user, currentSessionId))
                .thenReturn(new JwtService.IssuedAccessToken("phone-change-access-token", accessExpiresAt));

        AuthResponse response = phoneChangeService.verifyCode(
                userId,
                currentSessionId,
                new VerifyPhoneChangeRequest(challengeId, "123456"),
                "127.0.0.1",
                "JUnit"
        );

        assertThat(response.authenticated()).isTrue();
        assertThat(response.authMethod()).isEqualTo("PHONE_CHANGE");
        assertThat(response.phoneNumber()).isEqualTo("+375299999999");
        assertThat(response.trustedSession()).isTrue();
        assertThat(user.getPhoneNumber()).isEqualTo("+375299999999");
        assertThat(challenge.getConsumedAt()).isNotNull();
        verify(userSessionService).revokeOthers(userId, currentSessionId);
        verify(authSecurityEventService).recordEvent(
                eq(userId),
                eq(currentSessionId),
                eq("PHONE_CHANGED"),
                eq("WARN"),
                eq("127.0.0.1"),
                eq("JUnit"),
                eq(null),
                eq(null),
                eq(null),
                eq("Phone number changed to ending 9999")
        );
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
