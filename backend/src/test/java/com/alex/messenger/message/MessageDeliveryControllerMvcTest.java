package com.alex.messenger.message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alex.messenger.message.dto.AcknowledgeMessageDeliveryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MessageDeliveryControllerMvcTest {

    @Mock
    private MessageDeliveryService messageDeliveryService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        UUID currentUserId = UUID.randomUUID();
        UUID currentSessionId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(currentUserId.toString(), "test");
        authentication.setDetails(currentSessionId.toString());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new MessageDeliveryController(messageDeliveryService))
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acknowledgeDeliveryReturnsBadRequestWhenNoAcknowledgementModeProvided() throws Exception {
        mockMvc.perform(
                        post("/api/messages/delivery")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new AcknowledgeMessageDeliveryRequest(null, null, null)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageDeliveryService);
    }

    @Test
    void acknowledgeDeliveryReturnsBadRequestWhenModesAreCombined() throws Exception {
        mockMvc.perform(
                        post("/api/messages/delivery")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new AcknowledgeMessageDeliveryRequest(
                                                List.of(UUID.randomUUID()),
                                                UUID.randomUUID(),
                                                UUID.randomUUID()
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(messageDeliveryService);
    }

    @Test
    void acknowledgeDeliveryPropagatesBadRequestFromService() throws Exception {
        when(messageDeliveryService.acknowledgeDelivery(any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery boundary acknowledgement is available only in direct chats"));

        mockMvc.perform(
                        post("/api/messages/delivery")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new AcknowledgeMessageDeliveryRequest(null, UUID.randomUUID(), UUID.randomUUID())
                                ))
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void acknowledgeDeliveryPropagatesNotFoundFromService() throws Exception {
        when(messageDeliveryService.acknowledgeDelivery(any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        mockMvc.perform(
                        post("/api/messages/delivery")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new AcknowledgeMessageDeliveryRequest(null, UUID.randomUUID(), UUID.randomUUID())
                                ))
                )
                .andExpect(status().isNotFound());
    }
}
