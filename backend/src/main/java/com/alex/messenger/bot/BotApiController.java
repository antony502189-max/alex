package com.alex.messenger.bot;

import com.alex.messenger.bot.dto.BotApiProfileResponse;
import com.alex.messenger.bot.dto.BotApiAnswerPreCheckoutQueryRequest;
import com.alex.messenger.bot.dto.BotApiDeleteMessageRequest;
import com.alex.messenger.bot.dto.BotApiDeleteMessageResponse;
import com.alex.messenger.bot.dto.BotApiEditMessageTextRequest;
import com.alex.messenger.bot.dto.BotApiRefundPaymentRequest;
import com.alex.messenger.bot.dto.BotApiSendInvoiceRequest;
import com.alex.messenger.bot.dto.BotApiSendMessageRequest;
import com.alex.messenger.bot.dto.BotApiSetMyCommandsRequest;
import com.alex.messenger.bot.dto.BotApiAnswerWebAppQueryRequest;
import com.alex.messenger.bot.dto.BotApiAnswerInlineQueryRequest;
import com.alex.messenger.bot.dto.BotApiAnswerCallbackQueryRequest;
import com.alex.messenger.bot.dto.BotCallbackQueryResponse;
import com.alex.messenger.bot.dto.BotCommandResponse;
import com.alex.messenger.bot.dto.BotInlineResultResponse;
import com.alex.messenger.bot.dto.BotPaymentInvoiceResponse;
import com.alex.messenger.bot.dto.BotPaymentReceiptResponse;
import com.alex.messenger.bot.dto.BotPreCheckoutQueryResponse;
import com.alex.messenger.bot.dto.BotWebhookInfoResponse;
import com.alex.messenger.bot.dto.BotUpdatesResponse;
import com.alex.messenger.bot.dto.UpdateBotWebhookRequest;
import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.shared.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bot-api")
@RequiredArgsConstructor
public class BotApiController {

    private final FeatureFlagService featureFlagService;
    private final DeveloperBotService developerBotService;
    private final BotUpdateService botUpdateService;
    private final BotApiService botApiService;

    @GetMapping("/me")
    public ResponseEntity<BotApiProfileResponse> me() {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(developerBotService.getBotApiProfile(CurrentUser.id()));
    }

    @GetMapping("/webhook-info")
    public ResponseEntity<BotWebhookInfoResponse> webhookInfo() {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(developerBotService.getBotWebhookInfo(CurrentUser.id()));
    }

    @PostMapping("/set-webhook")
    public ResponseEntity<BotWebhookInfoResponse> setWebhook(@Valid @RequestBody UpdateBotWebhookRequest request) {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(developerBotService.updateWebhookForBotApi(CurrentUser.id(), request));
    }

    @PostMapping("/delete-webhook")
    public ResponseEntity<BotWebhookInfoResponse> deleteWebhook() {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(developerBotService.clearWebhookForBotApi(CurrentUser.id()));
    }

    @GetMapping("/updates")
    public ResponseEntity<BotUpdatesResponse> getUpdates(
            @RequestParam(required = false) Long offset,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer timeoutSeconds
    ) {
        featureFlagService.requireBotsEnabled();
        return ResponseEntity.ok(botUpdateService.getUpdates(CurrentUser.id(), offset, limit, timeoutSeconds));
    }

    @PostMapping("/send-message")
    public ResponseEntity<ChatMessageResponse> sendMessage(@Valid @RequestBody BotApiSendMessageRequest request) {
        featureFlagService.requireBotsEnabled();
        featureFlagService.requireBotApiFullEnabled();
        return ResponseEntity.ok(botApiService.sendMessage(CurrentUser.id(), request));
    }

    @PostMapping("/edit-message-text")
    public ResponseEntity<ChatMessageResponse> editMessageText(
            @Valid @RequestBody BotApiEditMessageTextRequest request
    ) {
        featureFlagService.requireBotsEnabled();
        featureFlagService.requireBotApiFullEnabled();
        return ResponseEntity.ok(botApiService.editMessageText(CurrentUser.id(), request));
    }

    @PostMapping("/delete-message")
    public ResponseEntity<BotApiDeleteMessageResponse> deleteMessage(
            @Valid @RequestBody BotApiDeleteMessageRequest request
    ) {
        featureFlagService.requireBotsEnabled();
        featureFlagService.requireBotApiFullEnabled();
        return ResponseEntity.ok(botApiService.deleteMessage(CurrentUser.id(), request));
    }

    @GetMapping("/my-commands")
    public ResponseEntity<List<BotCommandResponse>> myCommands() {
        featureFlagService.requireBotsEnabled();
        featureFlagService.requireBotApiFullEnabled();
        return ResponseEntity.ok(botApiService.getMyCommands(CurrentUser.id()));
    }

    @PostMapping("/set-my-commands")
    public ResponseEntity<List<BotCommandResponse>> setMyCommands(
            @Valid @RequestBody BotApiSetMyCommandsRequest request
    ) {
        featureFlagService.requireBotsEnabled();
        featureFlagService.requireBotApiFullEnabled();
        return ResponseEntity.ok(botApiService.setMyCommands(CurrentUser.id(), request));
    }

    @PostMapping("/answer-inline-query")
    public ResponseEntity<List<BotInlineResultResponse>> answerInlineQuery(
            @Valid @RequestBody BotApiAnswerInlineQueryRequest request
    ) {
        featureFlagService.requireBotsEnabled();
        featureFlagService.requireBotApiFullEnabled();
        return ResponseEntity.ok(botApiService.answerInlineQuery(CurrentUser.id(), request));
    }

    @PostMapping("/answer-callback-query")
    public ResponseEntity<BotCallbackQueryResponse> answerCallbackQuery(
            @Valid @RequestBody BotApiAnswerCallbackQueryRequest request
    ) {
        featureFlagService.requireBotsEnabled();
        featureFlagService.requireBotApiFullEnabled();
        return ResponseEntity.ok(botApiService.answerCallbackQuery(CurrentUser.id(), request));
    }

    @PostMapping("/answer-web-app-query")
    public ResponseEntity<ChatMessageResponse> answerWebAppQuery(
            @Valid @RequestBody BotApiAnswerWebAppQueryRequest request
    ) {
        featureFlagService.requireBotsEnabled();
        featureFlagService.requireBotApiFullEnabled();
        return ResponseEntity.ok(botApiService.answerWebAppQuery(CurrentUser.id(), request));
    }

    @PostMapping("/send-invoice")
    public ResponseEntity<BotPaymentInvoiceResponse> sendInvoice(
            @Valid @RequestBody BotApiSendInvoiceRequest request
    ) {
        featureFlagService.requireBotsEnabled();
        featureFlagService.requireBotApiFullEnabled();
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(botApiService.sendInvoice(CurrentUser.id(), request));
    }

    @PostMapping("/answer-pre-checkout-query")
    public ResponseEntity<BotPreCheckoutQueryResponse> answerPreCheckoutQuery(
            @Valid @RequestBody BotApiAnswerPreCheckoutQueryRequest request
    ) {
        featureFlagService.requireBotsEnabled();
        featureFlagService.requireBotApiFullEnabled();
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(botApiService.answerPreCheckoutQuery(CurrentUser.id(), request));
    }

    @PostMapping("/refund-payment")
    public ResponseEntity<BotPaymentReceiptResponse> refundPayment(
            @Valid @RequestBody BotApiRefundPaymentRequest request
    ) {
        featureFlagService.requireBotsEnabled();
        featureFlagService.requireBotApiFullEnabled();
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(botApiService.refundPayment(CurrentUser.id(), request));
    }
}
