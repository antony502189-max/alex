package com.alex.messenger.monetization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.SendMessageRequest;
import com.alex.messenger.monetization.dto.AssignMonetizationArtifactSubscriptionAlertRequest;
import com.alex.messenger.monetization.dto.CreateMonetizationArtifactAlertCommentRequest;
import com.alex.messenger.monetization.dto.CreateMonetizationOwnerReminderDigestSubscriptionRequest;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertWorkloadResponse;
import com.alex.messenger.monetization.dto.MonetizationProviderReconciliationRequest;
import com.alex.messenger.monetization.dto.MonetizationProviderStatusUpdateRequest;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertReminderResponse;
import com.alex.messenger.monetization.dto.CreateMonetizationWithdrawalRequest;
import com.alex.messenger.monetization.dto.CreateMonetizationArtifactSubscriptionRequest;
import com.alex.messenger.monetization.dto.CreateSponsoredMessageRequest;
import com.alex.messenger.monetization.dto.GenerateMonetizationAlertDigestRequest;
import com.alex.messenger.monetization.dto.MonetizationWithdrawalProviderCallbackRequest;
import com.alex.messenger.monetization.dto.PublishMonetizationArtifactRequest;
import com.alex.messenger.monetization.dto.SnoozeMonetizationArtifactSubscriptionAlertRequest;
import com.alex.messenger.monetization.dto.UpdateMonetizationAlertPolicyRequest;
import com.alex.messenger.payments.PaymentService;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MonetizationServiceTest {

    @Mock
    private SponsoredMessageRepository sponsoredMessageRepository;

    @Mock
    private SponsoredMessageEventRepository sponsoredMessageEventRepository;

    @Mock
    private ChannelMonetizationPayoutRepository channelMonetizationPayoutRepository;

    @Mock
    private ChannelMonetizationPayoutItemRepository channelMonetizationPayoutItemRepository;

    @Mock
    private ChannelMonetizationWithdrawalRepository channelMonetizationWithdrawalRepository;

    @Mock
    private ChannelMonetizationReconciliationRunRepository channelMonetizationReconciliationRunRepository;

    @Mock
    private ChannelMonetizationWithdrawalCallbackRepository channelMonetizationWithdrawalCallbackRepository;

    @Mock
    private ChannelMonetizationExportArtifactRepository channelMonetizationExportArtifactRepository;

    @Mock
    private ChannelMonetizationProviderSyncRunRepository channelMonetizationProviderSyncRunRepository;

    @Mock
    private ChannelMonetizationArtifactPublicationRepository channelMonetizationArtifactPublicationRepository;

    @Mock
    private ChannelMonetizationArtifactSubscriptionRepository channelMonetizationArtifactSubscriptionRepository;

    @Mock
    private ChannelMonetizationArtifactSubscriptionFailureRepository channelMonetizationArtifactSubscriptionFailureRepository;

    @Mock
    private ChannelMonetizationArtifactSubscriptionAlertRepository channelMonetizationArtifactSubscriptionAlertRepository;

    @Mock
    private ChannelMonetizationArtifactAlertCommentRepository channelMonetizationArtifactAlertCommentRepository;

    @Mock
    private ChannelMonetizationArtifactAlertAuditEventRepository channelMonetizationArtifactAlertAuditEventRepository;

    @Mock
    private ChannelMonetizationAlertDigestRunRepository channelMonetizationAlertDigestRunRepository;

    @Mock
    private ChannelMonetizationAlertPolicyRepository channelMonetizationAlertPolicyRepository;

    @Mock
    private ChannelMonetizationOwnerReminderDigestSubscriptionRepository
            channelMonetizationOwnerReminderDigestSubscriptionRepository;

    @Mock
    private ChannelMonetizationOwnerReminderDigestRunRepository channelMonetizationOwnerReminderDigestRunRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageService messageService;

    @Mock
    private PaymentService paymentService;

    private MonetizationService monetizationService;

    @BeforeEach
    void setUp() {
        monetizationService = new MonetizationService(
                sponsoredMessageRepository,
                sponsoredMessageEventRepository,
                channelMonetizationPayoutRepository,
                channelMonetizationPayoutItemRepository,
                channelMonetizationWithdrawalRepository,
                channelMonetizationReconciliationRunRepository,
                channelMonetizationWithdrawalCallbackRepository,
                channelMonetizationExportArtifactRepository,
                channelMonetizationProviderSyncRunRepository,
                channelMonetizationArtifactPublicationRepository,
                channelMonetizationArtifactSubscriptionRepository,
                channelMonetizationArtifactSubscriptionFailureRepository,
                channelMonetizationArtifactSubscriptionAlertRepository,
                channelMonetizationArtifactAlertCommentRepository,
                channelMonetizationArtifactAlertAuditEventRepository,
                channelMonetizationAlertDigestRunRepository,
                channelMonetizationAlertPolicyRepository,
                channelMonetizationOwnerReminderDigestSubscriptionRepository,
                channelMonetizationOwnerReminderDigestRunRepository,
                chatService,
                userRepository,
                messageService,
                paymentService,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void publishSponsoredMessageCreatesChannelMessageAndActivatesCampaign() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID sponsoredMessageId = UUID.randomUUID();
        UUID deliveredMessageId = UUID.randomUUID();
        SponsoredMessageEntity sponsoredMessage = sponsoredMessage(
                sponsoredMessageId,
                chatId,
                UUID.randomUUID(),
                requesterId,
                100,
                1,
                5
        );
        ChatEntity channel = channel(chatId, sponsoredMessage.getSponsorUserId());
        sponsoredMessage.setTitle("Launch");
        sponsoredMessage.setMessageText("Sponsored body");
        sponsoredMessage.setCallToActionLabel("Open");
        sponsoredMessage.setCallToActionUrl("https://example.com/launch");

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.getChat(chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(sponsoredMessageRepository.findById(sponsoredMessageId)).thenReturn(Optional.of(sponsoredMessage));
        when(sponsoredMessageRepository.save(any(SponsoredMessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sponsoredMessageEventRepository.countBySponsoredMessageIdAndEventType(sponsoredMessageId, "IMPRESSION")).thenReturn(0L);
        when(sponsoredMessageEventRepository.countBySponsoredMessageIdAndEventType(sponsoredMessageId, "CLICK")).thenReturn(0L);
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(deliveredMessageId));

        var response = monetizationService.publishSponsoredMessage(requesterId, chatId, sponsoredMessageId);

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(requesterId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(chatId);
        assertThat(requestCaptor.getValue().silent()).isTrue();
        assertThat(requestCaptor.getValue().text()).contains("Sponsored: Launch");
        assertThat(requestCaptor.getValue().text()).contains("Open: https://example.com/launch");
        assertThat(response.deliveredMessageId()).isEqualTo(deliveredMessageId);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.publishedAt()).isNotNull();
    }

    @Test
    void recordImpressionConsumesBudgetOncePerViewer() {
        UUID viewerId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID sponsoredMessageId = UUID.randomUUID();
        UUID sponsorUserId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, ownerUserId);
        SponsoredMessageEntity sponsoredMessage = sponsoredMessage(
                sponsoredMessageId,
                chatId,
                sponsorUserId,
                UUID.randomUUID(),
                100,
                7,
                20
        );
        sponsoredMessage.setStatus("ACTIVE");
        sponsoredMessage.setPublishedAt(Instant.parse("2026-03-14T10:00:00Z"));
        AtomicBoolean impressionRecorded = new AtomicBoolean(false);

        when(chatService.getOwnedChat(viewerId, chatId)).thenReturn(channel);
        when(chatService.getChat(chatId)).thenReturn(channel);
        when(sponsoredMessageRepository.findById(sponsoredMessageId)).thenReturn(Optional.of(sponsoredMessage));
        when(sponsoredMessageRepository.save(any(SponsoredMessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sponsoredMessageEventRepository.existsBySponsoredMessageIdAndViewerUserIdAndEventType(
                sponsoredMessageId,
                viewerId,
                "IMPRESSION"
        )).thenAnswer(invocation -> impressionRecorded.get());
        when(sponsoredMessageEventRepository.save(any(SponsoredMessageEventEntity.class))).thenAnswer(invocation -> {
            impressionRecorded.set(true);
            return invocation.getArgument(0);
        });
        when(paymentService.hasAvailableBalance(sponsorUserId, 7L)).thenReturn(true);
        when(sponsoredMessageEventRepository.countBySponsoredMessageIdAndEventType(sponsoredMessageId, "IMPRESSION"))
                .thenAnswer(invocation -> impressionRecorded.get() ? 1L : 0L);
        when(sponsoredMessageEventRepository.countBySponsoredMessageIdAndEventType(sponsoredMessageId, "CLICK")).thenReturn(0L);

        var first = monetizationService.recordImpression(viewerId, chatId, sponsoredMessageId);
        var second = monetizationService.recordImpression(viewerId, chatId, sponsoredMessageId);

        assertThat(first.spentUnits()).isEqualTo(7L);
        assertThat(first.impressionsCount()).isEqualTo(1L);
        assertThat(second.spentUnits()).isEqualTo(7L);
        assertThat(second.impressionsCount()).isEqualTo(1L);
        assertThat(first.earnedUnits()).isEqualTo(7L);
        assertThat(sponsoredMessage.getSpentUnits()).isEqualTo(7L);
        assertThat(sponsoredMessage.getEarnedUnits()).isEqualTo(7L);
        verify(paymentService).transferSponsoredRevenue(
                sponsorUserId,
                ownerUserId,
                sponsoredMessageId,
                7L,
                "Sponsored impression on channel %s".formatted(chatId),
                "Monetization revenue from sponsored impression"
        );
    }

    @Test
    void recordClickCompletesCampaignWhenBudgetExhausted() {
        UUID viewerId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID sponsoredMessageId = UUID.randomUUID();
        UUID sponsorUserId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, sponsorUserId);
        SponsoredMessageEntity sponsoredMessage = sponsoredMessage(
                sponsoredMessageId,
                chatId,
                sponsorUserId,
                UUID.randomUUID(),
                25,
                2,
                5
        );
        sponsoredMessage.setSpentUnits(20L);
        sponsoredMessage.setStatus("ACTIVE");
        sponsoredMessage.setPublishedAt(Instant.parse("2026-03-14T10:00:00Z"));

        when(chatService.getOwnedChat(viewerId, chatId)).thenReturn(channel);
        when(chatService.getChat(chatId)).thenReturn(channel);
        when(sponsoredMessageRepository.findById(sponsoredMessageId)).thenReturn(Optional.of(sponsoredMessage));
        when(sponsoredMessageRepository.save(any(SponsoredMessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sponsoredMessageEventRepository.existsBySponsoredMessageIdAndViewerUserIdAndEventType(
                sponsoredMessageId,
                viewerId,
                "CLICK"
        )).thenReturn(false);
        when(sponsoredMessageEventRepository.save(any(SponsoredMessageEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sponsoredMessageEventRepository.countBySponsoredMessageIdAndEventType(sponsoredMessageId, "IMPRESSION")).thenReturn(0L);
        when(sponsoredMessageEventRepository.countBySponsoredMessageIdAndEventType(sponsoredMessageId, "CLICK")).thenReturn(1L);

        var response = monetizationService.recordClick(viewerId, chatId, sponsoredMessageId);

        assertThat(response.spentUnits()).isEqualTo(25L);
        assertThat(response.remainingBudgetUnits()).isZero();
        assertThat(response.clicksCount()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("COMPLETED");
    }

    @Test
    void getChannelStatsAggregatesCountsAndCtr() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID sponsorUserId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        SponsoredMessageEntity draft = sponsoredMessage(UUID.randomUUID(), chatId, UUID.randomUUID(), requesterId, 100, 1, 5);
        draft.setStatus("DRAFT");
        SponsoredMessageEntity active = sponsoredMessage(UUID.randomUUID(), chatId, sponsorUserId, requesterId, 120, 2, 6);
        active.setStatus("ACTIVE");
        active.setSpentUnits(30L);
        active.setEarnedUnits(24L);
        active.setPublishedAt(Instant.parse("2026-03-14T11:00:00Z"));
        SponsoredMessageEntity paused = sponsoredMessage(UUID.randomUUID(), chatId, UUID.randomUUID(), requesterId, 80, 1, 4);
        paused.setStatus("PAUSED");
        paused.setSpentUnits(12L);
        paused.setEarnedUnits(10L);
        paused.setPublishedAt(Instant.parse("2026-03-14T12:00:00Z"));
        SponsoredMessageEntity completed = sponsoredMessage(UUID.randomUUID(), chatId, UUID.randomUUID(), requesterId, 50, 1, 5);
        completed.setStatus("COMPLETED");
        completed.setSpentUnits(50L);
        completed.setEarnedUnits(50L);
        completed.setPublishedAt(Instant.parse("2026-03-14T13:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(sponsoredMessageRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(completed, paused, active, draft));
        when(channelMonetizationPayoutRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId)).thenReturn(List.of());
        when(channelMonetizationPayoutRepository.countByChannelChatId(chatId)).thenReturn(0L);
        when(sponsoredMessageEventRepository.findAllBySponsoredMessageIdIn(any())).thenReturn(List.of(
                event(active.getId(), UUID.randomUUID(), "IMPRESSION", 2),
                event(active.getId(), UUID.randomUUID(), "IMPRESSION", 2),
                event(active.getId(), UUID.randomUUID(), "CLICK", 6),
                event(completed.getId(), UUID.randomUUID(), "CLICK", 5)
        ));

        var response = monetizationService.getChannelStats(requesterId, chatId);

        assertThat(response.channelChatId()).isEqualTo(chatId);
        assertThat(response.totalSponsoredMessages()).isEqualTo(4);
        assertThat(response.draftCount()).isEqualTo(1);
        assertThat(response.activeCount()).isEqualTo(1);
        assertThat(response.pausedCount()).isEqualTo(1);
        assertThat(response.completedCount()).isEqualTo(1);
        assertThat(response.canceledCount()).isZero();
        assertThat(response.publishedCount()).isEqualTo(3);
        assertThat(response.totalBudgetUnits()).isEqualTo(350L);
        assertThat(response.totalSpentUnits()).isEqualTo(92L);
        assertThat(response.totalEarnedUnits()).isEqualTo(84L);
        assertThat(response.totalSettledUnits()).isZero();
        assertThat(response.outstandingPayoutUnits()).isEqualTo(84L);
        assertThat(response.remainingBudgetUnits()).isEqualTo(258L);
        assertThat(response.impressionsCount()).isEqualTo(2L);
        assertThat(response.clicksCount()).isEqualTo(2L);
        assertThat(response.totalPayoutUnits()).isZero();
        assertThat(response.uniqueSponsorCount()).isEqualTo(4);
        assertThat(response.totalPayouts()).isZero();
        assertThat(response.clickThroughRatePercent()).isEqualTo(100.0d);
    }

    @Test
    void createSponsoredMessageRejectsExpiredActivityWindow() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user(requesterId, "Manager")));

        assertThatThrownBy(() -> monetizationService.createSponsoredMessage(
                requesterId,
                chatId,
                new CreateSponsoredMessageRequest(
                        null,
                        "Launch",
                        "Body",
                        null,
                        null,
                        100L,
                        1L,
                        5L,
                        Instant.now().minusSeconds(60)
                )
        )).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deliverSponsoredMessageReturnsCampaignAndChargesSingleImpression() {
        UUID viewerId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID sponsoredMessageId = UUID.randomUUID();
        UUID sponsorUserId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, ownerUserId);
        SponsoredMessageEntity sponsoredMessage = sponsoredMessage(
                sponsoredMessageId,
                chatId,
                sponsorUserId,
                ownerUserId,
                100,
                3,
                5
        );
        sponsoredMessage.setStatus("ACTIVE");
        sponsoredMessage.setPublishedAt(Instant.parse("2026-03-14T10:00:00Z"));

        when(chatService.getOwnedChat(viewerId, chatId)).thenReturn(channel);
        when(chatService.getChat(chatId)).thenReturn(channel);
        when(sponsoredMessageRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId)).thenReturn(List.of(sponsoredMessage));
        when(sponsoredMessageRepository.findById(sponsoredMessageId)).thenReturn(Optional.of(sponsoredMessage));
        when(sponsoredMessageRepository.save(any(SponsoredMessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sponsoredMessageEventRepository.existsBySponsoredMessageIdAndViewerUserIdAndEventType(
                sponsoredMessageId,
                viewerId,
                "IMPRESSION"
        )).thenReturn(false);
        when(sponsoredMessageEventRepository.save(any(SponsoredMessageEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sponsoredMessageEventRepository.countBySponsoredMessageIdAndEventType(sponsoredMessageId, "IMPRESSION")).thenReturn(1L);
        when(sponsoredMessageEventRepository.countBySponsoredMessageIdAndEventType(sponsoredMessageId, "CLICK")).thenReturn(0L);
        when(paymentService.hasAvailableBalance(sponsorUserId, 3L)).thenReturn(true);

        var response = monetizationService.deliverSponsoredMessage(viewerId, chatId);

        assertThat(response.sponsoredMessageId()).isEqualTo(sponsoredMessageId);
        assertThat(response.impressionRecorded()).isTrue();
        assertThat(response.remainingBudgetUnits()).isEqualTo(97L);
        verify(paymentService).transferSponsoredRevenue(
                sponsorUserId,
                ownerUserId,
                sponsoredMessageId,
                3L,
                "Sponsored impression on channel %s".formatted(chatId),
                "Monetization revenue from sponsored impression"
        );
    }

    @Test
    void recordImpressionCompletesCampaignWhenSponsorWalletCannotCoverEvent() {
        UUID viewerId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID sponsoredMessageId = UUID.randomUUID();
        UUID sponsorUserId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, ownerUserId);
        SponsoredMessageEntity sponsoredMessage = sponsoredMessage(
                sponsoredMessageId,
                chatId,
                sponsorUserId,
                ownerUserId,
                100,
                4,
                9
        );
        sponsoredMessage.setStatus("ACTIVE");
        sponsoredMessage.setPublishedAt(Instant.parse("2026-03-14T10:00:00Z"));

        when(chatService.getOwnedChat(viewerId, chatId)).thenReturn(channel);
        when(chatService.getChat(chatId)).thenReturn(channel);
        when(sponsoredMessageRepository.findById(sponsoredMessageId)).thenReturn(Optional.of(sponsoredMessage));
        when(sponsoredMessageRepository.save(any(SponsoredMessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sponsoredMessageEventRepository.existsBySponsoredMessageIdAndViewerUserIdAndEventType(
                sponsoredMessageId,
                viewerId,
                "IMPRESSION"
        )).thenReturn(false);
        when(sponsoredMessageEventRepository.countBySponsoredMessageIdAndEventType(sponsoredMessageId, "IMPRESSION")).thenReturn(0L);
        when(sponsoredMessageEventRepository.countBySponsoredMessageIdAndEventType(sponsoredMessageId, "CLICK")).thenReturn(0L);
        when(paymentService.hasAvailableBalance(sponsorUserId, 4L)).thenReturn(false);

        var response = monetizationService.recordImpression(viewerId, chatId, sponsoredMessageId);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.completedAt()).isNotNull();
        verify(paymentService).hasAvailableBalance(sponsorUserId, 4L);
    }

    @Test
    void runPayoutSettlesOutstandingRevenueAndCreatesAuditRecord() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, ownerUserId);
        SponsoredMessageEntity first = sponsoredMessage(UUID.randomUUID(), chatId, UUID.randomUUID(), ownerUserId, 100, 1, 5);
        first.setStatus("COMPLETED");
        first.setEarnedUnits(12L);
        first.setSettledUnits(5L);
        first.setCompletedAt(Instant.parse("2026-03-14T12:00:00Z"));
        SponsoredMessageEntity second = sponsoredMessage(UUID.randomUUID(), chatId, UUID.randomUUID(), ownerUserId, 80, 1, 5);
        second.setStatus("CANCELED");
        second.setEarnedUnits(9L);
        second.setSettledUnits(0L);
        second.setCanceledAt(Instant.parse("2026-03-14T12:10:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(chatService.getChat(chatId)).thenReturn(channel);
        when(sponsoredMessageRepository.lockReadyForPayoutByChannel(chatId, 100)).thenReturn(List.of(first, second));
        when(channelMonetizationPayoutRepository.save(any(ChannelMonetizationPayoutEntity.class))).thenAnswer(invocation -> {
            ChannelMonetizationPayoutEntity payout = invocation.getArgument(0);
            payout.setId(payoutId);
            payout.setCreatedAt(Instant.parse("2026-03-14T12:15:00Z"));
            return payout;
        });
        when(channelMonetizationPayoutItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sponsoredMessageRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = monetizationService.runPayout(requesterId, chatId, 100);

        assertThat(response.payoutId()).isEqualTo(payoutId);
        assertThat(response.channelChatId()).isEqualTo(chatId);
        assertThat(response.totalUnits()).isEqualTo(16L);
        assertThat(response.sponsoredMessageCount()).isEqualTo(2);
        assertThat(response.triggerMode()).isEqualTo("MANUAL");
        assertThat(response.items()).hasSize(2);
        assertThat(first.getSettledUnits()).isEqualTo(12L);
        assertThat(second.getSettledUnits()).isEqualTo(9L);
    }

    @Test
    void processReadyPayoutsGroupsCompletedMessagesByChannel() {
        UUID firstChatId = UUID.randomUUID();
        UUID secondChatId = UUID.randomUUID();
        ChatEntity firstChannel = channel(firstChatId, UUID.randomUUID());
        ChatEntity secondChannel = channel(secondChatId, UUID.randomUUID());
        SponsoredMessageEntity first = sponsoredMessage(UUID.randomUUID(), firstChatId, UUID.randomUUID(), UUID.randomUUID(), 100, 1, 5);
        first.setStatus("COMPLETED");
        first.setEarnedUnits(8L);
        first.setCompletedAt(Instant.parse("2026-03-14T12:00:00Z"));
        SponsoredMessageEntity second = sponsoredMessage(UUID.randomUUID(), secondChatId, UUID.randomUUID(), UUID.randomUUID(), 100, 1, 5);
        second.setStatus("CANCELED");
        second.setEarnedUnits(4L);
        second.setCanceledAt(Instant.parse("2026-03-14T12:05:00Z"));

        when(sponsoredMessageRepository.lockReadyForPayoutBatch(any(), eq(20))).thenReturn(List.of(first, second));
        when(chatService.getChat(firstChatId)).thenReturn(firstChannel);
        when(chatService.getChat(secondChatId)).thenReturn(secondChannel);
        when(channelMonetizationPayoutRepository.save(any(ChannelMonetizationPayoutEntity.class))).thenAnswer(invocation -> {
            ChannelMonetizationPayoutEntity payout = invocation.getArgument(0);
            payout.setId(UUID.randomUUID());
            payout.setCreatedAt(Instant.parse("2026-03-14T12:20:00Z"));
            return payout;
        });
        when(channelMonetizationPayoutItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sponsoredMessageRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int processed = monetizationService.processReadyPayouts(Instant.parse("2026-03-14T12:30:00Z"), 20);

        assertThat(processed).isEqualTo(2);
        assertThat(first.getSettledUnits()).isEqualTo(8L);
        assertThat(second.getSettledUnits()).isEqualTo(4L);
    }

    @Test
    void createWithdrawalReservesAvailablePayoutBalance() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, ownerUserId);
        ChannelMonetizationPayoutEntity payout = new ChannelMonetizationPayoutEntity();
        payout.setId(UUID.randomUUID());
        payout.setChannelChatId(chatId);
        payout.setRecipientUserId(ownerUserId);
        payout.setTotalUnits(25L);

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(chatService.getChat(chatId)).thenReturn(channel);
        when(channelMonetizationPayoutRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId)).thenReturn(List.of(payout));
        when(channelMonetizationWithdrawalRepository.findAllByChannelChatIdOrderByRequestedAtDesc(chatId)).thenReturn(List.of());
        when(channelMonetizationWithdrawalRepository.save(any(ChannelMonetizationWithdrawalEntity.class))).thenAnswer(invocation -> {
            ChannelMonetizationWithdrawalEntity withdrawal = invocation.getArgument(0);
            withdrawal.setId(UUID.randomUUID());
            withdrawal.setRequestedAt(Instant.parse("2026-03-14T12:30:00Z"));
            return withdrawal;
        });

        var response = monetizationService.createWithdrawal(
                requesterId,
                chatId,
                new CreateMonetizationWithdrawalRequest(12L, "BANK_CARD", "Visa **** 4242", "Weekly payout")
        );

        assertThat(response.amountUnits()).isEqualTo(12L);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.destinationType()).isEqualTo("BANK_CARD");
    }

    @Test
    void syncWithdrawalCompletesPendingRequestAndTriggersWalletDebit() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID withdrawalId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, ownerUserId);
        ChannelMonetizationWithdrawalEntity withdrawal = new ChannelMonetizationWithdrawalEntity();
        withdrawal.setId(withdrawalId);
        withdrawal.setChannelChatId(chatId);
        withdrawal.setRecipientUserId(ownerUserId);
        withdrawal.setRequestedByUserId(requesterId);
        withdrawal.setAmountUnits(10L);
        withdrawal.setCurrencyCode("XTR");
        withdrawal.setDestinationType("BANK_CARD");
        withdrawal.setDestinationLabel("Visa **** 4242");
        withdrawal.setStatus("PENDING");
        withdrawal.setRequestedAt(Instant.parse("2026-03-14T12:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationWithdrawalRepository.findByIdAndChannelChatId(withdrawalId, chatId)).thenReturn(Optional.of(withdrawal));
        when(paymentService.hasAvailableBalance(ownerUserId, 10L)).thenReturn(true);
        when(channelMonetizationWithdrawalRepository.save(any(ChannelMonetizationWithdrawalEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = monetizationService.syncWithdrawal(requesterId, chatId, withdrawalId);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.providerReference()).startsWith("wdr_");
        verify(paymentService).withdrawToExternal(
                ownerUserId,
                10L,
                "Monetization withdrawal for channel %s to %s".formatted(chatId, "Visa **** 4242")
        );
    }

    @Test
    void processPendingWithdrawalsMovesRequestsToProcessing() {
        UUID chatId = UUID.randomUUID();
        UUID withdrawalId = UUID.randomUUID();
        ChannelMonetizationWithdrawalEntity withdrawal = new ChannelMonetizationWithdrawalEntity();
        withdrawal.setId(withdrawalId);
        withdrawal.setChannelChatId(chatId);
        withdrawal.setRecipientUserId(UUID.randomUUID());
        withdrawal.setRequestedByUserId(UUID.randomUUID());
        withdrawal.setAmountUnits(11L);
        withdrawal.setStatus("PENDING");
        withdrawal.setRequestedAt(Instant.parse("2026-03-14T12:00:00Z"));

        when(channelMonetizationWithdrawalRepository.lockPendingBatch(any(), eq(20))).thenReturn(List.of(withdrawal));
        when(channelMonetizationWithdrawalRepository.save(any(ChannelMonetizationWithdrawalEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int processed = monetizationService.processPendingWithdrawals(Instant.parse("2026-03-14T12:10:00Z"), 20);

        assertThat(processed).isEqualTo(1);
        assertThat(withdrawal.getStatus()).isEqualTo("PROCESSING");
        assertThat(withdrawal.getProviderReference()).startsWith("wdr_");
    }

    @Test
    void runReconciliationCompletesProcessingWithdrawalAndStoresRun() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID withdrawalId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, ownerUserId);
        ChannelMonetizationWithdrawalEntity withdrawal = new ChannelMonetizationWithdrawalEntity();
        withdrawal.setId(withdrawalId);
        withdrawal.setChannelChatId(chatId);
        withdrawal.setRecipientUserId(ownerUserId);
        withdrawal.setRequestedByUserId(requesterId);
        withdrawal.setAmountUnits(10L);
        withdrawal.setCurrencyCode("XTR");
        withdrawal.setDestinationType("BANK_CARD");
        withdrawal.setDestinationLabel("Visa **** 4242");
        withdrawal.setStatus("PROCESSING");
        withdrawal.setProviderReference("wdr_demo");
        withdrawal.setProcessingAt(Instant.parse("2026-03-14T12:01:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationWithdrawalRepository.lockProcessingByChannel(chatId, 100)).thenReturn(List.of(withdrawal));
        when(channelMonetizationWithdrawalRepository.findByIdAndChannelChatId(withdrawalId, chatId)).thenReturn(Optional.of(withdrawal));
        when(paymentService.hasAvailableBalance(ownerUserId, 10L)).thenReturn(true);
        when(channelMonetizationWithdrawalRepository.save(any(ChannelMonetizationWithdrawalEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationReconciliationRunRepository.save(any(ChannelMonetizationReconciliationRunEntity.class))).thenAnswer(invocation -> {
            ChannelMonetizationReconciliationRunEntity run = invocation.getArgument(0);
            run.setId(runId);
            run.setCreatedAt(Instant.parse("2026-03-14T12:05:00Z"));
            return run;
        });

        var response = monetizationService.runReconciliation(requesterId, chatId, 100);

        assertThat(response.reconciliationRunId()).isEqualTo(runId);
        assertThat(response.completedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isZero();
        assertThat(withdrawal.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void getChannelReportAggregatesRevenuePayoutsAndWithdrawals() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, ownerUserId);
        SponsoredMessageEntity completed = sponsoredMessage(UUID.randomUUID(), chatId, UUID.randomUUID(), ownerUserId, 100, 1, 5);
        completed.setStatus("COMPLETED");
        completed.setEarnedUnits(20L);
        completed.setSettledUnits(12L);
        completed.setCompletedAt(Instant.parse("2026-03-14T10:00:00Z"));
        ChannelMonetizationPayoutEntity payout = new ChannelMonetizationPayoutEntity();
        payout.setId(UUID.randomUUID());
        payout.setChannelChatId(chatId);
        payout.setRecipientUserId(ownerUserId);
        payout.setTotalUnits(12L);
        payout.setCompletedAt(Instant.parse("2026-03-14T11:00:00Z"));
        ChannelMonetizationWithdrawalEntity pending = new ChannelMonetizationWithdrawalEntity();
        pending.setId(UUID.randomUUID());
        pending.setChannelChatId(chatId);
        pending.setRecipientUserId(ownerUserId);
        pending.setRequestedByUserId(requesterId);
        pending.setAmountUnits(3L);
        pending.setStatus("PENDING");
        pending.setRequestedAt(Instant.parse("2026-03-14T11:30:00Z"));
        ChannelMonetizationWithdrawalEntity completedWithdrawal = new ChannelMonetizationWithdrawalEntity();
        completedWithdrawal.setId(UUID.randomUUID());
        completedWithdrawal.setChannelChatId(chatId);
        completedWithdrawal.setRecipientUserId(ownerUserId);
        completedWithdrawal.setRequestedByUserId(requesterId);
        completedWithdrawal.setAmountUnits(4L);
        completedWithdrawal.setStatus("COMPLETED");
        completedWithdrawal.setCompletedAt(Instant.parse("2026-03-14T12:00:00Z"));
        ChannelMonetizationReconciliationRunEntity run = new ChannelMonetizationReconciliationRunEntity();
        run.setId(UUID.randomUUID());
        run.setChannelChatId(chatId);
        run.setCreatedAt(Instant.parse("2026-03-14T12:10:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(sponsoredMessageRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId)).thenReturn(List.of(completed));
        when(channelMonetizationPayoutRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId)).thenReturn(List.of(payout));
        when(channelMonetizationWithdrawalRepository.findAllByChannelChatIdOrderByRequestedAtDesc(chatId))
                .thenReturn(List.of(completedWithdrawal, pending));
        when(channelMonetizationReconciliationRunRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId)).thenReturn(List.of(run));

        var response = monetizationService.getChannelReport(requesterId, chatId);

        assertThat(response.totalRevenueUnits()).isEqualTo(20L);
        assertThat(response.totalSettledUnits()).isEqualTo(12L);
        assertThat(response.outstandingPayoutUnits()).isEqualTo(8L);
        assertThat(response.availableWithdrawalUnits()).isEqualTo(5L);
        assertThat(response.totalWithdrawnUnits()).isEqualTo(4L);
        assertThat(response.pendingWithdrawalUnits()).isEqualTo(3L);
        assertThat(response.completedWithdrawalCount()).isEqualTo(1);
        assertThat(response.pendingWithdrawalCount()).isEqualTo(1);
        assertThat(response.lastReconciliationAt()).isEqualTo(Instant.parse("2026-03-14T12:10:00Z"));
    }

    @Test
    void exportPayoutsBuildsCsvPayload() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, ownerUserId);
        ChannelMonetizationPayoutEntity payout = new ChannelMonetizationPayoutEntity();
        payout.setId(payoutId);
        payout.setChannelChatId(chatId);
        payout.setRecipientUserId(ownerUserId);
        payout.setTriggerMode("MANUAL");
        payout.setStatus("COMPLETED");
        payout.setTotalUnits(15L);
        payout.setSponsoredMessageCount(2);
        payout.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
        payout.setCompletedAt(Instant.parse("2026-03-14T12:05:00Z"));
        ChannelMonetizationPayoutItemEntity item = new ChannelMonetizationPayoutItemEntity();
        item.setId(UUID.randomUUID());
        item.setPayoutId(payoutId);
        item.setSponsoredMessageId(UUID.randomUUID());
        item.setSettledUnits(15L);
        item.setCreatedAt(Instant.parse("2026-03-14T12:05:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationPayoutRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId)).thenReturn(List.of(payout));
        when(channelMonetizationPayoutItemRepository.findAllByPayoutIdInOrderByCreatedAtAsc(List.of(payoutId))).thenReturn(List.of(item));

        var response = monetizationService.exportPayouts(requesterId, chatId);

        assertThat(response.format()).isEqualTo("CSV");
        assertThat(response.rowCount()).isEqualTo(1);
        assertThat(response.totalUnits()).isEqualTo(15L);
        assertThat(response.content()).contains("payout_id,channel_chat_id");
        assertThat(response.content()).contains(payoutId.toString());
    }

    @Test
    void providerCallbackCompletesWithdrawalAndStoresAuditRecord() {
        UUID withdrawalId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        ChannelMonetizationWithdrawalEntity withdrawal = new ChannelMonetizationWithdrawalEntity();
        withdrawal.setId(withdrawalId);
        withdrawal.setChannelChatId(chatId);
        withdrawal.setRecipientUserId(ownerUserId);
        withdrawal.setRequestedByUserId(UUID.randomUUID());
        withdrawal.setAmountUnits(9L);
        withdrawal.setDestinationType("BANK_CARD");
        withdrawal.setDestinationLabel("Visa **** 4242");
        withdrawal.setStatus("PROCESSING");
        withdrawal.setProviderReference("prov_demo");
        withdrawal.setProcessingAt(Instant.parse("2026-03-14T12:01:00Z"));

        when(channelMonetizationWithdrawalRepository.findById(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(paymentService.hasAvailableBalance(ownerUserId, 9L)).thenReturn(true);
        when(channelMonetizationWithdrawalRepository.save(any(ChannelMonetizationWithdrawalEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationWithdrawalCallbackRepository.save(any(ChannelMonetizationWithdrawalCallbackEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationWithdrawalCallbackEntity callback = invocation.getArgument(0);
                    callback.setId(UUID.randomUUID());
                    callback.setReceivedAt(Instant.parse("2026-03-14T12:03:00Z"));
                    return callback;
                });

        var response = monetizationService.applyProviderCallback(new MonetizationWithdrawalProviderCallbackRequest(
                withdrawalId,
                "prov_demo",
                "completed",
                "STATUS_SYNC",
                null,
                Map.of("event", "completed")
        ));

        assertThat(response.withdrawalId()).isEqualTo(withdrawalId);
        assertThat(response.providerStatus()).isEqualTo("COMPLETED");
        assertThat(response.applied()).isTrue();
        assertThat(response.appliedWithdrawalStatus()).isEqualTo("COMPLETED");
        assertThat(response.payloadJson()).contains("\"event\":\"completed\"");
        verify(paymentService).withdrawToExternal(
                ownerUserId,
                9L,
                "Monetization withdrawal for channel %s to %s".formatted(chatId, "Visa **** 4242")
        );
    }

    @Test
    void exportWithdrawalsBuildsArtifactWithChecksum() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, ownerUserId);
        ChannelMonetizationWithdrawalEntity withdrawal = new ChannelMonetizationWithdrawalEntity();
        withdrawal.setId(UUID.randomUUID());
        withdrawal.setChannelChatId(chatId);
        withdrawal.setRecipientUserId(ownerUserId);
        withdrawal.setRequestedByUserId(requesterId);
        withdrawal.setAmountUnits(15L);
        withdrawal.setCurrencyCode("XTR");
        withdrawal.setDestinationType("BANK_CARD");
        withdrawal.setDestinationLabel("Visa **** 4242");
        withdrawal.setStatus("FAILED");
        withdrawal.setProviderReference("prov_demo");
        withdrawal.setProviderStatus("FAILED");
        withdrawal.setFailureReason("Provider rejected destination");
        withdrawal.setRequestedAt(Instant.parse("2026-03-14T12:00:00Z"));
        withdrawal.setProviderUpdatedAt(Instant.parse("2026-03-14T12:05:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationWithdrawalRepository.findAllByChannelChatIdOrderByRequestedAtDesc(chatId))
                .thenReturn(List.of(withdrawal));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(UUID.randomUUID());
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:06:00Z"));
                    return artifact;
                });

        var response = monetizationService.exportWithdrawals(requesterId, chatId);

        assertThat(response.artifactType()).isEqualTo("WITHDRAWALS_EXPORT");
        assertThat(response.format()).isEqualTo("CSV");
        assertThat(response.rowCount()).isEqualTo(1);
        assertThat(response.totalUnits()).isEqualTo(15L);
        assertThat(response.checksum()).hasSize(64);
        assertThat(response.content()).contains("withdrawal_id,channel_chat_id");
        assertThat(response.content()).contains("provider_status");
        assertThat(response.content()).contains("Provider rejected destination");
    }

    @Test
    void exportReportPersistsArtifactAndSupportsArtifactLookup() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, ownerUserId);
        SponsoredMessageEntity completed = sponsoredMessage(UUID.randomUUID(), chatId, UUID.randomUUID(), ownerUserId, 100, 1, 5);
        completed.setStatus("COMPLETED");
        completed.setEarnedUnits(20L);
        completed.setSettledUnits(8L);
        completed.setCompletedAt(Instant.parse("2026-03-14T10:00:00Z"));
        final ChannelMonetizationExportArtifactEntity[] storedArtifact = new ChannelMonetizationExportArtifactEntity[1];

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(sponsoredMessageRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId)).thenReturn(List.of(completed));
        when(channelMonetizationPayoutRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId)).thenReturn(List.of());
        when(channelMonetizationWithdrawalRepository.findAllByChannelChatIdOrderByRequestedAtDesc(chatId)).thenReturn(List.of());
        when(channelMonetizationReconciliationRunRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId)).thenReturn(List.of());
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:20:00Z"));
                    storedArtifact[0] = artifact;
                    return artifact;
                });

        var exported = monetizationService.exportReport(requesterId, chatId);

        when(channelMonetizationExportArtifactRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(storedArtifact[0]));
        when(channelMonetizationExportArtifactRepository.findByIdAndChannelChatId(artifactId, chatId))
                .thenReturn(Optional.of(storedArtifact[0]));

        var artifacts = monetizationService.listArtifacts(requesterId, chatId);
        var artifact = monetizationService.getArtifact(requesterId, chatId, artifactId);

        assertThat(exported.artifactId()).isEqualTo(artifactId);
        assertThat(exported.artifactType()).isEqualTo("REPORT_EXPORT");
        assertThat(exported.content()).contains("\"totalRevenueUnits\":20");
        assertThat(artifacts).hasSize(1);
        assertThat(artifacts.get(0).content()).isNull();
        assertThat(artifact.content()).contains("\"outstandingPayoutUnits\":12");
        assertThat(artifact.checksum()).hasSize(64);
    }

    @Test
    void reconcileProviderStatusesBuildsSyncRunArtifactAndPublishesSummary() {
        UUID chatId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID withdrawalId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID syncRunId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID publishedMessageId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, ownerUserId);
        ChannelMonetizationWithdrawalEntity withdrawal = new ChannelMonetizationWithdrawalEntity();
        withdrawal.setId(withdrawalId);
        withdrawal.setChannelChatId(chatId);
        withdrawal.setRecipientUserId(ownerUserId);
        withdrawal.setRequestedByUserId(UUID.randomUUID());
        withdrawal.setAmountUnits(14L);
        withdrawal.setDestinationType("BANK_CARD");
        withdrawal.setDestinationLabel("Visa **** 4242");
        withdrawal.setStatus("PROCESSING");
        withdrawal.setProviderReference("prov_demo");
        withdrawal.setProcessingAt(Instant.parse("2026-03-14T12:01:00Z"));
        final ChannelMonetizationExportArtifactEntity[] storedArtifact = new ChannelMonetizationExportArtifactEntity[1];

        when(chatService.getChat(chatId)).thenReturn(channel);
        when(channelMonetizationWithdrawalRepository.findById(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(paymentService.hasAvailableBalance(ownerUserId, 14L)).thenReturn(true);
        when(channelMonetizationWithdrawalRepository.save(any(ChannelMonetizationWithdrawalEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationWithdrawalCallbackRepository.save(any(ChannelMonetizationWithdrawalCallbackEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationWithdrawalCallbackEntity callback = invocation.getArgument(0);
                    callback.setId(UUID.randomUUID());
                    return callback;
                });
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:15:00Z"));
                    storedArtifact[0] = artifact;
                    return artifact;
                });
        when(channelMonetizationExportArtifactRepository.findById(artifactId))
                .thenAnswer(invocation -> Optional.ofNullable(storedArtifact[0]));
        when(channelMonetizationProviderSyncRunRepository.save(any(ChannelMonetizationProviderSyncRunEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationProviderSyncRunEntity run = invocation.getArgument(0);
                    run.setId(syncRunId);
                    run.setCreatedAt(Instant.parse("2026-03-14T12:16:00Z"));
                    return run;
                });
        when(messageService.sendMessage(eq(ownerUserId), any(SendMessageRequest.class))).thenReturn(message(publishedMessageId));
        when(channelMonetizationArtifactPublicationRepository.save(any(ChannelMonetizationArtifactPublicationEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactPublicationEntity publication = invocation.getArgument(0);
                    publication.setId(publicationId);
                    publication.setPublishedAt(Instant.parse("2026-03-14T12:17:00Z"));
                    return publication;
                });

        var response = monetizationService.reconcileProviderStatuses(
                chatId,
                new MonetizationProviderReconciliationRequest(
                        List.of(
                                new MonetizationProviderStatusUpdateRequest(
                                        withdrawalId,
                                        "prov_demo",
                                        "completed",
                                        "STATUS_SYNC",
                                        null,
                                        Map.of("providerEvent", "done")
                                ),
                                new MonetizationProviderStatusUpdateRequest(
                                        UUID.randomUUID(),
                                        "prov_missing",
                                        "failed",
                                        "STATUS_SYNC",
                                        "missing withdrawal",
                                        Map.of()
                                )
                        ),
                        targetChatId,
                        "nightly sync"
                )
        );

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(ownerUserId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(targetChatId);
        assertThat(requestCaptor.getValue().text()).contains("Monetization artifact: PROVIDER_RECONCILIATION_EXPORT");
        assertThat(requestCaptor.getValue().text()).contains("nightly sync");
        assertThat(response.providerSyncRunId()).isEqualTo(syncRunId);
        assertThat(response.payloadSize()).isEqualTo(2);
        assertThat(response.appliedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.ignoredCount()).isZero();
        assertThat(response.artifactId()).isEqualTo(artifactId);
        assertThat(withdrawal.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void publishArtifactSendsSummaryMessageAndStoresPublication() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID publishedMessageId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, ownerUserId);
        ChatEntity targetChat = channel(targetChatId, requesterId);
        ChannelMonetizationExportArtifactEntity artifact = new ChannelMonetizationExportArtifactEntity();
        artifact.setId(artifactId);
        artifact.setChannelChatId(chatId);
        artifact.setGeneratedByUserId(requesterId);
        artifact.setArtifactType("WITHDRAWALS_EXPORT");
        artifact.setFormat("CSV");
        artifact.setFileName("channel.csv");
        artifact.setRowCount(3);
        artifact.setTotalUnits(21L);
        artifact.setChecksum("abc123");
        artifact.setContent("csv");
        artifact.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationExportArtifactRepository.findByIdAndChannelChatId(artifactId, chatId)).thenReturn(Optional.of(artifact));
        when(chatService.getOwnedChat(requesterId, targetChatId)).thenReturn(targetChat);
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(publishedMessageId));
        when(channelMonetizationArtifactPublicationRepository.save(any(ChannelMonetizationArtifactPublicationEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactPublicationEntity publication = invocation.getArgument(0);
                    publication.setId(publicationId);
                    publication.setPublishedAt(Instant.parse("2026-03-14T12:10:00Z"));
                    return publication;
                });

        var response = monetizationService.publishArtifact(
                requesterId,
                chatId,
                artifactId,
                new PublishMonetizationArtifactRequest(targetChatId, "ops handoff")
        );

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(requesterId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(targetChatId);
        assertThat(requestCaptor.getValue().text()).contains("File: channel.csv");
        assertThat(requestCaptor.getValue().text()).contains("ops handoff");
        assertThat(response.publicationId()).isEqualTo(publicationId);
        assertThat(response.publishedMessageId()).isEqualTo(publishedMessageId);
        assertThat(response.deliveryMode()).isEqualTo("CHAT_MESSAGE");
        assertThat(response.note()).isEqualTo("ops handoff");
    }

    @Test
    void createPauseAndResumeArtifactSubscriptionManagesLifecycle() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChatEntity targetChat = channel(targetChatId, requesterId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(targetChatId);
        subscription.setCreatedByUserId(requesterId);
        subscription.setArtifactType("REPORT_EXPORT");
        subscription.setDeliveryMode("CHAT_MESSAGE");
        subscription.setStatus("ACTIVE");
        subscription.setMinIntervalMinutes(30);
        subscription.setAutoGenerate(true);
        subscription.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
        subscription.setUpdatedAt(Instant.parse("2026-03-14T12:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, targetChatId)).thenReturn(targetChat);
        when(channelMonetizationArtifactSubscriptionRepository.save(any(ChannelMonetizationArtifactSubscriptionEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactSubscriptionEntity saved = invocation.getArgument(0);
                    if (saved.getId() == null) {
                        saved.setId(subscriptionId);
                    }
                    if (saved.getCreatedAt() == null) {
                        saved.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
                    }
                    if (saved.getUpdatedAt() == null) {
                        saved.setUpdatedAt(saved.getCreatedAt());
                    }
                    return saved;
                });
        when(channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(subscriptionId, chatId))
                .thenReturn(Optional.of(subscription));

        var created = monetizationService.createArtifactSubscription(
                requesterId,
                chatId,
                new CreateMonetizationArtifactSubscriptionRequest(targetChatId, "report_export", 30, 120, true, "scheduled")
        );
        var paused = monetizationService.pauseArtifactSubscription(requesterId, chatId, subscriptionId);
        var resumed = monetizationService.resumeArtifactSubscription(requesterId, chatId, subscriptionId);

        assertThat(created.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(created.artifactType()).isEqualTo("REPORT_EXPORT");
        assertThat(created.autoGenerate()).isTrue();
        assertThat(created.alertSuppressionMinutes()).isEqualTo(120);
        assertThat(paused.status()).isEqualTo("PAUSED");
        assertThat(resumed.status()).isEqualTo("ACTIVE");
    }

    @Test
    void processArtifactSubscriptionsAutoGeneratesReportAndPublishesLatestArtifact() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID publishedMessageId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(targetChatId);
        subscription.setCreatedByUserId(requesterId);
        subscription.setArtifactType("REPORT_EXPORT");
        subscription.setDeliveryMode("CHAT_MESSAGE");
        subscription.setStatus("ACTIVE");
        subscription.setMinIntervalMinutes(30);
        subscription.setAutoGenerate(true);
        subscription.setNote("auto delivery");
        subscription.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));
        subscription.setUpdatedAt(Instant.parse("2026-03-14T11:00:00Z"));
        final ChannelMonetizationExportArtifactEntity[] storedArtifact = new ChannelMonetizationExportArtifactEntity[1];
        SponsoredMessageEntity completed = sponsoredMessage(UUID.randomUUID(), chatId, UUID.randomUUID(), requesterId, 100, 1, 5);
        completed.setStatus("COMPLETED");
        completed.setEarnedUnits(11L);
        completed.setSettledUnits(4L);
        completed.setCompletedAt(Instant.parse("2026-03-14T10:00:00Z"));

        when(channelMonetizationArtifactSubscriptionRepository.lockActiveBatch(any(), eq(20))).thenReturn(List.of(subscription));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(sponsoredMessageRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId)).thenReturn(List.of(completed));
        when(channelMonetizationPayoutRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId)).thenReturn(List.of());
        when(channelMonetizationWithdrawalRepository.findAllByChannelChatIdOrderByRequestedAtDesc(chatId)).thenReturn(List.of());
        when(channelMonetizationReconciliationRunRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId)).thenReturn(List.of());
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
                    storedArtifact[0] = artifact;
                    return artifact;
                });
        when(channelMonetizationExportArtifactRepository.findById(artifactId))
                .thenAnswer(invocation -> Optional.ofNullable(storedArtifact[0]));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(publishedMessageId));
        when(channelMonetizationArtifactPublicationRepository.save(any(ChannelMonetizationArtifactPublicationEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactPublicationEntity publication = invocation.getArgument(0);
                    publication.setId(publicationId);
                    publication.setPublishedAt(Instant.parse("2026-03-14T12:01:00Z"));
                    return publication;
                });
        when(channelMonetizationArtifactSubscriptionRepository.save(any(ChannelMonetizationArtifactSubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int processed = monetizationService.processArtifactSubscriptions(Instant.parse("2026-03-14T12:00:00Z"), 20);

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(requesterId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(targetChatId);
        assertThat(requestCaptor.getValue().text()).contains("Monetization artifact: REPORT_EXPORT");
        assertThat(requestCaptor.getValue().text()).contains("auto delivery");
        assertThat(processed).isEqualTo(1);
        assertThat(subscription.getLastDeliveredArtifactId()).isEqualTo(artifactId);
        assertThat(subscription.getLastDeliveredAt()).isNotNull();
        assertThat(subscription.getLastGeneratedAt()).isNotNull();
    }

    @Test
    void processArtifactSubscriptionsAutoGeneratesIssueSummaryAndPublishesLatestArtifact() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID publishedMessageId = UUID.randomUUID();
        Instant now = Instant.now();
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(targetChatId);
        subscription.setCreatedByUserId(requesterId);
        subscription.setArtifactType("ALERT_OWNER_REMINDER_DIGEST_ISSUES_SUMMARY_EXPORT");
        subscription.setDeliveryMode("CHAT_MESSAGE");
        subscription.setStatus("ACTIVE");
        subscription.setMinIntervalMinutes(30);
        subscription.setAutoGenerate(true);
        subscription.setNote("issue summary auto delivery");
        subscription.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));
        subscription.setUpdatedAt(Instant.parse("2026-03-14T11:00:00Z"));
        ChannelMonetizationOwnerReminderDigestSubscriptionEntity issue =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        issue.setId(UUID.randomUUID());
        issue.setChannelChatId(chatId);
        issue.setOwnerUserId(ownerUserId);
        issue.setFailureState("BACKOFF");
        issue.setStatus("ACTIVE");
        issue.setLastFailureAt(now.minusSeconds(60));
        issue.setNextRetryAt(now.minusSeconds(5));
        issue.setUpdatedAt(now.minusSeconds(60));
        final ChannelMonetizationExportArtifactEntity[] storedArtifact = new ChannelMonetizationExportArtifactEntity[1];

        when(channelMonetizationArtifactSubscriptionRepository.lockActiveBatch(any(), eq(20))).thenReturn(List.of(subscription));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel(chatId, requesterId));
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.findAllByChannelChatIdOrderByUpdatedAtDesc(chatId))
                .thenReturn(List.of(issue));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
                    storedArtifact[0] = artifact;
                    return artifact;
                });
        when(channelMonetizationExportArtifactRepository.findById(artifactId))
                .thenAnswer(invocation -> Optional.ofNullable(storedArtifact[0]));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(publishedMessageId));
        when(channelMonetizationArtifactPublicationRepository.save(any(ChannelMonetizationArtifactPublicationEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactPublicationEntity publication = invocation.getArgument(0);
                    publication.setId(publicationId);
                    publication.setPublishedAt(Instant.parse("2026-03-14T12:01:00Z"));
                    return publication;
                });
        when(channelMonetizationArtifactSubscriptionRepository.save(any(ChannelMonetizationArtifactSubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int processed = monetizationService.processArtifactSubscriptions(Instant.parse("2026-03-14T12:00:00Z"), 20);

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(requesterId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(targetChatId);
        assertThat(requestCaptor.getValue().text()).contains("ALERT_OWNER_REMINDER_DIGEST_ISSUES_SUMMARY_EXPORT");
        assertThat(requestCaptor.getValue().text()).contains("issue summary auto delivery");
        assertThat(processed).isEqualTo(1);
        assertThat(subscription.getLastDeliveredArtifactId()).isEqualTo(artifactId);
        assertThat(subscription.getLastDeliveredAt()).isNotNull();
        assertThat(subscription.getLastGeneratedAt()).isNotNull();
    }

    @Test
    void processArtifactSubscriptionsRecordsFailureAndCreatesAlertAfterThreshold() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        UUID alertMessageId = UUID.randomUUID();
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(targetChatId);
        subscription.setCreatedByUserId(requesterId);
        subscription.setArtifactType("BROKEN_EXPORT");
        subscription.setDeliveryMode("CHAT_MESSAGE");
        subscription.setStatus("ACTIVE");
        subscription.setMinIntervalMinutes(30);
        subscription.setAutoGenerate(true);
        subscription.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));
        subscription.setUpdatedAt(Instant.parse("2026-03-14T11:00:00Z"));
        subscription.setConsecutiveFailureCount(2);

        when(channelMonetizationArtifactSubscriptionRepository.lockActiveBatch(any(), eq(20))).thenReturn(List.of(subscription));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(alertMessageId));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findFirstBySubscriptionIdOrderByCreatedAtDesc(subscriptionId))
                .thenReturn(Optional.empty());
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactSubscriptionAlertEntity alert = invocation.getArgument(0);
                    if (alert.getId() == null) {
                        alert.setId(alertId);
                    }
                    if (alert.getCreatedAt() == null) {
                        alert.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
                    }
                    return alert;
                });
        when(channelMonetizationArtifactSubscriptionFailureRepository.save(any(ChannelMonetizationArtifactSubscriptionFailureEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactSubscriptionRepository.save(any(ChannelMonetizationArtifactSubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int processed = monetizationService.processArtifactSubscriptions(Instant.parse("2026-03-14T12:00:00Z"), 20);

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(requesterId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(chatId);
        assertThat(requestCaptor.getValue().text()).contains("Monetization subscription alert");
        assertThat(requestCaptor.getValue().text()).contains("Consecutive failures: 3");
        assertThat(processed).isZero();
        assertThat(subscription.getConsecutiveFailureCount()).isEqualTo(3);
        assertThat(subscription.getLastFailureReason()).isEqualTo("Unsupported artifact type");
        assertThat(subscription.getEscalationStatus()).isEqualTo("OPEN");
    }

    @Test
    void updateAlertPolicyPersistsConfiguredThresholdsAndTargets() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID alertTargetChatId = UUID.randomUUID();
        UUID reminderTargetChatId = UUID.randomUUID();
        UUID personalReminderTargetChatId = UUID.randomUUID();
        UUID breachTargetChatId = UUID.randomUUID();
        UUID defaultOwnerUserId = UUID.randomUUID();
        UUID triageFallbackOwnerUserId = UUID.randomUUID();
        UUID triageTargetChatId = UUID.randomUUID();
        UUID triageEscalationTargetChatId = UUID.randomUUID();
        UUID digestTargetChatId = UUID.randomUUID();
        UUID personalReminderDigestTargetChatId = UUID.randomUUID();
        String claimNextStrategy = "TRIAGE_FIRST";
        ChatEntity channel = channel(chatId, requesterId);

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, alertTargetChatId)).thenReturn(channel(alertTargetChatId, requesterId));
        when(chatService.getOwnedChat(requesterId, reminderTargetChatId)).thenReturn(channel(reminderTargetChatId, requesterId));
        when(chatService.getOwnedChat(requesterId, personalReminderTargetChatId))
                .thenReturn(channel(personalReminderTargetChatId, requesterId));
        when(chatService.getOwnedChat(requesterId, breachTargetChatId)).thenReturn(channel(breachTargetChatId, requesterId));
        when(chatService.getOwnedChat(requesterId, triageTargetChatId)).thenReturn(channel(triageTargetChatId, requesterId));
        when(chatService.getOwnedChat(requesterId, triageEscalationTargetChatId))
                .thenReturn(channel(triageEscalationTargetChatId, requesterId));
        when(chatService.getOwnedChat(requesterId, digestTargetChatId)).thenReturn(channel(digestTargetChatId, requesterId));
        when(chatService.getOwnedChat(requesterId, personalReminderDigestTargetChatId))
                .thenReturn(channel(personalReminderDigestTargetChatId, requesterId));
        when(userRepository.findById(defaultOwnerUserId)).thenReturn(Optional.of(user(defaultOwnerUserId, "Owner")));
        when(userRepository.findById(triageFallbackOwnerUserId)).thenReturn(Optional.of(user(triageFallbackOwnerUserId, "Triage Owner")));
        when(channelMonetizationAlertPolicyRepository.findById(chatId)).thenReturn(Optional.empty());
        when(channelMonetizationAlertPolicyRepository.save(any(ChannelMonetizationAlertPolicyEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationAlertPolicyEntity policy = invocation.getArgument(0);
                    policy.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
                    policy.setUpdatedAt(Instant.parse("2026-03-14T12:01:00Z"));
                    return policy;
                });

        var response = monetizationService.updateAlertPolicy(
                requesterId,
                chatId,
                new UpdateMonetizationAlertPolicyRequest(
                        2,
                        4,
                        90,
                        30,
                        180,
                        45,
                        60,
                        240,
                        10,
                        40,
                        12,
                        20,
                        25,
                        75,
                        false,
                        true,
                        true,
                        claimNextStrategy,
                        true,
                        alertTargetChatId,
                        reminderTargetChatId,
                        personalReminderTargetChatId,
                        breachTargetChatId,
                        defaultOwnerUserId,
                        triageFallbackOwnerUserId,
                        triageTargetChatId,
                        triageEscalationTargetChatId,
                        digestTargetChatId,
                        personalReminderDigestTargetChatId
                )
        );

        assertThat(response.channelChatId()).isEqualTo(chatId);
        assertThat(response.alertThreshold()).isEqualTo(2);
        assertThat(response.highSeverityThreshold()).isEqualTo(4);
        assertThat(response.alertSuppressionMinutes()).isEqualTo(90);
        assertThat(response.acknowledgeSlaMinutes()).isEqualTo(30);
        assertThat(response.resolveSlaMinutes()).isEqualTo(180);
        assertThat(response.reminderIntervalMinutes()).isEqualTo(45);
        assertThat(response.severityUpgradeAfterMinutes()).isEqualTo(60);
        assertThat(response.breachEscalationAfterMinutes()).isEqualTo(240);
        assertThat(response.highSeverityAcknowledgeSlaMinutes()).isEqualTo(10);
        assertThat(response.highSeverityResolveSlaMinutes()).isEqualTo(40);
        assertThat(response.highSeverityReminderIntervalMinutes()).isEqualTo(12);
        assertThat(response.triageDelayMinutes()).isEqualTo(20);
        assertThat(response.triageReminderIntervalMinutes()).isEqualTo(25);
        assertThat(response.triageEscalationAfterMinutes()).isEqualTo(75);
        assertThat(response.autoDigestEnabled()).isFalse();
        assertThat(response.autoTriageEnabled()).isTrue();
        assertThat(response.triageAutoAssignEnabled()).isTrue();
        assertThat(response.claimNextStrategy()).isEqualTo(claimNextStrategy);
        assertThat(response.claimNextTriageOnlyDefault()).isTrue();
        assertThat(response.alertTargetChatId()).isEqualTo(alertTargetChatId);
        assertThat(response.reminderTargetChatId()).isEqualTo(reminderTargetChatId);
        assertThat(response.personalReminderTargetChatId()).isEqualTo(personalReminderTargetChatId);
        assertThat(response.breachTargetChatId()).isEqualTo(breachTargetChatId);
        assertThat(response.defaultOwnerUserId()).isEqualTo(defaultOwnerUserId);
        assertThat(response.triageFallbackOwnerUserId()).isEqualTo(triageFallbackOwnerUserId);
        assertThat(response.triageTargetChatId()).isEqualTo(triageTargetChatId);
        assertThat(response.triageEscalationTargetChatId()).isEqualTo(triageEscalationTargetChatId);
        assertThat(response.digestTargetChatId()).isEqualTo(digestTargetChatId);
        assertThat(response.personalReminderDigestTargetChatId()).isEqualTo(personalReminderDigestTargetChatId);
    }

    @Test
    void processArtifactSubscriptionsAutoAssignsDefaultOwnerForHighSeverityAlerts() {
        UUID requesterId = UUID.randomUUID();
        UUID defaultOwnerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(targetChatId);
        subscription.setCreatedByUserId(requesterId);
        subscription.setArtifactType("BROKEN_EXPORT");
        subscription.setDeliveryMode("CHAT_MESSAGE");
        subscription.setStatus("ACTIVE");
        subscription.setMinIntervalMinutes(30);
        subscription.setAutoGenerate(true);
        subscription.setConsecutiveFailureCount(1);
        subscription.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));
        ChannelMonetizationAlertPolicyEntity policy = new ChannelMonetizationAlertPolicyEntity();
        policy.setChannelChatId(chatId);
        policy.setAlertThreshold(2);
        policy.setHighSeverityThreshold(2);
        policy.setHighSeverityAcknowledgeSlaMinutes(5);
        policy.setHighSeverityResolveSlaMinutes(25);
        policy.setHighSeverityReminderIntervalMinutes(10);
        policy.setDefaultOwnerUserId(defaultOwnerUserId);

        when(channelMonetizationArtifactSubscriptionRepository.lockActiveBatch(any(), eq(20))).thenReturn(List.of(subscription));
        when(channelMonetizationAlertPolicyRepository.findById(chatId)).thenReturn(Optional.of(policy));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findFirstBySubscriptionIdOrderByCreatedAtDesc(subscriptionId))
                .thenReturn(Optional.empty());
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactSubscriptionAlertEntity alert = invocation.getArgument(0);
                    if (alert.getId() == null) {
                        alert.setId(alertId);
                    }
                    if (alert.getCreatedAt() == null) {
                        alert.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
                    }
                    return alert;
                });
        when(channelMonetizationArtifactSubscriptionFailureRepository.save(any(ChannelMonetizationArtifactSubscriptionFailureEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactSubscriptionRepository.save(any(ChannelMonetizationArtifactSubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Instant before = Instant.now();
        int processed = monetizationService.processArtifactSubscriptions(Instant.parse("2026-03-14T12:00:00Z"), 20);
        Instant after = Instant.now();

        assertThat(processed).isZero();
        assertThat(subscription.getEscalationStatus()).isEqualTo("OPEN");
        verify(channelMonetizationArtifactSubscriptionAlertRepository).save(argThat(alert ->
                defaultOwnerUserId.equals(alert.getOwnerUserId())
                        && "HIGH".equals(alert.getSeverity())
                        && alert.getAssignedAt() != null
                        && alert.getAcknowledgeByDueAt() != null
                        && alert.getResolveByDueAt() != null
                        && !alert.getAcknowledgeByDueAt().isBefore(before.plusSeconds(4 * 60L))
                        && !alert.getAcknowledgeByDueAt().isAfter(after.plusSeconds(6 * 60L))
                        && !alert.getResolveByDueAt().isBefore(before.plusSeconds(24 * 60L))
                        && !alert.getResolveByDueAt().isAfter(after.plusSeconds(26 * 60L))
        ));
        verify(channelMonetizationArtifactAlertAuditEventRepository, org.mockito.Mockito.atLeastOnce())
                .save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class));
    }

    @Test
    void resolveArtifactSubscriptionAlertMarksAlertResolved() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(UUID.randomUUID());
        subscription.setCreatedByUserId(requesterId);
        subscription.setArtifactType("REPORT_EXPORT");
        subscription.setStatus("ACTIVE");
        subscription.setEscalationStatus("OPEN");
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(subscription.getTargetChatId());
        alert.setSeverity("WARN");
        alert.setFailureCount(3);
        alert.setLastFailureReason("delivery failed");
        alert.setStatus("OPEN");
        alert.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(subscriptionId, chatId))
                .thenReturn(Optional.of(subscription));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findByIdAndSubscriptionId(alertId, subscriptionId))
                .thenReturn(Optional.of(alert));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactSubscriptionRepository.save(any(ChannelMonetizationArtifactSubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = monetizationService.resolveArtifactSubscriptionAlert(requesterId, chatId, subscriptionId, alertId);

        assertThat(response.alertId()).isEqualTo(alertId);
        assertThat(response.status()).isEqualTo("RESOLVED");
        assertThat(response.resolvedAt()).isNotNull();
        assertThat(subscription.getEscalationStatus()).isEqualTo("RESOLVED");
    }

    @Test
    void acknowledgeArtifactSubscriptionAlertMarksAlertAcknowledged() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(UUID.randomUUID());
        subscription.setCreatedByUserId(requesterId);
        subscription.setArtifactType("REPORT_EXPORT");
        subscription.setStatus("ACTIVE");
        subscription.setEscalationStatus("OPEN");
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(subscription.getTargetChatId());
        alert.setSeverity("WARN");
        alert.setFailureCount(3);
        alert.setStatus("OPEN");

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(subscriptionId, chatId))
                .thenReturn(Optional.of(subscription));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findByIdAndSubscriptionId(alertId, subscriptionId))
                .thenReturn(Optional.of(alert));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactSubscriptionRepository.save(any(ChannelMonetizationArtifactSubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = monetizationService.acknowledgeArtifactSubscriptionAlert(requesterId, chatId, subscriptionId, alertId);

        assertThat(response.status()).isEqualTo("ACKNOWLEDGED");
        assertThat(response.acknowledgedByUserId()).isEqualTo(requesterId);
        assertThat(response.acknowledgedAt()).isNotNull();
        assertThat(subscription.getEscalationStatus()).isEqualTo("ACKNOWLEDGED");
    }

    @Test
    void snoozeArtifactSubscriptionAlertMarksAlertSnoozed() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(UUID.randomUUID());
        subscription.setCreatedByUserId(requesterId);
        subscription.setArtifactType("REPORT_EXPORT");
        subscription.setStatus("ACTIVE");
        subscription.setEscalationStatus("OPEN");
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(subscription.getTargetChatId());
        alert.setSeverity("WARN");
        alert.setFailureCount(3);
        alert.setStatus("OPEN");
        ChannelMonetizationAlertPolicyEntity policy = new ChannelMonetizationAlertPolicyEntity();
        policy.setChannelChatId(chatId);
        policy.setAlertSuppressionMinutes(45);

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(subscriptionId, chatId))
                .thenReturn(Optional.of(subscription));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findByIdAndSubscriptionId(alertId, subscriptionId))
                .thenReturn(Optional.of(alert));
        when(channelMonetizationAlertPolicyRepository.findById(chatId)).thenReturn(Optional.of(policy));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactSubscriptionRepository.save(any(ChannelMonetizationArtifactSubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = monetizationService.snoozeArtifactSubscriptionAlert(
                requesterId,
                chatId,
                subscriptionId,
                alertId,
                new SnoozeMonetizationArtifactSubscriptionAlertRequest(30, null)
        );

        assertThat(response.status()).isEqualTo("SNOOZED");
        assertThat(response.acknowledgedByUserId()).isEqualTo(requesterId);
        assertThat(response.snoozedUntil()).isNotNull();
        assertThat(subscription.getEscalationStatus()).isEqualTo("SNOOZED");
    }

    @Test
    void artifactAlertSummaryAggregatesAlertAndSubscriptionCounts() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity openAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        openAlert.setId(UUID.randomUUID());
        openAlert.setSubscriptionId(UUID.randomUUID());
        openAlert.setChannelChatId(chatId);
        openAlert.setTargetChatId(UUID.randomUUID());
        openAlert.setSeverity("HIGH");
        openAlert.setStatus("OPEN");
        openAlert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T07:00:00Z"));
        openAlert.setResolveByDueAt(Instant.parse("2026-03-14T08:00:00Z"));
        openAlert.setBreachedAt(Instant.parse("2026-03-14T09:00:00Z"));
        openAlert.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity snoozedAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        snoozedAlert.setId(UUID.randomUUID());
        snoozedAlert.setSubscriptionId(UUID.randomUUID());
        snoozedAlert.setChannelChatId(chatId);
        snoozedAlert.setTargetChatId(UUID.randomUUID());
        snoozedAlert.setSeverity("WARN");
        snoozedAlert.setStatus("SNOOZED");
        snoozedAlert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T09:00:00Z"));
        snoozedAlert.setResolveByDueAt(Instant.parse("2026-03-14T10:00:00Z"));
        snoozedAlert.setSnoozedUntil(Instant.parse("2026-03-14T20:00:00Z"));
        snoozedAlert.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));
        ChannelMonetizationArtifactSubscriptionEntity openSubscription = new ChannelMonetizationArtifactSubscriptionEntity();
        openSubscription.setId(UUID.randomUUID());
        openSubscription.setChannelChatId(chatId);
        openSubscription.setEscalationStatus("OPEN");
        ChannelMonetizationArtifactSubscriptionEntity snoozedSubscription = new ChannelMonetizationArtifactSubscriptionEntity();
        snoozedSubscription.setId(UUID.randomUUID());
        snoozedSubscription.setChannelChatId(chatId);
        snoozedSubscription.setEscalationStatus("SNOOZED");
        ChannelMonetizationArtifactSubscriptionFailureEntity failure = new ChannelMonetizationArtifactSubscriptionFailureEntity();
        failure.setId(UUID.randomUUID());
        failure.setChannelChatId(chatId);
        failure.setFailedAt(Instant.parse("2026-03-14T12:05:00Z"));
        ChannelMonetizationAlertDigestRunEntity digestRun = new ChannelMonetizationAlertDigestRunEntity();
        digestRun.setId(UUID.randomUUID());
        digestRun.setChannelChatId(chatId);
        digestRun.setCreatedAt(Instant.parse("2026-03-14T12:10:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(openAlert, snoozedAlert));
        when(channelMonetizationArtifactSubscriptionRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(openSubscription, snoozedSubscription));
        when(channelMonetizationArtifactSubscriptionFailureRepository.findAllByChannelChatIdOrderByFailedAtDesc(chatId))
                .thenReturn(List.of(failure));
        when(channelMonetizationAlertDigestRunRepository.findFirstByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(Optional.of(digestRun));

        var response = monetizationService.getArtifactAlertSummary(requesterId, chatId);

        assertThat(response.totalAlerts()).isEqualTo(2);
        assertThat(response.openAlerts()).isEqualTo(1);
        assertThat(response.snoozedAlerts()).isEqualTo(1);
        assertThat(response.highSeverityOpenAlerts()).isEqualTo(1);
        assertThat(response.overdueAcknowledgementAlerts()).isEqualTo(1);
        assertThat(response.overdueResolutionAlerts()).isEqualTo(1);
        assertThat(response.breachedAlerts()).isEqualTo(1);
        assertThat(response.openSubscriptions()).isEqualTo(1);
        assertThat(response.snoozedSubscriptions()).isEqualTo(1);
        assertThat(response.latestDigestRunId()).isEqualTo(digestRun.getId());
    }

    @Test
    void assignArtifactSubscriptionAlertStoresOwnerAndAuditEvent() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(UUID.randomUUID());
        subscription.setCreatedByUserId(requesterId);
        subscription.setArtifactType("REPORT_EXPORT");
        subscription.setStatus("ACTIVE");
        subscription.setEscalationStatus("OPEN");
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(subscription.getTargetChatId());
        alert.setSeverity("WARN");
        alert.setFailureCount(3);
        alert.setStatus("OPEN");

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(subscriptionId, chatId))
                .thenReturn(Optional.of(subscription));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findByIdAndSubscriptionId(alertId, subscriptionId))
                .thenReturn(Optional.of(alert));
        UserEntity owner = new UserEntity();
        owner.setId(ownerUserId);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(owner));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactSubscriptionRepository.save(any(ChannelMonetizationArtifactSubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactAlertAuditEventRepository.save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = monetizationService.assignArtifactSubscriptionAlert(
                requesterId,
                chatId,
                subscriptionId,
                alertId,
                new AssignMonetizationArtifactSubscriptionAlertRequest(ownerUserId, "take ownership")
        );

        assertThat(response.ownerUserId()).isEqualTo(ownerUserId);
        assertThat(response.assignedAt()).isNotNull();
        assertThat(subscription.getEscalationStatus()).isEqualTo("ACKNOWLEDGED");
        verify(channelMonetizationArtifactAlertAuditEventRepository).save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class));
    }

    @Test
    void claimArtifactSubscriptionAlertAssignsCurrentOperator() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setEscalationStatus("OPEN");
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(UUID.randomUUID());
        alert.setStatus("OPEN");

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(subscriptionId, chatId))
                .thenReturn(Optional.of(subscription));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findByIdAndSubscriptionId(alertId, subscriptionId))
                .thenReturn(Optional.of(alert));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactSubscriptionRepository.save(any(ChannelMonetizationArtifactSubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactAlertAuditEventRepository.save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = monetizationService.claimArtifactSubscriptionAlert(requesterId, chatId, subscriptionId, alertId);

        assertThat(response.ownerUserId()).isEqualTo(requesterId);
        assertThat(response.assignedAt()).isNotNull();
        assertThat(subscription.getEscalationStatus()).isEqualTo("ACKNOWLEDGED");
        verify(channelMonetizationArtifactAlertAuditEventRepository).save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class));
    }

    @Test
    void releaseArtifactSubscriptionAlertClearsCurrentOwner() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setEscalationStatus("ACKNOWLEDGED");
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(UUID.randomUUID());
        alert.setStatus("OPEN");
        alert.setOwnerUserId(requesterId);
        alert.setAssignedAt(Instant.parse("2026-03-14T11:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(subscriptionId, chatId))
                .thenReturn(Optional.of(subscription));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findByIdAndSubscriptionId(alertId, subscriptionId))
                .thenReturn(Optional.of(alert));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactAlertAuditEventRepository.save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = monetizationService.releaseArtifactSubscriptionAlert(requesterId, chatId, subscriptionId, alertId);

        assertThat(response.ownerUserId()).isNull();
        assertThat(response.assignedAt()).isNull();
        verify(channelMonetizationArtifactAlertAuditEventRepository).save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class));
    }

    @Test
    void claimArtifactSubscriptionAlertRejectsAnotherOwner() {
        UUID requesterId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(UUID.randomUUID());
        alert.setStatus("OPEN");
        alert.setOwnerUserId(otherOwnerId);

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(subscriptionId, chatId))
                .thenReturn(Optional.of(subscription));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findByIdAndSubscriptionId(alertId, subscriptionId))
                .thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> monetizationService.claimArtifactSubscriptionAlert(requesterId, chatId, subscriptionId, alertId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already claimed by another operator");
    }

    @Test
    void listClaimableArtifactSubscriptionAlertQueueReturnsOnlyUnassignedMatchingAlerts() {
        UUID requesterId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity triageAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        triageAlert.setId(UUID.randomUUID());
        triageAlert.setSubscriptionId(UUID.randomUUID());
        triageAlert.setChannelChatId(chatId);
        triageAlert.setTargetChatId(UUID.randomUUID());
        triageAlert.setSeverity("HIGH");
        triageAlert.setStatus("OPEN");
        triageAlert.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity ownedAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        ownedAlert.setId(UUID.randomUUID());
        ownedAlert.setSubscriptionId(UUID.randomUUID());
        ownedAlert.setChannelChatId(chatId);
        ownedAlert.setTargetChatId(UUID.randomUUID());
        ownedAlert.setOwnerUserId(otherOwnerId);
        ownedAlert.setSeverity("HIGH");
        ownedAlert.setStatus("OPEN");
        ownedAlert.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity nonTriageWarn = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        nonTriageWarn.setId(UUID.randomUUID());
        nonTriageWarn.setSubscriptionId(UUID.randomUUID());
        nonTriageWarn.setChannelChatId(chatId);
        nonTriageWarn.setTargetChatId(UUID.randomUUID());
        nonTriageWarn.setSeverity("WARN");
        nonTriageWarn.setStatus("OPEN");
        nonTriageWarn.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(nonTriageWarn, ownedAlert, triageAlert));

        var response = monetizationService.listClaimableArtifactSubscriptionAlertQueue(
                requesterId,
                chatId,
                "high",
                "open",
                true,
                false,
                false,
                null
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).alertId()).isEqualTo(triageAlert.getId());
    }

    @Test
    void claimNextArtifactSubscriptionAlertClaimsHighestPriorityUnassignedAlert() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID firstSubscriptionId = UUID.randomUUID();
        UUID secondSubscriptionId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionEntity firstSubscription = new ChannelMonetizationArtifactSubscriptionEntity();
        firstSubscription.setId(firstSubscriptionId);
        firstSubscription.setChannelChatId(chatId);
        firstSubscription.setEscalationStatus("OPEN");
        ChannelMonetizationArtifactSubscriptionAlertEntity breachedHigh = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        breachedHigh.setId(UUID.randomUUID());
        breachedHigh.setSubscriptionId(firstSubscriptionId);
        breachedHigh.setChannelChatId(chatId);
        breachedHigh.setTargetChatId(UUID.randomUUID());
        breachedHigh.setSeverity("HIGH");
        breachedHigh.setStatus("OPEN");
        breachedHigh.setBreachedAt(Instant.parse("2026-03-14T08:00:00Z"));
        breachedHigh.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity openWarn = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        openWarn.setId(UUID.randomUUID());
        openWarn.setSubscriptionId(secondSubscriptionId);
        openWarn.setChannelChatId(chatId);
        openWarn.setTargetChatId(UUID.randomUUID());
        openWarn.setSeverity("WARN");
        openWarn.setStatus("OPEN");
        openWarn.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(openWarn, breachedHigh));
        when(channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(firstSubscriptionId, chatId))
                .thenReturn(Optional.of(firstSubscription));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactSubscriptionRepository.save(any(ChannelMonetizationArtifactSubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactAlertAuditEventRepository.save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = monetizationService.claimNextArtifactSubscriptionAlert(
                requesterId,
                chatId,
                null,
                "open",
                false,
                false,
                false,
                null
        );

        assertThat(response.alertId()).isEqualTo(breachedHigh.getId());
        assertThat(response.ownerUserId()).isEqualTo(requesterId);
        assertThat(firstSubscription.getEscalationStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(openWarn.getOwnerUserId()).isNull();
    }

    @Test
    void getClaimableArtifactAlertWorkloadSummarizesBacklogAndNextCandidate() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity triageFollowUp = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        triageFollowUp.setId(UUID.randomUUID());
        triageFollowUp.setSubscriptionId(UUID.randomUUID());
        triageFollowUp.setChannelChatId(chatId);
        triageFollowUp.setTargetChatId(UUID.randomUUID());
        triageFollowUp.setSeverity("HIGH");
        triageFollowUp.setStatus("OPEN");
        triageFollowUp.setTriagedAt(Instant.parse("2026-03-14T10:00:00Z"));
        triageFollowUp.setCreatedAt(Instant.parse("2026-03-14T09:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity breached = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        breached.setId(UUID.randomUUID());
        breached.setSubscriptionId(UUID.randomUUID());
        breached.setChannelChatId(chatId);
        breached.setTargetChatId(UUID.randomUUID());
        breached.setSeverity("HIGH");
        breached.setStatus("OPEN");
        breached.setBreachedAt(Instant.parse("2026-03-14T08:00:00Z"));
        breached.setAcknowledgeByDueAt(Instant.parse("2026-03-14T07:00:00Z"));
        breached.setResolveByDueAt(Instant.parse("2026-03-14T07:30:00Z"));
        breached.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(breached, triageFollowUp));

        var response = monetizationService.getClaimableArtifactAlertWorkload(
                requesterId,
                chatId,
                null,
                "open",
                false,
                false,
                false,
                "TRIAGE_FIRST"
        );

        assertThat(response.totalClaimableAlerts()).isEqualTo(2);
        assertThat(response.highSeverityClaimableAlerts()).isEqualTo(2);
        assertThat(response.breachedClaimableAlerts()).isEqualTo(1);
        assertThat(response.overdueClaimableAlerts()).isEqualTo(1);
        assertThat(response.triageFollowUpClaimableAlerts()).isEqualTo(1);
        assertThat(response.nextAlertId()).isEqualTo(triageFollowUp.getId());
        assertThat(response.nextSubscriptionId()).isEqualTo(triageFollowUp.getSubscriptionId());
    }

    @Test
    void peekNextClaimableArtifactSubscriptionAlertRespectsTriageFirstStrategy() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity triageFollowUp = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        triageFollowUp.setId(UUID.randomUUID());
        triageFollowUp.setSubscriptionId(UUID.randomUUID());
        triageFollowUp.setChannelChatId(chatId);
        triageFollowUp.setTargetChatId(UUID.randomUUID());
        triageFollowUp.setSeverity("HIGH");
        triageFollowUp.setStatus("OPEN");
        triageFollowUp.setTriagedAt(Instant.parse("2026-03-14T10:00:00Z"));
        triageFollowUp.setCreatedAt(Instant.parse("2026-03-14T09:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity breached = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        breached.setId(UUID.randomUUID());
        breached.setSubscriptionId(UUID.randomUUID());
        breached.setChannelChatId(chatId);
        breached.setTargetChatId(UUID.randomUUID());
        breached.setSeverity("HIGH");
        breached.setStatus("OPEN");
        breached.setBreachedAt(Instant.parse("2026-03-14T08:00:00Z"));
        breached.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(breached, triageFollowUp));

        var response = monetizationService.peekNextClaimableArtifactSubscriptionAlert(
                requesterId,
                chatId,
                null,
                "open",
                false,
                false,
                false,
                "TRIAGE_FIRST"
        );

        assertThat(response.alertId()).isEqualTo(triageFollowUp.getId());
    }

    @Test
    void claimNextArtifactSubscriptionAlertUsesPolicyDefaultsWhenFiltersAreOmitted() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID triageSubscriptionId = UUID.randomUUID();
        UUID regularSubscriptionId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationAlertPolicyEntity policy = new ChannelMonetizationAlertPolicyEntity();
        policy.setChannelChatId(chatId);
        policy.setClaimNextStrategy("TRIAGE_FIRST");
        policy.setClaimNextTriageOnlyDefault(true);
        ChannelMonetizationArtifactSubscriptionEntity triageSubscription = new ChannelMonetizationArtifactSubscriptionEntity();
        triageSubscription.setId(triageSubscriptionId);
        triageSubscription.setChannelChatId(chatId);
        triageSubscription.setEscalationStatus("OPEN");
        ChannelMonetizationArtifactSubscriptionAlertEntity triageFollowUp = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        triageFollowUp.setId(UUID.randomUUID());
        triageFollowUp.setSubscriptionId(triageSubscriptionId);
        triageFollowUp.setChannelChatId(chatId);
        triageFollowUp.setTargetChatId(UUID.randomUUID());
        triageFollowUp.setSeverity("HIGH");
        triageFollowUp.setStatus("OPEN");
        triageFollowUp.setTriagedAt(Instant.parse("2026-03-14T10:00:00Z"));
        triageFollowUp.setCreatedAt(Instant.parse("2026-03-14T09:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity regularAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        regularAlert.setId(UUID.randomUUID());
        regularAlert.setSubscriptionId(regularSubscriptionId);
        regularAlert.setChannelChatId(chatId);
        regularAlert.setTargetChatId(UUID.randomUUID());
        regularAlert.setSeverity("HIGH");
        regularAlert.setStatus("OPEN");
        regularAlert.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationAlertPolicyRepository.findById(chatId)).thenReturn(Optional.of(policy));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(regularAlert, triageFollowUp));
        when(channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(triageSubscriptionId, chatId))
                .thenReturn(Optional.of(triageSubscription));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactSubscriptionRepository.save(any(ChannelMonetizationArtifactSubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactAlertAuditEventRepository.save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = monetizationService.claimNextArtifactSubscriptionAlert(
                requesterId,
                chatId,
                null,
                null,
                null,
                false,
                false,
                null
        );

        assertThat(response.alertId()).isEqualTo(triageFollowUp.getId());
        assertThat(response.ownerUserId()).isEqualTo(requesterId);
        assertThat(regularAlert.getOwnerUserId()).isNull();
    }

    @Test
    void peekMyArtifactSubscriptionAlertReturnsHighestPriorityOwnedAlert() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity breachedHigh = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        breachedHigh.setId(UUID.randomUUID());
        breachedHigh.setSubscriptionId(UUID.randomUUID());
        breachedHigh.setChannelChatId(chatId);
        breachedHigh.setTargetChatId(UUID.randomUUID());
        breachedHigh.setOwnerUserId(requesterId);
        breachedHigh.setSeverity("HIGH");
        breachedHigh.setStatus("OPEN");
        breachedHigh.setBreachedAt(Instant.parse("2026-03-14T08:00:00Z"));
        breachedHigh.setCreatedAt(Instant.parse("2026-03-14T09:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity acknowledgedWarn = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        acknowledgedWarn.setId(UUID.randomUUID());
        acknowledgedWarn.setSubscriptionId(UUID.randomUUID());
        acknowledgedWarn.setChannelChatId(chatId);
        acknowledgedWarn.setTargetChatId(UUID.randomUUID());
        acknowledgedWarn.setOwnerUserId(requesterId);
        acknowledgedWarn.setSeverity("WARN");
        acknowledgedWarn.setStatus("ACKNOWLEDGED");
        acknowledgedWarn.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user(requesterId, "Owner")));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(acknowledgedWarn, breachedHigh));

        var response = monetizationService.peekMyArtifactSubscriptionAlert(requesterId, chatId);

        assertThat(response.alertId()).isEqualTo(breachedHigh.getId());
        assertThat(response.ownerUserId()).isEqualTo(requesterId);
    }

    @Test
    void peekOwnerArtifactSubscriptionAlertScopesToRequestedOwner() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID otherOwnerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity ownerAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        ownerAlert.setId(UUID.randomUUID());
        ownerAlert.setSubscriptionId(UUID.randomUUID());
        ownerAlert.setChannelChatId(chatId);
        ownerAlert.setTargetChatId(UUID.randomUUID());
        ownerAlert.setOwnerUserId(ownerUserId);
        ownerAlert.setSeverity("HIGH");
        ownerAlert.setStatus("OPEN");
        ownerAlert.setCreatedAt(Instant.parse("2026-03-14T09:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity otherOwnerAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        otherOwnerAlert.setId(UUID.randomUUID());
        otherOwnerAlert.setSubscriptionId(UUID.randomUUID());
        otherOwnerAlert.setChannelChatId(chatId);
        otherOwnerAlert.setTargetChatId(UUID.randomUUID());
        otherOwnerAlert.setOwnerUserId(otherOwnerUserId);
        otherOwnerAlert.setSeverity("HIGH");
        otherOwnerAlert.setStatus("OPEN");
        otherOwnerAlert.setBreachedAt(Instant.parse("2026-03-14T08:00:00Z"));
        otherOwnerAlert.setCreatedAt(Instant.parse("2026-03-14T08:30:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(otherOwnerAlert, ownerAlert));

        var response = monetizationService.peekOwnerArtifactSubscriptionAlert(requesterId, chatId, ownerUserId);

        assertThat(response.alertId()).isEqualTo(ownerAlert.getId());
        assertThat(response.ownerUserId()).isEqualTo(ownerUserId);
    }

    @Test
    void addArtifactSubscriptionAlertCommentStoresCommentAndAuditEvent() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(UUID.randomUUID());
        subscription.setCreatedByUserId(requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(subscription.getTargetChatId());
        alert.setStatus("OPEN");

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(subscriptionId, chatId))
                .thenReturn(Optional.of(subscription));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findByIdAndSubscriptionId(alertId, subscriptionId))
                .thenReturn(Optional.of(alert));
        when(channelMonetizationArtifactAlertCommentRepository.save(any(ChannelMonetizationArtifactAlertCommentEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactAlertCommentEntity comment = invocation.getArgument(0);
                    comment.setId(commentId);
                    comment.setCreatedAt(Instant.parse("2026-03-14T12:30:00Z"));
                    return comment;
                });
        when(channelMonetizationArtifactAlertAuditEventRepository.save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = monetizationService.addArtifactSubscriptionAlertComment(
                requesterId,
                chatId,
                subscriptionId,
                alertId,
                new CreateMonetizationArtifactAlertCommentRequest("operator note")
        );

        assertThat(response.commentId()).isEqualTo(commentId);
        assertThat(response.body()).isEqualTo("operator note");
        verify(channelMonetizationArtifactAlertAuditEventRepository).save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class));
    }

    @Test
    void listArtifactSubscriptionAlertTimelineReturnsAuditEventsInOrder() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        ChannelMonetizationArtifactAlertAuditEventEntity created = new ChannelMonetizationArtifactAlertAuditEventEntity();
        created.setId(UUID.randomUUID());
        created.setAlertId(alertId);
        created.setSubscriptionId(subscriptionId);
        created.setChannelChatId(chatId);
        created.setActionType("CREATED");
        created.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
        ChannelMonetizationArtifactAlertAuditEventEntity assigned = new ChannelMonetizationArtifactAlertAuditEventEntity();
        assigned.setId(UUID.randomUUID());
        assigned.setAlertId(alertId);
        assigned.setSubscriptionId(subscriptionId);
        assigned.setChannelChatId(chatId);
        assigned.setActionType("ASSIGNED");
        assigned.setCreatedAt(Instant.parse("2026-03-14T12:05:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(subscriptionId, chatId))
                .thenReturn(Optional.of(subscription));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findByIdAndSubscriptionId(alertId, subscriptionId))
                .thenReturn(Optional.of(alert));
        when(channelMonetizationArtifactAlertAuditEventRepository.findAllByAlertIdOrderByCreatedAtAsc(alertId))
                .thenReturn(List.of(created, assigned));

        var response = monetizationService.listArtifactSubscriptionAlertTimeline(requesterId, chatId, subscriptionId, alertId);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).actionType()).isEqualTo("CREATED");
        assertThat(response.get(1).actionType()).isEqualTo("ASSIGNED");
    }

    @Test
    void remindArtifactSubscriptionAlertPublishesReminderAndUpdatesCounter() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        UUID reminderMessageId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(UUID.randomUUID());
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(subscription.getTargetChatId());
        alert.setStatus("OPEN");
        alert.setLastFailureReason("delivery failed");
        alert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        alert.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(subscriptionId, chatId))
                .thenReturn(Optional.of(subscription));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findByIdAndSubscriptionId(alertId, subscriptionId))
                .thenReturn(Optional.of(alert));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(reminderMessageId));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactAlertAuditEventRepository.save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MonetizationArtifactAlertReminderResponse response = monetizationService.remindArtifactSubscriptionAlert(
                requesterId,
                chatId,
                subscriptionId,
                alertId
        );

        assertThat(response.alertId()).isEqualTo(alertId);
        assertThat(response.reminderType()).isEqualTo("ACKNOWLEDGEMENT_OVERDUE");
        assertThat(response.publishedMessageId()).isEqualTo(reminderMessageId);
        assertThat(response.routedTargetChatId()).isEqualTo(subscription.getTargetChatId());
        assertThat(response.reminderCount()).isEqualTo(1);
        assertThat(alert.getLastReminderAt()).isNotNull();
    }

    @Test
    void remindArtifactSubscriptionAlertUsesConfiguredReminderTargetChatId() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        UUID reminderTargetChatId = UUID.randomUUID();
        UUID reminderMessageId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(UUID.randomUUID());
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(subscription.getTargetChatId());
        alert.setStatus("OPEN");
        alert.setLastFailureReason("delivery failed");
        alert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        alert.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        ChannelMonetizationAlertPolicyEntity policy = new ChannelMonetizationAlertPolicyEntity();
        policy.setChannelChatId(chatId);
        policy.setReminderTargetChatId(reminderTargetChatId);
        policy.setSeverityUpgradeAfterMinutes(10080);
        policy.setBreachEscalationAfterMinutes(10080);

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(subscriptionId, chatId))
                .thenReturn(Optional.of(subscription));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findByIdAndSubscriptionId(alertId, subscriptionId))
                .thenReturn(Optional.of(alert));
        when(channelMonetizationAlertPolicyRepository.findById(chatId)).thenReturn(Optional.of(policy));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(reminderMessageId));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactAlertAuditEventRepository.save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MonetizationArtifactAlertReminderResponse response = monetizationService.remindArtifactSubscriptionAlert(
                requesterId,
                chatId,
                subscriptionId,
                alertId
        );

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(requesterId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(reminderTargetChatId);
        assertThat(response.routedTargetChatId()).isEqualTo(reminderTargetChatId);
        assertThat(alert.getLastReminderTargetChatId()).isEqualTo(reminderTargetChatId);
    }

    @Test
    void remindArtifactSubscriptionAlertUsesPersonalReminderTargetChatIdForOwnedAlert() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        UUID personalReminderTargetChatId = UUID.randomUUID();
        UUID reminderMessageId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(UUID.randomUUID());
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(subscription.getTargetChatId());
        alert.setOwnerUserId(requesterId);
        alert.setStatus("OPEN");
        alert.setLastFailureReason("delivery failed");
        alert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        alert.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        ChannelMonetizationAlertPolicyEntity policy = new ChannelMonetizationAlertPolicyEntity();
        policy.setChannelChatId(chatId);
        policy.setPersonalReminderTargetChatId(personalReminderTargetChatId);
        policy.setSeverityUpgradeAfterMinutes(10080);
        policy.setBreachEscalationAfterMinutes(10080);

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(subscriptionId, chatId))
                .thenReturn(Optional.of(subscription));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findByIdAndSubscriptionId(alertId, subscriptionId))
                .thenReturn(Optional.of(alert));
        when(channelMonetizationAlertPolicyRepository.findById(chatId)).thenReturn(Optional.of(policy));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(reminderMessageId));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactAlertAuditEventRepository.save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MonetizationArtifactAlertReminderResponse response = monetizationService.remindArtifactSubscriptionAlert(
                requesterId,
                chatId,
                subscriptionId,
                alertId
        );

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(requesterId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(personalReminderTargetChatId);
        assertThat(response.routedTargetChatId()).isEqualTo(personalReminderTargetChatId);
        assertThat(alert.getLastReminderTargetChatId()).isEqualTo(personalReminderTargetChatId);
    }

    @Test
    void listMyDueArtifactAlertReminderQueueReturnsOnlyDueOwnedAlerts() {
        UUID requesterId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity dueOwned = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        dueOwned.setId(UUID.randomUUID());
        dueOwned.setSubscriptionId(UUID.randomUUID());
        dueOwned.setChannelChatId(chatId);
        dueOwned.setTargetChatId(UUID.randomUUID());
        dueOwned.setOwnerUserId(requesterId);
        dueOwned.setSeverity("HIGH");
        dueOwned.setStatus("OPEN");
        dueOwned.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        dueOwned.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        dueOwned.setCreatedAt(Instant.parse("2026-03-14T09:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity notDueOwned = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        notDueOwned.setId(UUID.randomUUID());
        notDueOwned.setSubscriptionId(UUID.randomUUID());
        notDueOwned.setChannelChatId(chatId);
        notDueOwned.setTargetChatId(UUID.randomUUID());
        notDueOwned.setOwnerUserId(requesterId);
        notDueOwned.setSeverity("HIGH");
        notDueOwned.setStatus("OPEN");
        notDueOwned.setAcknowledgeByDueAt(Instant.parse("2099-03-14T01:00:00Z"));
        notDueOwned.setResolveByDueAt(Instant.parse("2099-03-14T02:00:00Z"));
        notDueOwned.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity dueOtherOwner = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        dueOtherOwner.setId(UUID.randomUUID());
        dueOtherOwner.setSubscriptionId(UUID.randomUUID());
        dueOtherOwner.setChannelChatId(chatId);
        dueOtherOwner.setTargetChatId(UUID.randomUUID());
        dueOtherOwner.setOwnerUserId(otherOwnerId);
        dueOtherOwner.setSeverity("HIGH");
        dueOtherOwner.setStatus("OPEN");
        dueOtherOwner.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        dueOtherOwner.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        dueOtherOwner.setCreatedAt(Instant.parse("2026-03-14T08:30:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user(requesterId, "Owner")));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(notDueOwned, dueOwned, dueOtherOwner));

        var response = monetizationService.listMyDueArtifactAlertReminderQueue(
                requesterId,
                chatId,
                "high",
                false
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).alertId()).isEqualTo(dueOwned.getId());
        assertThat(response.get(0).ownerUserId()).isEqualTo(requesterId);
    }

    @Test
    void remindOwnerDueArtifactAlertsPublishesDueRemindersAndRespectsLimit() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID firstReminderMessageId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity breachedHigh = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        breachedHigh.setId(UUID.randomUUID());
        breachedHigh.setSubscriptionId(UUID.randomUUID());
        breachedHigh.setChannelChatId(chatId);
        breachedHigh.setTargetChatId(UUID.randomUUID());
        breachedHigh.setOwnerUserId(ownerUserId);
        breachedHigh.setSeverity("HIGH");
        breachedHigh.setStatus("OPEN");
        breachedHigh.setLastFailureReason("payment export failed");
        breachedHigh.setBreachedAt(Instant.parse("2026-03-14T00:30:00Z"));
        breachedHigh.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        breachedHigh.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        breachedHigh.setCreatedAt(Instant.parse("2026-03-14T08:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity warnDue = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        warnDue.setId(UUID.randomUUID());
        warnDue.setSubscriptionId(UUID.randomUUID());
        warnDue.setChannelChatId(chatId);
        warnDue.setTargetChatId(UUID.randomUUID());
        warnDue.setOwnerUserId(ownerUserId);
        warnDue.setSeverity("WARN");
        warnDue.setStatus("ACKNOWLEDGED");
        warnDue.setLastFailureReason("report export failed");
        warnDue.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        warnDue.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        warnDue.setCreatedAt(Instant.parse("2026-03-14T09:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(warnDue, breachedHigh));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(firstReminderMessageId));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactAlertAuditEventRepository.save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = monetizationService.remindOwnerDueArtifactAlerts(
                requesterId,
                chatId,
                ownerUserId,
                null,
                false,
                1
        );

        assertThat(response.ownerUserId()).isEqualTo(ownerUserId);
        assertThat(response.dueAlerts()).isEqualTo(2);
        assertThat(response.remindedAlerts()).isEqualTo(1);
        assertThat(response.reminders()).hasSize(1);
        assertThat(response.reminders().get(0).alertId()).isEqualTo(breachedHigh.getId());
        assertThat(breachedHigh.getLastReminderAt()).isNotNull();
        assertThat(warnDue.getLastReminderAt()).isNull();
        verify(messageService).sendMessage(eq(requesterId), any(SendMessageRequest.class));
    }

    @Test
    void remindMyDueArtifactAlertsUsesCurrentUserShortcut() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID reminderMessageId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity dueOwned = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        dueOwned.setId(UUID.randomUUID());
        dueOwned.setSubscriptionId(UUID.randomUUID());
        dueOwned.setChannelChatId(chatId);
        dueOwned.setTargetChatId(UUID.randomUUID());
        dueOwned.setOwnerUserId(requesterId);
        dueOwned.setSeverity("HIGH");
        dueOwned.setStatus("OPEN");
        dueOwned.setLastFailureReason("artifact delivery failed");
        dueOwned.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        dueOwned.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        dueOwned.setCreatedAt(Instant.parse("2026-03-14T08:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user(requesterId, "Owner")));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(dueOwned));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(reminderMessageId));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactAlertAuditEventRepository.save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = monetizationService.remindMyDueArtifactAlerts(
                requesterId,
                chatId,
                null,
                false,
                10
        );

        assertThat(response.ownerUserId()).isEqualTo(requesterId);
        assertThat(response.ownerDisplayName()).isEqualTo("Owner");
        assertThat(response.dueAlerts()).isEqualTo(1);
        assertThat(response.remindedAlerts()).isEqualTo(1);
        assertThat(response.reminders()).hasSize(1);
        assertThat(response.reminders().get(0).publishedMessageId()).isEqualTo(reminderMessageId);
    }

    @Test
    void getMyArtifactAlertReminderDigestSummarizesDueAlerts() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity breachedHigh = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        breachedHigh.setId(UUID.randomUUID());
        breachedHigh.setSubscriptionId(UUID.randomUUID());
        breachedHigh.setChannelChatId(chatId);
        breachedHigh.setTargetChatId(UUID.randomUUID());
        breachedHigh.setOwnerUserId(requesterId);
        breachedHigh.setSeverity("HIGH");
        breachedHigh.setStatus("OPEN");
        breachedHigh.setBreachedAt(Instant.parse("2026-03-14T00:30:00Z"));
        breachedHigh.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        breachedHigh.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        breachedHigh.setCreatedAt(Instant.parse("2026-03-14T08:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity dueWarn = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        dueWarn.setId(UUID.randomUUID());
        dueWarn.setSubscriptionId(UUID.randomUUID());
        dueWarn.setChannelChatId(chatId);
        dueWarn.setTargetChatId(UUID.randomUUID());
        dueWarn.setOwnerUserId(requesterId);
        dueWarn.setSeverity("WARN");
        dueWarn.setStatus("ACKNOWLEDGED");
        dueWarn.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        dueWarn.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        dueWarn.setCreatedAt(Instant.parse("2026-03-14T09:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity notDue = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        notDue.setId(UUID.randomUUID());
        notDue.setSubscriptionId(UUID.randomUUID());
        notDue.setChannelChatId(chatId);
        notDue.setTargetChatId(UUID.randomUUID());
        notDue.setOwnerUserId(requesterId);
        notDue.setSeverity("HIGH");
        notDue.setStatus("OPEN");
        notDue.setAcknowledgeByDueAt(Instant.parse("2099-03-14T01:00:00Z"));
        notDue.setResolveByDueAt(Instant.parse("2099-03-14T02:00:00Z"));
        notDue.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user(requesterId, "Owner")));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(notDue, dueWarn, breachedHigh));

        var response = monetizationService.getMyArtifactAlertReminderDigest(requesterId, chatId, null, false);

        assertThat(response.ownerUserId()).isEqualTo(requesterId);
        assertThat(response.ownerDisplayName()).isEqualTo("Owner");
        assertThat(response.dueAlerts()).isEqualTo(2);
        assertThat(response.highSeverityDueAlerts()).isEqualTo(1);
        assertThat(response.breachedDueAlerts()).isEqualTo(2);
        assertThat(response.overdueDueAlerts()).isEqualTo(2);
        assertThat(response.nextAlertId()).isEqualTo(breachedHigh.getId());
        assertThat(response.nextSubscriptionId()).isEqualTo(breachedHigh.getSubscriptionId());
        assertThat(response.nextSeverity()).isEqualTo("HIGH");
    }

    @Test
    void exportOwnerArtifactAlertReminderDigestPersistsArtifact() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity dueAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        dueAlert.setId(UUID.randomUUID());
        dueAlert.setSubscriptionId(UUID.randomUUID());
        dueAlert.setChannelChatId(chatId);
        dueAlert.setTargetChatId(UUID.randomUUID());
        dueAlert.setOwnerUserId(ownerUserId);
        dueAlert.setSeverity("HIGH");
        dueAlert.setStatus("OPEN");
        dueAlert.setBreachedAt(Instant.parse("2026-03-14T08:00:00Z"));
        dueAlert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        dueAlert.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        dueAlert.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(dueAlert));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:45:00Z"));
                    return artifact;
                });

        var response = monetizationService.exportOwnerArtifactAlertReminderDigest(
                requesterId,
                chatId,
                ownerUserId,
                null,
                false
        );

        assertThat(response.artifactId()).isEqualTo(artifactId);
        assertThat(response.rowCount()).isEqualTo(1);
        assertThat(response.totalUnits()).isEqualTo(1);
        assertThat(response.fileName()).contains("reminder-digest");
    }

    @Test
    void publishMyArtifactAlertReminderDigestExportsAndPublishesArtifact() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID publishedMessageId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity dueAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        dueAlert.setId(UUID.randomUUID());
        dueAlert.setSubscriptionId(UUID.randomUUID());
        dueAlert.setChannelChatId(chatId);
        dueAlert.setTargetChatId(UUID.randomUUID());
        dueAlert.setOwnerUserId(requesterId);
        dueAlert.setSeverity("HIGH");
        dueAlert.setStatus("OPEN");
        dueAlert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        dueAlert.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        dueAlert.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        final ChannelMonetizationExportArtifactEntity[] storedArtifact = new ChannelMonetizationExportArtifactEntity[1];

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, targetChatId)).thenReturn(channel(targetChatId, requesterId));
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user(requesterId, "Me")));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(dueAlert));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:45:00Z"));
                    storedArtifact[0] = artifact;
                    return artifact;
                });
        when(channelMonetizationExportArtifactRepository.findById(artifactId))
                .thenAnswer(invocation -> Optional.ofNullable(storedArtifact[0]));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(publishedMessageId));
        when(channelMonetizationArtifactPublicationRepository.save(any(ChannelMonetizationArtifactPublicationEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactPublicationEntity publication = invocation.getArgument(0);
                    publication.setId(publicationId);
                    publication.setPublishedAt(Instant.parse("2026-03-14T12:46:00Z"));
                    return publication;
                });

        var response = monetizationService.publishMyArtifactAlertReminderDigest(
                requesterId,
                chatId,
                null,
                false,
                new PublishMonetizationArtifactRequest(targetChatId, "reminder digest")
        );

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(requesterId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(targetChatId);
        assertThat(requestCaptor.getValue().text()).contains("reminder digest");
        assertThat(response.publicationId()).isEqualTo(publicationId);
        assertThat(response.artifactId()).isEqualTo(artifactId);
        assertThat(response.publishedMessageId()).isEqualTo(publishedMessageId);
    }

    @Test
    void publishMyArtifactAlertReminderQueueUsesPersonalReminderDigestTargetWhenRequestTargetMissing() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID personalReminderDigestTargetChatId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID publishedMessageId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity dueAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        dueAlert.setId(UUID.randomUUID());
        dueAlert.setSubscriptionId(UUID.randomUUID());
        dueAlert.setChannelChatId(chatId);
        dueAlert.setTargetChatId(UUID.randomUUID());
        dueAlert.setOwnerUserId(requesterId);
        dueAlert.setSeverity("HIGH");
        dueAlert.setStatus("OPEN");
        dueAlert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        dueAlert.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        dueAlert.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        ChannelMonetizationAlertPolicyEntity policy = new ChannelMonetizationAlertPolicyEntity();
        policy.setChannelChatId(chatId);
        policy.setPersonalReminderDigestTargetChatId(personalReminderDigestTargetChatId);
        final ChannelMonetizationExportArtifactEntity[] storedArtifact = new ChannelMonetizationExportArtifactEntity[1];

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, personalReminderDigestTargetChatId))
                .thenReturn(channel(personalReminderDigestTargetChatId, requesterId));
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user(requesterId, "Me")));
        when(channelMonetizationAlertPolicyRepository.findById(chatId)).thenReturn(Optional.of(policy));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(dueAlert));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:45:00Z"));
                    storedArtifact[0] = artifact;
                    return artifact;
                });
        when(channelMonetizationExportArtifactRepository.findById(artifactId))
                .thenAnswer(invocation -> Optional.ofNullable(storedArtifact[0]));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(publishedMessageId));
        when(channelMonetizationArtifactPublicationRepository.save(any(ChannelMonetizationArtifactPublicationEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactPublicationEntity publication = invocation.getArgument(0);
                    publication.setId(publicationId);
                    publication.setPublishedAt(Instant.parse("2026-03-14T12:46:00Z"));
                    return publication;
                });

        var response = monetizationService.publishMyArtifactAlertReminderQueue(
                requesterId,
                chatId,
                null,
                false,
                new PublishMonetizationArtifactRequest(null, "policy target")
        );

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(requesterId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(personalReminderDigestTargetChatId);
        assertThat(requestCaptor.getValue().text()).contains("policy target");
        assertThat(response.publicationId()).isEqualTo(publicationId);
        assertThat(response.artifactId()).isEqualTo(artifactId);
    }

    @Test
    void createOwnerArtifactAlertReminderDigestSubscriptionPersistsFiltersAndTarget() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel(chatId, requesterId));
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, targetChatId)).thenReturn(channel(targetChatId, requesterId));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.save(any(
                ChannelMonetizationOwnerReminderDigestSubscriptionEntity.class
        ))).thenAnswer(invocation -> {
            ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription = invocation.getArgument(0);
            subscription.setId(subscriptionId);
            subscription.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
            subscription.setUpdatedAt(Instant.parse("2026-03-14T12:00:00Z"));
            return subscription;
        });

        var response = monetizationService.createOwnerArtifactAlertReminderDigestSubscription(
                requesterId,
                chatId,
                ownerUserId,
                new CreateMonetizationOwnerReminderDigestSubscriptionRequest(
                        targetChatId,
                        "high",
                        true,
                        90,
                        "digest note"
                )
        );

        assertThat(response.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(response.channelChatId()).isEqualTo(chatId);
        assertThat(response.ownerUserId()).isEqualTo(ownerUserId);
        assertThat(response.targetChatId()).isEqualTo(targetChatId);
        assertThat(response.createdByUserId()).isEqualTo(requesterId);
        assertThat(response.severity()).isEqualTo("HIGH");
        assertThat(response.breachedOnly()).isTrue();
        assertThat(response.minIntervalMinutes()).isEqualTo(90);
        assertThat(response.note()).isEqualTo("digest note");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.failureState()).isEqualTo("NONE");
        assertThat(response.nextRetryAt()).isNull();
        assertThat(response.autoPausedAt()).isNull();
    }

    @Test
    void listArtifactAlertReminderDigestSubscriptionIssuesFiltersAndSortsOperationalStates() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID otherOwnerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        Instant now = Instant.now();

        ChannelMonetizationOwnerReminderDigestSubscriptionEntity autoPaused =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        autoPaused.setId(UUID.randomUUID());
        autoPaused.setChannelChatId(chatId);
        autoPaused.setOwnerUserId(ownerUserId);
        autoPaused.setStatus("PAUSED");
        autoPaused.setFailureState("AUTO_PAUSED");
        autoPaused.setConsecutiveFailureCount(3);
        autoPaused.setLastFailureAt(now.minusSeconds(60));
        autoPaused.setUpdatedAt(now.minusSeconds(60));

        ChannelMonetizationOwnerReminderDigestSubscriptionEntity dueBackoff =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        dueBackoff.setId(UUID.randomUUID());
        dueBackoff.setChannelChatId(chatId);
        dueBackoff.setOwnerUserId(ownerUserId);
        dueBackoff.setStatus("ACTIVE");
        dueBackoff.setFailureState("BACKOFF");
        dueBackoff.setConsecutiveFailureCount(1);
        dueBackoff.setLastFailureAt(now.minusSeconds(120));
        dueBackoff.setNextRetryAt(now.minusSeconds(30));
        dueBackoff.setUpdatedAt(now.minusSeconds(120));

        ChannelMonetizationOwnerReminderDigestSubscriptionEntity futureBackoff =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        futureBackoff.setId(UUID.randomUUID());
        futureBackoff.setChannelChatId(chatId);
        futureBackoff.setOwnerUserId(otherOwnerUserId);
        futureBackoff.setStatus("ACTIVE");
        futureBackoff.setFailureState("BACKOFF");
        futureBackoff.setConsecutiveFailureCount(2);
        futureBackoff.setLastFailureAt(now.minusSeconds(180));
        futureBackoff.setNextRetryAt(now.plusSeconds(3600));
        futureBackoff.setUpdatedAt(now.minusSeconds(180));

        ChannelMonetizationOwnerReminderDigestSubscriptionEntity healthy =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        healthy.setId(UUID.randomUUID());
        healthy.setChannelChatId(chatId);
        healthy.setOwnerUserId(ownerUserId);
        healthy.setStatus("ACTIVE");
        healthy.setFailureState("NONE");
        healthy.setUpdatedAt(now.minusSeconds(10));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel(chatId, requesterId));
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.findAllByChannelChatIdOrderByUpdatedAtDesc(chatId))
                .thenReturn(List.of(healthy, futureBackoff, dueBackoff, autoPaused));

        var response = monetizationService.listArtifactAlertReminderDigestSubscriptionIssues(
                requesterId,
                chatId,
                ownerUserId,
                null,
                true
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).subscriptionId()).isEqualTo(dueBackoff.getId());
        assertThat(response.get(0).failureState()).isEqualTo("BACKOFF");

        var unfiltered = monetizationService.listArtifactAlertReminderDigestSubscriptionIssues(
                requesterId,
                chatId,
                null,
                null,
                false
        );

        assertThat(unfiltered).extracting("subscriptionId")
                .containsExactly(autoPaused.getId(), dueBackoff.getId(), futureBackoff.getId());
    }

    @Test
    void getArtifactAlertReminderDigestSubscriptionIssueSummaryAggregatesOwnersAndRetryCounts() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID otherOwnerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        Instant now = Instant.now();

        ChannelMonetizationOwnerReminderDigestSubscriptionEntity dueBackoff =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        dueBackoff.setId(UUID.randomUUID());
        dueBackoff.setChannelChatId(chatId);
        dueBackoff.setOwnerUserId(ownerUserId);
        dueBackoff.setFailureState("BACKOFF");
        dueBackoff.setStatus("ACTIVE");
        dueBackoff.setLastFailureAt(now.minusSeconds(120));
        dueBackoff.setNextRetryAt(now.minusSeconds(30));
        dueBackoff.setUpdatedAt(now.minusSeconds(120));

        ChannelMonetizationOwnerReminderDigestSubscriptionEntity autoPaused =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        autoPaused.setId(UUID.randomUUID());
        autoPaused.setChannelChatId(chatId);
        autoPaused.setOwnerUserId(otherOwnerUserId);
        autoPaused.setFailureState("AUTO_PAUSED");
        autoPaused.setStatus("PAUSED");
        autoPaused.setLastFailureAt(now.minusSeconds(60));
        autoPaused.setAutoPausedAt(now.minusSeconds(60));
        autoPaused.setUpdatedAt(now.minusSeconds(60));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel(chatId, requesterId));
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(userRepository.findById(otherOwnerUserId)).thenReturn(Optional.of(user(otherOwnerUserId, "Other")));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.findAllByChannelChatIdOrderByUpdatedAtDesc(chatId))
                .thenReturn(List.of(dueBackoff, autoPaused));

        var response = monetizationService.getArtifactAlertReminderDigestSubscriptionIssueSummary(requesterId, chatId);

        assertThat(response.channelChatId()).isEqualTo(chatId);
        assertThat(response.totalIssues()).isEqualTo(2);
        assertThat(response.backoffSubscriptions()).isEqualTo(1);
        assertThat(response.autoPausedSubscriptions()).isEqualTo(1);
        assertThat(response.dueRetrySubscriptions()).isEqualTo(1);
        assertThat(response.owners()).hasSize(2);
        assertThat(response.owners().get(0).ownerUserId()).isEqualTo(otherOwnerUserId);
        assertThat(response.owners().get(0).autoPausedSubscriptions()).isEqualTo(1);
    }

    @Test
    void listMyArtifactAlertReminderDigestSubscriptionIssuesScopesToRequester() {
        UUID requesterId = UUID.randomUUID();
        UUID otherOwnerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        Instant now = Instant.now();

        ChannelMonetizationOwnerReminderDigestSubscriptionEntity mine =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        mine.setId(UUID.randomUUID());
        mine.setChannelChatId(chatId);
        mine.setOwnerUserId(requesterId);
        mine.setFailureState("BACKOFF");
        mine.setStatus("ACTIVE");
        mine.setLastFailureAt(now.minusSeconds(120));
        mine.setNextRetryAt(now.plusSeconds(120));
        mine.setUpdatedAt(now.minusSeconds(120));

        ChannelMonetizationOwnerReminderDigestSubscriptionEntity other =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        other.setId(UUID.randomUUID());
        other.setChannelChatId(chatId);
        other.setOwnerUserId(otherOwnerUserId);
        other.setFailureState("AUTO_PAUSED");
        other.setStatus("PAUSED");
        other.setLastFailureAt(now.minusSeconds(60));
        other.setUpdatedAt(now.minusSeconds(60));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel(chatId, requesterId));
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user(requesterId, "Me")));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.findAllByChannelChatIdOrderByUpdatedAtDesc(chatId))
                .thenReturn(List.of(other, mine));

        var response = monetizationService.listMyArtifactAlertReminderDigestSubscriptionIssues(
                requesterId,
                chatId,
                null,
                false
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).subscriptionId()).isEqualTo(mine.getId());
    }

    @Test
    void exportArtifactAlertReminderDigestSubscriptionIssuesPersistsArtifact() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        Instant now = Instant.now();
        ChannelMonetizationOwnerReminderDigestSubscriptionEntity issue =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        issue.setId(UUID.randomUUID());
        issue.setChannelChatId(chatId);
        issue.setOwnerUserId(ownerUserId);
        issue.setFailureState("BACKOFF");
        issue.setStatus("ACTIVE");
        issue.setLastFailureAt(now.minusSeconds(60));
        issue.setNextRetryAt(now.minusSeconds(10));
        issue.setUpdatedAt(now.minusSeconds(60));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel(chatId, requesterId));
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.findAllByChannelChatIdOrderByUpdatedAtDesc(chatId))
                .thenReturn(List.of(issue));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:45:00Z"));
                    return artifact;
                });

        var response = monetizationService.exportArtifactAlertReminderDigestSubscriptionIssues(
                requesterId,
                chatId,
                ownerUserId,
                null,
                false
        );

        assertThat(response.artifactId()).isEqualTo(artifactId);
        assertThat(response.rowCount()).isEqualTo(1);
        assertThat(response.fileName()).contains("owner-reminder-digest-issues");
    }

    @Test
    void exportArtifactAlertReminderDigestSubscriptionIssueSummaryPersistsArtifact() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        Instant now = Instant.now();
        ChannelMonetizationOwnerReminderDigestSubscriptionEntity issue =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        issue.setId(UUID.randomUUID());
        issue.setChannelChatId(chatId);
        issue.setOwnerUserId(ownerUserId);
        issue.setFailureState("AUTO_PAUSED");
        issue.setStatus("PAUSED");
        issue.setLastFailureAt(now.minusSeconds(60));
        issue.setAutoPausedAt(now.minusSeconds(30));
        issue.setUpdatedAt(now.minusSeconds(60));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel(chatId, requesterId));
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.findAllByChannelChatIdOrderByUpdatedAtDesc(chatId))
                .thenReturn(List.of(issue));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:45:00Z"));
                    return artifact;
                });

        var response = monetizationService.exportArtifactAlertReminderDigestSubscriptionIssueSummary(
                requesterId,
                chatId
        );

        assertThat(response.artifactId()).isEqualTo(artifactId);
        assertThat(response.rowCount()).isEqualTo(1);
        assertThat(response.fileName()).contains("owner-reminder-digest-issues-summary");
    }

    @Test
    void publishMyArtifactAlertReminderDigestSubscriptionIssuesUsesPolicyTargetWhenRequestMissing() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID publishedMessageId = UUID.randomUUID();
        Instant now = Instant.now();
        ChannelMonetizationOwnerReminderDigestSubscriptionEntity issue =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        issue.setId(UUID.randomUUID());
        issue.setChannelChatId(chatId);
        issue.setOwnerUserId(requesterId);
        issue.setTargetChatId(targetChatId);
        issue.setFailureState("BACKOFF");
        issue.setStatus("ACTIVE");
        issue.setLastFailureAt(now.minusSeconds(60));
        issue.setNextRetryAt(now.minusSeconds(5));
        issue.setUpdatedAt(now.minusSeconds(60));
        ChannelMonetizationAlertPolicyEntity policy = new ChannelMonetizationAlertPolicyEntity();
        policy.setChannelChatId(chatId);
        policy.setPersonalReminderDigestTargetChatId(targetChatId);
        final ChannelMonetizationExportArtifactEntity[] storedArtifact = new ChannelMonetizationExportArtifactEntity[1];

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel(chatId, requesterId));
        when(chatService.getOwnedChat(requesterId, targetChatId)).thenReturn(channel(targetChatId, requesterId));
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user(requesterId, "Owner")));
        when(channelMonetizationAlertPolicyRepository.findById(chatId)).thenReturn(Optional.of(policy));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.findAllByChannelChatIdOrderByUpdatedAtDesc(chatId))
                .thenReturn(List.of(issue));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:45:00Z"));
                    storedArtifact[0] = artifact;
                    return artifact;
                });
        when(channelMonetizationExportArtifactRepository.findById(artifactId))
                .thenAnswer(invocation -> Optional.ofNullable(storedArtifact[0]));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(publishedMessageId));
        when(channelMonetizationArtifactPublicationRepository.save(any(ChannelMonetizationArtifactPublicationEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactPublicationEntity publication = invocation.getArgument(0);
                    publication.setId(publicationId);
                    publication.setPublishedAt(Instant.parse("2026-03-14T12:46:00Z"));
                    return publication;
                });

        var response = monetizationService.publishMyArtifactAlertReminderDigestSubscriptionIssues(
                requesterId,
                chatId,
                null,
                false,
                null
        );

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(requesterId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(targetChatId);
        assertThat(response.publicationId()).isEqualTo(publicationId);
        assertThat(response.artifactId()).isEqualTo(artifactId);
        assertThat(response.publishedMessageId()).isEqualTo(publishedMessageId);
    }

    @Test
    void publishArtifactAlertReminderDigestSubscriptionIssueSummaryPublishesGeneratedArtifact() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID publishedMessageId = UUID.randomUUID();
        Instant now = Instant.now();
        ChannelMonetizationOwnerReminderDigestSubscriptionEntity issue =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        issue.setId(UUID.randomUUID());
        issue.setChannelChatId(chatId);
        issue.setOwnerUserId(ownerUserId);
        issue.setFailureState("BACKOFF");
        issue.setStatus("ACTIVE");
        issue.setLastFailureAt(now.minusSeconds(60));
        issue.setNextRetryAt(now.minusSeconds(5));
        issue.setUpdatedAt(now.minusSeconds(60));
        final ChannelMonetizationExportArtifactEntity[] storedArtifact = new ChannelMonetizationExportArtifactEntity[1];

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel(chatId, requesterId));
        when(chatService.getOwnedChat(requesterId, targetChatId)).thenReturn(channel(targetChatId, requesterId));
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.findAllByChannelChatIdOrderByUpdatedAtDesc(chatId))
                .thenReturn(List.of(issue));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:45:00Z"));
                    storedArtifact[0] = artifact;
                    return artifact;
                });
        when(channelMonetizationExportArtifactRepository.findById(artifactId))
                .thenAnswer(invocation -> Optional.ofNullable(storedArtifact[0]));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(publishedMessageId));
        when(channelMonetizationArtifactPublicationRepository.save(any(ChannelMonetizationArtifactPublicationEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactPublicationEntity publication = invocation.getArgument(0);
                    publication.setId(publicationId);
                    publication.setPublishedAt(Instant.parse("2026-03-14T12:46:00Z"));
                    return publication;
                });

        var response = monetizationService.publishArtifactAlertReminderDigestSubscriptionIssueSummary(
                requesterId,
                chatId,
                new PublishMonetizationArtifactRequest(targetChatId, "issue summary")
        );

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(requesterId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(targetChatId);
        assertThat(requestCaptor.getValue().text()).contains("issue summary");
        assertThat(response.publicationId()).isEqualTo(publicationId);
        assertThat(response.artifactId()).isEqualTo(artifactId);
        assertThat(response.publishedMessageId()).isEqualTo(publishedMessageId);
    }

    @Test
    void resumeArtifactAlertReminderDigestSubscriptionIssuesClearsSelectedFailures() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        Instant now = Instant.now();
        ChannelMonetizationOwnerReminderDigestSubscriptionEntity autoPaused =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        autoPaused.setId(UUID.randomUUID());
        autoPaused.setChannelChatId(chatId);
        autoPaused.setOwnerUserId(ownerUserId);
        autoPaused.setStatus("PAUSED");
        autoPaused.setFailureState("AUTO_PAUSED");
        autoPaused.setConsecutiveFailureCount(3);
        autoPaused.setAutoPausedAt(now.minusSeconds(30));
        autoPaused.setUpdatedAt(now.minusSeconds(30));
        ChannelMonetizationOwnerReminderDigestSubscriptionEntity backoff =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        backoff.setId(UUID.randomUUID());
        backoff.setChannelChatId(chatId);
        backoff.setOwnerUserId(ownerUserId);
        backoff.setStatus("ACTIVE");
        backoff.setFailureState("BACKOFF");
        backoff.setConsecutiveFailureCount(1);
        backoff.setNextRetryAt(now.plusSeconds(300));
        backoff.setUpdatedAt(now.minusSeconds(60));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel(chatId, requesterId));
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.findAllByChannelChatIdOrderByUpdatedAtDesc(chatId))
                .thenReturn(List.of(backoff, autoPaused));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.save(any(
                ChannelMonetizationOwnerReminderDigestSubscriptionEntity.class
        ))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = monetizationService.resumeArtifactAlertReminderDigestSubscriptionIssues(
                requesterId,
                chatId,
                ownerUserId,
                "AUTO_PAUSED",
                false,
                10
        );

        assertThat(response.matchedSubscriptions()).isEqualTo(1);
        assertThat(response.processedSubscriptions()).isEqualTo(1);
        assertThat(response.subscriptionIds()).containsExactly(autoPaused.getId());
        assertThat(autoPaused.getStatus()).isEqualTo("ACTIVE");
        assertThat(autoPaused.getFailureState()).isEqualTo("NONE");
        assertThat(backoff.getFailureState()).isEqualTo("BACKOFF");
    }

    @Test
    void retryArtifactAlertReminderDigestSubscriptionIssuesDispatchesDueBackoffSubscriptions() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID publishedMessageId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Instant now = Instant.now();
        ChannelMonetizationOwnerReminderDigestSubscriptionEntity dueBackoff =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        dueBackoff.setId(UUID.randomUUID());
        dueBackoff.setChannelChatId(chatId);
        dueBackoff.setOwnerUserId(ownerUserId);
        dueBackoff.setTargetChatId(targetChatId);
        dueBackoff.setCreatedByUserId(requesterId);
        dueBackoff.setStatus("ACTIVE");
        dueBackoff.setFailureState("BACKOFF");
        dueBackoff.setSeverity("WARN");
        dueBackoff.setNextRetryAt(now.minusSeconds(60));
        dueBackoff.setUpdatedAt(now.minusSeconds(60));
        ChannelMonetizationOwnerReminderDigestSubscriptionEntity futureBackoff =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        futureBackoff.setId(UUID.randomUUID());
        futureBackoff.setChannelChatId(chatId);
        futureBackoff.setOwnerUserId(ownerUserId);
        futureBackoff.setStatus("ACTIVE");
        futureBackoff.setFailureState("BACKOFF");
        futureBackoff.setNextRetryAt(now.plusSeconds(3600));
        futureBackoff.setUpdatedAt(now.minusSeconds(120));
        ChannelMonetizationArtifactSubscriptionAlertEntity dueAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        dueAlert.setId(UUID.randomUUID());
        dueAlert.setSubscriptionId(UUID.randomUUID());
        dueAlert.setChannelChatId(chatId);
        dueAlert.setTargetChatId(UUID.randomUUID());
        dueAlert.setOwnerUserId(ownerUserId);
        dueAlert.setSeverity("WARN");
        dueAlert.setStatus("OPEN");
        dueAlert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        dueAlert.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        dueAlert.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        final ChannelMonetizationExportArtifactEntity[] storedArtifact = new ChannelMonetizationExportArtifactEntity[1];

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel(chatId, requesterId));
        when(chatService.getOwnedChat(requesterId, targetChatId)).thenReturn(channel(targetChatId, requesterId));
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.findAllByChannelChatIdOrderByUpdatedAtDesc(chatId))
                .thenReturn(List.of(futureBackoff, dueBackoff));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.save(any(
                ChannelMonetizationOwnerReminderDigestSubscriptionEntity.class
        ))).thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationOwnerReminderDigestRunRepository.save(any(
                ChannelMonetizationOwnerReminderDigestRunEntity.class
        ))).thenAnswer(invocation -> {
            ChannelMonetizationOwnerReminderDigestRunEntity run = invocation.getArgument(0);
            run.setId(runId);
            return run;
        });
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(dueAlert));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:45:00Z"));
                    storedArtifact[0] = artifact;
                    return artifact;
                });
        when(channelMonetizationExportArtifactRepository.findById(artifactId))
                .thenAnswer(invocation -> Optional.ofNullable(storedArtifact[0]));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(publishedMessageId));
        when(channelMonetizationArtifactPublicationRepository.save(any(ChannelMonetizationArtifactPublicationEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactPublicationEntity publication = invocation.getArgument(0);
                    publication.setId(publicationId);
                    publication.setPublishedAt(Instant.parse("2026-03-14T12:46:00Z"));
                    return publication;
                });

        var response = monetizationService.retryArtifactAlertReminderDigestSubscriptionIssues(
                requesterId,
                chatId,
                ownerUserId,
                true,
                10
        );

        assertThat(response.failureState()).isEqualTo("BACKOFF");
        assertThat(response.retryDueOnly()).isTrue();
        assertThat(response.matchedSubscriptions()).isEqualTo(1);
        assertThat(response.processedSubscriptions()).isEqualTo(1);
        assertThat(response.subscriptionIds()).containsExactly(dueBackoff.getId());
        assertThat(response.runIds()).containsExactly(runId);
    }

    @Test
    void processOwnerReminderDigestSubscriptionsPublishesDueDigestUsingPolicyTarget() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID publishedMessageId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Instant dueBefore = Instant.parse("2026-03-14T12:00:00Z");
        ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        subscription.setChannelChatId(chatId);
        subscription.setOwnerUserId(ownerUserId);
        subscription.setCreatedByUserId(requesterId);
        subscription.setSeverity("HIGH");
        subscription.setStatus("ACTIVE");
        subscription.setMinIntervalMinutes(60);
        subscription.setCreatedAt(Instant.parse("2026-03-14T09:00:00Z"));
        subscription.setUpdatedAt(Instant.parse("2026-03-14T09:00:00Z"));
        subscription.setNote("scheduled digest");
        ChannelMonetizationArtifactSubscriptionAlertEntity dueAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        dueAlert.setId(UUID.randomUUID());
        dueAlert.setSubscriptionId(UUID.randomUUID());
        dueAlert.setChannelChatId(chatId);
        dueAlert.setTargetChatId(UUID.randomUUID());
        dueAlert.setOwnerUserId(ownerUserId);
        dueAlert.setSeverity("HIGH");
        dueAlert.setStatus("OPEN");
        dueAlert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        dueAlert.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        dueAlert.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        ChannelMonetizationAlertPolicyEntity policy = new ChannelMonetizationAlertPolicyEntity();
        policy.setChannelChatId(chatId);
        policy.setPersonalReminderDigestTargetChatId(targetChatId);
        final ChannelMonetizationExportArtifactEntity[] storedArtifact = new ChannelMonetizationExportArtifactEntity[1];

        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.lockDueBatch(dueBefore, 10))
                .thenReturn(List.of(subscription));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.save(any(
                ChannelMonetizationOwnerReminderDigestSubscriptionEntity.class
        ))).thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationOwnerReminderDigestRunRepository.save(any(
                ChannelMonetizationOwnerReminderDigestRunEntity.class
        ))).thenAnswer(invocation -> {
            ChannelMonetizationOwnerReminderDigestRunEntity run = invocation.getArgument(0);
            run.setId(runId);
            return run;
        });
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel(chatId, requesterId));
        when(chatService.getOwnedChat(requesterId, targetChatId)).thenReturn(channel(targetChatId, requesterId));
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationAlertPolicyRepository.findById(chatId)).thenReturn(Optional.of(policy));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(dueAlert));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:01:00Z"));
                    storedArtifact[0] = artifact;
                    return artifact;
                });
        when(channelMonetizationExportArtifactRepository.findById(artifactId))
                .thenAnswer(invocation -> Optional.ofNullable(storedArtifact[0]));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(publishedMessageId));
        when(channelMonetizationArtifactPublicationRepository.save(any(ChannelMonetizationArtifactPublicationEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactPublicationEntity publication = invocation.getArgument(0);
                    publication.setId(publicationId);
                    publication.setPublishedAt(Instant.parse("2026-03-14T12:02:00Z"));
                    return publication;
                });

        int processed = monetizationService.processOwnerReminderDigestSubscriptions(dueBefore, 10);

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(requesterId), requestCaptor.capture());
        assertThat(processed).isEqualTo(1);
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(targetChatId);
        assertThat(requestCaptor.getValue().text()).contains("scheduled digest");
        assertThat(subscription.getLastProcessedAt()).isEqualTo(dueBefore);
        assertThat(subscription.getLastDeliveredArtifactId()).isEqualTo(artifactId);
        assertThat(subscription.getLastDeliveredAt()).isEqualTo(Instant.parse("2026-03-14T12:02:00Z"));
        assertThat(subscription.getConsecutiveFailureCount()).isZero();
        assertThat(subscription.getFailureState()).isEqualTo("NONE");
    }

    @Test
    void processOwnerReminderDigestSubscriptionsMarksEmptyRunAsProcessedWithoutPublishing() {
        UUID chatId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Instant dueBefore = Instant.parse("2026-03-14T12:00:00Z");
        ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        subscription.setChannelChatId(chatId);
        subscription.setOwnerUserId(ownerUserId);
        subscription.setCreatedByUserId(UUID.randomUUID());
        subscription.setStatus("ACTIVE");
        subscription.setMinIntervalMinutes(30);
        subscription.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        subscription.setUpdatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        subscription.setConsecutiveFailureCount(2);
        subscription.setLastFailureAt(Instant.parse("2026-03-14T11:00:00Z"));
        subscription.setLastFailureReason("old failure");

        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.lockDueBatch(dueBefore, 5))
                .thenReturn(List.of(subscription));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.save(any(
                ChannelMonetizationOwnerReminderDigestSubscriptionEntity.class
        ))).thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationOwnerReminderDigestRunRepository.save(any(
                ChannelMonetizationOwnerReminderDigestRunEntity.class
        ))).thenAnswer(invocation -> {
            ChannelMonetizationOwnerReminderDigestRunEntity run = invocation.getArgument(0);
            run.setId(runId);
            return run;
        });
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of());

        int processed = monetizationService.processOwnerReminderDigestSubscriptions(dueBefore, 5);

        assertThat(processed).isEqualTo(1);
        assertThat(subscription.getLastProcessedAt()).isEqualTo(dueBefore);
        assertThat(subscription.getLastDeliveredAt()).isNull();
        assertThat(subscription.getLastDeliveredArtifactId()).isNull();
        assertThat(subscription.getConsecutiveFailureCount()).isZero();
        assertThat(subscription.getFailureState()).isEqualTo("NONE");
        assertThat(subscription.getLastFailureAt()).isNull();
        assertThat(subscription.getLastFailureReason()).isNull();
    }

    @Test
    void processOwnerReminderDigestSubscriptionsFailureSchedulesBackoffAndSkipsUntilRetryAt() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Instant dueBefore = Instant.parse("2026-03-14T12:00:00Z");
        ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        subscription.setChannelChatId(chatId);
        subscription.setOwnerUserId(ownerUserId);
        subscription.setCreatedByUserId(requesterId);
        subscription.setStatus("ACTIVE");
        subscription.setMinIntervalMinutes(60);
        subscription.setCreatedAt(Instant.parse("2026-03-14T09:00:00Z"));
        subscription.setUpdatedAt(Instant.parse("2026-03-14T09:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity dueAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        dueAlert.setId(UUID.randomUUID());
        dueAlert.setSubscriptionId(UUID.randomUUID());
        dueAlert.setChannelChatId(chatId);
        dueAlert.setTargetChatId(UUID.randomUUID());
        dueAlert.setOwnerUserId(ownerUserId);
        dueAlert.setSeverity("WARN");
        dueAlert.setStatus("OPEN");
        dueAlert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        dueAlert.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        dueAlert.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));

        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.lockDueBatch(dueBefore, 10))
                .thenReturn(List.of(subscription));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.lockDueBatch(
                dueBefore.plusSeconds(30L * 60L),
                10
        )).thenReturn(List.of());
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.save(any(
                ChannelMonetizationOwnerReminderDigestSubscriptionEntity.class
        ))).thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationOwnerReminderDigestRunRepository.save(any(
                ChannelMonetizationOwnerReminderDigestRunEntity.class
        ))).thenAnswer(invocation -> {
            ChannelMonetizationOwnerReminderDigestRunEntity run = invocation.getArgument(0);
            run.setId(runId);
            return run;
        });
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel(chatId, requesterId));
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(dueAlert));

        int processed = monetizationService.processOwnerReminderDigestSubscriptions(dueBefore, 10);

        assertThat(processed).isEqualTo(1);
        assertThat(subscription.getStatus()).isEqualTo("ACTIVE");
        assertThat(subscription.getConsecutiveFailureCount()).isEqualTo(1);
        assertThat(subscription.getFailureState()).isEqualTo("BACKOFF");
        assertThat(subscription.getLastFailureReason()).isEqualTo("Target chat id is required");
        assertThat(subscription.getNextRetryAt()).isEqualTo(dueBefore.plusSeconds(60L * 60L));

        int processedBeforeRetry = monetizationService.processOwnerReminderDigestSubscriptions(
                dueBefore.plusSeconds(30L * 60L),
                10
        );
        assertThat(processedBeforeRetry).isZero();
    }

    @Test
    void processOwnerReminderDigestSubscriptionsAutoPausesAfterFailureThresholdAndResumeClearsFailureState() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Instant dueBefore = Instant.parse("2026-03-14T12:00:00Z");
        ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setOwnerUserId(ownerUserId);
        subscription.setCreatedByUserId(requesterId);
        subscription.setStatus("ACTIVE");
        subscription.setMinIntervalMinutes(30);
        subscription.setConsecutiveFailureCount(2);
        subscription.setFailureState("BACKOFF");
        subscription.setCreatedAt(Instant.parse("2026-03-14T09:00:00Z"));
        subscription.setUpdatedAt(Instant.parse("2026-03-14T09:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity dueAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        dueAlert.setId(UUID.randomUUID());
        dueAlert.setSubscriptionId(UUID.randomUUID());
        dueAlert.setChannelChatId(chatId);
        dueAlert.setTargetChatId(UUID.randomUUID());
        dueAlert.setOwnerUserId(ownerUserId);
        dueAlert.setSeverity("WARN");
        dueAlert.setStatus("OPEN");
        dueAlert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        dueAlert.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        dueAlert.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));

        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.lockDueBatch(dueBefore, 10))
                .thenReturn(List.of(subscription));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository
                .findByIdAndChannelChatIdAndOwnerUserId(subscriptionId, chatId, ownerUserId))
                .thenReturn(Optional.of(subscription));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.save(any(
                ChannelMonetizationOwnerReminderDigestSubscriptionEntity.class
        ))).thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationOwnerReminderDigestRunRepository.save(any(
                ChannelMonetizationOwnerReminderDigestRunEntity.class
        ))).thenAnswer(invocation -> {
            ChannelMonetizationOwnerReminderDigestRunEntity run = invocation.getArgument(0);
            run.setId(runId);
            return run;
        });
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel(chatId, requesterId));
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(dueAlert));

        int processed = monetizationService.processOwnerReminderDigestSubscriptions(dueBefore, 10);

        assertThat(processed).isEqualTo(1);
        assertThat(subscription.getStatus()).isEqualTo("PAUSED");
        assertThat(subscription.getConsecutiveFailureCount()).isEqualTo(3);
        assertThat(subscription.getFailureState()).isEqualTo("AUTO_PAUSED");
        assertThat(subscription.getAutoPausedAt()).isEqualTo(dueBefore);
        assertThat(subscription.getNextRetryAt()).isNull();

        var resumed = monetizationService.resumeOwnerArtifactAlertReminderDigestSubscription(
                requesterId,
                chatId,
                ownerUserId,
                subscriptionId
        );

        assertThat(resumed.status()).isEqualTo("ACTIVE");
        assertThat(resumed.failureState()).isEqualTo("NONE");
        assertThat(resumed.consecutiveFailureCount()).isZero();
        assertThat(resumed.nextRetryAt()).isNull();
        assertThat(resumed.autoPausedAt()).isNull();
    }

    @Test
    void dispatchOwnerArtifactAlertReminderDigestSubscriptionCreatesManualDeliveredRun() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID publishedMessageId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setOwnerUserId(ownerUserId);
        subscription.setTargetChatId(targetChatId);
        subscription.setCreatedByUserId(requesterId);
        subscription.setStatus("ACTIVE");
        subscription.setMinIntervalMinutes(60);
        subscription.setSeverity("WARN");
        subscription.setBreachedOnly(false);
        subscription.setCreatedAt(Instant.parse("2026-03-14T09:00:00Z"));
        subscription.setUpdatedAt(Instant.parse("2026-03-14T09:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity dueAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        dueAlert.setId(UUID.randomUUID());
        dueAlert.setSubscriptionId(UUID.randomUUID());
        dueAlert.setChannelChatId(chatId);
        dueAlert.setTargetChatId(UUID.randomUUID());
        dueAlert.setOwnerUserId(ownerUserId);
        dueAlert.setSeverity("WARN");
        dueAlert.setStatus("OPEN");
        dueAlert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        dueAlert.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        dueAlert.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        final ChannelMonetizationExportArtifactEntity[] storedArtifact = new ChannelMonetizationExportArtifactEntity[1];

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel(chatId, requesterId));
        when(chatService.getOwnedChat(requesterId, targetChatId)).thenReturn(channel(targetChatId, requesterId));
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository
                .findByIdAndChannelChatIdAndOwnerUserId(subscriptionId, chatId, ownerUserId))
                .thenReturn(Optional.of(subscription));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository.save(any(
                ChannelMonetizationOwnerReminderDigestSubscriptionEntity.class
        ))).thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationOwnerReminderDigestRunRepository.save(any(
                ChannelMonetizationOwnerReminderDigestRunEntity.class
        ))).thenAnswer(invocation -> {
            ChannelMonetizationOwnerReminderDigestRunEntity run = invocation.getArgument(0);
            run.setId(runId);
            return run;
        });
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(dueAlert));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:01:00Z"));
                    storedArtifact[0] = artifact;
                    return artifact;
                });
        when(channelMonetizationExportArtifactRepository.findById(artifactId))
                .thenAnswer(invocation -> Optional.ofNullable(storedArtifact[0]));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(publishedMessageId));
        when(channelMonetizationArtifactPublicationRepository.save(any(ChannelMonetizationArtifactPublicationEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactPublicationEntity publication = invocation.getArgument(0);
                    publication.setId(publicationId);
                    publication.setPublishedAt(Instant.parse("2026-03-14T12:02:00Z"));
                    return publication;
                });

        var response = monetizationService.dispatchOwnerArtifactAlertReminderDigestSubscription(
                requesterId,
                chatId,
                ownerUserId,
                subscriptionId
        );

        assertThat(response.runId()).isEqualTo(runId);
        assertThat(response.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(response.processedByUserId()).isEqualTo(requesterId);
        assertThat(response.triggerMode()).isEqualTo("MANUAL");
        assertThat(response.status()).isEqualTo("DELIVERED");
        assertThat(response.targetChatId()).isEqualTo(targetChatId);
        assertThat(response.artifactId()).isEqualTo(artifactId);
        assertThat(response.publicationId()).isEqualTo(publicationId);
        assertThat(response.publishedMessageId()).isEqualTo(publishedMessageId);
        assertThat(response.dueAlertCount()).isEqualTo(1);
    }

    @Test
    void listOwnerArtifactAlertReminderDigestSubscriptionRunsReturnsPersistedHistory() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        ChannelMonetizationOwnerReminderDigestRunEntity run = new ChannelMonetizationOwnerReminderDigestRunEntity();
        run.setId(UUID.randomUUID());
        run.setSubscriptionId(subscriptionId);
        run.setChannelChatId(chatId);
        run.setOwnerUserId(ownerUserId);
        run.setProcessedByUserId(requesterId);
        run.setTriggerMode("SCHEDULED");
        run.setStatus("NOOP");
        run.setSeverity("HIGH");
        run.setBreachedOnly(true);
        run.setDueAlertCount(0);
        run.setBreachedDueAlertCount(0);
        run.setProcessedAt(Instant.parse("2026-03-14T12:00:00Z"));
        ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setOwnerUserId(ownerUserId);

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel(chatId, requesterId));
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationOwnerReminderDigestSubscriptionRepository
                .findByIdAndChannelChatIdAndOwnerUserId(subscriptionId, chatId, ownerUserId))
                .thenReturn(Optional.of(subscription));
        when(channelMonetizationOwnerReminderDigestRunRepository.findAllBySubscriptionIdOrderByProcessedAtDesc(subscriptionId))
                .thenReturn(List.of(run));

        var response = monetizationService.listOwnerArtifactAlertReminderDigestSubscriptionRuns(
                requesterId,
                chatId,
                ownerUserId,
                subscriptionId
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).runId()).isEqualTo(run.getId());
        assertThat(response.get(0).status()).isEqualTo("NOOP");
        assertThat(response.get(0).triggerMode()).isEqualTo("SCHEDULED");
    }

    @Test
    void processAlertRemindersSendsScheduledReminderForDueAlert() {
        UUID channelOwnerId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        UUID reminderMessageId = UUID.randomUUID();
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(UUID.randomUUID());
        alert.setStatus("ACKNOWLEDGED");
        alert.setLastFailureReason("delivery failed");
        alert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        alert.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        ChannelMonetizationAlertPolicyEntity policy = new ChannelMonetizationAlertPolicyEntity();
        policy.setChannelChatId(chatId);
        policy.setReminderIntervalMinutes(30);
        ChatEntity channel = channel(chatId, channelOwnerId);

        when(channelMonetizationArtifactSubscriptionAlertRepository.lockDueReminderBatch(any(), eq(20)))
                .thenReturn(List.of(alert));
        when(channelMonetizationAlertPolicyRepository.findById(chatId)).thenReturn(Optional.of(policy));
        when(chatService.getChat(chatId)).thenReturn(channel);
        when(messageService.sendMessage(eq(channelOwnerId), any(SendMessageRequest.class))).thenReturn(message(reminderMessageId));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactAlertAuditEventRepository.save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int processed = monetizationService.processAlertReminders(Instant.parse("2026-03-14T12:00:00Z"), 20);

        assertThat(processed).isEqualTo(1);
        assertThat(alert.getLastReminderMessageId()).isEqualTo(reminderMessageId);
        assertThat(alert.getReminderCount()).isEqualTo(1);
    }

    @Test
    void processAlertRemindersUsesHighSeverityReminderInterval() {
        UUID channelOwnerId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        UUID reminderMessageId = UUID.randomUUID();
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(UUID.randomUUID());
        alert.setSeverity("HIGH");
        alert.setStatus("OPEN");
        alert.setLastFailureReason("delivery failed");
        alert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        alert.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        alert.setLastReminderAt(Instant.parse("2026-03-14T11:50:00Z"));
        alert.setBreachedAt(Instant.parse("2026-03-14T03:00:00Z"));
        ChannelMonetizationAlertPolicyEntity policy = new ChannelMonetizationAlertPolicyEntity();
        policy.setChannelChatId(chatId);
        policy.setReminderIntervalMinutes(60);
        policy.setHighSeverityReminderIntervalMinutes(15);
        ChatEntity channel = channel(chatId, channelOwnerId);

        when(channelMonetizationArtifactSubscriptionAlertRepository.lockDueReminderBatch(any(), eq(20)))
                .thenReturn(List.of(alert));
        when(channelMonetizationAlertPolicyRepository.findById(chatId)).thenReturn(Optional.of(policy));
        when(chatService.getChat(chatId)).thenReturn(channel);
        when(messageService.sendMessage(eq(channelOwnerId), any(SendMessageRequest.class))).thenReturn(message(reminderMessageId));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactAlertAuditEventRepository.save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int processed = monetizationService.processAlertReminders(Instant.parse("2026-03-14T12:20:00Z"), 20);

        assertThat(processed).isEqualTo(1);
        assertThat(alert.getLastReminderMessageId()).isEqualTo(reminderMessageId);
        assertThat(alert.getReminderCount()).isEqualTo(1);
    }

    @Test
    void processAlertRemindersEscalatesSeverityAndPublishesBreachMessage() {
        UUID channelOwnerId = UUID.randomUUID();
        UUID defaultOwnerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        UUID reminderTargetChatId = UUID.randomUUID();
        UUID breachTargetChatId = UUID.randomUUID();
        UUID breachMessageId = UUID.randomUUID();
        UUID reminderMessageId = UUID.randomUUID();
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(UUID.randomUUID());
        alert.setSeverity("WARN");
        alert.setStatus("OPEN");
        alert.setLastFailureReason("delivery failed");
        alert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        alert.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        ChannelMonetizationAlertPolicyEntity policy = new ChannelMonetizationAlertPolicyEntity();
        policy.setChannelChatId(chatId);
        policy.setReminderIntervalMinutes(30);
        policy.setSeverityUpgradeAfterMinutes(30);
        policy.setBreachEscalationAfterMinutes(60);
        policy.setReminderTargetChatId(reminderTargetChatId);
        policy.setBreachTargetChatId(breachTargetChatId);
        policy.setDefaultOwnerUserId(defaultOwnerUserId);
        ChatEntity channel = channel(chatId, channelOwnerId);

        when(channelMonetizationArtifactSubscriptionAlertRepository.lockDueReminderBatch(any(), eq(20)))
                .thenReturn(List.of(alert));
        when(channelMonetizationAlertPolicyRepository.findById(chatId)).thenReturn(Optional.of(policy));
        when(chatService.getChat(chatId)).thenReturn(channel);
        when(messageService.sendMessage(eq(channelOwnerId), any(SendMessageRequest.class)))
                .thenReturn(message(breachMessageId), message(reminderMessageId));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactAlertAuditEventRepository.save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int processed = monetizationService.processAlertReminders(Instant.parse("2026-03-14T12:00:00Z"), 20);

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService, org.mockito.Mockito.times(2)).sendMessage(eq(channelOwnerId), requestCaptor.capture());
        assertThat(requestCaptor.getAllValues().get(0).chatId()).isEqualTo(breachTargetChatId);
        assertThat(requestCaptor.getAllValues().get(0).text()).contains("breached SLA");
        assertThat(requestCaptor.getAllValues().get(1).chatId()).isEqualTo(reminderTargetChatId);
        assertThat(processed).isEqualTo(1);
        assertThat(alert.getSeverity()).isEqualTo("HIGH");
        assertThat(alert.getSeverityEscalatedAt()).isNotNull();
        assertThat(alert.getOwnerUserId()).isEqualTo(defaultOwnerUserId);
        assertThat(alert.getBreachedAt()).isNotNull();
        assertThat(alert.getBreachMessageId()).isEqualTo(breachMessageId);
        assertThat(alert.getLastReminderMessageId()).isEqualTo(reminderMessageId);
        assertThat(alert.getLastReminderTargetChatId()).isEqualTo(reminderTargetChatId);
    }

    @Test
    void triageArtifactSubscriptionAlertPublishesConfiguredTriageMessage() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        UUID triageTargetChatId = UUID.randomUUID();
        UUID triageMessageId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(UUID.randomUUID());
        subscription.setCreatedByUserId(requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(subscription.getTargetChatId());
        alert.setSeverity("HIGH");
        alert.setStatus("OPEN");
        alert.setLastFailureReason("delivery failed");
        alert.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        ChannelMonetizationAlertPolicyEntity policy = new ChannelMonetizationAlertPolicyEntity();
        policy.setChannelChatId(chatId);
        policy.setTriageTargetChatId(triageTargetChatId);

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(subscriptionId, chatId))
                .thenReturn(Optional.of(subscription));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findByIdAndSubscriptionId(alertId, subscriptionId))
                .thenReturn(Optional.of(alert));
        when(channelMonetizationAlertPolicyRepository.findById(chatId)).thenReturn(Optional.of(policy));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(triageMessageId));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactAlertAuditEventRepository.save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = monetizationService.triageArtifactSubscriptionAlert(
                requesterId,
                chatId,
                subscriptionId,
                alertId
        );

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(requesterId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(triageTargetChatId);
        assertThat(requestCaptor.getValue().text()).contains("triage required");
        assertThat(response.alertId()).isEqualTo(alertId);
        assertThat(response.targetChatId()).isEqualTo(triageTargetChatId);
        assertThat(response.publishedMessageId()).isEqualTo(triageMessageId);
        assertThat(response.manual()).isTrue();
        assertThat(alert.getTriagedAt()).isNotNull();
        assertThat(alert.getTriageMessageId()).isEqualTo(triageMessageId);
        assertThat(alert.getTriageTargetChatId()).isEqualTo(triageTargetChatId);
    }

    @Test
    void processAlertTriagePublishesScheduledTriageForDueAlert() {
        UUID channelOwnerId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        UUID triageTargetChatId = UUID.randomUUID();
        UUID triageMessageId = UUID.randomUUID();
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(UUID.randomUUID());
        alert.setSeverity("HIGH");
        alert.setStatus("OPEN");
        alert.setLastFailureReason("delivery failed");
        alert.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        ChannelMonetizationAlertPolicyEntity policy = new ChannelMonetizationAlertPolicyEntity();
        policy.setChannelChatId(chatId);
        policy.setAutoTriageEnabled(true);
        policy.setTriageDelayMinutes(15);
        policy.setTriageTargetChatId(triageTargetChatId);
        ChatEntity channel = channel(chatId, channelOwnerId);

        when(channelMonetizationArtifactSubscriptionAlertRepository.lockPendingTriageBatch(any(), eq(20)))
                .thenReturn(List.of(alert));
        when(channelMonetizationAlertPolicyRepository.findById(chatId)).thenReturn(Optional.of(policy));
        when(chatService.getChat(chatId)).thenReturn(channel);
        when(messageService.sendMessage(eq(channelOwnerId), any(SendMessageRequest.class))).thenReturn(message(triageMessageId));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactAlertAuditEventRepository.save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int processed = monetizationService.processAlertTriage(Instant.parse("2026-03-14T12:00:00Z"), 20);

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(channelOwnerId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(triageTargetChatId);
        assertThat(requestCaptor.getValue().text()).contains("SCHEDULED");
        assertThat(processed).isEqualTo(1);
        assertThat(alert.getTriagedAt()).isNotNull();
        assertThat(alert.getTriageMessageId()).isEqualTo(triageMessageId);
        assertThat(alert.getTriageTargetChatId()).isEqualTo(triageTargetChatId);
    }

    @Test
    void remindTriageArtifactSubscriptionAlertPublishesReminderToConfiguredTarget() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        UUID triageTargetChatId = UUID.randomUUID();
        UUID triageReminderMessageId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(UUID.randomUUID());
        subscription.setCreatedByUserId(requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(subscription.getTargetChatId());
        alert.setSeverity("HIGH");
        alert.setStatus("OPEN");
        alert.setLastFailureReason("delivery failed");
        alert.setTriagedAt(Instant.parse("2026-03-14T10:00:00Z"));
        ChannelMonetizationAlertPolicyEntity policy = new ChannelMonetizationAlertPolicyEntity();
        policy.setChannelChatId(chatId);
        policy.setTriageTargetChatId(triageTargetChatId);

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(subscriptionId, chatId))
                .thenReturn(Optional.of(subscription));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findByIdAndSubscriptionId(alertId, subscriptionId))
                .thenReturn(Optional.of(alert));
        when(channelMonetizationAlertPolicyRepository.findById(chatId)).thenReturn(Optional.of(policy));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class)))
                .thenReturn(message(triageReminderMessageId));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactAlertAuditEventRepository.save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = monetizationService.remindTriageArtifactSubscriptionAlert(
                requesterId,
                chatId,
                subscriptionId,
                alertId
        );

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(requesterId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(triageTargetChatId);
        assertThat(requestCaptor.getValue().text()).contains("triage reminder");
        assertThat(response.targetChatId()).isEqualTo(subscription.getTargetChatId());
        assertThat(response.routedTargetChatId()).isEqualTo(triageTargetChatId);
        assertThat(response.publishedMessageId()).isEqualTo(triageReminderMessageId);
        assertThat(response.manual()).isTrue();
        assertThat(alert.getLastTriageReminderAt()).isNotNull();
        assertThat(alert.getTriageReminderCount()).isEqualTo(1);
        assertThat(alert.getLastTriageReminderMessageId()).isEqualTo(triageReminderMessageId);
        assertThat(alert.getLastTriageReminderTargetChatId()).isEqualTo(triageTargetChatId);
    }

    @Test
    void processTriageRemindersEscalatesTriagedAlertAfterEscalationWindow() {
        UUID channelOwnerId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        UUID triageFallbackOwnerUserId = UUID.randomUUID();
        UUID triageEscalationTargetChatId = UUID.randomUUID();
        UUID triageEscalationMessageId = UUID.randomUUID();
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(alertId);
        alert.setSubscriptionId(subscriptionId);
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(UUID.randomUUID());
        alert.setSeverity("HIGH");
        alert.setStatus("OPEN");
        alert.setLastFailureReason("delivery failed");
        alert.setTriagedAt(Instant.parse("2026-03-14T10:00:00Z"));
        ChannelMonetizationAlertPolicyEntity policy = new ChannelMonetizationAlertPolicyEntity();
        policy.setChannelChatId(chatId);
        policy.setTriageReminderIntervalMinutes(20);
        policy.setTriageEscalationAfterMinutes(60);
        policy.setTriageAutoAssignEnabled(true);
        policy.setTriageFallbackOwnerUserId(triageFallbackOwnerUserId);
        policy.setTriageEscalationTargetChatId(triageEscalationTargetChatId);
        ChatEntity channel = channel(chatId, channelOwnerId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setEscalationStatus("OPEN");

        when(channelMonetizationArtifactSubscriptionAlertRepository.lockDueTriageReminderBatch(any(), eq(20)))
                .thenReturn(List.of(alert));
        when(channelMonetizationAlertPolicyRepository.findById(chatId)).thenReturn(Optional.of(policy));
        when(chatService.getChat(chatId)).thenReturn(channel);
        when(channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(subscriptionId, chatId))
                .thenReturn(Optional.of(subscription));
        when(messageService.sendMessage(eq(channelOwnerId), any(SendMessageRequest.class)))
                .thenReturn(message(triageEscalationMessageId));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactSubscriptionRepository.save(any(ChannelMonetizationArtifactSubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactAlertAuditEventRepository.save(any(ChannelMonetizationArtifactAlertAuditEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int processed = monetizationService.processTriageReminders(Instant.parse("2026-03-14T12:00:00Z"), 20);

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(channelOwnerId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(triageEscalationTargetChatId);
        assertThat(requestCaptor.getValue().text()).contains("triage escalated");
        assertThat(processed).isEqualTo(1);
        assertThat(alert.getTriageEscalatedAt()).isNotNull();
        assertThat(alert.getTriageEscalationMessageId()).isEqualTo(triageEscalationMessageId);
        assertThat(alert.getTriageEscalationTargetChatId()).isEqualTo(triageEscalationTargetChatId);
        assertThat(alert.getOwnerUserId()).isEqualTo(triageFallbackOwnerUserId);
        assertThat(alert.getAssignedAt()).isNotNull();
        assertThat(subscription.getEscalationStatus()).isEqualTo("ACKNOWLEDGED");
    }

    @Test
    void listBreachedArtifactSubscriptionAlertsReturnsPersistedBreachAlerts() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity breachedAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        breachedAlert.setId(UUID.randomUUID());
        breachedAlert.setSubscriptionId(UUID.randomUUID());
        breachedAlert.setChannelChatId(chatId);
        breachedAlert.setTargetChatId(UUID.randomUUID());
        breachedAlert.setStatus("OPEN");
        breachedAlert.setBreachedAt(Instant.parse("2026-03-14T09:00:00Z"));
        breachedAlert.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity openAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        openAlert.setId(UUID.randomUUID());
        openAlert.setSubscriptionId(UUID.randomUUID());
        openAlert.setChannelChatId(chatId);
        openAlert.setTargetChatId(UUID.randomUUID());
        openAlert.setStatus("OPEN");
        openAlert.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(openAlert, breachedAlert));

        var response = monetizationService.listBreachedArtifactSubscriptionAlerts(requesterId, chatId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).alertId()).isEqualTo(breachedAlert.getId());
        assertThat(response.get(0).breachedAt()).isEqualTo(breachedAlert.getBreachedAt());
    }

    @Test
    void listArtifactSubscriptionAlertQueueFiltersSeverityOwnerAndBreachState() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity breachedOwnedHigh = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        breachedOwnedHigh.setId(UUID.randomUUID());
        breachedOwnedHigh.setSubscriptionId(UUID.randomUUID());
        breachedOwnedHigh.setChannelChatId(chatId);
        breachedOwnedHigh.setTargetChatId(UUID.randomUUID());
        breachedOwnedHigh.setOwnerUserId(ownerUserId);
        breachedOwnedHigh.setSeverity("HIGH");
        breachedOwnedHigh.setStatus("OPEN");
        breachedOwnedHigh.setBreachedAt(Instant.parse("2026-03-14T08:00:00Z"));
        breachedOwnedHigh.setAcknowledgeByDueAt(Instant.parse("2026-03-14T06:00:00Z"));
        breachedOwnedHigh.setResolveByDueAt(Instant.parse("2026-03-14T07:00:00Z"));
        breachedOwnedHigh.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity ownedWarn = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        ownedWarn.setId(UUID.randomUUID());
        ownedWarn.setSubscriptionId(UUID.randomUUID());
        ownedWarn.setChannelChatId(chatId);
        ownedWarn.setTargetChatId(UUID.randomUUID());
        ownedWarn.setOwnerUserId(ownerUserId);
        ownedWarn.setSeverity("WARN");
        ownedWarn.setStatus("OPEN");
        ownedWarn.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity otherHigh = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        otherHigh.setId(UUID.randomUUID());
        otherHigh.setSubscriptionId(UUID.randomUUID());
        otherHigh.setChannelChatId(chatId);
        otherHigh.setTargetChatId(UUID.randomUUID());
        otherHigh.setSeverity("HIGH");
        otherHigh.setStatus("OPEN");
        otherHigh.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(ownedWarn, otherHigh, breachedOwnedHigh));

        var response = monetizationService.listArtifactSubscriptionAlertQueue(
                requesterId,
                chatId,
                "high",
                "open",
                ownerUserId,
                true,
                false
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).alertId()).isEqualTo(breachedOwnedHigh.getId());
    }

    @Test
    void getArtifactAlertWorkloadAggregatesOwnersAndUnassignedBacklog() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alertA1 = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alertA1.setId(UUID.randomUUID());
        alertA1.setSubscriptionId(UUID.randomUUID());
        alertA1.setChannelChatId(chatId);
        alertA1.setTargetChatId(UUID.randomUUID());
        alertA1.setOwnerUserId(ownerA);
        alertA1.setSeverity("HIGH");
        alertA1.setStatus("OPEN");
        alertA1.setBreachedAt(Instant.parse("2026-03-14T08:00:00Z"));
        alertA1.setAcknowledgeByDueAt(Instant.parse("2026-03-14T06:00:00Z"));
        alertA1.setResolveByDueAt(Instant.parse("2026-03-14T07:00:00Z"));
        alertA1.setAssignedAt(Instant.parse("2026-03-14T09:00:00Z"));
        alertA1.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity alertA2 = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alertA2.setId(UUID.randomUUID());
        alertA2.setSubscriptionId(UUID.randomUUID());
        alertA2.setChannelChatId(chatId);
        alertA2.setTargetChatId(UUID.randomUUID());
        alertA2.setOwnerUserId(ownerA);
        alertA2.setSeverity("WARN");
        alertA2.setStatus("ACKNOWLEDGED");
        alertA2.setAssignedAt(Instant.parse("2026-03-14T09:30:00Z"));
        alertA2.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity alertB = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alertB.setId(UUID.randomUUID());
        alertB.setSubscriptionId(UUID.randomUUID());
        alertB.setChannelChatId(chatId);
        alertB.setTargetChatId(UUID.randomUUID());
        alertB.setOwnerUserId(ownerB);
        alertB.setSeverity("HIGH");
        alertB.setStatus("OPEN");
        alertB.setAcknowledgeByDueAt(Instant.parse("2026-03-14T05:00:00Z"));
        alertB.setResolveByDueAt(Instant.parse("2026-03-14T06:00:00Z"));
        alertB.setAssignedAt(Instant.parse("2026-03-14T08:30:00Z"));
        alertB.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity unassigned = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        unassigned.setId(UUID.randomUUID());
        unassigned.setSubscriptionId(UUID.randomUUID());
        unassigned.setChannelChatId(chatId);
        unassigned.setTargetChatId(UUID.randomUUID());
        unassigned.setSeverity("WARN");
        unassigned.setStatus("OPEN");
        unassigned.setCreatedAt(Instant.parse("2026-03-14T12:30:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(unassigned, alertB, alertA2, alertA1));
        when(userRepository.findById(ownerA)).thenReturn(Optional.of(user(ownerA, "Owner A")));
        when(userRepository.findById(ownerB)).thenReturn(Optional.of(user(ownerB, "Owner B")));

        MonetizationArtifactAlertWorkloadResponse response = monetizationService.getArtifactAlertWorkload(requesterId, chatId);

        assertThat(response.totalAlerts()).isEqualTo(4);
        assertThat(response.openAlerts()).isEqualTo(3);
        assertThat(response.highSeverityOpenAlerts()).isEqualTo(2);
        assertThat(response.breachedAlerts()).isEqualTo(2);
        assertThat(response.overdueAlerts()).isEqualTo(2);
        assertThat(response.unassignedAlerts()).isEqualTo(1);
        assertThat(response.unassignedHighSeverityAlerts()).isZero();
        assertThat(response.assignedOwnerCount()).isEqualTo(2);
        assertThat(response.owners()).hasSize(2);
        assertThat(response.owners().stream()
                .filter(owner -> ownerA.equals(owner.ownerUserId()))
                .findFirst()
                .orElseThrow()
                .ownerDisplayName()).isEqualTo("Owner A");
        assertThat(response.owners().stream()
                .filter(owner -> ownerA.equals(owner.ownerUserId()))
                .findFirst()
                .orElseThrow()
                .breachedAlerts()).isEqualTo(1);
    }

    @Test
    void exportArtifactAlertWorkloadPersistsQueueSnapshotArtifact() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(UUID.randomUUID());
        alert.setSubscriptionId(UUID.randomUUID());
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(UUID.randomUUID());
        alert.setOwnerUserId(ownerUserId);
        alert.setSeverity("HIGH");
        alert.setStatus("OPEN");
        alert.setBreachedAt(Instant.parse("2026-03-14T08:00:00Z"));
        alert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T06:00:00Z"));
        alert.setResolveByDueAt(Instant.parse("2026-03-14T07:00:00Z"));
        alert.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(alert));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:45:00Z"));
                    return artifact;
                });

        var response = monetizationService.exportArtifactAlertWorkload(requesterId, chatId);

        assertThat(response.artifactId()).isEqualTo(artifactId);
        assertThat(response.artifactType()).isEqualTo("ALERT_WORKLOAD_EXPORT");
        assertThat(response.fileName()).isEqualTo("channel-%s-alert-workload.json".formatted(chatId));
        assertThat(response.content()).contains("\"summary\"");
        assertThat(response.content()).contains("\"ownerDisplayName\":\"Owner\"");
        assertThat(response.content()).contains(alert.getId().toString());
    }

    @Test
    void listOwnerArtifactSubscriptionAlertQueueScopesAlertsToRequestedOwner() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID otherOwnerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity ownerAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        ownerAlert.setId(UUID.randomUUID());
        ownerAlert.setSubscriptionId(UUID.randomUUID());
        ownerAlert.setChannelChatId(chatId);
        ownerAlert.setTargetChatId(UUID.randomUUID());
        ownerAlert.setOwnerUserId(ownerUserId);
        ownerAlert.setSeverity("HIGH");
        ownerAlert.setStatus("OPEN");
        ownerAlert.setBreachedAt(Instant.parse("2026-03-14T08:00:00Z"));
        ownerAlert.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity otherAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        otherAlert.setId(UUID.randomUUID());
        otherAlert.setSubscriptionId(UUID.randomUUID());
        otherAlert.setChannelChatId(chatId);
        otherAlert.setTargetChatId(UUID.randomUUID());
        otherAlert.setOwnerUserId(otherOwnerUserId);
        otherAlert.setSeverity("HIGH");
        otherAlert.setStatus("OPEN");
        otherAlert.setBreachedAt(Instant.parse("2026-03-14T08:00:00Z"));
        otherAlert.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(otherAlert, ownerAlert));

        var response = monetizationService.listOwnerArtifactSubscriptionAlertQueue(
                requesterId,
                chatId,
                ownerUserId,
                "high",
                "open",
                true,
                false
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).alertId()).isEqualTo(ownerAlert.getId());
    }

    @Test
    void getOwnerArtifactAlertWorkloadAggregatesOwnerSpecificBacklog() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity openHigh = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        openHigh.setId(UUID.randomUUID());
        openHigh.setSubscriptionId(UUID.randomUUID());
        openHigh.setChannelChatId(chatId);
        openHigh.setTargetChatId(UUID.randomUUID());
        openHigh.setOwnerUserId(ownerUserId);
        openHigh.setSeverity("HIGH");
        openHigh.setStatus("OPEN");
        openHigh.setBreachedAt(Instant.parse("2026-03-14T08:00:00Z"));
        openHigh.setAcknowledgeByDueAt(Instant.parse("2026-03-14T06:00:00Z"));
        openHigh.setResolveByDueAt(Instant.parse("2026-03-14T07:00:00Z"));
        openHigh.setAssignedAt(Instant.parse("2026-03-14T09:00:00Z"));
        openHigh.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity acknowledgedWarn = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        acknowledgedWarn.setId(UUID.randomUUID());
        acknowledgedWarn.setSubscriptionId(UUID.randomUUID());
        acknowledgedWarn.setChannelChatId(chatId);
        acknowledgedWarn.setTargetChatId(UUID.randomUUID());
        acknowledgedWarn.setOwnerUserId(ownerUserId);
        acknowledgedWarn.setSeverity("WARN");
        acknowledgedWarn.setStatus("ACKNOWLEDGED");
        acknowledgedWarn.setAssignedAt(Instant.parse("2026-03-14T09:30:00Z"));
        acknowledgedWarn.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(acknowledgedWarn, openHigh));

        var response = monetizationService.getOwnerArtifactAlertWorkload(requesterId, chatId, ownerUserId);

        assertThat(response.ownerUserId()).isEqualTo(ownerUserId);
        assertThat(response.ownerDisplayName()).isEqualTo("Owner");
        assertThat(response.totalAlerts()).isEqualTo(2);
        assertThat(response.openAlerts()).isEqualTo(1);
        assertThat(response.acknowledgedAlerts()).isEqualTo(1);
        assertThat(response.highSeverityAlerts()).isEqualTo(1);
        assertThat(response.breachedAlerts()).isEqualTo(1);
        assertThat(response.overdueAlerts()).isEqualTo(1);
    }

    @Test
    void getMyArtifactAlertWorkloadUsesCurrentUserAsOwner() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID otherOwnerUserId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity myAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        myAlert.setId(UUID.randomUUID());
        myAlert.setSubscriptionId(UUID.randomUUID());
        myAlert.setChannelChatId(chatId);
        myAlert.setTargetChatId(UUID.randomUUID());
        myAlert.setOwnerUserId(requesterId);
        myAlert.setSeverity("HIGH");
        myAlert.setStatus("OPEN");
        myAlert.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity otherAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        otherAlert.setId(UUID.randomUUID());
        otherAlert.setSubscriptionId(UUID.randomUUID());
        otherAlert.setChannelChatId(chatId);
        otherAlert.setTargetChatId(UUID.randomUUID());
        otherAlert.setOwnerUserId(otherOwnerUserId);
        otherAlert.setSeverity("WARN");
        otherAlert.setStatus("OPEN");
        otherAlert.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user(requesterId, "Me")));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(otherAlert, myAlert));

        var response = monetizationService.getMyArtifactAlertWorkload(requesterId, chatId);

        assertThat(response.ownerUserId()).isEqualTo(requesterId);
        assertThat(response.ownerDisplayName()).isEqualTo("Me");
        assertThat(response.totalAlerts()).isEqualTo(1);
        assertThat(response.highSeverityAlerts()).isEqualTo(1);
    }

    @Test
    void publishOwnerArtifactAlertWorkloadExportsAndPublishesArtifact() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID publishedMessageId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(UUID.randomUUID());
        alert.setSubscriptionId(UUID.randomUUID());
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(UUID.randomUUID());
        alert.setOwnerUserId(ownerUserId);
        alert.setSeverity("HIGH");
        alert.setStatus("OPEN");
        alert.setBreachedAt(Instant.parse("2026-03-14T08:00:00Z"));
        alert.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        final ChannelMonetizationExportArtifactEntity[] storedArtifact = new ChannelMonetizationExportArtifactEntity[1];

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, targetChatId)).thenReturn(channel(targetChatId, requesterId));
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(alert));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:45:00Z"));
                    storedArtifact[0] = artifact;
                    return artifact;
                });
        when(channelMonetizationExportArtifactRepository.findById(artifactId))
                .thenAnswer(invocation -> Optional.ofNullable(storedArtifact[0]));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(publishedMessageId));
        when(channelMonetizationArtifactPublicationRepository.save(any(ChannelMonetizationArtifactPublicationEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactPublicationEntity publication = invocation.getArgument(0);
                    publication.setId(publicationId);
                    publication.setPublishedAt(Instant.parse("2026-03-14T12:46:00Z"));
                    return publication;
                });

        var response = monetizationService.publishOwnerArtifactAlertWorkload(
                requesterId,
                chatId,
                ownerUserId,
                new PublishMonetizationArtifactRequest(targetChatId, "owner workload")
        );

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(requesterId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(targetChatId);
        assertThat(requestCaptor.getValue().text()).contains("owner workload");
        assertThat(response.publicationId()).isEqualTo(publicationId);
        assertThat(response.artifactId()).isEqualTo(artifactId);
        assertThat(response.publishedMessageId()).isEqualTo(publishedMessageId);
    }

    @Test
    void exportOwnerArtifactAlertReminderQueuePersistsArtifact() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity dueAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        dueAlert.setId(UUID.randomUUID());
        dueAlert.setSubscriptionId(UUID.randomUUID());
        dueAlert.setChannelChatId(chatId);
        dueAlert.setTargetChatId(UUID.randomUUID());
        dueAlert.setOwnerUserId(ownerUserId);
        dueAlert.setSeverity("HIGH");
        dueAlert.setStatus("OPEN");
        dueAlert.setBreachedAt(Instant.parse("2026-03-14T08:00:00Z"));
        dueAlert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        dueAlert.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        dueAlert.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity otherOwnerAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        otherOwnerAlert.setId(UUID.randomUUID());
        otherOwnerAlert.setSubscriptionId(UUID.randomUUID());
        otherOwnerAlert.setChannelChatId(chatId);
        otherOwnerAlert.setTargetChatId(UUID.randomUUID());
        otherOwnerAlert.setOwnerUserId(UUID.randomUUID());
        otherOwnerAlert.setSeverity("HIGH");
        otherOwnerAlert.setStatus("OPEN");
        otherOwnerAlert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        otherOwnerAlert.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        otherOwnerAlert.setCreatedAt(Instant.parse("2026-03-14T09:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(userRepository.findById(ownerUserId)).thenReturn(Optional.of(user(ownerUserId, "Owner")));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(otherOwnerAlert, dueAlert));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:45:00Z"));
                    return artifact;
                });

        var response = monetizationService.exportOwnerArtifactAlertReminderQueue(
                requesterId,
                chatId,
                ownerUserId,
                "high",
                false
        );

        assertThat(response.artifactId()).isEqualTo(artifactId);
        assertThat(response.rowCount()).isEqualTo(1);
        assertThat(response.totalUnits()).isEqualTo(1);
        assertThat(response.fileName()).contains("reminder-queue");
    }

    @Test
    void publishMyArtifactAlertReminderQueueExportsAndPublishesArtifact() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID publishedMessageId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity dueAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        dueAlert.setId(UUID.randomUUID());
        dueAlert.setSubscriptionId(UUID.randomUUID());
        dueAlert.setChannelChatId(chatId);
        dueAlert.setTargetChatId(UUID.randomUUID());
        dueAlert.setOwnerUserId(requesterId);
        dueAlert.setSeverity("HIGH");
        dueAlert.setStatus("OPEN");
        dueAlert.setAcknowledgeByDueAt(Instant.parse("2026-03-14T01:00:00Z"));
        dueAlert.setResolveByDueAt(Instant.parse("2026-03-14T02:00:00Z"));
        dueAlert.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        final ChannelMonetizationExportArtifactEntity[] storedArtifact = new ChannelMonetizationExportArtifactEntity[1];

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, targetChatId)).thenReturn(channel(targetChatId, requesterId));
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(user(requesterId, "Me")));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(dueAlert));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:45:00Z"));
                    storedArtifact[0] = artifact;
                    return artifact;
                });
        when(channelMonetizationExportArtifactRepository.findById(artifactId))
                .thenAnswer(invocation -> Optional.ofNullable(storedArtifact[0]));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(publishedMessageId));
        when(channelMonetizationArtifactPublicationRepository.save(any(ChannelMonetizationArtifactPublicationEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactPublicationEntity publication = invocation.getArgument(0);
                    publication.setId(publicationId);
                    publication.setPublishedAt(Instant.parse("2026-03-14T12:46:00Z"));
                    return publication;
                });

        var response = monetizationService.publishMyArtifactAlertReminderQueue(
                requesterId,
                chatId,
                "high",
                false,
                new PublishMonetizationArtifactRequest(targetChatId, "reminder queue")
        );

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(requesterId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(targetChatId);
        assertThat(requestCaptor.getValue().text()).contains("reminder queue");
        assertThat(response.publicationId()).isEqualTo(publicationId);
        assertThat(response.artifactId()).isEqualTo(artifactId);
        assertThat(response.publishedMessageId()).isEqualTo(publishedMessageId);
    }

    @Test
    void processArtifactSubscriptionsSuppressesRepeatedAlertWithinWindow() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(targetChatId);
        subscription.setCreatedByUserId(requesterId);
        subscription.setArtifactType("BROKEN_EXPORT");
        subscription.setStatus("ACTIVE");
        subscription.setAutoGenerate(true);
        subscription.setMinIntervalMinutes(30);
        subscription.setAlertSuppressionMinutes(180);
        subscription.setConsecutiveFailureCount(4);
        subscription.setLastAlertedAt(Instant.parse("2026-03-14T11:30:00Z"));
        subscription.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity openAlert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        openAlert.setId(UUID.randomUUID());
        openAlert.setSubscriptionId(subscriptionId);
        openAlert.setChannelChatId(chatId);
        openAlert.setTargetChatId(targetChatId);
        openAlert.setSeverity("WARN");
        openAlert.setFailureCount(4);
        openAlert.setLastFailureReason("old failure");
        openAlert.setStatus("OPEN");

        when(channelMonetizationArtifactSubscriptionRepository.lockActiveBatch(any(), eq(20))).thenReturn(List.of(subscription));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findFirstBySubscriptionIdOrderByCreatedAtDesc(subscriptionId))
                .thenReturn(Optional.of(openAlert));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactSubscriptionFailureRepository.save(any(ChannelMonetizationArtifactSubscriptionFailureEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactSubscriptionRepository.save(any(ChannelMonetizationArtifactSubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int processed = monetizationService.processArtifactSubscriptions(Instant.parse("2026-03-14T12:00:00Z"), 20);

        assertThat(processed).isZero();
        assertThat(subscription.getConsecutiveFailureCount()).isEqualTo(5);
        assertThat(subscription.getEscalationStatus()).isEqualTo("SUPPRESSED");
        verify(messageService, org.mockito.Mockito.never()).sendMessage(eq(requesterId), any(SendMessageRequest.class));
    }

    @Test
    void processArtifactSubscriptionsUsesAlertPolicyThresholdAndRouting() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID alertTargetChatId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID alertMessageId = UUID.randomUUID();
        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(subscriptionId);
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(targetChatId);
        subscription.setCreatedByUserId(requesterId);
        subscription.setArtifactType("BROKEN_EXPORT");
        subscription.setStatus("ACTIVE");
        subscription.setAutoGenerate(true);
        subscription.setMinIntervalMinutes(30);
        subscription.setConsecutiveFailureCount(1);
        subscription.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));

        ChannelMonetizationAlertPolicyEntity policy = new ChannelMonetizationAlertPolicyEntity();
        policy.setChannelChatId(chatId);
        policy.setAlertThreshold(2);
        policy.setHighSeverityThreshold(2);
        policy.setAlertSuppressionMinutes(60);
        policy.setAutoDigestEnabled(true);
        policy.setAlertTargetChatId(alertTargetChatId);

        when(channelMonetizationArtifactSubscriptionRepository.lockActiveBatch(any(), eq(20))).thenReturn(List.of(subscription));
        when(channelMonetizationAlertPolicyRepository.findById(chatId)).thenReturn(Optional.of(policy));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findFirstBySubscriptionIdOrderByCreatedAtDesc(subscriptionId))
                .thenReturn(Optional.empty());
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactSubscriptionFailureRepository.save(any(ChannelMonetizationArtifactSubscriptionFailureEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactSubscriptionRepository.save(any(ChannelMonetizationArtifactSubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(alertMessageId));

        int processed = monetizationService.processArtifactSubscriptions(Instant.parse("2026-03-14T12:00:00Z"), 20);

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(requesterId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(alertTargetChatId);
        assertThat(processed).isZero();
        assertThat(subscription.getEscalationStatus()).isEqualTo("OPEN");
    }

    @Test
    void generateAlertDigestCreatesArtifactRunAndPublishesDigest() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID publishedMessageId = UUID.randomUUID();
        UUID digestRunId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(UUID.randomUUID());
        alert.setSubscriptionId(UUID.randomUUID());
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(targetChatId);
        alert.setSeverity("WARN");
        alert.setFailureCount(3);
        alert.setLastFailureReason("delivery failed");
        alert.setStatus("OPEN");
        alert.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
        ChannelMonetizationArtifactSubscriptionFailureEntity failure = new ChannelMonetizationArtifactSubscriptionFailureEntity();
        failure.setId(UUID.randomUUID());
        failure.setSubscriptionId(alert.getSubscriptionId());
        failure.setChannelChatId(chatId);
        failure.setTargetChatId(targetChatId);
        failure.setArtifactType("REPORT_EXPORT");
        failure.setAttemptNumber(3);
        failure.setFailureReason("delivery failed");
        failure.setFailedAt(Instant.parse("2026-03-14T12:01:00Z"));
        final ChannelMonetizationExportArtifactEntity[] storedArtifact = new ChannelMonetizationExportArtifactEntity[1];

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, targetChatId)).thenReturn(channel(targetChatId, requesterId));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(alert));
        when(channelMonetizationArtifactSubscriptionFailureRepository.findAllByChannelChatIdOrderByFailedAtDesc(chatId))
                .thenReturn(List.of(failure));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:02:00Z"));
                    storedArtifact[0] = artifact;
                    return artifact;
                });
        when(channelMonetizationExportArtifactRepository.findById(artifactId))
                .thenAnswer(invocation -> Optional.ofNullable(storedArtifact[0]));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(publishedMessageId));
        when(channelMonetizationArtifactPublicationRepository.save(any(ChannelMonetizationArtifactPublicationEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactPublicationEntity publication = invocation.getArgument(0);
                    publication.setId(publicationId);
                    publication.setPublishedAt(Instant.parse("2026-03-14T12:03:00Z"));
                    return publication;
                });
        when(channelMonetizationAlertDigestRunRepository.save(any(ChannelMonetizationAlertDigestRunEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationAlertDigestRunEntity run = invocation.getArgument(0);
                    run.setId(digestRunId);
                    run.setCreatedAt(Instant.parse("2026-03-14T12:04:00Z"));
                    return run;
                });

        var response = monetizationService.generateAlertDigest(
                requesterId,
                chatId,
                new GenerateMonetizationAlertDigestRequest(targetChatId, "digest note")
        );

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(requesterId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(targetChatId);
        assertThat(requestCaptor.getValue().text()).contains("Monetization artifact: ALERT_DIGEST_EXPORT");
        assertThat(requestCaptor.getValue().text()).contains("digest note");
        assertThat(response.alertDigestRunId()).isEqualTo(digestRunId);
        assertThat(response.openAlertCount()).isEqualTo(1);
        assertThat(response.affectedSubscriptionCount()).isEqualTo(1);
        assertThat(response.artifactId()).isEqualTo(artifactId);
        assertThat(response.publishedMessageId()).isEqualTo(publishedMessageId);
    }

    @Test
    void processAlertDigestsUsesConfiguredDigestTargetAndRespectsAutoDigestPolicy() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID alertTargetChatId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID publishedMessageId = UUID.randomUUID();
        UUID digestRunId = UUID.randomUUID();

        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(targetChatId);
        subscription.setCreatedByUserId(requesterId);
        subscription.setArtifactType("REPORT_EXPORT");
        subscription.setStatus("ACTIVE");
        subscription.setEscalationStatus("SUPPRESSED");
        subscription.setLastFailureAt(Instant.parse("2026-03-14T10:00:00Z"));
        subscription.setLastAlertedAt(Instant.parse("2026-03-14T03:00:00Z"));
        subscription.setCreatedAt(Instant.parse("2026-03-14T09:00:00Z"));
        subscription.setUpdatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(UUID.randomUUID());
        alert.setSubscriptionId(subscription.getId());
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(alertTargetChatId);
        alert.setSeverity("WARN");
        alert.setFailureCount(3);
        alert.setLastFailureReason("delivery failed");
        alert.setStatus("OPEN");
        ChannelMonetizationArtifactSubscriptionFailureEntity failure = new ChannelMonetizationArtifactSubscriptionFailureEntity();
        failure.setId(UUID.randomUUID());
        failure.setSubscriptionId(subscription.getId());
        failure.setChannelChatId(chatId);
        failure.setTargetChatId(targetChatId);
        failure.setArtifactType("REPORT_EXPORT");
        failure.setAttemptNumber(3);
        failure.setFailureReason("delivery failed");
        failure.setFailedAt(Instant.parse("2026-03-14T10:01:00Z"));
        ChannelMonetizationAlertPolicyEntity policy = new ChannelMonetizationAlertPolicyEntity();
        policy.setChannelChatId(chatId);
        policy.setAutoDigestEnabled(true);
        policy.setDigestTargetChatId(alertTargetChatId);
        final ChannelMonetizationExportArtifactEntity[] storedArtifact = new ChannelMonetizationExportArtifactEntity[1];

        when(channelMonetizationArtifactSubscriptionRepository.lockEscalatedBatch(any(), eq(20))).thenReturn(List.of(subscription));
        when(channelMonetizationAlertPolicyRepository.findById(chatId)).thenReturn(Optional.of(policy));
        when(chatService.getChat(chatId)).thenReturn(channel(chatId, requesterId));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(alert));
        when(channelMonetizationArtifactSubscriptionFailureRepository.findAllByChannelChatIdOrderByFailedAtDesc(chatId))
                .thenReturn(List.of(failure));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:02:00Z"));
                    storedArtifact[0] = artifact;
                    return artifact;
                });
        when(channelMonetizationExportArtifactRepository.findById(artifactId))
                .thenAnswer(invocation -> Optional.ofNullable(storedArtifact[0]));
        when(messageService.sendMessage(any(), any(SendMessageRequest.class))).thenReturn(message(publishedMessageId));
        when(channelMonetizationArtifactPublicationRepository.save(any(ChannelMonetizationArtifactPublicationEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactPublicationEntity publication = invocation.getArgument(0);
                    publication.setId(publicationId);
                    publication.setPublishedAt(Instant.parse("2026-03-14T12:03:00Z"));
                    return publication;
                });
        when(channelMonetizationAlertDigestRunRepository.save(any(ChannelMonetizationAlertDigestRunEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationAlertDigestRunEntity run = invocation.getArgument(0);
                    run.setId(digestRunId);
                    run.setCreatedAt(Instant.parse("2026-03-14T12:04:00Z"));
                    return run;
                });
        when(channelMonetizationArtifactSubscriptionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int processed = monetizationService.processAlertDigests(Instant.parse("2026-03-14T12:00:00Z"), 20);

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(any(), requestCaptor.capture());
        assertThat(requestCaptor.getValue().chatId()).isEqualTo(alertTargetChatId);
        assertThat(processed).isEqualTo(1);
        assertThat(subscription.getEscalationStatus()).isEqualTo("OPEN");
        assertThat(subscription.getLastAlertedAt()).isNotNull();
    }

    @Test
    void generateAlertDigestReopensExpiredSnoozedAlert() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID publishedMessageId = UUID.randomUUID();
        UUID digestRunId = UUID.randomUUID();
        ChatEntity channel = channel(chatId, requesterId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
        alert.setId(UUID.randomUUID());
        alert.setSubscriptionId(UUID.randomUUID());
        alert.setChannelChatId(chatId);
        alert.setTargetChatId(targetChatId);
        alert.setSeverity("WARN");
        alert.setFailureCount(3);
        alert.setLastFailureReason("delivery failed");
        alert.setStatus("SNOOZED");
        alert.setSnoozedUntil(Instant.parse("2026-03-14T01:00:00Z"));
        alert.setCreatedAt(Instant.parse("2026-03-14T00:00:00Z"));
        ChannelMonetizationArtifactSubscriptionFailureEntity failure = new ChannelMonetizationArtifactSubscriptionFailureEntity();
        failure.setId(UUID.randomUUID());
        failure.setSubscriptionId(alert.getSubscriptionId());
        failure.setChannelChatId(chatId);
        failure.setTargetChatId(targetChatId);
        failure.setArtifactType("REPORT_EXPORT");
        failure.setAttemptNumber(3);
        failure.setFailureReason("delivery failed");
        failure.setFailedAt(Instant.parse("2026-03-14T00:30:00Z"));
        final ChannelMonetizationExportArtifactEntity[] storedArtifact = new ChannelMonetizationExportArtifactEntity[1];

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(chatService.getOwnedChat(requesterId, targetChatId)).thenReturn(channel(targetChatId, requesterId));
        when(channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(alert));
        when(channelMonetizationArtifactSubscriptionAlertRepository.save(any(ChannelMonetizationArtifactSubscriptionAlertEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelMonetizationArtifactSubscriptionFailureRepository.findAllByChannelChatIdOrderByFailedAtDesc(chatId))
                .thenReturn(List.of(failure));
        when(channelMonetizationExportArtifactRepository.save(any(ChannelMonetizationExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationExportArtifactEntity artifact = invocation.getArgument(0);
                    artifact.setId(artifactId);
                    artifact.setCreatedAt(Instant.parse("2026-03-14T12:02:00Z"));
                    storedArtifact[0] = artifact;
                    return artifact;
                });
        when(channelMonetizationExportArtifactRepository.findById(artifactId))
                .thenAnswer(invocation -> Optional.ofNullable(storedArtifact[0]));
        when(messageService.sendMessage(eq(requesterId), any(SendMessageRequest.class))).thenReturn(message(publishedMessageId));
        when(channelMonetizationArtifactPublicationRepository.save(any(ChannelMonetizationArtifactPublicationEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationArtifactPublicationEntity publication = invocation.getArgument(0);
                    publication.setId(publicationId);
                    publication.setPublishedAt(Instant.parse("2026-03-14T12:03:00Z"));
                    return publication;
                });
        when(channelMonetizationAlertDigestRunRepository.save(any(ChannelMonetizationAlertDigestRunEntity.class)))
                .thenAnswer(invocation -> {
                    ChannelMonetizationAlertDigestRunEntity run = invocation.getArgument(0);
                    run.setId(digestRunId);
                    run.setCreatedAt(Instant.parse("2026-03-14T12:04:00Z"));
                    return run;
                });

        var response = monetizationService.generateAlertDigest(
                requesterId,
                chatId,
                new GenerateMonetizationAlertDigestRequest(targetChatId, "digest note")
        );

        assertThat(response.alertDigestRunId()).isEqualTo(digestRunId);
        assertThat(alert.getStatus()).isEqualTo("OPEN");
        assertThat(alert.getSnoozedUntil()).isNull();
    }

    private SponsoredMessageEntity sponsoredMessage(
            UUID sponsoredMessageId,
            UUID chatId,
            UUID sponsorUserId,
            UUID createdByUserId,
            long budgetUnits,
            long impressionCost,
            long clickCost
    ) {
        SponsoredMessageEntity entity = new SponsoredMessageEntity();
        entity.setId(sponsoredMessageId);
        entity.setChannelChatId(chatId);
        entity.setSponsorUserId(sponsorUserId);
        entity.setCreatedByUserId(createdByUserId);
        entity.setTitle("Sponsored");
        entity.setMessageText("Body");
        entity.setBudgetUnits(budgetUnits);
        entity.setSpentUnits(0L);
        entity.setEarnedUnits(0L);
        entity.setSettledUnits(0L);
        entity.setCostPerImpressionUnits(impressionCost);
        entity.setCostPerClickUnits(clickCost);
        entity.setStatus("DRAFT");
        entity.setCreatedAt(Instant.parse("2026-03-14T09:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-03-14T09:00:00Z"));
        return entity;
    }

    private SponsoredMessageEventEntity event(UUID sponsoredMessageId, UUID viewerUserId, String eventType, long costUnits) {
        SponsoredMessageEventEntity event = new SponsoredMessageEventEntity();
        event.setId(UUID.randomUUID());
        event.setSponsoredMessageId(sponsoredMessageId);
        event.setViewerUserId(viewerUserId);
        event.setEventType(eventType);
        event.setCostUnits(costUnits);
        event.setCreatedAt(Instant.parse("2026-03-14T14:00:00Z"));
        return event;
    }

    private ChatEntity channel(UUID chatId, UUID createdBy) {
        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("CHANNEL");
        chat.setCreatedBy(createdBy);
        return chat;
    }

    private UserEntity user(UUID userId, String displayName) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setDisplayName(displayName);
        user.setPhoneNumber("+1234567890");
        return user;
    }

    private ChatMessageResponse message(UUID messageId) {
        return new ChatMessageResponse(
                UUID.randomUUID(),
                messageId,
                null,
                UUID.randomUUID(),
                "Bot",
                null,
                null,
                false,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                0,
                "hello",
                List.of(),
                "TEXT",
                null,
                false,
                null,
                null,
                null,
                Instant.parse("2026-03-14T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                "SENT",
                null,
                null,
                null,
                null,
                null
        );
    }
}
