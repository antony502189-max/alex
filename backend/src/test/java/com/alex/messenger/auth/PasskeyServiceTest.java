package com.alex.messenger.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.alex.messenger.auth.dto.AuthResponse;
import com.alex.messenger.auth.dto.VerifyPasskeyLoginRequest;
import com.alex.messenger.auth.session.CreateUserSessionCommand;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PasskeyServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasskeyCredentialRepository passkeyCredentialRepository;

    @Mock
    private PasskeyChallengeRepository passkeyChallengeRepository;

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private JwtService jwtService;

    private PasskeyService passkeyService;

    @BeforeEach
    void setUp() {
        AuthProperties authProperties = new AuthProperties();
        authProperties.getRefresh().setTtl(Duration.ofDays(30));
        passkeyService = new PasskeyService(
                userRepository,
                passkeyCredentialRepository,
                passkeyChallengeRepository,
                userSessionService,
                jwtService,
                authProperties
        );
    }

    @Test
    void verifyLoginCreatesPasskeyAuthenticatedSession() {
        UUID userId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-03-19T12:00:00Z");
        String rawChallenge = "login-passkey-challenge";

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setPhoneNumber("+375291234567");
        user.setDisplayName("Alex");

        PasskeyChallengeEntity challenge = new PasskeyChallengeEntity();
        challenge.setId(challengeId);
        challenge.setUserId(userId);
        challenge.setFlowType("LOGIN");
        challenge.setChallengeHash(hash(rawChallenge));
        challenge.setExpiresAt(Instant.now().plusSeconds(300));

        PasskeyCredentialEntity credential = new PasskeyCredentialEntity();
        credential.setId(UUID.randomUUID());
        credential.setUserId(userId);
        credential.setCredentialId("credential-1");
        credential.setPublicKey("public-key");
        credential.setSignCount(3L);

        when(passkeyChallengeRepository.findByIdAndConsumedAtIsNull(challengeId)).thenReturn(Optional.of(challenge));
        when(passkeyChallengeRepository.save(any(PasskeyChallengeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passkeyCredentialRepository.findByCredentialIdAndRevokedAtIsNull("credential-1"))
                .thenReturn(Optional.of(credential));
        when(passkeyCredentialRepository.save(any(PasskeyCredentialEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userSessionService.createSession(eq(userId), any(CreateUserSessionCommand.class))).thenReturn(sessionId);
        when(jwtService.issueAccessToken(user, sessionId))
                .thenReturn(new JwtService.IssuedAccessToken("passkey-access-token", now.plusSeconds(3600)));

        AuthResponse response = passkeyService.verifyLogin(
                new VerifyPasskeyLoginRequest(
                        challengeId,
                        rawChallenge,
                        "credential-1",
                        8L,
                        "Pixel 10",
                        "android",
                        "1.0.0"
                ),
                "127.0.0.1",
                "JUnit"
        );

        assertThat(response.authenticated()).isTrue();
        assertThat(response.authMethod()).isEqualTo("PASSKEY");
        assertThat(response.sessionId()).isEqualTo(sessionId);
        assertThat(response.token()).isEqualTo("passkey-access-token");
        assertThat(response.phoneNumber()).isEqualTo(user.getPhoneNumber());
        assertThat(credential.getSignCount()).isEqualTo(8L);
        assertThat(credential.getLastUsedAt()).isNotNull();
        assertThat(challenge.getConsumedAt()).isNotNull();
        assertThat(user.getLastSeenAt()).isNotNull();
    }

    @Test
    void verifyLoginRejectsNegativeSignCount() {
        UUID userId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        String rawChallenge = "login-passkey-challenge";

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setPhoneNumber("+375291234567");
        user.setDisplayName("Alex");

        PasskeyChallengeEntity challenge = new PasskeyChallengeEntity();
        challenge.setId(challengeId);
        challenge.setUserId(userId);
        challenge.setFlowType("LOGIN");
        challenge.setChallengeHash(hash(rawChallenge));
        challenge.setExpiresAt(Instant.now().plusSeconds(300));

        PasskeyCredentialEntity credential = new PasskeyCredentialEntity();
        credential.setId(UUID.randomUUID());
        credential.setUserId(userId);
        credential.setCredentialId("credential-1");
        credential.setPublicKey("public-key");
        credential.setSignCount(3L);

        when(passkeyChallengeRepository.findByIdAndConsumedAtIsNull(challengeId)).thenReturn(Optional.of(challenge));
        when(passkeyCredentialRepository.findByCredentialIdAndRevokedAtIsNull("credential-1"))
                .thenReturn(Optional.of(credential));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ResponseStatusException exception = catchThrowableOfType(
                () -> passkeyService.verifyLogin(
                        new VerifyPasskeyLoginRequest(
                                challengeId,
                                rawChallenge,
                                "credential-1",
                                -1L,
                                "Pixel 10",
                                "android",
                                "1.0.0"
                        ),
                        "127.0.0.1",
                        "JUnit"
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
