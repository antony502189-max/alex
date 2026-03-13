package com.alex.messenger.bot;

import com.alex.messenger.bot.dto.BotApiAnswerPreCheckoutQueryRequest;
import com.alex.messenger.bot.dto.BotApiRefundPaymentRequest;
import com.alex.messenger.bot.dto.BotApiSendInvoiceRequest;
import com.alex.messenger.bot.dto.BotPaymentInvoiceResponse;
import com.alex.messenger.bot.dto.BotPaymentReceiptResponse;
import com.alex.messenger.bot.dto.BotPaymentShippingAddressPayload;
import com.alex.messenger.bot.dto.BotPaymentShippingOptionPayload;
import com.alex.messenger.bot.dto.BotPreCheckoutQueryResponse;
import com.alex.messenger.bot.dto.BotSuccessfulPaymentResponse;
import com.alex.messenger.bot.dto.CompleteBotPreCheckoutRequest;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.SendMessageRequest;
import com.alex.messenger.payments.PaymentInvoiceEntity;
import com.alex.messenger.payments.PaymentInvoiceRepository;
import com.alex.messenger.payments.PaymentService;
import com.alex.messenger.payments.dto.PaymentIntentResponse;
import com.alex.messenger.payments.dto.UpdatePaymentIntentRequest;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BotPaymentService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_DECLINED = "DECLINED";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final FeatureFlagService featureFlagService;
    private final BotAccountRepository botAccountRepository;
    private final BotUpdateRepository botUpdateRepository;
    private final BotMessageActionRepository botMessageActionRepository;
    private final BotPaymentInvoiceRepository botPaymentInvoiceRepository;
    private final BotPreCheckoutQueryRepository botPreCheckoutQueryRepository;
    private final BotPaymentReceiptRepository botPaymentReceiptRepository;
    private final PaymentInvoiceRepository paymentInvoiceRepository;
    private final PaymentService paymentService;
    private final MessageService messageService;
    private final ChatService chatService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public BotPaymentInvoiceResponse sendInvoice(UUID botUserId, BotApiSendInvoiceRequest request) {
        featureFlagService.requirePaymentsEnabled();
        requireBot(botUserId);

        UUID payerUserId = resolvePayerUserId(botUserId, request.chatId(), request.recipientUserId());
        String invoicePayload = normalizeRequired(request.invoicePayload(), "Invoice payload", 255);
        String payButtonText = normalizeOptional(request.payButtonText(), 64);
        String providerToken = normalizeOptional(request.providerToken(), 128);
        Map<String, String> providerData = normalizeProviderData(request.providerData());
        boolean needName = Boolean.TRUE.equals(request.needName());
        boolean needPhoneNumber = Boolean.TRUE.equals(request.needPhoneNumber());
        boolean needEmail = Boolean.TRUE.equals(request.needEmail());
        boolean needShippingAddress = Boolean.TRUE.equals(request.needShippingAddress());
        boolean flexible = Boolean.TRUE.equals(request.flexible());
        Long maxTipAmountUnits = normalizeOptionalPositiveAmount(request.maxTipAmountUnits(), "Max tip amount");
        List<Long> suggestedTipAmounts = normalizeSuggestedTipAmounts(request.suggestedTipAmounts(), maxTipAmountUnits);
        List<BotPaymentShippingOptionPayload> shippingOptions = normalizeShippingOptions(request.shippingOptions());
        validateCheckoutRequirements(needShippingAddress, flexible, shippingOptions, maxTipAmountUnits, suggestedTipAmounts);
        if (payButtonText == null) {
            payButtonText = "Pay";
        }

        var invoiceResponse = paymentService.createSelfInvoice(
                botUserId,
                request.title(),
                request.description(),
                request.amountUnits(),
                request.expiresAt(),
                Map.of(
                        "kind", "BOT_PAYMENT",
                        "invoicePayload", invoicePayload
                )
        );
        PaymentInvoiceEntity invoice = requirePaymentInvoice(invoiceResponse.invoiceId());

        ChatMessageResponse message = messageService.sendMessage(
                botUserId,
                new SendMessageRequest(
                        request.chatId(),
                        request.recipientUserId(),
                        null,
                        null,
                        buildInvoiceMessageText(request.title(), request.description(), invoice.getAmountUnits(), invoice.getCurrencyCode()),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null
                )
        );

        BotPaymentInvoiceEntity botInvoice = new BotPaymentInvoiceEntity();
        botInvoice.setPaymentInvoiceId(invoice.getId());
        botInvoice.setBotUserId(botUserId);
        botInvoice.setChatId(message.chatId());
        botInvoice.setMessageId(message.messageId());
        botInvoice.setPayerUserId(payerUserId);
        botInvoice.setInvoicePayload(invoicePayload);
        botInvoice.setPayButtonText(payButtonText);
        botInvoice.setProviderToken(providerToken);
        botInvoice.setProviderDataJson(serializeProviderData(providerData));
        botInvoice.setNeedName(needName);
        botInvoice.setNeedPhoneNumber(needPhoneNumber);
        botInvoice.setNeedEmail(needEmail);
        botInvoice.setNeedShippingAddress(needShippingAddress);
        botInvoice.setFlexible(flexible);
        botInvoice.setMaxTipAmountUnits(maxTipAmountUnits);
        botInvoice.setSuggestedTipAmountsJson(serializeLongList(suggestedTipAmounts));
        botInvoice.setShippingOptionsJson(serializeShippingOptions(shippingOptions));
        botPaymentInvoiceRepository.save(botInvoice);

        BotMessageActionEntity payAction = new BotMessageActionEntity();
        payAction.setBotUserId(botUserId);
        payAction.setMessageId(message.messageId());
        payAction.setActionType("PAY");
        payAction.setButtonText(payButtonText);
        payAction.setPaymentInvoiceId(invoice.getId());
        payAction.setSortOrder(0);
        botMessageActionRepository.save(payAction);

        return toInvoiceResponse(botInvoice, invoice);
    }

    @Transactional(readOnly = true)
    public BotPaymentInvoiceResponse getMessageInvoice(UUID requesterId, UUID messageId) {
        featureFlagService.requirePaymentsEnabled();
        messageService.getMessage(requesterId, messageId);
        BotPaymentInvoiceEntity botInvoice = botPaymentInvoiceRepository.findByMessageId(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot payment invoice not found"));
        PaymentInvoiceEntity invoice = requirePaymentInvoice(botInvoice.getPaymentInvoiceId());
        return toInvoiceResponse(botInvoice, invoice);
    }

    @Transactional(readOnly = true)
    public BotPaymentReceiptResponse getReceipt(UUID requesterId, UUID receiptId) {
        featureFlagService.requirePaymentsEnabled();
        BotPaymentReceiptEntity receipt = botPaymentReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot payment receipt not found"));
        chatService.getOwnedChat(requesterId, receipt.getChatId());
        BotPaymentInvoiceEntity botInvoice = requireBotInvoice(receipt.getPaymentInvoiceId());
        return toReceiptResponse(receipt, botInvoice);
    }

    @Transactional(readOnly = true)
    public BotPaymentReceiptResponse getMessageReceipt(UUID requesterId, UUID messageId) {
        featureFlagService.requirePaymentsEnabled();
        messageService.getMessage(requesterId, messageId);
        BotPaymentReceiptEntity receipt = botPaymentReceiptRepository.findByInvoiceMessageIdOrServiceMessageIdOrRefundMessageId(
                        messageId,
                        messageId,
                        messageId
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot payment receipt not found"));
        BotPaymentInvoiceEntity botInvoice = requireBotInvoice(receipt.getPaymentInvoiceId());
        return toReceiptResponse(receipt, botInvoice);
    }

    @Transactional
    public BotPaymentReceiptResponse refundPayment(UUID botUserId, BotApiRefundPaymentRequest request) {
        featureFlagService.requirePaymentsEnabled();
        requireBot(botUserId);
        BotPaymentReceiptEntity receipt = botPaymentReceiptRepository.findById(request.receiptId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot payment receipt not found"));
        if (!botUserId.equals(receipt.getBotUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bot payment receipt does not belong to this bot");
        }
        return refundPaymentInternal(receipt, normalizeOptional(request.reason(), 255));
    }

    @Transactional
    public void syncRefundedPayment(UUID paymentIntentId, Instant refundedAt, String reason) {
        BotPaymentReceiptEntity receipt = botPaymentReceiptRepository.findByPaymentIntentId(paymentIntentId).orElse(null);
        if (receipt == null || receipt.getRefundedAt() != null) {
            return;
        }
        applyRefundRuntime(receipt, refundedAt != null ? refundedAt : Instant.now(), normalizeOptional(reason, 255), false);
    }

    @Transactional
    public BotPreCheckoutQueryResponse createPreCheckoutQuery(
            UUID requesterId,
            ChatMessageResponse message,
            BotMessageActionEntity action
    ) {
        featureFlagService.requirePaymentsEnabled();
        if (!"PAY".equals(action.getActionType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bot message action is not payable");
        }

        BotPaymentInvoiceEntity botInvoice = requireBotInvoice(action.getPaymentInvoiceId());
        if (!message.messageId().equals(botInvoice.getMessageId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot payment invoice not found");
        }
        chatService.getOwnedChat(requesterId, botInvoice.getChatId());
        if (!requesterId.equals(botInvoice.getPayerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Payment is not available for this user");
        }

        PaymentInvoiceEntity invoice = requireOpenPaymentInvoice(botInvoice.getPaymentInvoiceId());
        BotPreCheckoutQueryEntity query = new BotPreCheckoutQueryEntity();
        query.setBotUserId(botInvoice.getBotUserId());
        query.setChatId(botInvoice.getChatId());
        query.setMessageId(botInvoice.getMessageId());
        query.setPaymentInvoiceId(botInvoice.getPaymentInvoiceId());
        query.setFromUserId(requesterId);
        query.setStatus(STATUS_PENDING);
        BotPreCheckoutQueryEntity savedQuery = botPreCheckoutQueryRepository.save(query);

        BotUpdateEntity update = new BotUpdateEntity();
        update.setBotUserId(botInvoice.getBotUserId());
        update.setChatId(botInvoice.getChatId());
        update.setMessageId(botInvoice.getMessageId());
        update.setUpdateType("PRE_CHECKOUT_QUERY");
        update.setPreCheckoutQueryId(savedQuery.getId());
        botUpdateRepository.save(update);

        return toPreCheckoutQueryResponse(savedQuery, botInvoice, invoice);
    }

    @Transactional
    public BotPreCheckoutQueryResponse answerPreCheckoutQuery(UUID botUserId, BotApiAnswerPreCheckoutQueryRequest request) {
        featureFlagService.requirePaymentsEnabled();
        requireBot(botUserId);
        BotPreCheckoutQueryEntity query = botPreCheckoutQueryRepository.findByIdAndBotUserId(
                        request.preCheckoutQueryId(),
                        botUserId
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pre-checkout query not found"));
        if (!STATUS_PENDING.equals(query.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pre-checkout query can no longer be answered");
        }

        query.setStatus(Boolean.TRUE.equals(request.ok()) ? STATUS_APPROVED : STATUS_DECLINED);
        query.setAnswerText(normalizeOptional(request.text(), 255));
        query.setAnsweredAt(Instant.now());
        BotPreCheckoutQueryEntity savedQuery = botPreCheckoutQueryRepository.save(query);

        BotPaymentInvoiceEntity botInvoice = requireBotInvoice(savedQuery.getPaymentInvoiceId());
        PaymentInvoiceEntity invoice = requirePaymentInvoice(savedQuery.getPaymentInvoiceId());
        return toPreCheckoutQueryResponse(savedQuery, botInvoice, invoice);
    }

    @Transactional
    public BotSuccessfulPaymentResponse completePreCheckout(UUID requesterId, UUID preCheckoutQueryId) {
        return completePreCheckout(requesterId, preCheckoutQueryId, null);
    }

    @Transactional
    public BotSuccessfulPaymentResponse completePreCheckout(
            UUID requesterId,
            UUID preCheckoutQueryId,
            CompleteBotPreCheckoutRequest request
    ) {
        featureFlagService.requirePaymentsEnabled();
        BotPreCheckoutQueryEntity query = botPreCheckoutQueryRepository.findByIdAndFromUserId(preCheckoutQueryId, requesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pre-checkout query not found"));
        BotPaymentInvoiceEntity botInvoice = requireBotInvoice(query.getPaymentInvoiceId());
        chatService.getOwnedChat(requesterId, botInvoice.getChatId());
        if (!requesterId.equals(botInvoice.getPayerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Payment is not available for this user");
        }

        PaymentInvoiceEntity invoice = requirePaymentInvoice(botInvoice.getPaymentInvoiceId());
        if (STATUS_COMPLETED.equals(query.getStatus())) {
            BotPaymentReceiptEntity existingReceipt = requireReceipt(query.getReceiptId());
            return toSuccessfulPaymentResponse(query, botInvoice, existingReceipt);
        }
        if (!STATUS_APPROVED.equals(query.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pre-checkout query has not been approved");
        }
        requireOpenPaymentInvoice(botInvoice.getPaymentInvoiceId());

        CheckoutSelection selection = resolveCheckoutSelection(botInvoice, invoice, request);
        PaymentIntentResponse intent = paymentService.createPaymentIntent(
                requesterId,
                botInvoice.getPaymentInvoiceId(),
                selection.totalAmountUnits()
        );
        PaymentIntentResponse confirmedIntent = paymentService.confirmIntent(requesterId, intent.paymentIntentId());
        Instant completedAt = confirmedIntent.confirmedAt() != null ? confirmedIntent.confirmedAt() : Instant.now();

        ChatMessageResponse serviceMessage = messageService.sendInternalServiceMessage(
                botInvoice.getBotUserId(),
                botInvoice.getChatId(),
                "BOT_PAYMENT_SUCCESS",
                buildSuccessfulPaymentText(invoice, selection)
        );

        BotPaymentReceiptEntity receipt = new BotPaymentReceiptEntity();
        receipt.setPaymentInvoiceId(botInvoice.getPaymentInvoiceId());
        receipt.setPaymentIntentId(confirmedIntent.paymentIntentId());
        receipt.setPreCheckoutQueryId(query.getId());
        receipt.setBotUserId(botInvoice.getBotUserId());
        receipt.setChatId(botInvoice.getChatId());
        receipt.setInvoiceMessageId(botInvoice.getMessageId());
        receipt.setServiceMessageId(serviceMessage.messageId());
        receipt.setPayerUserId(query.getFromUserId());
        receipt.setTitle(invoice.getTitle());
        receipt.setDescription(invoice.getDescription());
        receipt.setInvoicePayload(botInvoice.getInvoicePayload());
        receipt.setCurrencyCode(invoice.getCurrencyCode());
        receipt.setBaseAmountUnits(invoice.getAmountUnits());
        receipt.setShippingAmountUnits(selection.shippingAmountUnits());
        receipt.setTipAmountUnits(selection.tipAmountUnits());
        receipt.setTotalAmountUnits(selection.totalAmountUnits());
        receipt.setProviderToken(botInvoice.getProviderToken());
        receipt.setProviderDataJson(botInvoice.getProviderDataJson());
        receipt.setPayerName(selection.payerName());
        receipt.setPhoneNumber(selection.phoneNumber());
        receipt.setEmail(selection.email());
        receipt.setShippingAddressJson(serializeShippingAddress(selection.shippingAddress()));
        receipt.setShippingOptionId(selection.shippingOption() != null ? selection.shippingOption().optionId() : null);
        receipt.setShippingOptionTitle(selection.shippingOption() != null ? selection.shippingOption().title() : null);
        receipt = botPaymentReceiptRepository.save(receipt);

        query.setPaymentIntentId(confirmedIntent.paymentIntentId());
        query.setStatus(STATUS_COMPLETED);
        query.setRequestedName(selection.payerName());
        query.setRequestedPhoneNumber(selection.phoneNumber());
        query.setRequestedEmail(selection.email());
        query.setShippingAddressJson(serializeShippingAddress(selection.shippingAddress()));
        query.setShippingOptionId(selection.shippingOption() != null ? selection.shippingOption().optionId() : null);
        query.setShippingOptionTitle(selection.shippingOption() != null ? selection.shippingOption().title() : null);
        query.setShippingOptionAmountUnits(selection.shippingAmountUnits());
        query.setTipAmountUnits(selection.tipAmountUnits());
        query.setTotalAmountUnits(selection.totalAmountUnits());
        query.setReceiptId(receipt.getId());
        query.setCompletedAt(completedAt);
        BotPreCheckoutQueryEntity savedQuery = botPreCheckoutQueryRepository.save(query);

        botInvoice.setSuccessfulPaymentMessageId(serviceMessage.messageId());
        botPaymentInvoiceRepository.save(botInvoice);

        BotUpdateEntity update = new BotUpdateEntity();
        update.setBotUserId(botInvoice.getBotUserId());
        update.setChatId(botInvoice.getChatId());
        update.setMessageId(botInvoice.getMessageId());
        update.setUpdateType("SUCCESSFUL_PAYMENT");
        update.setPreCheckoutQueryId(savedQuery.getId());
        botUpdateRepository.save(update);

        return toSuccessfulPaymentResponse(savedQuery, botInvoice, receipt);
    }

    private BotPaymentInvoiceResponse toInvoiceResponse(BotPaymentInvoiceEntity botInvoice, PaymentInvoiceEntity invoice) {
        return new BotPaymentInvoiceResponse(
                botInvoice.getPaymentInvoiceId(),
                botInvoice.getBotUserId(),
                botInvoice.getChatId(),
                botInvoice.getMessageId(),
                botInvoice.getPayerUserId(),
                invoice.getTitle(),
                invoice.getDescription(),
                invoice.getAmountUnits(),
                invoice.getCurrencyCode(),
                invoice.getStatus(),
                botInvoice.getInvoicePayload(),
                botInvoice.getPayButtonText(),
                botInvoice.isNeedName(),
                botInvoice.isNeedPhoneNumber(),
                botInvoice.isNeedEmail(),
                botInvoice.isNeedShippingAddress(),
                botInvoice.isFlexible(),
                botInvoice.getMaxTipAmountUnits(),
                deserializeLongList(botInvoice.getSuggestedTipAmountsJson()),
                deserializeShippingOptions(botInvoice.getShippingOptionsJson()),
                botInvoice.getSuccessfulPaymentMessageId(),
                invoice.getCreatedAt(),
                invoice.getExpiresAt()
        );
    }

    private BotPreCheckoutQueryResponse toPreCheckoutQueryResponse(
            BotPreCheckoutQueryEntity query,
            BotPaymentInvoiceEntity botInvoice,
            PaymentInvoiceEntity invoice
    ) {
        return new BotPreCheckoutQueryResponse(
                query.getId(),
                query.getBotUserId(),
                query.getChatId(),
                query.getMessageId(),
                query.getPaymentInvoiceId(),
                query.getFromUserId(),
                query.getPaymentIntentId(),
                invoice.getTitle(),
                invoice.getDescription(),
                invoice.getAmountUnits(),
                invoice.getCurrencyCode(),
                query.getStatus(),
                botInvoice.getInvoicePayload(),
                botInvoice.isNeedName(),
                botInvoice.isNeedPhoneNumber(),
                botInvoice.isNeedEmail(),
                botInvoice.isNeedShippingAddress(),
                botInvoice.isFlexible(),
                botInvoice.getMaxTipAmountUnits(),
                deserializeLongList(botInvoice.getSuggestedTipAmountsJson()),
                deserializeShippingOptions(botInvoice.getShippingOptionsJson()),
                query.getRequestedName(),
                query.getRequestedPhoneNumber(),
                query.getRequestedEmail(),
                deserializeShippingAddress(query.getShippingAddressJson()),
                query.getShippingOptionId(),
                query.getShippingOptionTitle(),
                query.getShippingOptionAmountUnits() != null ? query.getShippingOptionAmountUnits() : 0L,
                query.getTipAmountUnits() != null ? query.getTipAmountUnits() : 0L,
                query.getTotalAmountUnits() != null ? query.getTotalAmountUnits() : invoice.getAmountUnits(),
                query.getReceiptId(),
                query.getAnswerText(),
                query.getCreatedAt(),
                query.getAnsweredAt(),
                query.getCompletedAt()
        );
    }

    private BotSuccessfulPaymentResponse toSuccessfulPaymentResponse(
            BotPreCheckoutQueryEntity query,
            BotPaymentInvoiceEntity botInvoice,
            BotPaymentReceiptEntity receipt
    ) {
        return new BotSuccessfulPaymentResponse(
                query.getId(),
                query.getBotUserId(),
                query.getChatId(),
                query.getMessageId(),
                query.getPaymentInvoiceId(),
                query.getPaymentIntentId(),
                query.getFromUserId(),
                receipt.getTitle(),
                receipt.getDescription(),
                receipt.getBaseAmountUnits(),
                receipt.getShippingAmountUnits(),
                receipt.getTipAmountUnits(),
                receipt.getTotalAmountUnits(),
                receipt.getCurrencyCode(),
                receipt.getInvoicePayload(),
                botInvoice.isNeedName(),
                botInvoice.isNeedPhoneNumber(),
                botInvoice.isNeedEmail(),
                botInvoice.isNeedShippingAddress(),
                botInvoice.isFlexible(),
                receipt.getPayerName(),
                receipt.getPhoneNumber(),
                receipt.getEmail(),
                deserializeShippingAddress(receipt.getShippingAddressJson()),
                receipt.getShippingOptionId(),
                receipt.getShippingOptionTitle(),
                botInvoice.getMaxTipAmountUnits(),
                deserializeLongList(botInvoice.getSuggestedTipAmountsJson()),
                deserializeShippingOptions(botInvoice.getShippingOptionsJson()),
                receipt.getId(),
                receipt.getServiceMessageId(),
                receipt.getRefundMessageId(),
                query.getCreatedAt(),
                query.getCompletedAt(),
                query.getCompletedAt(),
                receipt.getRefundedAt()
        );
    }

    private BotPaymentReceiptResponse toReceiptResponse(BotPaymentReceiptEntity receipt, BotPaymentInvoiceEntity botInvoice) {
        return new BotPaymentReceiptResponse(
                receipt.getId(),
                receipt.getPaymentInvoiceId(),
                receipt.getPaymentIntentId(),
                receipt.getPreCheckoutQueryId(),
                receipt.getBotUserId(),
                receipt.getChatId(),
                receipt.getInvoiceMessageId(),
                receipt.getServiceMessageId(),
                receipt.getRefundMessageId(),
                receipt.getPayerUserId(),
                receipt.getTitle(),
                receipt.getDescription(),
                receipt.getInvoicePayload(),
                receipt.getCurrencyCode(),
                receipt.getBaseAmountUnits(),
                receipt.getShippingAmountUnits(),
                receipt.getTipAmountUnits(),
                receipt.getTotalAmountUnits(),
                botInvoice.isNeedName(),
                botInvoice.isNeedPhoneNumber(),
                botInvoice.isNeedEmail(),
                botInvoice.isNeedShippingAddress(),
                botInvoice.isFlexible(),
                receipt.getPayerName(),
                receipt.getPhoneNumber(),
                receipt.getEmail(),
                deserializeShippingAddress(receipt.getShippingAddressJson()),
                receipt.getShippingOptionId(),
                receipt.getShippingOptionTitle(),
                botInvoice.getMaxTipAmountUnits(),
                deserializeLongList(botInvoice.getSuggestedTipAmountsJson()),
                deserializeShippingOptions(botInvoice.getShippingOptionsJson()),
                deserializeProviderData(receipt.getProviderDataJson()),
                receipt.getCreatedAt(),
                receipt.getRefundedAt()
        );
    }

    private BotPaymentReceiptResponse refundPaymentInternal(BotPaymentReceiptEntity receipt, String reason) {
        if (receipt.getRefundedAt() == null) {
            PaymentIntentResponse refundedIntent = paymentService.refundIntent(
                    receipt.getBotUserId(),
                    receipt.getPaymentIntentId(),
                    new UpdatePaymentIntentRequest(reason)
            );
            applyRefundRuntime(
                    receipt,
                    refundedIntent.refundedAt() != null ? refundedIntent.refundedAt() : Instant.now(),
                    reason,
                    true
            );
        }
        BotPaymentInvoiceEntity botInvoice = requireBotInvoice(receipt.getPaymentInvoiceId());
        return toReceiptResponse(receipt, botInvoice);
    }

    private void applyRefundRuntime(
            BotPaymentReceiptEntity receipt,
            Instant refundedAt,
            String reason,
            boolean enqueueBotUpdate
    ) {
        if (receipt.getRefundedAt() != null) {
            return;
        }
        ChatMessageResponse refundMessage = messageService.sendInternalServiceMessage(
                receipt.getBotUserId(),
                receipt.getChatId(),
                "BOT_PAYMENT_REFUND",
                buildRefundText(receipt, reason)
        );
        receipt.setRefundedAt(refundedAt);
        receipt.setRefundMessageId(refundMessage.messageId());
        receipt = botPaymentReceiptRepository.save(receipt);

        if (enqueueBotUpdate) {
            BotUpdateEntity update = new BotUpdateEntity();
            update.setBotUserId(receipt.getBotUserId());
            update.setChatId(receipt.getChatId());
            update.setMessageId(receipt.getInvoiceMessageId());
            update.setUpdateType("REFUNDED_PAYMENT");
            update.setPreCheckoutQueryId(receipt.getPreCheckoutQueryId());
            botUpdateRepository.save(update);
        }
    }

    private CheckoutSelection resolveCheckoutSelection(
            BotPaymentInvoiceEntity botInvoice,
            PaymentInvoiceEntity invoice,
            CompleteBotPreCheckoutRequest request
    ) {
        String payerName = normalizeOptional(request != null ? request.payerName() : null, 120);
        String phoneNumber = normalizeOptional(request != null ? request.phoneNumber() : null, 64);
        String email = normalizeOptional(request != null ? request.email() : null, 120);
        BotPaymentShippingAddressPayload shippingAddress = normalizeShippingAddress(request != null ? request.shippingAddress() : null);
        String shippingOptionId = normalizeOptional(request != null ? request.shippingOptionId() : null, 64);
        Long tipAmountUnits = normalizeTipAmount(request != null ? request.tipAmountUnits() : null, botInvoice.getMaxTipAmountUnits());
        List<BotPaymentShippingOptionPayload> shippingOptions = deserializeShippingOptions(botInvoice.getShippingOptionsJson());

        if (botInvoice.isNeedName() && payerName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payer name is required");
        }
        if (botInvoice.isNeedPhoneNumber() && phoneNumber == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number is required");
        }
        if (botInvoice.isNeedEmail() && email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        if (botInvoice.isNeedShippingAddress() && shippingAddress == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shipping address is required");
        }
        if (!botInvoice.isNeedShippingAddress() && shippingAddress != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shipping address is not required for this invoice");
        }

        BotPaymentShippingOptionPayload shippingOption = null;
        if (!shippingOptions.isEmpty()) {
            if (shippingOptionId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shipping option is required");
            }
            shippingOption = shippingOptions.stream()
                    .filter(option -> option.optionId().equals(shippingOptionId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shipping option is invalid"));
        } else if (shippingOptionId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shipping option is not supported for this invoice");
        }

        long shippingAmountUnits = shippingOption != null ? shippingOption.amountUnits() : 0L;
        long totalAmountUnits = invoice.getAmountUnits() + shippingAmountUnits + tipAmountUnits;
        return new CheckoutSelection(payerName, phoneNumber, email, shippingAddress, shippingOption, shippingAmountUnits, tipAmountUnits, totalAmountUnits);
    }

    private void validateCheckoutRequirements(
            boolean needShippingAddress,
            boolean flexible,
            List<BotPaymentShippingOptionPayload> shippingOptions,
            Long maxTipAmountUnits,
            List<Long> suggestedTipAmounts
    ) {
        if (flexible && !needShippingAddress) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Flexible invoices require shipping address");
        }
        if (!needShippingAddress && !shippingOptions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shipping options require shipping address");
        }
        if (flexible && shippingOptions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Flexible invoices require shipping options");
        }
        if (maxTipAmountUnits == null && !suggestedTipAmounts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suggested tip amounts require max tip amount");
        }
    }

    private PaymentInvoiceEntity requireOpenPaymentInvoice(UUID paymentInvoiceId) {
        PaymentInvoiceEntity invoice = requirePaymentInvoice(paymentInvoiceId);
        if (!"OPEN".equals(invoice.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bot payment invoice is no longer payable");
        }
        if (invoice.getExpiresAt() != null && !invoice.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Bot payment invoice has expired");
        }
        return invoice;
    }

    private PaymentInvoiceEntity requirePaymentInvoice(UUID paymentInvoiceId) {
        return paymentInvoiceRepository.findById(paymentInvoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment invoice not found"));
    }

    private BotPaymentInvoiceEntity requireBotInvoice(UUID paymentInvoiceId) {
        return botPaymentInvoiceRepository.findById(paymentInvoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot payment invoice not found"));
    }

    private BotPaymentReceiptEntity requireReceipt(UUID receiptId) {
        return botPaymentReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot payment receipt not found"));
    }

    private void requireBot(UUID botUserId) {
        if (botAccountRepository.findById(botUserId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found");
        }
    }

    private UUID resolvePayerUserId(UUID botUserId, UUID chatId, UUID recipientUserId) {
        if (chatId == null && recipientUserId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat or recipient is required");
        }
        UUID resolvedRecipientId = recipientUserId;
        if (chatId != null) {
            ChatEntity chat = chatService.getOwnedChat(botUserId, chatId);
            if (!"DIRECT".equals(chat.getChatType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bot payments are only supported in direct chats");
            }
            UUID peerUserId = chatService.getPeerUserId(chat, botUserId);
            if (resolvedRecipientId != null && !resolvedRecipientId.equals(peerUserId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient does not match direct chat peer");
            }
            resolvedRecipientId = peerUserId;
        }

        if (resolvedRecipientId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient is required");
        }
        if (botUserId.equals(resolvedRecipientId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bot cannot invoice itself");
        }
        UserEntity payer = userRepository.findById(resolvedRecipientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (payer.isBot()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bot payments require a human payer");
        }
        return payer.getId();
    }

    private String buildInvoiceMessageText(String title, String description, long amountUnits, String currencyCode) {
        String normalizedTitle = normalizeRequired(title, "Invoice title", 120);
        String normalizedDescription = normalizeOptional(description, 500);
        StringBuilder builder = new StringBuilder(normalizedTitle)
                .append("\nAmount: ")
                .append(amountUnits)
                .append(' ')
                .append(currencyCode != null ? currencyCode : "XTR");
        if (normalizedDescription != null) {
            builder.append("\n").append(normalizedDescription);
        }
        return builder.toString();
    }

    private String buildSuccessfulPaymentText(PaymentInvoiceEntity invoice, CheckoutSelection selection) {
        StringBuilder builder = new StringBuilder("Payment successful: ")
                .append(invoice.getTitle())
                .append(" (")
                .append(selection.totalAmountUnits())
                .append(' ')
                .append(invoice.getCurrencyCode())
                .append(')');
        if (selection.tipAmountUnits() > 0) {
            builder.append("\nTip: ").append(selection.tipAmountUnits()).append(' ').append(invoice.getCurrencyCode());
        }
        if (selection.shippingAmountUnits() > 0 && selection.shippingOption() != null) {
            builder.append("\nShipping: ").append(selection.shippingOption().title());
        }
        return builder.toString();
    }

    private String buildRefundText(BotPaymentReceiptEntity receipt, String reason) {
        StringBuilder builder = new StringBuilder("Payment refunded: ")
                .append(receipt.getTitle())
                .append(" (")
                .append(receipt.getTotalAmountUnits())
                .append(' ')
                .append(receipt.getCurrencyCode())
                .append(')');
        if (reason != null) {
            builder.append("\nReason: ").append(reason);
        }
        return builder.toString();
    }

    private Map<String, String> normalizeProviderData(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = normalizeRequired(entry.getKey(), "Provider data key", 64);
            String value = normalizeRequired(entry.getValue(), "Provider data value", 255);
            normalized.put(key, value);
        }
        return normalized;
    }

    private String serializeProviderData(Map<String, String> providerData) {
        try {
            return objectMapper.writeValueAsString(providerData != null ? providerData : Map.of());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store bot payment provider data", exception);
        }
    }

    private Map<String, String> deserializeProviderData(String rawValue) {
        try {
            if (rawValue == null || rawValue.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(rawValue, new TypeReference<Map<String, String>>() { });
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to load bot payment provider data", exception);
        }
    }

    private String serializeLongList(List<Long> values) {
        try {
            return objectMapper.writeValueAsString(values != null ? values : List.of());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store bot payment tips", exception);
        }
    }

    private List<Long> deserializeLongList(String rawValue) {
        try {
            if (rawValue == null || rawValue.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(rawValue, new TypeReference<List<Long>>() { });
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to load bot payment tips", exception);
        }
    }

    private String serializeShippingOptions(List<BotPaymentShippingOptionPayload> shippingOptions) {
        try {
            return objectMapper.writeValueAsString(shippingOptions != null ? shippingOptions : List.of());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store shipping options", exception);
        }
    }

    private List<BotPaymentShippingOptionPayload> deserializeShippingOptions(String rawValue) {
        try {
            if (rawValue == null || rawValue.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(rawValue, new TypeReference<List<BotPaymentShippingOptionPayload>>() { });
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to load shipping options", exception);
        }
    }

    private String serializeShippingAddress(BotPaymentShippingAddressPayload shippingAddress) {
        if (shippingAddress == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(shippingAddress);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store shipping address", exception);
        }
    }

    private BotPaymentShippingAddressPayload deserializeShippingAddress(String rawValue) {
        try {
            if (rawValue == null || rawValue.isBlank()) {
                return null;
            }
            return objectMapper.readValue(rawValue, BotPaymentShippingAddressPayload.class);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to load shipping address", exception);
        }
    }

    private List<Long> normalizeSuggestedTipAmounts(List<Long> values, Long maxTipAmountUnits) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long value : values) {
            long amountUnits = normalizePositiveAmount(value, "Suggested tip amount");
            if (maxTipAmountUnits != null && amountUnits > maxTipAmountUnits) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suggested tip amount exceeds max tip amount");
            }
            normalized.add(amountUnits);
        }
        return normalized.stream().sorted().toList();
    }

    private List<BotPaymentShippingOptionPayload> normalizeShippingOptions(List<BotPaymentShippingOptionPayload> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, BotPaymentShippingOptionPayload> normalized = new LinkedHashMap<>();
        for (BotPaymentShippingOptionPayload option : values) {
            if (option == null) {
                continue;
            }
            String optionId = normalizeRequired(option.optionId(), "Shipping option id", 64);
            if (normalized.containsKey(optionId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shipping option ids must be unique");
            }
            normalized.put(
                    optionId,
                    new BotPaymentShippingOptionPayload(
                            optionId,
                            normalizeRequired(option.title(), "Shipping option title", 120),
                            normalizeNonNegative(option.amountUnits(), "Shipping option amount")
                    )
            );
        }
        return new ArrayList<>(normalized.values());
    }

    private BotPaymentShippingAddressPayload normalizeShippingAddress(BotPaymentShippingAddressPayload value) {
        if (value == null) {
            return null;
        }
        return new BotPaymentShippingAddressPayload(
                normalizeRequired(value.countryCode(), "Country code", 16).toUpperCase(),
                normalizeOptional(value.state(), 120),
                normalizeRequired(value.city(), "City", 120),
                normalizeRequired(value.streetLine1(), "Street line 1", 255),
                normalizeOptional(value.streetLine2(), 255),
                normalizeRequired(value.postCode(), "Post code", 32)
        );
    }

    private long normalizeTipAmount(Long value, Long maxTipAmountUnits) {
        if (value == null) {
            return 0L;
        }
        long normalized = normalizeNonNegative(value, "Tip amount");
        if (maxTipAmountUnits == null) {
            if (normalized > 0L) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tips are not supported for this invoice");
            }
            return 0L;
        }
        if (normalized > maxTipAmountUnits) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tip amount exceeds max tip amount");
        }
        return normalized;
    }

    private Long normalizeOptionalPositiveAmount(Long value, String field) {
        if (value == null) {
            return null;
        }
        return normalizePositiveAmount(value, field);
    }

    private long normalizePositiveAmount(Long value, String field) {
        if (value == null || value <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be positive");
        }
        return value;
    }

    private long normalizeNonNegative(Long value, String field) {
        if (value == null || value < 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must not be negative");
        }
        return value;
    }

    private String normalizeRequired(String value, String field, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value is too long");
        }
        return normalized;
    }

    private record CheckoutSelection(
            String payerName,
            String phoneNumber,
            String email,
            BotPaymentShippingAddressPayload shippingAddress,
            BotPaymentShippingOptionPayload shippingOption,
            long shippingAmountUnits,
            long tipAmountUnits,
            long totalAmountUnits
    ) {
    }
}
