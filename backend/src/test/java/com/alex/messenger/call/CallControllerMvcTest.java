package com.alex.messenger.call;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alex.messenger.call.dto.CreateCallJoinLinkRequest;
import com.alex.messenger.call.dto.CreateCallCommentRequest;
import com.alex.messenger.call.dto.CreateCallReactionRequest;
import com.alex.messenger.call.dto.CallSignalRequest;
import com.alex.messenger.call.dto.StartCallRequest;
import com.alex.messenger.call.dto.UpdateCallParticipantModerationRequest;
import com.alex.messenger.feature.FeatureFlagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class CallControllerMvcTest {

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private CallService callService;

    @Mock
    private CallRtcConfigService callRtcConfigService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new CallController(featureFlagService, callService, callRtcConfigService))
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void startCallReturnsBadRequestForUnsupportedMode() throws Exception {
        mockMvc.perform(
                        post("/api/calls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new StartCallRequest(UUID.randomUUID(), "VOICE", "UNSUPPORTED", false)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, callService, callRtcConfigService);
    }

    @Test
    void createJoinLinkReturnsBadRequestForPastExpiration() throws Exception {
        mockMvc.perform(
                        post("/api/calls/links")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateCallJoinLinkRequest(
                                                UUID.randomUUID(),
                                                "VOICE",
                                                "VOICE_CHAT",
                                                "Townhall",
                                                Instant.parse("2000-01-01T00:00:00Z")
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, callService, callRtcConfigService);
    }

    @Test
    void commentReturnsBadRequestForBlankContent() throws Exception {
        mockMvc.perform(
                        post("/api/calls/{callId}/comments", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(new CreateCallCommentRequest(" ")))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, callService, callRtcConfigService);
    }

    @Test
    void reactReturnsBadRequestForBlankEmoji() throws Exception {
        mockMvc.perform(
                        post("/api/calls/{callId}/reactions", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(new CreateCallReactionRequest(" ")))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, callService, callRtcConfigService);
    }

    @Test
    void moderateParticipantReturnsBadRequestWhenNoChangesProvided() throws Exception {
        mockMvc.perform(
                        post("/api/calls/{callId}/participants/{userId}/moderation", UUID.randomUUID(), UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpdateCallParticipantModerationRequest(null, null, null, null, null)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, callService, callRtcConfigService);
    }

    @Test
    void sendSignalReturnsBadRequestForBlankPayload() throws Exception {
        mockMvc.perform(
                        post("/api/calls/{callId}/signal", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CallSignalRequest(UUID.randomUUID(), "offer", " ")
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, callService, callRtcConfigService);
    }

    @Test
    void sendSignalReturnsBadRequestForTooLongSignalType() throws Exception {
        mockMvc.perform(
                        post("/api/calls/{callId}/signal", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CallSignalRequest(UUID.randomUUID(), "x".repeat(33), "{\"sdp\":\"x\"}")
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, callService, callRtcConfigService);
    }

    @Test
    void sendSignalReturnsBadRequestForUnsupportedSignalType() throws Exception {
        mockMvc.perform(
                        post("/api/calls/{callId}/signal", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CallSignalRequest(UUID.randomUUID(), "teleport", "{\"value\":1}")
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, callService, callRtcConfigService);
    }

    @Test
    void getRecentCallsReturnsBadRequestForNonPositiveLimit() throws Exception {
        mockMvc.perform(
                        get("/api/calls/recent")
                                .param("limit", "0")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, callService, callRtcConfigService);
    }

    @Test
    void commentsReturnsBadRequestForTooLargeLimit() throws Exception {
        mockMvc.perform(
                        get("/api/calls/{callId}/comments", UUID.randomUUID())
                                .param("limit", "101")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, callService, callRtcConfigService);
    }

    @Test
    void reactionsReturnsBadRequestForNonPositiveLimit() throws Exception {
        mockMvc.perform(
                        get("/api/calls/{callId}/reactions", UUID.randomUUID())
                                .param("limit", "0")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, callService, callRtcConfigService);
    }
}
