package com.alex.messenger.lawful;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.lawful.dto.DirectLawfulExportRequest;
import com.alex.messenger.lawful.dto.DirectLawfulExportResponse;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.MessageAttachmentResponse;
import com.alex.messenger.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LawfulDirectExportServiceTest {

    @Mock
    private LawfulDirectExportRepository lawfulDirectExportRepository;

    @Mock
    private LawfulInterceptionService lawfulInterceptionService;

    @Mock
    private LawfulExportChecksumService checksumService;

    @Mock
    private UserRepository userRepository;

    private LawfulDirectExportService lawfulDirectExportService;

    @BeforeEach
    void setUp() {
        lawfulDirectExportService = new LawfulDirectExportService(
                lawfulDirectExportRepository,
                lawfulInterceptionService,
                checksumService,
                userRepository
        );
    }

    @Test
    void exportRejectsUnknownTargetUser() {
        UUID targetUserId = UUID.randomUUID();
        when(userRepository.existsById(targetUserId)).thenReturn(false);

        ResponseStatusException exception = catchThrowableOfType(
                () -> lawfulDirectExportService.export(
                        "operator-a",
                        new DirectLawfulExportRequest(targetUserId, null, null, "Need export", false)
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void exportStripsAttachmentsWhenMetadataIsDisabled() {
        UUID targetUserId = UUID.randomUUID();
        ChatMessageResponse message = buildMessage(List.of(
                new MessageAttachmentResponse(
                        UUID.randomUUID(),
                        "photo.jpg",
                        "image/jpeg",
                        "IMAGE",
                        1024,
                        null,
                        "https://example.test/file",
                        "https://example.test/preview",
                        "https://example.test/thumb",
                        640,
                        480,
                        List.of(),
                        Instant.parse("2026-03-13T12:00:00Z"),
                        true,
                        true,
                        false,
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
                  )
          ));

        when(userRepository.existsById(targetUserId)).thenReturn(true);
        when(lawfulInterceptionService.exportDecryptedMessages(targetUserId, null, null)).thenReturn(List.of(message));
        when(checksumService.computeDirectExportChecksum(any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn("checksum-1");
        when(lawfulDirectExportRepository.save(any(LawfulDirectExportEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DirectLawfulExportResponse response = lawfulDirectExportService.export(
                "operator-a",
                new DirectLawfulExportRequest(targetUserId, null, null, "Need export", false)
        );

        assertThat(response.messageCount()).isEqualTo(1);
        assertThat(response.messages()).singleElement().satisfies(savedMessage ->
                assertThat(savedMessage.attachments()).isEmpty()
        );
        assertThat(response.artifactChecksum()).isEqualTo("checksum-1");
        verify(lawfulDirectExportRepository).save(any(LawfulDirectExportEntity.class));
    }

    @Test
    void exportKeepsAttachmentsWhenMetadataIsEnabled() {
        UUID targetUserId = UUID.randomUUID();
        ChatMessageResponse message = buildMessage(List.of(
                new MessageAttachmentResponse(
                        UUID.randomUUID(),
                        "photo.jpg",
                        "image/jpeg",
                        "IMAGE",
                        1024,
                        null,
                        "https://example.test/file",
                        "https://example.test/preview",
                        "https://example.test/thumb",
                        640,
                        480,
                        List.of(),
                        Instant.parse("2026-03-13T12:00:00Z"),
                        true,
                        true,
                        false,
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
                  )
          ));

        when(userRepository.existsById(targetUserId)).thenReturn(true);
        when(lawfulInterceptionService.exportDecryptedMessages(targetUserId, null, null)).thenReturn(List.of(message));
        when(checksumService.computeDirectExportChecksum(any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn("checksum-2");
        when(lawfulDirectExportRepository.save(any(LawfulDirectExportEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DirectLawfulExportResponse response = lawfulDirectExportService.export(
                "operator-a",
                new DirectLawfulExportRequest(targetUserId, null, null, "Need export", true)
        );

        assertThat(response.messages()).singleElement().satisfies(savedMessage ->
                assertThat(savedMessage.attachments()).hasSize(1)
        );
    }

    private ChatMessageResponse buildMessage(List<MessageAttachmentResponse> attachments) {
        return new ChatMessageResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                "Sender",
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
                List.of(),
                "TEXT",
                null,
                false,
                null,
                null,
                null,
                Instant.parse("2026-03-13T12:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                attachments,
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
