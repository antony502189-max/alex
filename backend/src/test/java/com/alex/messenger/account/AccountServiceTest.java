package com.alex.messenger.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.account.dto.AccountExportResponse;
import com.alex.messenger.account.dto.RequestAccountExport;
import com.alex.messenger.account.dto.ScheduleAccountDeletionRequest;
import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.lawful.LawfulExportChecksumService;
import com.alex.messenger.lawful.LawfulInterceptionService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountExportJobRepository accountExportJobRepository;

    @Mock
    private AccountDeletionJobRepository accountDeletionJobRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private LawfulInterceptionService lawfulInterceptionService;

    @Mock
    private LawfulExportChecksumService lawfulExportChecksumService;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        AccountProperties accountProperties = new AccountProperties();
        accountProperties.getDeletion().setDefaultDelay(Duration.ofDays(7));
        accountProperties.getDeletion().setDefaultSelfDestructDays(365);
        accountProperties.getDeletion().setExecutionBatchSize(50);
        accountService = new AccountService(
                accountExportJobRepository,
                accountDeletionJobRepository,
                userRepository,
                userSessionService,
                lawfulInterceptionService,
                lawfulExportChecksumService,
                accountProperties
        );
    }

    @Test
    void exportCompletesJobWithChecksumAndMessageCount() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant fromInclusive = Instant.parse("2026-03-01T00:00:00Z");
        Instant toExclusive = Instant.parse("2026-03-19T00:00:00Z");

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setPhoneNumber("+375291234567");
        user.setDisplayName("Alex");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountExportJobRepository.save(any(AccountExportJobEntity.class))).thenAnswer(invocation -> {
            AccountExportJobEntity job = invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId(UUID.randomUUID());
            }
            if (job.getCreatedAt() == null) {
                job.setCreatedAt(Instant.parse("2026-03-19T12:00:00Z"));
            }
            return job;
        });
        when(lawfulInterceptionService.exportDecryptedMessages(userId, fromInclusive, toExclusive))
                .thenReturn(List.of(message(userId)));
        when(lawfulExportChecksumService.computeDirectExportChecksum(
                any(),
                eq(userId),
                eq("self"),
                eq("SELF_SERVICE_EXPORT"),
                eq(fromInclusive),
                eq(toExclusive),
                eq(true),
                any()
        )).thenReturn("checksum-123");

        AccountExportResponse response = accountService.export(
                userId,
                sessionId,
                new RequestAccountExport("json", true, fromInclusive, toExclusive)
        );

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.format()).isEqualTo("JSON");
        assertThat(response.includeAttachmentsMetadata()).isTrue();
        assertThat(response.messageCount()).isEqualTo(1);
        assertThat(response.artifactChecksum()).isEqualTo("checksum-123");
    }

    @Test
    void scheduleInactiveDeletionJobsUsesUserSpecificSelfDestructWindow() {
        UUID dueUserId = UUID.randomUUID();
        UUID recentUserId = UUID.randomUUID();

        UserEntity dueUser = new UserEntity();
        dueUser.setId(dueUserId);
        dueUser.setDisplayName("Due");
        dueUser.setPhoneNumber("+375291111111");
        dueUser.setCreatedAt(Instant.now().minus(Duration.ofDays(60)));
        dueUser.setLastSeenAt(Instant.now().minus(Duration.ofDays(40)));
        dueUser.setAccountSelfDestructDays(30);

        UserEntity recentUser = new UserEntity();
        recentUser.setId(recentUserId);
        recentUser.setDisplayName("Recent");
        recentUser.setPhoneNumber("+375292222222");
        recentUser.setCreatedAt(Instant.now().minus(Duration.ofDays(20)));
        recentUser.setLastSeenAt(Instant.now().minus(Duration.ofDays(10)));
        recentUser.setAccountSelfDestructDays(30);

        when(userRepository.findTop100ByDeletedAtIsNullOrderByLastSeenAtAsc())
                .thenReturn(List.of(dueUser, recentUser));
        when(accountDeletionJobRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(dueUserId, "SCHEDULED"))
                .thenReturn(Optional.empty());
        when(accountDeletionJobRepository.save(any(AccountDeletionJobEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int created = accountService.scheduleInactiveDeletionJobs();

        ArgumentCaptor<AccountDeletionJobEntity> captor = ArgumentCaptor.forClass(AccountDeletionJobEntity.class);
        verify(accountDeletionJobRepository).save(captor.capture());
        assertThat(created).isEqualTo(1);
        assertThat(captor.getValue().getUserId()).isEqualTo(dueUserId);
        assertThat(captor.getValue().getTriggerType()).isEqualTo("INACTIVITY");
    }

    @Test
    void exportRejectsInvalidRange() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setPhoneNumber("+375291234567");
        user.setDisplayName("Alex");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> accountService.export(
                userId,
                sessionId,
                new RequestAccountExport(
                        "json",
                        false,
                        Instant.parse("2026-03-20T12:00:00Z"),
                        Instant.parse("2026-03-19T12:00:00Z")
                )
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void scheduleDeletionRejectsInvalidDelay() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setPhoneNumber("+375291234567");
        user.setDisplayName("Alex");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> accountService.scheduleDeletion(
                userId,
                sessionId,
                new ScheduleAccountDeletionRequest("cleanup", 366)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ChatMessageResponse message(UUID userId) {
        return new ChatMessageResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                userId,
                "Alex",
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
                "hello",
                null,
                "TEXT",
                null,
                false,
                null,
                null,
                null,
                Instant.parse("2026-03-10T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                "READ",
                null,
                null,
                null,
                null,
                null
        );
    }
}
