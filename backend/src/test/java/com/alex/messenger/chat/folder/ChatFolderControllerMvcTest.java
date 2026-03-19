package com.alex.messenger.chat.folder;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alex.messenger.chat.dto.UpsertChatFolderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
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
class ChatFolderControllerMvcTest {

    @Mock
    private ChatFolderService chatFolderService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatFolderController(chatFolderService))
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void createReturnsBadRequestForBlankTitle() throws Exception {
        mockMvc.perform(
                        post("/api/folders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpsertChatFolderRequest(
                                                " ",
                                                0,
                                                null,
                                                null,
                                                null,
                                                List.of("DIRECT"),
                                                true,
                                                false,
                                                false,
                                                false,
                                                true,
                                                false,
                                                true,
                                                false,
                                                true
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatFolderService);
    }

    @Test
    void createReturnsBadRequestForNegativePosition() throws Exception {
        mockMvc.perform(
                        post("/api/folders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpsertChatFolderRequest(
                                                "Work",
                                                -1,
                                                null,
                                                null,
                                                null,
                                                List.of("DIRECT"),
                                                true,
                                                false,
                                                false,
                                                false,
                                                true,
                                                false,
                                                true,
                                                false,
                                                true
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatFolderService);
    }

    @Test
    void createReturnsBadRequestForNullIncludedChatId() throws Exception {
        mockMvc.perform(
                        post("/api/folders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpsertChatFolderRequest(
                                                "Work",
                                                0,
                                                null,
                                                Collections.singletonList(null),
                                                null,
                                                List.of("DIRECT"),
                                                true,
                                                false,
                                                false,
                                                false,
                                                true,
                                                false,
                                                true,
                                                false,
                                                true
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatFolderService);
    }

    @Test
    void createReturnsBadRequestForUnsupportedIncludedChatType() throws Exception {
        mockMvc.perform(
                        post("/api/folders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpsertChatFolderRequest(
                                                "Work",
                                                0,
                                                null,
                                                null,
                                                null,
                                                List.of("TEAM"),
                                                true,
                                                false,
                                                false,
                                                false,
                                                true,
                                                false,
                                                true,
                                                false,
                                                true
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatFolderService);
    }
}
