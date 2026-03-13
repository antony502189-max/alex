package com.alex.messenger.bot;

import com.alex.messenger.bot.dto.BotUpdateResponse;
import com.alex.messenger.bot.dto.BotUpdatesResponse;
import com.alex.messenger.bot.dto.BotPreCheckoutQueryResponse;
import com.alex.messenger.bot.dto.BotPaymentShippingAddressPayload;
import com.alex.messenger.bot.dto.BotPaymentShippingOptionPayload;
import com.alex.messenger.bot.dto.BotSuccessfulPaymentResponse;
import com.alex.messenger.bot.dto.BotWebAppDataResponse;
import com.alex.messenger.bot.dto.BotWebAppQueryResponse;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.message.MessageLookupEntity;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.payments.PaymentInvoiceEntity;
import com.alex.messenger.payments.PaymentInvoiceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BotUpdateService {

    private final BotAccountRepository botAccountRepository;
    private final BotCallbackQueryService botCallbackQueryService;
    private final BotUpdateRepository botUpdateRepository;
    private final BotWebAppEventRepository botWebAppEventRepository;
    private final BotWebAppQueryRepository botWebAppQueryRepository;
    private final BotPaymentInvoiceRepository botPaymentInvoiceRepository;
    private final BotPreCheckoutQueryRepository botPreCheckoutQueryRepository;
    private final BotPaymentReceiptRepository botPaymentReceiptRepository;
    private final PaymentInvoiceRepository paymentInvoiceRepository;
    private final ObjectProvider<MessageService> messageServiceProvider;
    private final ChatService chatService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder().build();

    @Value("${alex.bots.long-poll.max-timeout-seconds:30}")
    private int maxLongPollTimeoutSeconds;

    @Value("${alex.bots.long-poll.max-limit:100}")
    private int maxLongPollLimit;

    @Value("${alex.bots.webhook.request-timeout:PT10S}")
    private Duration webhookRequestTimeout;

    public void maybeEnqueueIncomingMessage(ChatEntity chat, UUID senderId, MessageLookupEntity message) {
        maybeEnqueueDirectMessageUpdate(chat, senderId, message, "MESSAGE_CREATED");
    }

    public void maybeEnqueueMessageEdited(ChatEntity chat, UUID senderId, MessageLookupEntity message) {
        maybeEnqueueDirectMessageUpdate(chat, senderId, message, "MESSAGE_EDITED");
    }

    public void maybeEnqueueMessageDeleted(ChatEntity chat, UUID senderId, MessageLookupEntity message) {
        maybeEnqueueDirectMessageUpdate(chat, senderId, message, "MESSAGE_DELETED");
    }

    private void maybeEnqueueDirectMessageUpdate(
            ChatEntity chat,
            UUID senderId,
            MessageLookupEntity message,
            String updateType
    ) {
        if (chat == null || senderId == null || message == null || !"DIRECT".equals(chat.getChatType())) {
            return;
        }

        UserEntity sender = userRepository.findById(senderId).orElse(null);
        if (sender == null || sender.isBot()) {
            return;
        }

        UUID peerUserId = chatService.getPeerUserId(chat, senderId);
        if (botAccountRepository.findById(peerUserId).isEmpty()) {
            return;
        }

        BotUpdateEntity update = new BotUpdateEntity();
        update.setBotUserId(peerUserId);
        update.setChatId(chat.getId());
        update.setMessageId(message.getMessageId());
        update.setUpdateType(updateType);
        botUpdateRepository.save(update);
    }

    public BotUpdatesResponse getUpdates(UUID botUserId, Long offset, Integer limit, Integer timeoutSeconds) {
        BotAccountEntity account = requireBotAccount(botUserId);
        if (account.isWebhookEnabled() && account.getWebhookUrl() != null && !account.getWebhookUrl().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Webhook is enabled for this bot. Disable it before using long polling."
            );
        }

        long normalizedOffset = offset != null ? Math.max(0L, offset) : 0L;
        int normalizedLimit = Math.max(1, Math.min(limit != null ? limit : 20, Math.max(1, maxLongPollLimit)));
        int normalizedTimeout = Math.max(0, Math.min(timeoutSeconds != null ? timeoutSeconds : 0, Math.max(1, maxLongPollTimeoutSeconds)));
        Instant deadline = Instant.now().plusSeconds(normalizedTimeout);

        List<BotUpdateEntity> updates = List.of();
        do {
            updates = botUpdateRepository.findPendingLongPollUpdates(botUserId, normalizedOffset, normalizedLimit);
            if (!updates.isEmpty() || normalizedTimeout == 0 || Instant.now().isAfter(deadline)) {
                break;
            }
            sleepOneSecond();
        } while (true);

        if (!updates.isEmpty()) {
            Instant deliveredAt = Instant.now();
            updates.forEach(update -> update.setDeliveredAt(deliveredAt));
            botUpdateRepository.saveAll(updates);
        }

        List<BotUpdateResponse> responses = updates.stream().map(this::toUpdateResponse).toList();
        long nextOffset = responses.isEmpty() ? normalizedOffset : responses.get(responses.size() - 1).updateId();
        return new BotUpdatesResponse(responses, nextOffset);
    }

    public List<BotUpdateEntity> lockWebhookDeliveryBatch(int batchSize) {
        return botUpdateRepository.lockWebhookDeliveryBatch(batchSize);
    }

    public void deliverWebhookUpdate(BotUpdateEntity update) {
        BotAccountEntity account = requireBotAccount(update.getBotUserId());
        Instant attemptedAt = Instant.now();

        update.setLastDeliveryAttemptAt(attemptedAt);
        update.setDeliveryAttempts(update.getDeliveryAttempts() + 1);

        try {
            BotUpdateResponse payload = toUpdateResponse(update);
            byte[] serializedPayload = objectMapper.writeValueAsBytes(payload);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(account.getWebhookUrl()))
                    .timeout(webhookRequestTimeout)
                    .header("Content-Type", "application/json");
            if (account.getWebhookSecretValue() != null && !account.getWebhookSecretValue().isBlank()) {
                requestBuilder.header("X-Alex-Bot-Secret", account.getWebhookSecretValue());
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(serializedPayload)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                update.setDeliveredAt(attemptedAt);
                update.setLastError(null);
                account.setLastWebhookDeliveryAt(attemptedAt);
                account.setLastWebhookError(null);
            } else {
                String error = truncateError("HTTP " + response.statusCode());
                update.setLastError(error);
                account.setLastWebhookError(error);
            }
        } catch (Exception exception) {
            String error = truncateError(exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName());
            update.setLastError(error);
            account.setLastWebhookError(error);
        }

        botAccountRepository.save(account);
        botUpdateRepository.save(update);
    }

    private BotUpdateResponse toUpdateResponse(BotUpdateEntity update) {
        ChatMessageResponse message = update.getMessageId() != null
                ? messageServiceProvider.getObject().getMessage(update.getBotUserId(), update.getMessageId())
                : null;
        return new BotUpdateResponse(
                update.getId(),
                update.getUpdateType(),
                update.getBotUserId(),
                update.getChatId(),
                message,
                update.getCallbackQueryId() != null
                        ? botCallbackQueryService.getCallbackQuery(update.getCallbackQueryId())
                        : null,
                update.getWebAppEventId() != null
                        ? botWebAppEventRepository.findById(update.getWebAppEventId()).map(this::toWebAppDataResponse).orElse(null)
                        : null,
                update.getWebAppQueryId() != null
                        ? botWebAppQueryRepository.findById(update.getWebAppQueryId()).map(this::toWebAppQueryResponse).orElse(null)
                        : null,
                update.getPreCheckoutQueryId() != null && "PRE_CHECKOUT_QUERY".equals(update.getUpdateType())
                        ? botPreCheckoutQueryRepository.findById(update.getPreCheckoutQueryId()).map(this::toPreCheckoutQueryResponse).orElse(null)
                        : null,
                update.getPreCheckoutQueryId() != null
                        && List.of("SUCCESSFUL_PAYMENT", "REFUNDED_PAYMENT").contains(update.getUpdateType())
                        ? botPreCheckoutQueryRepository.findById(update.getPreCheckoutQueryId()).map(this::toSuccessfulPaymentResponse).orElse(null)
                        : null,
                update.getCreatedAt()
        );
    }

    private BotWebAppDataResponse toWebAppDataResponse(BotWebAppEventEntity event) {
        return new BotWebAppDataResponse(
                event.getId(),
                event.getBotUserId(),
                event.getChatId(),
                event.getMessageId(),
                event.getFromUserId(),
                event.getButtonText(),
                event.getPayloadData(),
                event.getStartParameter(),
                event.getPlatform(),
                event.getCreatedAt()
        );
    }

    private BotWebAppQueryResponse toWebAppQueryResponse(BotWebAppQueryEntity query) {
        return new BotWebAppQueryResponse(
                query.getId(),
                query.getBotUserId(),
                query.getChatId(),
                query.getFromUserId(),
                query.getStartParameter(),
                query.getPlatform(),
                query.getQueryText(),
                query.getCreatedAt(),
                query.getAnsweredAt(),
                query.getResultMessageId()
        );
    }

    private BotPreCheckoutQueryResponse toPreCheckoutQueryResponse(BotPreCheckoutQueryEntity query) {
        BotPaymentInvoiceEntity botInvoice = botPaymentInvoiceRepository.findById(query.getPaymentInvoiceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot payment invoice not found"));
        PaymentInvoiceEntity invoice = paymentInvoiceRepository.findById(query.getPaymentInvoiceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment invoice not found"));
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

    private BotSuccessfulPaymentResponse toSuccessfulPaymentResponse(BotPreCheckoutQueryEntity query) {
        BotPaymentInvoiceEntity botInvoice = botPaymentInvoiceRepository.findById(query.getPaymentInvoiceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot payment invoice not found"));
        BotPaymentReceiptEntity receipt = botPaymentReceiptRepository.findByPreCheckoutQueryId(query.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot payment receipt not found"));
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
                receipt.getCreatedAt(),
                receipt.getRefundedAt()
        );
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

    private BotAccountEntity requireBotAccount(UUID botUserId) {
        return botAccountRepository.findById(botUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found"));
    }

    private void sleepOneSecond() {
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Long polling was interrupted");
        }
    }

    private String truncateError(String value) {
        if (value == null || value.isBlank()) {
            return "Webhook delivery failed";
        }
        String normalized = value.trim();
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }
}
