package com.alex.messenger.auth;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alex.messenger.auth.dto.UpdatePushTokenRequest;
import com.alex.messenger.auth.dto.VerifyLoginCodeRequest;
import com.alex.messenger.auth.dto.VerifyPasskeyLoginRequest;
import com.alex.messenger.auth.dto.VerifyPasskeyRegistrationRequest;
import com.alex.messenger.auth.dto.VerifyPhoneChangeRequest;
import com.alex.messenger.auth.session.UserSessionService;
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
class AuthControllerMvcTest {

    @Mock
    private AuthService authService;

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private PasskeyService passkeyService;

    @Mock
    private PhoneChangeService phoneChangeService;

    @Mock
    private IdentityTokenService identityTokenService;

    @Mock
    private AuthSecurityEventService authSecurityEventService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(
                                authService,
                                userSessionService,
                                passkeyService,
                                phoneChangeService,
                                identityTokenService,
                                authSecurityEventService
                        )
                )
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void verifyCodeReturnsBadRequestForTooShortCode() throws Exception {
        mockMvc.perform(
                        post("/api/auth/verify-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new VerifyLoginCodeRequest(UUID.randomUUID(), "123")
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                authService,
                userSessionService,
                passkeyService,
                phoneChangeService,
                identityTokenService,
                authSecurityEventService
        );
    }

    @Test
    void verifyPhoneChangeReturnsBadRequestForTooShortCode() throws Exception {
        mockMvc.perform(
                        post("/api/auth/change-phone/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new VerifyPhoneChangeRequest(UUID.randomUUID(), "123")
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                authService,
                userSessionService,
                passkeyService,
                phoneChangeService,
                identityTokenService,
                authSecurityEventService
        );
    }

    @Test
    void verifyPasskeyLoginReturnsBadRequestForNegativeSignCount() throws Exception {
        mockMvc.perform(
                        post("/api/auth/passkeys/login/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new VerifyPasskeyLoginRequest(
                                                UUID.randomUUID(),
                                                "challenge",
                                                "credential-1",
                                                -1L,
                                                "Pixel 10",
                                                "android",
                                                "1.0.0"
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                authService,
                userSessionService,
                passkeyService,
                phoneChangeService,
                identityTokenService,
                authSecurityEventService
        );
    }

    @Test
    void verifyPasskeyRegistrationReturnsBadRequestForNegativeSignCount() throws Exception {
        mockMvc.perform(
                        post("/api/auth/passkeys/register/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new VerifyPasskeyRegistrationRequest(
                                                UUID.randomUUID(),
                                                "challenge",
                                                "credential-1",
                                                "public-key",
                                                "usb",
                                                "MacBook",
                                                -1L
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                authService,
                userSessionService,
                passkeyService,
                phoneChangeService,
                identityTokenService,
                authSecurityEventService
        );
    }

    @Test
    void updatePushTokenReturnsBadRequestForUnsupportedProvider() throws Exception {
        mockMvc.perform(
                        put("/api/auth/sessions/current/push-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpdatePushTokenRequest("fcm", "ExponentPushToken[abc]")
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                authService,
                userSessionService,
                passkeyService,
                phoneChangeService,
                identityTokenService,
                authSecurityEventService
        );
    }
}
