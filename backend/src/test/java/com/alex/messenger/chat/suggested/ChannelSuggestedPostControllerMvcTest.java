package com.alex.messenger.chat.suggested;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ChannelSuggestedPostControllerMvcTest {

    @Mock
    private ChannelSuggestedPostService channelSuggestedPostService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ChannelSuggestedPostController(channelSuggestedPostService))
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void listSuggestedPostsReturnsBadRequestForNonPositiveLimit() throws Exception {
        mockMvc.perform(
                        get("/api/channels/{chatId}/suggested-posts", java.util.UUID.randomUUID())
                                .param("limit", "0")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(channelSuggestedPostService);
    }

    @Test
    void listSuggestedPostsReturnsBadRequestForTooLargeLimit() throws Exception {
        mockMvc.perform(
                        get("/api/channels/{chatId}/suggested-posts", java.util.UUID.randomUUID())
                                .param("limit", "101")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(channelSuggestedPostService);
    }

    @Test
    void createSuggestedPostReturnsBadRequestForNullAttachmentId() throws Exception {
        mockMvc.perform(
                        post("/api/channels/{chatId}/suggested-posts", java.util.UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "text", "hello",
                                        "attachmentIds", new Object[] { java.util.UUID.randomUUID(), null }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(channelSuggestedPostService);
    }

    @Test
    void createSuggestedPostReturnsBadRequestForNullEntityEntry() throws Exception {
        mockMvc.perform(
                        post("/api/channels/{chatId}/suggested-posts", java.util.UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of(
                                        "text", "hello",
                                        "entities", new Object[] { null }
                                )))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(channelSuggestedPostService);
    }
}
