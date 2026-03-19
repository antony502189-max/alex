package com.alex.messenger.secret;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.secret.dto.CreateSecretChatRequest;
import com.alex.messenger.secret.dto.SendSecretChatMessageRequest;
import com.alex.messenger.secret.dto.UpdateSecretChatTimerRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class SecretChatControllerMvcTest {

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private SecretChatService secretChatService;

    @Mock
    private SecretAttachmentService secretAttachmentService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SecretChatController(featureFlagService, secretChatService, secretAttachmentService)
                )
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void getSecretChatMessagesReturnsBadRequestForNonPositiveLimit() throws Exception {
        mockMvc.perform(
                        get("/api/secret-chats/{secretChatId}/messages", java.util.UUID.randomUUID())
                                .param("limit", "0")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, secretChatService, secretAttachmentService);
    }

    @Test
    void getSecretChatMessagesReturnsBadRequestForTooLargeLimit() throws Exception {
        mockMvc.perform(
                        get("/api/secret-chats/{secretChatId}/messages", java.util.UUID.randomUUID())
                                .param("limit", "101")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, secretChatService, secretAttachmentService);
    }

    @Test
    void createSecretChatReturnsBadRequestForTooLargeTimer() throws Exception {
        mockMvc.perform(
                        post("/api/secret-chats")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateSecretChatRequest(java.util.UUID.randomUUID(), "pub-key", 604_801)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, secretChatService, secretAttachmentService);
    }

    @Test
    void updateSecretChatTimerReturnsBadRequestForNegativeValue() throws Exception {
        mockMvc.perform(
                        patch("/api/secret-chats/{secretChatId}/timer", java.util.UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(new UpdateSecretChatTimerRequest(-1)))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, secretChatService, secretAttachmentService);
    }

    @Test
    void sendSecretChatMessageReturnsBadRequestForNullAttachmentId() throws Exception {
        mockMvc.perform(
                        post("/api/secret-chats/{secretChatId}/messages", java.util.UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new SendSecretChatMessageRequest("ciphertext", "nonce", Collections.singletonList(null))
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, secretChatService, secretAttachmentService);
    }

    @Test
    void uploadSecretAttachmentReturnsBadRequestForUnsupportedKind() throws Exception {
        mockMvc.perform(
                        multipart("/api/secret-chats/{secretChatId}/attachments/upload", java.util.UUID.randomUUID())
                                .file(new MockMultipartFile("file", "secret.txt", "text/plain", "secret".getBytes()))
                                .param("kind", "audio")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, secretChatService, secretAttachmentService);
    }
}
