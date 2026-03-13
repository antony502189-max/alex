package com.alex.messenger.auth;

import com.alex.messenger.auth.dto.AuthRequest;
import com.alex.messenger.auth.dto.AuthResponse;
import com.alex.messenger.auth.dto.DisableTwoFactorRequest;
import com.alex.messenger.auth.dto.EnableTwoFactorRequest;
import com.alex.messenger.auth.dto.GenerateQrLoginResponse;
import com.alex.messenger.auth.dto.QrLoginBindRequest;
import com.alex.messenger.auth.dto.QrLoginChallengeResponse;
import com.alex.messenger.auth.dto.QrLoginPollRequest;
import com.alex.messenger.auth.dto.QrLoginStatusResponse;
import com.alex.messenger.auth.dto.RefreshTokenRequest;
import com.alex.messenger.auth.dto.RequestLoginCodeRequest;
import com.alex.messenger.auth.dto.RequestLoginCodeResponse;
import com.alex.messenger.auth.dto.TwoFactorStatusResponse;
import com.alex.messenger.auth.dto.VerifyLoginCodeRequest;
import com.alex.messenger.auth.dto.VerifyTwoFactorRequest;
import com.alex.messenger.auth.session.CreateUserSessionCommand;
import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.auth.session.UserSessionEntity;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final LoginCodeChallengeRepository loginCodeChallengeRepository;
    private final AuthTwoFactorChallengeRepository authTwoFactorChallengeRepository;
    private final QrLoginChallengeRepository qrLoginChallengeRepository;
    private final JwtService jwtService;
    private final UserSessionService userSessionService;
    private final AuthProperties authProperties;
    private final TwoFactorPasswordService twoFactorPasswordService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public RequestLoginCodeResponse requestCode(
            RequestLoginCodeRequest request,
            String ipAddress,
            String userAgent
    ) {
        String normalizedPhone = normalizePhoneNumber(request.phoneNumber());
        userRepository.findByPhoneNumber(normalizedPhone)
                .filter(UserEntity::isBot)
                .ifPresent(user -> {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bot accounts cannot sign in interactively");
                });

        Instant now = Instant.now();
        long recentRequests = loginCodeChallengeRepository.countByPhoneNumberAndCreatedAtAfter(
                normalizedPhone,
                now.minus(authProperties.getCode().getRequestWindow())
        );
        if (recentRequests >= authProperties.getCode().getMaxRequestsPerWindow()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many login code requests");
        }

        String code = generateCode(authProperties.getCode().getLength());
        LoginCodeChallengeEntity challenge = new LoginCodeChallengeEntity();
        challenge.setId(UUID.randomUUID());
        challenge.setPhoneNumber(normalizedPhone);
        challenge.setDisplayName(normalizeNullable(request.displayName(), 120));
        challenge.setDeviceName(normalizeNullable(request.deviceName(), 120));
        challenge.setPlatform(normalizeNullable(request.platform(), 32));
        challenge.setAppVersion(normalizeNullable(request.appVersion(), 32));
        challenge.setRequestedByIp(normalizeNullable(ipAddress, 64));
        challenge.setRequestedByUserAgent(normalizeNullable(userAgent, 255));
        challenge.setMaxAttempts(authProperties.getCode().getMaxAttempts());
        challenge.setExpiresAt(now.plus(authProperties.getCode().getTtl()));
        challenge.setCodeHash(hashLoginCode(challenge.getId(), code));

        LoginCodeChallengeEntity saved = loginCodeChallengeRepository.save(challenge);

        return new RequestLoginCodeResponse(
                saved.getId(),
                saved.getPhoneNumber(),
                saved.getExpiresAt(),
                authProperties.getCode().getLength(),
                authProperties.getCode().isExposeDebugCode() ? code : null
        );
    }

    @Transactional
    public AuthResponse verifyCode(
            VerifyLoginCodeRequest request,
            String ipAddress,
            String userAgent
    ) {
        LoginCodeChallengeEntity challenge = loginCodeChallengeRepository.findByIdAndConsumedAtIsNull(request.challengeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Login challenge not found"));
        if (!challenge.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Login code has expired");
        }
        if (challenge.getAttemptCount() != null && challenge.getMaxAttempts() != null
                && challenge.getAttemptCount() >= challenge.getMaxAttempts()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many verification attempts");
        }

        String normalizedCode = request.code().trim();
        if (!hashLoginCode(challenge.getId(), normalizedCode).equals(challenge.getCodeHash())) {
            challenge.setAttemptCount((challenge.getAttemptCount() != null ? challenge.getAttemptCount() : 0) + 1);
            loginCodeChallengeRepository.save(challenge);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login code is invalid");
        }

        challenge.setConsumedAt(Instant.now());
        loginCodeChallengeRepository.save(challenge);

        UserEntity user = userRepository.findByPhoneNumber(challenge.getPhoneNumber())
                .orElseGet(() -> createUser(challenge.getPhoneNumber(), challenge.getDisplayName()));
        if (user.isBot()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bot accounts cannot sign in interactively");
        }
        if (challenge.getDisplayName() != null && !challenge.getDisplayName().isBlank()) {
            user.setDisplayName(challenge.getDisplayName().trim());
        }
        if (isTwoFactorEnabled(user)) {
            return beginTwoFactorChallenge(user, challenge);
        }

        user.setLastSeenAt(Instant.now());
        UserEntity savedUser = userRepository.save(user);
        return createSessionResponse(
                savedUser,
                new SessionClientMetadata(
                        challenge.getDeviceName(),
                        challenge.getPlatform(),
                        challenge.getAppVersion(),
                        ipAddress,
                        userAgent
                ),
                "OTP",
                false
        );
    }

    @Transactional
    public AuthResponse login(AuthRequest request, String ipAddress, String userAgent) {
        String normalizedPhone = normalizePhoneNumber(request.phoneNumber());
        UserEntity user = userRepository.findByPhoneNumber(normalizedPhone)
                .orElseGet(() -> createUser(normalizedPhone, request.displayName()));
        if (user.isBot()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bot accounts cannot sign in interactively");
        }

        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        if (isTwoFactorEnabled(user)) {
            LoginCodeChallengeEntity syntheticChallenge = new LoginCodeChallengeEntity();
            syntheticChallenge.setDisplayName(normalizeNullable(request.displayName(), 120));
            syntheticChallenge.setDeviceName(normalizeNullable(request.deviceName(), 120));
            syntheticChallenge.setPlatform(normalizeNullable(request.platform(), 32));
            syntheticChallenge.setAppVersion(normalizeNullable(request.appVersion(), 32));
            syntheticChallenge.setRequestedByIp(normalizeNullable(ipAddress, 64));
            syntheticChallenge.setRequestedByUserAgent(normalizeNullable(userAgent, 255));
            return beginTwoFactorChallenge(user, syntheticChallenge);
        }

        user.setLastSeenAt(Instant.now());
        UserEntity saved = userRepository.save(user);
        return createSessionResponse(
                saved,
                new SessionClientMetadata(request.deviceName(), request.platform(), request.appVersion(), ipAddress, userAgent),
                "LEGACY_LOGIN",
                false
        );
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request, String ipAddress, String userAgent) {
        String refreshToken = normalizeRequired(request.refreshToken(), "Refresh token", 512);
        UserSessionEntity session = userSessionService.requireActiveRefreshToken(hashRefreshToken(refreshToken));
        UserEntity user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.isBot()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bot accounts cannot sign in interactively");
        }

        String nextRefreshToken = generateRefreshToken();
        Instant refreshExpiresAt = Instant.now().plus(authProperties.getRefresh().getTtl());
        UserSessionEntity rotated = userSessionService.rotateRefreshToken(
                session.getId(),
                hashRefreshToken(nextRefreshToken),
                refreshExpiresAt,
                ipAddress,
                userAgent
        );

        JwtService.IssuedAccessToken accessToken = jwtService.issueAccessToken(user, rotated.getId());
        user.setLastSeenAt(Instant.now());
        userRepository.save(user);
        return new AuthResponse(
                true,
                false,
                accessToken.token(),
                nextRefreshToken,
                rotated.getId(),
                user.getId(),
                user.getPhoneNumber(),
                user.getDisplayName(),
                user.getUsername(),
                accessToken.expiresAt(),
                refreshExpiresAt,
                rotated.getAuthMethod(),
                Boolean.TRUE.equals(rotated.getTrustedSession()),
                null,
                null
        );
    }

    @Transactional
    public GenerateQrLoginResponse generateQrLogin(UUID userId, UUID currentSessionId) {
        UserEntity user = getUser(userId);
        ensureQrApprovalAllowed(user, currentSessionId);

        String qrToken = generateQrToken();
        QrLoginChallengeEntity challenge = new QrLoginChallengeEntity();
        challenge.setUserId(userId);
        challenge.setCreatedBySessionId(currentSessionId);
        challenge.setQrTokenHash(hashQrToken(qrToken));
        challenge.setStatus("NEW");
        challenge.setExpiresAt(Instant.now().plus(authProperties.getQr().getChallengeTtl()));

        QrLoginChallengeEntity saved = qrLoginChallengeRepository.save(challenge);
        return new GenerateQrLoginResponse(saved.getId(), qrToken, saved.getCreatedAt(), saved.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public List<QrLoginChallengeResponse> listQrLoginChallenges(UUID userId, UUID currentSessionId) {
        userSessionService.requireOwnedSession(currentSessionId, userId);
        return qrLoginChallengeRepository.findAllByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(userId).stream()
                .limit(20)
                .map(this::toQrLoginChallengeResponse)
                .toList();
    }

    @Transactional
    public QrLoginChallengeResponse approveQrLogin(UUID userId, UUID currentSessionId, UUID challengeId) {
        UserEntity user = getUser(userId);
        ensureQrApprovalAllowed(user, currentSessionId);
        QrLoginChallengeEntity challenge = getOwnedQrLoginChallenge(userId, challengeId);
        if (isExpired(challenge)) {
            throw new ResponseStatusException(HttpStatus.GONE, "QR login challenge has expired");
        }
        if ("DECLINED".equals(challenge.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "QR login challenge has been declined");
        }
        if ("NEW".equals(challenge.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "QR login challenge has not been scanned yet");
        }
        challenge.setStatus("APPROVED");
        challenge.setApprovedAt(Instant.now());
        challenge.setApprovedBySessionId(currentSessionId);
        return toQrLoginChallengeResponse(qrLoginChallengeRepository.save(challenge));
    }

    @Transactional
    public QrLoginChallengeResponse declineQrLogin(UUID userId, UUID currentSessionId, UUID challengeId) {
        UserEntity user = getUser(userId);
        ensureQrApprovalAllowed(user, currentSessionId);
        QrLoginChallengeEntity challenge = getOwnedQrLoginChallenge(userId, challengeId);
        if (isExpired(challenge)) {
            throw new ResponseStatusException(HttpStatus.GONE, "QR login challenge has expired");
        }
        if ("APPROVED".equals(challenge.getStatus()) || "DECLINED".equals(challenge.getStatus())) {
            return toQrLoginChallengeResponse(challenge);
        }
        challenge.setStatus("DECLINED");
        challenge.setDeclinedAt(Instant.now());
        return toQrLoginChallengeResponse(qrLoginChallengeRepository.save(challenge));
    }

    @Transactional
    public QrLoginStatusResponse bindQrLogin(
            QrLoginBindRequest request,
            String ipAddress,
            String userAgent
    ) {
        QrLoginChallengeEntity challenge = getQrLoginChallengeByToken(request.qrToken());
        if (isExpired(challenge)) {
            return toQrLoginStatusResponse("EXPIRED", challenge, null);
        }
        if ("DECLINED".equals(challenge.getStatus())) {
            return toQrLoginStatusResponse("DECLINED", challenge, null);
        }
        if ("NEW".equals(challenge.getStatus())) {
            challenge.setBoundDeviceName(normalizeNullable(request.deviceName(), 120));
            challenge.setBoundPlatform(normalizeNullable(request.platform(), 32));
            challenge.setBoundAppVersion(normalizeNullable(request.appVersion(), 32));
            challenge.setBoundIpAddress(normalizeNullable(ipAddress, 64));
            challenge.setBoundUserAgent(normalizeNullable(userAgent, 255));
            challenge.setBoundAt(Instant.now());
            challenge.setStatus("PENDING_APPROVAL");
            qrLoginChallengeRepository.save(challenge);
        }
        return toQrLoginStatusResponse(challenge.getStatus(), challenge, null);
    }

    @Transactional
    public QrLoginStatusResponse pollQrLogin(
            QrLoginPollRequest request,
            String ipAddress,
            String userAgent
    ) {
        QrLoginChallengeEntity challenge = getQrLoginChallengeByToken(request.qrToken());
        if (isExpired(challenge)) {
            return toQrLoginStatusResponse("EXPIRED", challenge, null);
        }
        if ("NEW".equals(challenge.getStatus())) {
            return toQrLoginStatusResponse("AWAITING_SCAN", challenge, null);
        }
        if ("PENDING_APPROVAL".equals(challenge.getStatus())) {
            return toQrLoginStatusResponse("PENDING_APPROVAL", challenge, null);
        }
        if ("DECLINED".equals(challenge.getStatus())) {
            return toQrLoginStatusResponse("DECLINED", challenge, null);
        }
        if (!"APPROVED".equals(challenge.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "QR login challenge is in an unsupported state");
        }

        UserEntity user = getUser(challenge.getUserId());
        user.setLastSeenAt(Instant.now());
        UserEntity savedUser = userRepository.save(user);
        AuthResponse auth = createSessionResponse(
                savedUser,
                new SessionClientMetadata(
                        challenge.getBoundDeviceName(),
                        challenge.getBoundPlatform(),
                        challenge.getBoundAppVersion(),
                        ipAddress != null ? ipAddress : challenge.getBoundIpAddress(),
                        userAgent != null ? userAgent : challenge.getBoundUserAgent()
                ),
                "QR_LOGIN",
                false
        );
        challenge.setConsumedAt(Instant.now());
        qrLoginChallengeRepository.save(challenge);
        return toQrLoginStatusResponse("AUTHENTICATED", challenge, auth);
    }

    @Transactional
    public int deleteExpiredOrConsumedChallenges(Instant cutoff, int batchSize) {
        List<LoginCodeChallengeEntity> challenges = loginCodeChallengeRepository.findCleanupBatch(
                cutoff,
                PageRequest.of(0, Math.max(1, batchSize))
        );
        if (challenges.isEmpty()) {
            return 0;
        }
        loginCodeChallengeRepository.deleteAllInBatch(challenges);
        return challenges.size();
    }

    @Transactional
    public int deleteExpiredOrConsumedTwoFactorChallenges(Instant cutoff, int batchSize) {
        List<AuthTwoFactorChallengeEntity> challenges = authTwoFactorChallengeRepository.findCleanupBatch(
                cutoff,
                PageRequest.of(0, Math.max(1, batchSize))
        );
        if (challenges.isEmpty()) {
            return 0;
        }
        authTwoFactorChallengeRepository.deleteAllInBatch(challenges);
        return challenges.size();
    }

    @Transactional
    public int deleteExpiredOrFinishedQrChallenges(Instant cutoff, int batchSize) {
        List<QrLoginChallengeEntity> challenges = qrLoginChallengeRepository.findCleanupBatch(
                cutoff,
                PageRequest.of(0, Math.max(1, batchSize))
        );
        if (challenges.isEmpty()) {
            return 0;
        }
        qrLoginChallengeRepository.deleteAllInBatch(challenges);
        return challenges.size();
    }

    @Transactional(readOnly = true)
    public TwoFactorStatusResponse getTwoFactorStatus(UUID userId) {
        UserEntity user = getUser(userId);
        return new TwoFactorStatusResponse(
                isTwoFactorEnabled(user),
                user.getTwoFactorHint(),
                user.getTwoFactorEnabledAt()
        );
    }

    @Transactional
    public TwoFactorStatusResponse enableTwoFactor(UUID userId, UUID currentSessionId, EnableTwoFactorRequest request) {
        UserEntity user = getUser(userId);
        String password = normalizeTwoFactorPassword(request.password());
        String salt = twoFactorPasswordService.generateSalt();
        user.setTwoFactorPasswordSalt(salt);
        user.setTwoFactorPasswordHash(twoFactorPasswordService.hashPassword(password, salt));
        user.setTwoFactorHint(normalizeNullable(request.hint(), 120));
        user.setTwoFactorEnabledAt(Instant.now());
        userRepository.save(user);
        userSessionService.markTrusted(currentSessionId, userId);
        return new TwoFactorStatusResponse(true, user.getTwoFactorHint(), user.getTwoFactorEnabledAt());
    }

    @Transactional
    public TwoFactorStatusResponse disableTwoFactor(UUID userId, DisableTwoFactorRequest request) {
        UserEntity user = getUser(userId);
        requireTwoFactorPassword(user, request.password());
        user.setTwoFactorPasswordSalt(null);
        user.setTwoFactorPasswordHash(null);
        user.setTwoFactorHint(null);
        user.setTwoFactorEnabledAt(null);
        userRepository.save(user);
        return new TwoFactorStatusResponse(false, null, null);
    }

    @Transactional
    public AuthResponse verifyTwoFactor(
            VerifyTwoFactorRequest request,
            String ipAddress,
            String userAgent
    ) {
        AuthTwoFactorChallengeEntity challenge = authTwoFactorChallengeRepository.findByIdAndConsumedAtIsNull(request.challengeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Two-factor challenge not found"));
        if (!challenge.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Two-factor challenge has expired");
        }
        if (challenge.getAttemptCount() != null && challenge.getMaxAttempts() != null
                && challenge.getAttemptCount() >= challenge.getMaxAttempts()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many two-factor verification attempts");
        }

        UserEntity user = getUser(challenge.getUserId());
        requireTwoFactorPassword(user, request.password(), challenge);

        if (challenge.getDisplayName() != null && !challenge.getDisplayName().isBlank()) {
            user.setDisplayName(challenge.getDisplayName().trim());
        }
        user.setLastSeenAt(Instant.now());
        UserEntity savedUser = userRepository.save(user);
        challenge.setConsumedAt(Instant.now());
        authTwoFactorChallengeRepository.save(challenge);

        return createSessionResponse(
                savedUser,
                new SessionClientMetadata(
                        challenge.getDeviceName(),
                        challenge.getPlatform(),
                        challenge.getAppVersion(),
                        ipAddress,
                        userAgent
                ),
                "OTP_2FA",
                Boolean.TRUE.equals(request.trustSession())
        );
    }

    private void ensureQrApprovalAllowed(UserEntity user, UUID currentSessionId) {
        UserSessionEntity session = userSessionService.requireOwnedSession(currentSessionId, user.getId());
        if (isTwoFactorEnabled(user) && !Boolean.TRUE.equals(session.getTrustedSession())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Use a trusted session to approve QR login while two-factor authentication is enabled"
            );
        }
    }

    private QrLoginChallengeEntity getOwnedQrLoginChallenge(UUID userId, UUID challengeId) {
        return qrLoginChallengeRepository.findByIdAndUserIdAndConsumedAtIsNull(challengeId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR login challenge not found"));
    }

    private QrLoginChallengeEntity getQrLoginChallengeByToken(String qrToken) {
        return qrLoginChallengeRepository.findByQrTokenHashAndConsumedAtIsNull(hashQrToken(qrToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR login challenge not found"));
    }

    private boolean isExpired(QrLoginChallengeEntity challenge) {
        return challenge.getExpiresAt() == null || !challenge.getExpiresAt().isAfter(Instant.now());
    }

    private QrLoginChallengeResponse toQrLoginChallengeResponse(QrLoginChallengeEntity challenge) {
        return new QrLoginChallengeResponse(
                challenge.getId(),
                isExpired(challenge) ? "EXPIRED" : challenge.getStatus(),
                challenge.getBoundDeviceName(),
                challenge.getBoundPlatform(),
                challenge.getBoundAppVersion(),
                challenge.getBoundIpAddress(),
                challenge.getBoundUserAgent(),
                challenge.getCreatedAt(),
                challenge.getExpiresAt(),
                challenge.getBoundAt(),
                challenge.getApprovedAt()
        );
    }

    private QrLoginStatusResponse toQrLoginStatusResponse(
            String status,
            QrLoginChallengeEntity challenge,
            AuthResponse auth
    ) {
        return new QrLoginStatusResponse(
                status,
                challenge.getExpiresAt(),
                challenge.getBoundDeviceName(),
                challenge.getBoundPlatform(),
                challenge.getBoundAppVersion(),
                auth
        );
    }

    private UserEntity createUser(String phoneNumber, String displayName) {
        UserEntity user = new UserEntity();
        user.setPhoneNumber(phoneNumber);
        user.setDisplayName(
                displayName != null && !displayName.isBlank()
                        ? displayName.trim()
                        : "User " + phoneNumber.substring(Math.max(0, phoneNumber.length() - 4))
        );
        return user;
    }

    private AuthResponse createSessionResponse(
            UserEntity user,
            SessionClientMetadata metadata,
            String authMethod,
            boolean trustedSession
    ) {
        String refreshToken = generateRefreshToken();
        Instant refreshExpiresAt = Instant.now().plus(authProperties.getRefresh().getTtl());
        Instant trustedAt = trustedSession ? Instant.now() : null;
        UUID sessionId = userSessionService.createSession(
                user.getId(),
                new CreateUserSessionCommand(
                        metadata.deviceName(),
                        metadata.platform(),
                        metadata.appVersion(),
                        metadata.userAgent(),
                        metadata.ipAddress(),
                        authMethod,
                        hashRefreshToken(refreshToken),
                        refreshExpiresAt,
                        trustedSession,
                        trustedAt
                )
        );
        JwtService.IssuedAccessToken accessToken = jwtService.issueAccessToken(user, sessionId);
        return new AuthResponse(
                true,
                false,
                accessToken.token(),
                refreshToken,
                sessionId,
                user.getId(),
                user.getPhoneNumber(),
                user.getDisplayName(),
                user.getUsername(),
                accessToken.expiresAt(),
                refreshExpiresAt,
                authMethod,
                trustedSession,
                null,
                null
        );
    }

    private AuthResponse beginTwoFactorChallenge(UserEntity user, LoginCodeChallengeEntity sourceChallenge) {
        AuthTwoFactorChallengeEntity challenge = new AuthTwoFactorChallengeEntity();
        challenge.setUserId(user.getId());
        challenge.setDisplayName(normalizeNullable(sourceChallenge.getDisplayName(), 120));
        challenge.setDeviceName(normalizeNullable(sourceChallenge.getDeviceName(), 120));
        challenge.setPlatform(normalizeNullable(sourceChallenge.getPlatform(), 32));
        challenge.setAppVersion(normalizeNullable(sourceChallenge.getAppVersion(), 32));
        challenge.setRequestedByIp(normalizeNullable(sourceChallenge.getRequestedByIp(), 64));
        challenge.setRequestedByUserAgent(normalizeNullable(sourceChallenge.getRequestedByUserAgent(), 255));
        challenge.setMaxAttempts(authProperties.getTwoFactor().getMaxAttempts());
        challenge.setExpiresAt(Instant.now().plus(authProperties.getTwoFactor().getChallengeTtl()));
        AuthTwoFactorChallengeEntity saved = authTwoFactorChallengeRepository.save(challenge);
        return new AuthResponse(
                false,
                true,
                null,
                null,
                null,
                user.getId(),
                user.getPhoneNumber(),
                user.getDisplayName(),
                user.getUsername(),
                null,
                null,
                null,
                null,
                saved.getId(),
                user.getTwoFactorHint()
        );
    }

    private boolean isTwoFactorEnabled(UserEntity user) {
        return user.getTwoFactorPasswordHash() != null
                && user.getTwoFactorPasswordSalt() != null
                && user.getTwoFactorEnabledAt() != null;
    }

    private void requireTwoFactorPassword(UserEntity user, String rawPassword) {
        requireTwoFactorPassword(user, rawPassword, null);
    }

    private void requireTwoFactorPassword(
            UserEntity user,
            String rawPassword,
            AuthTwoFactorChallengeEntity challenge
    ) {
        if (!isTwoFactorEnabled(user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Two-factor authentication is not enabled");
        }
        String normalizedPassword = normalizeTwoFactorPassword(rawPassword);
        if (!twoFactorPasswordService.matches(
                normalizedPassword,
                user.getTwoFactorPasswordSalt(),
                user.getTwoFactorPasswordHash()
        )) {
            if (challenge != null) {
                challenge.setAttemptCount((challenge.getAttemptCount() != null ? challenge.getAttemptCount() : 0) + 1);
                authTwoFactorChallengeRepository.save(challenge);
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Two-factor password is invalid");
        }
    }

    private UserEntity getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String generateCode(int length) {
        int boundedLength = Math.max(4, Math.min(length, 8));
        int max = (int) Math.pow(10, boundedLength);
        int value = secureRandom.nextInt(max);
        return String.format("%0" + boundedLength + "d", value);
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateQrToken() {
        byte[] bytes = new byte[Math.max(16, authProperties.getQr().getTokenBytes())];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashLoginCode(UUID challengeId, String code) {
        return hash("%s:%s".formatted(challengeId != null ? challengeId : "<pending>", code != null ? code.trim() : ""));
    }

    private String hashRefreshToken(String refreshToken) {
        return hash(refreshToken);
    }

    private String hashQrToken(String qrToken) {
        return hash(normalizeRequired(qrToken, "QR token", 512));
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to hash auth secret", exception);
        }
    }

    private String normalizePhoneNumber(String phoneNumber) {
        return normalizeRequired(phoneNumber, "Phone number", 32);
    }

    private String normalizeRequired(String value, String fieldName, int maxLength) {
        String normalized = normalizeNullable(value, maxLength);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "%s is required".formatted(fieldName));
        }
        return normalized;
    }

    private String normalizeNullable(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private String normalizeTwoFactorPassword(String password) {
        String normalized = normalizeRequired(password, "Two-factor password", 128);
        if (normalized.length() < authProperties.getTwoFactor().getMinPasswordLength()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Two-factor password must be at least %d characters".formatted(
                            authProperties.getTwoFactor().getMinPasswordLength()
                    )
            );
        }
        return normalized;
    }

    private record SessionClientMetadata(
            String deviceName,
            String platform,
            String appVersion,
            String ipAddress,
            String userAgent
    ) {
    }
}
