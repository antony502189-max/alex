package com.alex.messenger.message.idempotency;

import com.alex.messenger.message.MessageLookupEntity;
import com.alex.messenger.message.MessageLookupRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MessageIdempotencyService {

    private static final Duration STALE_RESERVATION_TIMEOUT = Duration.ofSeconds(30);

    private final ClientMessageRequestRepository clientMessageRequestRepository;
    private final MessageLookupRepository messageLookupRepository;

    @Transactional
    public Reservation reserve(UUID senderUserId, UUID chatId, UUID clientMessageId, UUID proposedMessageId) {
        Instant now = Instant.now();
        int inserted = clientMessageRequestRepository.insertIfAbsent(
                UUID.randomUUID(),
                senderUserId,
                clientMessageId,
                chatId,
                proposedMessageId,
                "PENDING",
                now,
                now
        );
        if (inserted == 1) {
            return Reservation.allowProcessing();
        }

        ClientMessageRequestEntity existing = clientMessageRequestRepository
                .findLockedBySenderUserIdAndClientMessageId(senderUserId, clientMessageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Unable to lock client message request"));

        if (!existing.getChatId().equals(chatId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "clientMessageId is already bound to another chat");
        }

        MessageLookupEntity existingMessage = messageLookupRepository.findById(existing.getMessageId()).orElse(null);
        if (existingMessage != null) {
            if (!"COMPLETED".equals(existing.getStatus())) {
                existing.setStatus("COMPLETED");
                existing.setCompletedAt(now);
                existing.setUpdatedAt(now);
                clientMessageRequestRepository.save(existing);
            }
            return Reservation.returnExisting(existingMessage);
        }

        if (!isStale(existing, now)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Message is still being processed");
        }

        existing.setChatId(chatId);
        existing.setMessageId(proposedMessageId);
        existing.setStatus("PENDING");
        existing.setUpdatedAt(now);
        existing.setCompletedAt(null);
        clientMessageRequestRepository.save(existing);
        return Reservation.allowProcessing();
    }

    @Transactional
    public void markCompleted(UUID senderUserId, UUID clientMessageId, UUID messageId) {
        Instant now = Instant.now();
        ClientMessageRequestEntity existing = clientMessageRequestRepository
                .findLockedBySenderUserIdAndClientMessageId(senderUserId, clientMessageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Missing client message reservation"));
        existing.setMessageId(messageId);
        existing.setStatus("COMPLETED");
        existing.setCompletedAt(now);
        existing.setUpdatedAt(now);
        clientMessageRequestRepository.save(existing);
    }

    private boolean isStale(ClientMessageRequestEntity request, Instant now) {
        return request.getUpdatedAt() != null
                && request.getUpdatedAt().isBefore(now.minus(STALE_RESERVATION_TIMEOUT));
    }

    public record Reservation(
            boolean proceed,
            MessageLookupEntity existingMessage
    ) {

        public static Reservation allowProcessing() {
            return new Reservation(true, null);
        }

        public static Reservation returnExisting(MessageLookupEntity existingMessage) {
            return new Reservation(false, existingMessage);
        }
    }
}
