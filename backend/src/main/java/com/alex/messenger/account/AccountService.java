package com.alex.messenger.account;

import com.alex.messenger.account.dto.AccountDeletionResponse;
import com.alex.messenger.account.dto.AccountExportResponse;
import com.alex.messenger.account.dto.RequestAccountExport;
import com.alex.messenger.account.dto.ScheduleAccountDeletionRequest;
import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.lawful.LawfulExportChecksumService;
import com.alex.messenger.lawful.LawfulInterceptionService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountExportJobRepository accountExportJobRepository;
    private final AccountDeletionJobRepository accountDeletionJobRepository;
    private final UserRepository userRepository;
    private final UserSessionService userSessionService;
    private final LawfulInterceptionService lawfulInterceptionService;
    private final LawfulExportChecksumService lawfulExportChecksumService;
    private final AccountProperties accountProperties;

    @Transactional
    public AccountExportResponse export(UUID userId, UUID sessionId, RequestAccountExport request) {
        userSessionService.requireOwnedSession(sessionId, userId);
        requireActiveUser(userId);
        RequestAccountExport effectiveRequest = request != null
                ? request
                : new RequestAccountExport(null, null, null, null);

        String format = normalizeFormat(effectiveRequest.format());
        boolean includeAttachmentsMetadata = Boolean.TRUE.equals(effectiveRequest.includeAttachmentsMetadata());
        AccountExportJobEntity job = new AccountExportJobEntity();
        job.setUserId(userId);
        job.setRequestedBySessionId(sessionId);
        job.setStatus("RUNNING");
        job.setExportFormat(format);
        job.setIncludeAttachmentsMetadata(includeAttachmentsMetadata);
        AccountExportJobEntity savedJob = accountExportJobRepository.save(job);

        List<ChatMessageResponse> messages = lawfulInterceptionService.exportDecryptedMessages(
                userId,
                effectiveRequest.fromInclusive(),
                effectiveRequest.toExclusive()
        );
        String checksum = lawfulExportChecksumService.computeDirectExportChecksum(
                savedJob.getId(),
                userId,
                "self",
                "SELF_SERVICE_EXPORT",
                effectiveRequest.fromInclusive(),
                effectiveRequest.toExclusive(),
                includeAttachmentsMetadata,
                messages
        );

        savedJob.setStatus("COMPLETED");
        savedJob.setMessageCount(messages.size());
        savedJob.setArtifactChecksum(checksum);
        savedJob.setCompletedAt(Instant.now());
        return toExportResponse(accountExportJobRepository.save(savedJob));
    }

    @Transactional
    public AccountDeletionResponse scheduleDeletion(
            UUID userId,
            UUID sessionId,
            ScheduleAccountDeletionRequest request
    ) {
        userSessionService.requireOwnedSession(sessionId, userId);
        requireActiveUser(userId);

        Instant scheduledFor = Instant.now().plusSeconds(resolveDelayDays(request) * 24L * 60L * 60L);
        AccountDeletionJobEntity job = accountDeletionJobRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, "SCHEDULED")
                .orElseGet(AccountDeletionJobEntity::new);
        job.setUserId(userId);
        job.setRequestedBySessionId(sessionId);
        job.setTriggerType("USER_REQUEST");
        job.setStatus("SCHEDULED");
        job.setReason(normalizeNullable(request != null ? request.reason() : null, 255));
        job.setScheduledFor(scheduledFor);
        job.setCancelledAt(null);
        job.setExecutedAt(null);
        return toDeletionResponse(accountDeletionJobRepository.save(job));
    }

    @Transactional
    public int scheduleInactiveDeletionJobs() {
        Instant now = Instant.now();
        List<UserEntity> inactiveUsers = userRepository.findTop100ByDeletedAtIsNullOrderByLastSeenAtAsc();
        int created = 0;
        for (UserEntity user : inactiveUsers) {
            if (!isSelfDestructDue(user, now)) {
                continue;
            }
            if (accountDeletionJobRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), "SCHEDULED").isPresent()) {
                continue;
            }
            AccountDeletionJobEntity job = new AccountDeletionJobEntity();
            job.setUserId(user.getId());
            job.setTriggerType("INACTIVITY");
            job.setStatus("SCHEDULED");
            job.setReason("Inactive account self-destruct");
            job.setScheduledFor(now);
            accountDeletionJobRepository.save(job);
            created++;
        }
        return created;
    }

    @Transactional
    public int executeDueDeletions() {
        List<AccountDeletionJobEntity> jobs = accountDeletionJobRepository.findAllByStatusAndScheduledForBeforeOrderByScheduledForAsc(
                "SCHEDULED",
                Instant.now().plusSeconds(1),
                org.springframework.data.domain.PageRequest.of(0, Math.max(1, accountProperties.getDeletion().getExecutionBatchSize()))
        );
        int executed = 0;
        for (AccountDeletionJobEntity job : jobs) {
            UserEntity user = userRepository.findById(job.getUserId()).orElse(null);
            if (user == null || user.getDeletedAt() != null) {
                job.setStatus("EXECUTED");
                job.setExecutedAt(Instant.now());
                accountDeletionJobRepository.save(job);
                continue;
            }
            softDeleteUser(user);
            job.setStatus("EXECUTED");
            job.setExecutedAt(Instant.now());
            accountDeletionJobRepository.save(job);
            executed++;
        }
        return executed;
    }

    private void softDeleteUser(UserEntity user) {
        Instant now = Instant.now();
        user.setDeletedAt(now);
        user.setDisplayName("Deleted Account");
        user.setUsername(null);
        user.setAbout(null);
        user.setPhoneNumber("deleted-" + user.getId().toString().replace("-", "").substring(0, 16));
        user.setPhotoStorageProvider(null);
        user.setPhotoBucketName(null);
        user.setPhotoObjectKey(null);
        user.setPhotoContentType(null);
        user.setPhotoUpdatedAt(null);
        user.setPreferredLanguage(null);
        user.setTranslationTargetLanguage(null);
        user.setTwoFactorPasswordHash(null);
        user.setTwoFactorPasswordSalt(null);
        user.setTwoFactorHint(null);
        user.setTwoFactorEnabledAt(null);
        userRepository.save(user);
        userSessionService.revokeAll(user.getId());
    }

    private boolean isSelfDestructDue(UserEntity user, Instant now) {
        int selfDestructDays = user.getAccountSelfDestructDays() != null
                ? Math.max(1, user.getAccountSelfDestructDays())
                : Math.max(1, accountProperties.getDeletion().getDefaultSelfDestructDays());
        Instant activityAt = user.getLastSeenAt() != null ? user.getLastSeenAt() : user.getCreatedAt();
        if (activityAt == null) {
            return false;
        }
        return !activityAt.plusSeconds(selfDestructDays * 24L * 60L * 60L).isAfter(now);
    }

    private UserEntity requireActiveUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.GONE, "Account is deleted");
        }
        return user;
    }

    private int resolveDelayDays(ScheduleAccountDeletionRequest request) {
        int defaultDelay = Math.max(1, (int) accountProperties.getDeletion().getDefaultDelay().toDays());
        if (request == null || request.delayDays() == null) {
            return defaultDelay;
        }
        return Math.max(1, request.delayDays());
    }

    private String normalizeFormat(String format) {
        String normalized = normalizeNullable(format, 16);
        if (normalized == null) {
            normalized = accountProperties.getExport().getDefaultFormat();
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!List.of("JSON").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported export format");
        }
        return normalized;
    }

    private String normalizeNullable(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private AccountExportResponse toExportResponse(AccountExportJobEntity job) {
        return new AccountExportResponse(
                job.getId(),
                job.getStatus(),
                job.getExportFormat(),
                Boolean.TRUE.equals(job.getIncludeAttachmentsMetadata()),
                job.getMessageCount() != null ? job.getMessageCount() : 0,
                job.getArtifactChecksum(),
                job.getArtifactLocation(),
                job.getCreatedAt(),
                job.getCompletedAt()
        );
    }

    private AccountDeletionResponse toDeletionResponse(AccountDeletionJobEntity job) {
        return new AccountDeletionResponse(
                job.getId(),
                job.getTriggerType(),
                job.getStatus(),
                job.getReason(),
                job.getScheduledFor(),
                job.getCreatedAt(),
                job.getExecutedAt()
        );
    }
}
