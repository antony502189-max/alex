package com.alex.messenger.message;

import com.alex.messenger.message.dto.MessageLiveLocationPayload;
import com.alex.messenger.message.dto.UpdateLiveLocationRequest;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MessageLiveLocationService {

    private final MessageLiveLocationRepository messageLiveLocationRepository;

    @Transactional
    public MessageLiveLocationPayload activate(UUID messageId, UUID chatId, UUID senderUserId, MessageLiveLocationPayload payload) {
        MessageLiveLocationPayload normalized = normalizeForCreate(payload);
        Instant now = Instant.now();
        MessageLiveLocationEntity entity = messageLiveLocationRepository.findByMessageId(messageId)
                .orElseGet(MessageLiveLocationEntity::new);
        entity.setMessageId(messageId);
        entity.setChatId(chatId);
        entity.setSenderUserId(senderUserId);
        entity.setLatitude(normalized.latitude());
        entity.setLongitude(normalized.longitude());
        entity.setTitle(trimToNull(normalized.title(), 120));
        entity.setAddress(trimToNull(normalized.address(), 240));
        entity.setExpiresAt(now.plusSeconds(normalized.livePeriodSeconds()));
        entity.setLastUpdatedAt(now);
        entity.setStoppedAt(null);
        return toPayload(messageLiveLocationRepository.save(entity));
    }

    @Transactional
    public MessageLiveLocationPayload update(MessageLookupEntity message, UpdateLiveLocationRequest request) {
        MessageLiveLocationEntity entity = requireActive(message.getMessageId());
        if (!entity.getSenderUserId().equals(message.getSenderId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Live location can be updated only by the sender");
        }
        MessageLiveLocationPayload normalized = normalizeForUpdate(request);
        entity.setLatitude(normalized.latitude());
        entity.setLongitude(normalized.longitude());
        entity.setTitle(trimToNull(normalized.title(), 120));
        entity.setAddress(trimToNull(normalized.address(), 240));
        entity.setLastUpdatedAt(Instant.now());
        return toPayload(messageLiveLocationRepository.save(entity));
    }

    @Transactional
    public MessageLiveLocationPayload stop(MessageLookupEntity message) {
        MessageLiveLocationEntity entity = requireExisting(message.getMessageId());
        if (!entity.getSenderUserId().equals(message.getSenderId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Live location can be stopped only by the sender");
        }
        if (entity.getStoppedAt() == null) {
            entity.setStoppedAt(Instant.now());
            entity.setLastUpdatedAt(entity.getStoppedAt());
            messageLiveLocationRepository.save(entity);
        }
        return toPayload(entity);
    }

    @Transactional(readOnly = true)
    public MessageLiveLocationPayload getPayload(UUID messageId) {
        return messageLiveLocationRepository.findByMessageId(messageId)
                .map(this::toPayload)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<UUID, MessageLiveLocationPayload> getPayloads(Collection<UUID> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Map.of();
        }
        return messageLiveLocationRepository.findAllByMessageIdIn(messageIds).stream()
                .collect(Collectors.toMap(MessageLiveLocationEntity::getMessageId, this::toPayload));
    }

    private MessageLiveLocationEntity requireExisting(UUID messageId) {
        return messageLiveLocationRepository.findByMessageId(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Live location is unavailable"));
    }

    private MessageLiveLocationEntity requireActive(UUID messageId) {
        MessageLiveLocationEntity entity = requireExisting(messageId);
        if (entity.getStoppedAt() != null || !entity.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Live location is no longer active");
        }
        return entity;
    }

    private MessageLiveLocationPayload normalizeForCreate(MessageLiveLocationPayload payload) {
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Live location payload is required");
        }
        MessageLiveLocationPayload normalized = normalizeCoordinates(payload.latitude(), payload.longitude(), payload.title(), payload.address());
        Integer livePeriodSeconds = payload.livePeriodSeconds();
        if (livePeriodSeconds == null || livePeriodSeconds < 60 || livePeriodSeconds > 86_400) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Live location duration is invalid");
        }
        return new MessageLiveLocationPayload(
                normalized.latitude(),
                normalized.longitude(),
                normalized.title(),
                normalized.address(),
                livePeriodSeconds,
                null,
                null,
                null,
                null
        );
    }

    private MessageLiveLocationPayload normalizeForUpdate(UpdateLiveLocationRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Live location update payload is required");
        }
        return normalizeCoordinates(request.latitude(), request.longitude(), request.title(), request.address());
    }

    private MessageLiveLocationPayload normalizeCoordinates(Double latitude, Double longitude, String title, String address) {
        if (latitude == null || longitude == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Live location coordinates are required");
        }
        if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Live location coordinates are invalid");
        }
        return new MessageLiveLocationPayload(
                latitude,
                longitude,
                trimToNull(title, 120),
                trimToNull(address, 240),
                null,
                null,
                null,
                null,
                null
        );
    }

    private MessageLiveLocationPayload toPayload(MessageLiveLocationEntity entity) {
        boolean active = entity.getStoppedAt() == null && entity.getExpiresAt() != null && entity.getExpiresAt().isAfter(Instant.now());
        return new MessageLiveLocationPayload(
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getTitle(),
                entity.getAddress(),
                null,
                entity.getExpiresAt(),
                entity.getLastUpdatedAt(),
                entity.getStoppedAt(),
                active
        );
    }

    private String trimToNull(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Live location field is too long");
        }
        return normalized;
    }
}
