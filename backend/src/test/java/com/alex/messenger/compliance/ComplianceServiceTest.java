package com.alex.messenger.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.compliance.dto.ComplianceCaseApproveRequest;
import com.alex.messenger.compliance.dto.ComplianceCaseArtifactDownloadResponse;
import com.alex.messenger.compliance.dto.ComplianceCaseCreateRequest;
import com.alex.messenger.compliance.dto.ComplianceCaseExportArtifactResponse;
import com.alex.messenger.compliance.dto.ComplianceCaseExportDownloadAuditResponse;
import com.alex.messenger.compliance.dto.ComplianceCaseExportResponse;
import com.alex.messenger.compliance.dto.ComplianceCaseResponse;
import com.alex.messenger.lawful.LawfulInterceptionService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.MessageAttachmentResponse;
import com.alex.messenger.user.UserRepository;
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
class ComplianceServiceTest {

    @Mock
    private ComplianceCaseRepository complianceCaseRepository;

    @Mock
    private ComplianceCaseEventRepository complianceCaseEventRepository;

    @Mock
    private LawfulInterceptionService lawfulInterceptionService;

    @Mock
    private ComplianceCaseExportArtifactRepository complianceCaseExportArtifactRepository;

    @Mock
    private ComplianceCaseExportDownloadAuditRepository complianceCaseExportDownloadAuditRepository;

    @Mock
    private ComplianceExportChecksumService complianceExportChecksumService;

    @Mock
    private ComplianceExportArtifactStorageService complianceExportArtifactStorageService;

    @Mock
    private UserRepository userRepository;

    private ComplianceProperties complianceProperties;

    private ComplianceService complianceService;

    @BeforeEach
    void setUp() {
        complianceProperties = new ComplianceProperties();
        complianceProperties.getExport().setArtifactTtl(java.time.Duration.ofHours(24));
        complianceProperties.getExport().setCleanupBatchSize(10);
        complianceProperties.getExport().setMaxInlineMessages(10);
        complianceService = new ComplianceService(
                complianceCaseRepository,
                complianceCaseEventRepository,
                complianceCaseExportArtifactRepository,
                complianceCaseExportDownloadAuditRepository,
                lawfulInterceptionService,
                complianceExportChecksumService,
                complianceExportArtifactStorageService,
                complianceProperties,
                userRepository
        );
    }

