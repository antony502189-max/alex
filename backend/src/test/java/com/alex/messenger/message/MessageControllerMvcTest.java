package com.alex.messenger.message;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alex.messenger.message.dto.CreatePollMessageRequest;
import com.alex.messenger.message.dto.CreateRepeatingMessageRequest;
import com.alex.messenger.message.dto.EditMessageRequest;
import com.alex.messenger.message.dto.ForwardMessageRequest;
import com.alex.messenger.message.dto.MessageLiveLocationPayload;
import com.alex.messenger.message.dto.MessageLocationPayload;
import com.alex.messenger.message.dto.MessageTextEntityPayload;
import com.alex.messenger.message.dto.ScheduleMessageRequest;
import com.alex.messenger.message.dto.SendInlineBotResultRequest;
import com.alex.messenger.message.dto.SendMessageRequest;
import com.alex.messenger.message.dto.UpdateLiveLocationRequest;
import com.alex.messenger.message.dto.VotePollRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class MessageControllerMvcTest {

    @Mock
    private MessageService messageService;

    @Mock
    private MessageTranslationService messageTranslationService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUserId.toString(), "test")
        );
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new MessageController(messageService, messageTranslationService))
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateLiveLocationReturnsBadRequestForInvalidCoordinates() throws Exception {
        mockMvc.perform(
                        patch("/api/messages/{messageId}/live-location", java.util.UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpdateLiveLocationRequest(120.0, 27.56, "Point", "Address")
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void searchMessagesReturnsBadRequestForTooLongQuery() throws Exception {
        mockMvc.perform(
                        get("/api/messages/chat/{chatId}/search", java.util.UUID.randomUUID())
                                .param("query", "a".repeat(256))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void sendMessageReturnsBadRequestWhenTargetMissing() throws Exception {
        mockMvc.perform(
                        post("/api/messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new SendMessageRequest(
                                                null,
                                                null,
                                                null,
                                                null,
                                                "hello",
                                                null,
                                                "TEXT",
                                                List.of(),
                                                null,
                                                null,
                                                null,
                                                List.of(),
                                                null,
                                                false,
                                                null,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void sendMessageReturnsBadRequestWhenPayloadEmpty() throws Exception {
        mockMvc.perform(
                        post("/api/messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new SendMessageRequest(
                                                java.util.UUID.randomUUID(),
                                                null,
                                                null,
                                                null,
                                                "   ",
                                                "   ",
                                                "TEXT",
                                                List.of(),
                                                null,
                                                null,
                                                null,
                                                List.of(),
                                                null,
                                                false,
                                                null,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void sendMessageReturnsBadRequestForMultipleStructuredPayloads() throws Exception {
        mockMvc.perform(
                        post("/api/messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new SendMessageRequest(
                                                java.util.UUID.randomUUID(),
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                "LOCATION",
                                                List.of(),
                                                new MessageLocationPayload(53.9, 27.56, "Point", "Address"),
                                                new MessageLiveLocationPayload(
                                                        53.9,
                                                        27.56,
                                                        "Moving",
                                                        "Address",
                                                        300,
                                                        null,
                                                        null,
                                                        null,
                                                        null
                                                ),
                                                null,
                                                List.of(),
                                                null,
                                                false,
                                                null,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void sendMessageReturnsBadRequestWhenStructuredPayloadCombinedWithAttachment() throws Exception {
        mockMvc.perform(
                        post("/api/messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new SendMessageRequest(
                                                java.util.UUID.randomUUID(),
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                "LOCATION",
                                                List.of(),
                                                new MessageLocationPayload(53.9, 27.56, "Point", "Address"),
                                                null,
                                                null,
                                                List.of(java.util.UUID.randomUUID()),
                                                null,
                                                false,
                                                null,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void sendMessageReturnsBadRequestForInvalidLiveLocationPayload() throws Exception {
        mockMvc.perform(
                        post("/api/messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new SendMessageRequest(
                                                java.util.UUID.randomUUID(),
                                                null,
                                                null,
                                                null,
                                                "",
                                                null,
                                                "LIVE_LOCATION",
                                                List.of(),
                                                null,
                                                new MessageLiveLocationPayload(
                                                        53.9,
                                                        27.56,
                                                        "Point",
                                                        "Address",
                                                        30,
                                                        null,
                                                        null,
                                                        null,
                                                        null
                                                ),
                                                null,
                                                List.of(),
                                                null,
                                                false,
                                                null,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void sendPollReturnsBadRequestWhenTargetMissing() throws Exception {
        mockMvc.perform(
                        post("/api/messages/poll")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreatePollMessageRequest(
                                                null,
                                                null,
                                                null,
                                                null,
                                                "Question?",
                                                List.of("A", "B"),
                                                false,
                                                false,
                                                null,
                                                null,
                                                false,
                                                null,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void forwardMessageReturnsBadRequestWhenTargetMissing() throws Exception {
        mockMvc.perform(
                        post("/api/messages/forward")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new ForwardMessageRequest(
                                                null,
                                                null,
                                                null,
                                                null,
                                                java.util.UUID.randomUUID(),
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void sendInlineBotResultReturnsBadRequestWhenTargetMissing() throws Exception {
        mockMvc.perform(
                        post("/api/messages/inline-bot-result")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new SendInlineBotResultRequest(
                                                null,
                                                null,
                                                null,
                                                null,
                                                "alex_echo_bot",
                                                "result-1",
                                                "query",
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void sendInlineBotResultReturnsBadRequestForBlankBotUsername() throws Exception {
        mockMvc.perform(
                        post("/api/messages/inline-bot-result")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new SendInlineBotResultRequest(
                                                java.util.UUID.randomUUID(),
                                                null,
                                                null,
                                                null,
                                                "   ",
                                                "result-1",
                                                "query",
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void sendInlineBotResultReturnsBadRequestForBlankResultId() throws Exception {
        mockMvc.perform(
                        post("/api/messages/inline-bot-result")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new SendInlineBotResultRequest(
                                                java.util.UUID.randomUUID(),
                                                null,
                                                null,
                                                null,
                                                "alex_echo_bot",
                                                "   ",
                                                "query",
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void sendInlineBotResultReturnsBadRequestForTooLongQuery() throws Exception {
        mockMvc.perform(
                        post("/api/messages/inline-bot-result")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new SendInlineBotResultRequest(
                                                java.util.UUID.randomUUID(),
                                                null,
                                                null,
                                                null,
                                                "alex_echo_bot",
                                                "result-1",
                                                "a".repeat(256),
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void sendWhenOnlineReturnsBadRequestForServiceMessageType() throws Exception {
        mockMvc.perform(
                        post("/api/messages/send-when-online")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new SendMessageRequest(
                                                java.util.UUID.randomUUID(),
                                                null,
                                                null,
                                                null,
                                                "hello",
                                                null,
                                                "SERVICE_MESSAGE",
                                                List.of(),
                                                null,
                                                null,
                                                null,
                                                List.of(),
                                                null,
                                                false,
                                                null,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void votePollReturnsBadRequestForNullOptionId() throws Exception {
        mockMvc.perform(
                        post("/api/messages/{messageId}/poll/vote", java.util.UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new VotePollRequest(Collections.singletonList(null))
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void sendMessageReturnsBadRequestForNullAttachmentId() throws Exception {
        mockMvc.perform(
                        post("/api/messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new SendMessageRequest(
                                                java.util.UUID.randomUUID(),
                                                null,
                                                null,
                                                null,
                                                "hello",
                                                null,
                                                "TEXT",
                                                List.of(),
                                                null,
                                                null,
                                                null,
                                                Collections.singletonList(null),
                                                null,
                                                false,
                                                null,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void scheduleMessageReturnsBadRequestWhenTargetMissing() throws Exception {
        mockMvc.perform(
                        post("/api/messages/scheduled")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new ScheduleMessageRequest(
                                                null,
                                                null,
                                                null,
                                                null,
                                                "hello",
                                                null,
                                                "TEXT",
                                                List.of(),
                                                null,
                                                null,
                                                List.of(),
                                                null,
                                                false,
                                                null,
                                                Instant.parse("2999-01-01T00:00:00Z")
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void scheduleMessageReturnsBadRequestWhenPayloadEmpty() throws Exception {
        mockMvc.perform(
                        post("/api/messages/scheduled")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new ScheduleMessageRequest(
                                                java.util.UUID.randomUUID(),
                                                null,
                                                null,
                                                null,
                                                "   ",
                                                "   ",
                                                "TEXT",
                                                List.of(),
                                                null,
                                                null,
                                                List.of(),
                                                null,
                                                false,
                                                null,
                                                Instant.parse("2999-01-01T00:00:00Z")
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void scheduleMessageReturnsBadRequestForNullAttachmentId() throws Exception {
        mockMvc.perform(
                        post("/api/messages/scheduled")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new ScheduleMessageRequest(
                                                java.util.UUID.randomUUID(),
                                                null,
                                                null,
                                                null,
                                                "hello",
                                                null,
                                                "TEXT",
                                                List.of(),
                                                null,
                                                null,
                                                Collections.singletonList(null),
                                                null,
                                                false,
                                                null,
                                                Instant.parse("2999-01-01T00:00:00Z")
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void createRepeatingMessageReturnsBadRequestWhenTargetMissing() throws Exception {
        mockMvc.perform(
                        post("/api/messages/repeating")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateRepeatingMessageRequest(
                                                null,
                                                null,
                                                null,
                                                null,
                                                "hello",
                                                null,
                                                "TEXT",
                                                List.of(),
                                                null,
                                                null,
                                                List.of(),
                                                null,
                                                false,
                                                null,
                                                Instant.parse("2999-01-01T00:00:00Z"),
                                                60,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void createRepeatingMessageReturnsBadRequestWhenPayloadEmpty() throws Exception {
        mockMvc.perform(
                        post("/api/messages/repeating")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateRepeatingMessageRequest(
                                                java.util.UUID.randomUUID(),
                                                null,
                                                null,
                                                null,
                                                "   ",
                                                "   ",
                                                "TEXT",
                                                List.of(),
                                                null,
                                                null,
                                                List.of(),
                                                null,
                                                false,
                                                null,
                                                Instant.parse("2999-01-01T00:00:00Z"),
                                                60,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void createRepeatingMessageReturnsBadRequestForNullAttachmentId() throws Exception {
        mockMvc.perform(
                        post("/api/messages/repeating")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateRepeatingMessageRequest(
                                                java.util.UUID.randomUUID(),
                                                null,
                                                null,
                                                null,
                                                "hello",
                                                null,
                                                "TEXT",
                                                List.of(),
                                                null,
                                                null,
                                                Collections.singletonList(null),
                                                null,
                                                false,
                                                null,
                                                Instant.parse("2999-01-01T00:00:00Z"),
                                                60,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void sendMessageReturnsBadRequestForNullEntity() throws Exception {
        mockMvc.perform(
                        post("/api/messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new SendMessageRequest(
                                                java.util.UUID.randomUUID(),
                                                null,
                                                null,
                                                null,
                                                "hello",
                                                null,
                                                "TEXT",
                                                Collections.singletonList(null),
                                                null,
                                                null,
                                                null,
                                                List.of(),
                                                null,
                                                false,
                                                null,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void scheduleMessageReturnsBadRequestForNullEntity() throws Exception {
        mockMvc.perform(
                        post("/api/messages/scheduled")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new ScheduleMessageRequest(
                                                java.util.UUID.randomUUID(),
                                                null,
                                                null,
                                                null,
                                                "hello",
                                                null,
                                                "TEXT",
                                                Collections.singletonList(null),
                                                null,
                                                null,
                                                List.of(),
                                                null,
                                                false,
                                                null,
                                                Instant.parse("2999-01-01T00:00:00Z")
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void createRepeatingMessageReturnsBadRequestForNullEntity() throws Exception {
        mockMvc.perform(
                        post("/api/messages/repeating")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateRepeatingMessageRequest(
                                                java.util.UUID.randomUUID(),
                                                null,
                                                null,
                                                null,
                                                "hello",
                                                null,
                                                "TEXT",
                                                Collections.singletonList(null),
                                                null,
                                                null,
                                                List.of(),
                                                null,
                                                false,
                                                null,
                                                Instant.parse("2999-01-01T00:00:00Z"),
                                                60,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void editMessageReturnsBadRequestForNullEntity() throws Exception {
        mockMvc.perform(
                        patch("/api/messages/{messageId}", java.util.UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new EditMessageRequest("updated", Collections.singletonList(null))
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void editMessageReturnsBadRequestForInvalidEntityPayload() throws Exception {
        mockMvc.perform(
                        patch("/api/messages/{messageId}", java.util.UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new EditMessageRequest(
                                                "updated",
                                                List.of(new MessageTextEntityPayload("BOLD", 0, 0))
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void getHistoryReturnsBadRequestForNonPositiveLimit() throws Exception {
        mockMvc.perform(
                        get("/api/messages/chat/{chatId}", java.util.UUID.randomUUID())
                                .param("limit", "0")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void getHistoryReturnsBadRequestForTooLargeLimit() throws Exception {
        mockMvc.perform(
                        get("/api/messages/chat/{chatId}", java.util.UUID.randomUUID())
                                .param("limit", "101")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void searchMessagesReturnsBadRequestForNonPositiveLimit() throws Exception {
        mockMvc.perform(
                        get("/api/messages/chat/{chatId}/search", java.util.UUID.randomUUID())
                                .param("query", "hello")
                                .param("limit", "0")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }

    @Test
    void searchMessagesReturnsBadRequestForTooLargeLimit() throws Exception {
        mockMvc.perform(
                        get("/api/messages/chat/{chatId}/search", java.util.UUID.randomUUID())
                                .param("query", "hello")
                                .param("limit", "101")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageService, messageTranslationService);
    }
}
