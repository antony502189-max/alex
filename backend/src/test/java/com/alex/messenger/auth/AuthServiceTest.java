package com.alex.messenger.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.auth.dto.AuthResponse;
import com.alex.messenger.auth.dto.GenerateQrLoginResponse;
import com.alex.messenger.auth.dto.QrLoginBindRequest;
import com.alex.messenger.auth.dto.QrLoginStatusResponse;
import com.alex.messenger.auth.dto.RefreshTokenRequest;
import com.alex.messenger.auth.dto.RequestLoginCodeRequest;
import com.alex.messenger.auth.dto.RequestLoginCodeResponse;
import com.alex.messenger.auth.dto.VerifyLoginCodeRequest;
import com.alex.messenger.auth.dto.VerifyTwoFactorRequest;
import com.alex.messenger.auth.session.CreateUserSessionCommand;
import com.alex.messenger.auth.session.UserSessionEntity;
import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
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
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginCodeChallengeRepository loginCodeChallengeRepository;

    @Mock
    private AuthTwoFactorChallengeRepository authTwoFactorChallengeRepository;

    @Mock
    private QrLoginChallengeRepository qrLoginChallengeRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserSessionService userSessionService;

    private AuthProperties authProperties;
    private TwoFactorPasswordService twoFactorPasswordService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        authProperties.getCode().setExposeDebugCode(true);
        authProperties.getCode().setMaxRequestsPerWindow(3);
        authProperties.getCode().setRequestWindow(Duration.ofMinutes(10));
        authProperties.getRefresh().setTtl(Duration.ofDays(30));
        twoFactorPasswordService = new TwoFactorPasswordService(authProperties);
        authService = new AuthService(
                userRepository,
                loginCodeChallengeRepository,
                authTwoFactorChallengeRepository,
                qrLoginChallengeRepository,
                jwtService,
                userSessionService,
                authProperties,
                twoFactorPasswordService
        );
    }

    @Test
    void requestCodeRejectsWhenRateLimitIsReached() {
        when(userRepository.findByPhoneNumber("+375291234567")).thenReturn(Optional.empty());
        when(loginCodeChallengeRepository.countByPhoneNumberAndCreatedAtAfter(eq("+375291234567"), any()))
                .thenReturn(3L);

        ResponseStatusException exception = catchThrowableOfType(
                () -> authService.requestCode(
                        new RequestLoginCodeRequest("+375291234567", "Alex", "android", "android", "0.1.0"),
                        "127.0.0.1",
                        "JUnit"
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(loginCodeChallengeRepository, never()).save(any());
    }

    @Test
    void requestCodeReturnsDebugCodeInDevelopmentMode() {
        when(userRepository.findByPhoneNumber("+375291234567")).thenReturn(Optional.empty());
        when(loginCodeChallengeRepository.countByPhoneNumberAndCreatedAtAfter(eq("+375291234567"), any()))
                .thenReturn(0L);
        when(loginCodeChallengeRepository.save(any(LoginCodeChallengeEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RequestLoginCodeResponse response = authService.requestCode(
                new RequestLoginCodeRequest("+375291234567", "Alex", "android", "android", "0.1.0"),
                "127.0.0.1",
                "JUnit"
        );

        assertThat(response.challengeId()).isNotNull();
        assertThat(response.debugCode()).hasSize(authProperties.getCode().getLength());
    }

    @Test
    void verifyCodeCreatesOtpSession() {
        UUID challengeId = UUID.randomUUID();
        LoginCodeChallengeEntity challenge = new LoginCodeChallengeEntity();
        challenge.setId(challengeId);
        challenge.setPhoneNumber("+375291234567");
        challenge.setDisplayName("Alex");
        challenge.setDeviceName("android");
        challenge.setPlatform("android");
        challenge.setAppVersion("0.1.0");
        challenge.setAttemptCount(0);
        challenge.setMaxAttempts(5);
        challenge.setExpiresAt(Instant.now().plusSeconds(300));
        challenge.setCodeHash(hash("%s:%s".formatted(challengeId, "123456")));

        UserEntity savedUser = new UserEntity();
        savedUser.setId(UUID.randomUUID());
        savedUser.setPhoneNumber("+375291234567");
        savedUser.setDisplayName("Alex");

        UUID sessionId = UUID.randomUUID();
        Instant accessExpiresAt = Instant.parse("2026-03-12T10:15:00Z");

        when(loginCodeChallengeRepository.findByIdAndConsumedAtIsNull(challengeId)).thenReturn(Optional.of(challenge));
        when(loginCodeChallengeRepository.save(any(LoginCodeChallengeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByPhoneNumber("+375291234567")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        when(userSessionService.createSession(eq(savedUser.getId()), any(CreateUserSessionCommand.class))).thenReturn(sessionId);
        when(jwtService.issueAccessToken(savedUser, sessionId))
                .thenReturn(new JwtService.IssuedAccessToken("access-token", accessExpiresAt));

        AuthResponse response = authService.verifyCode(
                new VerifyLoginCodeRequest(challengeId, "123456"),
                "127.0.0.1",
                "JUnit"
        );

        assertThat(response.token()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.sessionId()).isEqualTo(sessionId);
        assertThat(response.authMethod()).isEqualTo("OTP");
        assertThat(response.authenticated()).isTrue();
        assertThat(response.requiresTwoFactor()).isFalse();
        assertThat(response.trustedSession()).isFalse();
    }

    @Test
    void verifyCodeReturnsPendingTwoFactorChallengeWhenEnabled() {
        UUID challengeId = UUID.randomUUID();
        LoginCodeChallengeEntity challenge = new LoginCodeChallengeEntity();
        challenge.setId(challengeId);
        challenge.setPhoneNumber("+375291234567");
        challenge.setDisplayName("Alex");
        challenge.setDeviceName("android");
        challenge.setPlatform("android");
        challenge.setAppVersion("0.1.0");
        challenge.setAttemptCount(0);
        challenge.setMaxAttempts(5);
        challenge.setExpiresAt(Instant.now().plusSeconds(300));
        challenge.setCodeHash(hash("%s:%s".formatted(challengeId, "123456")));

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setPhoneNumber("+375291234567");
        user.setDisplayName("Alex");
        user.setTwoFactorPasswordSalt(twoFactorPasswordService.generateSalt());
        user.setTwoFactorPasswordHash(twoFactorPasswordService.hashPassword("two-factor-pass", user.getTwoFactorPasswordSalt()));
        user.setTwoFactorHint("telegram");
        user.setTwoFactorEnabledAt(Instant.now().minusSeconds(60));

        when(loginCodeChallengeRepository.findByIdAndConsumedAtIsNull(challengeId)).thenReturn(Optional.of(challenge));
        when(loginCodeChallengeRepository.save(any(LoginCodeChallengeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByPhoneNumber("+375291234567")).thenReturn(Optional.of(user));
        when(authTwoFactorChallengeRepository.save(any(AuthTwoFactorChallengeEntity.class)))
                .thenAnswer(invocation -> {
                    AuthTwoFactorChallengeEntity saved = invocation.getArgument(0);
                    if (saved.getId() == null) {
                        saved.setId(UUID.randomUUID());
                    }
                    return saved;
                });

        AuthResponse response = authService.verifyCode(
                new VerifyLoginCodeRequest(challengeId, "123456"),
                "127.0.0.1",
                "JUnit"
        );

        assertThat(response.authenticated()).isFalse();
        assertThat(response.requiresTwoFactor()).isTrue();
        assertThat(response.token()).isNull();
        assertThat(response.twoFactorChallengeId()).isNotNull();
        assertThat(response.twoFactorHint()).isEqualTo("telegram");
        verify(userSessionService, never()).createSession(any(), any(CreateUserSessionCommand.class));
    }

    @Test
    void verifyTwoFactorCreatesTrustedSession() {
        UUID challengeId = UUID.randomUUID();
        AuthTwoFactorChallengeEntity challenge = new AuthTwoFactorChallengeEntity();
        challenge.setId(challengeId);
        challenge.setUserId(UUID.randomUUID());
        challenge.setDisplayName("Alex");
        challenge.setDeviceName("android");
        challenge.setPlatform("android");
        challenge.setAppVersion("0.1.0");
        challenge.setAttemptCount(0);
        challenge.setMaxAttempts(5);
        challenge.setExpiresAt(Instant.now().plusSeconds(300));

        UserEntity user = new UserEntity();
        user.setId(challenge.getUserId());
        user.setPhoneNumber("+375291234567");
        user.setDisplayName("Alex");
        user.setTwoFactorPasswordSalt(twoFactorPasswordService.generateSalt());
        user.setTwoFactorPasswordHash(twoFactorPasswordService.hashPassword("two-factor-pass", user.getTwoFactorPasswordSalt()));
        user.setTwoFactorHint("telegram");
        user.setTwoFactorEnabledAt(Instant.now().minusSeconds(60));

        UUID sessionId = UUID.randomUUID();
        Instant accessExpiresAt = Instant.parse("2026-03-12T10:15:00Z");

        when(authTwoFactorChallengeRepository.findByIdAndConsumedAtIsNull(challengeId)).thenReturn(Optional.of(challenge));
        when(authTwoFactorChallengeRepository.save(any(AuthTwoFactorChallengeEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(challenge.getUserId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenReturn(user);
        when(userSessionService.createSession(eq(user.getId()), any(CreateUserSessionCommand.class))).thenReturn(sessionId);
        when(jwtService.issueAccessToken(user, sessionId))
                .thenReturn(new JwtService.IssuedAccessToken("two-factor-access-token", accessExpiresAt));

        AuthResponse response = authService.verifyTwoFactor(
                new VerifyTwoFactorRequest(challengeId, "two-factor-pass", true),
                "127.0.0.1",
                "JUnit"
        );

        assertThat(response.authenticated()).isTrue();
        assertThat(response.requiresTwoFactor()).isFalse();
        assertThat(response.token()).isEqualTo("two-factor-access-token");
        assertThat(response.trustedSession()).isTrue();
        assertThat(response.authMethod()).isEqualTo("OTP_2FA");
    }

    @Test
    void generateQrLoginRequiresTrustedSessionWhenTwoFactorIsEnabled() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setPhoneNumber("+375291234567");
        user.setDisplayName("Alex");
        user.setTwoFactorPasswordSalt(twoFactorPasswordService.generateSalt());
        user.setTwoFactorPasswordHash(twoFactorPasswordService.hashPassword("two-factor-pass", user.getTwoFactorPasswordSalt()));
        user.setTwoFactorEnabledAt(Instant.now().minusSeconds(60));

        UserSessionEntity session = new UserSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setTrustedSession(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSessionService.requireOwnedSession(sessionId, userId)).thenReturn(session);

        ResponseStatusException exception = catchThrowableOfType(
                () -> authService.generateQrLogin(userId, sessionId),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(qrLoginChallengeRepository, never()).save(any(QrLoginChallengeEntity.class));
    }

    @Test
    void pollQrLoginCreatesQrAuthenticatedSessionAfterApproval() {
        String qrToken = "qr-secret-token";
        QrLoginChallengeEntity challenge = new QrLoginChallengeEntity();
        challenge.setId(UUID.randomUUID());
        challenge.setUserId(UUID.randomUUID());
        challenge.setQrTokenHash(hash(qrToken));
        challenge.setStatus("APPROVED");
        challenge.setBoundDeviceName("Web browser");
        challenge.setBoundPlatform("web");
        challenge.setBoundAppVersion("1.0.0");
        challenge.setBoundIpAddress("127.0.0.1");
        challenge.setBoundUserAgent("Browser");
        challenge.setExpiresAt(Instant.now().plusSeconds(300));

        UserEntity user = new UserEntity();
        user.setId(challenge.getUserId());
        user.setPhoneNumber("+375291234567");
        user.setDisplayName("Alex");

        UUID sessionId = UUID.randomUUID();
        Instant accessExpiresAt = Instant.parse("2026-03-12T12:00:00Z");

        when(qrLoginChallengeRepository.findByQrTokenHashAndConsumedAtIsNull(hash(qrToken)))
                .thenReturn(Optional.of(challenge));
        when(qrLoginChallengeRepository.save(any(QrLoginChallengeEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(challenge.getUserId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenReturn(user);
        when(userSessionService.createSession(eq(user.getId()), any(CreateUserSessionCommand.class))).thenReturn(sessionId);
        when(jwtService.issueAccessToken(user, sessionId))
                .thenReturn(new JwtService.IssuedAccessToken("qr-access-token", accessExpiresAt));

        QrLoginStatusResponse response = authService.pollQrLogin(
                new com.alex.messenger.auth.dto.QrLoginPollRequest(qrToken),
                "127.0.0.1",
                "Browser"
        );

        assertThat(response.status()).isEqualTo("AUTHENTICATED");
        assertThat(response.auth()).isNotNull();
        assertThat(response.auth().authMethod()).isEqualTo("QR_LOGIN");
        assertThat(response.auth().token()).isEqualTo("qr-access-token");
        assertThat(response.auth().trustedSession()).isFalse();
        assertThat(challenge.getConsumedAt()).isNotNull();
    }

    @Test
    void refreshRotatesSessionTokens() {
        UserSessionEntity session = new UserSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(UUID.randomUUID());
        session.setAuthMethod("OTP");
        session.setRefreshTokenExpiresAt(Instant.now().plus(Duration.ofDays(10)));

        UserEntity user = new UserEntity();
        user.setId(session.getUserId());
        user.setPhoneNumber("+375291234567");
        user.setDisplayName("Alex");

        when(userSessionService.requireActiveRefreshToken(any())).thenReturn(session);
        when(userRepository.findById(session.getUserId())).thenReturn(Optional.of(user));
        when(userSessionService.rotateRefreshToken(eq(session.getId()), any(), any(), eq("127.0.0.1"), eq("JUnit")))
                .thenReturn(session);
        when(userRepository.save(user)).thenReturn(user);
        when(jwtService.issueAccessToken(user, session.getId()))
                .thenReturn(new JwtService.IssuedAccessToken("new-access-token", Instant.parse("2026-03-12T11:00:00Z")));

        AuthResponse response = authService.refresh(
                new RefreshTokenRequest("refresh-token"),
                "127.0.0.1",
                "JUnit"
        );

        assertThat(response.token()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.sessionId()).isEqualTo(session.getId());
        assertThat(response.authMethod()).isEqualTo("OTP");
        assertThat(response.authenticated()).isTrue();
    }

    @Test
    void deleteExpiredOrConsumedChallengesRemovesCleanupBatch() {
        LoginCodeChallengeEntity consumedChallenge = new LoginCodeChallengeEntity();
        consumedChallenge.setId(UUID.randomUUID());
        consumedChallenge.setConsumedAt(Instant.now().minusSeconds(10));

        LoginCodeChallengeEntity expiredChallenge = new LoginCodeChallengeEntity();
        expiredChallenge.setId(UUID.randomUUID());
        expiredChallenge.setExpiresAt(Instant.now().minusSeconds(10));

        when(loginCodeChallengeRepository.findCleanupBatch(any(), any()))
                .thenReturn(List.of(consumedChallenge, expiredChallenge));

        int deletedCount = authService.deleteExpiredOrConsumedChallenges(Instant.now(), 50);

        assertThat(deletedCount).isEqualTo(2);
        verify(loginCodeChallengeRepository).deleteAllInBatch(List.of(consumedChallenge, expiredChallenge));
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
