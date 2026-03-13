package com.alex.messenger.payments;

import com.alex.messenger.payments.dto.CreateInvoiceRequest;
import com.alex.messenger.payments.dto.CreatePaymentIntentRequest;
import com.alex.messenger.payments.dto.PaymentIntentResponse;
import com.alex.messenger.payments.dto.PaymentInvoiceResponse;
import com.alex.messenger.payments.dto.PaymentTransactionResponse;
import com.alex.messenger.payments.dto.PaymentWalletResponse;
import com.alex.messenger.payments.dto.TopUpWalletRequest;
import com.alex.messenger.payments.dto.UpdatePaymentIntentRequest;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
public class PaymentService {

    private static final String CURRENCY_CODE = "XTR";

    private final PaymentWalletAccountRepository paymentWalletAccountRepository;
    private final PaymentWalletTransactionRepository paymentWalletTransactionRepository;
    private final PaymentInvoiceRepository paymentInvoiceRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PaymentWalletResponse getWallet(UUID requesterId) {
        requireUser(requesterId);
        return toWalletResponse(getOrCreateWallet(requesterId));
    }

    @Transactional
    public PaymentWalletResponse topUpWallet(UUID requesterId, TopUpWalletRequest request) {
        requireUser(requesterId);
        long amountUnits = normalizeAmount(request != null ? request.amountUnits() : null, "Top-up amount");
        PaymentWalletAccountEntity wallet = getOrCreateWallet(requesterId);
        wallet.setBalanceUnits(wallet.getBalanceUnits() + amountUnits);
        wallet = paymentWalletAccountRepository.save(wallet);
        recordTransaction(
                wallet,
                null,
                null,
                null,
                "TOP_UP",
                "CREDIT",
                amountUnits,
                normalizeOptional(request != null ? request.description() : null, 255)
        );
        return toWalletResponse(wallet);
    }

    @Transactional(readOnly = true)
    public List<PaymentTransactionResponse> listTransactions(UUID requesterId) {
        requireUser(requesterId);
        getOrCreateWallet(requesterId);
        return paymentWalletTransactionRepository.findAllByWalletUserIdOrderByCreatedAtDesc(requesterId).stream()
                .map(this::toTransactionResponse)
                .toList();
    }

    @Transactional
    public PaymentInvoiceResponse createInvoice(UUID requesterId, CreateInvoiceRequest request) {
        return createInvoiceInternal(requesterId, request, false);
    }

    @Transactional
    public PaymentInvoiceResponse createSelfInvoice(
            UUID requesterId,
            String title,
            String description,
            Long amountUnits,
            Instant expiresAt,
            Map<String, String> metadata
    ) {
        return createInvoiceInternal(
                requesterId,
                new CreateInvoiceRequest(requesterId, title, description, amountUnits, expiresAt, metadata),
                true
        );
    }

