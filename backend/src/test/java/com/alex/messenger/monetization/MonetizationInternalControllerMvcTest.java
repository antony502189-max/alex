package com.alex.messenger.monetization;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alex.messenger.feature.FeatureFlagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
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
class MonetizationInternalControllerMvcTest {

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private MonetizationService monetizationService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new MonetizationInternalController(featureFlagService, monetizationService))
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void reconcileProviderStatusesReturnsBadRequestForNullUpdateEntry() throws Exception {
        mockMvc.perform(
                        post("/api/internal/monetization/channels/{chatId}/provider-reconciliation", java.util.UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "updates", new Object[] { null }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, monetizationService);
    }
}
