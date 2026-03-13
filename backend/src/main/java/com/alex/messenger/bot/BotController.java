package com.alex.messenger.bot;

import com.alex.messenger.bot.dto.BotCommandResponse;
import com.alex.messenger.bot.dto.BotMessageActionResponse;
import com.alex.messenger.bot.dto.BotMessageActionTriggerResponse;
import com.alex.messenger.bot.dto.BotInlineResultResponse;
import com.alex.messenger.bot.dto.BotPaymentInvoiceResponse;
import com.alex.messenger.bot.dto.BotPaymentReceiptResponse;
import com.alex.messenger.bot.dto.BotSuccessfulPaymentResponse;
import com.alex.messenger.bot.dto.BotSummaryResponse;
import com.alex.messenger.bot.dto.BotWebAppContextResponse;
import com.alex.messenger.bot.dto.BotWebAppDataResponse;
import com.alex.messenger.bot.dto.BotWebAppQueryResponse;
import com.alex.messenger.bot.dto.CompleteBotPreCheckoutRequest;
import com.alex.messenger.bot.dto.CreateBotWebAppQueryRequest;
import com.alex.messenger.bot.dto.BotWebAppLaunchResponse;
import com.alex.messenger.bot.dto.ResolveBotWebAppRequest;
import com.alex.messenger.bot.dto.SendBotWebAppDataRequest;
import com.alex.messenger.feature.FeatureFlagService;
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
@RequestMapping("/api/bots")
@RequiredArgsConstructor
public class BotController {

    private final FeatureFlagService featureFlagService;
    private final BotService botService;
    private final BotMessageActionService botMessageActionService;
    private final BotWebAppService botWebAppService;
    private final BotPaymentService botPaymentService;

    @GetMapping
    public ResponseEntity<List<BotSummaryResponse>> listBots() {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(botService.listBots());
    }

    @GetMapping("/{botUserId}/commands")
    public ResponseEntity<List<BotCommandResponse>> getCommands(@PathVariable UUID botUserId) {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(botService.getCommands(botUserId));
    }

    @GetMapping("/inline/{username}")
    public ResponseEntity<List<BotInlineResultResponse>> getInlineResults(
            @PathVariable String username,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String query
    ) {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(botService.getInlineResults(username, query));
    }

    @GetMapping("/messages/{messageId}/actions")
    public ResponseEntity<List<BotMessageActionResponse>> getMessageActions(@PathVariable UUID messageId) {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(botMessageActionService.listMessageActions(CurrentUser.id(), messageId));
    }

    @org.springframework.web.bind.annotation.PostMapping("/messages/{messageId}/actions/{actionId}/trigger")
    public ResponseEntity<BotMessageActionTriggerResponse> triggerMessageAction(
            @PathVariable UUID messageId,
            @PathVariable UUID actionId
    ) {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(botMessageActionService.triggerAction(CurrentUser.id(), messageId, actionId));
    }

    @GetMapping("/{botUserId}/web-app-launch")
    public ResponseEntity<BotWebAppLaunchResponse> getWebAppLaunch(
            @PathVariable UUID botUserId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID chatId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String startParameter
    ) {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(
                botWebAppService.createLaunch(CurrentUser.id(), botUserId, chatId, startParameter)
        );
    }

    @PostMapping("/web-app/context")
    public ResponseEntity<BotWebAppContextResponse> resolveWebAppContext(
            @Valid @RequestBody ResolveBotWebAppRequest request
    ) {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(botWebAppService.resolveContext(CurrentUser.id(), request));
    }

    @PostMapping("/web-app/send-data")
    public ResponseEntity<BotWebAppDataResponse> sendWebAppData(
            @Valid @RequestBody SendBotWebAppDataRequest request
    ) {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(botWebAppService.sendData(CurrentUser.id(), request));
    }

    @PostMapping("/web-app/query")
    public ResponseEntity<BotWebAppQueryResponse> createWebAppQuery(
            @Valid @RequestBody CreateBotWebAppQueryRequest request
    ) {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(botWebAppService.createQuery(CurrentUser.id(), request));
    }

    @GetMapping("/messages/{messageId}/payment-invoice")
    public ResponseEntity<BotPaymentInvoiceResponse> getMessagePaymentInvoice(@PathVariable UUID messageId) {
        featureFlagService.requireBotsEnabled();
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(botPaymentService.getMessageInvoice(CurrentUser.id(), messageId));
    }

    @GetMapping("/messages/{messageId}/payment-receipt")
    public ResponseEntity<BotPaymentReceiptResponse> getMessagePaymentReceipt(@PathVariable UUID messageId) {
        featureFlagService.requireBotsEnabled();
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(botPaymentService.getMessageReceipt(CurrentUser.id(), messageId));
    }

    @GetMapping("/payments/receipts/{receiptId}")
    public ResponseEntity<BotPaymentReceiptResponse> getPaymentReceipt(@PathVariable UUID receiptId) {
        featureFlagService.requireBotsEnabled();
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(botPaymentService.getReceipt(CurrentUser.id(), receiptId));
    }

    @PostMapping("/payments/pre-checkout/{preCheckoutQueryId}/complete")
    public ResponseEntity<BotSuccessfulPaymentResponse> completePreCheckout(
            @PathVariable UUID preCheckoutQueryId,
            @Valid @RequestBody(required = false) CompleteBotPreCheckoutRequest request
    ) {
        featureFlagService.requireBotsEnabled();
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(botPaymentService.completePreCheckout(CurrentUser.id(), preCheckoutQueryId, request));
    }
}
