package com.alex.messenger.auth;

import com.alex.messenger.auth.dto.AuthResponse;
import com.alex.messenger.auth.dto.RequestPhoneChangeRequest;
import com.alex.messenger.auth.dto.RequestPhoneChangeResponse;
import com.alex.messenger.auth.dto.VerifyPhoneChangeRequest;
import com.alex.messenger.auth.session.UserSessionEntity;
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
public class PhoneChangeService {

    private final UserRepository userRepository;
    private final PhoneChangeChallengeRepository phoneChangeChallengeRepository;
    private final UserSessionService userSessionService;
    private final JwtService jwtService;
    private final AuthProperties authProperties;
    private final AuthSecurityEventService authSecurityEventService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public RequestPhoneChangeResponse requestCode(
            UUID userId,
            UUID sessionId,
            RequestPhoneChangeRequest request
    ) {
        UserEntity user = requireUser(userId);
        userSessionService.requireOwnedSession(sessionId, userId);
        String normalizedPhone = normalizeRequired(request.newPhoneNumber(), "Phone number", 32);
        if (normalizedPhone.equals(user.getPhoneNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number is already active");
        }
        userRepository.findByPhoneNumber(normalizedPhone).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number is already in use");
        });

        String code = generateCode(authProperties.getCode().getLength());
        PhoneChangeChallengeEntity challenge = new PhoneChangeChallengeEntity();
        challenge.setUserId(userId);
        challenge.setSessionId(sessionId);
        challenge.setNewPhoneNumber(normalizedPhone);
        challenge.setCodeHash(hash("%s:%s".formatted(normalizedPhone, code)));
        challenge.setMaxAttempts(authProperties.getCode().getMaxAttempts());
        challenge.setExpiresAt(Instant.now().plus(authProperties.getPhoneChange().getTtl()));
        PhoneChangeChallengeEntity saved = phoneChangeChallengeRepository.save(challenge);
        return new RequestPhoneChangeResponse(
                saved.getId(),
                saved.getNewPhoneNumber(),
                saved.getExpiresAt(),
                authProperties.getCode().isExposeDebugCode() ? code : null
        );
    }

    @Transactional
    public AuthResponse verifyCode(
            UUID userId,
            UUID currentSessionId,
            VerifyPhoneChangeRequest request,
            String ipAddress,
            String userAgent
    ) {
        PhoneChangeChallengeEntity challenge = phoneChangeChallengeRepository.findByIdAndConsumedAtIsNull(request.challengeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Phone change challenge not found"));
        if (!userId.equals(challenge.getUserId()) || !currentSessionId.equals(challenge.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Phone change challenge access denied");
        }
        if (!challenge.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Phone change code has expired");
        }
        if (challenge.getAttemptCount() != null && challenge.getMaxAttempts() != null
                && challenge.getAttemptCount() >= challenge.getMaxAttempts()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many verification attempts");
        }

        String normalizedCode = normalizeRequired(request.code(), "Code", 8);
        if (!challenge.getCodeHash().equals(hash("%s:%s".formatted(challenge.getNewPhoneNumber(), normalizedCode)))) {
            challenge.setAttemptCount((challenge.getAttemptCount() != null ? challenge.getAttemptCount() : 0) + 1);
            phoneChangeChallengeRepository.save(challenge);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phone change code is invalid");
        }

        UserEntity user = requireUser(userId);
        userRepository.findByPhoneNumber(challenge.getNewPhoneNumber())
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number is already in use");
                });

        challenge.setConsumedAt(Instant.now());
        phoneChangeChallengeRepository.save(challenge);

        user.setPhoneNumber(challenge.getNewPhoneNumber());
        user.setLastSeenAt(Instant.now());
        UserEntity savedUser = userRepository.save(user);

        userSessionService.revokeOthers(userId, currentSessionId);
        authSecurityEventService.recordEvent(
                userId,
                currentSessionId,
                "PHONE_CHANGED",
                "WARN",
                ipAddress,
                userAgent,
                null,
                null,
                null,
                "Phone number changed to ending %s".formatted(
                        challenge.getNewPhoneNumber().substring(Math.max(0, challenge.getNewPhoneNumber().length() - 4))
                )
        );

        String refreshToken = generateRefreshToken();
        Instant refreshExpiresAt = Instant.now().plus(authProperties.getRefresh().getTtl());
        UserSessionEntity rotated = userSessionService.rotateRefreshToken(
                currentSessionId,
                hash(refreshToken),
                refreshExpiresAt,
                ipAddress,
                userAgent
        );
        JwtService.IssuedAccessToken accessToken = jwtService.issueAccessToken(savedUser, rotated.getId());
        return new AuthResponse(
                true,
                false,
                accessToken.token(),
                refreshToken,
                rotated.getId(),
                savedUser.getId(),
                savedUser.getPhoneNumber(),
                savedUser.getDisplayName(),
                savedUser.getUsername(),
                accessToken.expiresAt(),
                refreshExpiresAt,
                "PHONE_CHANGE",
                Boolean.TRUE.equals(rotated.getTrustedSession()),
                null,
                null
        );
    }

    @Transactional
    public int deleteExpiredChallenges(Instant cutoff, int batchSize) {
        var challenges = phoneChangeChallengeRepository.findCleanupBatch(cutoff, PageRequest.of(0, Math.max(1, batchSize)));
        if (challenges.isEmpty()) {
            return 0;
        }
        phoneChangeChallengeRepository.deleteAllInBatch(challenges);
        return challenges.size();
    }

    private UserEntity requireUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.GONE, "User is deleted");
        }
        return user;
    }

    private String generateCode(int length) {
        int boundedLength = Math.max(4, Math.min(length, 8));
        int max = (int) Math.pow(10, boundedLength);
        return String.format("%0" + boundedLength + "d", secureRandom.nextInt(max));
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
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to hash auth secret", exception);
        }
    }

    private String normalizeRequired(String value, String fieldName, int maxLength) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "%s is required".formatted(fieldName));
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "%s is required".formatted(fieldName));
        }
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }
}
