package com.alex.messenger.premium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.premium.dto.ActivatePremiumTrialRequest;
import com.alex.messenger.premium.dto.BoostChannelRequest;
import com.alex.messenger.premium.dto.SendPremiumGiftRequest;
import com.alex.messenger.premium.dto.UpdateEmojiStatusRequest;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PremiumServiceTest {

    @Mock
    private PremiumEntitlementRepository premiumEntitlementRepository;

    @Mock
    private PremiumCustomEmojiRepository premiumCustomEmojiRepository;

    @Mock
    private PremiumGiftRepository premiumGiftRepository;

    @Mock
    private ChannelBoostRepository channelBoostRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatService chatService;

    private PremiumService premiumService;

    @BeforeEach
    void setUp() {
        premiumService = new PremiumService(
                premiumEntitlementRepository,
                premiumCustomEmojiRepository,
                premiumGiftRepository,
                channelBoostRepository,
                userRepository,
                chatService
        );
    }

    @Test
    void activateTrialExtendsPremiumEntitlement() {
        UUID userId = UUID.randomUUID();
        UserEntity user = user(userId, "Premium");
        PremiumEntitlementEntity entitlement = entitlement(userId, null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(premiumEntitlementRepository.findById(userId)).thenReturn(Optional.of(entitlement));
        when(premiumEntitlementRepository.save(any(PremiumEntitlementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = premiumService.activateTrial(userId, new ActivatePremiumTrialRequest(15));

        assertThat(response.active()).isTrue();
        assertThat(response.activeUntil()).isAfter(Instant.now());
    }

    @Test
    void updateEmojiStatusRequiresPremium() {
        UUID userId = UUID.randomUUID();
        PremiumEntitlementEntity entitlement = entitlement(userId, Instant.now().minusSeconds(60));

        when(premiumEntitlementRepository.findById(userId)).thenReturn(Optional.of(entitlement));

        assertThatThrownBy(() -> premiumService.updateEmojiStatus(userId, new UpdateEmojiStatusRequest(UUID.randomUUID())))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void sendGiftExtendsRecipientPremium() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID emojiId = UUID.randomUUID();

        when(userRepository.findById(senderId)).thenReturn(Optional.of(user(senderId, "Sender")));
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(user(recipientId, "Recipient")));
        when(premiumEntitlementRepository.findById(senderId)).thenReturn(Optional.of(entitlement(senderId, Instant.now().plusSeconds(3600))));
        when(premiumEntitlementRepository.findById(recipientId)).thenReturn(Optional.of(entitlement(recipientId, null)));
        when(premiumCustomEmojiRepository.findById(emojiId)).thenReturn(Optional.of(customEmoji(emojiId)));
        when(premiumGiftRepository.save(any(PremiumGiftEntity.class))).thenAnswer(invocation -> {
            PremiumGiftEntity gift = invocation.getArgument(0);
            gift.setId(UUID.randomUUID());
            gift.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
            return gift;
        });
        when(premiumEntitlementRepository.save(any(PremiumEntitlementEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findAllById(any())).thenReturn(List.of(user(senderId, "Sender"), user(recipientId, "Recipient")));
        when(premiumCustomEmojiRepository.findAllById(any())).thenReturn(List.of(customEmoji(emojiId)));

        var response = premiumService.sendGift(
                senderId,
                new SendPremiumGiftRequest(recipientId, emojiId, "Enjoy", 30)
        );

        assertThat(response.recipientUserId()).isEqualTo(recipientId);
        assertThat(response.customEmojiId()).isEqualTo(emojiId);
        assertThat(response.premiumDaysGranted()).isEqualTo(30);
    }

    @Test
    void boostChannelRequiresPremiumAndReturnsStats() {
        UUID userId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        ChatEntity channel = channel(channelId);
        PremiumEntitlementEntity entitlement = entitlement(userId, Instant.now().plusSeconds(3600));

        when(premiumEntitlementRepository.findById(userId)).thenReturn(Optional.of(entitlement));
        when(chatService.getOwnedChat(userId, channelId)).thenReturn(channel);
        when(channelBoostRepository.findByChannelChatIdAndBoostedByUserId(channelId, userId)).thenReturn(Optional.empty());
        when(channelBoostRepository.save(any(ChannelBoostEntity.class))).thenAnswer(invocation -> {
            ChannelBoostEntity boost = invocation.getArgument(0);
            boost.setId(UUID.randomUUID());
            boost.setUpdatedAt(Instant.parse("2026-03-14T12:00:00Z"));
            return boost;
        });
        when(channelBoostRepository.findAllByChannelChatIdOrderByUpdatedAtDesc(channelId)).thenAnswer(invocation -> {
            ChannelBoostEntity boost = new ChannelBoostEntity();
            boost.setId(UUID.randomUUID());
            boost.setChannelChatId(channelId);
            boost.setBoostedByUserId(userId);
            boost.setBoostCount(3);
            boost.setUpdatedAt(Instant.parse("2026-03-14T12:00:00Z"));
            return List.of(boost);
        });

        var response = premiumService.boostChannel(userId, channelId, new BoostChannelRequest(3));

        assertThat(response.channelChatId()).isEqualTo(channelId);
        assertThat(response.totalBoosts()).isEqualTo(3);
        assertThat(response.viewerBoostCount()).isEqualTo(3);
    }

    private PremiumEntitlementEntity entitlement(UUID userId, Instant activeUntil) {
        PremiumEntitlementEntity entity = new PremiumEntitlementEntity();
        entity.setUserId(userId);
        entity.setTier("PREMIUM");
        entity.setActiveUntil(activeUntil);
        return entity;
    }

    private PremiumCustomEmojiEntity customEmoji(UUID emojiId) {
        PremiumCustomEmojiEntity emoji = new PremiumCustomEmojiEntity();
        emoji.setId(emojiId);
        emoji.setShortCode("premium_crown");
        emoji.setEmoji("👑");
        emoji.setLabel("Premium Crown");
        emoji.setPremiumRequired(true);
        return emoji;
    }

    private UserEntity user(UUID userId, String displayName) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setDisplayName(displayName);
        return user;
    }

    private ChatEntity channel(UUID chatId) {
        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("CHANNEL");
        return chat;
    }
}
