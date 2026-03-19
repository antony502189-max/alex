package com.alex.messenger.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.attachment.AttachmentEntity;
import com.alex.messenger.attachment.AttachmentRepository;
import com.alex.messenger.payments.PaymentWalletTransactionRepository;
import com.alex.messenger.premium.PremiumGiftRepository;
import com.alex.messenger.user.dto.UpdateProfileTabRequest;
import com.alex.messenger.user.dto.UpsertProfileAudioRequest;
import com.alex.messenger.user.dto.UserProfileAudioResponse;
import com.alex.messenger.user.dto.UserProfileRatingResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserProfileMetadataServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfilePreferenceRepository userProfilePreferenceRepository;

    @Mock
    private UserProfileRatingRepository userProfileRatingRepository;

    @Mock
    private ProfileAudioRepository profileAudioRepository;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private PremiumGiftRepository premiumGiftRepository;

    @Mock
    private PaymentWalletTransactionRepository paymentWalletTransactionRepository;

    private UserProfileMetadataService userProfileMetadataService;

    @BeforeEach
    void setUp() {
        userProfileMetadataService = new UserProfileMetadataService(
                userRepository,
                userProfilePreferenceRepository,
                userProfileRatingRepository,
                profileAudioRepository,
                attachmentRepository,
                premiumGiftRepository,
                paymentWalletTransactionRepository
        );
    }

    @Test
    void updateProfileTabCreatesPreferenceWithNormalizedValue() {
        UUID requesterId = UUID.randomUUID();
        UserEntity user = activeUser(requesterId);

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user));
        when(userProfilePreferenceRepository.findById(requesterId)).thenReturn(Optional.empty());
        when(userProfilePreferenceRepository.save(any(UserProfilePreferenceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = userProfileMetadataService.updateProfileTab(
                requesterId,
                new UpdateProfileTabRequest(" audio ")
        );

        assertThat(response.userId()).isEqualTo(requesterId);
        assertThat(response.defaultProfileTab()).isEqualTo("AUDIO");
    }

    @Test
    void getProfileRatingAggregatesGiftAndStarSignals() {
        UUID requesterId = UUID.randomUUID();
        UserEntity user = activeUser(requesterId);

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user));
        when(premiumGiftRepository.countByRecipientUserId(requesterId)).thenReturn(3L);
        when(premiumGiftRepository.countBySenderUserId(requesterId)).thenReturn(2L);
        when(premiumGiftRepository.sumPremiumDaysByRecipientUserId(requesterId)).thenReturn(120L);
        when(premiumGiftRepository.sumPremiumDaysBySenderUserId(requesterId)).thenReturn(45L);
        when(paymentWalletTransactionRepository.sumAmountUnitsByWalletUserIdAndDirectionAndTransactionTypeIn(
                requesterId,
                "CREDIT",
                List.of("PAYMENT", "SPONSORED_MESSAGE")
        )).thenReturn(700L);
        when(paymentWalletTransactionRepository.sumAmountUnitsByWalletUserIdAndDirectionAndTransactionTypeIn(
                requesterId,
                "DEBIT",
                List.of("PAYMENT", "SPONSORED_MESSAGE")
        )).thenReturn(160L);
        when(paymentWalletTransactionRepository.countByWalletUserIdAndTransactionTypeIn(
                requesterId,
                List.of("PAYMENT", "SPONSORED_MESSAGE")
        )).thenReturn(4L);
        when(userProfileRatingRepository.findById(requesterId)).thenReturn(Optional.empty());
        when(userProfileRatingRepository.save(any(UserProfileRatingEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileRatingResponse response = userProfileMetadataService.getProfileRating(requesterId);

        assertThat(response.userId()).isEqualTo(requesterId);
        assertThat(response.ratingScore()).isEqualTo(2585L);
        assertThat(response.ratingLevel()).isEqualTo("TRUSTED");
        assertThat(response.successfulTransactionCount()).isEqualTo(9L);
        assertThat(response.starsReceivedUnits()).isEqualTo(700L);
        assertThat(response.starsSpentUnits()).isEqualTo(160L);
    }

    @Test
    void upsertProfileAudioRejectsAttachmentFromAnotherUser() {
        UUID requesterId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UserEntity user = activeUser(requesterId);

        AttachmentEntity attachment = audioAttachment(attachmentId, UUID.randomUUID());
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user));
        when(attachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));

        ResponseStatusException exception = catchThrowableOfType(
                () -> userProfileMetadataService.upsertProfileAudio(
                        requesterId,
                        new UpsertProfileAudioRequest(attachmentId, "Profile theme", "Performer", "Caption")
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void upsertProfileAudioClearsSavedAudioWhenAttachmentIdMissing() {
        UUID requesterId = UUID.randomUUID();
        UserEntity user = activeUser(requesterId);
        ProfileAudioEntity existing = new ProfileAudioEntity();
        existing.setUserId(requesterId);
        existing.setAttachmentId(UUID.randomUUID());
        existing.setUpdatedAt(Instant.parse("2026-03-19T10:00:00Z"));

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user));
        when(profileAudioRepository.findById(requesterId)).thenReturn(Optional.of(existing));

        UserProfileAudioResponse response = userProfileMetadataService.upsertProfileAudio(
                requesterId,
                new UpsertProfileAudioRequest(null, null, null, null)
        );

        assertThat(response.userId()).isEqualTo(requesterId);
        assertThat(response.attachmentId()).isNull();
        verify(profileAudioRepository).delete(existing);
    }

    private UserEntity activeUser(UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setPhoneNumber("+375291234567");
        user.setDisplayName("Requester");
        user.setPhonePrivacy("EVERYBODY");
        user.setLastSeenPrivacy("EVERYBODY");
        user.setStoryPrivacy("EVERYBODY");
        return user;
    }

    private AttachmentEntity audioAttachment(UUID attachmentId, UUID uploaderUserId) {
        AttachmentEntity attachment = new AttachmentEntity();
        attachment.setId(attachmentId);
        attachment.setUploaderUserId(uploaderUserId);
        attachment.setChatId(UUID.randomUUID());
        attachment.setOriginalFileName("profile-theme.ogg");
        attachment.setContentType("audio/ogg");
        attachment.setKind("AUDIO");
        attachment.setFileSizeBytes(1024L);
        attachment.setDurationMs(30_000L);
        attachment.setModerationStatus("APPROVED");
        attachment.setCreatedAt(Instant.parse("2026-03-19T09:00:00Z"));
        return attachment;
    }
}
