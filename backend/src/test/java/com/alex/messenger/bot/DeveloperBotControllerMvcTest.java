package com.alex.messenger.bot;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alex.messenger.bot.dto.CreateDeveloperBotRequest;
import com.alex.messenger.bot.dto.UpdateBotWebhookRequest;
import com.alex.messenger.bot.dto.UpdateDeveloperBotRequest;
import com.alex.messenger.feature.FeatureFlagService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class DeveloperBotControllerMvcTest {

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private DeveloperBotService developerBotService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new DeveloperBotController(featureFlagService, developerBotService))
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void createBotReturnsBadRequestForBlankDisplayName() throws Exception {
        mockMvc.perform(
                        post("/api/developer/bots")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateDeveloperBotRequest(
                                                " ",
                                                "weatherbot",
                                                null,
                                                null,
                                                true,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService);
    }

    @Test
    void createBotReturnsBadRequestForUsernameWithoutBotSuffix() throws Exception {
        mockMvc.perform(
                        post("/api/developer/bots")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateDeveloperBotRequest(
                                                "Weather Bot",
                                                "weather",
                                                null,
                                                null,
                                                true,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService);
    }

    @Test
    void createBotReturnsBadRequestForInvalidWebAppUrl() throws Exception {
        mockMvc.perform(
                        post("/api/developer/bots")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateDeveloperBotRequest(
                                                "Weather Bot",
                                                "weatherbot",
                                                null,
                                                null,
                                                true,
                                                "ftp://example.com/app"
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService);
    }

    @Test
    void updateBotReturnsBadRequestWhenNoChangesProvided() throws Exception {
        mockMvc.perform(
                        patch("/api/developer/bots/{botUserId}", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService);
    }

    @Test
    void updateBotReturnsBadRequestForInvalidWebAppUrl() throws Exception {
        mockMvc.perform(
                        patch("/api/developer/bots/{botUserId}", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpdateDeveloperBotRequest(
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                "ftp://example.com/web-app"
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService);
    }

    @Test
    void updateWebhookReturnsBadRequestForBlankWebhookUrl() throws Exception {
        mockMvc.perform(
                        put("/api/developer/bots/{botUserId}/webhook", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(new UpdateBotWebhookRequest("  ", null)))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService);
    }

    @Test
    void updateWebhookReturnsBadRequestForInvalidWebhookUrl() throws Exception {
        mockMvc.perform(
                        put("/api/developer/bots/{botUserId}/webhook", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(new UpdateBotWebhookRequest("ftp://example.com/hook", null)))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(developerBotService);
    }
}
