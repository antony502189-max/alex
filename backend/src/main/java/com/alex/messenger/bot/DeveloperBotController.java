package com.alex.messenger.bot;

import com.alex.messenger.bot.dto.CreateDeveloperBotRequest;
import com.alex.messenger.bot.dto.DeveloperBotResponse;
import com.alex.messenger.bot.dto.IssuedBotTokenResponse;
import com.alex.messenger.bot.dto.UpdateBotWebhookRequest;
import com.alex.messenger.bot.dto.UpdateDeveloperBotRequest;
import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.shared.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/developer/bots")
@RequiredArgsConstructor
public class DeveloperBotController {

    private final FeatureFlagService featureFlagService;
    private final DeveloperBotService developerBotService;

    @GetMapping
    public ResponseEntity<List<DeveloperBotResponse>> listBots() {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(developerBotService.listOwnedBots(CurrentUser.id()));
    }

    @PostMapping
    public ResponseEntity<IssuedBotTokenResponse> createBot(@Valid @RequestBody CreateDeveloperBotRequest request) {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(developerBotService.createBot(CurrentUser.id(), request));
    }

    @GetMapping("/{botUserId}")
    public ResponseEntity<DeveloperBotResponse> getBot(@PathVariable UUID botUserId) {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(developerBotService.getOwnedBot(CurrentUser.id(), botUserId));
    }

    @PatchMapping("/{botUserId}")
    public ResponseEntity<DeveloperBotResponse> updateBot(
            @PathVariable UUID botUserId,
            @Valid @RequestBody UpdateDeveloperBotRequest request
    ) {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(developerBotService.updateBot(CurrentUser.id(), botUserId, request));
    }

    @PostMapping("/{botUserId}/token")
    public ResponseEntity<IssuedBotTokenResponse> rotateToken(@PathVariable UUID botUserId) {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(developerBotService.rotateToken(CurrentUser.id(), botUserId));
    }

    @PutMapping("/{botUserId}/webhook")
    public ResponseEntity<DeveloperBotResponse> updateWebhook(
            @PathVariable UUID botUserId,
            @Valid @RequestBody UpdateBotWebhookRequest request
    ) {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(developerBotService.updateWebhook(CurrentUser.id(), botUserId, request));
    }

    @DeleteMapping("/{botUserId}/webhook")
    public ResponseEntity<DeveloperBotResponse> clearWebhook(@PathVariable UUID botUserId) {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(developerBotService.clearWebhook(CurrentUser.id(), botUserId));
    }
}
