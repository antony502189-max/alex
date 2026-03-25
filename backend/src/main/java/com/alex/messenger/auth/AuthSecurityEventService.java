package com.alex.messenger.auth;

import com.alex.messenger.auth.dto.AuthSecurityEventResponse;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthSecurityEventService {

    private final AuthSecurityEventRepository authSecurityEventRepository;

    @Transactional
    public void recordEvent(
            UUID userId,
            UUID sessionId,
            String eventType,
            String severity,
            String ipAddress,
            String userAgent,
            String deviceName,
            String platform,
            String appVersion,
            String details
    ) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User id is required for auth security event");
        }
        AuthSecurityEventEntity event = new AuthSecurityEventEntity();
        event.setUserId(userId);
        event.setSessionId(sessionId);
        event.setEventType(normalizeRequiredUpper(eventType, "Event type", 64));
        event.setSeverity(normalizeSeverity(severity));
        event.setIpAddress(normalizeOptional(ipAddress, 64));
        event.setUserAgent(normalizeOptional(userAgent, 255));
        event.setDeviceName(normalizeOptional(deviceName, 120));
        event.setPlatform(normalizeOptional(platform, 32));
        event.setAppVersion(normalizeOptional(appVersion, 32));
        event.setDetails(normalizeOptional(details, 500));
        authSecurityEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<AuthSecurityEventResponse> listEvents(UUID userId, int limit) {
        return authSecurityEventRepository.findAllByUserIdOrderByCreatedAtDesc(
                        userId,
                        PageRequest.of(0, Math.max(1, Math.min(limit, 100)))
                ).stream()
                .map(this::toResponse)
                .toList();
    }

    private AuthSecurityEventResponse toResponse(AuthSecurityEventEntity event) {
        return new AuthSecurityEventResponse(
                event.getId(),
                event.getUserId(),
                event.getSessionId(),
                event.getEventType(),
                event.getSeverity(),
                event.getIpAddress(),
                event.getUserAgent(),
                event.getDeviceName(),
                event.getPlatform(),
                event.getAppVersion(),
                event.getDetails(),
                event.getCreatedAt()
        );
    }

    private String normalizeSeverity(String severity) {
        String normalized = normalizeRequiredUpper(severity != null ? severity : "INFO", "Severity", 16);
        if (!List.of("INFO", "WARN", "CRITICAL").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported auth security severity");
        }
        return normalized;
    }

    private String normalizeRequiredUpper(String value, String fieldName, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "%s is required".formatted(fieldName));
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }
}
