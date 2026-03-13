package com.alex.messenger.monetization;

import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.SendMessageRequest;
import com.alex.messenger.monetization.dto.ChannelMonetizationStatsResponse;
import com.alex.messenger.monetization.dto.CreateSponsoredMessageRequest;
import com.alex.messenger.monetization.dto.SponsoredMessageResponse;
import com.alex.messenger.user.UserRepository;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MonetizationService {

    private final SponsoredMessageRepository sponsoredMessageRepository;
    private final SponsoredMessageEventRepository sponsoredMessageEventRepository;
    private final ChatService chatService;
    private final UserRepository userRepository;
    private final MessageService messageService;

    @Transactional(readOnly = true)
    public List<SponsoredMessageResponse> listSponsoredMessages(UUID requesterId, UUID chatId) {
        ensureCanManageMonetization(requesterId, chatId);
        return sponsoredMessageRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SponsoredMessageResponse createSponsoredMessage(
            UUID requesterId,
            UUID chatId,
            CreateSponsoredMessageRequest request
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        UUID sponsorUserId = request.sponsorUserId() != null ? request.sponsorUserId() : requesterId;
        requireUser(sponsorUserId);
        Instant activeUntil = request.activeUntil();
        if (activeUntil != null && !activeUntil.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sponsored message activity window must be in the future");
        }

        SponsoredMessageEntity message = new SponsoredMessageEntity();
        message.setChannelChatId(chatId);
        message.setSponsorUserId(sponsorUserId);
        message.setCreatedByUserId(requesterId);
        message.setTitle(normalizeRequired(request.title(), "Sponsored message title", 120));
        message.setMessageText(normalizeRequired(request.messageText(), "Sponsored message text", 1000));
        message.setCallToActionLabel(normalizeOptional(request.callToActionLabel(), 64));
        message.setCallToActionUrl(normalizeHttpUrl(request.callToActionUrl()));
        message.setBudgetUnits(normalizePositive(request.budgetUnits(), "Sponsored message budget"));
        message.setCostPerImpressionUnits(normalizeNonNegative(request.costPerImpressionUnits(), 1L, "Impression cost"));
        message.setCostPerClickUnits(normalizeNonNegative(request.costPerClickUnits(), 5L, "Click cost"));
        message.setStatus("DRAFT");
        message.setActiveUntil(activeUntil);
        return toResponse(sponsoredMessageRepository.save(message));
    }

    @Transactional
    public SponsoredMessageResponse publishSponsoredMessage(UUID requesterId, UUID chatId, UUID sponsoredMessageId) {
        ensureCanManageMonetization(requesterId, chatId);
        SponsoredMessageEntity sponsoredMessage = getManagedSponsoredMessage(chatId, sponsoredMessageId);
        if (sponsoredMessage.getDeliveredMessageId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sponsored message is already published");
        }
        if (sponsoredMessage.getActiveUntil() != null && !sponsoredMessage.getActiveUntil().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Sponsored message activity window has expired");
        }

        ChatMessageResponse published = messageService.sendMessage(
                requesterId,
                new SendMessageRequest(
                        chatId,
                        null,
                        null,
                        null,
                        composeSponsoredText(sponsoredMessage),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        null
                )
        );
        sponsoredMessage.setDeliveredMessageId(published.messageId());
        sponsoredMessage.setPublishedAt(Instant.now());
        sponsoredMessage.setStatus("ACTIVE");
        return toResponse(sponsoredMessageRepository.save(sponsoredMessage));
    }

    @Transactional
    public SponsoredMessageResponse pauseSponsoredMessage(UUID requesterId, UUID chatId, UUID sponsoredMessageId) {
        ensureCanManageMonetization(requesterId, chatId);
        SponsoredMessageEntity sponsoredMessage = getManagedSponsoredMessage(chatId, sponsoredMessageId);
        if (!"ACTIVE".equals(sponsoredMessage.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active sponsored messages can be paused");
        }
        sponsoredMessage.setStatus("PAUSED");
        return toResponse(sponsoredMessageRepository.save(sponsoredMessage));
    }

    @Transactional
    public SponsoredMessageResponse resumeSponsoredMessage(UUID requesterId, UUID chatId, UUID sponsoredMessageId) {
        ensureCanManageMonetization(requesterId, chatId);
        SponsoredMessageEntity sponsoredMessage = getManagedSponsoredMessage(chatId, sponsoredMessageId);
        if (sponsoredMessage.getDeliveredMessageId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sponsored message must be published before it can resume");
        }
        if ("COMPLETED".equals(sponsoredMessage.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completed sponsored message cannot be resumed");
        }
        if (sponsoredMessage.getActiveUntil() != null && !sponsoredMessage.getActiveUntil().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Sponsored message activity window has expired");
        }
        sponsoredMessage.setStatus("ACTIVE");
        return toResponse(sponsoredMessageRepository.save(sponsoredMessage));
    }

    @Transactional
    public SponsoredMessageResponse recordImpression(UUID viewerId, UUID chatId, UUID sponsoredMessageId) {
        chatService.getOwnedChat(viewerId, chatId);
        return recordEvent(viewerId, chatId, sponsoredMessageId, "IMPRESSION");
    }

    @Transactional
    public SponsoredMessageResponse recordClick(UUID viewerId, UUID chatId, UUID sponsoredMessageId) {
        chatService.getOwnedChat(viewerId, chatId);
        return recordEvent(viewerId, chatId, sponsoredMessageId, "CLICK");
    }

    @Transactional(readOnly = true)
    public ChannelMonetizationStatsResponse getChannelStats(UUID requesterId, UUID chatId) {
        ensureCanManageMonetization(requesterId, chatId);
        List<SponsoredMessageEntity> sponsoredMessages = sponsoredMessageRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId);
        if (sponsoredMessages.isEmpty()) {
            return new ChannelMonetizationStatsResponse(chatId, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0);
        }

        List<UUID> sponsoredMessageIds = sponsoredMessages.stream().map(SponsoredMessageEntity::getId).toList();
        List<SponsoredMessageEventEntity> events = sponsoredMessageEventRepository.findAllBySponsoredMessageIdIn(sponsoredMessageIds);
        long impressions = events.stream().filter(event -> "IMPRESSION".equals(event.getEventType())).count();
        long clicks = events.stream().filter(event -> "CLICK".equals(event.getEventType())).count();
        long totalBudget = sponsoredMessages.stream().mapToLong(message -> message.getBudgetUnits() != null ? message.getBudgetUnits() : 0L).sum();
        long totalSpent = sponsoredMessages.stream().mapToLong(message -> message.getSpentUnits() != null ? message.getSpentUnits() : 0L).sum();

        return new ChannelMonetizationStatsResponse(
                chatId,
                sponsoredMessages.size(),
                countByStatus(sponsoredMessages, "DRAFT"),
                countByStatus(sponsoredMessages, "ACTIVE"),
                countByStatus(sponsoredMessages, "PAUSED"),
                countByStatus(sponsoredMessages, "COMPLETED"),
                (int) sponsoredMessages.stream().filter(message -> message.getPublishedAt() != null).count(),
                totalBudget,
                totalSpent,
                Math.max(0L, totalBudget - totalSpent),
                impressions,
                clicks,
                impressions == 0 ? 0.0 : (clicks * 100.0d) / impressions
        );
    }

    private SponsoredMessageResponse recordEvent(UUID viewerId, UUID chatId, UUID sponsoredMessageId, String eventType) {
        SponsoredMessageEntity sponsoredMessage = sponsoredMessageRepository.findById(sponsoredMessageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sponsored message not found"));
        if (!chatId.equals(sponsoredMessage.getChannelChatId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sponsored message not found");
        }
        if (sponsoredMessage.getPublishedAt() == null || !"ACTIVE".equals(sponsoredMessage.getStatus())) {
            return toResponse(sponsoredMessage);
        }
        if (sponsoredMessage.getActiveUntil() != null && !sponsoredMessage.getActiveUntil().isAfter(Instant.now())) {
            sponsoredMessage.setStatus("COMPLETED");
            return toResponse(sponsoredMessageRepository.save(sponsoredMessage));
        }
        if (sponsoredMessageEventRepository.existsBySponsoredMessageIdAndViewerUserIdAndEventType(
                sponsoredMessageId,
                viewerId,
                eventType
        )) {
            return toResponse(sponsoredMessage);
        }

        long eventCost = "CLICK".equals(eventType)
                ? sponsoredMessage.getCostPerClickUnits()
                : sponsoredMessage.getCostPerImpressionUnits();
        long spent = sponsoredMessage.getSpentUnits() != null ? sponsoredMessage.getSpentUnits() : 0L;
        if (spent + eventCost > sponsoredMessage.getBudgetUnits()) {
            sponsoredMessage.setStatus("COMPLETED");
            return toResponse(sponsoredMessageRepository.save(sponsoredMessage));
        }

        SponsoredMessageEventEntity event = new SponsoredMessageEventEntity();
        event.setSponsoredMessageId(sponsoredMessageId);
        event.setViewerUserId(viewerId);
        event.setEventType(eventType);
        event.setCostUnits(eventCost);
        sponsoredMessageEventRepository.save(event);

        sponsoredMessage.setSpentUnits(spent + eventCost);
        if (sponsoredMessage.getSpentUnits() >= sponsoredMessage.getBudgetUnits()) {
            sponsoredMessage.setStatus("COMPLETED");
        }
        return toResponse(sponsoredMessageRepository.save(sponsoredMessage));
    }

    private SponsoredMessageEntity getManagedSponsoredMessage(UUID chatId, UUID sponsoredMessageId) {
        SponsoredMessageEntity sponsoredMessage = sponsoredMessageRepository.findById(sponsoredMessageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sponsored message not found"));
        if (!chatId.equals(sponsoredMessage.getChannelChatId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sponsored message not found");
        }
        return sponsoredMessage;
    }

    private void ensureCanManageMonetization(UUID requesterId, UUID chatId) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        if (!"CHANNEL".equals(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Monetization is available only for channels");
        }
        if (!chatService.hasMessageModerationPermission(requesterId, chatId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Monetization management is not allowed for this member");
        }
    }

    private SponsoredMessageResponse toResponse(SponsoredMessageEntity sponsoredMessage) {
        long impressionsCount = sponsoredMessageEventRepository.countBySponsoredMessageIdAndEventType(
                sponsoredMessage.getId(),
                "IMPRESSION"
        );
        long clicksCount = sponsoredMessageEventRepository.countBySponsoredMessageIdAndEventType(
                sponsoredMessage.getId(),
                "CLICK"
        );
        long spentUnits = sponsoredMessage.getSpentUnits() != null ? sponsoredMessage.getSpentUnits() : 0L;
        long budgetUnits = sponsoredMessage.getBudgetUnits() != null ? sponsoredMessage.getBudgetUnits() : 0L;
        return new SponsoredMessageResponse(
                sponsoredMessage.getId(),
                sponsoredMessage.getChannelChatId(),
                sponsoredMessage.getSponsorUserId(),
                sponsoredMessage.getCreatedByUserId(),
                sponsoredMessage.getTitle(),
                sponsoredMessage.getMessageText(),
                sponsoredMessage.getCallToActionLabel(),
                sponsoredMessage.getCallToActionUrl(),
                budgetUnits,
                spentUnits,
                Math.max(0L, budgetUnits - spentUnits),
                sponsoredMessage.getCostPerImpressionUnits() != null ? sponsoredMessage.getCostPerImpressionUnits() : 0L,
                sponsoredMessage.getCostPerClickUnits() != null ? sponsoredMessage.getCostPerClickUnits() : 0L,
                impressionsCount,
                clicksCount,
                sponsoredMessage.getStatus(),
                sponsoredMessage.getDeliveredMessageId(),
                sponsoredMessage.getCreatedAt(),
                sponsoredMessage.getUpdatedAt(),
                sponsoredMessage.getPublishedAt(),
                sponsoredMessage.getActiveUntil()
        );
    }

    private int countByStatus(List<SponsoredMessageEntity> sponsoredMessages, String status) {
        return (int) sponsoredMessages.stream().filter(message -> status.equals(message.getStatus())).count();
    }

    private String composeSponsoredText(SponsoredMessageEntity sponsoredMessage) {
        StringBuilder builder = new StringBuilder();
        builder.append("Sponsored: ").append(sponsoredMessage.getTitle()).append("\n\n");
        builder.append(sponsoredMessage.getMessageText());
        if (sponsoredMessage.getCallToActionLabel() != null && sponsoredMessage.getCallToActionUrl() != null) {
            builder.append("\n\n");
            builder.append(sponsoredMessage.getCallToActionLabel()).append(": ").append(sponsoredMessage.getCallToActionUrl());
        }
        return builder.toString();
    }

    private void requireUser(UUID userId) {
        if (userRepository.findById(userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    private String normalizeRequired(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is too long");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value is too long");
        }
        return normalized;
    }

    private String normalizeHttpUrl(String value) {
        String normalized = normalizeOptional(value, 512);
        if (normalized == null) {
            return null;
        }
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(java.util.Locale.ROOT) : null;
            if (!uri.isAbsolute() || scheme == null || (!"http".equals(scheme) && !"https".equals(scheme))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sponsored message CTA URL must be a valid http(s) URL");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sponsored message CTA URL must be a valid http(s) URL");
        }
    }

    private long normalizePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be positive");
        }
        return value;
    }

    private long normalizeNonNegative(Long value, long fallback, String field) {
        long normalized = value != null ? value : fallback;
        if (normalized < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " cannot be negative");
        }
        return normalized;
    }
}
