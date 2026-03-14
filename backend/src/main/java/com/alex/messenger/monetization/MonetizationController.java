package com.alex.messenger.monetization;

import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.monetization.dto.ChannelMonetizationReportResponse;
import com.alex.messenger.monetization.dto.ChannelMonetizationStatsResponse;
import com.alex.messenger.monetization.dto.AssignMonetizationArtifactSubscriptionAlertRequest;
import com.alex.messenger.monetization.dto.CreateMonetizationArtifactAlertCommentRequest;
import com.alex.messenger.monetization.dto.CreateMonetizationOwnerReminderDigestSubscriptionRequest;
import com.alex.messenger.monetization.dto.CreateSponsoredMessageRequest;
import com.alex.messenger.monetization.dto.CreateMonetizationWithdrawalRequest;
import com.alex.messenger.monetization.dto.CreateMonetizationArtifactSubscriptionRequest;
import com.alex.messenger.monetization.dto.GenerateMonetizationAlertDigestRequest;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertAuditEventResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertCommentResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertReminderResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertReminderBatchResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertReminderDigestResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertSummaryResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertTriageReminderResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertTriageResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertWorkloadOwnerResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactAlertWorkloadResponse;
import com.alex.messenger.monetization.dto.MonetizationAlertDigestRunResponse;
import com.alex.messenger.monetization.dto.MonetizationAlertPolicyResponse;
import com.alex.messenger.monetization.dto.MonetizationExportArtifactResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactPublicationResponse;
import com.alex.messenger.monetization.dto.MonetizationClaimableAlertWorkloadResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactSubscriptionAlertResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactSubscriptionFailureResponse;
import com.alex.messenger.monetization.dto.MonetizationArtifactSubscriptionResponse;
import com.alex.messenger.monetization.dto.MonetizationOwnerReminderDigestSubscriptionResponse;
import com.alex.messenger.monetization.dto.MonetizationOwnerReminderDigestRunResponse;
import com.alex.messenger.monetization.dto.MonetizationOwnerReminderDigestIssueSummaryResponse;
import com.alex.messenger.monetization.dto.MonetizationOwnerReminderDigestIssueActionResponse;
import com.alex.messenger.monetization.dto.MonetizationPayoutExportResponse;
import com.alex.messenger.monetization.dto.MonetizationPayoutResponse;
import com.alex.messenger.monetization.dto.MonetizationProviderSyncRunResponse;
import com.alex.messenger.monetization.dto.MonetizationReconciliationRunResponse;
import com.alex.messenger.monetization.dto.PublishMonetizationArtifactRequest;
import com.alex.messenger.monetization.dto.SnoozeMonetizationArtifactSubscriptionAlertRequest;
import com.alex.messenger.monetization.dto.SponsoredMessageDeliveryResponse;
import com.alex.messenger.monetization.dto.SponsoredMessageResponse;
import com.alex.messenger.monetization.dto.UpdateMonetizationAlertPolicyRequest;
import com.alex.messenger.monetization.dto.MonetizationWithdrawalProviderCallbackResponse;
import com.alex.messenger.monetization.dto.MonetizationWithdrawalResponse;
import com.alex.messenger.shared.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monetization")
@RequiredArgsConstructor
public class MonetizationController {

    private final FeatureFlagService featureFlagService;
    private final MonetizationService monetizationService;

