package com.alex.messenger.auth.session;

import com.alex.messenger.auth.AuthSecurityEventService;
import com.alex.messenger.auth.dto.UpdatePushTokenRequest;
import com.alex.messenger.auth.dto.UserSessionResponse;
import com.alex.messenger.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
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
public class UserSessionService {

    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);
    private static final Duration ONLINE_ACTIVITY_WINDOW = Duration.ofMinutes(2);
    private static final int SECURITY_EVENT_DETAILS_MAX_LENGTH = 500;

    private final UserSessionRepository userSessionRepository;
    private final UserRepository userRepository;
    private final AuthSecurityEventService authSecurityEventService;

    @Transactional
    public UUID createSession(UUID userId, CreateUserSessionCommand command) {
        Instant now = Instant.now();
        List<UserSessionEntity> recentSessions = userSessionRepository.findTop5ByUserIdAndRevokedAtIsNullOrderByLastActiveAtDesc(userId);
        UserSessionEntity session = new UserSessionEntity();
        session.setUserId(userId);
        session.setDeviceName(normalizeDeviceName(command.deviceName(), command.platform()));
        session.setPlatform(normalizeNullable(command.platform(), 32));
        session.setAppVersion(normalizeNullable(command.appVersion(), 32));
        session.setUserAgent(normalizeNullable(command.userAgent(), 255));
        session.setIpAddress(normalizeNullable(command.ipAddress(), 64));
        session.setAuthMethod(normalizeAuthMethod(command.authMethod()));
        session.setRefreshTokenHash(normalizeNullable(command.refreshTokenHash(), 128));
        session.setRefreshTokenExpiresAt(command.refreshTokenExpiresAt());
        session.setLastRefreshedAt(command.refreshTokenHash() != null ? now : null);
        session.setTrustedSession(Boolean.TRUE.equals(command.trustedSession()));
        session.setTrustedAt(Boolean.TRUE.equals(command.trustedSession()) ? command.trustedAt() : null);
        UUID sessionId = userSessionRepository.save(session).getId();
        userRepository.touchLastSeenAt(userId, now);
        if (isSuspiciousLogin(session, recentSessions)) {
            authSecurityEventService.recordEvent(
                    userId,
                    sessionId,
                    "SUSPICIOUS_LOGIN",
                    "WARN",
                    session.getIpAddress(),
                    session.getUserAgent(),
                    session.getDeviceName(),
                    session.getPlatform(),
                    session.getAppVersion(),
                    truncate("New session differs from recent active devices or IP addresses", SECURITY_EVENT_DETAILS_MAX_LENGTH)
            );
        }
        return sessionId;
    }

    @Transactional(readOnly = true)
    public boolean isActive(UUID sessionId, UUID userId) {
        return userSessionRepository.existsByIdAndUserIdAndRevokedAtIsNull(sessionId, userId);
    }

    @Transactional(readOnly = true)
    public boolean isUserOnline(UUID userId) {
        return userSessionRepository.existsByUserIdAndRevokedAtIsNullAndLastActiveAtAfter(
                userId,
                Instant.now().minus(ONLINE_ACTIVITY_WINDOW)
        );
    }

    @Transactional
    public void touch(UUID sessionId, UUID userId) {
        Instant now = Instant.now();
        if (userSessionRepository.touchIfStale(sessionId, userId, now, now.minus(TOUCH_INTERVAL)) > 0) {
            userRepository.touchLastSeenAt(userId, now);
        }
    }

    @Transactional(readOnly = true)
    public List<UserSessionResponse> list(UUID userId, UUID currentSessionId) {
        return userSessionRepository.findAllByUserIdAndRevokedAtIsNullOrderByLastActiveAtDesc(userId).stream()
                .map(session -> new UserSessionResponse(
                        session.getId(),
                        session.getDeviceName(),
                        session.getPlatform(),
                        session.getAppVersion(),
                        session.getUserAgent(),
                        session.getIpAddress(),
                        session.getCreatedAt(),
                        session.getLastActiveAt(),
                        Boolean.TRUE.equals(session.getNotificationsEnabled()),
                        session.getId().equals(currentSessionId),
                        session.getAuthMethod(),
                        Boolean.TRUE.equals(session.getTrustedSession()),
                        session.getTrustedAt()
                ))
                .toList();
    }

    @Transactional
    public UserSessionResponse updatePushToken(
            UUID userId,
            UUID currentSessionId,
            UpdatePushTokenRequest request
    ) {
        UserSessionEntity session = requireActiveSession(currentSessionId, userId);
        String normalizedProvider = normalizeProvider(request.provider());
        session.setPushProvider(normalizedProvider);
        session.setPushToken(normalizeRequiredToken(request.pushToken()));
        session.setNotificationsEnabled(true);
        userSessionRepository.save(session);
        return toResponse(session, currentSessionId);
    }

    @Transactional
    public UserSessionResponse clearPushToken(UUID userId, UUID currentSessionId) {
        UserSessionEntity session = requireActiveSession(currentSessionId, userId);
        session.setPushProvider(null);
        session.setPushToken(null);
        session.setNotificationsEnabled(false);
        userSessionRepository.save(session);
        return toResponse(session, currentSessionId);
    }

    @Transactional(readOnly = true)
    public List<PushSessionTarget> getPushTargets(UUID userId) {
        return userSessionRepository.findAllByUserIdAndRevokedAtIsNullAndNotificationsEnabledTrueAndPushTokenIsNotNull(userId)
                .stream()
                .map(session -> new PushSessionTarget(
                        session.getId(),
                        session.getPushProvider(),
                        session.getPushToken()
                ))
                .toList();
    }

    @Transactional
    public void revoke(UUID userId, UUID targetSessionId) {
        if (userSessionRepository.revoke(targetSessionId, userId, Instant.now()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        authSecurityEventService.recordEvent(
                userId,
                targetSessionId,
                "SESSION_REVOKED",
                "INFO",
                null,
                null,
                null,
                null,
                null,
                "User revoked a session"
        );
    }

    @Transactional
    public void revokeOthers(UUID userId, UUID currentSessionId) {
        int revokedSessions = userSessionRepository.revokeOthers(userId, currentSessionId, Instant.now());
        if (revokedSessions > 0) {
            authSecurityEventService.recordEvent(
                    userId,
                    currentSessionId,
                    "SESSIONS_REVOKED",
                    "INFO",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "Revoked %d other sessions".formatted(revokedSessions)
            );
        }
    }

    @Transactional
    public void revokeAll(UUID userId) {
        int revokedSessions = userSessionRepository.revokeAllForUser(userId, Instant.now());
        if (revokedSessions > 0) {
            authSecurityEventService.recordEvent(
                    userId,
                    null,
                    "ALL_SESSIONS_REVOKED",
                    "WARN",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "Revoked %d sessions".formatted(revokedSessions)
            );
        }
    }

    @Transactional(readOnly = true)
    public UserSessionEntity requireActiveRefreshToken(String refreshTokenHash) {
        UserSessionEntity session = userSessionRepository.findByRefreshTokenHashAndRevokedAtIsNull(refreshTokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid"));
        if (session.getRefreshTokenExpiresAt() == null || !session.getRefreshTokenExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has expired");
        }
        return session;
    }

    @Transactional
    public UserSessionEntity rotateRefreshToken(
            UUID sessionId,
            String refreshTokenHash,
            Instant refreshTokenExpiresAt,
            String ipAddress,
            String userAgent
    ) {
        UserSessionEntity session = userSessionRepository.findByIdAndRevokedAtIsNull(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        session.setRefreshTokenHash(normalizeNullable(refreshTokenHash, 128));
        session.setRefreshTokenExpiresAt(refreshTokenExpiresAt);
        session.setLastRefreshedAt(Instant.now());
        session.setLastActiveAt(Instant.now());
        session.setIpAddress(normalizeNullable(ipAddress, 64));
        session.setUserAgent(normalizeNullable(userAgent, 255));
        userRepository.touchLastSeenAt(session.getUserId(), session.getLastActiveAt());
        return userSessionRepository.save(session);
    }

    @Transactional
    public void markTrusted(UUID sessionId, UUID userId) {
        UserSessionEntity session = requireActiveSession(sessionId, userId);
        session.setTrustedSession(true);
        session.setTrustedAt(Instant.now());
        userSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public UserSessionEntity requireOwnedSession(UUID sessionId, UUID userId) {
        return requireActiveSession(sessionId, userId);
    }

    private UserSessionResponse toResponse(UserSessionEntity session, UUID currentSessionId) {
        return new UserSessionResponse(
                session.getId(),
                session.getDeviceName(),
                session.getPlatform(),
                session.getAppVersion(),
                session.getUserAgent(),
                session.getIpAddress(),
                session.getCreatedAt(),
                session.getLastActiveAt(),
                Boolean.TRUE.equals(session.getNotificationsEnabled()),
                session.getId().equals(currentSessionId),
                session.getAuthMethod(),
                Boolean.TRUE.equals(session.getTrustedSession()),
                session.getTrustedAt()
        );
    }

    private UserSessionEntity requireActiveSession(UUID sessionId, UUID userId) {
        UserSessionEntity session = userSessionRepository.findByIdAndRevokedAtIsNull(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session access denied");
        }
        return session;
    }

    private String normalizeProvider(String provider) {
        String normalized = provider.trim().toUpperCase(Locale.ROOT);
        if (!List.of("EXPO").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported push provider");
        }
        return normalized;
    }

    private String normalizeRequiredToken(String pushToken) {
        String normalized = normalizeNullable(pushToken, 255);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Push token is required");
        }
        return normalized;
    }

    private String normalizeDeviceName(String deviceName, String platform) {
        String normalizedDevice = normalizeNullable(deviceName, 120);
        if (normalizedDevice != null) {
            return normalizedDevice;
        }
        String normalizedPlatform = normalizeNullable(platform, 32);
        return normalizedPlatform != null
                ? normalizedPlatform.substring(0, 1).toUpperCase(Locale.ROOT) + normalizedPlatform.substring(1)
                : "Unknown device";
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

    private String normalizeAuthMethod(String authMethod) {
        String normalized = normalizeNullable(authMethod, 32);
        return normalized != null ? normalized.toUpperCase(Locale.ROOT) : "LEGACY_LOGIN";
    }

    private boolean isSuspiciousLogin(UserSessionEntity session, List<UserSessionEntity> recentSessions) {
        if (recentSessions == null || recentSessions.isEmpty()) {
            return false;
        }
        for (UserSessionEntity recentSession : recentSessions) {
            if (recentSession == null) {
                continue;
            }
            boolean sameIp = recentSession.getIpAddress() != null
                    && recentSession.getIpAddress().equalsIgnoreCase(String.valueOf(session.getIpAddress()));
            boolean sameDevice = recentSession.getDeviceName() != null
                    && recentSession.getDeviceName().equalsIgnoreCase(String.valueOf(session.getDeviceName()))
                    && recentSession.getPlatform() != null
                    && recentSession.getPlatform().equalsIgnoreCase(String.valueOf(session.getPlatform()));
            if (sameIp || sameDevice) {
                return false;
            }
        }
        return true;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
