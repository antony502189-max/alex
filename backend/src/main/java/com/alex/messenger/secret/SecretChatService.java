package com.alex.messenger.secret;

import com.alex.messenger.auth.session.UserSessionEntity;
import com.alex.messenger.auth.session.UserSessionRepository;
import com.alex.messenger.media.PhotoAccess;
import com.alex.messenger.media.ProfilePhotoService;
import com.alex.messenger.secret.dto.AcceptSecretChatRequest;
import com.alex.messenger.secret.dto.CreateSecretChatRequest;
import com.alex.messenger.secret.dto.SecretChatMessageResponse;
import com.alex.messenger.secret.dto.SecretChatReadEventResponse;
import com.alex.messenger.secret.dto.SecretChatScreenshotEventResponse;
import com.alex.messenger.secret.dto.SecretChatSummaryResponse;
import com.alex.messenger.secret.dto.SendSecretChatMessageRequest;
import com.alex.messenger.secret.dto.UpdateSecretChatTimerRequest;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SecretChatService {

    private final SecretChatRepository secretChatRepository;
    private final SecretChatMessageRepository secretChatMessageRepository;
    private final SecretAttachmentService secretAttachmentService;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final ProfilePhotoService profilePhotoService;
    private final SecretChatRealtimeService secretChatRealtimeService;

    @Transactional(readOnly = true)
    public List<SecretChatSummaryResponse> listChats(UUID userId, UUID sessionId) {
        ensureSessionActive(userId, sessionId);
        return secretChatRepository.findVisibleChats(userId, sessionId).stream()
                .map(chat -> toSummary(chat, userId))
                .toList();
    }

    @Transactional
    public SecretChatSummaryResponse createChat(UUID userId, UUID sessionId, CreateSecretChatRequest request) {
        ensureSessionActive(userId, sessionId);
        if (userId.equals(request.recipientUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Secret chat requires another user");
        }
        validatePublicKey(request.initiatorPublicKey());
        userRepository.findById(request.recipientUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient not found"));

        SecretChatEntity chat = new SecretChatEntity();
        chat.setInitiatorUserId(userId);
        chat.setInitiatorSessionId(sessionId);
        chat.setRecipientUserId(request.recipientUserId());
        chat.setInitiatorPublicKey(request.initiatorPublicKey().trim());
        chat.setAutoDeleteSeconds(normalizeAutoDeleteSeconds(request.autoDeleteSeconds()));
        chat.setStatus("PENDING");

        SecretChatEntity saved = secretChatRepository.save(chat);
        SecretChatSummaryResponse response = toSummary(saved, userId);
        publishChatUpdate(saved, "CHAT_UPDATED");
        return response;
    }

    @Transactional
    public SecretChatSummaryResponse acceptChat(
            UUID userId,
            UUID sessionId,
            UUID secretChatId,
            AcceptSecretChatRequest request
    ) {
        ensureSessionActive(userId, sessionId);
        SecretChatEntity chat = getAccessibleChat(userId, sessionId, secretChatId);
        if (!userId.equals(chat.getRecipientUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only recipient can accept this secret chat");
        }
        if (!"PENDING".equals(chat.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Secret chat is no longer pending");
        }

        validatePublicKey(request.recipientPublicKey());
        String fingerprint = request.sharedKeyFingerprint().trim();
        if (fingerprint.isBlank() || fingerprint.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shared key fingerprint");
        }

        chat.setRecipientSessionId(sessionId);
        chat.setRecipientPublicKey(request.recipientPublicKey().trim());
        chat.setSharedKeyFingerprint(fingerprint);
        chat.setStatus("ACTIVE");
        chat.setAcceptedAt(Instant.now());

        SecretChatEntity saved = secretChatRepository.save(chat);
        SecretChatSummaryResponse response = toSummary(saved, userId);
        publishChatUpdate(saved, "CHAT_UPDATED");
        return response;
    }

    @Transactional
    public SecretChatSummaryResponse declineChat(UUID userId, UUID sessionId, UUID secretChatId) {
        ensureSessionActive(userId, sessionId);
        SecretChatEntity chat = getAccessibleChat(userId, sessionId, secretChatId);
        if (!"PENDING".equals(chat.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Secret chat is no longer pending");
        }
        if (!userId.equals(chat.getRecipientUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only recipient can decline this secret chat");
        }

        chat.setStatus("DECLINED");
        chat.setClosedAt(Instant.now());
        SecretChatEntity saved = secretChatRepository.save(chat);
        SecretChatSummaryResponse response = toSummary(saved, userId);
        publishChatUpdate(saved, "CHAT_UPDATED");
        return response;
    }

    @Transactional
    public SecretChatSummaryResponse closeChat(UUID userId, UUID sessionId, UUID secretChatId) {
        ensureSessionActive(userId, sessionId);
        SecretChatEntity chat = getAccessibleChat(userId, sessionId, secretChatId);
        ensureBoundParticipant(chat, sessionId);
        if ("CLOSED".equals(chat.getStatus()) || "DECLINED".equals(chat.getStatus())) {
            return toSummary(chat, userId);
        }

        chat.setStatus("CLOSED");
        chat.setClosedAt(Instant.now());
        SecretChatEntity saved = secretChatRepository.save(chat);
        SecretChatSummaryResponse response = toSummary(saved, userId);
        publishChatUpdate(saved, "CHAT_UPDATED");
        return response;
    }

    @Transactional
    public SecretChatSummaryResponse updateTimer(
            UUID userId,
            UUID sessionId,
            UUID secretChatId,
            UpdateSecretChatTimerRequest request
    ) {
        ensureSessionActive(userId, sessionId);
        SecretChatEntity chat = getAccessibleChat(userId, sessionId, secretChatId);
        ensureBoundParticipant(chat, sessionId);
        if (!"ACTIVE".equals(chat.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Secret chat must be active to change timer");
        }

        chat.setAutoDeleteSeconds(normalizeAutoDeleteSeconds(request.autoDeleteSeconds()));
        SecretChatEntity saved = secretChatRepository.save(chat);
        SecretChatSummaryResponse response = toSummary(saved, userId);
        publishChatUpdate(saved, "CHAT_UPDATED");
        return response;
    }

    @Transactional(readOnly = true)
    public List<SecretChatMessageResponse> getMessages(
            UUID userId,
            UUID sessionId,
            UUID secretChatId,
            Instant before,
            int limit
    ) {
        ensureSessionActive(userId, sessionId);
        SecretChatEntity chat = getAccessibleChat(userId, sessionId, secretChatId);
        ensureBoundParticipant(chat, sessionId);
        if (!"ACTIVE".equals(chat.getStatus())) {
            return List.of();
        }

        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        Instant now = Instant.now();
        List<SecretChatMessageEntity> messages = before == null
                ? secretChatMessageRepository.findRecentVisible(secretChatId, now, PageRequest.of(0, normalizedLimit))
                : secretChatMessageRepository.findRecentVisibleBefore(secretChatId, before, now, PageRequest.of(0, normalizedLimit));
        Collections.reverse(messages);
        return messages.stream().map(this::toMessageResponse).toList();
    }

    @Transactional
    public SecretChatMessageResponse sendMessage(
            UUID userId,
            UUID sessionId,
            UUID secretChatId,
            SendSecretChatMessageRequest request
    ) {
        ensureSessionActive(userId, sessionId);
        SecretChatEntity chat = getAccessibleChat(userId, sessionId, secretChatId);
        ensureBoundParticipant(chat, sessionId);
        if (!"ACTIVE".equals(chat.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Secret chat must be active");
        }
        validateCiphertext(request.ciphertext(), request.nonce());
        List<UUID> attachmentIds = request.attachmentIds() != null ? List.copyOf(request.attachmentIds()) : List.of();
        Collection<SecretAttachmentEntity> attachments = secretAttachmentService
                .assertUsableAttachments(userId, sessionId, secretChatId, attachmentIds)
                .values();

        SecretChatMessageEntity message = new SecretChatMessageEntity();
        message.setSecretChatId(chat.getId());
        message.setSenderUserId(userId);
        message.setSenderSessionId(sessionId);
        message.setMessageType(attachmentIds.isEmpty() ? "TEXT" : "ATTACHMENT");
        message.setCiphertext(request.ciphertext().trim());
        message.setNonce(request.nonce().trim());

        SecretChatMessageEntity saved = secretChatMessageRepository.save(message);
        secretAttachmentService.linkAttachmentsToMessage(attachments, saved.getId());
        chat.setLastMessageAt(saved.getCreatedAt());
        secretChatRepository.save(chat);

        SecretChatMessageResponse response = toMessageResponse(saved);
        secretChatRealtimeService.publishMessage(participantUserIds(chat), response);
        publishChatUpdate(chat, "CHAT_UPDATED");
        return response;
    }

    @Transactional
    public SecretChatReadEventResponse markRead(UUID userId, UUID sessionId, UUID secretChatId) {
        ensureSessionActive(userId, sessionId);
        SecretChatEntity chat = getAccessibleChat(userId, sessionId, secretChatId);
        ensureBoundParticipant(chat, sessionId);
        if (!"ACTIVE".equals(chat.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Secret chat must be active");
        }

        List<SecretChatMessageEntity> unreadMessages = secretChatMessageRepository.findUnreadIncoming(chat.getId(), userId);
        if (unreadMessages.isEmpty()) {
            return new SecretChatReadEventResponse(chat.getId(), userId, Instant.now(), null, List.of());
        }

        Instant readAt = Instant.now();
        Instant expiresAt = chat.getAutoDeleteSeconds() != null ? readAt.plusSeconds(chat.getAutoDeleteSeconds()) : null;
        List<UUID> messageIds = new ArrayList<>(unreadMessages.size());
        for (SecretChatMessageEntity message : unreadMessages) {
            message.setReadAt(readAt);
            message.setExpiresAt(expiresAt);
            messageIds.add(message.getId());
        }
        secretChatMessageRepository.saveAll(unreadMessages);

        SecretChatReadEventResponse response = new SecretChatReadEventResponse(
                chat.getId(),
                userId,
                readAt,
                expiresAt,
                List.copyOf(messageIds)
        );
        secretChatRealtimeService.publishReadEvent(participantUserIds(chat), response);
        return response;
    }

    @Transactional
    public int deleteExpiredMessages(Instant now, int batchSize) {
        List<SecretChatMessageEntity> expiredMessages = secretChatMessageRepository.findExpired(
                now,
                PageRequest.of(0, Math.max(1, batchSize))
        );
        if (expiredMessages.isEmpty()) {
            return 0;
        }
        secretAttachmentService.removeAttachmentsForMessages(
                expiredMessages.stream().map(SecretChatMessageEntity::getId).toList()
        );
        secretChatMessageRepository.deleteAll(expiredMessages);
        return expiredMessages.size();
    }

    @Transactional(readOnly = true)
    public SecretChatScreenshotEventResponse reportScreenshot(UUID userId, UUID sessionId, UUID secretChatId) {
        ensureSessionActive(userId, sessionId);
        SecretChatEntity chat = getAccessibleChat(userId, sessionId, secretChatId);
        ensureBoundParticipant(chat, sessionId);
        if (!"ACTIVE".equals(chat.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Secret chat must be active");
        }

        SecretChatScreenshotEventResponse response = new SecretChatScreenshotEventResponse(
                chat.getId(),
                userId,
                Instant.now()
        );
        secretChatRealtimeService.publishScreenshotEvent(participantUserIds(chat), response);
        return response;
    }

    private void ensureSessionActive(UUID userId, UUID sessionId) {
        if (userSessionRepository.existsByIdAndUserIdAndRevokedAtIsNull(sessionId, userId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session is no longer active");
    }

    private SecretChatEntity getAccessibleChat(UUID userId, UUID sessionId, UUID secretChatId) {
        return secretChatRepository.findAccessible(secretChatId, userId, sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Secret chat not found"));
    }

    private void ensureBoundParticipant(SecretChatEntity chat, UUID sessionId) {
        boolean initiatorBound = sessionId.equals(chat.getInitiatorSessionId());
        boolean recipientBound = chat.getRecipientSessionId() != null && sessionId.equals(chat.getRecipientSessionId());
        if (initiatorBound || recipientBound) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Secret chat is bound to another session");
    }

    private SecretChatSummaryResponse toSummary(SecretChatEntity chat, UUID requesterUserId) {
        UUID peerUserId = requesterUserId.equals(chat.getInitiatorUserId())
                ? chat.getRecipientUserId()
                : chat.getInitiatorUserId();
        UUID peerSessionId = requesterUserId.equals(chat.getInitiatorUserId())
                ? chat.getRecipientSessionId()
                : chat.getInitiatorSessionId();
        UserEntity peer = userRepository.findById(peerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Peer user not found"));
        UserSessionEntity peerSession = peerSessionId != null
                ? userSessionRepository.findByIdAndRevokedAtIsNull(peerSessionId).orElse(null)
                : null;
        PhotoAccess photoAccess = profilePhotoService.buildPhotoAccess(
                peer.getPhotoStorageProvider(),
                peer.getPhotoBucketName(),
                peer.getPhotoObjectKey()
        );

        return new SecretChatSummaryResponse(
                chat.getId(),
                peerUserId,
                peer.getDisplayName(),
                peer.getPhoneNumber(),
                photoAccess != null ? photoAccess.photoUrl() : null,
                photoAccess != null ? photoAccess.photoAccessExpiresAt() : null,
                chat.getInitiatorSessionId(),
                chat.getRecipientSessionId(),
                peerSessionId,
                peerSession != null ? peerSession.getDeviceName() : null,
                chat.getInitiatorPublicKey(),
                chat.getRecipientPublicKey(),
                chat.getSharedKeyFingerprint(),
                chat.getStatus(),
                requesterUserId.equals(chat.getInitiatorUserId()) ? "OUTGOING" : "INCOMING",
                chat.getAutoDeleteSeconds(),
                chat.getCreatedAt(),
                chat.getAcceptedAt(),
                chat.getClosedAt(),
                chat.getLastMessageAt()
        );
    }

    private SecretChatMessageResponse toMessageResponse(SecretChatMessageEntity message) {
        return new SecretChatMessageResponse(
                message.getSecretChatId(),
                message.getId(),
                message.getSenderUserId(),
                message.getSenderSessionId(),
                message.getMessageType(),
                message.getCiphertext(),
                message.getNonce(),
                message.getCreatedAt(),
                message.getReadAt(),
                message.getExpiresAt()
        );
    }

    private void publishChatUpdate(SecretChatEntity chat, String eventType) {
        secretChatRealtimeService.publishChatUpdate(
                List.of(chat.getInitiatorUserId()),
                eventType,
                toSummary(chat, chat.getInitiatorUserId())
        );
        if (!chat.getRecipientUserId().equals(chat.getInitiatorUserId())) {
            secretChatRealtimeService.publishChatUpdate(
                    List.of(chat.getRecipientUserId()),
                    eventType,
                    toSummary(chat, chat.getRecipientUserId())
            );
        }
    }

    private Collection<UUID> participantUserIds(SecretChatEntity chat) {
        return List.of(chat.getInitiatorUserId(), chat.getRecipientUserId());
    }

    private Integer normalizeAutoDeleteSeconds(Integer autoDeleteSeconds) {
        if (autoDeleteSeconds == null || autoDeleteSeconds == 0) {
            return null;
        }
        if (autoDeleteSeconds < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Auto-delete timer must be positive");
        }
        if (autoDeleteSeconds > 604_800) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Auto-delete timer is too large");
        }
        return autoDeleteSeconds;
    }

    private void validatePublicKey(String value) {
        String normalized = value != null ? value.trim() : "";
        if (normalized.isBlank() || normalized.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid public key");
        }
    }

    private void validateCiphertext(String ciphertext, String nonce) {
        String normalizedCiphertext = ciphertext != null ? ciphertext.trim() : "";
        String normalizedNonce = nonce != null ? nonce.trim() : "";
        if (normalizedCiphertext.isBlank() || normalizedNonce.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ciphertext and nonce are required");
        }
        if (normalizedCiphertext.length() > 32000 || normalizedNonce.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Encrypted payload is too large");
        }
    }
}
