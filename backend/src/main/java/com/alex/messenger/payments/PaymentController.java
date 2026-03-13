package com.alex.messenger.payments;

import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.bot.BotPaymentService;
import com.alex.messenger.payments.dto.CreateInvoiceRequest;
import com.alex.messenger.payments.dto.CreatePaymentIntentRequest;
import com.alex.messenger.payments.dto.PaymentIntentResponse;
import com.alex.messenger.payments.dto.PaymentInvoiceResponse;
import com.alex.messenger.payments.dto.PaymentTransactionResponse;
import com.alex.messenger.payments.dto.PaymentWalletResponse;
import com.alex.messenger.payments.dto.TopUpWalletRequest;
import com.alex.messenger.payments.dto.UpdatePaymentIntentRequest;
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
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final FeatureFlagService featureFlagService;
    private final PaymentService paymentService;
    private final BotPaymentService botPaymentService;

    @GetMapping("/wallet")
    public ResponseEntity<PaymentWalletResponse> wallet() {
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(paymentService.getWallet(CurrentUser.id()));
    }

    @PostMapping("/wallet/top-up")
    public ResponseEntity<PaymentWalletResponse> topUp(@RequestBody(required = false) TopUpWalletRequest request) {
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(paymentService.topUpWallet(CurrentUser.id(), request));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<PaymentTransactionResponse>> transactions() {
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(paymentService.listTransactions(CurrentUser.id()));
    }

    @PostMapping("/invoices")
    public ResponseEntity<PaymentInvoiceResponse> createInvoice(@Valid @RequestBody CreateInvoiceRequest request) {
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(paymentService.createInvoice(CurrentUser.id(), request));
    }

    @GetMapping("/invoices/outgoing")
    public ResponseEntity<List<PaymentInvoiceResponse>> outgoingInvoices() {
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(paymentService.listCreatedInvoices(CurrentUser.id()));
    }

    @GetMapping("/invoices/incoming")
    public ResponseEntity<List<PaymentInvoiceResponse>> incomingInvoices() {
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(paymentService.listReceivableInvoices(CurrentUser.id()));
    }

    @GetMapping("/invoices/{invoiceId}")
    public ResponseEntity<PaymentInvoiceResponse> invoice(@PathVariable UUID invoiceId) {
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(paymentService.getInvoice(CurrentUser.id(), invoiceId));
    }

    @PostMapping("/invoices/{invoiceId}/cancel")
    public ResponseEntity<PaymentInvoiceResponse> cancelInvoice(@PathVariable UUID invoiceId) {
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(paymentService.cancelInvoice(CurrentUser.id(), invoiceId));
    }

    @PostMapping("/intents")
    public ResponseEntity<PaymentIntentResponse> createIntent(@Valid @RequestBody CreatePaymentIntentRequest request) {
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(paymentService.createPaymentIntent(CurrentUser.id(), request));
    }

    @GetMapping("/intents/outgoing")
    public ResponseEntity<List<PaymentIntentResponse>> outgoingIntents() {
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(paymentService.listOutgoingIntents(CurrentUser.id()));
    }

    @GetMapping("/intents/incoming")
    public ResponseEntity<List<PaymentIntentResponse>> incomingIntents() {
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(paymentService.listIncomingIntents(CurrentUser.id()));
    }

    @PostMapping("/intents/{intentId}/confirm")
    public ResponseEntity<PaymentIntentResponse> confirmIntent(@PathVariable UUID intentId) {
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(paymentService.confirmIntent(CurrentUser.id(), intentId));
    }

    @PostMapping("/intents/{intentId}/cancel")
    public ResponseEntity<PaymentIntentResponse> cancelIntent(
            @PathVariable UUID intentId,
            @RequestBody(required = false) UpdatePaymentIntentRequest request
    ) {
        featureFlagService.requirePaymentsEnabled();
        return ResponseEntity.ok(paymentService.cancelIntent(CurrentUser.id(), intentId, request));
    }

    @PostMapping("/intents/{intentId}/refund")
    public ResponseEntity<PaymentIntentResponse> refundIntent(
            @PathVariable UUID intentId,
            @RequestBody(required = false) UpdatePaymentIntentRequest request
    ) {
        featureFlagService.requirePaymentsEnabled();
        PaymentIntentResponse response = paymentService.refundIntent(CurrentUser.id(), intentId, request);
        botPaymentService.syncRefundedPayment(response.paymentIntentId(), response.refundedAt(), request != null ? request.reason() : null);
        return ResponseEntity.ok(response);
    }
}
