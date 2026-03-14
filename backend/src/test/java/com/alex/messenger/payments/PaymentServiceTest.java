package com.alex.messenger.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.alex.messenger.payments.dto.CreateInvoiceRequest;
import com.alex.messenger.payments.dto.CreatePaymentIntentRequest;
import com.alex.messenger.payments.dto.TopUpWalletRequest;
import com.alex.messenger.payments.dto.UpdatePaymentIntentRequest;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentWalletAccountRepository paymentWalletAccountRepository;

    @Mock
    private PaymentWalletTransactionRepository paymentWalletTransactionRepository;

    @Mock
    private PaymentInvoiceRepository paymentInvoiceRepository;

    @Mock
    private PaymentIntentRepository paymentIntentRepository;

    @Mock
    private UserRepository userRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentWalletAccountRepository,
                paymentWalletTransactionRepository,
                paymentInvoiceRepository,
                paymentIntentRepository,
                userRepository,
                new ObjectMapper()
        );
    }

    @Test
    void topUpWalletUpdatesBalanceAndReturnsWallet() {
        UUID userId = UUID.randomUUID();
        PaymentWalletAccountEntity wallet = wallet(userId, 10);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "Payer")));
        when(paymentWalletAccountRepository.findById(userId)).thenReturn(Optional.of(wallet));
        when(paymentWalletAccountRepository.save(any(PaymentWalletAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentWalletTransactionRepository.save(any(PaymentWalletTransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = paymentService.topUpWallet(userId, new TopUpWalletRequest(25L, "Manual top-up"));

        assertThat(response.balanceUnits()).isEqualTo(35L);
        assertThat(response.currencyCode()).isEqualTo("XTR");
    }

    @Test
    void createInvoiceAndConfirmIntentTransfersFunds() {
        UUID creatorId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();

        PaymentWalletAccountEntity payerWallet = wallet(payerId, 100);
        PaymentWalletAccountEntity recipientWallet = wallet(recipientId, 5);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user(creatorId, "Creator")));
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(user(recipientId, "Recipient")));
        when(userRepository.findById(payerId)).thenReturn(Optional.of(user(payerId, "Payer")));
        when(paymentInvoiceRepository.save(any(PaymentInvoiceEntity.class))).thenAnswer(invocation -> {
            PaymentInvoiceEntity invoice = invocation.getArgument(0);
            if (invoice.getId() == null) {
                invoice.setId(invoiceId);
            }
            if (invoice.getCreatedAt() == null) {
                invoice.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
            }
            if (invoice.getUpdatedAt() == null) {
                invoice.setUpdatedAt(Instant.parse("2026-03-14T12:00:00Z"));
            }
            return invoice;
        });
        when(paymentIntentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(invocation -> {
            PaymentIntentEntity intent = invocation.getArgument(0);
            if (intent.getId() == null) {
                intent.setId(intentId);
            }
            if (intent.getCreatedAt() == null) {
                intent.setCreatedAt(Instant.parse("2026-03-14T12:05:00Z"));
            }
            if (intent.getUpdatedAt() == null) {
                intent.setUpdatedAt(Instant.parse("2026-03-14T12:05:00Z"));
            }
            return intent;
        });
        when(paymentIntentRepository.findById(intentId))
                .thenReturn(Optional.of(intent(intentId, invoiceId, payerId, recipientId, 40L, "PENDING")));
        when(paymentInvoiceRepository.findById(invoiceId)).thenAnswer(invocation -> Optional.of(savedInvoice(invoiceId, creatorId, recipientId, 40L, "OPEN")));
        when(paymentWalletAccountRepository.findById(payerId)).thenReturn(Optional.of(payerWallet));
        when(paymentWalletAccountRepository.findById(recipientId)).thenReturn(Optional.of(recipientWallet));
        when(paymentWalletAccountRepository.save(any(PaymentWalletAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentWalletTransactionRepository.save(any(PaymentWalletTransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var invoice = paymentService.createInvoice(
                creatorId,
                new CreateInvoiceRequest(recipientId, "Invoice", "Desc", 40L, null, Map.of("type", "support"))
        );
        var intent = paymentService.createPaymentIntent(payerId, new CreatePaymentIntentRequest(invoice.invoiceId()));
        var confirmed = paymentService.confirmIntent(payerId, intent.paymentIntentId());

        assertThat(invoice.amountUnits()).isEqualTo(40L);
        assertThat(intent.status()).isEqualTo("PENDING");
        assertThat(confirmed.status()).isEqualTo("COMPLETED");
        assertThat(payerWallet.getBalanceUnits()).isEqualTo(60L);
        assertThat(recipientWallet.getBalanceUnits()).isEqualTo(45L);
    }

    @Test
    void createSelfInvoiceAllowsMatchingCreatorAndRecipient() {
        UUID botUserId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        when(userRepository.findById(botUserId)).thenReturn(Optional.of(user(botUserId, "Bot")));
        when(paymentInvoiceRepository.save(any(PaymentInvoiceEntity.class))).thenAnswer(invocation -> {
            PaymentInvoiceEntity invoice = invocation.getArgument(0);
            invoice.setId(invoiceId);
            invoice.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
            invoice.setUpdatedAt(Instant.parse("2026-03-14T12:00:00Z"));
            return invoice;
        });

        var response = paymentService.createSelfInvoice(botUserId, "Invoice", "Desc", 12L, null, Map.of("kind", "BOT_PAYMENT"));

        assertThat(response.invoiceId()).isEqualTo(invoiceId);
        assertThat(response.createdByUserId()).isEqualTo(botUserId);
        assertThat(response.recipientUserId()).isEqualTo(botUserId);
    }

    @Test
    void confirmIntentRejectsInsufficientBalance() {
        UUID payerId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();

        PaymentWalletAccountEntity payerWallet = wallet(payerId, 5);
        PaymentWalletAccountEntity recipientWallet = wallet(recipientId, 0);
        PaymentIntentEntity intent = intent(intentId, invoiceId, payerId, recipientId, 20L, "PENDING");

        when(userRepository.findById(payerId)).thenReturn(Optional.of(user(payerId, "Payer")));
        when(paymentIntentRepository.findById(intentId)).thenReturn(Optional.of(intent));
        when(paymentInvoiceRepository.findById(invoiceId)).thenReturn(Optional.of(savedInvoice(invoiceId, UUID.randomUUID(), recipientId, 20L, "OPEN")));
        when(paymentWalletAccountRepository.findById(payerId)).thenReturn(Optional.of(payerWallet));
        when(paymentWalletAccountRepository.findById(recipientId)).thenReturn(Optional.of(recipientWallet));

        assertThatThrownBy(() -> paymentService.confirmIntent(payerId, intentId))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void refundIntentMovesFundsBack() {
        UUID payerId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();

        PaymentWalletAccountEntity payerWallet = wallet(payerId, 10);
        PaymentWalletAccountEntity recipientWallet = wallet(recipientId, 70);
        PaymentIntentEntity intent = intent(intentId, invoiceId, payerId, recipientId, 30L, "COMPLETED");

        when(userRepository.findById(recipientId)).thenReturn(Optional.of(user(recipientId, "Recipient")));
        when(paymentIntentRepository.findById(intentId)).thenReturn(Optional.of(intent));
        when(paymentWalletAccountRepository.findById(payerId)).thenReturn(Optional.of(payerWallet));
        when(paymentWalletAccountRepository.findById(recipientId)).thenReturn(Optional.of(recipientWallet));
        when(paymentWalletAccountRepository.save(any(PaymentWalletAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentWalletTransactionRepository.save(any(PaymentWalletTransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentInvoiceRepository.findById(invoiceId)).thenReturn(Optional.of(savedInvoice(invoiceId, UUID.randomUUID(), recipientId, 30L, "PAID")));
        when(paymentInvoiceRepository.save(any(PaymentInvoiceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentIntentRepository.save(any(PaymentIntentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var refunded = paymentService.refundIntent(recipientId, intentId, new UpdatePaymentIntentRequest("Customer request"));

        assertThat(refunded.status()).isEqualTo("REFUNDED");
        assertThat(payerWallet.getBalanceUnits()).isEqualTo(40L);
        assertThat(recipientWallet.getBalanceUnits()).isEqualTo(40L);
    }

    @Test
    void transferSponsoredRevenueMovesFundsBetweenWallets() {
        UUID sponsorUserId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();
        UUID sponsoredMessageId = UUID.randomUUID();
        PaymentWalletAccountEntity sponsorWallet = wallet(sponsorUserId, 35);
        PaymentWalletAccountEntity recipientWallet = wallet(recipientUserId, 8);

        when(userRepository.findById(sponsorUserId)).thenReturn(Optional.of(user(sponsorUserId, "Sponsor")));
        when(userRepository.findById(recipientUserId)).thenReturn(Optional.of(user(recipientUserId, "Owner")));
        when(paymentWalletAccountRepository.findById(sponsorUserId)).thenReturn(Optional.of(sponsorWallet));
        when(paymentWalletAccountRepository.findById(recipientUserId)).thenReturn(Optional.of(recipientWallet));
        when(paymentWalletAccountRepository.save(any(PaymentWalletAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentWalletTransactionRepository.save(any(PaymentWalletTransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.transferSponsoredRevenue(
                sponsorUserId,
                recipientUserId,
                sponsoredMessageId,
                9L,
                "Sponsored impression",
                "Channel revenue"
        );

        assertThat(sponsorWallet.getBalanceUnits()).isEqualTo(26L);
        assertThat(recipientWallet.getBalanceUnits()).isEqualTo(17L);
    }

    @Test
    void hasAvailableBalanceReflectsWalletBalance() {
        UUID userId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "Balance holder")));
        when(paymentWalletAccountRepository.findById(userId)).thenReturn(Optional.of(wallet(userId, 14)));

        assertThat(paymentService.hasAvailableBalance(userId, 10L)).isTrue();
        assertThat(paymentService.hasAvailableBalance(userId, 20L)).isFalse();
    }

    @Test
    void withdrawToExternalDebitsWalletBalance() {
        UUID userId = UUID.randomUUID();
        PaymentWalletAccountEntity wallet = wallet(userId, 22);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "Withdrawer")));
        when(paymentWalletAccountRepository.findById(userId)).thenReturn(Optional.of(wallet));
        when(paymentWalletAccountRepository.save(any(PaymentWalletAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentWalletTransactionRepository.save(any(PaymentWalletTransactionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.withdrawToExternal(userId, 7L, "Payout withdrawal");

        assertThat(wallet.getBalanceUnits()).isEqualTo(15L);
    }

    private PaymentWalletAccountEntity wallet(UUID userId, long balance) {
        PaymentWalletAccountEntity wallet = new PaymentWalletAccountEntity();
        wallet.setUserId(userId);
        wallet.setBalanceUnits(balance);
        wallet.setCurrencyCode("XTR");
        wallet.setUpdatedAt(Instant.parse("2026-03-14T12:00:00Z"));
        return wallet;
    }

    private PaymentInvoiceEntity savedInvoice(UUID invoiceId, UUID creatorId, UUID recipientId, long amount, String status) {
        PaymentInvoiceEntity invoice = new PaymentInvoiceEntity();
        invoice.setId(invoiceId);
        invoice.setCreatedByUserId(creatorId);
        invoice.setRecipientUserId(recipientId);
        invoice.setTitle("Invoice");
        invoice.setAmountUnits(amount);
        invoice.setCurrencyCode("XTR");
        invoice.setStatus(status);
        invoice.setMetadataJson("{}");
        invoice.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
        invoice.setUpdatedAt(Instant.parse("2026-03-14T12:00:00Z"));
        return invoice;
    }

    private PaymentIntentEntity intent(UUID intentId, UUID invoiceId, UUID payerId, UUID recipientId, long amount, String status) {
        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setId(intentId);
        intent.setInvoiceId(invoiceId);
        intent.setPayerUserId(payerId);
        intent.setRecipientUserId(recipientId);
        intent.setAmountUnits(amount);
        intent.setCurrencyCode("XTR");
        intent.setStatus(status);
        intent.setCreatedAt(Instant.parse("2026-03-14T12:05:00Z"));
        intent.setUpdatedAt(Instant.parse("2026-03-14T12:05:00Z"));
        return intent;
    }

    private UserEntity user(UUID userId, String displayName) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setDisplayName(displayName);
        return user;
    }
}
