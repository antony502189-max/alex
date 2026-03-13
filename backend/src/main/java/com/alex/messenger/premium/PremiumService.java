package com.alex.messenger.premium;

import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.premium.dto.ActivatePremiumTrialRequest;
import com.alex.messenger.premium.dto.BoostChannelRequest;
import com.alex.messenger.premium.dto.ChannelBoostResponse;
import com.alex.messenger.premium.dto.ChannelBoostStatsResponse;
import com.alex.messenger.premium.dto.PremiumCustomEmojiResponse;
import com.alex.messenger.premium.dto.PremiumGiftResponse;
import com.alex.messenger.premium.dto.PremiumProfileResponse;
import com.alex.messenger.premium.dto.SendPremiumGiftRequest;
import com.alex.messenger.premium.dto.UpdateEmojiStatusRequest;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
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
public class PremiumService {

    private static final Duration DEFAULT_TRIAL_DURATION = Duration.ofDays(30);
    private static final int MAX_TRIAL_DAYS = 365;
    private static final int DEFAULT_GIFT_DAYS = 30;
    private static final int MAX_GIFT_DAYS = 365;
    private static final int MAX_BOOSTS_PER_USER = 10;

    private final PremiumEntitlementRepository premiumEntitlementRepository;
    private final PremiumCustomEmojiRepository premiumCustomEmojiRepository;
    private final PremiumGiftRepository premiumGiftRepository;
    private final ChannelBoostRepository channelBoostRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;

    @Transactional(readOnly = true)
    public PremiumProfileResponse getProfile(UUID requesterId) {
        requireUser(requesterId);
        return toProfileResponse(getOrCreateEntitlement(requesterId));
    }

    @Transactional
    public PremiumProfileResponse activateTrial(UUID requesterId, ActivatePremiumTrialRequest request) {
        requireUser(requesterId);
        PremiumEntitlementEntity entitlement = getOrCreateEntitlement(requesterId);
        int days = normalizeTrialDays(request != null ? request.durationDays() : null);
        Instant base = entitlement.getActiveUntil() != null && entitlement.getActiveUntil().isAfter(Instant.now())
                ? entitlement.getActiveUntil()
                : Instant.now();
        entitlement.setTier("PREMIUM");
        entitlement.setActiveUntil(base.plus(Duration.ofDays(days)));
        return toProfileResponse(premiumEntitlementRepository.save(entitlement));
    }

    @Transactional(readOnly = true)
    public List<PremiumCustomEmojiResponse> listCustomEmojis(UUID requesterId) {
        boolean premiumActive = isPremiumActive(getOrCreateEntitlement(requesterId));
        return premiumCustomEmojiRepository.findAllByOrderByPositionAscCreatedAtAsc().stream()
                .filter(emoji -> premiumActive || !Boolean.TRUE.equals(emoji.getPremiumRequired()))
                .map(this::toCustomEmojiResponse)
                .toList();
    }

