package com.alex.messenger.bot;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alex.messenger.feature.FeatureFlagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
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
class BotApiControllerMvcTest {

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private DeveloperBotService developerBotService;

    @Mock
    private BotUpdateService botUpdateService;

    @Mock
    private BotApiService botApiService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new BotApiController(
                                featureFlagService,
                                developerBotService,
                                botUpdateService,
                                botApiService
                        )
                )
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void getUpdatesReturnsBadRequestForNegativeOffset() throws Exception {
        mockMvc.perform(get("/api/bot-api/updates").param("offset", "-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void getUpdatesReturnsBadRequestForZeroLimit() throws Exception {
        mockMvc.perform(get("/api/bot-api/updates").param("limit", "0"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void getUpdatesReturnsBadRequestForNegativeTimeout() throws Exception {
        mockMvc.perform(get("/api/bot-api/updates").param("timeoutSeconds", "-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void setMyCommandsReturnsBadRequestForNullCommandEntry() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/set-my-commands")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of("commands", new Object[] { null })))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void answerInlineQueryReturnsBadRequestForNegativeCacheTime() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/answer-inline-query")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "query", "weather",
                                        "cacheTimeSeconds", -1,
                                        "results", new Object[] {
                                                Map.of(
                                                        "resultId", "forecast",
                                                        "title", "Forecast",
                                                        "text", "Sunny"
                                                )
                                        }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void answerInlineQueryReturnsBadRequestForNullResultEntry() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/answer-inline-query")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "query", "weather",
                                        "results", new Object[] { null }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void answerWebAppQueryReturnsBadRequestWhenPayloadEmpty() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/answer-web-app-query")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "webAppQueryId", UUID.randomUUID()
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void answerWebAppQueryReturnsBadRequestForServiceMessageType() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/answer-web-app-query")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "webAppQueryId", UUID.randomUUID(),
                                        "text", "hello",
                                        "messageType", "SERVICE_MESSAGE"
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void answerWebAppQueryReturnsBadRequestForStructuredPayloadWithAttachment() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/answer-web-app-query")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "webAppQueryId", UUID.randomUUID(),
                                        "messageType", "LOCATION",
                                        "location", Map.of(
                                                "latitude", 53.9,
                                                "longitude", 27.56
                                        ),
                                        "attachmentIds", new Object[] { UUID.randomUUID() }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void answerCallbackQueryReturnsBadRequestForInvalidRedirectUrl() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/answer-callback-query")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "callbackQueryId", UUID.randomUUID(),
                                        "redirectUrl", "ftp://example.com/done"
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void answerPreCheckoutQueryReturnsBadRequestWhenDeclinedWithoutText() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/answer-pre-checkout-query")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "preCheckoutQueryId", UUID.randomUUID(),
                                        "ok", false
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void sendMessageReturnsBadRequestForNullActionEntry() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/send-message")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "chatId", UUID.randomUUID(),
                                        "text", "hello",
                                        "actions", new Object[] { null }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void sendMessageReturnsBadRequestForUnsupportedActionType() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/send-message")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "chatId", UUID.randomUUID(),
                                        "text", "hello",
                                        "actions", new Object[] {
                                                Map.of(
                                                        "actionType", "PAY",
                                                        "buttonText", "Pay"
                                                )
                                        }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void sendMessageReturnsBadRequestForInvalidActionUrl() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/send-message")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "chatId", UUID.randomUUID(),
                                        "text", "hello",
                                        "actions", new Object[] {
                                                Map.of(
                                                        "actionType", "URL",
                                                        "buttonText", "Open",
                                                        "targetUrl", "ftp://example.com"
                                                )
                                        }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void sendMessageReturnsBadRequestWhenTargetIsMissing() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/send-message")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "text", "hello"
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void sendMessageReturnsBadRequestWhenPayloadEmpty() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/send-message")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "chatId", UUID.randomUUID()
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void sendMessageReturnsBadRequestForServiceMessageType() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/send-message")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "chatId", UUID.randomUUID(),
                                        "text", "hello",
                                        "messageType", "SERVICE_MESSAGE"
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void sendMessageReturnsBadRequestForConflictingStructuredPayloads() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/send-message")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "chatId", UUID.randomUUID(),
                                        "messageType", "LOCATION",
                                        "location", Map.of(
                                                "latitude", 53.9,
                                                "longitude", 27.56
                                        ),
                                        "contactCard", Map.of(
                                                "firstName", "Alex",
                                                "phoneNumber", "+123"
                                        )
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void sendMessageReturnsBadRequestForStructuredPayloadWithAttachment() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/send-message")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "chatId", UUID.randomUUID(),
                                        "messageType", "LOCATION",
                                        "location", Map.of(
                                                "latitude", 53.9,
                                                "longitude", 27.56
                                        ),
                                        "attachmentIds", new Object[] { UUID.randomUUID() }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void editMessageTextReturnsBadRequestForNullEntityEntry() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/edit-message-text")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "messageId", UUID.randomUUID(),
                                        "text", "updated",
                                        "entities", new Object[] { null }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void sendMediaGroupReturnsBadRequestForNullAttachmentId() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/send-media-group")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "chatId", UUID.randomUUID(),
                                        "attachmentIds", new Object[] { UUID.randomUUID(), null }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void sendMediaGroupReturnsBadRequestWhenTargetIsMissing() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/send-media-group")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "attachmentIds", new Object[] { UUID.randomUUID(), UUID.randomUUID() }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void sendInvoiceReturnsBadRequestForNegativeAmount() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/send-invoice")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "recipientUserId", UUID.randomUUID(),
                                        "title", "Invoice",
                                        "amountUnits", -1,
                                        "invoicePayload", "payload"
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void sendInvoiceReturnsBadRequestWhenTargetIsMissing() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/send-invoice")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "title", "Invoice",
                                        "amountUnits", 100,
                                        "invoicePayload", "payload"
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void sendInvoiceReturnsBadRequestForNullShippingOptionEntry() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/send-invoice")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "recipientUserId", UUID.randomUUID(),
                                        "title", "Invoice",
                                        "amountUnits", 100,
                                        "invoicePayload", "payload",
                                        "shippingOptions", new Object[] { null }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void sendInvoiceReturnsBadRequestForPastExpiry() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/send-invoice")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "recipientUserId", UUID.randomUUID(),
                                        "title", "Invoice",
                                        "amountUnits", 100,
                                        "invoicePayload", "payload",
                                        "expiresAt", "2000-01-01T00:00:00Z"
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void sendInvoiceReturnsBadRequestForSuggestedTipsWithoutMaxTip() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/send-invoice")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "recipientUserId", UUID.randomUUID(),
                                        "title", "Invoice",
                                        "amountUnits", 100,
                                        "invoicePayload", "payload",
                                        "suggestedTipAmounts", new Object[] { 5, 10 }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void sendInvoiceReturnsBadRequestForShippingOptionsWithoutShippingAddress() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/send-invoice")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "recipientUserId", UUID.randomUUID(),
                                        "title", "Invoice",
                                        "amountUnits", 100,
                                        "invoicePayload", "payload",
                                        "shippingOptions", new Object[] {
                                                Map.of(
                                                        "optionId", "standard",
                                                        "title", "Standard",
                                                        "amountUnits", 0
                                                )
                                        }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void sendInvoiceReturnsBadRequestForFlexibleInvoiceWithoutShippingAddress() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/send-invoice")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "recipientUserId", UUID.randomUUID(),
                                        "title", "Invoice",
                                        "amountUnits", 100,
                                        "invoicePayload", "payload",
                                        "flexible", true,
                                        "shippingOptions", new Object[] {
                                                Map.of(
                                                        "optionId", "standard",
                                                        "title", "Standard",
                                                        "amountUnits", 0
                                                )
                                        }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }

    @Test
    void sendInvoiceReturnsBadRequestForBlankProviderDataValue() throws Exception {
        mockMvc.perform(
                        post("/api/bot-api/send-invoice")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "recipientUserId", UUID.randomUUID(),
                                        "title", "Invoice",
                                        "amountUnits", 100,
                                        "invoicePayload", "payload",
                                        "providerData", Map.of("merchant", " ")
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService, botUpdateService, botApiService);
    }
}
