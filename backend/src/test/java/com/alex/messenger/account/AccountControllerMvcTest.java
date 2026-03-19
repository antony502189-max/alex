package com.alex.messenger.account;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alex.messenger.account.dto.RequestAccountExport;
import com.alex.messenger.account.dto.ScheduleAccountDeletionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
class AccountControllerMvcTest {

    @Mock
    private AccountService accountService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AccountController(accountService))
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void exportAccountReturnsBadRequestForUnsupportedFormat() throws Exception {
        mockMvc.perform(
                        post("/api/account/export")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new RequestAccountExport("xml", true, null, null)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void exportAccountReturnsBadRequestForInvalidRange() throws Exception {
        mockMvc.perform(
                        post("/api/account/export")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new RequestAccountExport(
                                                "json",
                                                true,
                                                Instant.parse("2026-03-20T12:00:00Z"),
                                                Instant.parse("2026-03-19T12:00:00Z")
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void scheduleDeletionReturnsBadRequestForTooLargeDelay() throws Exception {
        mockMvc.perform(
                        post("/api/account/delete")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new ScheduleAccountDeletionRequest("cleanup", 366)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void scheduleDeletionReturnsBadRequestForTooLongReason() throws Exception {
        mockMvc.perform(
                        post("/api/account/delete")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new ScheduleAccountDeletionRequest("x".repeat(256), 30)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }
}
