package com.alex.messenger.monetization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.SendMessageRequest;
import com.alex.messenger.monetization.dto.CreateSponsoredMessageRequest;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.time.Instant;
import java.util.List;
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
    private ChatService chatService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageService messageService;

    private MonetizationService monetizationService;

    @BeforeEach
    void setUp() {
        monetizationService = new MonetizationService(
                sponsoredMessageRepository,
                sponsoredMessageEventRepository,
                chatService,
                userRepository,
                messageService
        );
    }

    @Test
    void publishSponsoredMessageCreatesChannelMessageAndActivatesCampaign() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID sponsoredMessageId = UUID.randomUUID();
        UUID deliveredMessageId = UUID.randomUUID();
        ChatEntity channel = channel(chatId);
        SponsoredMessageEntity sponsoredMessage = sponsoredMessage(
                sponsoredMessageId,
                chatId,
                UUID.randomUUID(),
                requesterId,
                100,
                1,
                5
        );
        sponsoredMessage.setTitle("Launch");
        sponsoredMessage.setMessageText("Sponsored body");
        sponsoredMessage.setCallToActionLabel("Open");
        sponsoredMessage.setCallToActionUrl("https://example.com/launch");

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
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
        ChatEntity channel = channel(chatId);
        SponsoredMessageEntity sponsoredMessage = sponsoredMessage(
                sponsoredMessageId,
                chatId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                100,
                7,
                20
        );
        sponsoredMessage.setStatus("ACTIVE");
        sponsoredMessage.setPublishedAt(Instant.parse("2026-03-14T10:00:00Z"));
        AtomicBoolean impressionRecorded = new AtomicBoolean(false);

        when(chatService.getOwnedChat(viewerId, chatId)).thenReturn(channel);
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
        when(sponsoredMessageEventRepository.countBySponsoredMessageIdAndEventType(sponsoredMessageId, "IMPRESSION"))
                .thenAnswer(invocation -> impressionRecorded.get() ? 1L : 0L);
        when(sponsoredMessageEventRepository.countBySponsoredMessageIdAndEventType(sponsoredMessageId, "CLICK")).thenReturn(0L);

        var first = monetizationService.recordImpression(viewerId, chatId, sponsoredMessageId);
        var second = monetizationService.recordImpression(viewerId, chatId, sponsoredMessageId);

        assertThat(first.spentUnits()).isEqualTo(7L);
        assertThat(first.impressionsCount()).isEqualTo(1L);
        assertThat(second.spentUnits()).isEqualTo(7L);
        assertThat(second.impressionsCount()).isEqualTo(1L);
        assertThat(sponsoredMessage.getSpentUnits()).isEqualTo(7L);
    }

    @Test
    void recordClickCompletesCampaignWhenBudgetExhausted() {
        UUID viewerId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID sponsoredMessageId = UUID.randomUUID();
        ChatEntity channel = channel(chatId);
        SponsoredMessageEntity sponsoredMessage = sponsoredMessage(
                sponsoredMessageId,
                chatId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                25,
                2,
                5
        );
        sponsoredMessage.setSpentUnits(20L);
        sponsoredMessage.setStatus("ACTIVE");
        sponsoredMessage.setPublishedAt(Instant.parse("2026-03-14T10:00:00Z"));

        when(chatService.getOwnedChat(viewerId, chatId)).thenReturn(channel);
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
        ChatEntity channel = channel(chatId);
        SponsoredMessageEntity draft = sponsoredMessage(UUID.randomUUID(), chatId, UUID.randomUUID(), requesterId, 100, 1, 5);
        draft.setStatus("DRAFT");
        SponsoredMessageEntity active = sponsoredMessage(UUID.randomUUID(), chatId, UUID.randomUUID(), requesterId, 120, 2, 6);
        active.setStatus("ACTIVE");
        active.setSpentUnits(30L);
        active.setPublishedAt(Instant.parse("2026-03-14T11:00:00Z"));
        SponsoredMessageEntity paused = sponsoredMessage(UUID.randomUUID(), chatId, UUID.randomUUID(), requesterId, 80, 1, 4);
        paused.setStatus("PAUSED");
        paused.setSpentUnits(12L);
        paused.setPublishedAt(Instant.parse("2026-03-14T12:00:00Z"));
        SponsoredMessageEntity completed = sponsoredMessage(UUID.randomUUID(), chatId, UUID.randomUUID(), requesterId, 50, 1, 5);
        completed.setStatus("COMPLETED");
        completed.setSpentUnits(50L);
        completed.setPublishedAt(Instant.parse("2026-03-14T13:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(channel);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(sponsoredMessageRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(completed, paused, active, draft));
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
        assertThat(response.publishedCount()).isEqualTo(3);
        assertThat(response.totalBudgetUnits()).isEqualTo(350L);
        assertThat(response.totalSpentUnits()).isEqualTo(92L);
        assertThat(response.remainingBudgetUnits()).isEqualTo(258L);
        assertThat(response.impressionsCount()).isEqualTo(2L);
        assertThat(response.clicksCount()).isEqualTo(2L);
        assertThat(response.clickThroughRatePercent()).isEqualTo(100.0d);
    }

    @Test
    void createSponsoredMessageRejectsExpiredActivityWindow() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatEntity channel = channel(chatId);

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

    private ChatEntity channel(UUID chatId) {
        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("CHANNEL");
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