    @GetMapping("/channels/{chatId}/sponsored-messages")
    public ResponseEntity<List<SponsoredMessageResponse>> sponsoredMessages(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listSponsoredMessages(CurrentUser.id(), chatId));
    }

    @PostMapping("/channels/{chatId}/sponsored-messages")
    public ResponseEntity<SponsoredMessageResponse> createSponsoredMessage(
            @PathVariable UUID chatId,
            @Valid @RequestBody CreateSponsoredMessageRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.createSponsoredMessage(CurrentUser.id(), chatId, request));
    }

    @PostMapping("/channels/{chatId}/sponsored-messages/{sponsoredMessageId}/publish")
    public ResponseEntity<SponsoredMessageResponse> publishSponsoredMessage(
            @PathVariable UUID chatId,
            @PathVariable UUID sponsoredMessageId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.publishSponsoredMessage(CurrentUser.id(), chatId, sponsoredMessageId));
    }

    @PostMapping("/channels/{chatId}/sponsored-messages/{sponsoredMessageId}/pause")
    public ResponseEntity<SponsoredMessageResponse> pauseSponsoredMessage(
            @PathVariable UUID chatId,
            @PathVariable UUID sponsoredMessageId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.pauseSponsoredMessage(CurrentUser.id(), chatId, sponsoredMessageId));
    }

    @PostMapping("/channels/{chatId}/sponsored-messages/{sponsoredMessageId}/resume")
    public ResponseEntity<SponsoredMessageResponse> resumeSponsoredMessage(
            @PathVariable UUID chatId,
            @PathVariable UUID sponsoredMessageId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.resumeSponsoredMessage(CurrentUser.id(), chatId, sponsoredMessageId));
    }

    @PostMapping("/channels/{chatId}/sponsored-messages/{sponsoredMessageId}/cancel")
    public ResponseEntity<SponsoredMessageResponse> cancelSponsoredMessage(
            @PathVariable UUID chatId,
            @PathVariable UUID sponsoredMessageId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.cancelSponsoredMessage(CurrentUser.id(), chatId, sponsoredMessageId));
    }

    @PostMapping("/channels/{chatId}/sponsored-messages/{sponsoredMessageId}/impression")
    public ResponseEntity<SponsoredMessageResponse> recordImpression(
            @PathVariable UUID chatId,
            @PathVariable UUID sponsoredMessageId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.recordImpression(CurrentUser.id(), chatId, sponsoredMessageId));
    }

    @PostMapping("/channels/{chatId}/sponsored-messages/{sponsoredMessageId}/click")
    public ResponseEntity<SponsoredMessageResponse> recordClick(
            @PathVariable UUID chatId,
            @PathVariable UUID sponsoredMessageId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.recordClick(CurrentUser.id(), chatId, sponsoredMessageId));
    }

    @GetMapping("/channels/{chatId}/delivery")
    public ResponseEntity<SponsoredMessageDeliveryResponse> deliverSponsoredMessage(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.deliverSponsoredMessage(CurrentUser.id(), chatId));
    }

    @GetMapping("/channels/{chatId}/payouts")
    public ResponseEntity<List<MonetizationPayoutResponse>> payoutHistory(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listPayouts(CurrentUser.id(), chatId));
    }

    @PostMapping("/channels/{chatId}/payouts/run")
    public ResponseEntity<MonetizationPayoutResponse> runPayout(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.runPayout(CurrentUser.id(), chatId, 100));
    }

    @GetMapping("/channels/{chatId}/payouts/export")
    public ResponseEntity<MonetizationPayoutExportResponse> exportPayouts(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.exportPayouts(CurrentUser.id(), chatId));
    }

    @GetMapping("/channels/{chatId}/withdrawals/export")
    public ResponseEntity<MonetizationExportArtifactResponse> exportWithdrawals(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.exportWithdrawals(CurrentUser.id(), chatId));
    }

    @GetMapping("/channels/{chatId}/report/export")
    public ResponseEntity<MonetizationExportArtifactResponse> exportReport(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.exportReport(CurrentUser.id(), chatId));
    }

    @GetMapping("/channels/{chatId}/artifacts")
    public ResponseEntity<List<MonetizationExportArtifactResponse>> artifacts(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listArtifacts(CurrentUser.id(), chatId));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions")
    public ResponseEntity<List<MonetizationArtifactSubscriptionResponse>> artifactSubscriptions(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listArtifactSubscriptions(CurrentUser.id(), chatId));
    }

    @GetMapping("/channels/{chatId}/alert-policy")
    public ResponseEntity<MonetizationAlertPolicyResponse> alertPolicy(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.getAlertPolicy(CurrentUser.id(), chatId));
    }

    @PutMapping("/channels/{chatId}/alert-policy")
    public ResponseEntity<MonetizationAlertPolicyResponse> updateAlertPolicy(
            @PathVariable UUID chatId,
            @RequestBody UpdateMonetizationAlertPolicyRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.updateAlertPolicy(CurrentUser.id(), chatId, request));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions")
    public ResponseEntity<MonetizationArtifactSubscriptionResponse> createArtifactSubscription(
            @PathVariable UUID chatId,
            @Valid @RequestBody CreateMonetizationArtifactSubscriptionRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.createArtifactSubscription(CurrentUser.id(), chatId, request));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/{subscriptionId}/pause")
    public ResponseEntity<MonetizationArtifactSubscriptionResponse> pauseArtifactSubscription(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.pauseArtifactSubscription(CurrentUser.id(), chatId, subscriptionId));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/{subscriptionId}/resume")
    public ResponseEntity<MonetizationArtifactSubscriptionResponse> resumeArtifactSubscription(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.resumeArtifactSubscription(CurrentUser.id(), chatId, subscriptionId));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/{subscriptionId}/failures")
    public ResponseEntity<List<MonetizationArtifactSubscriptionFailureResponse>> artifactSubscriptionFailures(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listArtifactSubscriptionFailures(CurrentUser.id(), chatId, subscriptionId));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/{subscriptionId}/alerts")
    public ResponseEntity<List<MonetizationArtifactSubscriptionAlertResponse>> artifactSubscriptionAlerts(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listArtifactSubscriptionAlerts(CurrentUser.id(), chatId, subscriptionId));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/{subscriptionId}/alerts/{alertId}/comments")
    public ResponseEntity<List<MonetizationArtifactAlertCommentResponse>> artifactSubscriptionAlertComments(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId,
            @PathVariable UUID alertId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listArtifactSubscriptionAlertComments(
                CurrentUser.id(),
                chatId,
                subscriptionId,
                alertId
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/{subscriptionId}/alerts/{alertId}/comments")
    public ResponseEntity<MonetizationArtifactAlertCommentResponse> addArtifactSubscriptionAlertComment(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId,
            @PathVariable UUID alertId,
            @RequestBody CreateMonetizationArtifactAlertCommentRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.addArtifactSubscriptionAlertComment(
                CurrentUser.id(),
                chatId,
                subscriptionId,
                alertId,
                request
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/{subscriptionId}/alerts/{alertId}/timeline")
    public ResponseEntity<List<MonetizationArtifactAlertAuditEventResponse>> artifactSubscriptionAlertTimeline(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId,
            @PathVariable UUID alertId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listArtifactSubscriptionAlertTimeline(
                CurrentUser.id(),
                chatId,
                subscriptionId,
                alertId
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/summary")
    public ResponseEntity<MonetizationArtifactAlertSummaryResponse> artifactAlertSummary(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.getArtifactAlertSummary(CurrentUser.id(), chatId));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/overdue")
    public ResponseEntity<List<MonetizationArtifactSubscriptionAlertResponse>> overdueArtifactAlerts(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listOverdueArtifactSubscriptionAlerts(CurrentUser.id(), chatId));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/breached")
    public ResponseEntity<List<MonetizationArtifactSubscriptionAlertResponse>> breachedArtifactAlerts(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listBreachedArtifactSubscriptionAlerts(CurrentUser.id(), chatId));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/triage")
    public ResponseEntity<List<MonetizationArtifactSubscriptionAlertResponse>> triageArtifactAlerts(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listTriageArtifactSubscriptionAlerts(CurrentUser.id(), chatId));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/triage-overdue")
    public ResponseEntity<List<MonetizationArtifactSubscriptionAlertResponse>> overdueTriageArtifactAlerts(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listOverdueTriageArtifactSubscriptionAlerts(CurrentUser.id(), chatId));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/queue")
    public ResponseEntity<List<MonetizationArtifactSubscriptionAlertResponse>> artifactAlertQueue(
            @PathVariable UUID chatId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(required = false) Boolean breachedOnly,
            @RequestParam(required = false) Boolean overdueOnly
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listArtifactSubscriptionAlertQueue(
                CurrentUser.id(),
                chatId,
                severity,
                status,
                ownerUserId,
                breachedOnly,
                overdueOnly
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/workload")
    public ResponseEntity<MonetizationArtifactAlertWorkloadResponse> artifactAlertWorkload(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.getArtifactAlertWorkload(CurrentUser.id(), chatId));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/workload/export")
    public ResponseEntity<MonetizationExportArtifactResponse> exportArtifactAlertWorkload(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.exportArtifactAlertWorkload(CurrentUser.id(), chatId));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/owners/{ownerUserId}/queue")
    public ResponseEntity<List<MonetizationArtifactSubscriptionAlertResponse>> ownerArtifactAlertQueue(
            @PathVariable UUID chatId,
            @PathVariable UUID ownerUserId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean breachedOnly,
            @RequestParam(required = false) Boolean overdueOnly
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listOwnerArtifactSubscriptionAlertQueue(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                severity,
                status,
                breachedOnly,
                overdueOnly
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-queue")
    public ResponseEntity<List<MonetizationArtifactSubscriptionAlertResponse>> myArtifactAlertQueue(
            @PathVariable UUID chatId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean breachedOnly,
            @RequestParam(required = false) Boolean overdueOnly
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listMyArtifactSubscriptionAlertQueue(
                CurrentUser.id(),
                chatId,
                severity,
                status,
                breachedOnly,
                overdueOnly
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/claimable")
    public ResponseEntity<List<MonetizationArtifactSubscriptionAlertResponse>> claimableArtifactAlertQueue(
            @PathVariable UUID chatId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean triageOnly,
            @RequestParam(required = false) Boolean breachedOnly,
            @RequestParam(required = false) Boolean overdueOnly,
            @RequestParam(required = false) String strategy
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listClaimableArtifactSubscriptionAlertQueue(
                CurrentUser.id(),
                chatId,
                severity,
                status,
                triageOnly,
                breachedOnly,
                overdueOnly,
                strategy
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/claimable/workload")
    public ResponseEntity<MonetizationClaimableAlertWorkloadResponse> claimableArtifactAlertWorkload(
            @PathVariable UUID chatId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean triageOnly,
            @RequestParam(required = false) Boolean breachedOnly,
            @RequestParam(required = false) Boolean overdueOnly,
            @RequestParam(required = false) String strategy
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.getClaimableArtifactAlertWorkload(
                CurrentUser.id(),
                chatId,
                severity,
                status,
                triageOnly,
                breachedOnly,
                overdueOnly,
                strategy
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/claimable/next")
    public ResponseEntity<MonetizationArtifactSubscriptionAlertResponse> nextClaimableArtifactAlert(
            @PathVariable UUID chatId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean triageOnly,
            @RequestParam(required = false) Boolean breachedOnly,
            @RequestParam(required = false) Boolean overdueOnly,
            @RequestParam(required = false) String strategy
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.peekNextClaimableArtifactSubscriptionAlert(
                CurrentUser.id(),
                chatId,
                severity,
                status,
                triageOnly,
                breachedOnly,
                overdueOnly,
                strategy
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/owners/{ownerUserId}/workload")
    public ResponseEntity<MonetizationArtifactAlertWorkloadOwnerResponse> ownerArtifactAlertWorkload(
            @PathVariable UUID chatId,
            @PathVariable UUID ownerUserId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.getOwnerArtifactAlertWorkload(CurrentUser.id(), chatId, ownerUserId));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/owners/{ownerUserId}/next")
    public ResponseEntity<MonetizationArtifactSubscriptionAlertResponse> nextOwnerArtifactAlert(
            @PathVariable UUID chatId,
            @PathVariable UUID ownerUserId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.peekOwnerArtifactSubscriptionAlert(CurrentUser.id(), chatId, ownerUserId));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-workload")
    public ResponseEntity<MonetizationArtifactAlertWorkloadOwnerResponse> myArtifactAlertWorkload(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.getMyArtifactAlertWorkload(CurrentUser.id(), chatId));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-next")
    public ResponseEntity<MonetizationArtifactSubscriptionAlertResponse> nextMyArtifactAlert(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.peekMyArtifactSubscriptionAlert(CurrentUser.id(), chatId));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/owners/{ownerUserId}/reminder-queue")
    public ResponseEntity<List<MonetizationArtifactSubscriptionAlertResponse>> ownerArtifactAlertReminderQueue(
            @PathVariable UUID chatId,
            @PathVariable UUID ownerUserId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean breachedOnly
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listOwnerDueArtifactAlertReminderQueue(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                severity,
                breachedOnly
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-reminder-queue")
    public ResponseEntity<List<MonetizationArtifactSubscriptionAlertResponse>> myArtifactAlertReminderQueue(
            @PathVariable UUID chatId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean breachedOnly
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listMyDueArtifactAlertReminderQueue(
                CurrentUser.id(),
                chatId,
                severity,
                breachedOnly
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/owners/{ownerUserId}/reminder-digest")
    public ResponseEntity<MonetizationArtifactAlertReminderDigestResponse> ownerArtifactAlertReminderDigest(
            @PathVariable UUID chatId,
            @PathVariable UUID ownerUserId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean breachedOnly
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.getOwnerArtifactAlertReminderDigest(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                severity,
                breachedOnly
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-reminder-digest")
    public ResponseEntity<MonetizationArtifactAlertReminderDigestResponse> myArtifactAlertReminderDigest(
            @PathVariable UUID chatId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean breachedOnly
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.getMyArtifactAlertReminderDigest(
                CurrentUser.id(),
                chatId,
                severity,
                breachedOnly
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/owners/{ownerUserId}/reminder-digest-subscriptions")
    public ResponseEntity<List<MonetizationOwnerReminderDigestSubscriptionResponse>> ownerArtifactAlertReminderDigestSubscriptions(
            @PathVariable UUID chatId,
            @PathVariable UUID ownerUserId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listOwnerArtifactAlertReminderDigestSubscriptions(
                CurrentUser.id(),
                chatId,
                ownerUserId
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-reminder-digest-subscriptions")
    public ResponseEntity<List<MonetizationOwnerReminderDigestSubscriptionResponse>> myArtifactAlertReminderDigestSubscriptions(
            @PathVariable UUID chatId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listMyArtifactAlertReminderDigestSubscriptions(
                CurrentUser.id(),
                chatId
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/reminder-digest-subscriptions/issues")
    public ResponseEntity<List<MonetizationOwnerReminderDigestSubscriptionResponse>> artifactAlertReminderDigestSubscriptionIssues(
            @PathVariable UUID chatId,
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(required = false) String failureState,
            @RequestParam(required = false) Boolean retryDueOnly
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listArtifactAlertReminderDigestSubscriptionIssues(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                failureState,
                retryDueOnly
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-reminder-digest-subscriptions/issues")
    public ResponseEntity<List<MonetizationOwnerReminderDigestSubscriptionResponse>> myArtifactAlertReminderDigestSubscriptionIssues(
            @PathVariable UUID chatId,
            @RequestParam(required = false) String failureState,
            @RequestParam(required = false) Boolean retryDueOnly
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listMyArtifactAlertReminderDigestSubscriptionIssues(
                CurrentUser.id(),
                chatId,
                failureState,
                retryDueOnly
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/reminder-digest-subscriptions/issues/summary")
    public ResponseEntity<MonetizationOwnerReminderDigestIssueSummaryResponse> artifactAlertReminderDigestSubscriptionIssueSummary(
            @PathVariable UUID chatId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.getArtifactAlertReminderDigestSubscriptionIssueSummary(
                CurrentUser.id(),
                chatId
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/reminder-digest-subscriptions/issues/summary/export")
    public ResponseEntity<MonetizationExportArtifactResponse> exportArtifactAlertReminderDigestSubscriptionIssueSummary(
            @PathVariable UUID chatId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.exportArtifactAlertReminderDigestSubscriptionIssueSummary(
                CurrentUser.id(),
                chatId
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/reminder-digest-subscriptions/issues/export")
    public ResponseEntity<MonetizationExportArtifactResponse> exportArtifactAlertReminderDigestSubscriptionIssues(
            @PathVariable UUID chatId,
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(required = false) String failureState,
            @RequestParam(required = false) Boolean retryDueOnly
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.exportArtifactAlertReminderDigestSubscriptionIssues(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                failureState,
                retryDueOnly
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-reminder-digest-subscriptions/issues/export")
    public ResponseEntity<MonetizationExportArtifactResponse> exportMyArtifactAlertReminderDigestSubscriptionIssues(
            @PathVariable UUID chatId,
            @RequestParam(required = false) String failureState,
            @RequestParam(required = false) Boolean retryDueOnly
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.exportMyArtifactAlertReminderDigestSubscriptionIssues(
                CurrentUser.id(),
                chatId,
                failureState,
                retryDueOnly
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/owners/{ownerUserId}/reminder-digest-subscriptions")
    public ResponseEntity<MonetizationOwnerReminderDigestSubscriptionResponse> createOwnerArtifactAlertReminderDigestSubscription(
            @PathVariable UUID chatId,
            @PathVariable UUID ownerUserId,
            @Valid @RequestBody CreateMonetizationOwnerReminderDigestSubscriptionRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.createOwnerArtifactAlertReminderDigestSubscription(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                request
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-reminder-digest-subscriptions")
    public ResponseEntity<MonetizationOwnerReminderDigestSubscriptionResponse> createMyArtifactAlertReminderDigestSubscription(
            @PathVariable UUID chatId,
            @Valid @RequestBody CreateMonetizationOwnerReminderDigestSubscriptionRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.createMyArtifactAlertReminderDigestSubscription(
                CurrentUser.id(),
                chatId,
                request
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/owners/{ownerUserId}/reminder-digest-subscriptions/{subscriptionId}/runs")
    public ResponseEntity<List<MonetizationOwnerReminderDigestRunResponse>> ownerArtifactAlertReminderDigestSubscriptionRuns(
            @PathVariable UUID chatId,
            @PathVariable UUID ownerUserId,
            @PathVariable UUID subscriptionId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listOwnerArtifactAlertReminderDigestSubscriptionRuns(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                subscriptionId
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-reminder-digest-subscriptions/{subscriptionId}/runs")
    public ResponseEntity<List<MonetizationOwnerReminderDigestRunResponse>> myArtifactAlertReminderDigestSubscriptionRuns(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listMyArtifactAlertReminderDigestSubscriptionRuns(
                CurrentUser.id(),
                chatId,
                subscriptionId
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/owners/{ownerUserId}/reminder-queue/export")
    public ResponseEntity<MonetizationExportArtifactResponse> exportOwnerArtifactAlertReminderQueue(
            @PathVariable UUID chatId,
            @PathVariable UUID ownerUserId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean breachedOnly
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.exportOwnerArtifactAlertReminderQueue(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                severity,
                breachedOnly
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/owners/{ownerUserId}/reminder-digest/export")
    public ResponseEntity<MonetizationExportArtifactResponse> exportOwnerArtifactAlertReminderDigest(
            @PathVariable UUID chatId,
            @PathVariable UUID ownerUserId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean breachedOnly
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.exportOwnerArtifactAlertReminderDigest(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                severity,
                breachedOnly
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-reminder-digest/export")
    public ResponseEntity<MonetizationExportArtifactResponse> exportMyArtifactAlertReminderDigest(
            @PathVariable UUID chatId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean breachedOnly
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.exportMyArtifactAlertReminderDigest(
                CurrentUser.id(),
                chatId,
                severity,
                breachedOnly
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-reminder-queue/export")
    public ResponseEntity<MonetizationExportArtifactResponse> exportMyArtifactAlertReminderQueue(
            @PathVariable UUID chatId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean breachedOnly
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.exportMyArtifactAlertReminderQueue(
                CurrentUser.id(),
                chatId,
                severity,
                breachedOnly
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/owners/{ownerUserId}/workload/export")
    public ResponseEntity<MonetizationExportArtifactResponse> exportOwnerArtifactAlertWorkload(
            @PathVariable UUID chatId,
            @PathVariable UUID ownerUserId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.exportOwnerArtifactAlertWorkload(CurrentUser.id(), chatId, ownerUserId));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-workload/export")
    public ResponseEntity<MonetizationExportArtifactResponse> exportMyArtifactAlertWorkload(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.exportMyArtifactAlertWorkload(CurrentUser.id(), chatId));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/owners/{ownerUserId}/workload/publish")
    public ResponseEntity<MonetizationArtifactPublicationResponse> publishOwnerArtifactAlertWorkload(
            @PathVariable UUID chatId,
            @PathVariable UUID ownerUserId,
            @Valid @RequestBody PublishMonetizationArtifactRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.publishOwnerArtifactAlertWorkload(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                request
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-workload/publish")
    public ResponseEntity<MonetizationArtifactPublicationResponse> publishMyArtifactAlertWorkload(
            @PathVariable UUID chatId,
            @Valid @RequestBody PublishMonetizationArtifactRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.publishMyArtifactAlertWorkload(
                CurrentUser.id(),
                chatId,
                request
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/{subscriptionId}/alerts/{alertId}/acknowledge")
    public ResponseEntity<MonetizationArtifactSubscriptionAlertResponse> acknowledgeArtifactSubscriptionAlert(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId,
            @PathVariable UUID alertId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.acknowledgeArtifactSubscriptionAlert(
                CurrentUser.id(),
                chatId,
                subscriptionId,
                alertId
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/{subscriptionId}/alerts/{alertId}/assign")
    public ResponseEntity<MonetizationArtifactSubscriptionAlertResponse> assignArtifactSubscriptionAlert(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId,
            @PathVariable UUID alertId,
            @RequestBody(required = false) AssignMonetizationArtifactSubscriptionAlertRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.assignArtifactSubscriptionAlert(
                CurrentUser.id(),
                chatId,
                subscriptionId,
                alertId,
                request
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/{subscriptionId}/alerts/{alertId}/claim")
    public ResponseEntity<MonetizationArtifactSubscriptionAlertResponse> claimArtifactSubscriptionAlert(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId,
            @PathVariable UUID alertId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.claimArtifactSubscriptionAlert(
                CurrentUser.id(),
                chatId,
                subscriptionId,
                alertId
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/claim-next")
    public ResponseEntity<MonetizationArtifactSubscriptionAlertResponse> claimNextArtifactSubscriptionAlert(
            @PathVariable UUID chatId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean triageOnly,
            @RequestParam(required = false) Boolean breachedOnly,
            @RequestParam(required = false) Boolean overdueOnly,
            @RequestParam(required = false) String strategy
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.claimNextArtifactSubscriptionAlert(
                CurrentUser.id(),
                chatId,
                severity,
                status,
                triageOnly,
                breachedOnly,
                overdueOnly,
                strategy
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/{subscriptionId}/alerts/{alertId}/release")
    public ResponseEntity<MonetizationArtifactSubscriptionAlertResponse> releaseArtifactSubscriptionAlert(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId,
            @PathVariable UUID alertId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.releaseArtifactSubscriptionAlert(
                CurrentUser.id(),
                chatId,
                subscriptionId,
                alertId
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/{subscriptionId}/alerts/{alertId}/snooze")
    public ResponseEntity<MonetizationArtifactSubscriptionAlertResponse> snoozeArtifactSubscriptionAlert(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId,
            @PathVariable UUID alertId,
            @RequestBody(required = false) SnoozeMonetizationArtifactSubscriptionAlertRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.snoozeArtifactSubscriptionAlert(
                CurrentUser.id(),
                chatId,
                subscriptionId,
                alertId,
                request
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/{subscriptionId}/alerts/{alertId}/remind")
    public ResponseEntity<MonetizationArtifactAlertReminderResponse> remindArtifactSubscriptionAlert(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId,
            @PathVariable UUID alertId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.remindArtifactSubscriptionAlert(
                CurrentUser.id(),
                chatId,
                subscriptionId,
                alertId
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/owners/{ownerUserId}/remind-due")
    public ResponseEntity<MonetizationArtifactAlertReminderBatchResponse> remindOwnerDueArtifactAlerts(
            @PathVariable UUID chatId,
            @PathVariable UUID ownerUserId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean breachedOnly,
            @RequestParam(required = false) Integer limit
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.remindOwnerDueArtifactAlerts(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                severity,
                breachedOnly,
                limit
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-remind-due")
    public ResponseEntity<MonetizationArtifactAlertReminderBatchResponse> remindMyDueArtifactAlerts(
            @PathVariable UUID chatId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean breachedOnly,
            @RequestParam(required = false) Integer limit
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.remindMyDueArtifactAlerts(
                CurrentUser.id(),
                chatId,
                severity,
                breachedOnly,
                limit
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/owners/{ownerUserId}/reminder-digest-subscriptions/{subscriptionId}/pause")
    public ResponseEntity<MonetizationOwnerReminderDigestSubscriptionResponse> pauseOwnerArtifactAlertReminderDigestSubscription(
            @PathVariable UUID chatId,
            @PathVariable UUID ownerUserId,
            @PathVariable UUID subscriptionId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.pauseOwnerArtifactAlertReminderDigestSubscription(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                subscriptionId
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-reminder-digest-subscriptions/{subscriptionId}/pause")
    public ResponseEntity<MonetizationOwnerReminderDigestSubscriptionResponse> pauseMyArtifactAlertReminderDigestSubscription(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.pauseMyArtifactAlertReminderDigestSubscription(
                CurrentUser.id(),
                chatId,
                subscriptionId
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/owners/{ownerUserId}/reminder-digest-subscriptions/{subscriptionId}/resume")
    public ResponseEntity<MonetizationOwnerReminderDigestSubscriptionResponse> resumeOwnerArtifactAlertReminderDigestSubscription(
            @PathVariable UUID chatId,
            @PathVariable UUID ownerUserId,
            @PathVariable UUID subscriptionId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.resumeOwnerArtifactAlertReminderDigestSubscription(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                subscriptionId
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-reminder-digest-subscriptions/{subscriptionId}/resume")
    public ResponseEntity<MonetizationOwnerReminderDigestSubscriptionResponse> resumeMyArtifactAlertReminderDigestSubscription(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.resumeMyArtifactAlertReminderDigestSubscription(
                CurrentUser.id(),
                chatId,
                subscriptionId
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/reminder-digest-subscriptions/issues/resume")
    public ResponseEntity<MonetizationOwnerReminderDigestIssueActionResponse> resumeArtifactAlertReminderDigestSubscriptionIssues(
            @PathVariable UUID chatId,
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(required = false) String failureState,
            @RequestParam(required = false) Boolean retryDueOnly,
            @RequestParam(required = false) Integer limit
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.resumeArtifactAlertReminderDigestSubscriptionIssues(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                failureState,
                retryDueOnly,
                limit
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-reminder-digest-subscriptions/issues/resume")
    public ResponseEntity<MonetizationOwnerReminderDigestIssueActionResponse> resumeMyArtifactAlertReminderDigestSubscriptionIssues(
            @PathVariable UUID chatId,
            @RequestParam(required = false) String failureState,
            @RequestParam(required = false) Boolean retryDueOnly,
            @RequestParam(required = false) Integer limit
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.resumeMyArtifactAlertReminderDigestSubscriptionIssues(
                CurrentUser.id(),
                chatId,
                failureState,
                retryDueOnly,
                limit
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/reminder-digest-subscriptions/issues/retry")
    public ResponseEntity<MonetizationOwnerReminderDigestIssueActionResponse> retryArtifactAlertReminderDigestSubscriptionIssues(
            @PathVariable UUID chatId,
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(required = false) Boolean retryDueOnly,
            @RequestParam(required = false) Integer limit
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.retryArtifactAlertReminderDigestSubscriptionIssues(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                retryDueOnly,
                limit
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-reminder-digest-subscriptions/issues/retry")
    public ResponseEntity<MonetizationOwnerReminderDigestIssueActionResponse> retryMyArtifactAlertReminderDigestSubscriptionIssues(
            @PathVariable UUID chatId,
            @RequestParam(required = false) Boolean retryDueOnly,
            @RequestParam(required = false) Integer limit
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.retryMyArtifactAlertReminderDigestSubscriptionIssues(
                CurrentUser.id(),
                chatId,
                retryDueOnly,
                limit
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/owners/{ownerUserId}/reminder-digest-subscriptions/{subscriptionId}/dispatch")
    public ResponseEntity<MonetizationOwnerReminderDigestRunResponse> dispatchOwnerArtifactAlertReminderDigestSubscription(
            @PathVariable UUID chatId,
            @PathVariable UUID ownerUserId,
            @PathVariable UUID subscriptionId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.dispatchOwnerArtifactAlertReminderDigestSubscription(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                subscriptionId
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-reminder-digest-subscriptions/{subscriptionId}/dispatch")
    public ResponseEntity<MonetizationOwnerReminderDigestRunResponse> dispatchMyArtifactAlertReminderDigestSubscription(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.dispatchMyArtifactAlertReminderDigestSubscription(
                CurrentUser.id(),
                chatId,
                subscriptionId
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/owners/{ownerUserId}/reminder-queue/publish")
    public ResponseEntity<MonetizationArtifactPublicationResponse> publishOwnerArtifactAlertReminderQueue(
            @PathVariable UUID chatId,
            @PathVariable UUID ownerUserId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean breachedOnly,
            @Valid @RequestBody(required = false) PublishMonetizationArtifactRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.publishOwnerArtifactAlertReminderQueue(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                severity,
                breachedOnly,
                request
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-reminder-queue/publish")
    public ResponseEntity<MonetizationArtifactPublicationResponse> publishMyArtifactAlertReminderQueue(
            @PathVariable UUID chatId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean breachedOnly,
            @Valid @RequestBody(required = false) PublishMonetizationArtifactRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.publishMyArtifactAlertReminderQueue(
                CurrentUser.id(),
                chatId,
                severity,
                breachedOnly,
                request
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/owners/{ownerUserId}/reminder-digest/publish")
    public ResponseEntity<MonetizationArtifactPublicationResponse> publishOwnerArtifactAlertReminderDigest(
            @PathVariable UUID chatId,
            @PathVariable UUID ownerUserId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean breachedOnly,
            @Valid @RequestBody(required = false) PublishMonetizationArtifactRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.publishOwnerArtifactAlertReminderDigest(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                severity,
                breachedOnly,
                request
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-reminder-digest/publish")
    public ResponseEntity<MonetizationArtifactPublicationResponse> publishMyArtifactAlertReminderDigest(
            @PathVariable UUID chatId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean breachedOnly,
            @Valid @RequestBody(required = false) PublishMonetizationArtifactRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.publishMyArtifactAlertReminderDigest(
                CurrentUser.id(),
                chatId,
                severity,
                breachedOnly,
                request
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/reminder-digest-subscriptions/issues/summary/publish")
    public ResponseEntity<MonetizationArtifactPublicationResponse> publishArtifactAlertReminderDigestSubscriptionIssueSummary(
            @PathVariable UUID chatId,
            @Valid @RequestBody(required = false) PublishMonetizationArtifactRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.publishArtifactAlertReminderDigestSubscriptionIssueSummary(
                CurrentUser.id(),
                chatId,
                request
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/reminder-digest-subscriptions/issues/publish")
    public ResponseEntity<MonetizationArtifactPublicationResponse> publishArtifactAlertReminderDigestSubscriptionIssues(
            @PathVariable UUID chatId,
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(required = false) String failureState,
            @RequestParam(required = false) Boolean retryDueOnly,
            @Valid @RequestBody(required = false) PublishMonetizationArtifactRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.publishArtifactAlertReminderDigestSubscriptionIssues(
                CurrentUser.id(),
                chatId,
                ownerUserId,
                failureState,
                retryDueOnly,
                request
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alerts/my-reminder-digest-subscriptions/issues/publish")
    public ResponseEntity<MonetizationArtifactPublicationResponse> publishMyArtifactAlertReminderDigestSubscriptionIssues(
            @PathVariable UUID chatId,
            @RequestParam(required = false) String failureState,
            @RequestParam(required = false) Boolean retryDueOnly,
            @Valid @RequestBody(required = false) PublishMonetizationArtifactRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.publishMyArtifactAlertReminderDigestSubscriptionIssues(
                CurrentUser.id(),
                chatId,
                failureState,
                retryDueOnly,
                request
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/{subscriptionId}/alerts/{alertId}/triage")
    public ResponseEntity<MonetizationArtifactAlertTriageResponse> triageArtifactSubscriptionAlert(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId,
            @PathVariable UUID alertId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.triageArtifactSubscriptionAlert(
                CurrentUser.id(),
                chatId,
                subscriptionId,
                alertId
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/{subscriptionId}/alerts/{alertId}/triage/remind")
    public ResponseEntity<MonetizationArtifactAlertTriageReminderResponse> remindTriageArtifactSubscriptionAlert(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId,
            @PathVariable UUID alertId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.remindTriageArtifactSubscriptionAlert(
                CurrentUser.id(),
                chatId,
                subscriptionId,
                alertId
        ));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/{subscriptionId}/alerts/{alertId}/resolve")
    public ResponseEntity<MonetizationArtifactSubscriptionAlertResponse> resolveArtifactSubscriptionAlert(
            @PathVariable UUID chatId,
            @PathVariable UUID subscriptionId,
            @PathVariable UUID alertId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.resolveArtifactSubscriptionAlert(
                CurrentUser.id(),
                chatId,
                subscriptionId,
                alertId
        ));
    }

    @GetMapping("/channels/{chatId}/artifact-subscriptions/alert-digests")
    public ResponseEntity<List<MonetizationAlertDigestRunResponse>> alertDigestRuns(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listAlertDigestRuns(CurrentUser.id(), chatId));
    }

    @PostMapping("/channels/{chatId}/artifact-subscriptions/alert-digest")
    public ResponseEntity<MonetizationAlertDigestRunResponse> generateAlertDigest(
            @PathVariable UUID chatId,
            @RequestBody(required = false) GenerateMonetizationAlertDigestRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.generateAlertDigest(CurrentUser.id(), chatId, request));
    }

    @GetMapping("/channels/{chatId}/artifacts/{artifactId}")
    public ResponseEntity<MonetizationExportArtifactResponse> artifact(
            @PathVariable UUID chatId,
            @PathVariable UUID artifactId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.getArtifact(CurrentUser.id(), chatId, artifactId));
    }

    @GetMapping("/channels/{chatId}/provider-sync-runs")
    public ResponseEntity<List<MonetizationProviderSyncRunResponse>> providerSyncRuns(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listProviderSyncRuns(CurrentUser.id(), chatId));
    }

    @GetMapping("/channels/{chatId}/artifacts/{artifactId}/publications")
    public ResponseEntity<List<MonetizationArtifactPublicationResponse>> artifactPublications(
            @PathVariable UUID chatId,
            @PathVariable UUID artifactId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listArtifactPublications(CurrentUser.id(), chatId, artifactId));
    }

    @PostMapping("/channels/{chatId}/artifacts/{artifactId}/publish")
    public ResponseEntity<MonetizationArtifactPublicationResponse> publishArtifact(
            @PathVariable UUID chatId,
            @PathVariable UUID artifactId,
            @Valid @RequestBody PublishMonetizationArtifactRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.publishArtifact(CurrentUser.id(), chatId, artifactId, request));
    }

    @GetMapping("/channels/{chatId}/report")
    public ResponseEntity<ChannelMonetizationReportResponse> report(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.getChannelReport(CurrentUser.id(), chatId));
    }

    @GetMapping("/channels/{chatId}/withdrawals")
    public ResponseEntity<List<MonetizationWithdrawalResponse>> withdrawals(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listWithdrawals(CurrentUser.id(), chatId));
    }

    @PostMapping("/channels/{chatId}/withdrawals")
    public ResponseEntity<MonetizationWithdrawalResponse> createWithdrawal(
            @PathVariable UUID chatId,
            @Valid @RequestBody CreateMonetizationWithdrawalRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.createWithdrawal(CurrentUser.id(), chatId, request));
    }

    @PostMapping("/channels/{chatId}/withdrawals/{withdrawalId}/cancel")
    public ResponseEntity<MonetizationWithdrawalResponse> cancelWithdrawal(
            @PathVariable UUID chatId,
            @PathVariable UUID withdrawalId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.cancelWithdrawal(CurrentUser.id(), chatId, withdrawalId));
    }

    @PostMapping("/channels/{chatId}/withdrawals/{withdrawalId}/sync")
    public ResponseEntity<MonetizationWithdrawalResponse> syncWithdrawal(
            @PathVariable UUID chatId,
            @PathVariable UUID withdrawalId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.syncWithdrawal(CurrentUser.id(), chatId, withdrawalId));
    }

    @PostMapping("/channels/{chatId}/withdrawals/{withdrawalId}/retry")
    public ResponseEntity<MonetizationWithdrawalResponse> retryWithdrawal(
            @PathVariable UUID chatId,
            @PathVariable UUID withdrawalId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.retryWithdrawal(CurrentUser.id(), chatId, withdrawalId));
    }

    @GetMapping("/channels/{chatId}/withdrawals/{withdrawalId}/callbacks")
    public ResponseEntity<List<MonetizationWithdrawalProviderCallbackResponse>> withdrawalCallbacks(
            @PathVariable UUID chatId,
            @PathVariable UUID withdrawalId
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listWithdrawalCallbacks(CurrentUser.id(), chatId, withdrawalId));
    }

    @GetMapping("/channels/{chatId}/reconciliation-runs")
    public ResponseEntity<List<MonetizationReconciliationRunResponse>> reconciliationRuns(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.listReconciliationRuns(CurrentUser.id(), chatId));
    }

    @PostMapping("/channels/{chatId}/reconciliation/run")
    public ResponseEntity<MonetizationReconciliationRunResponse> runReconciliation(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.runReconciliation(CurrentUser.id(), chatId, 100));
    }

    @GetMapping("/channels/{chatId}/stats")
    public ResponseEntity<ChannelMonetizationStatsResponse> channelStats(@PathVariable UUID chatId) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.ok(monetizationService.getChannelStats(CurrentUser.id(), chatId));
    }
}
