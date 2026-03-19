package com.alex.messenger.auth;

import com.alex.messenger.auth.dto.AuthResponse;
import com.alex.messenger.auth.dto.PasskeyCredentialResponse;
import com.alex.messenger.auth.dto.PasskeyLoginOptionsRequest;
import com.alex.messenger.auth.dto.PasskeyLoginOptionsResponse;
import com.alex.messenger.auth.dto.PasskeyRegistrationOptionsResponse;
import com.alex.messenger.auth.dto.VerifyPasskeyLoginRequest;
import com.alex.messenger.auth.dto.VerifyPasskeyRegistrationRequest;
import com.alex.messenger.auth.session.CreateUserSessionCommand;
import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PasskeyService {

    private final UserRepository userRepository;
    private final PasskeyCredentialRepository passkeyCredentialRepository;
    private final PasskeyChallengeRepository passkeyChallengeRepository;
    private final UserSessionService userSessionService;
    private final JwtService jwtService;
    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public PasskeyRegistrationOptionsResponse requestRegistrationOptions(UUID userId, UUID sessionId) {
        UserEntity user = requireUser(userId);
        userSessionService.requireOwnedSession(sessionId, userId);
        if (passkeyCredentialRepository.countByUserIdAndRevokedAtIsNull(userId)
                >= authProperties.getPasskeys().getMaxCredentialsPerUser()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Passkey limit reached");
        }
        String challenge = generateChallenge();
        PasskeyChallengeEntity entity = new PasskeyChallengeEntity();
        entity.setUserId(userId);
        entity.setFlowType("REGISTER");
        entity.setChallengeHash(hash(normalizeRequired(challenge, "Challenge", 512)));
        entity.setExpiresAt(Instant.now().plus(authProperties.getPasskeys().getChallengeTtl()));
        PasskeyChallengeEntity saved = passkeyChallengeRepository.save(entity);
        return new PasskeyRegistrationOptionsResponse(
                saved.getId(),
                challenge,
                user.getId(),
                user.getPhoneNumber(),
                user.getDisplayName(),
                saved.getExpiresAt()
        );
    }

    @Transactional
    public PasskeyCredentialResponse verifyRegistration(
            UUID userId,
            UUID sessionId,
            VerifyPasskeyRegistrationRequest request
    ) {
        userSessionService.requireOwnedSession(sessionId, userId);
        PasskeyChallengeEntity challenge = getChallenge(request.challengeId(), "REGISTER");
        if (!userId.equals(challenge.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Passkey challenge access denied");
        }
        verifyChallenge(challenge, request.challenge());
        if (passkeyCredentialRepository.findByCredentialIdAndRevokedAtIsNull(normalizeRequired(
                request.credentialId(),
                "Credential id",
                255
        )).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Passkey credential already registered");
        }

        PasskeyCredentialEntity entity = new PasskeyCredentialEntity();
        entity.setUserId(userId);
        entity.setCredentialId(normalizeRequired(request.credentialId(), "Credential id", 255));
        entity.setPublicKey(normalizeRequired(request.publicKey(), "Public key", 8192));
        entity.setTransports(normalizeNullable(request.transports(), 255));
        entity.setLabel(normalizeNullable(request.label(), 120));
        entity.setSignCount(resolveRegistrationSignCount(request.signCount()));
        PasskeyCredentialEntity saved = passkeyCredentialRepository.save(entity);
        challenge.setConsumedAt(Instant.now());
        passkeyChallengeRepository.save(challenge);
        return toResponse(saved);
    }

    @Transactional
    public PasskeyLoginOptionsResponse requestLoginOptions(
            PasskeyLoginOptionsRequest request,
            String ipAddress,
            String userAgent
    ) {
        UserEntity user = userRepository.findByPhoneNumber(normalizeRequired(request.phoneNumber(), "Phone number", 32))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.isBot()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bot accounts cannot sign in interactively");
        }
        if (passkeyCredentialRepository.countByUserIdAndRevokedAtIsNull(user.getId()) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Passkeys are not configured for this account");
        }

        String challenge = generateChallenge();
        PasskeyChallengeEntity entity = new PasskeyChallengeEntity();
        entity.setUserId(user.getId());
        entity.setFlowType("LOGIN");
        entity.setChallengeHash(hash(normalizeRequired(challenge, "Challenge", 512)));
        entity.setRequestedPhoneNumber(user.getPhoneNumber());
        entity.setDeviceName(normalizeNullable(request.deviceName(), 120));
        entity.setPlatform(normalizeNullable(request.platform(), 32));
        entity.setAppVersion(normalizeNullable(request.appVersion(), 32));
        entity.setRequestedByIp(normalizeNullable(ipAddress, 64));
        entity.setRequestedByUserAgent(normalizeNullable(userAgent, 255));
        entity.setExpiresAt(Instant.now().plus(authProperties.getPasskeys().getChallengeTtl()));
        PasskeyChallengeEntity saved = passkeyChallengeRepository.save(entity);
        return new PasskeyLoginOptionsResponse(
                saved.getId(),
                challenge,
                user.getId(),
                user.getPhoneNumber(),
                saved.getExpiresAt()
        );
    }

    @Transactional
    public AuthResponse verifyLogin(
            VerifyPasskeyLoginRequest request,
            String ipAddress,
            String userAgent
    ) {
        PasskeyChallengeEntity challenge = getChallenge(request.challengeId(), "LOGIN");
        verifyChallenge(challenge, request.challenge());

        UserEntity user = requireUser(challenge.getUserId());
        if (user.isBot()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bot accounts cannot sign in interactively");
        }
        PasskeyCredentialEntity credential = passkeyCredentialRepository.findByCredentialIdAndRevokedAtIsNull(
                normalizeRequired(request.credentialId(), "Credential id", 255)
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Passkey credential is invalid"));
        if (!user.getId().equals(credential.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Passkey credential does not belong to this user");
        }

        Instant now = Instant.now();
        credential.setLastUsedAt(now);
        credential.setSignCount(resolveLoginSignCount(credential.getSignCount(), request.signCount()));
        passkeyCredentialRepository.save(credential);

        challenge.setConsumedAt(now);
        passkeyChallengeRepository.save(challenge);

        user.setLastSeenAt(now);
        UserEntity savedUser = userRepository.save(user);

        String refreshToken = generateRefreshToken();
        Instant refreshExpiresAt = now.plus(authProperties.getRefresh().getTtl());
        UUID sessionId = userSessionService.createSession(
                savedUser.getId(),
                new CreateUserSessionCommand(
                        normalizeNullable(request.deviceName(), 120),
                        normalizeNullable(request.platform(), 32),
                        normalizeNullable(request.appVersion(), 32),
                        normalizeNullable(userAgent, 255),
                        normalizeNullable(ipAddress, 64),
                        "PASSKEY",
                        hash(refreshToken),
                        refreshExpiresAt,
                        false,
                        null
                )
        );
        JwtService.IssuedAccessToken accessToken = jwtService.issueAccessToken(savedUser, sessionId);
        return new AuthResponse(
                true,
                false,
                accessToken.token(),
                refreshToken,
                sessionId,
                savedUser.getId(),
                savedUser.getPhoneNumber(),
                savedUser.getDisplayName(),
                savedUser.getUsername(),
                accessToken.expiresAt(),
                refreshExpiresAt,
                "PASSKEY",
                false,
                null,
                null
        );
    }

    @Transactional
    public int deleteExpiredChallenges(Instant cutoff, int batchSize) {
        var challenges = passkeyChallengeRepository.findCleanupBatch(cutoff, PageRequest.of(0, Math.max(1, batchSize)));
        if (challenges.isEmpty()) {
            return 0;
        }
        passkeyChallengeRepository.deleteAllInBatch(challenges);
        return challenges.size();
    }

    private PasskeyChallengeEntity getChallenge(UUID challengeId, String flowType) {
        PasskeyChallengeEntity challenge = passkeyChallengeRepository.findByIdAndConsumedAtIsNull(challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Passkey challenge not found"));
        if (!flowType.equals(challenge.getFlowType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Passkey challenge flow mismatch");
        }
        if (challenge.getExpiresAt() == null || !challenge.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Passkey challenge has expired");
        }
        return challenge;
    }

    private void verifyChallenge(PasskeyChallengeEntity challenge, String rawChallenge) {
        if (!challenge.getChallengeHash().equals(hash(normalizeRequired(rawChallenge, "Challenge", 512)))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Passkey challenge is invalid");
        }
    }

    private PasskeyCredentialResponse toResponse(PasskeyCredentialEntity entity) {
        return new PasskeyCredentialResponse(
                entity.getId(),
                entity.getCredentialId(),
                entity.getLabel(),
                entity.getTransports(),
                entity.getCreatedAt(),
                entity.getLastUsedAt()
        );
    }

    private UserEntity requireUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.GONE, "User is deleted");
        }
        return user;
    }

    private String generateChallenge() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to hash passkey secret", exception);
        }
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

    private long resolveRegistrationSignCount(Long signCount) {
        if (signCount == null) {
            return 0L;
        }
        if (signCount < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sign count must be non-negative");
        }
        return signCount;
    }

    private long resolveLoginSignCount(Long existingSignCount, Long signCount) {
        long baseline = existingSignCount != null ? existingSignCount : 0L;
        if (signCount == null) {
            return baseline + 1;
        }
        if (signCount < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sign count must be non-negative");
        }
        return Math.max(baseline, signCount);
    }
}
