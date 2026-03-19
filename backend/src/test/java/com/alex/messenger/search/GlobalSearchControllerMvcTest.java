package com.alex.messenger.search;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alex.messenger.chat.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class GlobalSearchControllerMvcTest {

    @Mock
    private GlobalSearchService globalSearchService;

    @Mock
    private ChatService chatService;

    @Mock
    private PublicPostSearchService publicPostSearchService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new GlobalSearchController(globalSearchService, chatService, publicPostSearchService)
                )
                .setValidator(validator)
                .build();
    }

    @Test
    void searchReturnsBadRequestForNonPositiveLimit() throws Exception {
        mockMvc.perform(
                        get("/api/search/global")
                                .param("query", "alex")
                                .param("limit", "0")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(globalSearchService, chatService, publicPostSearchService);
    }

    @Test
    void searchReturnsBadRequestForTooLongQuery() throws Exception {
        mockMvc.perform(
                        get("/api/search/global")
                                .param("query", "a".repeat(256))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(globalSearchService, chatService, publicPostSearchService);
    }

    @Test
    void discoverPublicChatsReturnsBadRequestForTooLargeLimit() throws Exception {
        mockMvc.perform(
                        get("/api/search/public")
                                .param("query", "alex")
                                .param("limit", "21")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(globalSearchService, chatService, publicPostSearchService);
    }

    @Test
    void discoverPublicChatsReturnsBadRequestForTooLongQuery() throws Exception {
        mockMvc.perform(
                        get("/api/search/public")
                                .param("query", "a".repeat(256))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(globalSearchService, chatService, publicPostSearchService);
    }

    @Test
    void searchPublicPostsReturnsBadRequestForTooLargeLimit() throws Exception {
        mockMvc.perform(
                        get("/api/search/public-posts")
                                .param("query", "alex")
                                .param("limit", "51")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(globalSearchService, chatService, publicPostSearchService);
    }

    @Test
    void searchPublicPostsReturnsBadRequestForTooLongQuery() throws Exception {
        mockMvc.perform(
                        get("/api/search/public-posts")
                                .param("query", "a".repeat(256))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(globalSearchService, chatService, publicPostSearchService);
    }
}
