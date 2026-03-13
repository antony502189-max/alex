package com.alex.messenger.monetization;

import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.monetization.dto.ChannelMonetizationStatsResponse;
import com.alex.messenger.monetization.dto.CreateSponsoredMessageRequest;
import com.alex.messenger.monetization.dto.SponsoredMessageResponse;
import com.alex.messenger.shared.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monetization")
@RequiredArgsConstructor
public class MonetizationController {

    private final FeatureFlagService featureFlagService;
    private final MonetizationService monetizationService;

    @GetMapping("/channels/{chatId}/sponsored-messages")
    public ResponseEntity<List<SponsoredMessageResponse>> sponsoredMessages(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listSponsoredMessages(CurrentUser.id(), chatId));
    }

    @PostMapping("/channels/{chatId}/sponsored-messages")
    public ResponseEntity<SponsoredMessageResponse> createSponsoredMessage(
            @PathVariable UUID chatId,
            @Valid @RequestBody CreateSponsoredMessageRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.createSponsoredMessage(CurrentUser.id(), chatId, request));
    }

    @PostMapping("/channels/{chatId}/sponsored-messages/{sponsoredMessageId}/publish")
    public ResponseEntity<SponsoredMessageResponse> publishSponsoredMessage(
            @PathVariable UUID chatId,
            @PathVariable UUID sponsoredMessageId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.publishSponsoredMessage(CurrentUser.id(), chatId, sponsoredMessageId));
    }

    @PostMapping("/channels/{chatId}/sponsored-messages/{sponsoredMessageId}/pause")
    public ResponseEntity<SponsoredMessageResponse> pauseSponsoredMessage(
            @PathVariable UUID chatId,
            @PathVariable UUID sponsoredMessageId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.pauseSponsoredMessage(CurrentUser.id(), chatId, sponsoredMessageId));
    }

    @PostMapping("/channels/{chatId}/sponsored-messages/{sponsoredMessageId}/resume")
    public ResponseEntity<SponsoredMessageResponse> resumeSponsoredMessage(
            @PathVariable UUID chatId,
            @PathVariable UUID sponsoredMessageId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.resumeSponsoredMessage(CurrentUser.id(), chatId, sponsoredMessageId));
    }

    @PostMapping("/channels/{chatId}/sponsored-messages/{sponsoredMessageId}/impression")
    public ResponseEntity<SponsoredMessageResponse> recordImpression(
            @PathVariable UUID chatId,
            @PathVariable UUID sponsoredMessageId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.recordImpression(CurrentUser.id(), chatId, sponsoredMessageId));
    }

    @PostMapping("/channels/{chatId}/sponsored-messages/{sponsoredMessageId}/click")
    public ResponseEntity<SponsoredMessageResponse> recordClick(
            @PathVariable UUID chatId,
            @PathVariable UUID sponsoredMessageId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.recordClick(CurrentUser.id(), chatId, sponsoredMessageId));
    }

    @GetMapping("/channels/{chatId}/stats")
    public ResponseEntity<ChannelMonetizationStatsResponse> channelStats(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.getChannelStats(CurrentUser.id(), chatId));
    }
}
