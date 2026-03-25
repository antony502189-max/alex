package com.alex.messenger.attachment;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alex.messenger.attachment.dto.CreateAttachmentUploadSessionRequest;
import com.alex.messenger.attachment.dto.ModerateAttachmentRequest;
import com.alex.messenger.attachment.dto.TrimAttachmentRequest;
import com.alex.messenger.attachment.dto.UploadAttachmentChunkRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class AttachmentControllerMvcTest {

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private AttachmentUploadSessionService attachmentUploadSessionService;

    @Mock
    private AttachmentAccessTokenService attachmentAccessTokenService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        currentUserId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUserId.toString(), "test")
        );
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AttachmentController(attachmentService, attachmentUploadSessionService, attachmentAccessTokenService)
                )
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadReturnsBadRequestForUnsupportedKind() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "asset.bin", "application/octet-stream", new byte[] {1});

        mockMvc.perform(
                        multipart("/api/attachments/upload")
                                .file(file)
                                .param("chatId", UUID.randomUUID().toString())
                                .param("kind", "STICKER")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(attachmentService, attachmentUploadSessionService);
    }

    @Test
    void uploadReturnsBadRequestForNegativeAlbumItemIndex() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "asset.jpg", "image/jpeg", new byte[] {1});

        mockMvc.perform(
                        multipart("/api/attachments/upload")
                                .file(file)
                                .param("chatId", UUID.randomUUID().toString())
                                .param("albumItemIndex", "-1")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(attachmentService, attachmentUploadSessionService);
    }

    @Test
    void moderateReturnsBadRequestForBlankStatus() throws Exception {
        mockMvc.perform(
                        post("/api/attachments/{attachmentId}/moderation", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new ModerateAttachmentRequest(" ", null, null)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(attachmentService, attachmentUploadSessionService);
    }

    @Test
    void trimReturnsBadRequestWhenEndMissing() throws Exception {
        mockMvc.perform(
                        post("/api/attachments/{attachmentId}/trim", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new TrimAttachmentRequest(10L, null)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(attachmentService, attachmentUploadSessionService);
    }

    @Test
    void createUploadSessionReturnsBadRequestForNegativeTotalSize() throws Exception {
        mockMvc.perform(
                        post("/api/attachments/upload-sessions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateAttachmentUploadSessionRequest(
                                                UUID.randomUUID(),
                                                "photo.jpg",
                                                "image/jpeg",
                                                "IMAGE",
                                                -1L,
                                                null,
                                                null,
                                                null,
                                                false,
                                                List.of(),
                                                null,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(attachmentService, attachmentUploadSessionService);
    }

    @Test
    void createUploadSessionReturnsBadRequestWhenAlbumItemIndexHasNoAlbum() throws Exception {
        mockMvc.perform(
                        post("/api/attachments/upload-sessions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateAttachmentUploadSessionRequest(
                                                UUID.randomUUID(),
                                                "photo.jpg",
                                                "image/jpeg",
                                                "IMAGE",
                                                10L,
                                                null,
                                                null,
                                                null,
                                                false,
                                                List.of(),
                                                null,
                                                1
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(attachmentService, attachmentUploadSessionService);
    }

    @Test
    void uploadChunkReturnsBadRequestForBlankChunkPayload() throws Exception {
        mockMvc.perform(
                        post("/api/attachments/upload-sessions/{sessionId}/chunks", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UploadAttachmentChunkRequest(0L, " ")
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(attachmentService, attachmentUploadSessionService);
    }

    @Test
    void downloadReturnsUnauthorizedWithoutAuthOrToken() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/attachments/{attachmentId}/download", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(attachmentService, attachmentUploadSessionService, attachmentAccessTokenService);
    }

    @Test
    void downloadAcceptsAccessTokenWithoutAuthenticatedSession() throws Exception {
        UUID attachmentId = UUID.randomUUID();
        UUID tokenUserId = UUID.randomUUID();
        SecurityContextHolder.clearContext();

        when(attachmentAccessTokenService.validate("signed-token", attachmentId))
                .thenReturn(new AttachmentAccessTokenService.ValidatedAttachmentAccessToken(
                        tokenUserId,
                        attachmentId,
                        java.time.Instant.parse("2026-03-12T12:15:00Z")
                ));
        when(attachmentService.download(
                tokenUserId,
                attachmentId,
                java.time.Instant.parse("2026-03-12T12:15:00Z")
        )).thenReturn(new AttachmentDownloadResult("https://cdn.example/attachments/photo.png", null));

        mockMvc.perform(
                        get("/api/attachments/{attachmentId}/download", attachmentId)
                                .param(AttachmentAccessTokenService.QUERY_PARAMETER, "signed-token")
                )
                .andExpect(status().isTemporaryRedirect());
    }
}