    @Transactional(readOnly = true)
    public List<PaymentInvoiceResponse> listCreatedInvoices(UUID requesterId) {
        requireUser(requesterId);
        return paymentInvoiceRepository.findAllByCreatedByUserIdOrderByCreatedAtDesc(requesterId).stream()
                .map(this::toInvoiceResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentInvoiceResponse> listReceivableInvoices(UUID requesterId) {
        requireUser(requesterId);
        return paymentInvoiceRepository.findAllByRecipientUserIdOrderByCreatedAtDesc(requesterId).stream()
                .map(this::toInvoiceResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentInvoiceResponse getInvoice(UUID requesterId, UUID invoiceId) {
        requireUser(requesterId);
        PaymentInvoiceEntity invoice = getAccessibleInvoice(requesterId, invoiceId);
        return toInvoiceResponse(invoice);
    }

    @Transactional
    public PaymentInvoiceResponse cancelInvoice(UUID requesterId, UUID invoiceId) {
        requireUser(requesterId);
        PaymentInvoiceEntity invoice = paymentInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment invoice not found"));
        if (!requesterId.equals(invoice.getCreatedByUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only invoice creator can cancel invoice");
        }
        if (!"OPEN".equals(invoice.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice can no longer be canceled");
        }
        invoice.setStatus("CANCELED");
        return toInvoiceResponse(paymentInvoiceRepository.save(invoice));
    }

    @Transactional
    public PaymentIntentResponse createPaymentIntent(UUID requesterId, CreatePaymentIntentRequest request) {
        return createPaymentIntent(requesterId, request.invoiceId(), null);
    }

    @Transactional
    public PaymentIntentResponse createPaymentIntent(UUID requesterId, UUID invoiceId, Long amountUnitsOverride) {
        requireUser(requesterId);
        PaymentInvoiceEntity invoice = paymentInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment invoice not found"));
        ensureInvoicePayable(invoice, requesterId);
        long amountUnits = amountUnitsOverride != null
                ? normalizeAmount(amountUnitsOverride, "Payment amount")
                : invoice.getAmountUnits();

        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setInvoiceId(invoice.getId());
        intent.setPayerUserId(requesterId);
        intent.setRecipientUserId(invoice.getRecipientUserId());
        intent.setAmountUnits(amountUnits);
        intent.setCurrencyCode(invoice.getCurrencyCode());
        intent.setStatus("PENDING");
        return toIntentResponse(paymentIntentRepository.save(intent));
    }

    @Transactional(readOnly = true)
    public List<PaymentIntentResponse> listOutgoingIntents(UUID requesterId) {
        requireUser(requesterId);
        return paymentIntentRepository.findAllByPayerUserIdOrderByCreatedAtDesc(requesterId).stream()
                .map(this::toIntentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentIntentResponse> listIncomingIntents(UUID requesterId) {
        requireUser(requesterId);
        return paymentIntentRepository.findAllByRecipientUserIdOrderByCreatedAtDesc(requesterId).stream()
                .map(this::toIntentResponse)
                .toList();
    }

    @Transactional
    public PaymentIntentResponse confirmIntent(UUID requesterId, UUID intentId) {
        requireUser(requesterId);
        PaymentIntentEntity intent = paymentIntentRepository.findById(intentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment intent not found"));
        if (!requesterId.equals(intent.getPayerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only payer can confirm payment");
        }
        if (!"PENDING".equals(intent.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment intent can no longer be confirmed");
        }

        PaymentInvoiceEntity invoice = paymentInvoiceRepository.findById(intent.getInvoiceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment invoice not found"));
        ensureInvoicePayable(invoice, requesterId);

        PaymentWalletAccountEntity payerWallet = getOrCreateWallet(intent.getPayerUserId());
        PaymentWalletAccountEntity recipientWallet = getOrCreateWallet(intent.getRecipientUserId());
        if (payerWallet.getBalanceUnits() < intent.getAmountUnits()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient wallet balance");
        }

        payerWallet.setBalanceUnits(payerWallet.getBalanceUnits() - intent.getAmountUnits());
        recipientWallet.setBalanceUnits(recipientWallet.getBalanceUnits() + intent.getAmountUnits());
        payerWallet = paymentWalletAccountRepository.save(payerWallet);
        recipientWallet = paymentWalletAccountRepository.save(recipientWallet);

        recordTransaction(
                payerWallet,
                recipientWallet.getUserId(),
                invoice.getId(),
                intent.getId(),
                "PAYMENT",
                "DEBIT",
                intent.getAmountUnits(),
                "Paid invoice %s".formatted(invoice.getTitle())
        );
        recordTransaction(
                recipientWallet,
                payerWallet.getUserId(),
                invoice.getId(),
                intent.getId(),
                "PAYMENT",
                "CREDIT",
                intent.getAmountUnits(),
                "Received payment for invoice %s".formatted(invoice.getTitle())
        );

        Instant now = Instant.now();
        intent.setStatus("COMPLETED");
        intent.setConfirmedAt(now);
        PaymentIntentEntity savedIntent = paymentIntentRepository.save(intent);
        invoice.setStatus("PAID");
        paymentInvoiceRepository.save(invoice);
        return toIntentResponse(savedIntent);
    }

    @Transactional
    public PaymentIntentResponse cancelIntent(UUID requesterId, UUID intentId, UpdatePaymentIntentRequest request) {
        requireUser(requesterId);
        PaymentIntentEntity intent = paymentIntentRepository.findById(intentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment intent not found"));
        if (!requesterId.equals(intent.getPayerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only payer can cancel payment intent");
        }
        if (!"PENDING".equals(intent.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment intent can no longer be canceled");
        }
        intent.setStatus("CANCELED");
        intent.setCanceledAt(Instant.now());
        intent.setCanceledReason(normalizeOptional(request != null ? request.reason() : null, 255));
        return toIntentResponse(paymentIntentRepository.save(intent));
    }

    @Transactional
    public PaymentIntentResponse refundIntent(UUID requesterId, UUID intentId, UpdatePaymentIntentRequest request) {
        requireUser(requesterId);
        PaymentIntentEntity intent = paymentIntentRepository.findById(intentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment intent not found"));
        if (!requesterId.equals(intent.getRecipientUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only recipient can refund payment");
        }
        if (!"COMPLETED".equals(intent.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only completed payments can be refunded");
        }

        PaymentWalletAccountEntity payerWallet = getOrCreateWallet(intent.getPayerUserId());
        PaymentWalletAccountEntity recipientWallet = getOrCreateWallet(intent.getRecipientUserId());
        if (recipientWallet.getBalanceUnits() < intent.getAmountUnits()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Recipient wallet does not have enough balance to refund");
        }

        recipientWallet.setBalanceUnits(recipientWallet.getBalanceUnits() - intent.getAmountUnits());
        payerWallet.setBalanceUnits(payerWallet.getBalanceUnits() + intent.getAmountUnits());
        recipientWallet = paymentWalletAccountRepository.save(recipientWallet);
        payerWallet = paymentWalletAccountRepository.save(payerWallet);

        PaymentInvoiceEntity invoice = paymentInvoiceRepository.findById(intent.getInvoiceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment invoice not found"));

        recordTransaction(
                recipientWallet,
                payerWallet.getUserId(),
                invoice.getId(),
                intent.getId(),
                "REFUND",
                "DEBIT",
                intent.getAmountUnits(),
                "Refunded invoice %s".formatted(invoice.getTitle())
        );
        recordTransaction(
                payerWallet,
                recipientWallet.getUserId(),
                invoice.getId(),
                intent.getId(),
                "REFUND",
                "CREDIT",
                intent.getAmountUnits(),
                "Refund for invoice %s".formatted(invoice.getTitle())
        );

        intent.setStatus("REFUNDED");
        intent.setRefundedAt(Instant.now());
        intent.setRefundedReason(normalizeOptional(request != null ? request.reason() : null, 255));
        PaymentIntentEntity savedIntent = paymentIntentRepository.save(intent);
        invoice.setStatus("REFUNDED");
        paymentInvoiceRepository.save(invoice);
        return toIntentResponse(savedIntent);
    }

    private PaymentWalletAccountEntity getOrCreateWallet(UUID userId) {
        return paymentWalletAccountRepository.findById(userId).orElseGet(() -> {
            PaymentWalletAccountEntity account = new PaymentWalletAccountEntity();
            account.setUserId(userId);
            return paymentWalletAccountRepository.save(account);
        });
    }

    private PaymentInvoiceResponse createInvoiceInternal(
            UUID requesterId,
            CreateInvoiceRequest request,
            boolean allowSelfRecipient
    ) {
        requireUser(requesterId);
        if (!allowSelfRecipient && requesterId.equals(request.recipientUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice recipient must be another user");
        }
        requireUser(request.recipientUserId());
        long amountUnits = normalizeAmount(request.amountUnits(), "Invoice amount");
        Instant expiresAt = request.expiresAt();
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice expiration must be in the future");
        }

        PaymentInvoiceEntity invoice = new PaymentInvoiceEntity();
        invoice.setCreatedByUserId(requesterId);
        invoice.setRecipientUserId(request.recipientUserId());
        invoice.setTitle(normalizeRequired(request.title(), "Invoice title", 120));
        invoice.setDescription(normalizeOptional(request.description(), 500));
        invoice.setAmountUnits(amountUnits);
        invoice.setCurrencyCode(CURRENCY_CODE);
        invoice.setStatus("OPEN");
        invoice.setMetadataJson(serializeMetadata(request.metadata()));
        invoice.setExpiresAt(expiresAt);
        return toInvoiceResponse(paymentInvoiceRepository.save(invoice));
    }

    private PaymentInvoiceEntity getAccessibleInvoice(UUID requesterId, UUID invoiceId) {
        PaymentInvoiceEntity invoice = paymentInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment invoice not found"));
        if (!requesterId.equals(invoice.getCreatedByUserId()) && !requesterId.equals(invoice.getRecipientUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invoice access denied");
        }
        return invoice;
    }

    private void ensureInvoicePayable(PaymentInvoiceEntity invoice, UUID payerUserId) {
        if (!"OPEN".equals(invoice.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice is no longer payable");
        }
        if (invoice.getExpiresAt() != null && !invoice.getExpiresAt().isAfter(Instant.now())) {
            invoice.setStatus("EXPIRED");
            paymentInvoiceRepository.save(invoice);
            throw new ResponseStatusException(HttpStatus.GONE, "Invoice has expired");
        }
        if (payerUserId.equals(invoice.getRecipientUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice recipient cannot pay own invoice");
        }
    }

    private PaymentWalletTransactionEntity recordTransaction(
            PaymentWalletAccountEntity wallet,
            UUID counterpartyUserId,
            UUID invoiceId,
            UUID paymentIntentId,
            String transactionType,
            String direction,
            long amountUnits,
            String description
    ) {
        PaymentWalletTransactionEntity transaction = new PaymentWalletTransactionEntity();
        transaction.setWalletUserId(wallet.getUserId());
        transaction.setCounterpartyUserId(counterpartyUserId);
        transaction.setInvoiceId(invoiceId);
        transaction.setPaymentIntentId(paymentIntentId);
        transaction.setTransactionType(transactionType);
        transaction.setDirection(direction);
        transaction.setAmountUnits(amountUnits);
        transaction.setBalanceAfterUnits(wallet.getBalanceUnits());
        transaction.setCurrencyCode(wallet.getCurrencyCode());
        transaction.setDescription(description);
        return paymentWalletTransactionRepository.save(transaction);
    }

    private PaymentWalletResponse toWalletResponse(PaymentWalletAccountEntity wallet) {
        return new PaymentWalletResponse(
                wallet.getUserId(),
                wallet.getBalanceUnits(),
                wallet.getCurrencyCode(),
                wallet.getUpdatedAt()
        );
    }

    private PaymentTransactionResponse toTransactionResponse(PaymentWalletTransactionEntity transaction) {
        return new PaymentTransactionResponse(
                transaction.getId(),
                transaction.getWalletUserId(),
                transaction.getCounterpartyUserId(),
                transaction.getInvoiceId(),
                transaction.getPaymentIntentId(),
                transaction.getTransactionType(),
                transaction.getDirection(),
                transaction.getAmountUnits(),
                transaction.getBalanceAfterUnits(),
                transaction.getCurrencyCode(),
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
    }

    private PaymentInvoiceResponse toInvoiceResponse(PaymentInvoiceEntity invoice) {
        return new PaymentInvoiceResponse(
                invoice.getId(),
                invoice.getCreatedByUserId(),
                invoice.getRecipientUserId(),
                invoice.getTitle(),
                invoice.getDescription(),
                invoice.getAmountUnits(),
                invoice.getCurrencyCode(),
                invoice.getStatus(),
                deserializeMetadata(invoice.getMetadataJson()),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt(),
                invoice.getExpiresAt()
        );
    }

    private PaymentIntentResponse toIntentResponse(PaymentIntentEntity intent) {
        return new PaymentIntentResponse(
                intent.getId(),
                intent.getInvoiceId(),
                intent.getPayerUserId(),
                intent.getRecipientUserId(),
                intent.getAmountUnits(),
                intent.getCurrencyCode(),
                intent.getStatus(),
                intent.getCanceledReason(),
                intent.getRefundedReason(),
                intent.getCreatedAt(),
                intent.getConfirmedAt(),
                intent.getCanceledAt(),
                intent.getRefundedAt(),
                intent.getUpdatedAt()
        );
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private long normalizeAmount(Long amountUnits, String field) {
        if (amountUnits == null || amountUnits <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be positive");
        }
        return amountUnits;
    }

    private String normalizeRequired(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is too long");
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

    private String serializeMetadata(Map<String, String> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata != null ? metadata : Map.of());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store invoice metadata", exception);
        }
    }

    private Map<String, String> deserializeMetadata(String metadataJson) {
        try {
            if (metadataJson == null || metadataJson.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(metadataJson, new TypeReference<Map<String, String>>() { });
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to load invoice metadata", exception);
        }
    }
}
