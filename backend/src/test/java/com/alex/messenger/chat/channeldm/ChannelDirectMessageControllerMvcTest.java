package com.alex.messenger.chat.channeldm;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class ChannelDirectMessageControllerMvcTest {

    @Mock
    private ChannelDirectMessageService channelDirectMessageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ChannelDirectMessageController(channelDirectMessageService))
                .setValidator(validator)
                .build();
    }

    @Test
    void listDirectMessagesReturnsBadRequestForNonPositiveLimit() throws Exception {
        mockMvc.perform(
                        get("/api/channels/{chatId}/direct-messages", java.util.UUID.randomUUID())
                                .param("limit", "0")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(channelDirectMessageService);
    }

    @Test
    void listDirectMessageTopicsReturnsBadRequestForTooLargeLimit() throws Exception {
        mockMvc.perform(
                        get("/api/channels/{chatId}/direct-messages/topics", java.util.UUID.randomUUID())
                                .param("limit", "101")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(channelDirectMessageService);
    }
}
