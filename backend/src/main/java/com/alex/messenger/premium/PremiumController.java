package com.alex.messenger.premium;

import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.premium.dto.ActivatePremiumTrialRequest;
import com.alex.messenger.premium.dto.BoostChannelRequest;
import com.alex.messenger.premium.dto.ChannelBoostStatsResponse;
import com.alex.messenger.premium.dto.PremiumCustomEmojiResponse;
import com.alex.messenger.premium.dto.PremiumGiftResponse;
import com.alex.messenger.premium.dto.PremiumProfileResponse;
import com.alex.messenger.premium.dto.SendPremiumGiftRequest;
import com.alex.messenger.premium.dto.UpdateEmojiStatusRequest;
import com.alex.messenger.shared.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/premium")
@RequiredArgsConstructor
public class PremiumController {

    private final FeatureFlagService featureFlagService;
    private final PremiumService premiumService;

    @GetMapping("/me")
    public ResponseEntity<PremiumProfileResponse> me() {
        featureFlagService.requirePremiumEnabled();
        return ResponseEntity.ok(premiumService.getProfile(CurrentUser.id()));
    }

    @PostMapping("/me/activate-trial")
    public ResponseEntity<PremiumProfileResponse> activateTrial(
            @RequestBody(required = false) ActivatePremiumTrialRequest request
    ) {
        featureFlagService.requirePremiumEnabled();
        return ResponseEntity.ok(premiumService.activateTrial(CurrentUser.id(), request));
    }

    @PutMapping("/me/emoji-status")
    public ResponseEntity<PremiumProfileResponse> updateEmojiStatus(
            @RequestBody(required = false) UpdateEmojiStatusRequest request
    ) {
        featureFlagService.requirePremiumEnabled();
        return ResponseEntity.ok(premiumService.updateEmojiStatus(CurrentUser.id(), request));
    }

    @GetMapping("/custom-emojis")
    public ResponseEntity<List<PremiumCustomEmojiResponse>> customEmojis() {
        featureFlagService.requirePremiumEnabled();
        return ResponseEntity.ok(premiumService.listCustomEmojis(CurrentUser.id()));
    }

    @GetMapping("/gifts/received")
    public ResponseEntity<List<PremiumGiftResponse>> receivedGifts() {
        featureFlagService.requirePremiumEnabled();
        return ResponseEntity.ok(premiumService.listReceivedGifts(CurrentUser.id()));
    }

    @GetMapping("/gifts/sent")
    public ResponseEntity<List<PremiumGiftResponse>> sentGifts() {
        featureFlagService.requirePremiumEnabled();
        return ResponseEntity.ok(premiumService.listSentGifts(CurrentUser.id()));
    }

    @PostMapping("/gifts")
    public ResponseEntity<PremiumGiftResponse> sendGift(@Valid @RequestBody SendPremiumGiftRequest request) {
        featureFlagService.requirePremiumEnabled();
        return ResponseEntity.ok(premiumService.sendGift(CurrentUser.id(), request));
    }

    @GetMapping("/channels/{chatId}/boosts")
    public ResponseEntity<ChannelBoostStatsResponse> channelBoosts(@PathVariable UUID chatId) {
        featureFlagService.requirePremiumEnabled();
        return ResponseEntity.ok(premiumService.getChannelBoostStats(CurrentUser.id(), chatId));
    }

    @PostMapping("/channels/{chatId}/boosts")
    public ResponseEntity<ChannelBoostStatsResponse> boostChannel(
            @PathVariable UUID chatId,
            @RequestBody(required = false) BoostChannelRequest request
    ) {
        featureFlagService.requirePremiumEnabled();
        return ResponseEntity.ok(premiumService.boostChannel(CurrentUser.id(), chatId, request));
    }
}
