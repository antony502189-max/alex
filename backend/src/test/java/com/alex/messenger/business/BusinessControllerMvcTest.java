package com.alex.messenger.business;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class BusinessControllerMvcTest {

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private BusinessService businessService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new BusinessController(featureFlagService, businessService))
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void updateProfileReturnsBadRequestForNullBusinessHourSlot() throws Exception {
        mockMvc.perform(
                        put("/api/business/profile")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "businessHours", new Object[] { null }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, businessService);
    }

    @Test
    void replaceTagsReturnsBadRequestForNullTagEntry() throws Exception {
        mockMvc.perform(
                        put("/api/business/chats/{chatId}/tags", java.util.UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "tags", new Object[] { null }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(featureFlagService, businessService);
    }
}
