package com.alex.messenger.user;

import com.alex.messenger.attachment.AttachmentEntity;
import com.alex.messenger.attachment.AttachmentRepository;
import com.alex.messenger.payments.PaymentWalletTransactionRepository;
import com.alex.messenger.premium.PremiumGiftRepository;
import com.alex.messenger.user.dto.ProfileAudioAttachmentResponse;
import com.alex.messenger.user.dto.UpdateProfileTabRequest;
import com.alex.messenger.user.dto.UpsertProfileAudioRequest;
import com.alex.messenger.user.dto.UserProfileAudioResponse;
import com.alex.messenger.user.dto.UserProfilePreferencesResponse;
import com.alex.messenger.user.dto.UserProfileRatingResponse;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserProfileMetadataService {

    private static final Set<String> PROFILE_TABS = Set.of(
            "MEDIA",
            "FILES",
            "LINKS",
            "AUDIO",
            "GIFTS",
            "CHANNELS",
            "GROUPS",
            "STORIES"
    );
    private static final Set<String> PROFILE_AUDIO_KINDS = Set.of("AUDIO", "VOICE");
    private static final List<String> STAR_TRANSACTION_TYPES = List.of("PAYMENT", "SPONSORED_MESSAGE");

    private final UserRepository userRepository;
    private final UserProfilePreferenceRepository userProfilePreferenceRepository;
    private final UserProfileRatingRepository userProfileRatingRepository;
    private final ProfileAudioRepository profileAudioRepository;
    private final AttachmentRepository attachmentRepository;
    private final PremiumGiftRepository premiumGiftRepository;
    private final PaymentWalletTransactionRepository paymentWalletTransactionRepository;

    @Transactional
    public UserProfilePreferencesResponse updateProfileTab(UUID requesterId, UpdateProfileTabRequest request) {
        requireUser(requesterId);
        UserProfilePreferenceEntity entity = userProfilePreferenceRepository.findById(requesterId)
                .orElseGet(() -> {
                    UserProfilePreferenceEntity preference = new UserProfilePreferenceEntity();
                    preference.setUserId(requesterId);
                    return preference;
                });
        entity.setDefaultProfileTab(normalizeProfileTab(request != null ? request.defaultProfileTab() : null));
        return toPreferencesResponse(userProfilePreferenceRepository.save(entity));
    }

    @Transactional
    public UserProfileAudioResponse getProfileAudio(UUID requesterId) {
        requireUser(requesterId);
        ProfileAudioEntity entity = profileAudioRepository.findById(requesterId).orElse(null);
        if (entity == null) {
            return emptyAudioResponse(requesterId);
        }
        AttachmentEntity attachment = attachmentRepository.findById(entity.getAttachmentId()).orElse(null);
        if (attachment == null) {
            return emptyAudioResponse(requesterId);
        }
        return toAudioResponse(entity, attachment);
    }

    @Transactional
    public UserProfileAudioResponse upsertProfileAudio(UUID requesterId, UpsertProfileAudioRequest request) {
        requireUser(requesterId);
        UUID attachmentId = request != null ? request.attachmentId() : null;
        if (attachmentId == null) {
            if (hasProfileAudioMetadata(request)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Attachment is required when profile audio metadata is provided"
                );
            }
            profileAudioRepository.findById(requesterId).ifPresent(profileAudioRepository::delete);
            return emptyAudioResponse(requesterId);
        }

        AttachmentEntity attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
        if (!requesterId.equals(attachment.getUploaderUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only your own audio attachment can be used");
        }
        if (!PROFILE_AUDIO_KINDS.contains(normalizeUpper(attachment.getKind()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile audio must be an audio attachment");
        }
        if (!"APPROVED".equals(normalizeUpper(attachment.getModerationStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Profile audio attachment is not approved");
        }

        ProfileAudioEntity entity = profileAudioRepository.findById(requesterId).orElseGet(() -> {
            ProfileAudioEntity profileAudio = new ProfileAudioEntity();
            profileAudio.setUserId(requesterId);
            return profileAudio;
        });
        entity.setAttachmentId(attachmentId);
        entity.setTitle(normalizeOptional(request.title(), 120));
        entity.setPerformer(normalizeOptional(request.performer(), 120));
        entity.setCaption(normalizeOptional(request.caption(), 255));
        return toAudioResponse(profileAudioRepository.save(entity), attachment);
    }

    @Transactional
    public UserProfileRatingResponse getProfileRating(UUID requesterId) {
        requireUser(requesterId);
        UserProfileRatingEntity rating = recomputeRating(requesterId);
        return toRatingResponse(rating);
    }

    private UserProfileRatingEntity recomputeRating(UUID userId) {
        long receivedGiftCount = premiumGiftRepository.countByRecipientUserId(userId);
        long sentGiftCount = premiumGiftRepository.countBySenderUserId(userId);
        long receivedGiftPremiumDays = defaultLong(premiumGiftRepository.sumPremiumDaysByRecipientUserId(userId));
        long sentGiftPremiumDays = defaultLong(premiumGiftRepository.sumPremiumDaysBySenderUserId(userId));
        long starsReceivedUnits = defaultLong(
                paymentWalletTransactionRepository.sumAmountUnitsByWalletUserIdAndDirectionAndTransactionTypeIn(
                        userId,
                        "CREDIT",
                        STAR_TRANSACTION_TYPES
                )
        );
        long starsSpentUnits = defaultLong(
                paymentWalletTransactionRepository.sumAmountUnitsByWalletUserIdAndDirectionAndTransactionTypeIn(
                        userId,
                        "DEBIT",
                        STAR_TRANSACTION_TYPES
                )
        );
        long successfulTransactionCount = receivedGiftCount
                + sentGiftCount
                + paymentWalletTransactionRepository.countByWalletUserIdAndTransactionTypeIn(userId, STAR_TRANSACTION_TYPES);

        long giftPoints = (receivedGiftCount * 50L)
                + (sentGiftCount * 25L)
                + (receivedGiftPremiumDays * 10L)
                + (sentGiftPremiumDays * 5L);
        long starPoints = starsReceivedUnits + (starsSpentUnits / 2L);
        long score = giftPoints + starPoints + (successfulTransactionCount * 20L);
        String level = resolveRatingLevel(score);

        UserProfileRatingEntity entity = userProfileRatingRepository.findById(userId).orElseGet(() -> {
            UserProfileRatingEntity rating = new UserProfileRatingEntity();
            rating.setUserId(userId);
            return rating;
        });
        entity.setRatingScore(score);
        entity.setRatingLevel(level);
        entity.setReceivedGiftCount(receivedGiftCount);
        entity.setSentGiftCount(sentGiftCount);
        entity.setReceivedGiftPremiumDays(receivedGiftPremiumDays);
        entity.setSentGiftPremiumDays(sentGiftPremiumDays);
        entity.setStarsReceivedUnits(starsReceivedUnits);
        entity.setStarsSpentUnits(starsSpentUnits);
        entity.setSuccessfulTransactionCount(successfulTransactionCount);
        entity.setLastRecomputedAt(Instant.now());
        return userProfileRatingRepository.save(entity);
    }

    private UserProfilePreferencesResponse toPreferencesResponse(UserProfilePreferenceEntity entity) {
        return new UserProfilePreferencesResponse(
                entity.getUserId(),
                entity.getDefaultProfileTab(),
                entity.getUpdatedAt()
        );
    }

    private UserProfileAudioResponse toAudioResponse(ProfileAudioEntity entity, AttachmentEntity attachment) {
        return new UserProfileAudioResponse(
                entity.getUserId(),
                entity.getAttachmentId(),
                entity.getTitle(),
                entity.getPerformer(),
                entity.getCaption(),
                new ProfileAudioAttachmentResponse(
                        attachment.getId(),
                        attachment.getOriginalFileName(),
                        attachment.getContentType(),
                        attachment.getKind(),
                        attachment.getFileSizeBytes(),
                        attachment.getDurationMs(),
                        attachment.getCreatedAt()
                ),
                entity.getUpdatedAt()
        );
    }

    private UserProfileAudioResponse emptyAudioResponse(UUID userId) {
        return new UserProfileAudioResponse(userId, null, null, null, null, null, null);
    }

    private UserProfileRatingResponse toRatingResponse(UserProfileRatingEntity entity) {
        return new UserProfileRatingResponse(
                entity.getUserId(),
                defaultLong(entity.getRatingScore()),
                entity.getRatingLevel(),
                defaultLong(entity.getReceivedGiftCount()),
                defaultLong(entity.getSentGiftCount()),
                defaultLong(entity.getReceivedGiftPremiumDays()),
                defaultLong(entity.getSentGiftPremiumDays()),
                defaultLong(entity.getStarsReceivedUnits()),
                defaultLong(entity.getStarsSpentUnits()),
                defaultLong(entity.getSuccessfulTransactionCount()),
                entity.getLastRecomputedAt()
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

    private String normalizeProfileTab(String value) {
        String normalized = normalizeUpper(value);
        if (normalized == null || !PROFILE_TABS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported profile tab");
        }
        return normalized;
    }

    private String normalizeUpper(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private boolean hasProfileAudioMetadata(UpsertProfileAudioRequest request) {
        return request != null
                && (hasText(request.title()) || hasText(request.performer()) || hasText(request.caption()));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
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

    private long defaultLong(Long value) {
        return value != null ? value : 0L;
    }

    private String resolveRatingLevel(long score) {
        if (score >= 5000L) {
            return "ICON";
        }
        if (score >= 1500L) {
            return "TRUSTED";
        }
        if (score >= 500L) {
            return "RECOGNIZED";
        }
        if (score >= 100L) {
            return "RISING";
        }
        return "NEW";
    }
}