    @Test
    void createCaseRejectsUnknownTargetUser() {
        UUID targetUserId = UUID.randomUUID();
        when(userRepository.existsById(targetUserId)).thenReturn(false);

        ResponseStatusException exception = catchThrowableOfType(
                () -> complianceService.createCase(
                        "operator-a",
                        new ComplianceCaseCreateRequest(
                                "CASE-1",
                                targetUserId,
                                "court-order",
                                "Fraud investigation",
                                null,
                                null
                        )
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(complianceCaseEventRepository, never()).save(any());
    }

    @Test
    void approveCaseRejectsSelfApproval() {
        UUID caseId = UUID.randomUUID();
        ComplianceCaseEntity complianceCase = new ComplianceCaseEntity();
        complianceCase.setId(caseId);
        complianceCase.setRequestedByOperatorId("operator-a");
        complianceCase.setStatus(ComplianceCaseStatus.PENDING_APPROVAL);

        when(complianceCaseRepository.findById(caseId)).thenReturn(Optional.of(complianceCase));

        ResponseStatusException exception = catchThrowableOfType(
                () -> complianceService.approveCase(
                        "operator-a",
                        caseId,
                        new ComplianceCaseApproveRequest("approved")
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(complianceCaseEventRepository, never()).save(any());
    }

    @Test
    void getCaseRecordsAccessAudit() {
        UUID caseId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        ComplianceCaseEntity complianceCase = new ComplianceCaseEntity();
        complianceCase.setId(caseId);
        complianceCase.setTargetUserId(targetUserId);
        complianceCase.setCaseReference("CASE-1");
        complianceCase.setLegalBasis("court-order");
        complianceCase.setReason("Fraud investigation");
        complianceCase.setStatus(ComplianceCaseStatus.APPROVED);
        complianceCase.setRequestedByOperatorId("operator-a");

        when(complianceCaseRepository.findById(caseId)).thenReturn(Optional.of(complianceCase));
        when(complianceCaseEventRepository.findAllByCaseIdOrderByCreatedAtAsc(caseId)).thenReturn(List.of());

        ComplianceCaseResponse response = complianceService.getCase("operator-z", caseId);

        assertThat(response.caseId()).isEqualTo(caseId);
        ArgumentCaptor<ComplianceCaseEventEntity> eventCaptor = ArgumentCaptor.forClass(ComplianceCaseEventEntity.class);
        verify(complianceCaseEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getActorOperatorId()).isEqualTo("operator-z");
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(ComplianceCaseEventType.CASE_ACCESSED);
        assertThat(eventCaptor.getValue().getSummary()).isEqualTo("Case viewed");
    }

    @Test
    void listArtifactsRecordsAccessAudit() {
        UUID caseId = UUID.randomUUID();
        ComplianceCaseEntity complianceCase = new ComplianceCaseEntity();
        complianceCase.setId(caseId);
        complianceCase.setStatus(ComplianceCaseStatus.EXPORTED);

        ComplianceCaseExportArtifactEntity artifact = new ComplianceCaseExportArtifactEntity();
        artifact.setId(UUID.randomUUID());
        artifact.setCaseId(caseId);
        artifact.setExportedByOperatorId("operator-c");
        artifact.setExportedAt(Instant.parse("2026-03-11T10:00:00Z"));
        artifact.setMessageCount(3);
        artifact.setArtifactChecksum("checksum-1");
        artifact.setContentType("application/vnd.alex.compliance-export+json");
        artifact.setExpiresAt(Instant.now().plusSeconds(3600));
        artifact.setDownloadCount(0);

        when(complianceCaseRepository.findById(caseId)).thenReturn(Optional.of(complianceCase));
        when(complianceCaseExportArtifactRepository.findAllByCaseIdAndDeletedAtIsNullOrderByExportedAtDescCreatedAtDesc(caseId))
                .thenReturn(List.of(artifact));

        List<ComplianceCaseExportArtifactResponse> response = complianceService.listArtifacts("operator-a", caseId);

        assertThat(response).singleElement().satisfies(item -> assertThat(item.artifactChecksum()).isEqualTo("checksum-1"));
        ArgumentCaptor<ComplianceCaseEventEntity> eventCaptor = ArgumentCaptor.forClass(ComplianceCaseEventEntity.class);
        verify(complianceCaseEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getActorOperatorId()).isEqualTo("operator-a");
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(ComplianceCaseEventType.CASE_ACCESSED);
        assertThat(eventCaptor.getValue().getSummary()).isEqualTo("Export artifacts listed");
    }

    @Test
    void exportCaseUpdatesChecksumAndExportMetadata() {
        UUID caseId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        ComplianceCaseEntity complianceCase = new ComplianceCaseEntity();
        complianceCase.setId(caseId);
        complianceCase.setTargetUserId(targetUserId);
        complianceCase.setCaseReference("CASE-42");
        complianceCase.setLegalBasis("court-order");
        complianceCase.setReason("Targeted export");
        complianceCase.setRequestedByOperatorId("operator-a");
        complianceCase.setApprovedByOperatorId("operator-b");
        complianceCase.setStatus(ComplianceCaseStatus.APPROVED);
        complianceCase.setCreatedAt(Instant.parse("2026-03-10T10:00:00Z"));
        complianceCase.setApprovedAt(Instant.parse("2026-03-10T11:00:00Z"));
        complianceCase.setExportCount(0);

        ChatMessageResponse message = new ChatMessageResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                false,
                targetUserId,
                null,
                null,
                null,
                null,
                null,
                0,
                "hello",
                List.of(),
                "TEXT",
                null,
                false,
                null,
                null,
                null,
                Instant.parse("2026-03-10T12:00:00Z"),
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

        ComplianceCaseExportArtifactEntity artifact = new ComplianceCaseExportArtifactEntity();
        artifact.setId(UUID.randomUUID());
        artifact.setCaseId(caseId);
        artifact.setExportedByOperatorId("operator-c");
        artifact.setExportedAt(Instant.parse("2026-03-10T13:00:00Z"));
        artifact.setMessageCount(1);
        artifact.setArtifactChecksum("checksum-1");
        artifact.setStoragePath("E:\\alex-main\\storage\\compliance-exports\\artifact.bin");
        artifact.setEncryptionIv("iv");
        artifact.setContentType("application/vnd.alex.compliance-export+json");
        artifact.setExpiresAt(Instant.parse("2026-03-11T13:00:00Z"));
        artifact.setDownloadCount(0);

        when(complianceCaseRepository.findById(caseId)).thenReturn(Optional.of(complianceCase));
        when(complianceCaseRepository.save(any(ComplianceCaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(complianceCaseEventRepository.save(any(ComplianceCaseEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(complianceCaseEventRepository.findAllByCaseIdOrderByCreatedAtAsc(caseId)).thenReturn(List.of());
        when(lawfulInterceptionService.exportDecryptedMessages(targetUserId, null, null)).thenReturn(List.of(message));
        when(complianceExportChecksumService.computeArtifactChecksum(any(ComplianceCaseExportArtifactPayload.class)))
                .thenReturn("checksum-1");
        when(complianceExportArtifactStorageService.writeArtifact(any(ComplianceCaseExportArtifactPayload.class)))
                .thenReturn(new ComplianceExportArtifactStorageService.StoredComplianceArtifact(
                        "E:\\alex-main\\storage\\compliance-exports\\artifact.bin",
                        "iv",
                        512
                ));
        when(complianceCaseExportArtifactRepository.save(any(ComplianceCaseExportArtifactEntity.class)))
                .thenAnswer(invocation -> {
                    ComplianceCaseExportArtifactEntity saved = invocation.getArgument(0);
                    saved.setContentType(artifact.getContentType());
                    return saved;
                });

        ComplianceCaseExportResponse response = complianceService.exportCase("operator-c", caseId);

        assertThat(response.messageCount()).isEqualTo(1);
        assertThat(response.artifactChecksum()).isNotBlank();
        assertThat(response.artifact()).isNotNull();
        assertThat(response.artifact().artifactChecksum()).isEqualTo("checksum-1");
        assertThat(response.caseInfo().status()).isEqualTo(ComplianceCaseStatus.EXPORTED.name());
        assertThat(response.caseInfo().lastExportedByOperatorId()).isEqualTo("operator-c");
        assertThat(response.caseInfo().exportCount()).isEqualTo(1);
        verify(complianceCaseEventRepository).save(any(ComplianceCaseEventEntity.class));
        verify(complianceCaseExportArtifactRepository).save(any(ComplianceCaseExportArtifactEntity.class));
    }

    @Test
    void exportCaseSanitizesTransientMediaAccessFields() {
        UUID caseId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        ComplianceCaseEntity complianceCase = new ComplianceCaseEntity();
        complianceCase.setId(caseId);
        complianceCase.setTargetUserId(targetUserId);
        complianceCase.setCaseReference("CASE-43");
        complianceCase.setLegalBasis("court-order");
        complianceCase.setReason("Targeted export");
        complianceCase.setRequestedByOperatorId("operator-a");
        complianceCase.setApprovedByOperatorId("operator-b");
        complianceCase.setStatus(ComplianceCaseStatus.APPROVED);
        complianceCase.setExportCount(0);

        ChatMessageResponse rawMessage = messageWithTransientMedia(targetUserId);

        when(complianceCaseRepository.findById(caseId)).thenReturn(Optional.of(complianceCase));
        when(complianceCaseRepository.save(any(ComplianceCaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(complianceCaseEventRepository.save(any(ComplianceCaseEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(complianceCaseEventRepository.findAllByCaseIdOrderByCreatedAtAsc(caseId)).thenReturn(List.of());
        when(lawfulInterceptionService.exportDecryptedMessages(targetUserId, null, null)).thenReturn(List.of(rawMessage));
        when(complianceExportChecksumService.computeArtifactChecksum(any(ComplianceCaseExportArtifactPayload.class)))
                .thenReturn("checksum-2");
        when(complianceExportArtifactStorageService.writeArtifact(any(ComplianceCaseExportArtifactPayload.class)))
                .thenReturn(new ComplianceExportArtifactStorageService.StoredComplianceArtifact(
                        "E:\\alex-main\\storage\\compliance-exports\\artifact-sanitized.bin",
                        "iv",
                        512
                ));
        when(complianceCaseExportArtifactRepository.save(any(ComplianceCaseExportArtifactEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ComplianceCaseExportResponse response = complianceService.exportCase("operator-c", caseId);

        ArgumentCaptor<ComplianceCaseExportArtifactPayload> payloadCaptor =
                ArgumentCaptor.forClass(ComplianceCaseExportArtifactPayload.class);
        verify(complianceExportArtifactStorageService).writeArtifact(payloadCaptor.capture());
        assertMessageSanitized(payloadCaptor.getValue().messages().get(0));
        assertMessageSanitized(response.messages().get(0));
    }

    @Test
    void listArtifactsReturnsOnlyAvailableArtifactsNewestFirst() {
        UUID caseId = UUID.randomUUID();

        ComplianceCaseEntity complianceCase = new ComplianceCaseEntity();
        complianceCase.setId(caseId);
        complianceCase.setStatus(ComplianceCaseStatus.EXPORTED);

        ComplianceCaseExportArtifactEntity availableArtifact = new ComplianceCaseExportArtifactEntity();
        availableArtifact.setId(UUID.randomUUID());
        availableArtifact.setCaseId(caseId);
        availableArtifact.setExportedByOperatorId("operator-c");
        availableArtifact.setExportedAt(Instant.parse("2026-03-11T10:00:00Z"));
        availableArtifact.setMessageCount(4);
        availableArtifact.setArtifactChecksum("checksum-available");
        availableArtifact.setContentType("application/vnd.alex.compliance-export+json");
        availableArtifact.setExpiresAt(Instant.now().plusSeconds(3600));
        availableArtifact.setDownloadCount(2);

        ComplianceCaseExportArtifactEntity expiredArtifact = new ComplianceCaseExportArtifactEntity();
        expiredArtifact.setId(UUID.randomUUID());
        expiredArtifact.setCaseId(caseId);
        expiredArtifact.setExportedByOperatorId("operator-b");
        expiredArtifact.setExportedAt(Instant.parse("2026-03-10T10:00:00Z"));
        expiredArtifact.setMessageCount(2);
        expiredArtifact.setArtifactChecksum("checksum-expired");
        expiredArtifact.setContentType("application/vnd.alex.compliance-export+json");
        expiredArtifact.setExpiresAt(Instant.now().minusSeconds(5));
        expiredArtifact.setDownloadCount(0);

        when(complianceCaseRepository.findById(caseId)).thenReturn(Optional.of(complianceCase));
        when(complianceCaseExportArtifactRepository.findAllByCaseIdAndDeletedAtIsNullOrderByExportedAtDescCreatedAtDesc(caseId))
                .thenReturn(List.of(availableArtifact, expiredArtifact));

        List<ComplianceCaseExportArtifactResponse> response = complianceService.listArtifacts("operator-a", caseId);

        assertThat(response).singleElement().satisfies(artifact -> {
            assertThat(artifact.artifactId()).isEqualTo(availableArtifact.getId());
            assertThat(artifact.artifactChecksum()).isEqualTo("checksum-available");
            assertThat(artifact.exportedByOperatorId()).isEqualTo("operator-c");
        });
    }

    @Test
    void listArtifactDownloadAuditsReturnsNewestEntriesForArtifact() {
        UUID caseId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();

        ComplianceCaseEntity complianceCase = new ComplianceCaseEntity();
        complianceCase.setId(caseId);
        complianceCase.setStatus(ComplianceCaseStatus.EXPORTED);

        ComplianceCaseExportArtifactEntity artifact = new ComplianceCaseExportArtifactEntity();
        artifact.setId(artifactId);
        artifact.setCaseId(caseId);

        ComplianceCaseExportDownloadAuditEntity newestAudit = new ComplianceCaseExportDownloadAuditEntity();
        newestAudit.setId(UUID.randomUUID());
        newestAudit.setArtifactId(artifactId);
        newestAudit.setCaseId(caseId);
        newestAudit.setOperatorId("operator-z");
        newestAudit.setDownloadedAt(Instant.parse("2026-03-11T12:00:00Z"));
        newestAudit.setChecksumVerified(true);

        ComplianceCaseExportDownloadAuditEntity olderAudit = new ComplianceCaseExportDownloadAuditEntity();
        olderAudit.setId(UUID.randomUUID());
        olderAudit.setArtifactId(artifactId);
        olderAudit.setCaseId(caseId);
        olderAudit.setOperatorId("operator-y");
        olderAudit.setDownloadedAt(Instant.parse("2026-03-11T11:00:00Z"));
        olderAudit.setChecksumVerified(true);

        when(complianceCaseRepository.findById(caseId)).thenReturn(Optional.of(complianceCase));
        when(complianceCaseExportArtifactRepository.findByIdAndCaseId(artifactId, caseId)).thenReturn(Optional.of(artifact));
        when(complianceCaseExportDownloadAuditRepository.findAllByCaseIdAndArtifactIdOrderByDownloadedAtDesc(caseId, artifactId))
                .thenReturn(List.of(newestAudit, olderAudit));

        List<ComplianceCaseExportDownloadAuditResponse> response =
                complianceService.listArtifactDownloadAudits("operator-a", caseId, artifactId);

        assertThat(response).extracting(ComplianceCaseExportDownloadAuditResponse::operatorId)
                .containsExactly("operator-z", "operator-y");
        assertThat(response).extracting(ComplianceCaseExportDownloadAuditResponse::artifactId)
                .containsExactly(artifactId, artifactId);
    }

    @Test
    void deleteExpiredArtifactsMarksArtifactDeletedAndPurgesStorage() {
        UUID caseId = UUID.randomUUID();
        Instant now = Instant.parse("2026-03-11T13:00:00Z");

        ComplianceCaseExportArtifactEntity artifact = new ComplianceCaseExportArtifactEntity();
        artifact.setId(UUID.randomUUID());
        artifact.setCaseId(caseId);
        artifact.setExpiresAt(now.minusSeconds(1));
        artifact.setStoragePath("E:\\alex-main\\storage\\compliance-exports\\artifact-expired.bin");
        artifact.setEncryptionIv("iv");

        when(complianceCaseExportArtifactRepository.findByExpiresAtBeforeAndDeletedAtIsNullOrderByExpiresAtAsc(
                eq(now),
                any()
        )).thenReturn(List.of(artifact));
        when(complianceCaseExportArtifactRepository.save(any(ComplianceCaseExportArtifactEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(complianceCaseEventRepository.save(any(ComplianceCaseEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        complianceService.deleteExpiredArtifacts(now);

        assertThat(artifact.getDeletedAt()).isEqualTo(now);
        verify(complianceExportArtifactStorageService).deleteArtifact(artifact);
        verify(complianceCaseEventRepository).save(any(ComplianceCaseEventEntity.class));
    }

    @Test
    void downloadArtifactVerifiesChecksumAndWritesAudit() {
        UUID caseId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        ComplianceCaseEntity complianceCase = new ComplianceCaseEntity();
        complianceCase.setId(caseId);
        complianceCase.setTargetUserId(targetUserId);
        complianceCase.setCaseReference("CASE-77");
        complianceCase.setLegalBasis("court-order");
        complianceCase.setReason("Targeted export");
        complianceCase.setRequestedByOperatorId("operator-a");
        complianceCase.setApprovedByOperatorId("operator-b");
        complianceCase.setStatus(ComplianceCaseStatus.EXPORTED);
        complianceCase.setExportCount(1);
        complianceCase.setLatestArtifactChecksum("checksum-verified");

        ComplianceCaseExportArtifactEntity artifact = new ComplianceCaseExportArtifactEntity();
        artifact.setId(artifactId);
        artifact.setCaseId(caseId);
        artifact.setExportedByOperatorId("operator-c");
        artifact.setExportedAt(Instant.parse("2026-03-11T10:00:00Z"));
        artifact.setMessageCount(1);
        artifact.setArtifactChecksum("checksum-verified");
        artifact.setStoragePath("E:\\alex-main\\storage\\compliance-exports\\artifact.bin");
        artifact.setEncryptionIv("iv");
        artifact.setContentType("application/vnd.alex.compliance-export+json");
        artifact.setExpiresAt(Instant.now().plusSeconds(3600));
        artifact.setDownloadCount(0);

        ChatMessageResponse message = new ChatMessageResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                false,
                targetUserId,
                null,
                null,
                null,
                null,
                null,
                0,
                "hello",
                List.of(),
                "TEXT",
                null,
                false,
                null,
                null,
                null,
                Instant.parse("2026-03-10T12:00:00Z"),
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

        ComplianceCaseExportArtifactPayload payload = new ComplianceCaseExportArtifactPayload(
                artifactId,
                caseId,
                targetUserId,
                "CASE-77",
                "court-order",
                "Targeted export",
                null,
                null,
                "operator-a",
                "operator-b",
                "operator-c",
                artifact.getExportedAt(),
                List.of(message)
        );

        when(complianceCaseRepository.findById(caseId)).thenReturn(Optional.of(complianceCase));
        when(complianceCaseEventRepository.findAllByCaseIdOrderByCreatedAtAsc(caseId)).thenReturn(List.of());
        when(complianceCaseExportArtifactRepository.findByIdAndCaseId(artifactId, caseId)).thenReturn(Optional.of(artifact));
        when(complianceExportArtifactStorageService.readArtifact(artifact)).thenReturn(payload);
        when(complianceExportChecksumService.computeArtifactChecksum(payload)).thenReturn("checksum-verified");
        when(complianceCaseExportArtifactRepository.save(any(ComplianceCaseExportArtifactEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(complianceCaseExportDownloadAuditRepository.save(any(ComplianceCaseExportDownloadAuditEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(complianceCaseEventRepository.save(any(ComplianceCaseEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ComplianceCaseArtifactDownloadResponse response = complianceService.downloadArtifact("operator-d", caseId, artifactId);

        assertThat(response.checksumVerified()).isTrue();
        assertThat(response.messages()).singleElement().satisfies(downloaded ->
                assertThat(downloaded.text()).isEqualTo("hello")
        );
        assertThat(response.artifact().downloadCount()).isEqualTo(1);
        assertThat(response.artifact().lastDownloadedByOperatorId()).isEqualTo("operator-d");
        verify(complianceCaseExportDownloadAuditRepository).save(any(ComplianceCaseExportDownloadAuditEntity.class));
    }

    @Test
    void downloadArtifactSanitizesTransientMediaAccessFieldsFromStoredPayload() {
        UUID caseId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        ComplianceCaseEntity complianceCase = new ComplianceCaseEntity();
        complianceCase.setId(caseId);
        complianceCase.setTargetUserId(targetUserId);
        complianceCase.setStatus(ComplianceCaseStatus.EXPORTED);

        ComplianceCaseExportArtifactEntity artifact = new ComplianceCaseExportArtifactEntity();
        artifact.setId(artifactId);
        artifact.setCaseId(caseId);
        artifact.setArtifactChecksum("checksum-legacy");
        artifact.setStoragePath("E:\\alex-main\\storage\\compliance-exports\\artifact-legacy.bin");
        artifact.setEncryptionIv("iv");
        artifact.setContentType("application/vnd.alex.compliance-export+json");
        artifact.setExpiresAt(Instant.now().plusSeconds(3600));
        artifact.setDownloadCount(0);

        ComplianceCaseExportArtifactPayload payload = new ComplianceCaseExportArtifactPayload(
                artifactId,
                caseId,
                targetUserId,
                "CASE-77",
                "court-order",
                "Targeted export",
                null,
                null,
                "operator-a",
                "operator-b",
                "operator-c",
                Instant.parse("2026-03-11T10:00:00Z"),
                List.of(messageWithTransientMedia(targetUserId))
        );

        when(complianceCaseRepository.findById(caseId)).thenReturn(Optional.of(complianceCase));
        when(complianceCaseEventRepository.findAllByCaseIdOrderByCreatedAtAsc(caseId)).thenReturn(List.of());
        when(complianceCaseExportArtifactRepository.findByIdAndCaseId(artifactId, caseId)).thenReturn(Optional.of(artifact));
        when(complianceExportArtifactStorageService.readArtifact(artifact)).thenReturn(payload);
        when(complianceExportChecksumService.computeArtifactChecksum(payload)).thenReturn("checksum-legacy");
        when(complianceCaseExportArtifactRepository.save(any(ComplianceCaseExportArtifactEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(complianceCaseExportDownloadAuditRepository.save(any(ComplianceCaseExportDownloadAuditEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(complianceCaseEventRepository.save(any(ComplianceCaseEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ComplianceCaseArtifactDownloadResponse response = complianceService.downloadArtifact("operator-d", caseId, artifactId);

        assertMessageSanitized(response.messages().get(0));
    }

    @Test
    void downloadArtifactRejectsExpiredArtifact() {
        UUID caseId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();

        ComplianceCaseEntity complianceCase = new ComplianceCaseEntity();
        complianceCase.setId(caseId);
        complianceCase.setTargetUserId(UUID.randomUUID());
        complianceCase.setStatus(ComplianceCaseStatus.EXPORTED);

        ComplianceCaseExportArtifactEntity artifact = new ComplianceCaseExportArtifactEntity();
        artifact.setId(artifactId);
        artifact.setCaseId(caseId);
        artifact.setArtifactChecksum("checksum-expired");
        artifact.setStoragePath("E:\\alex-main\\storage\\compliance-exports\\expired.bin");
        artifact.setEncryptionIv("iv");
        artifact.setContentType("application/vnd.alex.compliance-export+json");
        artifact.setExpiresAt(Instant.now().minusSeconds(5));
        artifact.setDownloadCount(0);

        when(complianceCaseRepository.findById(caseId)).thenReturn(Optional.of(complianceCase));
        when(complianceCaseExportArtifactRepository.findByIdAndCaseId(artifactId, caseId)).thenReturn(Optional.of(artifact));
        when(complianceCaseExportArtifactRepository.save(any(ComplianceCaseExportArtifactEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(complianceCaseEventRepository.save(any(ComplianceCaseEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseStatusException exception = catchThrowableOfType(
                () -> complianceService.downloadArtifact("operator-d", caseId, artifactId),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.GONE);
        verify(complianceExportArtifactStorageService).deleteArtifact(eq(artifact));
    }

    @Test
    void downloadArtifactWritesFailedAuditWhenChecksumMismatch() {
        UUID caseId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        ComplianceCaseEntity complianceCase = new ComplianceCaseEntity();
        complianceCase.setId(caseId);
        complianceCase.setTargetUserId(targetUserId);
        complianceCase.setStatus(ComplianceCaseStatus.EXPORTED);

        ComplianceCaseExportArtifactEntity artifact = new ComplianceCaseExportArtifactEntity();
        artifact.setId(artifactId);
        artifact.setCaseId(caseId);
        artifact.setArtifactChecksum("checksum-expected");
        artifact.setStoragePath("E:\\alex-main\\storage\\compliance-exports\\artifact.bin");
        artifact.setEncryptionIv("iv");
        artifact.setContentType("application/vnd.alex.compliance-export+json");
        artifact.setExpiresAt(Instant.now().plusSeconds(3600));
        artifact.setDownloadCount(0);

        ComplianceCaseExportArtifactPayload payload = new ComplianceCaseExportArtifactPayload(
                artifactId,
                caseId,
                targetUserId,
                "CASE-77",
                "court-order",
                "Targeted export",
                null,
                null,
                "operator-a",
                "operator-b",
                "operator-c",
                Instant.parse("2026-03-11T10:00:00Z"),
                List.of()
        );

        when(complianceCaseRepository.findById(caseId)).thenReturn(Optional.of(complianceCase));
        when(complianceCaseExportArtifactRepository.findByIdAndCaseId(artifactId, caseId)).thenReturn(Optional.of(artifact));
        when(complianceExportArtifactStorageService.readArtifact(artifact)).thenReturn(payload);
        when(complianceExportChecksumService.computeArtifactChecksum(payload)).thenReturn("checksum-actual");
        when(complianceCaseExportDownloadAuditRepository.save(any(ComplianceCaseExportDownloadAuditEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(complianceCaseEventRepository.save(any(ComplianceCaseEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseStatusException exception = catchThrowableOfType(
                () -> complianceService.downloadArtifact("operator-d", caseId, artifactId),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        ArgumentCaptor<ComplianceCaseExportDownloadAuditEntity> auditCaptor =
                ArgumentCaptor.forClass(ComplianceCaseExportDownloadAuditEntity.class);
        verify(complianceCaseExportDownloadAuditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().isChecksumVerified()).isFalse();
        assertThat(auditCaptor.getValue().getOperatorId()).isEqualTo("operator-d");

        ArgumentCaptor<ComplianceCaseEventEntity> eventCaptor = ArgumentCaptor.forClass(ComplianceCaseEventEntity.class);
        verify(complianceCaseEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(ComplianceCaseEventType.ARTIFACT_DOWNLOAD_FAILED);
    }

    private ChatMessageResponse messageWithTransientMedia(UUID targetUserId) {
        return new ChatMessageResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                "Alice",
                "https://cdn.example.com/profile/photo-token",
                Instant.parse("2026-03-11T13:00:00Z"),
                false,
                targetUserId,
                null,
                null,
                null,
                null,
                null,
                0,
                "hello",
                List.of(),
                "TEXT",
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.parse("2026-03-10T12:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(attachmentWithTransientMedia()),
                List.of(),
                "READ",
                Instant.parse("2026-03-10T12:01:00Z"),
                Instant.parse("2026-03-10T12:02:00Z"),
                null,
                null,
                null,
                false,
                null
        );
    }

    private MessageAttachmentResponse attachmentWithTransientMedia() {
        return new MessageAttachmentResponse(
                UUID.randomUUID(),
                "voice.ogg",
                "audio/ogg",
                "VOICE",
                4096,
                1200L,
                "https://cdn.example.com/download-token",
                "https://cdn.example.com/preview-token",
                "https://cdn.example.com/thumbnail-token",
                320,
                240,
                List.of(1, 2, 3),
                Instant.parse("2026-03-11T13:05:00Z"),
                true,
                false,
                true,
                false,
                null,
                null,
                "APPROVED",
                null,
                false,
                false,
                null,
                null,
                null,
                false
        );
    }

    private void assertMessageSanitized(ChatMessageResponse message) {
        assertThat(message.displaySenderPhotoUrl()).isNull();
        assertThat(message.displaySenderPhotoAccessExpiresAt()).isNull();
        assertThat(message.attachments()).singleElement().satisfies(attachment -> {
            assertThat(attachment.downloadUrl()).isNull();
            assertThat(attachment.previewUrl()).isNull();
            assertThat(attachment.thumbnailUrl()).isNull();
            assertThat(attachment.accessExpiresAt()).isNull();
            assertThat(attachment.originalFileName()).isEqualTo("voice.ogg");
        });
    }
}
