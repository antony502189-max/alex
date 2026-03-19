package com.alex.messenger.monetization;

import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.SendMessageRequest;
import com.alex.messenger.monetization.dto.AssignMonetizationArtifactSubscriptionAlertRequest;
import com.alex.messenger.monetization.dto.ChannelMonetizationReportResponse;
import com.alex.messenger.monetization.dto.ChannelMonetizationStatsResponse;
import com.alex.messenger.monetization.dto.CreateMonetizationArtifactAlertCommentRequest;
import com.alex.messenger.monetization.dto.CreateMonetizationOwnerReminderDigestSubscriptionRequest;
import com.alex.messenger.monetization.dto.CreateSponsoredMessageRequest;
import com.alex.messenger.monetization.dto.CreateMonetizationWithdrawalRequest;
import com.alex.messenger.monetization.dto.MonetizationExportArtifactResponse;
import com.alex.messenger.monetization.dto.MonetizationClaimableAlertWorkloadResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactPublicationResponse;
import com.alex.messenger.monetization.dto.CreateMonetizationArtifactSubscriptionRequest;
import com.alex.messenger.monetization.dto.GenerateMonetizationAlertDigestRequest;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertAuditEventResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertCommentResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertReminderDigestResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertReminderResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertReminderBatchResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertSummaryResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertTriageReminderResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertTriageResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertWorkloadOwnerResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertWorkloadResponse;
import com.alex.messenger.monetization.dto.MonetizationAlertDigestRunResponse;
import com.alex.messenger.monetization.dto.MonetizationAlertPolicyResponse;
import com.alex.messenger.monetization.dto.MonetizationOwnerReminderDigestIssueOwnerResponse;
import com.alex.messenger.monetization.dto.MonetizationOwnerReminderDigestIssueActionResponse;
import com.alex.messenger.monetization.dto.MonetizationOwnerReminderDigestIssueSummaryResponse;
import com.alex.messenger.monetization.dto.MonetizationOwnerReminderDigestRunResponse;
import com.alex.messenger.monetization.dto.MonetizationOwnerReminderDigestSubscriptionResponse;
import com.alex.messenger.monetization.dto.MonetizationPayoutItemResponse;
import com.alex.messenger.monetization.dto.MonetizationPayoutExportResponse;
import com.alex.messenger.monetization.dto.MonetizationPayoutResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactSubscriptionAlertResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactSubscriptionFailureResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactSubscriptionResponse;
import com.alex.messenger.monetization.dto.MonetizationProviderReconciliationRequest;
import com.alex.messenger.monetization.dto.MonetizationProviderStatusUpdateRequest;
import com.alex.messenger.monetization.dto.MonetizationProviderSyncRunResponse;
import com.alex.messenger.monetization.dto.MonetizationReconciliationRunResponse;
import com.alex.messenger.monetization.dto.MonetizationWithdrawalProviderCallbackRequest;
import com.alex.messenger.monetization.dto.MonetizationWithdrawalProviderCallbackResponse;
import com.alex.messenger.monetization.dto.PublishMonetizationArtifactRequest;
import com.alex.messenger.monetization.dto.SnoozeMonetizationArtifactSubscriptionAlertRequest;
import com.alex.messenger.monetization.dto.SponsoredMessageDeliveryResponse;
import com.alex.messenger.monetization.dto.SponsoredMessageResponse;
import com.alex.messenger.monetization.dto.MonetizationWithdrawalResponse;
import com.alex.messenger.monetization.dto.UpdateMonetizationAlertPolicyRequest;
import com.alex.messenger.payments.PaymentService;
import com.alex.messenger.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MonetizationService {

    private static final int DEFAULT_ALERT_THRESHOLD = 3;
    private static final int DEFAULT_HIGH_SEVERITY_THRESHOLD = 5;
    private static final int DEFAULT_ALERT_SUPPRESSION_MINUTES = 180;
    private static final int DEFAULT_ACKNOWLEDGE_SLA_MINUTES = 60;
    private static final int DEFAULT_RESOLVE_SLA_MINUTES = 240;
    private static final int DEFAULT_REMINDER_INTERVAL_MINUTES = 60;
    private static final int DEFAULT_SEVERITY_UPGRADE_AFTER_MINUTES = 30;
    private static final int DEFAULT_BREACH_ESCALATION_AFTER_MINUTES = 120;
    private static final int DEFAULT_HIGH_SEVERITY_ACKNOWLEDGE_SLA_MINUTES = 15;
    private static final int DEFAULT_HIGH_SEVERITY_RESOLVE_SLA_MINUTES = 60;
    private static final int DEFAULT_HIGH_SEVERITY_REMINDER_INTERVAL_MINUTES = 15;
    private static final int DEFAULT_ALERT_TRIAGE_DELAY_MINUTES = 15;
    private static final int DEFAULT_TRIAGE_REMINDER_INTERVAL_MINUTES = 30;
    private static final int DEFAULT_TRIAGE_ESCALATION_AFTER_MINUTES = 90;
    private static final int DEFAULT_OWNER_REMINDER_DIGEST_AUTO_PAUSE_FAILURE_THRESHOLD = 3;
    private static final int MAX_OWNER_REMINDER_DIGEST_BACKOFF_MINUTES = 720;

    private final SponsoredMessageRepository sponsoredMessageRepository;
    private final SponsoredMessageEventRepository sponsoredMessageEventRepository;
    private final ChannelMonetizationPayoutRepository channelMonetizationPayoutRepository;
    private final ChannelMonetizationPayoutItemRepository channelMonetizationPayoutItemRepository;
    private final ChannelMonetizationWithdrawalRepository channelMonetizationWithdrawalRepository;
    private final ChannelMonetizationReconciliationRunRepository channelMonetizationReconciliationRunRepository;
    private final ChannelMonetizationWithdrawalCallbackRepository channelMonetizationWithdrawalCallbackRepository;
    private final ChannelMonetizationExportArtifactRepository channelMonetizationExportArtifactRepository;
    private final ChannelMonetizationProviderSyncRunRepository channelMonetizationProviderSyncRunRepository;
    private final ChannelMonetizationArtifactPublicationRepository channelMonetizationArtifactPublicationRepository;
    private final ChannelMonetizationArtifactSubscriptionRepository channelMonetizationArtifactSubscriptionRepository;
    private final ChannelMonetizationArtifactSubscriptionFailureRepository channelMonetizationArtifactSubscriptionFailureRepository;
    private final ChannelMonetizationArtifactSubscriptionAlertRepository channelMonetizationArtifactSubscriptionAlertRepository;
    private final ChannelMonetizationArtifactAlertCommentRepository channelMonetizationArtifactAlertCommentRepository;
    private final ChannelMonetizationArtifactAlertAuditEventRepository channelMonetizationArtifactAlertAuditEventRepository;
    private final ChannelMonetizationAlertDigestRunRepository channelMonetizationAlertDigestRunRepository;
    private final ChannelMonetizationAlertPolicyRepository channelMonetizationAlertPolicyRepository;
    private final ChannelMonetizationOwnerReminderDigestSubscriptionRepository channelMonetizationOwnerReminderDigestSubscriptionRepository;
    private final ChannelMonetizationOwnerReminderDigestRunRepository channelMonetizationOwnerReminderDigestRunRepository;
    private final ChatService chatService;
    private final UserRepository userRepository;
    private final MessageService messageService;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<SponsoredMessageResponse> listSponsoredMessages(UUID requesterId, UUID chatId) {
        ensureCanManageMonetization(requesterId, chatId);
        return sponsoredMessageRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SponsoredMessageResponse createSponsoredMessage(
            UUID requesterId,
            UUID chatId,
            CreateSponsoredMessageRequest request
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        UUID sponsorUserId = request.sponsorUserId() != null ? request.sponsorUserId() : requesterId;
        requireUser(sponsorUserId);
        Instant activeUntil = request.activeUntil();
        if (activeUntil != null && !activeUntil.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sponsored message activity window must be in the future");
        }

        SponsoredMessageEntity message = new SponsoredMessageEntity();
        message.setChannelChatId(chatId);
        message.setSponsorUserId(sponsorUserId);
        message.setCreatedByUserId(requesterId);
        message.setTitle(normalizeRequired(request.title(), "Sponsored message title", 120));
        message.setMessageText(normalizeRequired(request.messageText(), "Sponsored message text", 1000));
        message.setCallToActionLabel(normalizeOptional(request.callToActionLabel(), 64));
        message.setCallToActionUrl(normalizeHttpUrl(request.callToActionUrl()));
        message.setBudgetUnits(normalizePositive(request.budgetUnits(), "Sponsored message budget"));
        message.setCostPerImpressionUnits(normalizeNonNegative(request.costPerImpressionUnits(), 1L, "Impression cost"));
        message.setCostPerClickUnits(normalizeNonNegative(request.costPerClickUnits(), 5L, "Click cost"));
        message.setStatus("DRAFT");
        message.setActiveUntil(activeUntil);
        return toResponse(sponsoredMessageRepository.save(message));
    }

    @Transactional
    public SponsoredMessageResponse publishSponsoredMessage(UUID requesterId, UUID chatId, UUID sponsoredMessageId) {
        ensureCanManageMonetization(requesterId, chatId);
        SponsoredMessageEntity sponsoredMessage = getManagedSponsoredMessage(chatId, sponsoredMessageId);
        if (sponsoredMessage.getDeliveredMessageId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sponsored message is already published");
        }
        if (sponsoredMessage.getActiveUntil() != null && !sponsoredMessage.getActiveUntil().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Sponsored message activity window has expired");
        }
        ensureCampaignCanSpend(sponsoredMessage);

        ChatMessageResponse published = messageService.sendMessage(
                requesterId,
                new SendMessageRequest(
                        chatId,
                        null,
                        null,
                        null,
                        composeSponsoredText(sponsoredMessage),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        null
                )
        );
        sponsoredMessage.setDeliveredMessageId(published.messageId());
        sponsoredMessage.setPublishedAt(Instant.now());
        sponsoredMessage.setStatus("ACTIVE");
        sponsoredMessage.setCompletedAt(null);
        sponsoredMessage.setCanceledAt(null);
        return toResponse(sponsoredMessageRepository.save(sponsoredMessage));
    }

    @Transactional
    public SponsoredMessageResponse pauseSponsoredMessage(UUID requesterId, UUID chatId, UUID sponsoredMessageId) {
        ensureCanManageMonetization(requesterId, chatId);
        SponsoredMessageEntity sponsoredMessage = getManagedSponsoredMessage(chatId, sponsoredMessageId);
        if (!"ACTIVE".equals(sponsoredMessage.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active sponsored messages can be paused");
        }
        sponsoredMessage.setStatus("PAUSED");
        return toResponse(sponsoredMessageRepository.save(sponsoredMessage));
    }

    @Transactional
    public SponsoredMessageResponse resumeSponsoredMessage(UUID requesterId, UUID chatId, UUID sponsoredMessageId) {
        ensureCanManageMonetization(requesterId, chatId);
        SponsoredMessageEntity sponsoredMessage = getManagedSponsoredMessage(chatId, sponsoredMessageId);
        if (sponsoredMessage.getDeliveredMessageId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sponsored message must be published before it can resume");
        }
        if (List.of("COMPLETED", "CANCELED").contains(sponsoredMessage.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completed or canceled sponsored message cannot be resumed");
        }
        if (sponsoredMessage.getActiveUntil() != null && !sponsoredMessage.getActiveUntil().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Sponsored message activity window has expired");
        }
        ensureCampaignCanSpend(sponsoredMessage);
        sponsoredMessage.setStatus("ACTIVE");
        return toResponse(sponsoredMessageRepository.save(sponsoredMessage));
    }

    @Transactional
    public SponsoredMessageResponse cancelSponsoredMessage(UUID requesterId, UUID chatId, UUID sponsoredMessageId) {
        ensureCanManageMonetization(requesterId, chatId);
        SponsoredMessageEntity sponsoredMessage = getManagedSponsoredMessage(chatId, sponsoredMessageId);
        if ("COMPLETED".equals(sponsoredMessage.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completed sponsored message cannot be canceled");
        }
        if ("CANCELED".equals(sponsoredMessage.getStatus())) {
            return toResponse(sponsoredMessage);
        }
        sponsoredMessage.setStatus("CANCELED");
        sponsoredMessage.setCanceledAt(Instant.now());
        return toResponse(sponsoredMessageRepository.save(sponsoredMessage));
    }

    @Transactional
    public SponsoredMessageResponse recordImpression(UUID viewerId, UUID chatId, UUID sponsoredMessageId) {
        chatService.getOwnedChat(viewerId, chatId);
        return recordEvent(viewerId, chatId, sponsoredMessageId, "IMPRESSION");
    }

    @Transactional
    public SponsoredMessageResponse recordClick(UUID viewerId, UUID chatId, UUID sponsoredMessageId) {
        chatService.getOwnedChat(viewerId, chatId);
        return recordEvent(viewerId, chatId, sponsoredMessageId, "CLICK");
    }

    @Transactional
    public SponsoredMessageDeliveryResponse deliverSponsoredMessage(UUID viewerId, UUID chatId) {
        ChatEntity chat = chatService.getOwnedChat(viewerId, chatId);
        if (!"CHANNEL".equals(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sponsored delivery is available only for channels");
        }

        List<SponsoredMessageEntity> candidates = sponsoredMessageRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId).stream()
                .filter(this::isDeliverable)
                .sorted(Comparator
                        .comparing((SponsoredMessageEntity message) -> hasSeenEvent(message.getId(), viewerId, "IMPRESSION"))
                        .thenComparing(SponsoredMessageEntity::getPublishedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No sponsored message is available for delivery");
        }

        SponsoredMessageEntity candidate = candidates.get(0);
        boolean impressionAlreadyRecorded = hasSeenEvent(candidate.getId(), viewerId, "IMPRESSION");
        SponsoredMessageResponse charged = recordEvent(viewerId, chatId, candidate.getId(), "IMPRESSION");
        return new SponsoredMessageDeliveryResponse(
                charged.sponsoredMessageId(),
                charged.channelChatId(),
                charged.title(),
                charged.messageText(),
                charged.callToActionLabel(),
                charged.callToActionUrl(),
                charged.deliveredMessageId(),
                charged.remainingBudgetUnits(),
                !impressionAlreadyRecorded,
                charged.publishedAt(),
                charged.activeUntil()
        );
    }

    @Transactional(readOnly = true)
    public List<MonetizationPayoutResponse> listPayouts(UUID requesterId, UUID chatId) {
        ensureCanManageMonetization(requesterId, chatId);
        List<ChannelMonetizationPayoutEntity> payouts = channelMonetizationPayoutRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId);
        return toPayoutResponses(payouts);
    }

    @Transactional
    public MonetizationPayoutResponse runPayout(UUID requesterId, UUID chatId, int batchSize) {
        ensureCanManageMonetization(requesterId, chatId);
        List<SponsoredMessageEntity> readyMessages = sponsoredMessageRepository.lockReadyForPayoutByChannel(chatId, Math.max(1, batchSize));
        if (readyMessages.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No monetization payout is ready for this channel");
        }
        return createPayoutRecord(readyMessages, "MANUAL", requesterId);
    }

    @Transactional(readOnly = true)
    public List<MonetizationWithdrawalResponse> listWithdrawals(UUID requesterId, UUID chatId) {
        ensureCanManageMonetization(requesterId, chatId);
        return channelMonetizationWithdrawalRepository.findAllByChannelChatIdOrderByRequestedAtDesc(chatId).stream()
                .map(this::toWithdrawalResponse)
                .toList();
    }

    @Transactional
    public MonetizationWithdrawalResponse createWithdrawal(
            UUID requesterId,
            UUID chatId,
            CreateMonetizationWithdrawalRequest request
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        UUID recipientUserId = resolveRevenueRecipientUserId(chatId);
        if (recipientUserId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Channel payout recipient is not configured");
        }
        long amountUnits = normalizePositive(request.amountUnits(), "Withdrawal amount");
        long availableUnits = getAvailableWithdrawalUnits(chatId);
        if (amountUnits > availableUnits) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Withdrawal amount exceeds available monetization balance");
        }

        ChannelMonetizationWithdrawalEntity withdrawal = new ChannelMonetizationWithdrawalEntity();
        withdrawal.setChannelChatId(chatId);
        withdrawal.setRecipientUserId(recipientUserId);
        withdrawal.setRequestedByUserId(requesterId);
        withdrawal.setAmountUnits(amountUnits);
        withdrawal.setCurrencyCode("XTR");
        withdrawal.setDestinationType(normalizeRequired(request.destinationType(), "Withdrawal destination type", 32));
        withdrawal.setDestinationLabel(normalizeRequired(request.destinationLabel(), "Withdrawal destination label", 255));
        withdrawal.setNote(normalizeOptional(request.note(), 255));
        withdrawal.setStatus("PENDING");
        return toWithdrawalResponse(channelMonetizationWithdrawalRepository.save(withdrawal));
    }

    @Transactional
    public MonetizationWithdrawalResponse cancelWithdrawal(UUID requesterId, UUID chatId, UUID withdrawalId) {
        ensureCanManageMonetization(requesterId, chatId);
        ChannelMonetizationWithdrawalEntity withdrawal = getManagedWithdrawal(chatId, withdrawalId);
        if (!"PENDING".equals(withdrawal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending withdrawals can be canceled");
        }
        withdrawal.setStatus("CANCELED");
        withdrawal.setCanceledAt(Instant.now());
        return toWithdrawalResponse(channelMonetizationWithdrawalRepository.save(withdrawal));
    }

    @Transactional
    public MonetizationWithdrawalResponse syncWithdrawal(UUID requesterId, UUID chatId, UUID withdrawalId) {
        ensureCanManageMonetization(requesterId, chatId);
        ChannelMonetizationWithdrawalEntity withdrawal = getManagedWithdrawal(chatId, withdrawalId);
        if ("PENDING".equals(withdrawal.getStatus())) {
            withdrawal = startWithdrawalProcessing(withdrawal);
        }
        if ("PROCESSING".equals(withdrawal.getStatus())) {
            return reconcileProcessingWithdrawal(withdrawal);
        }
        return toWithdrawalResponse(withdrawal);
    }

    @Transactional
    public MonetizationWithdrawalResponse retryWithdrawal(UUID requesterId, UUID chatId, UUID withdrawalId) {
        ensureCanManageMonetization(requesterId, chatId);
        ChannelMonetizationWithdrawalEntity withdrawal = getManagedWithdrawal(chatId, withdrawalId);
        if (!"FAILED".equals(withdrawal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed withdrawals can be retried");
        }
        withdrawal.setStatus("PENDING");
        withdrawal.setProviderStatus(null);
        withdrawal.setFailureReason(null);
        withdrawal.setProcessingAt(null);
        withdrawal.setProviderUpdatedAt(null);
        withdrawal.setCompletedAt(null);
        withdrawal.setCanceledAt(null);
        return toWithdrawalResponse(channelMonetizationWithdrawalRepository.save(withdrawal));
    }

    @Transactional(readOnly = true)
    public List<MonetizationWithdrawalProviderCallbackResponse> listWithdrawalCallbacks(
            UUID requesterId,
            UUID chatId,
            UUID withdrawalId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        getManagedWithdrawal(chatId, withdrawalId);
        return channelMonetizationWithdrawalCallbackRepository.findAllByWithdrawalIdOrderByReceivedAtDesc(withdrawalId).stream()
                .map(this::toWithdrawalCallbackResponse)
                .toList();
    }

    @Transactional
    public MonetizationWithdrawalProviderCallbackResponse applyProviderCallback(
            MonetizationWithdrawalProviderCallbackRequest request
    ) {
        ChannelMonetizationWithdrawalEntity withdrawal = resolveWithdrawalForCallback(request);
        String providerReference = resolveProviderReference(withdrawal, request.providerReference());
        String providerStatus = normalizeStatus(request.providerStatus());
        String callbackType = normalizeOptional(request.callbackType(), 32);
        if (callbackType == null) {
            callbackType = "STATUS_UPDATE";
        }
        String failureReason = normalizeOptional(request.failureReason(), 255);
        String payloadJson = serializePayload(request.payload());
        Instant now = Instant.now();

        withdrawal.setProviderReference(providerReference);
        withdrawal.setProviderStatus(providerStatus);
        withdrawal.setProviderUpdatedAt(now);

        ChannelMonetizationWithdrawalCallbackEntity callback = new ChannelMonetizationWithdrawalCallbackEntity();
        callback.setWithdrawalId(withdrawal.getId());
        callback.setChannelChatId(withdrawal.getChannelChatId());
        callback.setProviderReference(providerReference);
        callback.setCallbackType(callbackType);
        callback.setProviderStatus(providerStatus);
        callback.setFailureReason(failureReason);
        callback.setPayloadJson(payloadJson);
        callback.setReceivedAt(now);
        callback.setProcessedAt(now);

        String resultMessage = applyProviderStatus(withdrawal, providerStatus, failureReason);
        callback.setAppliedWithdrawalStatus(withdrawal.getStatus());
        callback.setApplied(!resultMessage.startsWith("Callback ignored"));
        callback.setResultMessage(resultMessage);

        ChannelMonetizationWithdrawalEntity savedWithdrawal = channelMonetizationWithdrawalRepository.save(withdrawal);
        ChannelMonetizationWithdrawalCallbackEntity savedCallback = saveWithdrawalCallback(callback);
        return toWithdrawalCallbackResponse(savedCallback, savedWithdrawal);
    }

    @Transactional
    public MonetizationProviderSyncRunResponse reconcileProviderStatuses(
            UUID chatId,
            MonetizationProviderReconciliationRequest request
    ) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider reconciliation payload is required");
        }
        requireChannelChat(chatId);
        List<MonetizationProviderStatusUpdateRequest> updates = request.updates() != null
                ? request.updates()
                : List.of();
        if (updates.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider reconciliation updates must not contain null");
        }
        if (updates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider reconciliation requires at least one update");
        }

        List<Map<String, Object>> resultEntries = new java.util.ArrayList<>();
        int appliedCount = 0;
        int ignoredCount = 0;
        int failedCount = 0;

        for (MonetizationProviderStatusUpdateRequest update : updates) {
            try {
                MonetizationWithdrawalProviderCallbackResponse response = applyProviderCallbackForChannel(chatId, update);
                if (response.applied()) {
                    appliedCount++;
                } else {
                    ignoredCount++;
                }
                Map<String, Object> successEntry = new LinkedHashMap<>();
                successEntry.put("withdrawalId", response.withdrawalId());
                successEntry.put("providerReference", response.providerReference());
                successEntry.put("providerStatus", response.providerStatus());
                successEntry.put("applied", response.applied());
                successEntry.put("appliedWithdrawalStatus", response.appliedWithdrawalStatus());
                successEntry.put("resultMessage", response.resultMessage());
                resultEntries.add(successEntry);
            } catch (ResponseStatusException exception) {
                failedCount++;
                Map<String, Object> failedEntry = new LinkedHashMap<>();
                failedEntry.put("withdrawalId", update.withdrawalId());
                failedEntry.put("providerReference", update.providerReference());
                failedEntry.put("providerStatus", update.providerStatus());
                failedEntry.put("error", exception.getReason());
                resultEntries.add(failedEntry);
            }
        }

        String content = serializeProviderSyncResult(chatId, updates.size(), appliedCount, ignoredCount, failedCount, resultEntries);
        MonetizationExportArtifactResponse artifact = persistArtifact(
                chatId,
                null,
                "PROVIDER_RECONCILIATION_EXPORT",
                "JSON",
                "channel-%s-provider-reconciliation.json".formatted(chatId),
                resultEntries.size(),
                0L,
                content
        );

        ChannelMonetizationProviderSyncRunEntity run = new ChannelMonetizationProviderSyncRunEntity();
        run.setChannelChatId(chatId);
        run.setTriggeredByUserId(null);
        run.setTriggerMode("INTERNAL");
        run.setPayloadSize(updates.size());
        run.setAppliedCount(appliedCount);
        run.setIgnoredCount(ignoredCount);
        run.setFailedCount(failedCount);
        run.setArtifactId(artifact.artifactId());
        ChannelMonetizationProviderSyncRunEntity savedRun = channelMonetizationProviderSyncRunRepository.save(run);

        UUID publishArtifactToChatId = request != null ? request.publishArtifactToChatId() : null;
        if (publishArtifactToChatId != null) {
            ChannelMonetizationExportArtifactEntity artifactEntity = channelMonetizationExportArtifactRepository.findById(artifact.artifactId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monetization export artifact not found"));
            publishArtifactInternal(null, chatId, artifactEntity, publishArtifactToChatId, request.note());
        }

        return toProviderSyncRunResponse(savedRun);
    }

    @Transactional(readOnly = true)
    public List<MonetizationProviderSyncRunResponse> listProviderSyncRuns(UUID requesterId, UUID chatId) {
        ensureCanManageMonetization(requesterId, chatId);
        return channelMonetizationProviderSyncRunRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId).stream()
                .map(this::toProviderSyncRunResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonetizationArtifactPublicationResponse> listArtifactPublications(
            UUID requesterId,
            UUID chatId,
            UUID artifactId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        getArtifactEntity(chatId, artifactId);
        return channelMonetizationArtifactPublicationRepository.findAllByArtifactIdOrderByPublishedAtDesc(artifactId).stream()
                .map(this::toArtifactPublicationResponse)
                .toList();
    }

    @Transactional
    public MonetizationArtifactPublicationResponse publishArtifact(
            UUID requesterId,
            UUID chatId,
            UUID artifactId,
            PublishMonetizationArtifactRequest request
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        ChannelMonetizationExportArtifactEntity artifact = getArtifactEntity(chatId, artifactId);
        UUID targetChatId = request != null ? request.targetChatId() : null;
        if (targetChatId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target chat id is required");
        }
        chatService.getOwnedChat(requesterId, targetChatId);
        return publishArtifactInternal(requesterId, chatId, artifact, targetChatId, request.note());
    }

    @Transactional(readOnly = true)
    public List<MonetizationArtifactSubscriptionResponse> listArtifactSubscriptions(UUID requesterId, UUID chatId) {
        ensureCanManageMonetization(requesterId, chatId);
        return channelMonetizationArtifactSubscriptionRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId).stream()
                .map(this::toArtifactSubscriptionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonetizationAlertPolicyResponse getAlertPolicy(UUID requesterId, UUID chatId) {
        ensureCanManageMonetization(requesterId, chatId);
        return toAlertPolicyResponse(resolveAlertPolicy(chatId).orElse(null), chatId);
    }

    @Transactional
    public MonetizationAlertPolicyResponse updateAlertPolicy(
            UUID requesterId,
            UUID chatId,
            UpdateMonetizationAlertPolicyRequest request
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Alert policy payload is required");
        }
        if (request.alertTargetChatId() != null) {
            chatService.getOwnedChat(requesterId, request.alertTargetChatId());
        }
        if (request.digestTargetChatId() != null) {
            chatService.getOwnedChat(requesterId, request.digestTargetChatId());
        }
        if (request.reminderTargetChatId() != null) {
            chatService.getOwnedChat(requesterId, request.reminderTargetChatId());
        }
        if (request.personalReminderTargetChatId() != null) {
            chatService.getOwnedChat(requesterId, request.personalReminderTargetChatId());
        }
        if (request.breachTargetChatId() != null) {
            chatService.getOwnedChat(requesterId, request.breachTargetChatId());
        }
        if (request.defaultOwnerUserId() != null) {
            requireUser(request.defaultOwnerUserId());
        }
        if (request.triageFallbackOwnerUserId() != null) {
            requireUser(request.triageFallbackOwnerUserId());
        }
        if (request.triageTargetChatId() != null) {
            chatService.getOwnedChat(requesterId, request.triageTargetChatId());
        }
        if (request.triageEscalationTargetChatId() != null) {
            chatService.getOwnedChat(requesterId, request.triageEscalationTargetChatId());
        }
        if (request.personalReminderDigestTargetChatId() != null) {
            chatService.getOwnedChat(requesterId, request.personalReminderDigestTargetChatId());
        }

        ChannelMonetizationAlertPolicyEntity policy = resolveAlertPolicy(chatId)
                .orElseGet(() -> {
                    ChannelMonetizationAlertPolicyEntity created = new ChannelMonetizationAlertPolicyEntity();
                    created.setChannelChatId(chatId);
                    return created;
                });
        int alertThreshold = normalizeAlertThreshold(request.alertThreshold());
        policy.setConfiguredByUserId(requesterId);
        policy.setAlertThreshold(alertThreshold);
        policy.setHighSeverityThreshold(normalizeHighSeverityThreshold(request.highSeverityThreshold(), alertThreshold));
        policy.setAlertSuppressionMinutes(normalizeSubscriptionInterval(
                request.alertSuppressionMinutes(),
                DEFAULT_ALERT_SUPPRESSION_MINUTES
        ));
        policy.setAcknowledgeSlaMinutes(normalizeSubscriptionInterval(
                request.acknowledgeSlaMinutes(),
                DEFAULT_ACKNOWLEDGE_SLA_MINUTES
        ));
        policy.setResolveSlaMinutes(normalizeSubscriptionInterval(
                request.resolveSlaMinutes(),
                DEFAULT_RESOLVE_SLA_MINUTES
        ));
        policy.setReminderIntervalMinutes(normalizeSubscriptionInterval(
                request.reminderIntervalMinutes(),
                DEFAULT_REMINDER_INTERVAL_MINUTES
        ));
        int severityUpgradeAfterMinutes = normalizeSubscriptionInterval(
                request.severityUpgradeAfterMinutes(),
                DEFAULT_SEVERITY_UPGRADE_AFTER_MINUTES
        );
        policy.setSeverityUpgradeAfterMinutes(severityUpgradeAfterMinutes);
        policy.setBreachEscalationAfterMinutes(normalizeBreachEscalationAfterMinutes(
                request.breachEscalationAfterMinutes(),
                severityUpgradeAfterMinutes
        ));
        policy.setHighSeverityAcknowledgeSlaMinutes(normalizeHighSeverityAcknowledgeSlaMinutes(
                request.highSeverityAcknowledgeSlaMinutes(),
                policy.getAcknowledgeSlaMinutes()
        ));
        policy.setHighSeverityResolveSlaMinutes(normalizeHighSeverityResolveSlaMinutes(
                request.highSeverityResolveSlaMinutes(),
                policy.getHighSeverityAcknowledgeSlaMinutes(),
                policy.getResolveSlaMinutes()
        ));
        policy.setHighSeverityReminderIntervalMinutes(normalizeHighSeverityReminderIntervalMinutes(
                request.highSeverityReminderIntervalMinutes(),
                policy.getReminderIntervalMinutes()
        ));
        policy.setTriageDelayMinutes(normalizeSubscriptionInterval(
                request.triageDelayMinutes(),
                DEFAULT_ALERT_TRIAGE_DELAY_MINUTES
        ));
        int triageReminderIntervalMinutes = normalizeSubscriptionInterval(
                request.triageReminderIntervalMinutes(),
                DEFAULT_TRIAGE_REMINDER_INTERVAL_MINUTES
        );
        policy.setTriageReminderIntervalMinutes(triageReminderIntervalMinutes);
        policy.setTriageEscalationAfterMinutes(normalizeTriageEscalationAfterMinutes(
                request.triageEscalationAfterMinutes(),
                triageReminderIntervalMinutes
        ));
        policy.setAutoDigestEnabled(request.autoDigestEnabled() == null || request.autoDigestEnabled());
        policy.setAutoTriageEnabled(request.autoTriageEnabled() != null && request.autoTriageEnabled());
        policy.setTriageAutoAssignEnabled(request.triageAutoAssignEnabled() != null && request.triageAutoAssignEnabled());
        policy.setClaimNextStrategy(normalizeClaimStrategy(request.claimNextStrategy()));
        policy.setClaimNextTriageOnlyDefault(
                request.claimNextTriageOnlyDefault() != null && request.claimNextTriageOnlyDefault()
        );
        policy.setAlertTargetChatId(request.alertTargetChatId());
        policy.setReminderTargetChatId(request.reminderTargetChatId());
        policy.setPersonalReminderTargetChatId(request.personalReminderTargetChatId());
        policy.setBreachTargetChatId(request.breachTargetChatId());
        policy.setDefaultOwnerUserId(request.defaultOwnerUserId());
        policy.setTriageFallbackOwnerUserId(request.triageFallbackOwnerUserId());
        policy.setTriageTargetChatId(request.triageTargetChatId());
        policy.setTriageEscalationTargetChatId(request.triageEscalationTargetChatId());
        policy.setDigestTargetChatId(request.digestTargetChatId());
        policy.setPersonalReminderDigestTargetChatId(request.personalReminderDigestTargetChatId());
        return toAlertPolicyResponse(channelMonetizationAlertPolicyRepository.save(policy), chatId);
    }

    @Transactional
    public MonetizationArtifactSubscriptionResponse createArtifactSubscription(
            UUID requesterId,
            UUID chatId,
            CreateMonetizationArtifactSubscriptionRequest request
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        EffectiveAlertPolicy alertPolicy = effectiveAlertPolicy(chatId);
        String artifactType = normalizeRequired(request.artifactType(), "Artifact type", 32).toUpperCase(java.util.Locale.ROOT);
        validateArtifactType(artifactType);
        chatService.getOwnedChat(requesterId, request.targetChatId());

        ChannelMonetizationArtifactSubscriptionEntity subscription = new ChannelMonetizationArtifactSubscriptionEntity();
        subscription.setChannelChatId(chatId);
        subscription.setTargetChatId(request.targetChatId());
        subscription.setCreatedByUserId(requesterId);
        subscription.setArtifactType(artifactType);
        subscription.setDeliveryMode("CHAT_MESSAGE");
        subscription.setNote(normalizeOptional(request.note(), 255));
        subscription.setStatus("ACTIVE");
        subscription.setMinIntervalMinutes(normalizeSubscriptionInterval(request.minIntervalMinutes()));
        subscription.setAlertSuppressionMinutes(normalizeSubscriptionInterval(
                request.alertSuppressionMinutes(),
                alertPolicy.alertSuppressionMinutes()
        ));
        subscription.setAutoGenerate(Boolean.TRUE.equals(request.autoGenerate()));
        return toArtifactSubscriptionResponse(channelMonetizationArtifactSubscriptionRepository.save(subscription));
    }

    @Transactional
    public MonetizationArtifactSubscriptionResponse pauseArtifactSubscription(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = getSubscription(chatId, subscriptionId);
        subscription.setStatus("PAUSED");
        return toArtifactSubscriptionResponse(channelMonetizationArtifactSubscriptionRepository.save(subscription));
    }

    @Transactional
    public MonetizationArtifactSubscriptionResponse resumeArtifactSubscription(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = getSubscription(chatId, subscriptionId);
        subscription.setStatus("ACTIVE");
        return toArtifactSubscriptionResponse(channelMonetizationArtifactSubscriptionRepository.save(subscription));
    }

    @Transactional(readOnly = true)
    public List<MonetizationArtifactSubscriptionFailureResponse> listArtifactSubscriptionFailures(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        getSubscription(chatId, subscriptionId);
        return channelMonetizationArtifactSubscriptionFailureRepository.findAllBySubscriptionIdOrderByFailedAtDesc(subscriptionId).stream()
                .map(this::toArtifactSubscriptionFailureResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonetizationArtifactSubscriptionAlertResponse> listArtifactSubscriptionAlerts(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        getSubscription(chatId, subscriptionId);
        return channelMonetizationArtifactSubscriptionAlertRepository.findAllBySubscriptionIdOrderByCreatedAtDesc(subscriptionId).stream()
                .map(this::toArtifactSubscriptionAlertResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonetizationArtifactAlertCommentResponse> listArtifactSubscriptionAlertComments(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId,
            UUID alertId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        getSubscription(chatId, subscriptionId);
        getSubscriptionAlert(subscriptionId, alertId);
        return channelMonetizationArtifactAlertCommentRepository.findAllByAlertIdOrderByCreatedAtAsc(alertId).stream()
                .map(this::toArtifactAlertCommentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonetizationArtifactAlertAuditEventResponse> listArtifactSubscriptionAlertTimeline(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId,
            UUID alertId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        getSubscription(chatId, subscriptionId);
        getSubscriptionAlert(subscriptionId, alertId);
        return channelMonetizationArtifactAlertAuditEventRepository.findAllByAlertIdOrderByCreatedAtAsc(alertId).stream()
                .map(this::toArtifactAlertAuditEventResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonetizationArtifactAlertSummaryResponse getArtifactAlertSummary(UUID requesterId, UUID chatId) {
        ensureCanManageMonetization(requesterId, chatId);
        List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts = channelMonetizationArtifactSubscriptionAlertRepository
                .findAllByChannelChatIdOrderByCreatedAtDesc(chatId);
        List<ChannelMonetizationArtifactSubscriptionEntity> subscriptions = channelMonetizationArtifactSubscriptionRepository
                .findAllByChannelChatIdOrderByCreatedAtDesc(chatId);
        Instant latestAlertAt = alerts.stream()
                .map(ChannelMonetizationArtifactSubscriptionAlertEntity::getCreatedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        Instant latestFailureAt = channelMonetizationArtifactSubscriptionFailureRepository
                .findAllByChannelChatIdOrderByFailedAtDesc(chatId)
                .stream()
                .map(ChannelMonetizationArtifactSubscriptionFailureEntity::getFailedAt)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        ChannelMonetizationAlertDigestRunEntity latestDigestRun = channelMonetizationAlertDigestRunRepository
                .findFirstByChannelChatIdOrderByCreatedAtDesc(chatId)
                .orElse(null);
        return new MonetizationArtifactAlertSummaryResponse(
                chatId,
                alerts.size(),
                countAlertsByStatus(alerts, "OPEN"),
                countAlertsByStatus(alerts, "ACKNOWLEDGED"),
                countAlertsByStatus(alerts, "SNOOZED"),
                countAlertsByStatus(alerts, "RESOLVED"),
                countAlertsByStatusAndSeverity(alerts, "OPEN", "HIGH"),
                countAlertsByStatusAndSeverity(alerts, "OPEN", "WARN"),
                countOverdueAcknowledgementAlerts(alerts, Instant.now()),
                countOverdueResolutionAlerts(alerts, Instant.now()),
                countBreachedAlerts(alerts, Instant.now()),
                countSubscriptionsByEscalationStatus(subscriptions, "OPEN"),
                countSubscriptionsByEscalationStatus(subscriptions, "SUPPRESSED"),
                countSubscriptionsByEscalationStatus(subscriptions, "ACKNOWLEDGED"),
                countSubscriptionsByEscalationStatus(subscriptions, "SNOOZED"),
                latestAlertAt,
                latestFailureAt,
                latestDigestRun != null ? latestDigestRun.getId() : null,
                latestDigestRun != null ? latestDigestRun.getCreatedAt() : null
        );
    }

    @Transactional
    public MonetizationArtifactSubscriptionAlertResponse assignArtifactSubscriptionAlert(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId,
            UUID alertId,
            AssignMonetizationArtifactSubscriptionAlertRequest request
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = getSubscription(chatId, subscriptionId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = getSubscriptionAlert(subscriptionId, alertId);
        if ("RESOLVED".equals(alert.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Resolved monetization alert cannot be assigned");
        }
        UUID ownerUserId = request != null ? request.ownerUserId() : null;
        if (ownerUserId != null) {
            requireUser(ownerUserId);
        }
        assignAlertOwner(
                subscription,
                alert,
                ownerUserId,
                requesterId,
                ownerUserId != null ? "ASSIGNED" : "UNASSIGNED",
                normalizeOptional(request != null ? request.note() : null, 1000)
        );
        return toArtifactSubscriptionAlertResponse(alert);
    }

    @Transactional
    public MonetizationArtifactSubscriptionAlertResponse claimArtifactSubscriptionAlert(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId,
            UUID alertId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = getSubscription(chatId, subscriptionId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = getSubscriptionAlert(subscriptionId, alertId);
        if ("RESOLVED".equals(alert.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Resolved monetization alert cannot be claimed");
        }
        if (alert.getOwnerUserId() != null && !requesterId.equals(alert.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Monetization alert is already claimed by another operator");
        }
        if (requesterId.equals(alert.getOwnerUserId())) {
            return toArtifactSubscriptionAlertResponse(alert);
        }
        assignAlertOwner(
                subscription,
                alert,
                requesterId,
                requesterId,
                "CLAIMED",
                "Claimed by current operator"
        );
        return toArtifactSubscriptionAlertResponse(alert);
    }

    @Transactional
    public MonetizationArtifactSubscriptionAlertResponse claimNextArtifactSubscriptionAlert(
            UUID requesterId,
            UUID chatId,
            String severity,
            String status,
            Boolean triageOnly,
            Boolean breachedOnly,
            Boolean overdueOnly,
            String strategy
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = loadClaimableAlerts(
                chatId,
                severity,
                status,
                triageOnly,
                breachedOnly,
                overdueOnly,
                strategy,
                Instant.now()
        ).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No claimable monetization alert found"));
        ChannelMonetizationArtifactSubscriptionEntity subscription = getSubscription(chatId, alert.getSubscriptionId());
        assignAlertOwner(
                subscription,
                alert,
                requesterId,
                requesterId,
                "CLAIMED",
                "Claimed as next available alert"
        );
        return toArtifactSubscriptionAlertResponse(alert);
    }

    @Transactional
    public MonetizationArtifactSubscriptionAlertResponse releaseArtifactSubscriptionAlert(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId,
            UUID alertId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = getSubscription(chatId, subscriptionId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = getSubscriptionAlert(subscriptionId, alertId);
        if ("RESOLVED".equals(alert.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Resolved monetization alert cannot be released");
        }
        if (alert.getOwnerUserId() == null) {
            return toArtifactSubscriptionAlertResponse(alert);
        }
        if (!requesterId.equals(alert.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Monetization alert can only be released by its current owner");
        }
        assignAlertOwner(
                subscription,
                alert,
                null,
                requesterId,
                "RELEASED",
                "Released by current operator"
        );
        return toArtifactSubscriptionAlertResponse(alert);
    }

    @Transactional
    public MonetizationArtifactAlertCommentResponse addArtifactSubscriptionAlertComment(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId,
            UUID alertId,
            CreateMonetizationArtifactAlertCommentRequest request
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        getSubscription(chatId, subscriptionId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = getSubscriptionAlert(subscriptionId, alertId);
        ChannelMonetizationArtifactAlertCommentEntity comment = new ChannelMonetizationArtifactAlertCommentEntity();
        comment.setAlertId(alertId);
        comment.setSubscriptionId(subscriptionId);
        comment.setChannelChatId(chatId);
        comment.setAuthorUserId(requesterId);
        comment.setBody(normalizeRequired(request != null ? request.body() : null, "Alert comment", 1000));
        ChannelMonetizationArtifactAlertCommentEntity saved = channelMonetizationArtifactAlertCommentRepository.save(comment);
        recordAlertAuditEvent(
                alert,
                "COMMENT_ADDED",
                requesterId,
                alert.getOwnerUserId(),
                alert.getStatus(),
                alert.getStatus(),
                saved.getBody()
        );
        return toArtifactAlertCommentResponse(saved);
    }

    @Transactional
    public MonetizationArtifactSubscriptionAlertResponse acknowledgeArtifactSubscriptionAlert(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId,
            UUID alertId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = getSubscription(chatId, subscriptionId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = getSubscriptionAlert(subscriptionId, alertId);
        if ("RESOLVED".equals(alert.getStatus())) {
            return toArtifactSubscriptionAlertResponse(alert);
        }
        String previousStatus = alert.getStatus();
        alert.setStatus("ACKNOWLEDGED");
        alert.setAcknowledgedByUserId(requesterId);
        alert.setAcknowledgedAt(Instant.now());
        alert.setSnoozedUntil(null);
        channelMonetizationArtifactSubscriptionAlertRepository.save(alert);
        recordAlertAuditEvent(
                alert,
                "ACKNOWLEDGED",
                requesterId,
                alert.getOwnerUserId(),
                previousStatus,
                alert.getStatus(),
                null
        );
        subscription.setEscalationStatus("ACKNOWLEDGED");
        channelMonetizationArtifactSubscriptionRepository.save(subscription);
        return toArtifactSubscriptionAlertResponse(alert);
    }

    @Transactional
    public MonetizationArtifactSubscriptionAlertResponse snoozeArtifactSubscriptionAlert(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId,
            UUID alertId,
            SnoozeMonetizationArtifactSubscriptionAlertRequest request
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = getSubscription(chatId, subscriptionId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = getSubscriptionAlert(subscriptionId, alertId);
        if ("RESOLVED".equals(alert.getStatus())) {
            return toArtifactSubscriptionAlertResponse(alert);
        }
        EffectiveAlertPolicy alertPolicy = effectiveAlertPolicy(chatId);
        int snoozeMinutes = normalizeSubscriptionInterval(
                request != null ? request.snoozeMinutes() : null,
                alertPolicy.alertSuppressionMinutes()
        );
        Instant snoozedUntil = request != null && request.snoozedUntil() != null
                ? request.snoozedUntil()
                : Instant.now().plusSeconds(snoozeMinutes * 60L);
        if (!snoozedUntil.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Alert snooze deadline must be in the future");
        }

        String previousStatus = alert.getStatus();
        alert.setStatus("SNOOZED");
        alert.setAcknowledgedByUserId(requesterId);
        alert.setAcknowledgedAt(Instant.now());
        alert.setSnoozedUntil(snoozedUntil);
        channelMonetizationArtifactSubscriptionAlertRepository.save(alert);
        recordAlertAuditEvent(
                alert,
                "SNOOZED",
                requesterId,
                alert.getOwnerUserId(),
                previousStatus,
                alert.getStatus(),
                "Snoozed until %s".formatted(snoozedUntil)
        );
        subscription.setEscalationStatus("SNOOZED");
        channelMonetizationArtifactSubscriptionRepository.save(subscription);
        return toArtifactSubscriptionAlertResponse(alert);
    }

    @Transactional
    public MonetizationArtifactSubscriptionAlertResponse resolveArtifactSubscriptionAlert(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId,
            UUID alertId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        ChannelMonetizationArtifactSubscriptionEntity subscription = getSubscription(chatId, subscriptionId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = getSubscriptionAlert(subscriptionId, alertId);
        if (!"RESOLVED".equals(alert.getStatus())) {
            String previousStatus = alert.getStatus();
            alert.setStatus("RESOLVED");
            alert.setResolvedAt(Instant.now());
            channelMonetizationArtifactSubscriptionAlertRepository.save(alert);
            recordAlertAuditEvent(
                    alert,
                    "RESOLVED",
                    requesterId,
                    alert.getOwnerUserId(),
                    previousStatus,
                    alert.getStatus(),
                    null
            );
        }
        subscription.setEscalationStatus("RESOLVED");
        channelMonetizationArtifactSubscriptionRepository.save(subscription);
        return toArtifactSubscriptionAlertResponse(alert);
    }

    @Transactional(readOnly = true)
    public List<MonetizationArtifactSubscriptionAlertResponse> listOverdueArtifactSubscriptionAlerts(
            UUID requesterId,
            UUID chatId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        Instant now = Instant.now();
        return channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId).stream()
                .filter(alert -> isAlertOverdue(alert, now))
                .map(this::toArtifactSubscriptionAlertResponse)
                .toList();
    }

    @Transactional
    public List<MonetizationArtifactSubscriptionAlertResponse> listBreachedArtifactSubscriptionAlerts(
            UUID requesterId,
            UUID chatId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        Instant now = Instant.now();
        List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts = channelMonetizationArtifactSubscriptionAlertRepository
                .findAllByChannelChatIdOrderByCreatedAtDesc(chatId);
        alerts.forEach(alert -> refreshAlertEscalation(alert, now, null));
        return alerts.stream()
                .filter(alert -> isAlertBreached(alert, now))
                .map(this::toArtifactSubscriptionAlertResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonetizationArtifactSubscriptionAlertResponse> listTriageArtifactSubscriptionAlerts(
            UUID requesterId,
            UUID chatId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        Instant now = Instant.now();
        return channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId).stream()
                .filter(alert -> isAlertEligibleForTriage(alert, now))
                .filter(alert -> isAlertDueForTriage(alert, now))
                .sorted(alertQueueComparator(now))
                .map(this::toArtifactSubscriptionAlertResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonetizationArtifactSubscriptionAlertResponse> listOverdueTriageArtifactSubscriptionAlerts(
            UUID requesterId,
            UUID chatId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        Instant now = Instant.now();
        return channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId).stream()
                .filter(alert -> isAlertEligibleForTriageFollowUp(alert, now))
                .filter(alert -> isAlertDueForTriageReminder(alert, now) || isAlertDueForTriageEscalation(alert, now))
                .sorted(alertQueueComparator(now))
                .map(this::toArtifactSubscriptionAlertResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonetizationArtifactSubscriptionAlertResponse> listArtifactSubscriptionAlertQueue(
            UUID requesterId,
            UUID chatId,
            String severity,
            String status,
            UUID ownerUserId,
            Boolean breachedOnly,
            Boolean overdueOnly
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        String normalizedSeverity = normalizeAlertSeverityFilter(severity);
        String normalizedStatus = normalizeAlertStatusFilter(status);
        Instant now = Instant.now();
        return channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId).stream()
                .filter(alert -> normalizedSeverity == null || normalizedSeverity.equals(alert.getSeverity()))
                .filter(alert -> normalizedStatus == null || normalizedStatus.equals(alert.getStatus()))
                .filter(alert -> ownerUserId == null || ownerUserId.equals(alert.getOwnerUserId()))
                .filter(alert -> !Boolean.TRUE.equals(breachedOnly) || isAlertBreached(alert, now))
                .filter(alert -> !Boolean.TRUE.equals(overdueOnly) || isAlertOverdue(alert, now))
                .sorted(alertQueueComparator(now))
                .map(this::toArtifactSubscriptionAlertResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonetizationArtifactAlertWorkloadResponse getArtifactAlertWorkload(UUID requesterId, UUID chatId) {
        ensureCanManageMonetization(requesterId, chatId);
        List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts = channelMonetizationArtifactSubscriptionAlertRepository
                .findAllByChannelChatIdOrderByCreatedAtDesc(chatId);
        return buildAlertWorkload(chatId, alerts, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<MonetizationArtifactSubscriptionAlertResponse> listOwnerArtifactSubscriptionAlertQueue(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            String severity,
            String status,
            Boolean breachedOnly,
            Boolean overdueOnly
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        requireUser(ownerUserId);
        return listArtifactSubscriptionAlertQueue(
                requesterId,
                chatId,
                severity,
                status,
                ownerUserId,
                breachedOnly,
                overdueOnly
        );
    }

    @Transactional(readOnly = true)
    public List<MonetizationArtifactSubscriptionAlertResponse> listMyArtifactSubscriptionAlertQueue(
            UUID requesterId,
            UUID chatId,
            String severity,
            String status,
            Boolean breachedOnly,
            Boolean overdueOnly
    ) {
        return listOwnerArtifactSubscriptionAlertQueue(
                requesterId,
                chatId,
                requesterId,
                severity,
                status,
                breachedOnly,
                overdueOnly
        );
    }

    @Transactional(readOnly = true)
    public List<MonetizationArtifactSubscriptionAlertResponse> listClaimableArtifactSubscriptionAlertQueue(
            UUID requesterId,
            UUID chatId,
            String severity,
            String status,
            Boolean triageOnly,
            Boolean breachedOnly,
            Boolean overdueOnly,
            String strategy
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        Instant now = Instant.now();
        return loadClaimableAlerts(chatId, severity, status, triageOnly, breachedOnly, overdueOnly, strategy, now).stream()
                .map(this::toArtifactSubscriptionAlertResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonetizationClaimableAlertWorkloadResponse getClaimableArtifactAlertWorkload(
            UUID requesterId,
            UUID chatId,
            String severity,
            String status,
            Boolean triageOnly,
            Boolean breachedOnly,
            Boolean overdueOnly,
            String strategy
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        Instant now = Instant.now();
        List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts = loadClaimableAlerts(
                chatId,
                severity,
                status,
                triageOnly,
                breachedOnly,
                overdueOnly,
                strategy,
                now
        );
        ChannelMonetizationArtifactSubscriptionAlertEntity next = alerts.stream().findFirst().orElse(null);
        return new MonetizationClaimableAlertWorkloadResponse(
                chatId,
                alerts.size(),
                (int) alerts.stream().filter(alert -> "HIGH".equals(alert.getSeverity())).count(),
                countBreachedAlerts(alerts, now),
                (int) alerts.stream().filter(alert -> isAlertOverdue(alert, now)).count(),
                (int) alerts.stream().filter(alert -> isAlertEligibleForTriage(alert, now) || isAlertDueForTriage(alert, now)).count(),
                (int) alerts.stream().filter(alert -> isAlertEligibleForTriageFollowUp(alert, now)).count(),
                next != null ? next.getId() : null,
                next != null ? next.getSubscriptionId() : null,
                next != null ? next.getSeverity() : null,
                next != null ? next.getStatus() : null
        );
    }

    @Transactional(readOnly = true)
    public MonetizationArtifactSubscriptionAlertResponse peekNextClaimableArtifactSubscriptionAlert(
            UUID requesterId,
            UUID chatId,
            String severity,
            String status,
            Boolean triageOnly,
            Boolean breachedOnly,
            Boolean overdueOnly,
            String strategy
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        return loadClaimableAlerts(chatId, severity, status, triageOnly, breachedOnly, overdueOnly, strategy, Instant.now())
                .stream()
                .findFirst()
                .map(this::toArtifactSubscriptionAlertResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No claimable monetization alert found"));
    }

    @Transactional(readOnly = true)
    public MonetizationArtifactAlertWorkloadOwnerResponse getOwnerArtifactAlertWorkload(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        requireUser(ownerUserId);
        Instant now = Instant.now();
        return buildOwnerAlertWorkload(chatId, ownerUserId, loadOwnerAlerts(chatId, ownerUserId), now);
    }

    @Transactional(readOnly = true)
    public MonetizationArtifactAlertWorkloadOwnerResponse getMyArtifactAlertWorkload(
            UUID requesterId,
            UUID chatId
    ) {
        return getOwnerArtifactAlertWorkload(requesterId, chatId, requesterId);
    }

    @Transactional(readOnly = true)
    public MonetizationArtifactSubscriptionAlertResponse peekOwnerArtifactSubscriptionAlert(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        requireUser(ownerUserId);
        Instant now = Instant.now();
        return loadOwnerAlerts(chatId, ownerUserId).stream()
                .sorted(alertQueueComparator(now))
                .findFirst()
                .map(this::toArtifactSubscriptionAlertResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No owned monetization alert found"));
    }

    @Transactional(readOnly = true)
    public MonetizationArtifactSubscriptionAlertResponse peekMyArtifactSubscriptionAlert(
            UUID requesterId,
            UUID chatId
    ) {
        return peekOwnerArtifactSubscriptionAlert(requesterId, chatId, requesterId);
    }

    @Transactional(readOnly = true)
    public List<MonetizationArtifactSubscriptionAlertResponse> listOwnerDueArtifactAlertReminderQueue(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            String severity,
            Boolean breachedOnly
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        requireUser(ownerUserId);
        Instant now = Instant.now();
        return loadOwnerDueReminderAlerts(chatId, ownerUserId, severity, breachedOnly, now).stream()
                .map(this::toArtifactSubscriptionAlertResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonetizationArtifactSubscriptionAlertResponse> listMyDueArtifactAlertReminderQueue(
            UUID requesterId,
            UUID chatId,
            String severity,
            Boolean breachedOnly
    ) {
        return listOwnerDueArtifactAlertReminderQueue(requesterId, chatId, requesterId, severity, breachedOnly);
    }

    @Transactional(readOnly = true)
    public List<MonetizationOwnerReminderDigestSubscriptionResponse> listOwnerArtifactAlertReminderDigestSubscriptions(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        requireUser(ownerUserId);
        return channelMonetizationOwnerReminderDigestSubscriptionRepository
                .findAllByChannelChatIdAndOwnerUserIdOrderByCreatedAtDesc(chatId, ownerUserId).stream()
                .map(this::toOwnerReminderDigestSubscriptionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonetizationOwnerReminderDigestSubscriptionResponse> listMyArtifactAlertReminderDigestSubscriptions(
            UUID requesterId,
            UUID chatId
    ) {
        return listOwnerArtifactAlertReminderDigestSubscriptions(requesterId, chatId, requesterId);
    }

    @Transactional(readOnly = true)
    public List<MonetizationOwnerReminderDigestSubscriptionResponse> listArtifactAlertReminderDigestSubscriptionIssues(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            String failureState,
            Boolean retryDueOnly
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        if (ownerUserId != null) {
            requireUser(ownerUserId);
        }
        Instant now = Instant.now();
        return loadOwnerReminderDigestIssueSubscriptions(chatId, ownerUserId, failureState, retryDueOnly, now).stream()
                .map(this::toOwnerReminderDigestSubscriptionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonetizationOwnerReminderDigestSubscriptionResponse> listMyArtifactAlertReminderDigestSubscriptionIssues(
            UUID requesterId,
            UUID chatId,
            String failureState,
            Boolean retryDueOnly
    ) {
        return listArtifactAlertReminderDigestSubscriptionIssues(requesterId, chatId, requesterId, failureState, retryDueOnly);
    }

    @Transactional(readOnly = true)
    public MonetizationOwnerReminderDigestIssueSummaryResponse getArtifactAlertReminderDigestSubscriptionIssueSummary(
            UUID requesterId,
            UUID chatId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        Instant now = Instant.now();
        List<ChannelMonetizationOwnerReminderDigestSubscriptionEntity> issues =
                loadOwnerReminderDigestIssueSubscriptions(chatId, null, null, false, now);
        Instant latestFailureAt = issues.stream()
                .map(ChannelMonetizationOwnerReminderDigestSubscriptionEntity::getLastFailureAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        List<MonetizationOwnerReminderDigestIssueOwnerResponse> owners = issues.stream()
                .collect(java.util.stream.Collectors.groupingBy(ChannelMonetizationOwnerReminderDigestSubscriptionEntity::getOwnerUserId))
                .entrySet()
                .stream()
                .map(entry -> toOwnerReminderDigestIssueOwnerResponse(entry.getKey(), entry.getValue(), now))
                .sorted(Comparator
                        .comparingInt(MonetizationOwnerReminderDigestIssueOwnerResponse::autoPausedSubscriptions).reversed()
                        .thenComparingInt(MonetizationOwnerReminderDigestIssueOwnerResponse::backoffSubscriptions).reversed()
                        .thenComparingInt(MonetizationOwnerReminderDigestIssueOwnerResponse::totalIssues).reversed()
                        .thenComparing(MonetizationOwnerReminderDigestIssueOwnerResponse::latestFailureAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return new MonetizationOwnerReminderDigestIssueSummaryResponse(
                chatId,
                issues.size(),
                countOwnerReminderDigestSubscriptionsByFailureState(issues, "BACKOFF"),
                countOwnerReminderDigestSubscriptionsByFailureState(issues, "AUTO_PAUSED"),
                countDueRetryOwnerReminderDigestSubscriptions(issues, now),
                latestFailureAt,
                owners
        );
    }

    @Transactional
    public MonetizationExportArtifactResponse exportArtifactAlertReminderDigestSubscriptionIssueSummary(
            UUID requesterId,
            UUID chatId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        Instant now = Instant.now();
        MonetizationOwnerReminderDigestIssueSummaryResponse summary =
                getArtifactAlertReminderDigestSubscriptionIssueSummary(requesterId, chatId);
        String content = serializeOwnerReminderDigestIssueSummary(chatId, summary, now);
        return persistArtifact(
                chatId,
                requesterId,
                "ALERT_OWNER_REMINDER_DIGEST_ISSUES_SUMMARY_EXPORT",
                "JSON",
                "channel-%s-owner-reminder-digest-issues-summary.json".formatted(chatId),
                summary.totalIssues(),
                summary.dueRetrySubscriptions(),
                content
        );
    }

    @Transactional
    public MonetizationExportArtifactResponse exportArtifactAlertReminderDigestSubscriptionIssues(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            String failureState,
            Boolean retryDueOnly
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        Instant now = Instant.now();
        List<ChannelMonetizationOwnerReminderDigestSubscriptionEntity> issues =
                loadOwnerReminderDigestIssueSubscriptions(chatId, ownerUserId, failureState, retryDueOnly, now);
        String content = serializeOwnerReminderDigestIssueQueue(chatId, ownerUserId, failureState, retryDueOnly, issues, now);
        String ownerSegment = ownerUserId != null ? "-owner-%s".formatted(ownerUserId) : "";
        String failureSegment = failureState != null && !failureState.isBlank()
                ? "-%s".formatted(normalizeOwnerReminderDigestFailureStateFilter(failureState).toLowerCase(Locale.ROOT))
                : "";
        return persistArtifact(
                chatId,
                requesterId,
                "ALERT_OWNER_REMINDER_DIGEST_ISSUES_EXPORT",
                "JSON",
                "channel-%s-owner-reminder-digest-issues%s%s.json".formatted(chatId, ownerSegment, failureSegment),
                issues.size(),
                countDueRetryOwnerReminderDigestSubscriptions(issues, now),
                content
        );
    }

    @Transactional
    public MonetizationExportArtifactResponse exportMyArtifactAlertReminderDigestSubscriptionIssues(
            UUID requesterId,
            UUID chatId,
            String failureState,
            Boolean retryDueOnly
    ) {
        return exportArtifactAlertReminderDigestSubscriptionIssues(
                requesterId,
                chatId,
                requesterId,
                failureState,
                retryDueOnly
        );
    }

    @Transactional
    public MonetizationArtifactPublicationResponse publishArtifactAlertReminderDigestSubscriptionIssueSummary(
            UUID requesterId,
            UUID chatId,
            PublishMonetizationArtifactRequest request
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        UUID targetChatId = request != null ? request.targetChatId() : null;
        if (targetChatId == null) {
            targetChatId = resolvePersonalReminderDigestTargetChatId(effectiveAlertPolicy(chatId));
        }
        if (targetChatId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target chat id is required");
        }
        chatService.getOwnedChat(requesterId, targetChatId);
        MonetizationExportArtifactResponse artifact = exportArtifactAlertReminderDigestSubscriptionIssueSummary(
                requesterId,
                chatId
        );
        ChannelMonetizationExportArtifactEntity artifactEntity = channelMonetizationExportArtifactRepository.findById(artifact.artifactId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monetization export artifact not found"));
        return publishArtifactInternal(
                requesterId,
                chatId,
                artifactEntity,
                targetChatId,
                request != null ? request.note() : null
        );
    }

    @Transactional
    public MonetizationArtifactPublicationResponse publishArtifactAlertReminderDigestSubscriptionIssues(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            String failureState,
            Boolean retryDueOnly,
            PublishMonetizationArtifactRequest request
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        if (ownerUserId != null) {
            requireUser(ownerUserId);
        }
        UUID targetChatId = request != null ? request.targetChatId() : null;
        if (targetChatId == null) {
            targetChatId = resolvePersonalReminderDigestTargetChatId(effectiveAlertPolicy(chatId));
        }
        if (targetChatId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target chat id is required");
        }
        chatService.getOwnedChat(requesterId, targetChatId);
        MonetizationExportArtifactResponse artifact = exportArtifactAlertReminderDigestSubscriptionIssues(
                requesterId,
                chatId,
                ownerUserId,
                failureState,
                retryDueOnly
        );
        ChannelMonetizationExportArtifactEntity artifactEntity = channelMonetizationExportArtifactRepository.findById(artifact.artifactId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monetization export artifact not found"));
        return publishArtifactInternal(
                requesterId,
                chatId,
                artifactEntity,
                targetChatId,
                request != null ? request.note() : null
        );
    }

    @Transactional
    public MonetizationArtifactPublicationResponse publishMyArtifactAlertReminderDigestSubscriptionIssues(
            UUID requesterId,
            UUID chatId,
            String failureState,
            Boolean retryDueOnly,
            PublishMonetizationArtifactRequest request
    ) {
        return publishArtifactAlertReminderDigestSubscriptionIssues(
                requesterId,
                chatId,
                requesterId,
                failureState,
                retryDueOnly,
                request
        );
    }

    @Transactional
    public MonetizationOwnerReminderDigestIssueActionResponse resumeArtifactAlertReminderDigestSubscriptionIssues(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            String failureState,
            Boolean retryDueOnly,
            Integer limit
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        Instant now = Instant.now();
        List<ChannelMonetizationOwnerReminderDigestSubscriptionEntity> issues =
                loadOwnerReminderDigestIssueSubscriptions(chatId, ownerUserId, failureState, retryDueOnly, now);
        int resolvedLimit = normalizeOwnerReminderDigestIssueBatchLimit(limit);
        List<UUID> subscriptionIds = issues.stream()
                .limit(resolvedLimit)
                .map(subscription -> {
                    subscription.setStatus("ACTIVE");
                    clearOwnerReminderDigestSubscriptionFailure(subscription);
                    channelMonetizationOwnerReminderDigestSubscriptionRepository.save(subscription);
                    return subscription.getId();
                })
                .toList();
        return new MonetizationOwnerReminderDigestIssueActionResponse(
                chatId,
                ownerUserId,
                normalizeOwnerReminderDigestFailureStateFilter(failureState),
                Boolean.TRUE.equals(retryDueOnly),
                issues.size(),
                subscriptionIds.size(),
                now,
                subscriptionIds,
                List.of()
        );
    }

    @Transactional
    public MonetizationOwnerReminderDigestIssueActionResponse resumeMyArtifactAlertReminderDigestSubscriptionIssues(
            UUID requesterId,
            UUID chatId,
            String failureState,
            Boolean retryDueOnly,
            Integer limit
    ) {
        return resumeArtifactAlertReminderDigestSubscriptionIssues(
                requesterId,
                chatId,
                requesterId,
                failureState,
                retryDueOnly,
                limit
        );
    }

    @Transactional
    public MonetizationOwnerReminderDigestIssueActionResponse retryArtifactAlertReminderDigestSubscriptionIssues(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            Boolean retryDueOnly,
            Integer limit
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        Instant now = Instant.now();
        List<ChannelMonetizationOwnerReminderDigestSubscriptionEntity> issues =
                loadOwnerReminderDigestIssueSubscriptions(chatId, ownerUserId, "BACKOFF", retryDueOnly, now).stream()
                        .filter(subscription -> "ACTIVE".equals(subscription.getStatus()))
                        .toList();
        int resolvedLimit = normalizeOwnerReminderDigestIssueBatchLimit(limit);
        List<MonetizationOwnerReminderDigestRunResponse> runs = issues.stream()
                .limit(resolvedLimit)
                .map(subscription -> executeOwnerReminderDigestSubscription(subscription, now, "MANUAL_RETRY", requesterId))
                .toList();
        return new MonetizationOwnerReminderDigestIssueActionResponse(
                chatId,
                ownerUserId,
                "BACKOFF",
                Boolean.TRUE.equals(retryDueOnly),
                issues.size(),
                runs.size(),
                now,
                runs.stream().map(MonetizationOwnerReminderDigestRunResponse::subscriptionId).toList(),
                runs.stream().map(MonetizationOwnerReminderDigestRunResponse::runId).toList()
        );
    }

    @Transactional
    public MonetizationOwnerReminderDigestIssueActionResponse retryMyArtifactAlertReminderDigestSubscriptionIssues(
            UUID requesterId,
            UUID chatId,
            Boolean retryDueOnly,
            Integer limit
    ) {
        return retryArtifactAlertReminderDigestSubscriptionIssues(
                requesterId,
                chatId,
                requesterId,
                retryDueOnly,
                limit
        );
    }

    @Transactional
    public MonetizationOwnerReminderDigestSubscriptionResponse createOwnerArtifactAlertReminderDigestSubscription(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            CreateMonetizationOwnerReminderDigestSubscriptionRequest request
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        requireUser(ownerUserId);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reminder digest subscription payload is required");
        }
        if (request.targetChatId() != null) {
            chatService.getOwnedChat(requesterId, request.targetChatId());
        }

        ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription =
                new ChannelMonetizationOwnerReminderDigestSubscriptionEntity();
        subscription.setChannelChatId(chatId);
        subscription.setOwnerUserId(ownerUserId);
        subscription.setTargetChatId(request.targetChatId());
        subscription.setCreatedByUserId(requesterId);
        subscription.setSeverity(normalizeAlertSeverityFilter(request.severity()));
        subscription.setBreachedOnly(Boolean.TRUE.equals(request.breachedOnly()));
        subscription.setNote(normalizeOptional(request.note(), 255));
        subscription.setStatus("ACTIVE");
        subscription.setMinIntervalMinutes(normalizeSubscriptionInterval(request.minIntervalMinutes()));
        return toOwnerReminderDigestSubscriptionResponse(
                channelMonetizationOwnerReminderDigestSubscriptionRepository.save(subscription)
        );
    }

    @Transactional
    public MonetizationOwnerReminderDigestSubscriptionResponse createMyArtifactAlertReminderDigestSubscription(
            UUID requesterId,
            UUID chatId,
            CreateMonetizationOwnerReminderDigestSubscriptionRequest request
    ) {
        return createOwnerArtifactAlertReminderDigestSubscription(requesterId, chatId, requesterId, request);
    }

    @Transactional(readOnly = true)
    public List<MonetizationOwnerReminderDigestRunResponse> listOwnerArtifactAlertReminderDigestSubscriptionRuns(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            UUID subscriptionId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        getOwnerReminderDigestSubscription(chatId, ownerUserId, subscriptionId);
        return channelMonetizationOwnerReminderDigestRunRepository.findAllBySubscriptionIdOrderByProcessedAtDesc(subscriptionId).stream()
                .map(this::toOwnerReminderDigestRunResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonetizationOwnerReminderDigestRunResponse> listMyArtifactAlertReminderDigestSubscriptionRuns(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId
    ) {
        return listOwnerArtifactAlertReminderDigestSubscriptionRuns(requesterId, chatId, requesterId, subscriptionId);
    }

    @Transactional
    public MonetizationOwnerReminderDigestSubscriptionResponse pauseOwnerArtifactAlertReminderDigestSubscription(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            UUID subscriptionId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription = getOwnerReminderDigestSubscription(
                chatId,
                ownerUserId,
                subscriptionId
        );
        subscription.setStatus("PAUSED");
        return toOwnerReminderDigestSubscriptionResponse(
                channelMonetizationOwnerReminderDigestSubscriptionRepository.save(subscription)
        );
    }

    @Transactional
    public MonetizationOwnerReminderDigestSubscriptionResponse pauseMyArtifactAlertReminderDigestSubscription(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId
    ) {
        return pauseOwnerArtifactAlertReminderDigestSubscription(requesterId, chatId, requesterId, subscriptionId);
    }

    @Transactional
    public MonetizationOwnerReminderDigestSubscriptionResponse resumeOwnerArtifactAlertReminderDigestSubscription(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            UUID subscriptionId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription = getOwnerReminderDigestSubscription(
                chatId,
                ownerUserId,
                subscriptionId
        );
        subscription.setStatus("ACTIVE");
        clearOwnerReminderDigestSubscriptionFailure(subscription);
        return toOwnerReminderDigestSubscriptionResponse(
                channelMonetizationOwnerReminderDigestSubscriptionRepository.save(subscription)
        );
    }

    @Transactional
    public MonetizationOwnerReminderDigestSubscriptionResponse resumeMyArtifactAlertReminderDigestSubscription(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId
    ) {
        return resumeOwnerArtifactAlertReminderDigestSubscription(requesterId, chatId, requesterId, subscriptionId);
    }

    @Transactional
    public MonetizationOwnerReminderDigestRunResponse dispatchOwnerArtifactAlertReminderDigestSubscription(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            UUID subscriptionId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription = getOwnerReminderDigestSubscription(
                chatId,
                ownerUserId,
                subscriptionId
        );
        return executeOwnerReminderDigestSubscription(subscription, Instant.now(), "MANUAL", requesterId);
    }

    @Transactional
    public MonetizationOwnerReminderDigestRunResponse dispatchMyArtifactAlertReminderDigestSubscription(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId
    ) {
        return dispatchOwnerArtifactAlertReminderDigestSubscription(requesterId, chatId, requesterId, subscriptionId);
    }

    @Transactional(readOnly = true)
    public MonetizationArtifactAlertReminderDigestResponse getOwnerArtifactAlertReminderDigest(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            String severity,
            Boolean breachedOnly
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        requireUser(ownerUserId);
        Instant now = Instant.now();
        return buildOwnerReminderDigest(
                ownerUserId,
                loadOwnerDueReminderAlerts(chatId, ownerUserId, severity, breachedOnly, now),
                now
        );
    }

    @Transactional(readOnly = true)
    public MonetizationArtifactAlertReminderDigestResponse getMyArtifactAlertReminderDigest(
            UUID requesterId,
            UUID chatId,
            String severity,
            Boolean breachedOnly
    ) {
        return getOwnerArtifactAlertReminderDigest(requesterId, chatId, requesterId, severity, breachedOnly);
    }

    @Transactional
    public MonetizationArtifactAlertReminderBatchResponse remindOwnerDueArtifactAlerts(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            String severity,
            Boolean breachedOnly,
            Integer limit
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        requireUser(ownerUserId);
        Instant now = Instant.now();
        List<ChannelMonetizationArtifactSubscriptionAlertEntity> dueAlerts = loadOwnerDueReminderAlerts(
                chatId,
                ownerUserId,
                severity,
                breachedOnly,
                now
        );
        int resolvedLimit = normalizeAlertReminderBatchLimit(limit);
        List<MonetizationArtifactAlertReminderResponse> reminders = dueAlerts.stream()
                .limit(resolvedLimit)
                .map(alert -> sendAlertReminder(alert, requesterId, true))
                .toList();
        return new MonetizationArtifactAlertReminderBatchResponse(
                ownerUserId,
                resolveOwnerDisplayName(ownerUserId),
                dueAlerts.size(),
                reminders.size(),
                Instant.now(),
                reminders
        );
    }

    @Transactional
    public MonetizationArtifactAlertReminderBatchResponse remindMyDueArtifactAlerts(
            UUID requesterId,
            UUID chatId,
            String severity,
            Boolean breachedOnly,
            Integer limit
    ) {
        return remindOwnerDueArtifactAlerts(requesterId, chatId, requesterId, severity, breachedOnly, limit);
    }

    @Transactional
    public MonetizationExportArtifactResponse exportOwnerArtifactAlertReminderQueue(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            String severity,
            Boolean breachedOnly
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        requireUser(ownerUserId);
        Instant now = Instant.now();
        List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts = loadOwnerDueReminderAlerts(
                chatId,
                ownerUserId,
                severity,
                breachedOnly,
                now
        );
        String content = serializeOwnerReminderQueue(chatId, ownerUserId, alerts, now);
        return persistArtifact(
                chatId,
                requesterId,
                "ALERT_OWNER_REMINDER_QUEUE_EXPORT",
                "JSON",
                "channel-%s-alert-owner-%s-reminder-queue.json".formatted(chatId, ownerUserId),
                alerts.size(),
                countBreachedAlerts(alerts, now),
                content
        );
    }

    @Transactional
    public MonetizationExportArtifactResponse exportMyArtifactAlertReminderQueue(
            UUID requesterId,
            UUID chatId,
            String severity,
            Boolean breachedOnly
    ) {
        return exportOwnerArtifactAlertReminderQueue(requesterId, chatId, requesterId, severity, breachedOnly);
    }

    @Transactional
    public MonetizationExportArtifactResponse exportOwnerArtifactAlertReminderDigest(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            String severity,
            Boolean breachedOnly
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        requireUser(ownerUserId);
        Instant now = Instant.now();
        List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts = loadOwnerDueReminderAlerts(
                chatId,
                ownerUserId,
                severity,
                breachedOnly,
                now
        );
        MonetizationArtifactAlertReminderDigestResponse digest = buildOwnerReminderDigest(ownerUserId, alerts, now);
        String content = serializeOwnerReminderDigest(chatId, digest, alerts, now);
        return persistArtifact(
                chatId,
                requesterId,
                "ALERT_OWNER_REMINDER_DIGEST_EXPORT",
                "JSON",
                "channel-%s-alert-owner-%s-reminder-digest.json".formatted(chatId, ownerUserId),
                alerts.size(),
                digest.breachedDueAlerts(),
                content
        );
    }

    @Transactional
    public MonetizationExportArtifactResponse exportMyArtifactAlertReminderDigest(
            UUID requesterId,
            UUID chatId,
            String severity,
            Boolean breachedOnly
    ) {
        return exportOwnerArtifactAlertReminderDigest(requesterId, chatId, requesterId, severity, breachedOnly);
    }

    @Transactional
    public MonetizationArtifactPublicationResponse publishOwnerArtifactAlertReminderQueue(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            String severity,
            Boolean breachedOnly,
            PublishMonetizationArtifactRequest request
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        requireUser(ownerUserId);
        UUID targetChatId = request != null ? request.targetChatId() : null;
        if (targetChatId == null) {
            targetChatId = resolvePersonalReminderDigestTargetChatId(effectiveAlertPolicy(chatId));
        }
        if (targetChatId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target chat id is required");
        }
        chatService.getOwnedChat(requesterId, targetChatId);
        MonetizationExportArtifactResponse artifact = exportOwnerArtifactAlertReminderQueue(
                requesterId,
                chatId,
                ownerUserId,
                severity,
                breachedOnly
        );
        ChannelMonetizationExportArtifactEntity artifactEntity = channelMonetizationExportArtifactRepository.findById(artifact.artifactId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monetization export artifact not found"));
        return publishArtifactInternal(
                requesterId,
                chatId,
                artifactEntity,
                targetChatId,
                request != null ? request.note() : null
        );
    }

    @Transactional
    public MonetizationArtifactPublicationResponse publishMyArtifactAlertReminderQueue(
            UUID requesterId,
            UUID chatId,
            String severity,
            Boolean breachedOnly,
            PublishMonetizationArtifactRequest request
    ) {
        return publishOwnerArtifactAlertReminderQueue(requesterId, chatId, requesterId, severity, breachedOnly, request);
    }

    @Transactional
    public MonetizationArtifactPublicationResponse publishOwnerArtifactAlertReminderDigest(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            String severity,
            Boolean breachedOnly,
            PublishMonetizationArtifactRequest request
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        requireUser(ownerUserId);
        UUID targetChatId = request != null ? request.targetChatId() : null;
        if (targetChatId == null) {
            targetChatId = resolvePersonalReminderDigestTargetChatId(effectiveAlertPolicy(chatId));
        }
        if (targetChatId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target chat id is required");
        }
        chatService.getOwnedChat(requesterId, targetChatId);
        MonetizationExportArtifactResponse artifact = exportOwnerArtifactAlertReminderDigest(
                requesterId,
                chatId,
                ownerUserId,
                severity,
                breachedOnly
        );
        ChannelMonetizationExportArtifactEntity artifactEntity = channelMonetizationExportArtifactRepository.findById(artifact.artifactId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monetization export artifact not found"));
        return publishArtifactInternal(
                requesterId,
                chatId,
                artifactEntity,
                targetChatId,
                request != null ? request.note() : null
        );
    }

    @Transactional
    public MonetizationArtifactPublicationResponse publishMyArtifactAlertReminderDigest(
            UUID requesterId,
            UUID chatId,
            String severity,
            Boolean breachedOnly,
            PublishMonetizationArtifactRequest request
    ) {
        return publishOwnerArtifactAlertReminderDigest(requesterId, chatId, requesterId, severity, breachedOnly, request);
    }

    @Transactional
    public MonetizationExportArtifactResponse exportArtifactAlertWorkload(UUID requesterId, UUID chatId) {
        ensureCanManageMonetization(requesterId, chatId);
        Instant now = Instant.now();
        List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts = channelMonetizationArtifactSubscriptionAlertRepository
                .findAllByChannelChatIdOrderByCreatedAtDesc(chatId);
        MonetizationArtifactAlertWorkloadResponse workload = buildAlertWorkload(chatId, alerts, now);
        String content = serializeAlertWorkload(workload, alerts, now);
        return persistArtifact(
                chatId,
                requesterId,
                "ALERT_WORKLOAD_EXPORT",
                "JSON",
                "channel-%s-alert-workload.json".formatted(chatId),
                alerts.size(),
                workload.breachedAlerts(),
                content
        );
    }

    @Transactional
    public MonetizationExportArtifactResponse exportOwnerArtifactAlertWorkload(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        requireUser(ownerUserId);
        Instant now = Instant.now();
        List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts = loadOwnerAlerts(chatId, ownerUserId);
        MonetizationArtifactAlertWorkloadOwnerResponse workload = buildOwnerAlertWorkload(chatId, ownerUserId, alerts, now);
        String content = serializeOwnerAlertWorkload(chatId, workload, alerts, now);
        return persistArtifact(
                chatId,
                requesterId,
                "ALERT_OWNER_WORKLOAD_EXPORT",
                "JSON",
                "channel-%s-alert-owner-%s-workload.json".formatted(chatId, ownerUserId),
                alerts.size(),
                workload.breachedAlerts(),
                content
        );
    }

    @Transactional
    public MonetizationExportArtifactResponse exportMyArtifactAlertWorkload(UUID requesterId, UUID chatId) {
        return exportOwnerArtifactAlertWorkload(requesterId, chatId, requesterId);
    }

    @Transactional
    public MonetizationArtifactPublicationResponse publishOwnerArtifactAlertWorkload(
            UUID requesterId,
            UUID chatId,
            UUID ownerUserId,
            PublishMonetizationArtifactRequest request
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        requireUser(ownerUserId);
        UUID targetChatId = request != null ? request.targetChatId() : null;
        if (targetChatId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target chat id is required");
        }
        chatService.getOwnedChat(requesterId, targetChatId);
        MonetizationExportArtifactResponse artifact = exportOwnerArtifactAlertWorkload(requesterId, chatId, ownerUserId);
        ChannelMonetizationExportArtifactEntity artifactEntity = channelMonetizationExportArtifactRepository.findById(artifact.artifactId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monetization export artifact not found"));
        return publishArtifactInternal(
                requesterId,
                chatId,
                artifactEntity,
                targetChatId,
                request != null ? request.note() : null
        );
    }

    @Transactional
    public MonetizationArtifactPublicationResponse publishMyArtifactAlertWorkload(
            UUID requesterId,
            UUID chatId,
            PublishMonetizationArtifactRequest request
    ) {
        return publishOwnerArtifactAlertWorkload(requesterId, chatId, requesterId, request);
    }

    @Transactional
    public MonetizationArtifactAlertReminderResponse remindArtifactSubscriptionAlert(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId,
            UUID alertId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        getSubscription(chatId, subscriptionId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = getSubscriptionAlert(subscriptionId, alertId);
        if ("RESOLVED".equals(alert.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Resolved monetization alert cannot be reminded");
        }
        refreshAlertEscalation(alert, Instant.now(), requesterId);
        return sendAlertReminder(alert, requesterId, true);
    }

    @Transactional
    public MonetizationArtifactAlertTriageResponse triageArtifactSubscriptionAlert(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId,
            UUID alertId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        getSubscription(chatId, subscriptionId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = getSubscriptionAlert(subscriptionId, alertId);
        if (!isAlertEligibleForTriage(alert, Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Monetization alert is not eligible for triage");
        }
        return sendAlertTriage(alert, requesterId, true);
    }

    @Transactional
    public MonetizationArtifactAlertTriageReminderResponse remindTriageArtifactSubscriptionAlert(
            UUID requesterId,
            UUID chatId,
            UUID subscriptionId,
            UUID alertId
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        getSubscription(chatId, subscriptionId);
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = getSubscriptionAlert(subscriptionId, alertId);
        if (!isAlertEligibleForTriageFollowUp(alert, Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Monetization alert is not eligible for triage reminder");
        }
        return sendAlertTriageReminder(alert, requesterId, true);
    }

    @Transactional(readOnly = true)
    public List<MonetizationAlertDigestRunResponse> listAlertDigestRuns(UUID requesterId, UUID chatId) {
        ensureCanManageMonetization(requesterId, chatId);
        return channelMonetizationAlertDigestRunRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId).stream()
                .map(this::toAlertDigestRunResponse)
                .toList();
    }

    @Transactional
    public MonetizationAlertDigestRunResponse generateAlertDigest(
            UUID requesterId,
            UUID chatId,
            GenerateMonetizationAlertDigestRequest request
    ) {
        ensureCanManageMonetization(requesterId, chatId);
        EffectiveAlertPolicy alertPolicy = effectiveAlertPolicy(chatId);
        UUID targetChatId = request != null && request.targetChatId() != null
                ? request.targetChatId()
                : alertPolicy.digestTargetChatId();
        if (targetChatId != null) {
            chatService.getOwnedChat(requesterId, targetChatId);
        }
        MonetizationAlertDigestRunResponse run = createAlertDigestRun(
                chatId,
                requesterId,
                "MANUAL",
                targetChatId,
                request != null ? request.note() : null
        );
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No open monetization subscription alerts are available for digest generation");
        }
        return run;
    }

    @Transactional
    public MonetizationPayoutExportResponse exportPayouts(UUID requesterId, UUID chatId) {
        ensureCanManageMonetization(requesterId, chatId);
        List<ChannelMonetizationPayoutEntity> payouts = channelMonetizationPayoutRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId);
        List<MonetizationPayoutResponse> payoutResponses = toPayoutResponses(payouts);

        StringBuilder csv = new StringBuilder();
        csv.append("payout_id,channel_chat_id,recipient_user_id,trigger_mode,status,total_units,sponsored_message_count,period_started_at,period_ended_at,completed_at,item_count\n");
        for (MonetizationPayoutResponse payout : payoutResponses) {
            csv.append(payout.payoutId()).append(',')
                    .append(payout.channelChatId()).append(',')
                    .append(payout.recipientUserId()).append(',')
                    .append(payout.triggerMode()).append(',')
                    .append(payout.status()).append(',')
                    .append(payout.totalUnits()).append(',')
                    .append(payout.sponsoredMessageCount()).append(',')
                    .append(csvValue(payout.periodStartedAt())).append(',')
                    .append(csvValue(payout.periodEndedAt())).append(',')
                    .append(csvValue(payout.completedAt())).append(',')
                    .append(payout.items().size()).append('\n');
        }

        persistArtifact(
                chatId,
                requesterId,
                "PAYOUTS_EXPORT",
                "CSV",
                "channel-%s-payouts.csv".formatted(chatId),
                payoutResponses.size(),
                payoutResponses.stream().mapToLong(MonetizationPayoutResponse::totalUnits).sum(),
                csv.toString()
        );

        return new MonetizationPayoutExportResponse(
                chatId,
                "CSV",
                "channel-%s-payouts.csv".formatted(chatId),
                payoutResponses.size(),
                payoutResponses.stream().mapToLong(MonetizationPayoutResponse::totalUnits).sum(),
                Instant.now(),
                csv.toString()
        );
    }

    @Transactional
    public MonetizationExportArtifactResponse exportWithdrawals(UUID requesterId, UUID chatId) {
        ensureCanManageMonetization(requesterId, chatId);
        List<ChannelMonetizationWithdrawalEntity> withdrawals = channelMonetizationWithdrawalRepository
                .findAllByChannelChatIdOrderByRequestedAtDesc(chatId);
        StringBuilder csv = new StringBuilder();
        csv.append("withdrawal_id,channel_chat_id,recipient_user_id,requested_by_user_id,amount_units,currency_code,status,provider_reference,provider_status,destination_type,destination_label,requested_at,processing_at,provider_updated_at,completed_at,canceled_at,failure_reason\n");
        for (ChannelMonetizationWithdrawalEntity withdrawal : withdrawals) {
            csv.append(withdrawal.getId()).append(',')
                    .append(withdrawal.getChannelChatId()).append(',')
                    .append(withdrawal.getRecipientUserId()).append(',')
                    .append(withdrawal.getRequestedByUserId()).append(',')
                    .append(withdrawal.getAmountUnits() != null ? withdrawal.getAmountUnits() : 0L).append(',')
                    .append(csvValue(withdrawal.getCurrencyCode())).append(',')
                    .append(csvValue(withdrawal.getStatus())).append(',')
                    .append(csvValue(withdrawal.getProviderReference())).append(',')
                    .append(csvValue(withdrawal.getProviderStatus())).append(',')
                    .append(csvValue(withdrawal.getDestinationType())).append(',')
                    .append(csvValue(withdrawal.getDestinationLabel())).append(',')
                    .append(csvValue(withdrawal.getRequestedAt())).append(',')
                    .append(csvValue(withdrawal.getProcessingAt())).append(',')
                    .append(csvValue(withdrawal.getProviderUpdatedAt())).append(',')
                    .append(csvValue(withdrawal.getCompletedAt())).append(',')
                    .append(csvValue(withdrawal.getCanceledAt())).append(',')
                    .append(csvValue(withdrawal.getFailureReason())).append('\n');
        }
        return persistArtifact(
                chatId,
                requesterId,
                "WITHDRAWALS_EXPORT",
                "CSV",
                "channel-%s-withdrawals.csv".formatted(chatId),
                withdrawals.size(),
                withdrawals.stream().mapToLong(withdrawal -> withdrawal.getAmountUnits() != null ? withdrawal.getAmountUnits() : 0L).sum(),
                csv.toString()
        );
    }

    @Transactional
    public MonetizationExportArtifactResponse exportReport(UUID requesterId, UUID chatId) {
        ChannelMonetizationReportResponse report = getChannelReport(requesterId, chatId);
        String content = serializeReport(report);
        return persistArtifact(
                chatId,
                requesterId,
                "REPORT_EXPORT",
                "JSON",
                "channel-%s-monetization-report.json".formatted(chatId),
                1,
                report.totalRevenueUnits(),
                content
        );
    }

    @Transactional(readOnly = true)
    public List<MonetizationExportArtifactResponse> listArtifacts(UUID requesterId, UUID chatId) {
        ensureCanManageMonetization(requesterId, chatId);
        return channelMonetizationExportArtifactRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId).stream()
                .map(artifact -> toArtifactResponse(artifact, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public MonetizationExportArtifactResponse getArtifact(UUID requesterId, UUID chatId, UUID artifactId) {
        ensureCanManageMonetization(requesterId, chatId);
        ChannelMonetizationExportArtifactEntity artifact = channelMonetizationExportArtifactRepository
                .findByIdAndChannelChatId(artifactId, chatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monetization export artifact not found"));
        return toArtifactResponse(artifact, true);
    }

    @Transactional(readOnly = true)
    public List<MonetizationReconciliationRunResponse> listReconciliationRuns(UUID requesterId, UUID chatId) {
        ensureCanManageMonetization(requesterId, chatId);
        return channelMonetizationReconciliationRunRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId).stream()
                .map(this::toReconciliationRunResponse)
                .toList();
    }

    @Transactional
    public MonetizationReconciliationRunResponse runReconciliation(UUID requesterId, UUID chatId, int batchSize) {
        ensureCanManageMonetization(requesterId, chatId);
        List<ChannelMonetizationWithdrawalEntity> withdrawals = channelMonetizationWithdrawalRepository.lockProcessingByChannel(chatId, Math.max(1, batchSize));
        if (withdrawals.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No monetization withdrawals are waiting for reconciliation");
        }
        List<ChannelMonetizationWithdrawalEntity> reconciled = withdrawals.stream()
                .map(this::reconcileProcessingWithdrawal)
                .map(response -> getManagedWithdrawal(chatId, response.withdrawalId()))
                .toList();
        return saveReconciliationRun(chatId, requesterId, "MANUAL", reconciled);
    }

    @Transactional(readOnly = true)
    public ChannelMonetizationReportResponse getChannelReport(UUID requesterId, UUID chatId) {
        ensureCanManageMonetization(requesterId, chatId);
        List<SponsoredMessageEntity> sponsoredMessages = sponsoredMessageRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId);
        List<ChannelMonetizationPayoutEntity> payouts = channelMonetizationPayoutRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId);
        List<ChannelMonetizationWithdrawalEntity> withdrawals = channelMonetizationWithdrawalRepository.findAllByChannelChatIdOrderByRequestedAtDesc(chatId);
        List<ChannelMonetizationReconciliationRunEntity> runs = channelMonetizationReconciliationRunRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId);

        long totalRevenueUnits = sponsoredMessages.stream().mapToLong(message -> message.getEarnedUnits() != null ? message.getEarnedUnits() : 0L).sum();
        long totalSettledUnits = sponsoredMessages.stream().mapToLong(message -> message.getSettledUnits() != null ? message.getSettledUnits() : 0L).sum();
        long outstandingPayoutUnits = sponsoredMessages.stream().mapToLong(this::outstandingPayoutUnits).sum();
        long availableWithdrawalUnits = getAvailableWithdrawalUnits(chatId);
        long totalWithdrawnUnits = withdrawals.stream()
                .filter(withdrawal -> "COMPLETED".equals(withdrawal.getStatus()))
                .mapToLong(withdrawal -> withdrawal.getAmountUnits() != null ? withdrawal.getAmountUnits() : 0L)
                .sum();
        long pendingWithdrawalUnits = withdrawals.stream()
                .filter(withdrawal -> "PENDING".equals(withdrawal.getStatus()) || "PROCESSING".equals(withdrawal.getStatus()))
                .mapToLong(withdrawal -> withdrawal.getAmountUnits() != null ? withdrawal.getAmountUnits() : 0L)
                .sum();
        long failedWithdrawalUnits = withdrawals.stream()
                .filter(withdrawal -> "FAILED".equals(withdrawal.getStatus()))
                .mapToLong(withdrawal -> withdrawal.getAmountUnits() != null ? withdrawal.getAmountUnits() : 0L)
                .sum();
        int totalCompletedCampaigns = (int) sponsoredMessages.stream().filter(message -> {
            long earned = message.getEarnedUnits() != null ? message.getEarnedUnits() : 0L;
            return earned > 0;
        }).count();

        return new ChannelMonetizationReportResponse(
                chatId,
                totalRevenueUnits,
                totalSettledUnits,
                outstandingPayoutUnits,
                availableWithdrawalUnits,
                totalWithdrawnUnits,
                pendingWithdrawalUnits,
                failedWithdrawalUnits,
                withdrawals.size(),
                countWithdrawalsByStatus(withdrawals, "PENDING"),
                countWithdrawalsByStatus(withdrawals, "PROCESSING"),
                countWithdrawalsByStatus(withdrawals, "COMPLETED"),
                countWithdrawalsByStatus(withdrawals, "FAILED"),
                countWithdrawalsByStatus(withdrawals, "CANCELED"),
                totalCompletedCampaigns == 0 ? 0.0d : totalRevenueUnits / (double) totalCompletedCampaigns,
                payouts.stream().map(ChannelMonetizationPayoutEntity::getCompletedAt).filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(null),
                withdrawals.stream().map(withdrawal -> withdrawal.getCompletedAt() != null ? withdrawal.getCompletedAt() : withdrawal.getRequestedAt())
                        .filter(java.util.Objects::nonNull)
                        .max(Instant::compareTo)
                        .orElse(null),
                runs.stream().map(ChannelMonetizationReconciliationRunEntity::getCreatedAt).filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(null)
        );
    }

    @Transactional
    public int processReadyPayouts(Instant eligibleBefore, int batchSize) {
        List<SponsoredMessageEntity> readyMessages = sponsoredMessageRepository.lockReadyForPayoutBatch(eligibleBefore, Math.max(1, batchSize));
        if (readyMessages.isEmpty()) {
            return 0;
        }

        int processed = 0;
        for (Map.Entry<UUID, List<SponsoredMessageEntity>> entry : readyMessages.stream()
                .collect(java.util.stream.Collectors.groupingBy(SponsoredMessageEntity::getChannelChatId))
                .entrySet()) {
            processed += createPayoutRecord(entry.getValue(), "SCHEDULED", null).sponsoredMessageCount();
        }
        return processed;
    }

    @Transactional
    public int processPendingWithdrawals(Instant eligibleBefore, int batchSize) {
        List<ChannelMonetizationWithdrawalEntity> withdrawals = channelMonetizationWithdrawalRepository.lockPendingBatch(
                eligibleBefore,
                Math.max(1, batchSize)
        );
        int processed = 0;
        for (ChannelMonetizationWithdrawalEntity withdrawal : withdrawals) {
            startWithdrawalProcessing(withdrawal);
            processed++;
        }
        return processed;
    }

    @Transactional
    public int processWithdrawalReconciliation(Instant eligibleBefore, int batchSize) {
        List<ChannelMonetizationWithdrawalEntity> withdrawals = channelMonetizationWithdrawalRepository.lockProcessingBatch(
                eligibleBefore,
                Math.max(1, batchSize)
        );
        if (withdrawals.isEmpty()) {
            return 0;
        }
        Map<UUID, List<ChannelMonetizationWithdrawalEntity>> reconciledByChannel = withdrawals.stream()
                .map(this::reconcileProcessingWithdrawal)
                .map(response -> getManagedWithdrawal(response.channelChatId(), response.withdrawalId()))
                .collect(java.util.stream.Collectors.groupingBy(ChannelMonetizationWithdrawalEntity::getChannelChatId));
        for (Map.Entry<UUID, List<ChannelMonetizationWithdrawalEntity>> entry : reconciledByChannel.entrySet()) {
            saveReconciliationRun(entry.getKey(), null, "SCHEDULED", entry.getValue());
        }
        return withdrawals.size();
    }

    @Transactional
    public int processArtifactSubscriptions(Instant eligibleBefore, int batchSize) {
        List<ChannelMonetizationArtifactSubscriptionEntity> subscriptions = channelMonetizationArtifactSubscriptionRepository
                .lockActiveBatch(eligibleBefore, Math.max(1, batchSize));
        int processed = 0;
        for (ChannelMonetizationArtifactSubscriptionEntity subscription : subscriptions) {
            try {
                if (!isSubscriptionDue(subscription, eligibleBefore)) {
                    continue;
                }
                ChannelMonetizationExportArtifactEntity artifact = resolveArtifactForSubscription(subscription);
                if (artifact == null) {
                    continue;
                }
                if (artifact.getId().equals(subscription.getLastDeliveredArtifactId())) {
                    continue;
                }
                publishArtifactInternal(
                        subscription.getCreatedByUserId(),
                        subscription.getChannelChatId(),
                        artifact,
                        subscription.getTargetChatId(),
                        subscription.getNote()
                );
                subscription.setLastDeliveredArtifactId(artifact.getId());
                subscription.setLastDeliveredAt(Instant.now());
                subscription.setConsecutiveFailureCount(0);
                subscription.setLastFailureAt(null);
                subscription.setLastFailureReason(null);
                subscription.setEscalationStatus("NONE");
                channelMonetizationArtifactSubscriptionRepository.save(subscription);
                resolveOpenSubscriptionAlert(subscription);
                processed++;
            } catch (ResponseStatusException exception) {
                handleArtifactSubscriptionFailure(subscription, exception.getReason());
            }
        }
        return processed;
    }

    @Transactional
    public int processAlertDigests(Instant eligibleBefore, int batchSize) {
        List<ChannelMonetizationArtifactSubscriptionEntity> subscriptions = channelMonetizationArtifactSubscriptionRepository
                .lockEscalatedBatch(eligibleBefore, Math.max(1, batchSize));
        if (subscriptions.isEmpty()) {
            return 0;
        }

        int processed = 0;
        for (Map.Entry<UUID, List<ChannelMonetizationArtifactSubscriptionEntity>> entry : subscriptions.stream()
                .filter(subscription -> isAlertDigestDue(subscription))
                .collect(java.util.stream.Collectors.groupingBy(ChannelMonetizationArtifactSubscriptionEntity::getChannelChatId))
                .entrySet()) {
            EffectiveAlertPolicy alertPolicy = effectiveAlertPolicy(entry.getKey());
            if (!alertPolicy.autoDigestEnabled()) {
                continue;
            }
            MonetizationAlertDigestRunResponse run = createAlertDigestRun(
                    entry.getKey(),
                    null,
                    "SCHEDULED",
                    alertPolicy.digestTargetChatId() != null ? alertPolicy.digestTargetChatId() : entry.getKey(),
                    "Scheduled alert digest"
            );
            if (run != null) {
                Instant alertedAt = Instant.now();
                for (ChannelMonetizationArtifactSubscriptionEntity subscription : entry.getValue()) {
                    subscription.setLastAlertedAt(alertedAt);
                    subscription.setEscalationStatus("OPEN");
                }
                channelMonetizationArtifactSubscriptionRepository.saveAll(entry.getValue());
                processed++;
            }
        }
        return processed;
    }

    @Transactional
    public int processOwnerReminderDigestSubscriptions(Instant dueBefore, int batchSize) {
        List<ChannelMonetizationOwnerReminderDigestSubscriptionEntity> subscriptions =
                channelMonetizationOwnerReminderDigestSubscriptionRepository
                        .lockDueBatch(dueBefore, Math.max(1, batchSize));
        if (subscriptions.isEmpty()) {
            return 0;
        }

        int processed = 0;
        for (ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription : subscriptions) {
            if (!isOwnerReminderDigestSubscriptionDue(subscription, dueBefore)) {
                continue;
            }
            executeOwnerReminderDigestSubscription(subscription, dueBefore, "SCHEDULED", null);
            processed++;
        }
        return processed;
    }

    @Transactional
    public int processAlertReminders(Instant dueBefore, int batchSize) {
        List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts = channelMonetizationArtifactSubscriptionAlertRepository
                .lockDueReminderBatch(dueBefore, Math.max(1, batchSize));
        int processed = 0;
        for (ChannelMonetizationArtifactSubscriptionAlertEntity alert : alerts) {
            boolean escalationChanged = refreshAlertEscalation(alert, dueBefore, null);
            if (!isAlertDueForReminder(alert, dueBefore)) {
                if (escalationChanged) {
                    processed++;
                }
                continue;
            }
            sendAlertReminder(alert, null, false);
            processed++;
        }
        return processed;
    }

    @Transactional
    public int processAlertTriage(Instant dueBefore, int batchSize) {
        List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts = channelMonetizationArtifactSubscriptionAlertRepository
                .lockPendingTriageBatch(dueBefore, Math.max(1, batchSize));
        int processed = 0;
        for (ChannelMonetizationArtifactSubscriptionAlertEntity alert : alerts) {
            refreshAlertEscalation(alert, dueBefore, null);
            if (!isAlertEligibleForTriage(alert, dueBefore) || !isAlertDueForTriage(alert, dueBefore)) {
                continue;
            }
            sendAlertTriage(alert, null, false);
            processed++;
        }
        return processed;
    }

    @Transactional
    public int processTriageReminders(Instant dueBefore, int batchSize) {
        List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts = channelMonetizationArtifactSubscriptionAlertRepository
                .lockDueTriageReminderBatch(dueBefore, Math.max(1, batchSize));
        int processed = 0;
        for (ChannelMonetizationArtifactSubscriptionAlertEntity alert : alerts) {
            refreshAlertEscalation(alert, dueBefore, null);
            if (!isAlertEligibleForTriageFollowUp(alert, dueBefore)) {
                continue;
            }
            if (isAlertDueForTriageEscalation(alert, dueBefore)) {
                EffectiveAlertPolicy alertPolicy = effectiveAlertPolicy(alert.getChannelChatId());
                sendAlertTriageEscalation(alert, null);
                autoAssignTriageFallbackOwnerIfNeeded(alert, alertPolicy, null);
                processed++;
                continue;
            }
            if (isAlertDueForTriageReminder(alert, dueBefore)) {
                sendAlertTriageReminder(alert, null, false);
                processed++;
            }
        }
        return processed;
    }

    @Transactional(readOnly = true)
    public ChannelMonetizationStatsResponse getChannelStats(UUID requesterId, UUID chatId) {
        ensureCanManageMonetization(requesterId, chatId);
        List<SponsoredMessageEntity> sponsoredMessages = sponsoredMessageRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId);
        if (sponsoredMessages.isEmpty()) {
            return new ChannelMonetizationStatsResponse(chatId, 0, 0, 0, 0, 0, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0, 0, 0.0);
        }

        List<UUID> sponsoredMessageIds = sponsoredMessages.stream().map(SponsoredMessageEntity::getId).toList();
        List<SponsoredMessageEventEntity> events = sponsoredMessageEventRepository.findAllBySponsoredMessageIdIn(sponsoredMessageIds);
        long impressions = events.stream().filter(event -> "IMPRESSION".equals(event.getEventType())).count();
        long clicks = events.stream().filter(event -> "CLICK".equals(event.getEventType())).count();
        long totalBudget = sponsoredMessages.stream().mapToLong(message -> message.getBudgetUnits() != null ? message.getBudgetUnits() : 0L).sum();
        long totalSpent = sponsoredMessages.stream().mapToLong(message -> message.getSpentUnits() != null ? message.getSpentUnits() : 0L).sum();
        long totalEarned = sponsoredMessages.stream().mapToLong(message -> message.getEarnedUnits() != null ? message.getEarnedUnits() : 0L).sum();
        long totalSettled = sponsoredMessages.stream().mapToLong(message -> message.getSettledUnits() != null ? message.getSettledUnits() : 0L).sum();
        long outstandingPayout = sponsoredMessages.stream().mapToLong(this::outstandingPayoutUnits).sum();
        long totalPayoutUnits = channelMonetizationPayoutRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId).stream()
                .mapToLong(payout -> payout.getTotalUnits() != null ? payout.getTotalUnits() : 0L)
                .sum();
        int totalPayouts = (int) channelMonetizationPayoutRepository.countByChannelChatId(chatId);
        int uniqueSponsorCount = (int) sponsoredMessages.stream().map(SponsoredMessageEntity::getSponsorUserId).distinct().count();

        return new ChannelMonetizationStatsResponse(
                chatId,
                sponsoredMessages.size(),
                countByStatus(sponsoredMessages, "DRAFT"),
                countByStatus(sponsoredMessages, "ACTIVE"),
                countByStatus(sponsoredMessages, "PAUSED"),
                countByStatus(sponsoredMessages, "COMPLETED"),
                countByStatus(sponsoredMessages, "CANCELED"),
                (int) sponsoredMessages.stream().filter(message -> message.getPublishedAt() != null).count(),
                totalBudget,
                totalSpent,
                totalEarned,
                totalSettled,
                outstandingPayout,
                Math.max(0L, totalBudget - totalSpent),
                impressions,
                clicks,
                totalPayoutUnits,
                uniqueSponsorCount,
                totalPayouts,
                impressions == 0 ? 0.0 : (clicks * 100.0d) / impressions
        );
    }

    private SponsoredMessageResponse recordEvent(UUID viewerId, UUID chatId, UUID sponsoredMessageId, String eventType) {
        SponsoredMessageEntity sponsoredMessage = sponsoredMessageRepository.findById(sponsoredMessageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sponsored message not found"));
        if (!chatId.equals(sponsoredMessage.getChannelChatId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sponsored message not found");
        }
        if (sponsoredMessage.getPublishedAt() == null || !"ACTIVE".equals(sponsoredMessage.getStatus())) {
            return toResponse(sponsoredMessage);
        }
        if (sponsoredMessage.getActiveUntil() != null && !sponsoredMessage.getActiveUntil().isAfter(Instant.now())) {
            return toResponse(completeSponsoredMessage(sponsoredMessage));
        }
        if (hasSeenEvent(sponsoredMessageId, viewerId, eventType)) {
            return toResponse(sponsoredMessage);
        }

        long eventCost = "CLICK".equals(eventType)
                ? sponsoredMessage.getCostPerClickUnits()
                : sponsoredMessage.getCostPerImpressionUnits();
        long spent = sponsoredMessage.getSpentUnits() != null ? sponsoredMessage.getSpentUnits() : 0L;
        if (spent + eventCost > sponsoredMessage.getBudgetUnits()) {
            return toResponse(completeSponsoredMessage(sponsoredMessage));
        }

        UUID revenueRecipientId = resolveRevenueRecipientUserId(sponsoredMessage.getChannelChatId());
        if (revenueRecipientId != null
                && !revenueRecipientId.equals(sponsoredMessage.getSponsorUserId())
                && !paymentService.hasAvailableBalance(sponsoredMessage.getSponsorUserId(), eventCost)) {
            return toResponse(completeSponsoredMessage(sponsoredMessage));
        }

        SponsoredMessageEventEntity event = new SponsoredMessageEventEntity();
        event.setSponsoredMessageId(sponsoredMessageId);
        event.setViewerUserId(viewerId);
        event.setEventType(eventType);
        event.setCostUnits(eventCost);
        sponsoredMessageEventRepository.save(event);

        sponsoredMessage.setSpentUnits(spent + eventCost);
        if (revenueRecipientId != null && !revenueRecipientId.equals(sponsoredMessage.getSponsorUserId())) {
            paymentService.transferSponsoredRevenue(
                    sponsoredMessage.getSponsorUserId(),
                    revenueRecipientId,
                    sponsoredMessage.getId(),
                    eventCost,
                    "Sponsored %s on channel %s".formatted(eventType.toLowerCase(java.util.Locale.ROOT), sponsoredMessage.getChannelChatId()),
                    "Monetization revenue from sponsored %s".formatted(eventType.toLowerCase(java.util.Locale.ROOT))
            );
            sponsoredMessage.setEarnedUnits((sponsoredMessage.getEarnedUnits() != null ? sponsoredMessage.getEarnedUnits() : 0L) + eventCost);
        }
        if (sponsoredMessage.getSpentUnits() >= sponsoredMessage.getBudgetUnits()) {
            sponsoredMessage.setStatus("COMPLETED");
            sponsoredMessage.setCompletedAt(Instant.now());
        }
        return toResponse(sponsoredMessageRepository.save(sponsoredMessage));
    }

    private SponsoredMessageEntity getManagedSponsoredMessage(UUID chatId, UUID sponsoredMessageId) {
        SponsoredMessageEntity sponsoredMessage = sponsoredMessageRepository.findById(sponsoredMessageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sponsored message not found"));
        if (!chatId.equals(sponsoredMessage.getChannelChatId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sponsored message not found");
        }
        return sponsoredMessage;
    }

    private void ensureCanManageMonetization(UUID requesterId, UUID chatId) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        if (!"CHANNEL".equals(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Monetization is available only for channels");
        }
        if (!chatService.hasMessageModerationPermission(requesterId, chatId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Monetization management is not allowed for this member");
        }
    }

    private SponsoredMessageResponse toResponse(SponsoredMessageEntity sponsoredMessage) {
        long impressionsCount = sponsoredMessageEventRepository.countBySponsoredMessageIdAndEventType(
                sponsoredMessage.getId(),
                "IMPRESSION"
        );
        long clicksCount = sponsoredMessageEventRepository.countBySponsoredMessageIdAndEventType(
                sponsoredMessage.getId(),
                "CLICK"
        );
        long spentUnits = sponsoredMessage.getSpentUnits() != null ? sponsoredMessage.getSpentUnits() : 0L;
        long earnedUnits = sponsoredMessage.getEarnedUnits() != null ? sponsoredMessage.getEarnedUnits() : 0L;
        long settledUnits = sponsoredMessage.getSettledUnits() != null ? sponsoredMessage.getSettledUnits() : 0L;
        long budgetUnits = sponsoredMessage.getBudgetUnits() != null ? sponsoredMessage.getBudgetUnits() : 0L;
        return new SponsoredMessageResponse(
                sponsoredMessage.getId(),
                sponsoredMessage.getChannelChatId(),
                sponsoredMessage.getSponsorUserId(),
                sponsoredMessage.getCreatedByUserId(),
                sponsoredMessage.getTitle(),
                sponsoredMessage.getMessageText(),
                sponsoredMessage.getCallToActionLabel(),
                sponsoredMessage.getCallToActionUrl(),
                budgetUnits,
                spentUnits,
                earnedUnits,
                settledUnits,
                Math.max(0L, earnedUnits - settledUnits),
                Math.max(0L, budgetUnits - spentUnits),
                sponsoredMessage.getCostPerImpressionUnits() != null ? sponsoredMessage.getCostPerImpressionUnits() : 0L,
                sponsoredMessage.getCostPerClickUnits() != null ? sponsoredMessage.getCostPerClickUnits() : 0L,
                impressionsCount,
                clicksCount,
                sponsoredMessage.getStatus(),
                sponsoredMessage.getDeliveredMessageId(),
                sponsoredMessage.getCreatedAt(),
                sponsoredMessage.getUpdatedAt(),
                sponsoredMessage.getPublishedAt(),
                sponsoredMessage.getActiveUntil(),
                sponsoredMessage.getCompletedAt(),
                sponsoredMessage.getCanceledAt()
        );
    }

    private MonetizationWithdrawalResponse reconcileProcessingWithdrawal(ChannelMonetizationWithdrawalEntity withdrawal) {
        if (!"PROCESSING".equals(withdrawal.getStatus())) {
            return toWithdrawalResponse(withdrawal);
        }
        withdrawal.setFailureReason(null);
        withdrawal.setProviderUpdatedAt(Instant.now());
        if (!paymentService.hasAvailableBalance(withdrawal.getRecipientUserId(), withdrawal.getAmountUnits())) {
            withdrawal.setStatus("FAILED");
            withdrawal.setProviderStatus("FAILED");
            withdrawal.setFailureReason("Recipient wallet does not have enough balance to withdraw");
            return toWithdrawalResponse(channelMonetizationWithdrawalRepository.save(withdrawal));
        }

        paymentService.withdrawToExternal(
                withdrawal.getRecipientUserId(),
                withdrawal.getAmountUnits(),
                "Monetization withdrawal for channel %s to %s".formatted(
                        withdrawal.getChannelChatId(),
                        withdrawal.getDestinationLabel()
                )
        );
        withdrawal.setStatus("COMPLETED");
        if (withdrawal.getProviderReference() == null || withdrawal.getProviderReference().isBlank()) {
            withdrawal.setProviderReference("wdr_%s".formatted(withdrawal.getId().toString().replace("-", "").substring(0, 12)));
        }
        withdrawal.setProviderStatus("COMPLETED");
        withdrawal.setCompletedAt(Instant.now());
        return toWithdrawalResponse(channelMonetizationWithdrawalRepository.save(withdrawal));
    }

    private ChannelMonetizationWithdrawalEntity startWithdrawalProcessing(ChannelMonetizationWithdrawalEntity withdrawal) {
        if (!"PENDING".equals(withdrawal.getStatus())) {
            return withdrawal;
        }
        withdrawal.setStatus("PROCESSING");
        withdrawal.setProcessingAt(Instant.now());
        withdrawal.setProviderStatus("PROCESSING");
        withdrawal.setProviderUpdatedAt(withdrawal.getProcessingAt());
        if (withdrawal.getProviderReference() == null || withdrawal.getProviderReference().isBlank()) {
            withdrawal.setProviderReference("wdr_%s".formatted(withdrawal.getId().toString().replace("-", "").substring(0, 12)));
        }
        return channelMonetizationWithdrawalRepository.save(withdrawal);
    }

    private MonetizationWithdrawalResponse toWithdrawalResponse(ChannelMonetizationWithdrawalEntity withdrawal) {
        return new MonetizationWithdrawalResponse(
                withdrawal.getId(),
                withdrawal.getChannelChatId(),
                withdrawal.getRecipientUserId(),
                withdrawal.getRequestedByUserId(),
                withdrawal.getAmountUnits() != null ? withdrawal.getAmountUnits() : 0L,
                withdrawal.getCurrencyCode(),
                withdrawal.getDestinationType(),
                withdrawal.getDestinationLabel(),
                withdrawal.getNote(),
                withdrawal.getStatus(),
                withdrawal.getProviderReference(),
                withdrawal.getProviderStatus(),
                withdrawal.getFailureReason(),
                withdrawal.getRequestedAt(),
                withdrawal.getProcessingAt(),
                withdrawal.getProviderUpdatedAt(),
                withdrawal.getCompletedAt(),
                withdrawal.getCanceledAt()
        );
    }

    private MonetizationWithdrawalProviderCallbackResponse toWithdrawalCallbackResponse(
            ChannelMonetizationWithdrawalCallbackEntity callback
    ) {
        return new MonetizationWithdrawalProviderCallbackResponse(
                callback.getId(),
                callback.getWithdrawalId(),
                callback.getChannelChatId(),
                callback.getProviderReference(),
                callback.getCallbackType(),
                callback.getProviderStatus(),
                callback.getFailureReason(),
                callback.isApplied(),
                callback.getAppliedWithdrawalStatus(),
                callback.getResultMessage(),
                callback.getReceivedAt(),
                callback.getProcessedAt(),
                callback.getPayloadJson()
        );
    }

    private MonetizationWithdrawalProviderCallbackResponse toWithdrawalCallbackResponse(
            ChannelMonetizationWithdrawalCallbackEntity callback,
            ChannelMonetizationWithdrawalEntity withdrawal
    ) {
        callback.setAppliedWithdrawalStatus(withdrawal.getStatus());
        return toWithdrawalCallbackResponse(callback);
    }

    private MonetizationExportArtifactResponse toArtifactResponse(
            ChannelMonetizationExportArtifactEntity artifact,
            boolean includeContent
    ) {
        return new MonetizationExportArtifactResponse(
                artifact.getId(),
                artifact.getChannelChatId(),
                artifact.getGeneratedByUserId(),
                artifact.getArtifactType(),
                artifact.getFormat(),
                artifact.getFileName(),
                artifact.getRowCount() != null ? artifact.getRowCount() : 0,
                artifact.getTotalUnits() != null ? artifact.getTotalUnits() : 0L,
                artifact.getChecksum(),
                artifact.getCreatedAt(),
                includeContent ? artifact.getContent() : null
        );
    }

    private MonetizationProviderSyncRunResponse toProviderSyncRunResponse(ChannelMonetizationProviderSyncRunEntity run) {
        return new MonetizationProviderSyncRunResponse(
                run.getId(),
                run.getChannelChatId(),
                run.getTriggeredByUserId(),
                run.getTriggerMode(),
                run.getPayloadSize() != null ? run.getPayloadSize() : 0,
                run.getAppliedCount() != null ? run.getAppliedCount() : 0,
                run.getIgnoredCount() != null ? run.getIgnoredCount() : 0,
                run.getFailedCount() != null ? run.getFailedCount() : 0,
                run.getArtifactId(),
                run.getCreatedAt()
        );
    }

    private MonetizationArtifactPublicationResponse toArtifactPublicationResponse(
            ChannelMonetizationArtifactPublicationEntity publication
    ) {
        return new MonetizationArtifactPublicationResponse(
                publication.getId(),
                publication.getArtifactId(),
                publication.getChannelChatId(),
                publication.getTargetChatId(),
                publication.getPublishedByUserId(),
                publication.getDeliveryMode(),
                publication.getNote(),
                publication.getPublishedMessageId(),
                publication.getPublishedAt()
        );
    }

    private MonetizationArtifactSubscriptionResponse toArtifactSubscriptionResponse(
            ChannelMonetizationArtifactSubscriptionEntity subscription
    ) {
        return new MonetizationArtifactSubscriptionResponse(
                subscription.getId(),
                subscription.getChannelChatId(),
                subscription.getTargetChatId(),
                subscription.getCreatedByUserId(),
                subscription.getArtifactType(),
                subscription.getDeliveryMode(),
                subscription.getNote(),
                subscription.getStatus(),
                subscription.getMinIntervalMinutes() != null ? subscription.getMinIntervalMinutes() : 0,
                subscription.isAutoGenerate(),
                subscription.getLastDeliveredArtifactId(),
                subscription.getLastDeliveredAt(),
                subscription.getLastGeneratedAt(),
                subscription.getConsecutiveFailureCount() != null ? subscription.getConsecutiveFailureCount() : 0,
                subscription.getLastFailureAt(),
                subscription.getLastFailureReason(),
                subscription.getEscalationStatus(),
                subscription.getAlertSuppressionMinutes() != null ? subscription.getAlertSuppressionMinutes() : 0,
                subscription.getLastAlertedAt(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }

    private MonetizationOwnerReminderDigestSubscriptionResponse toOwnerReminderDigestSubscriptionResponse(
            ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription
    ) {
        return new MonetizationOwnerReminderDigestSubscriptionResponse(
                subscription.getId(),
                subscription.getChannelChatId(),
                subscription.getOwnerUserId(),
                subscription.getTargetChatId(),
                subscription.getCreatedByUserId(),
                subscription.getSeverity(),
                subscription.isBreachedOnly(),
                subscription.getNote(),
                subscription.getStatus(),
                subscription.getMinIntervalMinutes() != null ? subscription.getMinIntervalMinutes() : 0,
                subscription.getLastDeliveredArtifactId(),
                subscription.getLastDeliveredAt(),
                subscription.getLastProcessedAt(),
                subscription.getConsecutiveFailureCount() != null ? subscription.getConsecutiveFailureCount() : 0,
                subscription.getFailureState(),
                subscription.getLastFailureAt(),
                subscription.getLastFailureReason(),
                subscription.getNextRetryAt(),
                subscription.getAutoPausedAt(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }

    private MonetizationOwnerReminderDigestRunResponse toOwnerReminderDigestRunResponse(
            ChannelMonetizationOwnerReminderDigestRunEntity run
    ) {
        return new MonetizationOwnerReminderDigestRunResponse(
                run.getId(),
                run.getSubscriptionId(),
                run.getChannelChatId(),
                run.getOwnerUserId(),
                run.getProcessedByUserId(),
                run.getTriggerMode(),
                run.getStatus(),
                run.getTargetChatId(),
                run.getSeverity(),
                run.isBreachedOnly(),
                run.getDueAlertCount() != null ? run.getDueAlertCount() : 0,
                run.getBreachedDueAlertCount() != null ? run.getBreachedDueAlertCount() : 0,
                run.getArtifactId(),
                run.getPublicationId(),
                run.getPublishedMessageId(),
                run.getFailureReason(),
                run.getProcessedAt()
        );
    }

    private MonetizationOwnerReminderDigestIssueOwnerResponse toOwnerReminderDigestIssueOwnerResponse(
            UUID ownerUserId,
            List<ChannelMonetizationOwnerReminderDigestSubscriptionEntity> subscriptions,
            Instant now
    ) {
        Instant latestFailureAt = subscriptions.stream()
                .map(ChannelMonetizationOwnerReminderDigestSubscriptionEntity::getLastFailureAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new MonetizationOwnerReminderDigestIssueOwnerResponse(
                ownerUserId,
                resolveOwnerDisplayName(ownerUserId),
                subscriptions.size(),
                countOwnerReminderDigestSubscriptionsByFailureState(subscriptions, "BACKOFF"),
                countOwnerReminderDigestSubscriptionsByFailureState(subscriptions, "AUTO_PAUSED"),
                countDueRetryOwnerReminderDigestSubscriptions(subscriptions, now),
                latestFailureAt
        );
    }

    private MonetizationArtifactSubscriptionFailureResponse toArtifactSubscriptionFailureResponse(
            ChannelMonetizationArtifactSubscriptionFailureEntity failure
    ) {
        return new MonetizationArtifactSubscriptionFailureResponse(
                failure.getId(),
                failure.getSubscriptionId(),
                failure.getChannelChatId(),
                failure.getTargetChatId(),
                failure.getArtifactType(),
                failure.getAttemptNumber() != null ? failure.getAttemptNumber() : 0,
                failure.getFailureReason(),
                failure.isAlertCreated(),
                failure.getFailedAt()
        );
    }

    private MonetizationArtifactAlertCommentResponse toArtifactAlertCommentResponse(
            ChannelMonetizationArtifactAlertCommentEntity comment
    ) {
        return new MonetizationArtifactAlertCommentResponse(
                comment.getId(),
                comment.getAlertId(),
                comment.getSubscriptionId(),
                comment.getChannelChatId(),
                comment.getAuthorUserId(),
                comment.getBody(),
                comment.getCreatedAt()
        );
    }

    private MonetizationArtifactAlertAuditEventResponse toArtifactAlertAuditEventResponse(
            ChannelMonetizationArtifactAlertAuditEventEntity auditEvent
    ) {
        return new MonetizationArtifactAlertAuditEventResponse(
                auditEvent.getId(),
                auditEvent.getAlertId(),
                auditEvent.getSubscriptionId(),
                auditEvent.getChannelChatId(),
                auditEvent.getActionType(),
                auditEvent.getActorUserId(),
                auditEvent.getOwnerUserId(),
                auditEvent.getFromStatus(),
                auditEvent.getToStatus(),
                auditEvent.getNote(),
                auditEvent.getCreatedAt()
        );
    }

    private MonetizationArtifactAlertReminderResponse toArtifactAlertReminderResponse(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            String reminderType,
            Instant remindedAt
    ) {
        return new MonetizationArtifactAlertReminderResponse(
                alert.getId(),
                alert.getSubscriptionId(),
                alert.getChannelChatId(),
                alert.getTargetChatId(),
                alert.getLastReminderTargetChatId(),
                reminderType,
                alert.getLastReminderMessageId(),
                remindedAt,
                alert.getReminderCount() != null ? alert.getReminderCount() : 0
        );
    }

    private MonetizationArtifactAlertTriageResponse toArtifactAlertTriageResponse(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            boolean manual
    ) {
        return new MonetizationArtifactAlertTriageResponse(
                alert.getId(),
                alert.getSubscriptionId(),
                alert.getChannelChatId(),
                alert.getTriageTargetChatId(),
                alert.getTriageMessageId(),
                alert.getTriagedAt(),
                manual
        );
    }

    private MonetizationArtifactAlertTriageReminderResponse toArtifactAlertTriageReminderResponse(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            UUID routedTargetChatId,
            boolean manual
    ) {
        return new MonetizationArtifactAlertTriageReminderResponse(
                alert.getId(),
                alert.getSubscriptionId(),
                alert.getChannelChatId(),
                alert.getTargetChatId(),
                routedTargetChatId,
                alert.getLastTriageReminderMessageId(),
                alert.getLastTriageReminderAt(),
                alert.getTriageReminderCount() != null ? alert.getTriageReminderCount() : 0,
                manual
        );
    }

    private MonetizationArtifactSubscriptionAlertResponse toArtifactSubscriptionAlertResponse(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert
    ) {
        return new MonetizationArtifactSubscriptionAlertResponse(
                alert.getId(),
                alert.getSubscriptionId(),
                alert.getChannelChatId(),
                alert.getTargetChatId(),
                alert.getSeverity(),
                alert.getFailureCount() != null ? alert.getFailureCount() : 0,
                alert.getLastFailureReason(),
                alert.getStatus(),
                alert.getPublishedMessageId(),
                alert.getAcknowledgedByUserId(),
                alert.getAcknowledgedAt(),
                alert.getSnoozedUntil(),
                alert.getOwnerUserId(),
                alert.getAssignedAt(),
                alert.getAcknowledgeByDueAt(),
                alert.getResolveByDueAt(),
                alert.getLastReminderAt(),
                alert.getReminderCount() != null ? alert.getReminderCount() : 0,
                alert.getLastReminderMessageId(),
                alert.getLastReminderTargetChatId(),
                alert.getSeverityEscalatedAt(),
                alert.getBreachedAt(),
                alert.getBreachMessageId(),
                alert.getTriagedAt(),
                alert.getTriageMessageId(),
                alert.getTriageTargetChatId(),
                alert.getLastTriageReminderAt(),
                alert.getTriageReminderCount() != null ? alert.getTriageReminderCount() : 0,
                alert.getLastTriageReminderMessageId(),
                alert.getLastTriageReminderTargetChatId(),
                alert.getTriageEscalatedAt(),
                alert.getTriageEscalationMessageId(),
                alert.getTriageEscalationTargetChatId(),
                alert.getCreatedAt(),
                alert.getResolvedAt()
        );
    }

    private MonetizationAlertDigestRunResponse toAlertDigestRunResponse(ChannelMonetizationAlertDigestRunEntity run) {
        return new MonetizationAlertDigestRunResponse(
                run.getId(),
                run.getChannelChatId(),
                run.getGeneratedByUserId(),
                run.getTriggerMode(),
                run.getOpenAlertCount() != null ? run.getOpenAlertCount() : 0,
                run.getAffectedSubscriptionCount() != null ? run.getAffectedSubscriptionCount() : 0,
                run.getArtifactId(),
                run.getPublishedMessageId(),
                run.getCreatedAt()
        );
    }

    private MonetizationReconciliationRunResponse toReconciliationRunResponse(ChannelMonetizationReconciliationRunEntity run) {
        return new MonetizationReconciliationRunResponse(
                run.getId(),
                run.getChannelChatId(),
                run.getTriggeredByUserId(),
                run.getTriggerMode(),
                run.getProcessedCount() != null ? run.getProcessedCount() : 0,
                run.getPendingCount() != null ? run.getPendingCount() : 0,
                run.getProcessingCount() != null ? run.getProcessingCount() : 0,
                run.getCompletedCount() != null ? run.getCompletedCount() : 0,
                run.getFailedCount() != null ? run.getFailedCount() : 0,
                run.getCreatedAt()
        );
    }

    private List<MonetizationPayoutResponse> toPayoutResponses(List<ChannelMonetizationPayoutEntity> payouts) {
        if (payouts.isEmpty()) {
            return List.of();
        }
        List<UUID> payoutIds = payouts.stream().map(ChannelMonetizationPayoutEntity::getId).toList();
        Map<UUID, List<MonetizationPayoutItemResponse>> itemsByPayoutId = channelMonetizationPayoutItemRepository
                .findAllByPayoutIdInOrderByCreatedAtAsc(payoutIds)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ChannelMonetizationPayoutItemEntity::getPayoutId,
                        java.util.stream.Collectors.mapping(
                                item -> new MonetizationPayoutItemResponse(
                                        item.getSponsoredMessageId(),
                                        item.getSettledUnits() != null ? item.getSettledUnits() : 0L,
                                        item.getCreatedAt()
                                ),
                                java.util.stream.Collectors.toList()
                        )
                ));
        return payouts.stream()
                .map(payout -> new MonetizationPayoutResponse(
                        payout.getId(),
                        payout.getChannelChatId(),
                        payout.getRecipientUserId(),
                        payout.getTriggeredByUserId(),
                        payout.getTriggerMode(),
                        payout.getStatus(),
                        payout.getTotalUnits() != null ? payout.getTotalUnits() : 0L,
                        payout.getSponsoredMessageCount() != null ? payout.getSponsoredMessageCount() : 0,
                        payout.getPeriodStartedAt(),
                        payout.getPeriodEndedAt(),
                        payout.getCreatedAt(),
                        payout.getCompletedAt(),
                        itemsByPayoutId.getOrDefault(payout.getId(), List.of())
                ))
                .toList();
    }

    private MonetizationPayoutResponse createPayoutRecord(
            List<SponsoredMessageEntity> readyMessages,
            String triggerMode,
            UUID triggeredByUserId
    ) {
        List<SponsoredMessageEntity> payableMessages = readyMessages.stream()
                .filter(message -> outstandingPayoutUnits(message) > 0)
                .toList();
        if (payableMessages.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No monetization payout is ready for this channel");
        }

        UUID channelChatId = payableMessages.get(0).getChannelChatId();
        UUID recipientUserId = resolveRevenueRecipientUserId(channelChatId);
        if (recipientUserId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Channel payout recipient is not configured");
        }

        ChannelMonetizationPayoutEntity payout = new ChannelMonetizationPayoutEntity();
        payout.setChannelChatId(channelChatId);
        payout.setRecipientUserId(recipientUserId);
        payout.setTriggeredByUserId(triggeredByUserId);
        payout.setTriggerMode(triggerMode);
        payout.setStatus("COMPLETED");
        payout.setTotalUnits(payableMessages.stream().mapToLong(this::outstandingPayoutUnits).sum());
        payout.setSponsoredMessageCount(payableMessages.size());
        payout.setPeriodStartedAt(payableMessages.stream()
                .map(this::payoutStartAt)
                .filter(java.util.Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null));
        payout.setPeriodEndedAt(payableMessages.stream()
                .map(this::payoutEndAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null));
        payout.setCompletedAt(Instant.now());
        ChannelMonetizationPayoutEntity savedPayout = channelMonetizationPayoutRepository.save(payout);

        List<ChannelMonetizationPayoutItemEntity> payoutItems = payableMessages.stream()
                .map(message -> {
                    ChannelMonetizationPayoutItemEntity item = new ChannelMonetizationPayoutItemEntity();
                    item.setPayoutId(savedPayout.getId());
                    item.setSponsoredMessageId(message.getId());
                    item.setSettledUnits(outstandingPayoutUnits(message));
                    return item;
                })
                .toList();
        List<ChannelMonetizationPayoutItemEntity> savedItems = channelMonetizationPayoutItemRepository.saveAll(payoutItems);

        payableMessages.forEach(message -> message.setSettledUnits(message.getEarnedUnits() != null ? message.getEarnedUnits() : 0L));
        sponsoredMessageRepository.saveAll(payableMessages);

        return new MonetizationPayoutResponse(
                savedPayout.getId(),
                savedPayout.getChannelChatId(),
                savedPayout.getRecipientUserId(),
                savedPayout.getTriggeredByUserId(),
                savedPayout.getTriggerMode(),
                savedPayout.getStatus(),
                savedPayout.getTotalUnits() != null ? savedPayout.getTotalUnits() : 0L,
                savedPayout.getSponsoredMessageCount() != null ? savedPayout.getSponsoredMessageCount() : 0,
                savedPayout.getPeriodStartedAt(),
                savedPayout.getPeriodEndedAt(),
                savedPayout.getCreatedAt(),
                savedPayout.getCompletedAt(),
                savedItems.stream()
                        .map(item -> new MonetizationPayoutItemResponse(
                                item.getSponsoredMessageId(),
                                item.getSettledUnits() != null ? item.getSettledUnits() : 0L,
                                item.getCreatedAt()
                        ))
                        .toList()
        );
    }

    private long outstandingPayoutUnits(SponsoredMessageEntity sponsoredMessage) {
        long earnedUnits = sponsoredMessage.getEarnedUnits() != null ? sponsoredMessage.getEarnedUnits() : 0L;
        long settledUnits = sponsoredMessage.getSettledUnits() != null ? sponsoredMessage.getSettledUnits() : 0L;
        return Math.max(0L, earnedUnits - settledUnits);
    }

    private long getAvailableWithdrawalUnits(UUID chatId) {
        long settledUnits = channelMonetizationPayoutRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId).stream()
                .mapToLong(payout -> payout.getTotalUnits() != null ? payout.getTotalUnits() : 0L)
                .sum();
        long reservedOrWithdrawnUnits = channelMonetizationWithdrawalRepository.findAllByChannelChatIdOrderByRequestedAtDesc(chatId).stream()
                .filter(withdrawal -> List.of("PENDING", "PROCESSING", "COMPLETED").contains(withdrawal.getStatus()))
                .mapToLong(withdrawal -> withdrawal.getAmountUnits() != null ? withdrawal.getAmountUnits() : 0L)
                .sum();
        return Math.max(0L, settledUnits - reservedOrWithdrawnUnits);
    }

    private Instant payoutStartAt(SponsoredMessageEntity sponsoredMessage) {
        if (sponsoredMessage.getPublishedAt() != null) {
            return sponsoredMessage.getPublishedAt();
        }
        return sponsoredMessage.getCreatedAt();
    }

    private Instant payoutEndAt(SponsoredMessageEntity sponsoredMessage) {
        if (sponsoredMessage.getCompletedAt() != null) {
            return sponsoredMessage.getCompletedAt();
        }
        if (sponsoredMessage.getCanceledAt() != null) {
            return sponsoredMessage.getCanceledAt();
        }
        return sponsoredMessage.getUpdatedAt();
    }

    private ChannelMonetizationWithdrawalEntity getManagedWithdrawal(UUID chatId, UUID withdrawalId) {
        return channelMonetizationWithdrawalRepository.findByIdAndChannelChatId(withdrawalId, chatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monetization withdrawal not found"));
    }

    private MonetizationReconciliationRunResponse saveReconciliationRun(
            UUID chatId,
            UUID triggeredByUserId,
            String triggerMode,
            List<ChannelMonetizationWithdrawalEntity> withdrawals
    ) {
        ChannelMonetizationReconciliationRunEntity run = new ChannelMonetizationReconciliationRunEntity();
        run.setChannelChatId(chatId);
        run.setTriggeredByUserId(triggeredByUserId);
        run.setTriggerMode(triggerMode);
        run.setProcessedCount(withdrawals.size());
        run.setPendingCount(countWithdrawalsByStatus(withdrawals, "PENDING"));
        run.setProcessingCount(countWithdrawalsByStatus(withdrawals, "PROCESSING"));
        run.setCompletedCount(countWithdrawalsByStatus(withdrawals, "COMPLETED"));
        run.setFailedCount(countWithdrawalsByStatus(withdrawals, "FAILED"));
        return toReconciliationRunResponse(channelMonetizationReconciliationRunRepository.save(run));
    }

    private int countWithdrawalsByStatus(List<ChannelMonetizationWithdrawalEntity> withdrawals, String status) {
        return (int) withdrawals.stream().filter(withdrawal -> status.equals(withdrawal.getStatus())).count();
    }

    private boolean isDeliverable(SponsoredMessageEntity sponsoredMessage) {
        if (sponsoredMessage.getPublishedAt() == null || !"ACTIVE".equals(sponsoredMessage.getStatus())) {
            return false;
        }
        if (sponsoredMessage.getActiveUntil() != null && !sponsoredMessage.getActiveUntil().isAfter(Instant.now())) {
            return false;
        }
        long spentUnits = sponsoredMessage.getSpentUnits() != null ? sponsoredMessage.getSpentUnits() : 0L;
        long budgetUnits = sponsoredMessage.getBudgetUnits() != null ? sponsoredMessage.getBudgetUnits() : 0L;
        return spentUnits < budgetUnits;
    }

    private boolean hasSeenEvent(UUID sponsoredMessageId, UUID viewerId, String eventType) {
        return sponsoredMessageEventRepository.existsBySponsoredMessageIdAndViewerUserIdAndEventType(
                sponsoredMessageId,
                viewerId,
                eventType
        );
    }

    private SponsoredMessageEntity completeSponsoredMessage(SponsoredMessageEntity sponsoredMessage) {
        sponsoredMessage.setStatus("COMPLETED");
        if (sponsoredMessage.getCompletedAt() == null) {
            sponsoredMessage.setCompletedAt(Instant.now());
        }
        return sponsoredMessageRepository.save(sponsoredMessage);
    }

    private UUID resolveRevenueRecipientUserId(UUID chatId) {
        ChatEntity chat = chatService.getChat(chatId);
        return chat.getCreatedBy();
    }

    private void ensureCampaignCanSpend(SponsoredMessageEntity sponsoredMessage) {
        UUID revenueRecipientId = resolveRevenueRecipientUserId(sponsoredMessage.getChannelChatId());
        if (revenueRecipientId == null || revenueRecipientId.equals(sponsoredMessage.getSponsorUserId())) {
            return;
        }
        if (!paymentService.hasAvailableBalance(sponsoredMessage.getSponsorUserId(), sponsoredMessage.getCostPerImpressionUnits())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sponsor wallet must cover at least one impression");
        }
    }

    private int countByStatus(List<SponsoredMessageEntity> sponsoredMessages, String status) {
        return (int) sponsoredMessages.stream().filter(message -> status.equals(message.getStatus())).count();
    }

    private String composeSponsoredText(SponsoredMessageEntity sponsoredMessage) {
        StringBuilder builder = new StringBuilder();
        builder.append("Sponsored: ").append(sponsoredMessage.getTitle()).append("\n\n");
        builder.append(sponsoredMessage.getMessageText());
        if (sponsoredMessage.getCallToActionLabel() != null && sponsoredMessage.getCallToActionUrl() != null) {
            builder.append("\n\n");
            builder.append(sponsoredMessage.getCallToActionLabel()).append(": ").append(sponsoredMessage.getCallToActionUrl());
        }
        return builder.toString();
    }

    private MonetizationWithdrawalProviderCallbackResponse applyProviderCallbackForChannel(
            UUID chatId,
            MonetizationProviderStatusUpdateRequest update
    ) {
        MonetizationWithdrawalProviderCallbackResponse response = applyProviderCallback(new MonetizationWithdrawalProviderCallbackRequest(
                update.withdrawalId(),
                update.providerReference(),
                update.providerStatus(),
                update.callbackType(),
                update.failureReason(),
                update.payload()
        ));
        if (!chatId.equals(response.channelChatId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Provider update does not belong to the requested channel");
        }
        return response;
    }

    private ChannelMonetizationExportArtifactEntity getArtifactEntity(UUID chatId, UUID artifactId) {
        return channelMonetizationExportArtifactRepository.findByIdAndChannelChatId(artifactId, chatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monetization export artifact not found"));
    }

    private ChannelMonetizationArtifactSubscriptionEntity getSubscription(UUID chatId, UUID subscriptionId) {
        return channelMonetizationArtifactSubscriptionRepository.findByIdAndChannelChatId(subscriptionId, chatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monetization artifact subscription not found"));
    }

    private ChannelMonetizationOwnerReminderDigestSubscriptionEntity getOwnerReminderDigestSubscription(
            UUID chatId,
            UUID ownerUserId,
            UUID subscriptionId
    ) {
        requireUser(ownerUserId);
        return channelMonetizationOwnerReminderDigestSubscriptionRepository
                .findByIdAndChannelChatIdAndOwnerUserId(subscriptionId, chatId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Monetization owner reminder digest subscription not found"
                ));
    }

    private ChannelMonetizationArtifactSubscriptionAlertEntity getSubscriptionAlert(UUID subscriptionId, UUID alertId) {
        return channelMonetizationArtifactSubscriptionAlertRepository.findByIdAndSubscriptionId(alertId, subscriptionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monetization artifact subscription alert not found"));
    }

    private void recordAlertAuditEvent(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            String actionType,
            UUID actorUserId,
            UUID ownerUserId,
            String fromStatus,
            String toStatus,
            String note
    ) {
        ChannelMonetizationArtifactAlertAuditEventEntity auditEvent = new ChannelMonetizationArtifactAlertAuditEventEntity();
        auditEvent.setAlertId(alert.getId());
        auditEvent.setSubscriptionId(alert.getSubscriptionId());
        auditEvent.setChannelChatId(alert.getChannelChatId());
        auditEvent.setActionType(actionType);
        auditEvent.setActorUserId(actorUserId);
        auditEvent.setOwnerUserId(ownerUserId);
        auditEvent.setFromStatus(fromStatus);
        auditEvent.setToStatus(toStatus);
        auditEvent.setNote(normalizeOptional(note, 1000));
        channelMonetizationArtifactAlertAuditEventRepository.save(auditEvent);
    }

    private void handleArtifactSubscriptionFailure(
            ChannelMonetizationArtifactSubscriptionEntity subscription,
            String failureReason
    ) {
        EffectiveAlertPolicy alertPolicy = effectiveAlertPolicy(subscription.getChannelChatId());
        String normalizedReason = normalizeOptional(failureReason, 255);
        if (normalizedReason == null) {
            normalizedReason = "Artifact subscription delivery failed";
        }

        int attemptNumber = (subscription.getConsecutiveFailureCount() != null ? subscription.getConsecutiveFailureCount() : 0) + 1;
        subscription.setConsecutiveFailureCount(attemptNumber);
        subscription.setLastFailureAt(Instant.now());
        subscription.setLastFailureReason(normalizedReason);

        boolean alertCreated = false;
        if (attemptNumber >= alertPolicy.alertThreshold()) {
            ChannelMonetizationArtifactSubscriptionAlertEntity alert = channelMonetizationArtifactSubscriptionAlertRepository
                    .findFirstBySubscriptionIdOrderByCreatedAtDesc(subscription.getId())
                    .orElse(null);
            Instant now = Instant.now();
            String resolvedSeverity = attemptNumber >= alertPolicy.highSeverityThreshold() ? "HIGH" : "WARN";
            boolean snoozeActive = isSnoozeActive(alert, now);
            boolean shouldPublishAlert = shouldPublishSubscriptionAlert(subscription, alertPolicy);
            UUID alertTargetChatId = alertPolicy.alertTargetChatId() != null
                    ? alertPolicy.alertTargetChatId()
                    : subscription.getChannelChatId();
            String previousAlertStatus = alert != null ? alert.getStatus() : null;
            if (alert == null || "RESOLVED".equals(alert.getStatus())) {
                alert = new ChannelMonetizationArtifactSubscriptionAlertEntity();
                alert.setSubscriptionId(subscription.getId());
                alert.setChannelChatId(subscription.getChannelChatId());
                alert.setSeverity(resolvedSeverity);
                applyAlertSla(alert, now, alertPolicy);
                alertCreated = true;
            }
            alert.setTargetChatId(alertTargetChatId);
            String previousSeverity = alert.getSeverity();
            alert.setSeverity(resolvedSeverity);
            alert.setFailureCount(attemptNumber);
            alert.setLastFailureReason(normalizedReason);
            if (!alertCreated && "HIGH".equals(resolvedSeverity) && !"HIGH".equals(previousSeverity)) {
                tightenAlertSlaForSeverity(alert, now, alertPolicy);
            }
            if ("HIGH".equals(alert.getSeverity())) {
                autoAssignAlertOwnerIfNeeded(alert, alertPolicy, null, "Assigned by monetization severity policy");
            }
            if (snoozeActive) {
                alert.setStatus("SNOOZED");
                channelMonetizationArtifactSubscriptionAlertRepository.save(alert);
                recordAlertAuditEvent(
                        alert,
                        alertCreated ? "CREATED" : "REFRESHED",
                        null,
                        alert.getOwnerUserId(),
                        previousAlertStatus,
                        alert.getStatus(),
                        normalizedReason
                );
                subscription.setEscalationStatus("SNOOZED");
            } else {
                alert.setStatus("OPEN");
                alert.setAcknowledgedByUserId(null);
                alert.setAcknowledgedAt(null);
                alert.setSnoozedUntil(null);
                if (alertCreated || "RESOLVED".equals(previousAlertStatus)) {
                    applyAlertSla(alert, now, alertPolicy);
                }
                if (shouldPublishAlert) {
                    try {
                        ChatMessageResponse published = messageService.sendMessage(
                                subscription.getCreatedByUserId(),
                                new SendMessageRequest(
                                        alertTargetChatId,
                                        null,
                                        null,
                                        null,
                                        composeArtifactSubscriptionAlertText(subscription, attemptNumber, normalizedReason),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        true,
                                        null
                                )
                        );
                        alert.setPublishedMessageId(published.messageId());
                        subscription.setLastAlertedAt(now);
                    } catch (RuntimeException ignored) {
                        // Persist the alert even if chat publication fails.
                    }
                }
                channelMonetizationArtifactSubscriptionAlertRepository.save(alert);
                recordAlertAuditEvent(
                        alert,
                alertCreated ? "CREATED" : ("OPEN".equals(previousAlertStatus) ? "REFRESHED" : "REOPENED"),
                        null,
                        alert.getOwnerUserId(),
                        previousAlertStatus,
                        alert.getStatus(),
                        normalizedReason
                );
                subscription.setEscalationStatus(shouldPublishAlert ? "OPEN" : "SUPPRESSED");
            }
        }

        ChannelMonetizationArtifactSubscriptionFailureEntity failure = new ChannelMonetizationArtifactSubscriptionFailureEntity();
        failure.setSubscriptionId(subscription.getId());
        failure.setChannelChatId(subscription.getChannelChatId());
        failure.setTargetChatId(subscription.getTargetChatId());
        failure.setArtifactType(subscription.getArtifactType());
        failure.setAttemptNumber(attemptNumber);
        failure.setFailureReason(normalizedReason);
        failure.setAlertCreated(alertCreated);
        channelMonetizationArtifactSubscriptionFailureRepository.save(failure);
        channelMonetizationArtifactSubscriptionRepository.save(subscription);
    }

    private void handleOwnerReminderDigestSubscriptionFailure(
            ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription,
            String failureReason,
            Instant processedAt
    ) {
        String normalizedReason = normalizeOptional(failureReason, 255);
        if (normalizedReason == null) {
            normalizedReason = "Owner reminder digest delivery failed";
        }
        int failureCount = (subscription.getConsecutiveFailureCount() != null ? subscription.getConsecutiveFailureCount() : 0) + 1;
        subscription.setConsecutiveFailureCount(failureCount);
        subscription.setLastFailureAt(processedAt);
        subscription.setLastFailureReason(normalizedReason);
        if (failureCount >= DEFAULT_OWNER_REMINDER_DIGEST_AUTO_PAUSE_FAILURE_THRESHOLD) {
            subscription.setStatus("PAUSED");
            subscription.setFailureState("AUTO_PAUSED");
            subscription.setNextRetryAt(null);
            subscription.setAutoPausedAt(processedAt);
        } else {
            subscription.setFailureState("BACKOFF");
            subscription.setNextRetryAt(calculateOwnerReminderDigestRetryAt(subscription, processedAt, failureCount));
            subscription.setAutoPausedAt(null);
        }
        channelMonetizationOwnerReminderDigestSubscriptionRepository.save(subscription);
    }

    private void clearOwnerReminderDigestSubscriptionFailure(
            ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription
    ) {
        subscription.setConsecutiveFailureCount(0);
        subscription.setFailureState("NONE");
        subscription.setLastFailureAt(null);
        subscription.setLastFailureReason(null);
        subscription.setNextRetryAt(null);
        subscription.setAutoPausedAt(null);
    }

    private void resolveOpenSubscriptionAlert(ChannelMonetizationArtifactSubscriptionEntity subscription) {
        ChannelMonetizationArtifactSubscriptionAlertEntity alert = channelMonetizationArtifactSubscriptionAlertRepository
                .findFirstBySubscriptionIdOrderByCreatedAtDesc(subscription.getId())
                .orElse(null);
        if (alert == null || "RESOLVED".equals(alert.getStatus())) {
            return;
        }
        String previousStatus = alert.getStatus();
        alert.setStatus("RESOLVED");
        alert.setResolvedAt(Instant.now());
        channelMonetizationArtifactSubscriptionAlertRepository.save(alert);
        recordAlertAuditEvent(
                alert,
                "AUTO_RESOLVED",
                null,
                alert.getOwnerUserId(),
                previousStatus,
                alert.getStatus(),
                "Subscription delivery succeeded"
        );
    }

    private boolean shouldPublishSubscriptionAlert(
            ChannelMonetizationArtifactSubscriptionEntity subscription,
            EffectiveAlertPolicy alertPolicy
    ) {
        Instant lastAlertedAt = subscription.getLastAlertedAt();
        if (lastAlertedAt == null) {
            return true;
        }
        int suppressionMinutes = subscription.getAlertSuppressionMinutes() != null
                ? subscription.getAlertSuppressionMinutes()
                : alertPolicy.alertSuppressionMinutes();
        return !lastAlertedAt.plusSeconds(suppressionMinutes * 60L).isAfter(Instant.now());
    }

    private boolean isAlertDigestDue(ChannelMonetizationArtifactSubscriptionEntity subscription) {
        if (!List.of("OPEN", "SUPPRESSED", "SNOOZED").contains(subscription.getEscalationStatus())) {
            return false;
        }
        if ("SNOOZED".equals(subscription.getEscalationStatus())) {
            ChannelMonetizationArtifactSubscriptionAlertEntity latestAlert = channelMonetizationArtifactSubscriptionAlertRepository
                    .findFirstBySubscriptionIdOrderByCreatedAtDesc(subscription.getId())
                    .orElse(null);
            if (isSnoozeActive(latestAlert, Instant.now())) {
                return false;
            }
        }
        return shouldPublishSubscriptionAlert(subscription, effectiveAlertPolicy(subscription.getChannelChatId()));
    }

    private void requireChannelChat(UUID chatId) {
        ChatEntity chat = chatService.getChat(chatId);
        if (chat == null || !"CHANNEL".equals(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Monetization is available only for channels");
        }
    }

    private boolean isSubscriptionDue(ChannelMonetizationArtifactSubscriptionEntity subscription, Instant eligibleBefore) {
        Instant referenceTime = subscription.getLastDeliveredAt() != null
                ? subscription.getLastDeliveredAt()
                : subscription.getCreatedAt();
        if (referenceTime == null) {
            return true;
        }
        int minutes = subscription.getMinIntervalMinutes() != null ? subscription.getMinIntervalMinutes() : 60;
        return !referenceTime.plusSeconds(minutes * 60L).isAfter(eligibleBefore);
    }

    private boolean isOwnerReminderDigestSubscriptionDue(
            ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription,
            Instant eligibleBefore
    ) {
        if (!"ACTIVE".equals(subscription.getStatus())) {
            return false;
        }
        if (subscription.getNextRetryAt() != null) {
            return !subscription.getNextRetryAt().isAfter(eligibleBefore);
        }
        Instant referenceTime = subscription.getLastProcessedAt() != null
                ? subscription.getLastProcessedAt()
                : subscription.getCreatedAt();
        if (referenceTime == null) {
            return true;
        }
        int minutes = subscription.getMinIntervalMinutes() != null ? subscription.getMinIntervalMinutes() : 60;
        return !referenceTime.plusSeconds(minutes * 60L).isAfter(eligibleBefore);
    }

    private Instant calculateOwnerReminderDigestRetryAt(
            ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription,
            Instant processedAt,
            int failureCount
    ) {
        long baseMinutes = Math.max(5, subscription.getMinIntervalMinutes() != null ? subscription.getMinIntervalMinutes() : 60);
        long multiplier = 1L << Math.max(0, failureCount - 1);
        long backoffMinutes = Math.min(MAX_OWNER_REMINDER_DIGEST_BACKOFF_MINUTES, baseMinutes * multiplier);
        return processedAt.plusSeconds(backoffMinutes * 60L);
    }

    private MonetizationOwnerReminderDigestRunResponse executeOwnerReminderDigestSubscription(
            ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription,
            Instant processedAt,
            String triggerMode,
            UUID processedByUserId
    ) {
        List<ChannelMonetizationArtifactSubscriptionAlertEntity> dueAlerts = loadOwnerDueReminderAlerts(
                subscription.getChannelChatId(),
                subscription.getOwnerUserId(),
                subscription.getSeverity(),
                subscription.isBreachedOnly(),
                processedAt
        );
        subscription.setLastProcessedAt(processedAt);

        ChannelMonetizationOwnerReminderDigestRunEntity run = new ChannelMonetizationOwnerReminderDigestRunEntity();
        run.setSubscriptionId(subscription.getId());
        run.setChannelChatId(subscription.getChannelChatId());
        run.setOwnerUserId(subscription.getOwnerUserId());
        run.setProcessedByUserId(processedByUserId);
        run.setTriggerMode(triggerMode);
        run.setSeverity(subscription.getSeverity());
        run.setBreachedOnly(subscription.isBreachedOnly());
        run.setDueAlertCount(dueAlerts.size());
        run.setBreachedDueAlertCount((int) dueAlerts.stream().filter(alert -> isAlertBreached(alert, processedAt)).count());
        run.setProcessedAt(processedAt);

        if (dueAlerts.isEmpty()) {
            run.setStatus("NOOP");
            clearOwnerReminderDigestSubscriptionFailure(subscription);
            channelMonetizationOwnerReminderDigestSubscriptionRepository.save(subscription);
            return toOwnerReminderDigestRunResponse(channelMonetizationOwnerReminderDigestRunRepository.save(run));
        }

        try {
            MonetizationArtifactPublicationResponse publication = publishOwnerArtifactAlertReminderDigest(
                    subscription.getCreatedByUserId(),
                    subscription.getChannelChatId(),
                    subscription.getOwnerUserId(),
                    subscription.getSeverity(),
                    subscription.isBreachedOnly(),
                    new PublishMonetizationArtifactRequest(subscription.getTargetChatId(), subscription.getNote())
            );
            subscription.setLastDeliveredArtifactId(publication.artifactId());
            subscription.setLastDeliveredAt(publication.publishedAt());
            clearOwnerReminderDigestSubscriptionFailure(subscription);
            channelMonetizationOwnerReminderDigestSubscriptionRepository.save(subscription);

            run.setStatus("DELIVERED");
            run.setTargetChatId(publication.targetChatId());
            run.setArtifactId(publication.artifactId());
            run.setPublicationId(publication.publicationId());
            run.setPublishedMessageId(publication.publishedMessageId());
            return toOwnerReminderDigestRunResponse(channelMonetizationOwnerReminderDigestRunRepository.save(run));
        } catch (ResponseStatusException exception) {
            String normalizedReason = normalizeOptional(exception.getReason(), 255);
            handleOwnerReminderDigestSubscriptionFailure(subscription, normalizedReason, processedAt);
            run.setStatus("FAILED");
            run.setFailureReason(normalizedReason);
            return toOwnerReminderDigestRunResponse(channelMonetizationOwnerReminderDigestRunRepository.save(run));
        }
    }

    private ChannelMonetizationExportArtifactEntity resolveArtifactForSubscription(
            ChannelMonetizationArtifactSubscriptionEntity subscription
    ) {
        validateArtifactType(subscription.getArtifactType());
        if (subscription.isAutoGenerate()) {
            MonetizationExportArtifactResponse generated = switch (subscription.getArtifactType()) {
                case "REPORT_EXPORT" -> exportReport(subscription.getCreatedByUserId(), subscription.getChannelChatId());
                case "WITHDRAWALS_EXPORT" -> exportWithdrawals(subscription.getCreatedByUserId(), subscription.getChannelChatId());
                case "PAYOUTS_EXPORT" -> {
                    exportPayouts(subscription.getCreatedByUserId(), subscription.getChannelChatId());
                    yield channelMonetizationExportArtifactRepository
                            .findFirstByChannelChatIdAndArtifactTypeOrderByCreatedAtDesc(
                                    subscription.getChannelChatId(),
                                    subscription.getArtifactType()
                            )
                            .map(artifact -> toArtifactResponse(artifact, true))
                            .orElse(null);
                }
                case "ALERT_OWNER_REMINDER_DIGEST_ISSUES_EXPORT" -> exportArtifactAlertReminderDigestSubscriptionIssues(
                        subscription.getCreatedByUserId(),
                        subscription.getChannelChatId(),
                        null,
                        null,
                        false
                );
                case "ALERT_OWNER_REMINDER_DIGEST_ISSUES_SUMMARY_EXPORT" ->
                        exportArtifactAlertReminderDigestSubscriptionIssueSummary(
                                subscription.getCreatedByUserId(),
                                subscription.getChannelChatId()
                        );
                default -> null;
            };
            if (generated != null) {
                subscription.setLastGeneratedAt(Instant.now());
                channelMonetizationArtifactSubscriptionRepository.save(subscription);
                return channelMonetizationExportArtifactRepository.findById(generated.artifactId()).orElse(null);
            }
        }
        return channelMonetizationExportArtifactRepository.findFirstByChannelChatIdAndArtifactTypeOrderByCreatedAtDesc(
                subscription.getChannelChatId(),
                subscription.getArtifactType()
        ).orElse(null);
    }

    private MonetizationArtifactPublicationResponse publishArtifactInternal(
            UUID requesterId,
            UUID chatId,
            ChannelMonetizationExportArtifactEntity artifact,
            UUID targetChatId,
            String note
    ) {
        UUID senderId = requesterId != null ? requesterId : resolveRevenueRecipientUserId(chatId);
        if (senderId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Monetization artifact publisher is not configured");
        }
        ChatMessageResponse published = messageService.sendMessage(
                senderId,
                new SendMessageRequest(
                        targetChatId,
                        null,
                        null,
                        null,
                        composeArtifactPublicationText(artifact, normalizeOptional(note, 255)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        null
                )
        );

        ChannelMonetizationArtifactPublicationEntity publication = new ChannelMonetizationArtifactPublicationEntity();
        publication.setArtifactId(artifact.getId());
        publication.setChannelChatId(chatId);
        publication.setTargetChatId(targetChatId);
        publication.setPublishedByUserId(requesterId);
        publication.setDeliveryMode("CHAT_MESSAGE");
        publication.setNote(normalizeOptional(note, 255));
        publication.setPublishedMessageId(published.messageId());
        ChannelMonetizationArtifactPublicationEntity saved = channelMonetizationArtifactPublicationRepository.save(publication);
        return toArtifactPublicationResponse(saved);
    }

    private String composeArtifactPublicationText(ChannelMonetizationExportArtifactEntity artifact, String note) {
        StringBuilder builder = new StringBuilder();
        builder.append("Monetization artifact: ").append(artifact.getArtifactType()).append("\n\n");
        builder.append("File: ").append(artifact.getFileName()).append('\n');
        builder.append("Format: ").append(artifact.getFormat()).append('\n');
        builder.append("Rows: ").append(artifact.getRowCount() != null ? artifact.getRowCount() : 0).append('\n');
        builder.append("Total units: ").append(artifact.getTotalUnits() != null ? artifact.getTotalUnits() : 0L).append('\n');
        builder.append("Checksum: ").append(artifact.getChecksum());
        if (note != null) {
            builder.append("\n\nNote: ").append(note);
        }
        return builder.toString();
    }

    private String composeArtifactSubscriptionAlertText(
            ChannelMonetizationArtifactSubscriptionEntity subscription,
            int attemptNumber,
            String failureReason
    ) {
        return """
                Monetization subscription alert

                Artifact type: %s
                Target chat: %s
                Consecutive failures: %d
                Reason: %s
                """.formatted(
                subscription.getArtifactType(),
                subscription.getTargetChatId(),
                attemptNumber,
                failureReason
        ).trim();
    }

    private int normalizeAlertThreshold(Integer value) {
        int normalized = normalizeSubscriptionInterval(value, DEFAULT_ALERT_THRESHOLD);
        if (normalized > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Alert threshold must not exceed 20");
        }
        return normalized;
    }

    private int normalizeHighSeverityThreshold(Integer value, int alertThreshold) {
        int normalized = normalizeSubscriptionInterval(
                value,
                Math.max(DEFAULT_HIGH_SEVERITY_THRESHOLD, alertThreshold)
        );
        if (normalized < alertThreshold) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "High severity threshold must be greater than or equal to alert threshold"
            );
        }
        return normalized;
    }

    private int normalizeBreachEscalationAfterMinutes(Integer value, int severityUpgradeAfterMinutes) {
        int normalized = normalizeSubscriptionInterval(value, DEFAULT_BREACH_ESCALATION_AFTER_MINUTES);
        if (normalized < severityUpgradeAfterMinutes) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Breach escalation window must be greater than or equal to severity upgrade window"
            );
        }
        return normalized;
    }

    private int normalizeHighSeverityAcknowledgeSlaMinutes(Integer value, int standardAcknowledgeSlaMinutes) {
        int normalized = normalizeSubscriptionInterval(value, DEFAULT_HIGH_SEVERITY_ACKNOWLEDGE_SLA_MINUTES);
        if (normalized > standardAcknowledgeSlaMinutes) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "High severity acknowledge SLA must be less than or equal to the standard acknowledge SLA"
            );
        }
        return normalized;
    }

    private int normalizeHighSeverityResolveSlaMinutes(
            Integer value,
            int highSeverityAcknowledgeSlaMinutes,
            int standardResolveSlaMinutes
    ) {
        int normalized = normalizeSubscriptionInterval(value, DEFAULT_HIGH_SEVERITY_RESOLVE_SLA_MINUTES);
        if (normalized < highSeverityAcknowledgeSlaMinutes) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "High severity resolve SLA must be greater than or equal to the high severity acknowledge SLA"
            );
        }
        if (normalized > standardResolveSlaMinutes) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "High severity resolve SLA must be less than or equal to the standard resolve SLA"
            );
        }
        return normalized;
    }

    private int normalizeHighSeverityReminderIntervalMinutes(Integer value, int standardReminderIntervalMinutes) {
        int normalized = normalizeSubscriptionInterval(value, DEFAULT_HIGH_SEVERITY_REMINDER_INTERVAL_MINUTES);
        if (normalized > standardReminderIntervalMinutes) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "High severity reminder interval must be less than or equal to the standard reminder interval"
            );
        }
        return normalized;
    }

    private int normalizeTriageEscalationAfterMinutes(Integer value, int triageReminderIntervalMinutes) {
        int normalized = normalizeSubscriptionInterval(value, DEFAULT_TRIAGE_ESCALATION_AFTER_MINUTES);
        if (normalized < triageReminderIntervalMinutes) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Triage escalation window must be greater than or equal to the triage reminder interval"
            );
        }
        return normalized;
    }

    private java.util.Optional<ChannelMonetizationAlertPolicyEntity> resolveAlertPolicy(UUID chatId) {
        java.util.Optional<ChannelMonetizationAlertPolicyEntity> policy = channelMonetizationAlertPolicyRepository.findById(chatId);
        return policy != null ? policy : java.util.Optional.empty();
    }

    private EffectiveAlertPolicy effectiveAlertPolicy(UUID chatId) {
        return resolveAlertPolicy(chatId)
                .map(this::effectiveAlertPolicy)
                .orElseGet(() -> new EffectiveAlertPolicy(
                        DEFAULT_ALERT_THRESHOLD,
                        DEFAULT_HIGH_SEVERITY_THRESHOLD,
                        DEFAULT_ALERT_SUPPRESSION_MINUTES,
                        DEFAULT_ACKNOWLEDGE_SLA_MINUTES,
                        DEFAULT_RESOLVE_SLA_MINUTES,
                        DEFAULT_REMINDER_INTERVAL_MINUTES,
                        DEFAULT_SEVERITY_UPGRADE_AFTER_MINUTES,
                        DEFAULT_BREACH_ESCALATION_AFTER_MINUTES,
                        DEFAULT_HIGH_SEVERITY_ACKNOWLEDGE_SLA_MINUTES,
                        DEFAULT_HIGH_SEVERITY_RESOLVE_SLA_MINUTES,
                        DEFAULT_HIGH_SEVERITY_REMINDER_INTERVAL_MINUTES,
                        DEFAULT_ALERT_TRIAGE_DELAY_MINUTES,
                        DEFAULT_TRIAGE_REMINDER_INTERVAL_MINUTES,
                        DEFAULT_TRIAGE_ESCALATION_AFTER_MINUTES,
                        true,
                        false,
                        false,
                        "DEFAULT",
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
    }

    private EffectiveAlertPolicy effectiveAlertPolicy(ChannelMonetizationAlertPolicyEntity policy) {
        return new EffectiveAlertPolicy(
                policy.getAlertThreshold() != null ? policy.getAlertThreshold() : DEFAULT_ALERT_THRESHOLD,
                policy.getHighSeverityThreshold() != null
                        ? policy.getHighSeverityThreshold()
                        : DEFAULT_HIGH_SEVERITY_THRESHOLD,
                policy.getAlertSuppressionMinutes() != null
                        ? policy.getAlertSuppressionMinutes()
                        : DEFAULT_ALERT_SUPPRESSION_MINUTES,
                policy.getAcknowledgeSlaMinutes() != null
                        ? policy.getAcknowledgeSlaMinutes()
                        : DEFAULT_ACKNOWLEDGE_SLA_MINUTES,
                policy.getResolveSlaMinutes() != null
                        ? policy.getResolveSlaMinutes()
                        : DEFAULT_RESOLVE_SLA_MINUTES,
                policy.getReminderIntervalMinutes() != null
                        ? policy.getReminderIntervalMinutes()
                        : DEFAULT_REMINDER_INTERVAL_MINUTES,
                policy.getSeverityUpgradeAfterMinutes() != null
                        ? policy.getSeverityUpgradeAfterMinutes()
                        : DEFAULT_SEVERITY_UPGRADE_AFTER_MINUTES,
                policy.getBreachEscalationAfterMinutes() != null
                        ? policy.getBreachEscalationAfterMinutes()
                        : DEFAULT_BREACH_ESCALATION_AFTER_MINUTES,
                policy.getHighSeverityAcknowledgeSlaMinutes() != null
                        ? policy.getHighSeverityAcknowledgeSlaMinutes()
                        : DEFAULT_HIGH_SEVERITY_ACKNOWLEDGE_SLA_MINUTES,
                policy.getHighSeverityResolveSlaMinutes() != null
                        ? policy.getHighSeverityResolveSlaMinutes()
                        : DEFAULT_HIGH_SEVERITY_RESOLVE_SLA_MINUTES,
                policy.getHighSeverityReminderIntervalMinutes() != null
                        ? policy.getHighSeverityReminderIntervalMinutes()
                        : DEFAULT_HIGH_SEVERITY_REMINDER_INTERVAL_MINUTES,
                policy.getTriageDelayMinutes() != null
                        ? policy.getTriageDelayMinutes()
                        : DEFAULT_ALERT_TRIAGE_DELAY_MINUTES,
                policy.getTriageReminderIntervalMinutes() != null
                        ? policy.getTriageReminderIntervalMinutes()
                        : DEFAULT_TRIAGE_REMINDER_INTERVAL_MINUTES,
                policy.getTriageEscalationAfterMinutes() != null
                        ? policy.getTriageEscalationAfterMinutes()
                        : DEFAULT_TRIAGE_ESCALATION_AFTER_MINUTES,
                policy.isAutoDigestEnabled(),
                policy.isAutoTriageEnabled(),
                policy.isTriageAutoAssignEnabled(),
                normalizeClaimStrategy(policy.getClaimNextStrategy()),
                policy.isClaimNextTriageOnlyDefault(),
                policy.getAlertTargetChatId(),
                policy.getReminderTargetChatId(),
                policy.getPersonalReminderTargetChatId(),
                policy.getBreachTargetChatId(),
                policy.getDefaultOwnerUserId(),
                policy.getTriageFallbackOwnerUserId(),
                policy.getTriageTargetChatId(),
                policy.getTriageEscalationTargetChatId(),
                policy.getDigestTargetChatId(),
                policy.getPersonalReminderDigestTargetChatId()
        );
    }

    private MonetizationAlertPolicyResponse toAlertPolicyResponse(
            ChannelMonetizationAlertPolicyEntity policy,
            UUID chatId
    ) {
        EffectiveAlertPolicy resolvedPolicy = policy != null ? effectiveAlertPolicy(policy) : effectiveAlertPolicy(chatId);
        return new MonetizationAlertPolicyResponse(
                chatId,
                policy != null ? policy.getConfiguredByUserId() : null,
                resolvedPolicy.alertThreshold(),
                resolvedPolicy.highSeverityThreshold(),
                resolvedPolicy.alertSuppressionMinutes(),
                resolvedPolicy.acknowledgeSlaMinutes(),
                resolvedPolicy.resolveSlaMinutes(),
                resolvedPolicy.reminderIntervalMinutes(),
                resolvedPolicy.severityUpgradeAfterMinutes(),
                resolvedPolicy.breachEscalationAfterMinutes(),
                resolvedPolicy.highSeverityAcknowledgeSlaMinutes(),
                resolvedPolicy.highSeverityResolveSlaMinutes(),
                resolvedPolicy.highSeverityReminderIntervalMinutes(),
                resolvedPolicy.triageDelayMinutes(),
                resolvedPolicy.triageReminderIntervalMinutes(),
                resolvedPolicy.triageEscalationAfterMinutes(),
                resolvedPolicy.autoDigestEnabled(),
                resolvedPolicy.autoTriageEnabled(),
                resolvedPolicy.triageAutoAssignEnabled(),
                resolvedPolicy.claimNextStrategy(),
                resolvedPolicy.claimNextTriageOnlyDefault(),
                resolvedPolicy.alertTargetChatId(),
                resolvedPolicy.reminderTargetChatId(),
                resolvedPolicy.personalReminderTargetChatId(),
                resolvedPolicy.breachTargetChatId(),
                resolvedPolicy.defaultOwnerUserId(),
                resolvedPolicy.triageFallbackOwnerUserId(),
                resolvedPolicy.triageTargetChatId(),
                resolvedPolicy.triageEscalationTargetChatId(),
                resolvedPolicy.digestTargetChatId(),
                resolvedPolicy.personalReminderDigestTargetChatId(),
                policy != null ? policy.getCreatedAt() : null,
                policy != null ? policy.getUpdatedAt() : null
        );
    }

    private int countAlertsByStatus(List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts, String status) {
        return (int) alerts.stream().filter(alert -> status.equals(alert.getStatus())).count();
    }

    private int countAlertsByStatusAndSeverity(
            List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts,
            String status,
            String severity
    ) {
        return (int) alerts.stream()
                .filter(alert -> status.equals(alert.getStatus()))
                .filter(alert -> severity.equals(alert.getSeverity()))
                .count();
    }

    private int countOverdueAcknowledgementAlerts(
            List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts,
            Instant now
    ) {
        return (int) alerts.stream().filter(alert -> isAcknowledgementOverdue(alert, now)).count();
    }

    private int countOverdueResolutionAlerts(
            List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts,
            Instant now
    ) {
        return (int) alerts.stream().filter(alert -> isResolutionOverdue(alert, now)).count();
    }

    private int countBreachedAlerts(
            List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts,
            Instant now
    ) {
        return (int) alerts.stream().filter(alert -> isAlertBreached(alert, now)).count();
    }

    private int countSubscriptionsByEscalationStatus(
            List<ChannelMonetizationArtifactSubscriptionEntity> subscriptions,
            String escalationStatus
    ) {
        return (int) subscriptions.stream()
                .filter(subscription -> escalationStatus.equals(subscription.getEscalationStatus()))
                .count();
    }

    private MonetizationArtifactAlertWorkloadResponse buildAlertWorkload(
            UUID chatId,
            List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts,
            Instant now
    ) {
        Instant latestAlertAt = alerts.stream()
                .map(ChannelMonetizationArtifactSubscriptionAlertEntity::getCreatedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        List<MonetizationArtifactAlertWorkloadOwnerResponse> owners = alerts.stream()
                .filter(alert -> alert.getOwnerUserId() != null)
                .collect(java.util.stream.Collectors.groupingBy(ChannelMonetizationArtifactSubscriptionAlertEntity::getOwnerUserId))
                .entrySet()
                .stream()
                .map(entry -> toAlertWorkloadOwnerResponse(entry.getKey(), entry.getValue(), now))
                .sorted(Comparator
                        .comparingInt(MonetizationArtifactAlertWorkloadOwnerResponse::breachedAlerts).reversed()
                        .thenComparingInt(MonetizationArtifactAlertWorkloadOwnerResponse::highSeverityAlerts).reversed()
                        .thenComparingInt(MonetizationArtifactAlertWorkloadOwnerResponse::openAlerts).reversed()
                        .thenComparing(MonetizationArtifactAlertWorkloadOwnerResponse::latestAlertAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return new MonetizationArtifactAlertWorkloadResponse(
                chatId,
                alerts.size(),
                countAlertsByStatus(alerts, "OPEN"),
                countAlertsByStatusAndSeverity(alerts, "OPEN", "HIGH"),
                countBreachedAlerts(alerts, now),
                (int) alerts.stream().filter(alert -> isAlertOverdue(alert, now)).count(),
                countUnassignedAlerts(alerts),
                countUnassignedHighSeverityAlerts(alerts),
                owners.size(),
                latestAlertAt,
                owners
        );
    }

    private List<ChannelMonetizationArtifactSubscriptionAlertEntity> loadOwnerAlerts(UUID chatId, UUID ownerUserId) {
        return channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId).stream()
                .filter(alert -> ownerUserId.equals(alert.getOwnerUserId()))
                .toList();
    }

    private List<ChannelMonetizationOwnerReminderDigestSubscriptionEntity> loadOwnerReminderDigestIssueSubscriptions(
            UUID chatId,
            UUID ownerUserId,
            String failureState,
            Boolean retryDueOnly,
            Instant now
    ) {
        String normalizedFailureState = normalizeOwnerReminderDigestFailureStateFilter(failureState);
        return channelMonetizationOwnerReminderDigestSubscriptionRepository.findAllByChannelChatIdOrderByUpdatedAtDesc(chatId).stream()
                .filter(subscription -> ownerUserId == null || ownerUserId.equals(subscription.getOwnerUserId()))
                .filter(this::isOwnerReminderDigestIssueSubscription)
                .filter(subscription -> normalizedFailureState == null || normalizedFailureState.equals(subscription.getFailureState()))
                .filter(subscription -> !Boolean.TRUE.equals(retryDueOnly) || isOwnerReminderDigestRetryDue(subscription, now))
                .sorted(ownerReminderDigestIssueQueueComparator(now))
                .toList();
    }

    private List<ChannelMonetizationArtifactSubscriptionAlertEntity> loadOwnerDueReminderAlerts(
            UUID chatId,
            UUID ownerUserId,
            String severity,
            Boolean breachedOnly,
            Instant now
    ) {
        String normalizedSeverity = normalizeAlertSeverityFilter(severity);
        return loadOwnerAlerts(chatId, ownerUserId).stream()
                .filter(alert -> normalizedSeverity == null || normalizedSeverity.equals(alert.getSeverity()))
                .filter(alert -> !Boolean.TRUE.equals(breachedOnly) || isAlertBreached(alert, now))
                .filter(alert -> isAlertDueForReminder(alert, now))
                .sorted(alertQueueComparator(now))
                .toList();
    }

    private MonetizationArtifactAlertReminderDigestResponse buildOwnerReminderDigest(
            UUID ownerUserId,
            List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts,
            Instant now
    ) {
        ChannelMonetizationArtifactSubscriptionAlertEntity next = alerts.stream().findFirst().orElse(null);
        Instant latestAlertAt = alerts.stream()
                .map(ChannelMonetizationArtifactSubscriptionAlertEntity::getCreatedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new MonetizationArtifactAlertReminderDigestResponse(
                ownerUserId,
                resolveOwnerDisplayName(ownerUserId),
                alerts.size(),
                (int) alerts.stream().filter(alert -> "HIGH".equals(alert.getSeverity())).count(),
                countBreachedAlerts(alerts, now),
                (int) alerts.stream().filter(alert -> isAlertOverdue(alert, now)).count(),
                next != null ? next.getId() : null,
                next != null ? next.getSubscriptionId() : null,
                next != null ? next.getSeverity() : null,
                latestAlertAt
        );
    }

    private boolean isOwnerReminderDigestIssueSubscription(
            ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription
    ) {
        return List.of("BACKOFF", "AUTO_PAUSED").contains(subscription.getFailureState());
    }

    private boolean isOwnerReminderDigestRetryDue(
            ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription,
            Instant now
    ) {
        return "BACKOFF".equals(subscription.getFailureState())
                && subscription.getNextRetryAt() != null
                && !subscription.getNextRetryAt().isAfter(now);
    }

    private int countOwnerReminderDigestSubscriptionsByFailureState(
            List<ChannelMonetizationOwnerReminderDigestSubscriptionEntity> subscriptions,
            String failureState
    ) {
        return (int) subscriptions.stream()
                .filter(subscription -> failureState.equals(subscription.getFailureState()))
                .count();
    }

    private int countDueRetryOwnerReminderDigestSubscriptions(
            List<ChannelMonetizationOwnerReminderDigestSubscriptionEntity> subscriptions,
            Instant now
    ) {
        return (int) subscriptions.stream()
                .filter(subscription -> isOwnerReminderDigestRetryDue(subscription, now))
                .count();
    }

    private List<ChannelMonetizationArtifactSubscriptionAlertEntity> loadClaimableAlerts(
            UUID chatId,
            String severity,
            String status,
            Boolean triageOnly,
            Boolean breachedOnly,
            Boolean overdueOnly,
            String strategy,
            Instant now
    ) {
        EffectiveAlertPolicy alertPolicy = effectiveAlertPolicy(chatId);
        String normalizedSeverity = normalizeAlertSeverityFilter(severity);
        String normalizedStatus = normalizeAlertStatusFilter(status);
        boolean resolvedTriageOnly = triageOnly != null ? triageOnly : alertPolicy.claimNextTriageOnlyDefault();
        String normalizedStrategy = strategy != null && !strategy.isBlank()
                ? normalizeClaimStrategy(strategy)
                : alertPolicy.claimNextStrategy();
        return channelMonetizationArtifactSubscriptionAlertRepository.findAllByChannelChatIdOrderByCreatedAtDesc(chatId).stream()
                .filter(alert -> alert.getOwnerUserId() == null)
                .filter(alert -> !"RESOLVED".equals(alert.getStatus()))
                .filter(alert -> normalizedSeverity == null || normalizedSeverity.equals(alert.getSeverity()))
                .filter(alert -> normalizedStatus == null || normalizedStatus.equals(alert.getStatus()))
                .filter(alert -> !resolvedTriageOnly || isTriageClaimable(alert, now))
                .filter(alert -> !Boolean.TRUE.equals(breachedOnly) || isAlertBreached(alert, now))
                .filter(alert -> !Boolean.TRUE.equals(overdueOnly) || isAlertOverdue(alert, now))
                .sorted(claimableAlertComparator(now, normalizedStrategy))
                .toList();
    }

    private MonetizationArtifactAlertWorkloadOwnerResponse buildOwnerAlertWorkload(
            UUID chatId,
            UUID ownerUserId,
            List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts,
            Instant now
    ) {
        if (alerts.isEmpty()) {
            return new MonetizationArtifactAlertWorkloadOwnerResponse(
                    ownerUserId,
                    resolveOwnerDisplayName(ownerUserId),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    null,
                    null
            );
        }
        return toAlertWorkloadOwnerResponse(ownerUserId, alerts, now);
    }

    private MonetizationArtifactAlertWorkloadOwnerResponse toAlertWorkloadOwnerResponse(
            UUID ownerUserId,
            List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts,
            Instant now
    ) {
        Instant latestAssignedAt = alerts.stream()
                .map(ChannelMonetizationArtifactSubscriptionAlertEntity::getAssignedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        Instant latestAlertAt = alerts.stream()
                .map(ChannelMonetizationArtifactSubscriptionAlertEntity::getCreatedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        String ownerDisplayName = resolveOwnerDisplayName(ownerUserId);
        return new MonetizationArtifactAlertWorkloadOwnerResponse(
                ownerUserId,
                ownerDisplayName,
                alerts.size(),
                countAlertsByStatus(alerts, "OPEN"),
                countAlertsByStatus(alerts, "ACKNOWLEDGED"),
                countAlertsByStatus(alerts, "SNOOZED"),
                (int) alerts.stream().filter(alert -> "HIGH".equals(alert.getSeverity())).count(),
                countBreachedAlerts(alerts, now),
                (int) alerts.stream().filter(alert -> isAlertOverdue(alert, now)).count(),
                latestAssignedAt,
                latestAlertAt
        );
    }

    private String resolveOwnerDisplayName(UUID ownerUserId) {
        return userRepository.findById(ownerUserId)
                .map(user -> user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
                .orElse(null);
    }

    private int countUnassignedAlerts(List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts) {
        return (int) alerts.stream().filter(alert -> alert.getOwnerUserId() == null).count();
    }

    private int countUnassignedHighSeverityAlerts(List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts) {
        return (int) alerts.stream()
                .filter(alert -> alert.getOwnerUserId() == null)
                .filter(alert -> "HIGH".equals(alert.getSeverity()))
                .count();
    }

    private Comparator<ChannelMonetizationArtifactSubscriptionAlertEntity> alertQueueComparator(Instant now) {
        return Comparator
                .comparing((ChannelMonetizationArtifactSubscriptionAlertEntity alert) -> !isAlertBreached(alert, now))
                .thenComparing(alert -> !"HIGH".equals(alert.getSeverity()))
                .thenComparing(alert -> !isAlertOverdue(alert, now))
                .thenComparing(ChannelMonetizationArtifactSubscriptionAlertEntity::getResolveByDueAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChannelMonetizationArtifactSubscriptionAlertEntity::getAcknowledgeByDueAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChannelMonetizationArtifactSubscriptionAlertEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private Comparator<ChannelMonetizationOwnerReminderDigestSubscriptionEntity> ownerReminderDigestIssueQueueComparator(
            Instant now
    ) {
        return Comparator
                .comparing((ChannelMonetizationOwnerReminderDigestSubscriptionEntity subscription) ->
                        !"AUTO_PAUSED".equals(subscription.getFailureState()))
                .thenComparing(subscription -> !isOwnerReminderDigestRetryDue(subscription, now))
                .thenComparing(ChannelMonetizationOwnerReminderDigestSubscriptionEntity::getNextRetryAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChannelMonetizationOwnerReminderDigestSubscriptionEntity::getLastFailureAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ChannelMonetizationOwnerReminderDigestSubscriptionEntity::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private Comparator<ChannelMonetizationArtifactSubscriptionAlertEntity> claimableAlertComparator(
            Instant now,
            String strategy
    ) {
        Comparator<ChannelMonetizationArtifactSubscriptionAlertEntity> base = alertQueueComparator(now);
        return switch (strategy) {
            case "TRIAGE_FIRST" -> Comparator
                    .comparing((ChannelMonetizationArtifactSubscriptionAlertEntity alert) -> !isAlertEligibleForTriageFollowUp(alert, now))
                    .thenComparing((ChannelMonetizationArtifactSubscriptionAlertEntity alert) -> !isTriageClaimable(alert, now))
                    .thenComparing(base);
            case "OLDEST_FIRST" -> Comparator
                    .comparing(ChannelMonetizationArtifactSubscriptionAlertEntity::getCreatedAt,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(base);
            default -> base;
        };
    }

    private void applyAlertSla(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            Instant now,
            EffectiveAlertPolicy alertPolicy
    ) {
        alert.setAcknowledgeByDueAt(now.plusSeconds(resolveAcknowledgeSlaMinutes(alert, alertPolicy) * 60L));
        alert.setResolveByDueAt(now.plusSeconds(resolveResolveSlaMinutes(alert, alertPolicy) * 60L));
        alert.setLastReminderAt(null);
        alert.setReminderCount(0);
        alert.setLastReminderMessageId(null);
        alert.setLastReminderTargetChatId(null);
        alert.setSeverityEscalatedAt(null);
        alert.setBreachedAt(null);
        alert.setBreachMessageId(null);
        alert.setTriagedAt(null);
        alert.setTriageMessageId(null);
        alert.setTriageTargetChatId(null);
        alert.setLastTriageReminderAt(null);
        alert.setTriageReminderCount(0);
        alert.setLastTriageReminderMessageId(null);
        alert.setLastTriageReminderTargetChatId(null);
        alert.setTriageEscalatedAt(null);
        alert.setTriageEscalationMessageId(null);
        alert.setTriageEscalationTargetChatId(null);
    }

    private void tightenAlertSlaForSeverity(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            Instant now,
            EffectiveAlertPolicy alertPolicy
    ) {
        Instant tightenedAcknowledgeBy = now.plusSeconds(resolveAcknowledgeSlaMinutes(alert, alertPolicy) * 60L);
        if (alert.getAcknowledgeByDueAt() == null || alert.getAcknowledgeByDueAt().isAfter(tightenedAcknowledgeBy)) {
            alert.setAcknowledgeByDueAt(tightenedAcknowledgeBy);
        }
        Instant tightenedResolveBy = now.plusSeconds(resolveResolveSlaMinutes(alert, alertPolicy) * 60L);
        if (alert.getResolveByDueAt() == null || alert.getResolveByDueAt().isAfter(tightenedResolveBy)) {
            alert.setResolveByDueAt(tightenedResolveBy);
        }
    }

    private int resolveAcknowledgeSlaMinutes(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            EffectiveAlertPolicy alertPolicy
    ) {
        return "HIGH".equals(alert.getSeverity())
                ? alertPolicy.highSeverityAcknowledgeSlaMinutes()
                : alertPolicy.acknowledgeSlaMinutes();
    }

    private int resolveResolveSlaMinutes(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            EffectiveAlertPolicy alertPolicy
    ) {
        return "HIGH".equals(alert.getSeverity())
                ? alertPolicy.highSeverityResolveSlaMinutes()
                : alertPolicy.resolveSlaMinutes();
    }

    private int resolveReminderIntervalMinutes(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            EffectiveAlertPolicy alertPolicy
    ) {
        return "HIGH".equals(alert.getSeverity())
                ? alertPolicy.highSeverityReminderIntervalMinutes()
                : alertPolicy.reminderIntervalMinutes();
    }

    private boolean autoAssignAlertOwnerIfNeeded(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            EffectiveAlertPolicy alertPolicy,
            UUID actorUserId,
            String note
    ) {
        if (alert.getOwnerUserId() != null || alertPolicy.defaultOwnerUserId() == null) {
            return false;
        }
        alert.setOwnerUserId(alertPolicy.defaultOwnerUserId());
        if (alert.getAssignedAt() == null) {
            alert.setAssignedAt(Instant.now());
        }
        recordAlertAuditEvent(
                alert,
                "AUTO_ASSIGNED",
                actorUserId,
                alert.getOwnerUserId(),
                alert.getStatus(),
                alert.getStatus(),
                note
        );
        return true;
    }

    private boolean autoAssignTriageFallbackOwnerIfNeeded(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            EffectiveAlertPolicy alertPolicy,
            UUID actorUserId
    ) {
        if (!alertPolicy.triageAutoAssignEnabled()
                || alert.getOwnerUserId() != null
                || alertPolicy.triageFallbackOwnerUserId() == null) {
            return false;
        }
        ChannelMonetizationArtifactSubscriptionEntity subscription = getSubscription(
                alert.getChannelChatId(),
                alert.getSubscriptionId()
        );
        assignAlertOwner(
                subscription,
                alert,
                alertPolicy.triageFallbackOwnerUserId(),
                actorUserId,
                "TRIAGE_AUTO_ASSIGNED",
                "Assigned by triage fallback owner policy"
        );
        return true;
    }

    private void assignAlertOwner(
            ChannelMonetizationArtifactSubscriptionEntity subscription,
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            UUID ownerUserId,
            UUID actorUserId,
            String actionType,
            String note
    ) {
        UUID previousOwnerUserId = alert.getOwnerUserId();
        alert.setOwnerUserId(ownerUserId);
        alert.setAssignedAt(ownerUserId != null ? Instant.now() : null);
        channelMonetizationArtifactSubscriptionAlertRepository.save(alert);
        recordAlertAuditEvent(
                alert,
                actionType,
                actorUserId,
                previousOwnerUserId,
                alert.getStatus(),
                alert.getStatus(),
                note
        );
        if (ownerUserId != null && List.of("OPEN", "SUPPRESSED").contains(subscription.getEscalationStatus())) {
            subscription.setEscalationStatus("ACKNOWLEDGED");
            channelMonetizationArtifactSubscriptionRepository.save(subscription);
        }
    }

    private boolean isAcknowledgementOverdue(ChannelMonetizationArtifactSubscriptionAlertEntity alert, Instant now) {
        return !"RESOLVED".equals(alert.getStatus())
                && !isSnoozeActive(alert, now)
                && alert.getAcknowledgedAt() == null
                && alert.getAcknowledgeByDueAt() != null
                && !alert.getAcknowledgeByDueAt().isAfter(now);
    }

    private boolean isResolutionOverdue(ChannelMonetizationArtifactSubscriptionAlertEntity alert, Instant now) {
        return !"RESOLVED".equals(alert.getStatus())
                && !isSnoozeActive(alert, now)
                && alert.getResolveByDueAt() != null
                && !alert.getResolveByDueAt().isAfter(now);
    }

    private boolean isAlertOverdue(ChannelMonetizationArtifactSubscriptionAlertEntity alert, Instant now) {
        return isAcknowledgementOverdue(alert, now) || isResolutionOverdue(alert, now);
    }

    private boolean isAlertBreached(ChannelMonetizationArtifactSubscriptionAlertEntity alert, Instant now) {
        if ("RESOLVED".equals(alert.getStatus()) || isSnoozeActive(alert, now)) {
            return false;
        }
        if (alert.getBreachedAt() != null) {
            return true;
        }
        Instant escalationReferenceAt = resolveEscalationReferenceAt(alert, now);
        if (escalationReferenceAt == null) {
            return false;
        }
        EffectiveAlertPolicy alertPolicy = effectiveAlertPolicy(alert.getChannelChatId());
        return !escalationReferenceAt.plusSeconds(alertPolicy.breachEscalationAfterMinutes() * 60L).isAfter(now);
    }

    private boolean isAlertEligibleForTriage(ChannelMonetizationArtifactSubscriptionAlertEntity alert, Instant now) {
        return !"RESOLVED".equals(alert.getStatus())
                && !isSnoozeActive(alert, now)
                && "HIGH".equals(alert.getSeverity())
                && alert.getOwnerUserId() == null;
    }

    private boolean isAlertDueForTriage(ChannelMonetizationArtifactSubscriptionAlertEntity alert, Instant now) {
        EffectiveAlertPolicy alertPolicy = effectiveAlertPolicy(alert.getChannelChatId());
        if (!alertPolicy.autoTriageEnabled() && alert.getTriagedAt() == null) {
            return false;
        }
        if (alert.getTriagedAt() != null) {
            return false;
        }
        Instant referenceAt = alert.getSeverityEscalatedAt() != null
                ? alert.getSeverityEscalatedAt()
                : alert.getCreatedAt();
        if (referenceAt == null) {
            return false;
        }
        return !referenceAt.plusSeconds(alertPolicy.triageDelayMinutes() * 60L).isAfter(now);
    }

    private boolean isAlertEligibleForTriageFollowUp(ChannelMonetizationArtifactSubscriptionAlertEntity alert, Instant now) {
        return !"RESOLVED".equals(alert.getStatus())
                && !isSnoozeActive(alert, now)
                && alert.getOwnerUserId() == null
                && alert.getTriagedAt() != null;
    }

    private boolean isTriageClaimable(ChannelMonetizationArtifactSubscriptionAlertEntity alert, Instant now) {
        return isAlertEligibleForTriage(alert, now)
                || isAlertEligibleForTriageFollowUp(alert, now)
                || isAlertDueForTriage(alert, now)
                || isAlertDueForTriageReminder(alert, now)
                || isAlertDueForTriageEscalation(alert, now);
    }

    private boolean isAlertDueForTriageReminder(ChannelMonetizationArtifactSubscriptionAlertEntity alert, Instant now) {
        if (!isAlertEligibleForTriageFollowUp(alert, now)) {
            return false;
        }
        EffectiveAlertPolicy alertPolicy = effectiveAlertPolicy(alert.getChannelChatId());
        Instant referenceAt = alert.getLastTriageReminderAt() != null
                ? alert.getLastTriageReminderAt()
                : alert.getTriagedAt();
        if (referenceAt == null) {
            return false;
        }
        return !referenceAt.plusSeconds(alertPolicy.triageReminderIntervalMinutes() * 60L).isAfter(now);
    }

    private boolean isAlertDueForTriageEscalation(ChannelMonetizationArtifactSubscriptionAlertEntity alert, Instant now) {
        if (!isAlertEligibleForTriageFollowUp(alert, now) || alert.getTriageEscalatedAt() != null) {
            return false;
        }
        EffectiveAlertPolicy alertPolicy = effectiveAlertPolicy(alert.getChannelChatId());
        return !alert.getTriagedAt().plusSeconds(alertPolicy.triageEscalationAfterMinutes() * 60L).isAfter(now);
    }

    private boolean isAlertDueForReminder(ChannelMonetizationArtifactSubscriptionAlertEntity alert, Instant dueBefore) {
        if ("RESOLVED".equals(alert.getStatus()) || isSnoozeActive(alert, dueBefore)) {
            return false;
        }
        EffectiveAlertPolicy alertPolicy = effectiveAlertPolicy(alert.getChannelChatId());
        if (!isAlertOverdue(alert, dueBefore)) {
            return false;
        }
        Instant lastReminderAt = alert.getLastReminderAt();
        return lastReminderAt == null
                || !lastReminderAt.plusSeconds(resolveReminderIntervalMinutes(alert, alertPolicy) * 60L).isAfter(dueBefore);
    }

    private String resolveReminderType(ChannelMonetizationArtifactSubscriptionAlertEntity alert, Instant now) {
        if (isAcknowledgementOverdue(alert, now)) {
            return "ACKNOWLEDGEMENT_OVERDUE";
        }
        if (isResolutionOverdue(alert, now)) {
            return "RESOLUTION_OVERDUE";
        }
        return alert.getAcknowledgedAt() == null ? "ACKNOWLEDGEMENT_PENDING" : "RESOLUTION_PENDING";
    }

    private Instant resolveEscalationReferenceAt(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            Instant now
    ) {
        if (isAcknowledgementOverdue(alert, now)) {
            return alert.getAcknowledgeByDueAt();
        }
        if (isResolutionOverdue(alert, now)) {
            return alert.getResolveByDueAt();
        }
        return null;
    }

    private boolean refreshAlertEscalation(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            Instant now,
            UUID actorUserId
    ) {
        if ("RESOLVED".equals(alert.getStatus()) || isSnoozeActive(alert, now)) {
            return false;
        }

        EffectiveAlertPolicy alertPolicy = effectiveAlertPolicy(alert.getChannelChatId());
        Instant escalationReferenceAt = resolveEscalationReferenceAt(alert, now);
        if (escalationReferenceAt == null) {
            return false;
        }

        boolean changed = false;
        if (!"HIGH".equals(alert.getSeverity())
                && !escalationReferenceAt.plusSeconds(alertPolicy.severityUpgradeAfterMinutes() * 60L).isAfter(now)) {
            String previousSeverity = alert.getSeverity();
            alert.setSeverity("HIGH");
            if (alert.getSeverityEscalatedAt() == null) {
                alert.setSeverityEscalatedAt(now);
            }
            tightenAlertSlaForSeverity(alert, now, alertPolicy);
            recordAlertAuditEvent(
                    alert,
                    "SEVERITY_ESCALATED",
                    actorUserId,
                    alert.getOwnerUserId(),
                    alert.getStatus(),
                    alert.getStatus(),
                    "Severity escalated from %s because alert exceeded SLA threshold".formatted(
                            previousSeverity != null ? previousSeverity : "UNSET"
                    )
            );
            changed = true;
        }

        if ("HIGH".equals(alert.getSeverity())
                && autoAssignAlertOwnerIfNeeded(
                        alert,
                        alertPolicy,
                        actorUserId,
                        "Assigned by monetization severity policy"
                )) {
            changed = true;
        }

        if (alert.getBreachedAt() == null
                && !escalationReferenceAt.plusSeconds(alertPolicy.breachEscalationAfterMinutes() * 60L).isAfter(now)) {
            UUID senderId = actorUserId != null ? actorUserId : resolveRevenueRecipientUserId(alert.getChannelChatId());
            UUID breachTargetChatId = resolveBreachTargetChatId(alert, alertPolicy);
            if (senderId != null) {
                try {
                    ChatMessageResponse published = messageService.sendMessage(
                            senderId,
                            new SendMessageRequest(
                                    breachTargetChatId,
                                    null,
                                    null,
                                    null,
                                    composeArtifactAlertBreachText(alert),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    true,
                                    null
                            )
                    );
                    alert.setBreachMessageId(published.messageId());
                } catch (RuntimeException ignored) {
                    // Persist breached state even if chat publication fails.
                }
            }
            alert.setBreachedAt(now);
            recordAlertAuditEvent(
                    alert,
                    "BREACHED",
                    actorUserId,
                    alert.getOwnerUserId(),
                    alert.getStatus(),
                    alert.getStatus(),
                    "Alert exceeded breach escalation window"
            );
            changed = true;
        }

        if (changed) {
            channelMonetizationArtifactSubscriptionAlertRepository.save(alert);
        }
        return changed;
    }

    private MonetizationArtifactAlertReminderResponse sendAlertReminder(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            UUID requesterId,
            boolean manual
    ) {
        Instant now = Instant.now();
        String reminderType = resolveReminderType(alert, now);
        EffectiveAlertPolicy alertPolicy = effectiveAlertPolicy(alert.getChannelChatId());
        UUID routedTargetChatId = resolveReminderTargetChatId(alert, alertPolicy);
        UUID senderId = requesterId != null ? requesterId : resolveRevenueRecipientUserId(alert.getChannelChatId());
        if (senderId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Monetization alert reminder sender is not configured");
        }
        ChatMessageResponse published = messageService.sendMessage(
                senderId,
                new SendMessageRequest(
                        routedTargetChatId,
                        null,
                        null,
                        null,
                        composeArtifactAlertReminderText(alert, reminderType, manual),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        null
                )
        );
        alert.setLastReminderAt(now);
        alert.setReminderCount((alert.getReminderCount() != null ? alert.getReminderCount() : 0) + 1);
        alert.setLastReminderMessageId(published.messageId());
        alert.setLastReminderTargetChatId(routedTargetChatId);
        channelMonetizationArtifactSubscriptionAlertRepository.save(alert);
        recordAlertAuditEvent(
                alert,
                manual ? "MANUAL_REMINDER_SENT" : "REMINDER_SENT",
                requesterId,
                alert.getOwnerUserId(),
                alert.getStatus(),
                alert.getStatus(),
                reminderType
        );
        return toArtifactAlertReminderResponse(alert, reminderType, now);
    }

    private MonetizationArtifactAlertTriageResponse sendAlertTriage(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            UUID requesterId,
            boolean manual
    ) {
        Instant now = Instant.now();
        EffectiveAlertPolicy alertPolicy = effectiveAlertPolicy(alert.getChannelChatId());
        UUID triageTargetChatId = resolveTriageTargetChatId(alert, alertPolicy);
        UUID senderId = requesterId != null ? requesterId : resolveRevenueRecipientUserId(alert.getChannelChatId());
        if (senderId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Monetization alert triage sender is not configured");
        }
        ChatMessageResponse published = messageService.sendMessage(
                senderId,
                new SendMessageRequest(
                        triageTargetChatId,
                        null,
                        null,
                        null,
                        composeArtifactAlertTriageText(alert, manual),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        null
                )
        );
        alert.setTriagedAt(now);
        alert.setTriageMessageId(published.messageId());
        alert.setTriageTargetChatId(triageTargetChatId);
        alert.setLastTriageReminderAt(null);
        alert.setTriageReminderCount(0);
        alert.setLastTriageReminderMessageId(null);
        alert.setLastTriageReminderTargetChatId(null);
        alert.setTriageEscalatedAt(null);
        alert.setTriageEscalationMessageId(null);
        alert.setTriageEscalationTargetChatId(null);
        channelMonetizationArtifactSubscriptionAlertRepository.save(alert);
        recordAlertAuditEvent(
                alert,
                manual ? "MANUAL_TRIAGED" : "TRIAGED",
                requesterId,
                alert.getOwnerUserId(),
                alert.getStatus(),
                alert.getStatus(),
                "Alert routed for triage"
        );
        return toArtifactAlertTriageResponse(alert, manual);
    }

    private MonetizationArtifactAlertTriageReminderResponse sendAlertTriageReminder(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            UUID requesterId,
            boolean manual
    ) {
        Instant now = Instant.now();
        EffectiveAlertPolicy alertPolicy = effectiveAlertPolicy(alert.getChannelChatId());
        UUID routedTargetChatId = resolveTriageTargetChatId(alert, alertPolicy);
        UUID senderId = requesterId != null ? requesterId : resolveRevenueRecipientUserId(alert.getChannelChatId());
        if (senderId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Monetization alert triage reminder sender is not configured");
        }
        ChatMessageResponse published = messageService.sendMessage(
                senderId,
                new SendMessageRequest(
                        routedTargetChatId,
                        null,
                        null,
                        null,
                        composeArtifactAlertTriageReminderText(alert, manual),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        null
                )
        );
        alert.setLastTriageReminderAt(now);
        alert.setTriageReminderCount((alert.getTriageReminderCount() != null ? alert.getTriageReminderCount() : 0) + 1);
        alert.setLastTriageReminderMessageId(published.messageId());
        alert.setLastTriageReminderTargetChatId(routedTargetChatId);
        channelMonetizationArtifactSubscriptionAlertRepository.save(alert);
        recordAlertAuditEvent(
                alert,
                manual ? "MANUAL_TRIAGE_REMINDER_SENT" : "TRIAGE_REMINDER_SENT",
                requesterId,
                alert.getOwnerUserId(),
                alert.getStatus(),
                alert.getStatus(),
                "Alert still requires triage follow-up"
        );
        return toArtifactAlertTriageReminderResponse(alert, routedTargetChatId, manual);
    }

    private void sendAlertTriageEscalation(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            UUID requesterId
    ) {
        Instant now = Instant.now();
        EffectiveAlertPolicy alertPolicy = effectiveAlertPolicy(alert.getChannelChatId());
        UUID routedTargetChatId = resolveTriageEscalationTargetChatId(alert, alertPolicy);
        UUID senderId = requesterId != null ? requesterId : resolveRevenueRecipientUserId(alert.getChannelChatId());
        if (senderId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Monetization alert triage escalation sender is not configured");
        }
        ChatMessageResponse published = messageService.sendMessage(
                senderId,
                new SendMessageRequest(
                        routedTargetChatId,
                        null,
                        null,
                        null,
                        composeArtifactAlertTriageEscalationText(alert),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        null
                )
        );
        alert.setTriageEscalatedAt(now);
        alert.setTriageEscalationMessageId(published.messageId());
        alert.setTriageEscalationTargetChatId(routedTargetChatId);
        channelMonetizationArtifactSubscriptionAlertRepository.save(alert);
        recordAlertAuditEvent(
                alert,
                "TRIAGE_ESCALATED",
                requesterId,
                alert.getOwnerUserId(),
                alert.getStatus(),
                alert.getStatus(),
                "Alert remained unassigned after triage escalation window"
        );
    }

    private String composeArtifactAlertReminderText(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            String reminderType,
            boolean manual
    ) {
        String owner = alert.getOwnerUserId() != null ? alert.getOwnerUserId().toString() : "unassigned";
        return """
                Monetization alert reminder

                Type: %s
                Subscription: %s
                Owner: %s
                Status: %s
                Ack due: %s
                Resolve due: %s
                Reason: %s
                Trigger: %s
                """.formatted(
                reminderType,
                alert.getSubscriptionId(),
                owner,
                alert.getStatus(),
                alert.getAcknowledgeByDueAt(),
                alert.getResolveByDueAt(),
                alert.getLastFailureReason(),
                manual ? "MANUAL" : "SCHEDULED"
        ).trim();
    }

    private String composeArtifactAlertTriageText(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            boolean manual
    ) {
        return """
                Monetization alert triage required

                Subscription: %s
                Severity: %s
                Status: %s
                Ack due: %s
                Resolve due: %s
                Reason: %s
                Trigger: %s
                """.formatted(
                alert.getSubscriptionId(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getAcknowledgeByDueAt(),
                alert.getResolveByDueAt(),
                alert.getLastFailureReason(),
                manual ? "MANUAL" : "SCHEDULED"
        ).trim();
    }

    private String composeArtifactAlertTriageReminderText(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            boolean manual
    ) {
        return """
                Monetization alert triage reminder

                Subscription: %s
                Severity: %s
                Status: %s
                Initial triage at: %s
                Ack due: %s
                Resolve due: %s
                Reason: %s
                Trigger: %s
                """.formatted(
                alert.getSubscriptionId(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getTriagedAt(),
                alert.getAcknowledgeByDueAt(),
                alert.getResolveByDueAt(),
                alert.getLastFailureReason(),
                manual ? "MANUAL" : "SCHEDULED"
        ).trim();
    }

    private String composeArtifactAlertTriageEscalationText(ChannelMonetizationArtifactSubscriptionAlertEntity alert) {
        return """
                Monetization alert triage escalated

                Subscription: %s
                Severity: %s
                Status: %s
                Initial triage at: %s
                Ack due: %s
                Resolve due: %s
                Reason: %s
                """.formatted(
                alert.getSubscriptionId(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getTriagedAt(),
                alert.getAcknowledgeByDueAt(),
                alert.getResolveByDueAt(),
                alert.getLastFailureReason()
        ).trim();
    }

    private String composeArtifactAlertBreachText(ChannelMonetizationArtifactSubscriptionAlertEntity alert) {
        String owner = alert.getOwnerUserId() != null ? alert.getOwnerUserId().toString() : "unassigned";
        return """
                Monetization alert breached SLA

                Subscription: %s
                Owner: %s
                Status: %s
                Severity: %s
                Ack due: %s
                Resolve due: %s
                Reason: %s
                """.formatted(
                alert.getSubscriptionId(),
                owner,
                alert.getStatus(),
                alert.getSeverity(),
                alert.getAcknowledgeByDueAt(),
                alert.getResolveByDueAt(),
                alert.getLastFailureReason()
        ).trim();
    }

    private UUID resolveReminderTargetChatId(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            EffectiveAlertPolicy alertPolicy
    ) {
        if (alert.getOwnerUserId() != null && alertPolicy.personalReminderTargetChatId() != null) {
            return alertPolicy.personalReminderTargetChatId();
        }
        return alertPolicy.reminderTargetChatId() != null
                ? alertPolicy.reminderTargetChatId()
                : alert.getTargetChatId();
    }

    private UUID resolvePersonalReminderDigestTargetChatId(EffectiveAlertPolicy alertPolicy) {
        if (alertPolicy.personalReminderDigestTargetChatId() != null) {
            return alertPolicy.personalReminderDigestTargetChatId();
        }
        if (alertPolicy.digestTargetChatId() != null) {
            return alertPolicy.digestTargetChatId();
        }
        if (alertPolicy.personalReminderTargetChatId() != null) {
            return alertPolicy.personalReminderTargetChatId();
        }
        return alertPolicy.reminderTargetChatId();
    }

    private UUID resolveBreachTargetChatId(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            EffectiveAlertPolicy alertPolicy
    ) {
        if (alertPolicy.breachTargetChatId() != null) {
            return alertPolicy.breachTargetChatId();
        }
        return resolveReminderTargetChatId(alert, alertPolicy);
    }

    private UUID resolveTriageTargetChatId(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            EffectiveAlertPolicy alertPolicy
    ) {
        if (alertPolicy.triageTargetChatId() != null) {
            return alertPolicy.triageTargetChatId();
        }
        if (alertPolicy.breachTargetChatId() != null) {
            return alertPolicy.breachTargetChatId();
        }
        return resolveReminderTargetChatId(alert, alertPolicy);
    }

    private UUID resolveTriageEscalationTargetChatId(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            EffectiveAlertPolicy alertPolicy
    ) {
        if (alertPolicy.triageEscalationTargetChatId() != null) {
            return alertPolicy.triageEscalationTargetChatId();
        }
        return resolveTriageTargetChatId(alert, alertPolicy);
    }

    private boolean isSnoozeActive(ChannelMonetizationArtifactSubscriptionAlertEntity alert, Instant now) {
        return alert != null
                && "SNOOZED".equals(alert.getStatus())
                && alert.getSnoozedUntil() != null
                && alert.getSnoozedUntil().isAfter(now);
    }

    private boolean isExpiredSnoozedAlert(ChannelMonetizationArtifactSubscriptionAlertEntity alert, Instant now) {
        return alert != null
                && "SNOOZED".equals(alert.getStatus())
                && alert.getSnoozedUntil() != null
                && !alert.getSnoozedUntil().isAfter(now);
    }

    private ChannelMonetizationArtifactSubscriptionAlertEntity reopenExpiredSnoozedAlert(
            ChannelMonetizationArtifactSubscriptionAlertEntity alert,
            Instant now
    ) {
        if (!isExpiredSnoozedAlert(alert, now)) {
            return alert;
        }
        String previousStatus = alert.getStatus();
        alert.setStatus("OPEN");
        alert.setSnoozedUntil(null);
        ChannelMonetizationArtifactSubscriptionAlertEntity saved = channelMonetizationArtifactSubscriptionAlertRepository.save(alert);
        recordAlertAuditEvent(
                saved,
                "SNOOZE_EXPIRED",
                null,
                saved.getOwnerUserId(),
                previousStatus,
                saved.getStatus(),
                "Alert snooze window expired"
        );
        return saved;
    }

    private MonetizationAlertDigestRunResponse createAlertDigestRun(
            UUID chatId,
            UUID requesterId,
            String triggerMode,
            UUID targetChatId,
            String note
    ) {
        Instant now = Instant.now();
        List<ChannelMonetizationArtifactSubscriptionAlertEntity> openAlerts = channelMonetizationArtifactSubscriptionAlertRepository
                .findAllByChannelChatIdOrderByCreatedAtDesc(chatId)
                .stream()
                .filter(alert -> "OPEN".equals(alert.getStatus()) || isExpiredSnoozedAlert(alert, now))
                .map(alert -> reopenExpiredSnoozedAlert(alert, now))
                .toList();
        if (openAlerts.isEmpty()) {
            return null;
        }
        List<ChannelMonetizationArtifactSubscriptionFailureEntity> recentFailures = channelMonetizationArtifactSubscriptionFailureRepository
                .findAllByChannelChatIdOrderByFailedAtDesc(chatId)
                .stream()
                .limit(20)
                .toList();

        String content = serializeAlertDigest(chatId, openAlerts, recentFailures);
        MonetizationExportArtifactResponse artifact = persistArtifact(
                chatId,
                requesterId,
                "ALERT_DIGEST_EXPORT",
                "JSON",
                "channel-%s-alert-digest.json".formatted(chatId),
                openAlerts.size() + recentFailures.size(),
                openAlerts.size(),
                content
        );
        ChannelMonetizationExportArtifactEntity artifactEntity = channelMonetizationExportArtifactRepository.findById(artifact.artifactId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monetization export artifact not found"));

        MonetizationArtifactPublicationResponse publication = null;
        if (targetChatId != null) {
            if (requesterId != null) {
                chatService.getOwnedChat(requesterId, targetChatId);
            }
            publication = publishArtifactInternal(requesterId, chatId, artifactEntity, targetChatId, note);
        }

        ChannelMonetizationAlertDigestRunEntity run = new ChannelMonetizationAlertDigestRunEntity();
        run.setChannelChatId(chatId);
        run.setGeneratedByUserId(requesterId);
        run.setTriggerMode(triggerMode);
        run.setOpenAlertCount(openAlerts.size());
        run.setAffectedSubscriptionCount((int) openAlerts.stream().map(ChannelMonetizationArtifactSubscriptionAlertEntity::getSubscriptionId).distinct().count());
        run.setArtifactId(artifact.artifactId());
        run.setPublishedMessageId(publication != null ? publication.publishedMessageId() : null);
        return toAlertDigestRunResponse(channelMonetizationAlertDigestRunRepository.save(run));
    }

    private String serializeAlertDigest(
            UUID chatId,
            List<ChannelMonetizationArtifactSubscriptionAlertEntity> openAlerts,
            List<ChannelMonetizationArtifactSubscriptionFailureEntity> recentFailures
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelChatId", chatId);
        payload.put("openAlertCount", openAlerts.size());
        payload.put("affectedSubscriptionCount", openAlerts.stream().map(ChannelMonetizationArtifactSubscriptionAlertEntity::getSubscriptionId).distinct().count());
        payload.put("alerts", openAlerts.stream()
                .map(alert -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("alertId", alert.getId());
                    item.put("subscriptionId", alert.getSubscriptionId());
                    item.put("severity", alert.getSeverity());
                    item.put("failureCount", alert.getFailureCount());
                    item.put("lastFailureReason", alert.getLastFailureReason());
                    item.put("createdAt", alert.getCreatedAt());
                    return item;
                })
                .toList());
        payload.put("recentFailures", recentFailures.stream()
                .map(failure -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("failureId", failure.getId());
                    item.put("subscriptionId", failure.getSubscriptionId());
                    item.put("artifactType", failure.getArtifactType());
                    item.put("attemptNumber", failure.getAttemptNumber());
                    item.put("failureReason", failure.getFailureReason());
                    item.put("failedAt", failure.getFailedAt());
                    return item;
                })
                .toList());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize alert digest");
        }
    }

    private String serializeAlertWorkload(
            MonetizationArtifactAlertWorkloadResponse workload,
            List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts,
            Instant now
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelChatId", workload.channelChatId());
        payload.put("generatedAt", now);
        payload.put("summary", Map.of(
                "totalAlerts", workload.totalAlerts(),
                "openAlerts", workload.openAlerts(),
                "highSeverityOpenAlerts", workload.highSeverityOpenAlerts(),
                "breachedAlerts", workload.breachedAlerts(),
                "overdueAlerts", workload.overdueAlerts(),
                "unassignedAlerts", workload.unassignedAlerts(),
                "unassignedHighSeverityAlerts", workload.unassignedHighSeverityAlerts(),
                "assignedOwnerCount", workload.assignedOwnerCount()
        ));
        payload.put("owners", workload.owners().stream()
                .map(owner -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("ownerUserId", owner.ownerUserId());
                    item.put("ownerDisplayName", owner.ownerDisplayName());
                    item.put("totalAlerts", owner.totalAlerts());
                    item.put("openAlerts", owner.openAlerts());
                    item.put("acknowledgedAlerts", owner.acknowledgedAlerts());
                    item.put("snoozedAlerts", owner.snoozedAlerts());
                    item.put("highSeverityAlerts", owner.highSeverityAlerts());
                    item.put("breachedAlerts", owner.breachedAlerts());
                    item.put("overdueAlerts", owner.overdueAlerts());
                    item.put("latestAssignedAt", owner.latestAssignedAt());
                    item.put("latestAlertAt", owner.latestAlertAt());
                    return item;
                })
                .toList());
        payload.put("queue", alerts.stream()
                .sorted(alertQueueComparator(now))
                .limit(100)
                .map(alert -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("alertId", alert.getId());
                    item.put("subscriptionId", alert.getSubscriptionId());
                    item.put("severity", alert.getSeverity());
                    item.put("status", alert.getStatus());
                    item.put("ownerUserId", alert.getOwnerUserId());
                    item.put("breached", isAlertBreached(alert, now));
                    item.put("overdue", isAlertOverdue(alert, now));
                    item.put("failureCount", alert.getFailureCount());
                    item.put("acknowledgeByDueAt", alert.getAcknowledgeByDueAt());
                    item.put("resolveByDueAt", alert.getResolveByDueAt());
                    item.put("lastFailureReason", alert.getLastFailureReason());
                    return item;
                })
                .toList());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize alert workload");
        }
    }

    private String serializeOwnerAlertWorkload(
            UUID chatId,
            MonetizationArtifactAlertWorkloadOwnerResponse workload,
            List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts,
            Instant now
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelChatId", chatId);
        payload.put("generatedAt", now);
        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("ownerUserId", workload.ownerUserId());
        owner.put("ownerDisplayName", workload.ownerDisplayName() != null ? workload.ownerDisplayName() : "");
        owner.put("totalAlerts", workload.totalAlerts());
        owner.put("openAlerts", workload.openAlerts());
        owner.put("acknowledgedAlerts", workload.acknowledgedAlerts());
        owner.put("snoozedAlerts", workload.snoozedAlerts());
        owner.put("highSeverityAlerts", workload.highSeverityAlerts());
        owner.put("breachedAlerts", workload.breachedAlerts());
        owner.put("overdueAlerts", workload.overdueAlerts());
        owner.put("latestAssignedAt", workload.latestAssignedAt());
        owner.put("latestAlertAt", workload.latestAlertAt());
        payload.put("owner", owner);
        payload.put("queue", alerts.stream()
                .sorted(alertQueueComparator(now))
                .map(alert -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("alertId", alert.getId());
                    item.put("subscriptionId", alert.getSubscriptionId());
                    item.put("severity", alert.getSeverity());
                    item.put("status", alert.getStatus());
                    item.put("breached", isAlertBreached(alert, now));
                    item.put("overdue", isAlertOverdue(alert, now));
                    item.put("failureCount", alert.getFailureCount());
                    item.put("acknowledgeByDueAt", alert.getAcknowledgeByDueAt());
                    item.put("resolveByDueAt", alert.getResolveByDueAt());
                    item.put("lastFailureReason", alert.getLastFailureReason());
                    item.put("createdAt", alert.getCreatedAt());
                    return item;
                })
                .toList());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize owner alert workload");
        }
    }

    private String serializeOwnerReminderQueue(
            UUID chatId,
            UUID ownerUserId,
            List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts,
            Instant now
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelChatId", chatId);
        payload.put("generatedAt", now);
        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("ownerUserId", ownerUserId);
        owner.put("ownerDisplayName", resolveOwnerDisplayName(ownerUserId));
        owner.put("dueAlerts", alerts.size());
        owner.put("highSeverityDueAlerts", alerts.stream().filter(alert -> "HIGH".equals(alert.getSeverity())).count());
        owner.put("breachedDueAlerts", countBreachedAlerts(alerts, now));
        owner.put("nextAlertId", alerts.isEmpty() ? null : alerts.get(0).getId());
        payload.put("owner", owner);
        payload.put("queue", alerts.stream()
                .sorted(alertQueueComparator(now))
                .map(alert -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("alertId", alert.getId());
                    item.put("subscriptionId", alert.getSubscriptionId());
                    item.put("severity", alert.getSeverity());
                    item.put("status", alert.getStatus());
                    item.put("breached", isAlertBreached(alert, now));
                    item.put("overdue", isAlertOverdue(alert, now));
                    item.put("reminderDue", isAlertDueForReminder(alert, now));
                    item.put("reminderCount", alert.getReminderCount());
                    item.put("lastReminderAt", alert.getLastReminderAt());
                    item.put("lastReminderTargetChatId", alert.getLastReminderTargetChatId());
                    item.put("acknowledgeByDueAt", alert.getAcknowledgeByDueAt());
                    item.put("resolveByDueAt", alert.getResolveByDueAt());
                    item.put("lastFailureReason", alert.getLastFailureReason());
                    item.put("createdAt", alert.getCreatedAt());
                    return item;
                })
                .toList());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize owner reminder queue");
        }
    }

    private String serializeOwnerReminderDigest(
            UUID chatId,
            MonetizationArtifactAlertReminderDigestResponse digest,
            List<ChannelMonetizationArtifactSubscriptionAlertEntity> alerts,
            Instant now
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelChatId", chatId);
        payload.put("generatedAt", now);
        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("ownerUserId", digest.ownerUserId());
        owner.put("ownerDisplayName", digest.ownerDisplayName() != null ? digest.ownerDisplayName() : "");
        owner.put("dueAlerts", digest.dueAlerts());
        owner.put("highSeverityDueAlerts", digest.highSeverityDueAlerts());
        owner.put("breachedDueAlerts", digest.breachedDueAlerts());
        owner.put("overdueDueAlerts", digest.overdueDueAlerts());
        owner.put("nextAlertId", digest.nextAlertId());
        owner.put("nextSubscriptionId", digest.nextSubscriptionId());
        owner.put("nextSeverity", digest.nextSeverity() != null ? digest.nextSeverity() : "");
        owner.put("latestAlertAt", digest.latestAlertAt());
        payload.put("owner", owner);
        payload.put("queuePreview", alerts.stream()
                .sorted(alertQueueComparator(now))
                .limit(25)
                .map(alert -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("alertId", alert.getId());
                    item.put("subscriptionId", alert.getSubscriptionId());
                    item.put("severity", alert.getSeverity());
                    item.put("status", alert.getStatus());
                    item.put("breached", isAlertBreached(alert, now));
                    item.put("overdue", isAlertOverdue(alert, now));
                    item.put("reminderCount", alert.getReminderCount());
                    item.put("acknowledgeByDueAt", alert.getAcknowledgeByDueAt());
                    item.put("resolveByDueAt", alert.getResolveByDueAt());
                    item.put("lastFailureReason", alert.getLastFailureReason());
                    item.put("createdAt", alert.getCreatedAt());
                    return item;
                })
                .toList());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize owner reminder digest");
        }
    }

    private String serializeOwnerReminderDigestIssueQueue(
            UUID chatId,
            UUID ownerUserId,
            String failureState,
            Boolean retryDueOnly,
            List<ChannelMonetizationOwnerReminderDigestSubscriptionEntity> issues,
            Instant now
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelChatId", chatId);
        payload.put("ownerUserId", ownerUserId);
        payload.put("failureState", normalizeOwnerReminderDigestFailureStateFilter(failureState));
        payload.put("retryDueOnly", Boolean.TRUE.equals(retryDueOnly));
        payload.put("generatedAt", now);
        payload.put("summary", Map.of(
                "totalIssues", issues.size(),
                "backoffSubscriptions", countOwnerReminderDigestSubscriptionsByFailureState(issues, "BACKOFF"),
                "autoPausedSubscriptions", countOwnerReminderDigestSubscriptionsByFailureState(issues, "AUTO_PAUSED"),
                "dueRetrySubscriptions", countDueRetryOwnerReminderDigestSubscriptions(issues, now)
        ));
        payload.put("queue", issues.stream()
                .map(subscription -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("subscriptionId", subscription.getId());
                    item.put("ownerUserId", subscription.getOwnerUserId());
                    item.put("ownerDisplayName", resolveOwnerDisplayName(subscription.getOwnerUserId()));
                    item.put("targetChatId", subscription.getTargetChatId());
                    item.put("failureState", subscription.getFailureState());
                    item.put("status", subscription.getStatus());
                    item.put("severity", subscription.getSeverity());
                    item.put("breachedOnly", subscription.isBreachedOnly());
                    item.put("consecutiveFailureCount", subscription.getConsecutiveFailureCount());
                    item.put("lastFailureAt", subscription.getLastFailureAt());
                    item.put("lastFailureReason", subscription.getLastFailureReason());
                    item.put("nextRetryAt", subscription.getNextRetryAt());
                    item.put("autoPausedAt", subscription.getAutoPausedAt());
                    item.put("lastProcessedAt", subscription.getLastProcessedAt());
                    item.put("lastDeliveredAt", subscription.getLastDeliveredAt());
                    return item;
                })
                .toList());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to serialize owner reminder digest issue queue"
            );
        }
    }

    private String serializeOwnerReminderDigestIssueSummary(
            UUID chatId,
            MonetizationOwnerReminderDigestIssueSummaryResponse summary,
            Instant now
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelChatId", chatId);
        payload.put("generatedAt", now);
        payload.put("summary", Map.of(
                "totalIssues", summary.totalIssues(),
                "backoffSubscriptions", summary.backoffSubscriptions(),
                "autoPausedSubscriptions", summary.autoPausedSubscriptions(),
                "dueRetrySubscriptions", summary.dueRetrySubscriptions(),
                "latestFailureAt", summary.latestFailureAt()
        ));
        payload.put("owners", summary.owners().stream()
                .map(owner -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("ownerUserId", owner.ownerUserId());
                    item.put("ownerDisplayName", owner.ownerDisplayName());
                    item.put("totalIssues", owner.totalIssues());
                    item.put("backoffSubscriptions", owner.backoffSubscriptions());
                    item.put("autoPausedSubscriptions", owner.autoPausedSubscriptions());
                    item.put("dueRetrySubscriptions", owner.dueRetrySubscriptions());
                    item.put("latestFailureAt", owner.latestFailureAt());
                    return item;
                })
                .toList());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to serialize owner reminder digest issue summary"
            );
        }
    }

    private ChannelMonetizationWithdrawalEntity resolveWithdrawalForCallback(
            MonetizationWithdrawalProviderCallbackRequest request
    ) {
        if (request.withdrawalId() != null) {
            return channelMonetizationWithdrawalRepository.findById(request.withdrawalId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monetization withdrawal not found"));
        }
        String providerReference = normalizeOptional(request.providerReference(), 128);
        if (providerReference == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Withdrawal id or provider reference is required"
            );
        }
        return channelMonetizationWithdrawalRepository.findFirstByProviderReferenceOrderByRequestedAtDesc(providerReference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monetization withdrawal not found"));
    }

    private String resolveProviderReference(ChannelMonetizationWithdrawalEntity withdrawal, String requestedReference) {
        String normalizedRequestedReference = normalizeOptional(requestedReference, 128);
        String existingReference = normalizeOptional(withdrawal.getProviderReference(), 128);
        if (existingReference != null && normalizedRequestedReference != null && !existingReference.equals(normalizedRequestedReference)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Provider reference does not match withdrawal");
        }
        if (existingReference != null) {
            return existingReference;
        }
        if (normalizedRequestedReference != null) {
            return normalizedRequestedReference;
        }
        return "wdr_%s".formatted(withdrawal.getId().toString().replace("-", "").substring(0, 12));
    }

    private String applyProviderStatus(
            ChannelMonetizationWithdrawalEntity withdrawal,
        String providerStatus,
        String failureReason
    ) {
        if (List.of("COMPLETED", "CANCELED").contains(withdrawal.getStatus())) {
            return "Callback ignored because withdrawal is already finalized";
        }
        return switch (providerStatus) {
            case "PROCESSING" -> {
                if ("PENDING".equals(withdrawal.getStatus())) {
                    startWithdrawalProcessing(withdrawal);
                } else if (!"PROCESSING".equals(withdrawal.getStatus())) {
                    withdrawal.setStatus("PROCESSING");
                    if (withdrawal.getProcessingAt() == null) {
                        withdrawal.setProcessingAt(Instant.now());
                    }
                }
                yield "Provider callback moved withdrawal to processing";
            }
            case "COMPLETED" -> {
                if ("PENDING".equals(withdrawal.getStatus())) {
                    startWithdrawalProcessing(withdrawal);
                }
                reconcileProcessingWithdrawal(withdrawal);
                yield "Provider callback completed withdrawal";
            }
            case "FAILED" -> {
                withdrawal.setStatus("FAILED");
                withdrawal.setFailureReason(failureReason != null ? failureReason : "Provider reported failure");
                withdrawal.setCompletedAt(null);
                yield "Provider callback marked withdrawal as failed";
            }
            case "CANCELED" -> {
                withdrawal.setStatus("CANCELED");
                withdrawal.setCanceledAt(Instant.now());
                withdrawal.setCompletedAt(null);
                yield "Provider callback canceled withdrawal";
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported provider status");
        };
    }

    private ChannelMonetizationWithdrawalCallbackEntity saveWithdrawalCallback(
            ChannelMonetizationWithdrawalCallbackEntity callback
    ) {
        ChannelMonetizationWithdrawalCallbackEntity saved = channelMonetizationWithdrawalCallbackRepository.save(callback);
        return saved != null ? saved : callback;
    }

    private MonetizationExportArtifactResponse persistArtifact(
            UUID chatId,
            UUID requesterId,
            String artifactType,
            String format,
            String fileName,
            int rowCount,
            long totalUnits,
            String content
    ) {
        ChannelMonetizationExportArtifactEntity artifact = new ChannelMonetizationExportArtifactEntity();
        artifact.setChannelChatId(chatId);
        artifact.setGeneratedByUserId(requesterId);
        artifact.setArtifactType(artifactType);
        artifact.setFormat(format);
        artifact.setFileName(fileName);
        artifact.setRowCount(rowCount);
        artifact.setTotalUnits(totalUnits);
        artifact.setChecksum(computeChecksum(content));
        artifact.setContent(content);
        artifact.setCreatedAt(Instant.now());
        ChannelMonetizationExportArtifactEntity saved = channelMonetizationExportArtifactRepository.save(artifact);
        return toArtifactResponse(saved != null ? saved : artifact, true);
    }

    private String serializePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize provider callback payload");
        }
    }

    private String serializeReport(ChannelMonetizationReportResponse report) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelChatId", report.channelChatId());
        payload.put("totalRevenueUnits", report.totalRevenueUnits());
        payload.put("totalSettledUnits", report.totalSettledUnits());
        payload.put("outstandingPayoutUnits", report.outstandingPayoutUnits());
        payload.put("availableWithdrawalUnits", report.availableWithdrawalUnits());
        payload.put("totalWithdrawnUnits", report.totalWithdrawnUnits());
        payload.put("pendingWithdrawalUnits", report.pendingWithdrawalUnits());
        payload.put("failedWithdrawalUnits", report.failedWithdrawalUnits());
        payload.put("totalWithdrawals", report.totalWithdrawals());
        payload.put("pendingWithdrawalCount", report.pendingWithdrawalCount());
        payload.put("processingWithdrawalCount", report.processingWithdrawalCount());
        payload.put("completedWithdrawalCount", report.completedWithdrawalCount());
        payload.put("failedWithdrawalCount", report.failedWithdrawalCount());
        payload.put("canceledWithdrawalCount", report.canceledWithdrawalCount());
        payload.put("averageRevenuePerCampaignUnits", report.averageRevenuePerCampaignUnits());
        payload.put("lastPayoutAt", report.lastPayoutAt());
        payload.put("lastWithdrawalAt", report.lastWithdrawalAt());
        payload.put("lastReconciliationAt", report.lastReconciliationAt());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize monetization report");
        }
    }

    private String serializeProviderSyncResult(
            UUID chatId,
            int payloadSize,
            int appliedCount,
            int ignoredCount,
            int failedCount,
            List<Map<String, Object>> resultEntries
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelChatId", chatId);
        payload.put("payloadSize", payloadSize);
        payload.put("appliedCount", appliedCount);
        payload.put("ignoredCount", ignoredCount);
        payload.put("failedCount", failedCount);
        payload.put("results", resultEntries);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize provider reconciliation report");
        }
    }

    private String computeChecksum(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((content != null ? content : "").getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to compute monetization export checksum");
        }
    }

    private void requireUser(UUID userId) {
        if (userRepository.findById(userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    private String normalizeAlertSeverityFilter(String value) {
        String normalized = normalizeOptional(value, 16);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!List.of("WARN", "HIGH").contains(upper)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported alert severity");
        }
        return upper;
    }

    private String normalizeAlertStatusFilter(String value) {
        String normalized = normalizeOptional(value, 16);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!List.of("OPEN", "ACKNOWLEDGED", "SNOOZED", "RESOLVED").contains(upper)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported alert status");
        }
        return upper;
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

    private String normalizeHttpUrl(String value) {
        String normalized = normalizeOptional(value, 512);
        if (normalized == null) {
            return null;
        }
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(java.util.Locale.ROOT) : null;
            if (!uri.isAbsolute() || scheme == null || (!"http".equals(scheme) && !"https".equals(scheme))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sponsored message CTA URL must be a valid http(s) URL");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sponsored message CTA URL must be a valid http(s) URL");
        }
    }

    private long normalizePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be positive");
        }
        return value;
    }

    private long normalizeNonNegative(Long value, long fallback, String field) {
        long normalized = value != null ? value : fallback;
        if (normalized < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " cannot be negative");
        }
        return normalized;
    }

    private void validateArtifactType(String artifactType) {
        if (!List.of(
                "REPORT_EXPORT",
                "WITHDRAWALS_EXPORT",
                "PAYOUTS_EXPORT",
                "PROVIDER_RECONCILIATION_EXPORT",
                "ALERT_OWNER_REMINDER_DIGEST_ISSUES_EXPORT",
                "ALERT_OWNER_REMINDER_DIGEST_ISSUES_SUMMARY_EXPORT"
        ).contains(artifactType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported artifact type");
        }
    }

    private int normalizeSubscriptionInterval(Integer value) {
        return normalizeSubscriptionInterval(value, 60);
    }

    private int normalizeSubscriptionInterval(Integer value, int fallback) {
        int normalized = value != null ? value : fallback;
        if (normalized < 1 || normalized > 10080) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subscription interval must be between 1 and 10080 minutes");
        }
        return normalized;
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeRequired(value, "Provider status", 32);
        return normalized.toUpperCase(java.util.Locale.ROOT);
    }

    private String normalizeClaimStrategy(String value) {
        if (value == null || value.isBlank()) {
            return "DEFAULT";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DEFAULT", "TRIAGE_FIRST", "OLDEST_FIRST").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported claim strategy");
        }
        return normalized;
    }

    private String normalizeOwnerReminderDigestFailureStateFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("BACKOFF", "AUTO_PAUSED").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported owner reminder digest failure state");
        }
        return normalized;
    }

    private int normalizeOwnerReminderDigestIssueBatchLimit(Integer value) {
        int normalized = value != null ? value : 50;
        if (normalized < 1 || normalized > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Owner reminder digest issue batch limit must be between 1 and 100"
            );
        }
        return normalized;
    }

    private int normalizeAlertReminderBatchLimit(Integer value) {
        int normalized = value != null ? value : 25;
        if (normalized < 1 || normalized > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reminder batch limit must be between 1 and 100");
        }
        return normalized;
    }

    private String csvValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private record EffectiveAlertPolicy(
            int alertThreshold,
            int highSeverityThreshold,
            int alertSuppressionMinutes,
            int acknowledgeSlaMinutes,
            int resolveSlaMinutes,
            int reminderIntervalMinutes,
            int severityUpgradeAfterMinutes,
            int breachEscalationAfterMinutes,
            int highSeverityAcknowledgeSlaMinutes,
            int highSeverityResolveSlaMinutes,
            int highSeverityReminderIntervalMinutes,
            int triageDelayMinutes,
            int triageReminderIntervalMinutes,
            int triageEscalationAfterMinutes,
            boolean autoDigestEnabled,
            boolean autoTriageEnabled,
            boolean triageAutoAssignEnabled,
            String claimNextStrategy,
            boolean claimNextTriageOnlyDefault,
            UUID alertTargetChatId,
            UUID reminderTargetChatId,
            UUID personalReminderTargetChatId,
            UUID breachTargetChatId,
            UUID defaultOwnerUserId,
            UUID triageFallbackOwnerUserId,
            UUID triageTargetChatId,
            UUID triageEscalationTargetChatId,
            UUID digestTargetChatId,
            UUID personalReminderDigestTargetChatId
    ) {
    }
}
