package com.alex.messenger.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.compliance.dto.ComplianceCaseApproveRequest;
import com.alex.messenger.compliance.dto.ComplianceCaseCreateRequest;
import com.alex.messenger.compliance.dto.ComplianceCaseExportResponse;
import com.alex.messenger.lawful.LawfulInterceptionService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private UserRepository userRepository;

    private ComplianceService complianceService;

    @BeforeEach
    void setUp() {
        complianceService = new ComplianceService(
                complianceCaseRepository,
                complianceCaseEventRepository,
                lawfulInterceptionService,
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

        when(complianceCaseRepository.findById(caseId)).thenReturn(Optional.of(complianceCase));
        when(complianceCaseRepository.save(any(ComplianceCaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(complianceCaseEventRepository.save(any(ComplianceCaseEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(complianceCaseEventRepository.findAllByCaseIdOrderByCreatedAtAsc(caseId)).thenReturn(List.of());
        when(lawfulInterceptionService.exportDecryptedMessages(targetUserId, null, null)).thenReturn(List.of(message));

        ComplianceCaseExportResponse response = complianceService.exportCase("operator-c", caseId);

        assertThat(response.messageCount()).isEqualTo(1);
        assertThat(response.artifactChecksum()).isNotBlank();
        assertThat(response.caseInfo().status()).isEqualTo(ComplianceCaseStatus.EXPORTED.name());
        assertThat(response.caseInfo().lastExportedByOperatorId()).isEqualTo("operator-c");
        assertThat(response.caseInfo().exportCount()).isEqualTo(1);
        verify(complianceCaseEventRepository).save(any(ComplianceCaseEventEntity.class));
    }
}
