package com.alex.messenger.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.bot.dto.BotApiAnswerPreCheckoutQueryRequest;
import com.alex.messenger.bot.dto.BotApiRefundPaymentRequest;
import com.alex.messenger.bot.dto.BotApiSendInvoiceRequest;
import com.alex.messenger.bot.dto.BotPaymentShippingAddressPayload;
import com.alex.messenger.bot.dto.BotPaymentShippingOptionPayload;
import com.alex.messenger.bot.dto.CompleteBotPreCheckoutRequest;
import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.SendMessageRequest;
import com.alex.messenger.payments.PaymentInvoiceEntity;
import com.alex.messenger.payments.PaymentInvoiceRepository;
import com.alex.messenger.payments.PaymentService;
import com.alex.messenger.payments.dto.PaymentIntentResponse;
import com.alex.messenger.payments.dto.PaymentInvoiceResponse;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BotPaymentServiceTest {

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private BotAccountRepository botAccountRepository;

    @Mock
    private BotUpdateRepository botUpdateRepository;

    @Mock
    private BotMessageActionRepository botMessageActionRepository;

    @Mock
    private BotPaymentInvoiceRepository botPaymentInvoiceRepository;

    @Mock
    private BotPreCheckoutQueryRepository botPreCheckoutQueryRepository;

    @Mock
    private BotPaymentReceiptRepository botPaymentReceiptRepository;

    @Mock
    private PaymentInvoiceRepository paymentInvoiceRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private MessageService messageService;

    @Mock
    private com.alex.messenger.chat.ChatService chatService;

    @Mock
    private UserRepository userRepository;

    private BotPaymentService botPaymentService;

    @BeforeEach
    void setUp() {
        botPaymentService = new BotPaymentService(
                featureFlagService,
                botAccountRepository,
                botUpdateRepository,
                botMessageActionRepository,
                botPaymentInvoiceRepository,
                botPreCheckoutQueryRepository,
                botPaymentReceiptRepository,
                paymentInvoiceRepository,
                paymentService,
                messageService,
                chatService,
                userRepository,
                new ObjectMapper()
        );
    }

    @Test
    void sendInvoiceCreatesBotInvoiceAndPayAction() {
        UUID botUserId = UUID.randomUUID();
        UUID payerUserId = UUID.randomUUID();
        UUID paymentInvoiceId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        when(botAccountRepository.findById(botUserId)).thenReturn(Optional.of(new BotAccountEntity()));
        when(userRepository.findById(payerUserId)).thenReturn(Optional.of(user(payerUserId, false)));
        when(paymentService.createSelfInvoice(eq(botUserId), eq("Invoice"), eq("Desc"), eq(50L), eq(null), any()))
                .thenReturn(new PaymentInvoiceResponse(
                        paymentInvoiceId,
                        botUserId,
                        botUserId,
                        "Invoice",
                        "Desc",
                        50L,
                        "XTR",
                        "OPEN",
                        Map.of("kind", "BOT_PAYMENT"),
                        Instant.parse("2026-03-14T12:00:00Z"),
                        Instant.parse("2026-03-14T12:00:00Z"),
                        null
                ));
        when(paymentInvoiceRepository.findById(paymentInvoiceId)).thenReturn(Optional.of(invoice(paymentInvoiceId, botUserId)));
        when(messageService.sendMessage(eq(botUserId), any(SendMessageRequest.class))).thenReturn(message(chatId, messageId, botUserId));

        var response = botPaymentService.sendInvoice(
                botUserId,
                new BotApiSendInvoiceRequest(
                        null,
                        payerUserId,
                        "Invoice",
                        "Desc",
                        50L,
                        null,
                        "payload",
                        "Pay now",
                        null,
                        Map.of(),
                        false,
                        false,
                        true,
                        false,
                        false,
                        10L,
                        List.of(3L, 5L),
                        List.of()
                )
        );

        assertThat(response.paymentInvoiceId()).isEqualTo(paymentInvoiceId);
        assertThat(response.messageId()).isEqualTo(messageId);
        assertThat(response.payButtonText()).isEqualTo("Pay now");
        assertThat(response.needEmail()).isTrue();
        assertThat(response.maxTipAmountUnits()).isEqualTo(10L);
        assertThat(response.suggestedTipAmounts()).containsExactly(3L, 5L);

        ArgumentCaptor<BotMessageActionEntity> actionCaptor = ArgumentCaptor.forClass(BotMessageActionEntity.class);
        verify(botMessageActionRepository).save(actionCaptor.capture());
        assertThat(actionCaptor.getValue().getActionType()).isEqualTo("PAY");
        assertThat(actionCaptor.getValue().getPaymentInvoiceId()).isEqualTo(paymentInvoiceId);
    }

    @Test
    void createPreCheckoutQueryPersistsQueryAndEnqueuesUpdate() {
        UUID requesterId = UUID.randomUUID();
        UUID botUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID paymentInvoiceId = UUID.randomUUID();
        UUID queryId = UUID.randomUUID();

        BotMessageActionEntity action = new BotMessageActionEntity();
        action.setId(UUID.randomUUID());
        action.setBotUserId(botUserId);
        action.setMessageId(messageId);
        action.setActionType("PAY");
        action.setPaymentInvoiceId(paymentInvoiceId);

        when(botPaymentInvoiceRepository.findById(paymentInvoiceId))
                .thenReturn(Optional.of(botInvoice(paymentInvoiceId, botUserId, chatId, messageId, requesterId)));
        when(paymentInvoiceRepository.findById(paymentInvoiceId)).thenReturn(Optional.of(invoice(paymentInvoiceId, botUserId)));
        when(botPreCheckoutQueryRepository.save(any(BotPreCheckoutQueryEntity.class))).thenAnswer(invocation -> {
            BotPreCheckoutQueryEntity query = invocation.getArgument(0);
            query.setId(queryId);
            query.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
            return query;
        });

        var response = botPaymentService.createPreCheckoutQuery(requesterId, message(chatId, messageId, botUserId), action);

        assertThat(response.preCheckoutQueryId()).isEqualTo(queryId);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.shippingOptions()).hasSize(1);
        verify(botUpdateRepository).save(any(BotUpdateEntity.class));
    }

    @Test
    void completePreCheckoutConfirmsIntentAndEnqueuesSuccessfulPaymentUpdate() {
        UUID requesterId = UUID.randomUUID();
        UUID botUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID paymentInvoiceId = UUID.randomUUID();
        UUID preCheckoutQueryId = UUID.randomUUID();
        UUID paymentIntentId = UUID.randomUUID();

        BotPreCheckoutQueryEntity query = new BotPreCheckoutQueryEntity();
        query.setId(preCheckoutQueryId);
        query.setBotUserId(botUserId);
        query.setChatId(chatId);
        query.setMessageId(messageId);
        query.setPaymentInvoiceId(paymentInvoiceId);
        query.setFromUserId(requesterId);
        query.setStatus("APPROVED");
        query.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
        query.setAnsweredAt(Instant.parse("2026-03-14T12:01:00Z"));

        when(botPreCheckoutQueryRepository.findByIdAndFromUserId(preCheckoutQueryId, requesterId)).thenReturn(Optional.of(query));
        when(botPaymentInvoiceRepository.findById(paymentInvoiceId))
                .thenReturn(Optional.of(botInvoice(paymentInvoiceId, botUserId, chatId, messageId, requesterId)));
        when(paymentInvoiceRepository.findById(paymentInvoiceId)).thenReturn(Optional.of(invoice(paymentInvoiceId, botUserId)));
        when(paymentService.createPaymentIntent(requesterId, paymentInvoiceId, 50L))
                .thenReturn(new PaymentIntentResponse(
                        paymentIntentId,
                        paymentInvoiceId,
                        requesterId,
                        botUserId,
                        50L,
                        "XTR",
                        "PENDING",
                        null,
                        null,
                        Instant.parse("2026-03-14T12:02:00Z"),
                        null,
                        null,
                        null,
                        Instant.parse("2026-03-14T12:02:00Z")
                ));
        when(paymentService.confirmIntent(requesterId, paymentIntentId))
                .thenReturn(new PaymentIntentResponse(
                        paymentIntentId,
                        paymentInvoiceId,
                        requesterId,
                        botUserId,
                        50L,
                        "XTR",
                        "COMPLETED",
                        null,
                        null,
                        Instant.parse("2026-03-14T12:02:00Z"),
                        Instant.parse("2026-03-14T12:03:00Z"),
                        null,
                        null,
                        Instant.parse("2026-03-14T12:03:00Z")
                ));
        when(messageService.sendInternalServiceMessage(eq(botUserId), eq(chatId), eq("BOT_PAYMENT_SUCCESS"), anyString()))
                .thenReturn(message(chatId, UUID.randomUUID(), botUserId));
        when(botPaymentReceiptRepository.save(any(BotPaymentReceiptEntity.class))).thenAnswer(invocation -> {
            BotPaymentReceiptEntity receipt = invocation.getArgument(0);
            receipt.setId(UUID.randomUUID());
            receipt.setCreatedAt(Instant.parse("2026-03-14T12:03:00Z"));
            return receipt;
        });
        when(botPreCheckoutQueryRepository.save(any(BotPreCheckoutQueryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(botPaymentInvoiceRepository.save(any(BotPaymentInvoiceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = botPaymentService.completePreCheckout(
                requesterId,
                preCheckoutQueryId,
                new CompleteBotPreCheckoutRequest(
                        "Alex",
                        null,
                        "payer@example.com",
                        new BotPaymentShippingAddressPayload("BY", null, "Minsk", "Lenina 1", null, "220000"),
                        "standard",
                        0L
                )
        );

        assertThat(response.preCheckoutQueryId()).isEqualTo(preCheckoutQueryId);
        assertThat(response.paymentIntentId()).isEqualTo(paymentIntentId);
        assertThat(response.completedAt()).isEqualTo(Instant.parse("2026-03-14T12:03:00Z"));
        assertThat(response.totalAmountUnits()).isEqualTo(50L);
        assertThat(response.receiptId()).isNotNull();
        verify(botUpdateRepository).save(any(BotUpdateEntity.class));
    }

    @Test
    void answerPreCheckoutMarksQueryApproved() {
        UUID botUserId = UUID.randomUUID();
        UUID paymentInvoiceId = UUID.randomUUID();
        UUID preCheckoutQueryId = UUID.randomUUID();

        BotPreCheckoutQueryEntity query = new BotPreCheckoutQueryEntity();
        query.setId(preCheckoutQueryId);
        query.setBotUserId(botUserId);
        query.setChatId(UUID.randomUUID());
        query.setMessageId(UUID.randomUUID());
        query.setPaymentInvoiceId(paymentInvoiceId);
        query.setFromUserId(UUID.randomUUID());
        query.setStatus("PENDING");
        query.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));

        when(botAccountRepository.findById(botUserId)).thenReturn(Optional.of(new BotAccountEntity()));
        when(botPreCheckoutQueryRepository.findByIdAndBotUserId(preCheckoutQueryId, botUserId)).thenReturn(Optional.of(query));
        when(botPreCheckoutQueryRepository.save(any(BotPreCheckoutQueryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(botPaymentInvoiceRepository.findById(paymentInvoiceId))
                .thenReturn(Optional.of(botInvoice(paymentInvoiceId, botUserId, UUID.randomUUID(), UUID.randomUUID(), query.getFromUserId())));
        when(paymentInvoiceRepository.findById(paymentInvoiceId)).thenReturn(Optional.of(invoice(paymentInvoiceId, botUserId)));

        var response = botPaymentService.answerPreCheckoutQuery(
                botUserId,
                new BotApiAnswerPreCheckoutQueryRequest(preCheckoutQueryId, true, "ok")
        );

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.answerText()).isEqualTo("ok");
        assertThat(response.shippingOptions()).hasSize(1);
    }

    @Test
    void refundPaymentMarksReceiptRefundedAndEnqueuesRefundUpdate() {
        UUID botUserId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        UUID paymentIntentId = UUID.randomUUID();
        UUID paymentInvoiceId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID invoiceMessageId = UUID.randomUUID();

        BotPaymentReceiptEntity receipt = new BotPaymentReceiptEntity();
        receipt.setId(receiptId);
        receipt.setPaymentInvoiceId(paymentInvoiceId);
        receipt.setPaymentIntentId(paymentIntentId);
        receipt.setPreCheckoutQueryId(UUID.randomUUID());
        receipt.setBotUserId(botUserId);
        receipt.setChatId(chatId);
        receipt.setInvoiceMessageId(invoiceMessageId);
        receipt.setPayerUserId(UUID.randomUUID());
        receipt.setTitle("Invoice");
        receipt.setDescription("Desc");
        receipt.setInvoicePayload("payload");
        receipt.setCurrencyCode("XTR");
        receipt.setBaseAmountUnits(50L);
        receipt.setShippingAmountUnits(0L);
        receipt.setTipAmountUnits(0L);
        receipt.setTotalAmountUnits(50L);
        receipt.setProviderDataJson("{}");
        receipt.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));

        when(botAccountRepository.findById(botUserId)).thenReturn(Optional.of(new BotAccountEntity()));
        when(botPaymentReceiptRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
        when(paymentService.refundIntent(eq(botUserId), eq(paymentIntentId), any()))
                .thenReturn(new PaymentIntentResponse(
                        paymentIntentId,
                        paymentInvoiceId,
                        UUID.randomUUID(),
                        botUserId,
                        50L,
                        "XTR",
                        "REFUNDED",
                        null,
                        "customer request",
                        Instant.parse("2026-03-14T12:02:00Z"),
                        Instant.parse("2026-03-14T12:03:00Z"),
                        null,
                        Instant.parse("2026-03-14T12:04:00Z"),
                        Instant.parse("2026-03-14T12:04:00Z")
                ));
        when(messageService.sendInternalServiceMessage(eq(botUserId), eq(chatId), eq("BOT_PAYMENT_REFUND"), anyString()))
                .thenReturn(message(chatId, UUID.randomUUID(), botUserId));
        when(botPaymentReceiptRepository.save(any(BotPaymentReceiptEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(botPaymentInvoiceRepository.findById(paymentInvoiceId))
                .thenReturn(Optional.of(botInvoice(paymentInvoiceId, botUserId, chatId, invoiceMessageId, receipt.getPayerUserId())));

        var response = botPaymentService.refundPayment(
                botUserId,
                new BotApiRefundPaymentRequest(receiptId, "customer request")
        );

        assertThat(response.receiptId()).isEqualTo(receiptId);
        assertThat(response.refundedAt()).isEqualTo(Instant.parse("2026-03-14T12:04:00Z"));
        verify(botUpdateRepository).save(any(BotUpdateEntity.class));
    }

    private UserEntity user(UUID userId, boolean bot) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setBot(bot);
        user.setDisplayName(bot ? "Bot" : "User");
        return user;
    }

    private PaymentInvoiceEntity invoice(UUID paymentInvoiceId, UUID botUserId) {
        PaymentInvoiceEntity invoice = new PaymentInvoiceEntity();
        invoice.setId(paymentInvoiceId);
        invoice.setCreatedByUserId(botUserId);
        invoice.setRecipientUserId(botUserId);
        invoice.setTitle("Invoice");
        invoice.setDescription("Desc");
        invoice.setAmountUnits(50L);
        invoice.setCurrencyCode("XTR");
        invoice.setStatus("OPEN");
        invoice.setMetadataJson("{}");
        invoice.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
        invoice.setUpdatedAt(Instant.parse("2026-03-14T12:00:00Z"));
        return invoice;
    }

    private BotPaymentInvoiceEntity botInvoice(
            UUID paymentInvoiceId,
            UUID botUserId,
            UUID chatId,
            UUID messageId,
            UUID payerUserId
    ) {
        BotPaymentInvoiceEntity invoice = new BotPaymentInvoiceEntity();
        invoice.setPaymentInvoiceId(paymentInvoiceId);
        invoice.setBotUserId(botUserId);
        invoice.setChatId(chatId);
        invoice.setMessageId(messageId);
        invoice.setPayerUserId(payerUserId);
        invoice.setInvoicePayload("payload");
        invoice.setPayButtonText("Pay");
        invoice.setNeedEmail(true);
        invoice.setMaxTipAmountUnits(10L);
        invoice.setSuggestedTipAmountsJson("[3,5]");
        invoice.setNeedShippingAddress(true);
        invoice.setFlexible(true);
        invoice.setShippingOptionsJson("[{\"optionId\":\"standard\",\"title\":\"Standard\",\"amountUnits\":0}]");
        invoice.setProviderDataJson("{}");
        invoice.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
        return invoice;
    }

    private ChatMessageResponse message(UUID chatId, UUID messageId, UUID senderId) {
        return new ChatMessageResponse(
                chatId,
                messageId,
                null,
                senderId,
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
                "Invoice",
                java.util.List.of(),
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
                java.util.List.of(),
                java.util.List.of(),
                "SENT",
                null,
                null,
                null,
                null,
                null
        );
    }
}
