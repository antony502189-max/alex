package com.alex.messenger.bot;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.bot.dto.CompleteBotPreCheckoutRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class BotControllerMvcTest {

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private BotService botService;

    @Mock
    private BotMessageActionService botMessageActionService;

    @Mock
    private BotWebAppService botWebAppService;

    @Mock
    private BotPaymentService botPaymentService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new BotController(
                                featureFlagService,
                                botService,
                                botMessageActionService,
                                botWebAppService,
                                botPaymentService
                        )
                )
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void getInlineResultsReturnsBadRequestForTooLongQuery() throws Exception {
        mockMvc.perform(
                        get("/api/bots/inline/{username}", "alex_echo_bot")
                                .param("query", "a".repeat(256))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(botService, botMessageActionService, botWebAppService, botPaymentService);
    }

    @Test
    void getWebAppLaunchReturnsBadRequestForInvalidStartParameter() throws Exception {
        mockMvc.perform(
                        get("/api/bots/{botUserId}/web-app-launch", java.util.UUID.randomUUID())
                                .param("startParameter", "bad value!")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(botService, botMessageActionService, botWebAppService, botPaymentService);
    }

    @Test
    void resolveWebAppContextReturnsBadRequestForTooLongInitData() throws Exception {
        mockMvc.perform(
                        post("/api/bots/web-app/context")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(java.util.Map.of(
                                        "initData", "a".repeat(8193),
                                        "signature", "sig"
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(botService, botMessageActionService, botWebAppService, botPaymentService);
    }

    @Test
    void sendWebAppDataReturnsBadRequestForBlankPayload() throws Exception {
        mockMvc.perform(
                        post("/api/bots/web-app/send-data")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(java.util.Map.of(
                                        "initData", "init",
                                        "signature", "sig",
                                        "data", " "
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(botService, botMessageActionService, botWebAppService, botPaymentService);
    }

    @Test
    void createWebAppQueryReturnsBadRequestForTooLongSignature() throws Exception {
        mockMvc.perform(
                        post("/api/bots/web-app/query")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(java.util.Map.of(
                                        "initData", "init",
                                        "signature", "a".repeat(513),
                                        "queryText", "share"
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(botService, botMessageActionService, botWebAppService, botPaymentService);
    }

    @Test
    void completePreCheckoutReturnsBadRequestForNegativeTipAmount() throws Exception {
        mockMvc.perform(
                        post("/api/bots/payments/pre-checkout/{preCheckoutQueryId}/complete", java.util.UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CompleteBotPreCheckoutRequest(
                                                "Alex",
                                                null,
                                                "payer@example.com",
                                                null,
                                                null,
                                                -1L
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(botService, botMessageActionService, botWebAppService, botPaymentService);
    }
}