    @Transactional
    public PremiumProfileResponse updateEmojiStatus(UUID requesterId, UpdateEmojiStatusRequest request) {
        PremiumEntitlementEntity entitlement = requireActivePremium(requesterId);
        if (request == null || request.customEmojiId() == null) {
            entitlement.setCustomEmojiStatusId(null);
            entitlement.setCustomEmojiStatusEmoji(null);
            entitlement.setCustomEmojiStatusLabel(null);
            return toProfileResponse(premiumEntitlementRepository.save(entitlement));
        }

        PremiumCustomEmojiEntity emoji = premiumCustomEmojiRepository.findById(request.customEmojiId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Premium custom emoji not found"));
        entitlement.setCustomEmojiStatusId(emoji.getId());
        entitlement.setCustomEmojiStatusEmoji(emoji.getEmoji());
        entitlement.setCustomEmojiStatusLabel(emoji.getLabel());
        return toProfileResponse(premiumEntitlementRepository.save(entitlement));
    }

    @Transactional(readOnly = true)
    public List<PremiumGiftResponse> listReceivedGifts(UUID requesterId) {
        requireUser(requesterId);
        return toGiftResponses(premiumGiftRepository.findAllByRecipientUserIdOrderByCreatedAtDesc(requesterId));
    }

    @Transactional(readOnly = true)
    public List<PremiumGiftResponse> listSentGifts(UUID requesterId) {
        requireUser(requesterId);
        return toGiftResponses(premiumGiftRepository.findAllBySenderUserIdOrderByCreatedAtDesc(requesterId));
    }

    @Transactional
    public PremiumGiftResponse sendGift(UUID requesterId, SendPremiumGiftRequest request) {
        if (requesterId.equals(request.recipientUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot send a premium gift to yourself");
        }
        requireActivePremium(requesterId);
        UserEntity sender = requireUser(requesterId);
        UserEntity recipient = requireUser(request.recipientUserId());
        PremiumCustomEmojiEntity emoji = request.customEmojiId() != null
                ? premiumCustomEmojiRepository.findById(request.customEmojiId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Premium custom emoji not found"))
                : null;

        PremiumGiftEntity gift = new PremiumGiftEntity();
        gift.setSenderUserId(sender.getId());
        gift.setRecipientUserId(recipient.getId());
        gift.setCustomEmojiId(emoji != null ? emoji.getId() : null);
        gift.setMessage(normalizeOptional(request.message(), 255));
        gift.setPremiumDaysGranted(normalizeGiftDays(request.premiumDaysGranted()));
        PremiumGiftEntity savedGift = premiumGiftRepository.save(gift);

        PremiumEntitlementEntity recipientEntitlement = getOrCreateEntitlement(recipient.getId());
        Instant base = recipientEntitlement.getActiveUntil() != null
                && recipientEntitlement.getActiveUntil().isAfter(Instant.now())
                ? recipientEntitlement.getActiveUntil()
                : Instant.now();
        recipientEntitlement.setTier("PREMIUM");
        recipientEntitlement.setActiveUntil(base.plus(Duration.ofDays(savedGift.getPremiumDaysGranted())));
        premiumEntitlementRepository.save(recipientEntitlement);

        return toGiftResponses(List.of(savedGift)).get(0);
    }

    @Transactional(readOnly = true)
    public ChannelBoostStatsResponse getChannelBoostStats(UUID requesterId, UUID chatId) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        ensureBoostableChannel(chat);
        List<ChannelBoostEntity> boosts = channelBoostRepository.findAllByChannelChatIdOrderByUpdatedAtDesc(chatId);
        int totalBoosts = boosts.stream().mapToInt(boost -> boost.getBoostCount() != null ? boost.getBoostCount() : 0).sum();
        int viewerBoostCount = boosts.stream()
                .filter(boost -> requesterId.equals(boost.getBoostedByUserId()))
                .mapToInt(boost -> boost.getBoostCount() != null ? boost.getBoostCount() : 0)
                .sum();
        return new ChannelBoostStatsResponse(
                chatId,
                totalBoosts,
                boosts.size(),
                viewerBoostCount,
                boosts.stream().map(this::toChannelBoostResponse).toList()
        );
    }

    @Transactional
    public ChannelBoostStatsResponse boostChannel(UUID requesterId, UUID chatId, BoostChannelRequest request) {
        requireActivePremium(requesterId);
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        ensureBoostableChannel(chat);
        int boostCount = normalizeBoostCount(request != null ? request.boostCount() : null);

        ChannelBoostEntity boost = channelBoostRepository.findByChannelChatIdAndBoostedByUserId(chatId, requesterId)
                .orElseGet(ChannelBoostEntity::new);
        boost.setChannelChatId(chatId);
        boost.setBoostedByUserId(requesterId);
        boost.setBoostCount(boostCount);
        channelBoostRepository.save(boost);
        return getChannelBoostStats(requesterId, chatId);
    }

    private PremiumEntitlementEntity getOrCreateEntitlement(UUID userId) {
        return premiumEntitlementRepository.findById(userId).orElseGet(() -> {
            PremiumEntitlementEntity entity = new PremiumEntitlementEntity();
            entity.setUserId(userId);
            return premiumEntitlementRepository.save(entity);
        });
    }

    private PremiumEntitlementEntity requireActivePremium(UUID userId) {
        PremiumEntitlementEntity entitlement = getOrCreateEntitlement(userId);
        if (!isPremiumActive(entitlement)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Premium is required");
        }
        return entitlement;
    }

    private boolean isPremiumActive(PremiumEntitlementEntity entitlement) {
        return entitlement.getActiveUntil() != null && entitlement.getActiveUntil().isAfter(Instant.now());
    }

    private PremiumProfileResponse toProfileResponse(PremiumEntitlementEntity entitlement) {
        return new PremiumProfileResponse(
                entitlement.getUserId(),
                entitlement.getTier() != null ? entitlement.getTier() : "PREMIUM",
                isPremiumActive(entitlement),
                entitlement.getActiveUntil(),
                entitlement.getCustomEmojiStatusId(),
                entitlement.getCustomEmojiStatusEmoji(),
                entitlement.getCustomEmojiStatusLabel()
        );
    }

    private PremiumCustomEmojiResponse toCustomEmojiResponse(PremiumCustomEmojiEntity entity) {
        return new PremiumCustomEmojiResponse(
                entity.getId(),
                entity.getShortCode(),
                entity.getEmoji(),
                entity.getLabel(),
                Boolean.TRUE.equals(entity.getPremiumRequired())
        );
    }

    private List<PremiumGiftResponse> toGiftResponses(List<PremiumGiftEntity> gifts) {
        if (gifts.isEmpty()) {
            return List.of();
        }
        Map<UUID, UserEntity> usersById = userRepository.findAllById(
                gifts.stream()
                        .flatMap(gift -> java.util.stream.Stream.of(gift.getSenderUserId(), gift.getRecipientUserId()))
                        .distinct()
                        .toList()
        ).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        Map<UUID, PremiumCustomEmojiEntity> emojisById = premiumCustomEmojiRepository.findAllById(
                gifts.stream()
                        .map(PremiumGiftEntity::getCustomEmojiId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList()
        ).stream().collect(Collectors.toMap(PremiumCustomEmojiEntity::getId, Function.identity()));

        return gifts.stream().map(gift -> {
            UserEntity sender = usersById.get(gift.getSenderUserId());
            UserEntity recipient = usersById.get(gift.getRecipientUserId());
            PremiumCustomEmojiEntity emoji = gift.getCustomEmojiId() != null
                    ? emojisById.get(gift.getCustomEmojiId())
                    : null;
            return new PremiumGiftResponse(
                    gift.getId(),
                    gift.getSenderUserId(),
                    sender != null ? sender.getDisplayName() : "Unknown",
                    gift.getRecipientUserId(),
                    recipient != null ? recipient.getDisplayName() : "Unknown",
                    gift.getCustomEmojiId(),
                    emoji != null ? emoji.getEmoji() : null,
                    emoji != null ? emoji.getLabel() : null,
                    gift.getMessage(),
                    gift.getPremiumDaysGranted(),
                    gift.getCreatedAt()
            );
        }).toList();
    }

    private ChannelBoostResponse toChannelBoostResponse(ChannelBoostEntity entity) {
        return new ChannelBoostResponse(
                entity.getChannelChatId(),
                entity.getBoostedByUserId(),
                entity.getBoostCount() != null ? entity.getBoostCount() : 0,
                entity.getUpdatedAt()
        );
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void ensureBoostableChannel(ChatEntity chat) {
        if (!"CHANNEL".equals(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Channel boosts are available only for channels");
        }
    }

    private int normalizeTrialDays(Integer days) {
        int normalized = days != null ? days : (int) DEFAULT_TRIAL_DURATION.toDays();
        return Math.max(1, Math.min(normalized, MAX_TRIAL_DAYS));
    }

    private int normalizeGiftDays(Integer days) {
        int normalized = days != null ? days : DEFAULT_GIFT_DAYS;
        return Math.max(1, Math.min(normalized, MAX_GIFT_DAYS));
    }

    private int normalizeBoostCount(Integer boostCount) {
        int normalized = boostCount != null ? boostCount : 1;
        return Math.max(1, Math.min(normalized, MAX_BOOSTS_PER_USER));
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
}
