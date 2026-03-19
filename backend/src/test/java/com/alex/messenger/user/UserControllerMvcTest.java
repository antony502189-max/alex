package com.alex.messenger.user;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alex.messenger.user.dto.ImportContactsRequest;
import com.alex.messenger.user.dto.ImportedPhoneContactPayload;
import com.alex.messenger.user.dto.ReplaceCloseFriendsRequest;
import com.alex.messenger.user.dto.UpdatePrivacyExceptionsRequest;
import com.alex.messenger.user.dto.UpdateProfileTabRequest;
import com.alex.messenger.user.dto.UpsertProfileAudioRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
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
class UserControllerMvcTest {

    @Mock
    private UserService userService;

    @Mock
    private UserProfileMetadataService userProfileMetadataService;

    @Mock
    private UserPresenceService userPresenceService;

    @Mock
    private UserPrivacyService userPrivacyService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new UserController(userService, userProfileMetadataService, userPresenceService, userPrivacyService)
                )
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void updateProfileTabReturnsBadRequestForUnsupportedTab() throws Exception {
        mockMvc.perform(
                        patch("/api/users/me/profile-tab")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(new UpdateProfileTabRequest("music")))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService, userProfileMetadataService, userPresenceService, userPrivacyService);
    }

    @Test
    void searchReturnsBadRequestForTooLongQuery() throws Exception {
        mockMvc.perform(
                        get("/api/users/search")
                                .param("query", "a".repeat(256))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService, userProfileMetadataService, userPresenceService, userPrivacyService);
    }

    @Test
    void upsertProfileAudioReturnsBadRequestWhenMetadataProvidedWithoutAttachment() throws Exception {
        mockMvc.perform(
                        put("/api/users/me/profile-audio")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpsertProfileAudioRequest(null, "Intro", null, null)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService, userProfileMetadataService, userPresenceService, userPrivacyService);
    }

    @Test
    void updatePrivacyExceptionsReturnsBadRequestForOverlappingLists() throws Exception {
        java.util.UUID userId = java.util.UUID.randomUUID();
        mockMvc.perform(
                        patch("/api/users/me/privacy/exceptions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpdatePrivacyExceptionsRequest(
                                                java.util.List.of(userId),
                                                java.util.List.of(userId),
                                                null,
                                                null,
                                                null,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService, userProfileMetadataService, userPresenceService, userPrivacyService);
    }

    @Test
    void updatePrivacyExceptionsReturnsBadRequestForNullUserId() throws Exception {
        mockMvc.perform(
                        patch("/api/users/me/privacy/exceptions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpdatePrivacyExceptionsRequest(
                                                Collections.<UUID>singletonList(null),
                                                null,
                                                null,
                                                null,
                                                null,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService, userProfileMetadataService, userPresenceService, userPrivacyService);
    }

    @Test
    void upcomingBirthdaysReturnsBadRequestForTooLargeDays() throws Exception {
        mockMvc.perform(
                        get("/api/users/contacts/birthdays")
                                .param("days", "366")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService, userProfileMetadataService, userPresenceService, userPrivacyService);
    }

    @Test
    void replaceCloseFriendsReturnsBadRequestForNullUserId() throws Exception {
        mockMvc.perform(
                        put("/api/users/me/close-friends")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new ReplaceCloseFriendsRequest(Collections.singletonList(null))
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService, userProfileMetadataService, userPresenceService, userPrivacyService);
    }

    @Test
    void presenceReturnsBadRequestForTooManyUserIds() throws Exception {
        var request = get("/api/users/presence");
        for (int index = 0; index < 101; index++) {
            request = request.param("userId", java.util.UUID.randomUUID().toString());
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService, userProfileMetadataService, userPresenceService, userPrivacyService);
    }

    @Test
    void importContactsReturnsBadRequestForNullContactEntry() throws Exception {
        mockMvc.perform(
                        post("/api/users/contacts/import")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new ImportContactsRequest(
                                                Collections.<ImportedPhoneContactPayload>singletonList(null),
                                                true
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService, userProfileMetadataService, userPresenceService, userPrivacyService);
    }

    @Test
    void importContactsReturnsBadRequestForTooManyContacts() throws Exception {
        var contacts = new java.util.ArrayList<ImportedPhoneContactPayload>();
        for (int index = 0; index < 1001; index++) {
            contacts.add(new ImportedPhoneContactPayload("+375290000" + index, "User " + index));
        }

        mockMvc.perform(
                        post("/api/users/contacts/import")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new ImportContactsRequest(contacts, true)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService, userProfileMetadataService, userPresenceService, userPrivacyService);
    }
}
