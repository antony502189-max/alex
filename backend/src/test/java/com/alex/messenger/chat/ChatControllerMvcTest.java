package com.alex.messenger.chat;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alex.messenger.chat.dto.AddMembersRequest;
import com.alex.messenger.chat.dto.CreateInviteLinkRequest;
import com.alex.messenger.chat.dto.CreateChannelRequest;
import com.alex.messenger.chat.dto.CreateGroupChatRequest;
import com.alex.messenger.chat.dto.JoinByPublicUsernameRequest;
import com.alex.messenger.chat.dto.MuteChatRequest;
import com.alex.messenger.chat.dto.UpdateChatBanRequest;
import com.alex.messenger.chat.dto.UpdateChatProfileRequest;
import com.alex.messenger.chat.dto.UpdateChatPublicUsernameRequest;
import com.alex.messenger.chat.dto.UpdateForumTopicRequest;
import com.alex.messenger.chat.dto.UpdateMemberPermissionsRequest;
import com.alex.messenger.chat.dto.UpdateMemberRestrictionRequest;
import com.alex.messenger.chat.dto.UpdateMemberRoleRequest;
import com.alex.messenger.chat.folder.ChatFolderService;
import com.alex.messenger.chat.forum.ForumTopicService;
import com.alex.messenger.message.MessageDeliveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class ChatControllerMvcTest {

    @Mock
    private ChatService chatService;

    @Mock
    private ChatPinHistoryService chatPinHistoryService;

    @Mock
    private ChatReadEventPublisher chatReadEventPublisher;

    @Mock
    private ChatPinEventPublisher chatPinEventPublisher;

    @Mock
    private TypingEventPublisher typingEventPublisher;

    @Mock
    private MessageDeliveryService messageDeliveryService;

    @Mock
    private ForumTopicService forumTopicService;

    @Mock
    private ChatFolderService chatFolderService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUserId.toString(), "test")
        );
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(
                        chatService,
                        chatPinHistoryService,
                        chatReadEventPublisher,
                        chatPinEventPublisher,
                        typingEventPublisher,
                        messageDeliveryService,
                        forumTopicService,
                        chatFolderService
                ))
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updatePermissionsReturnsBadRequestWhenNoChangesProvided() throws Exception {
        mockMvc.perform(
                        post("/api/chats/{chatId}/permissions/{userId}", UUID.randomUUID(), UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpdateMemberPermissionsRequest(null, null, null, null, null, null, null)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
    }

    @Test
    void createGroupReturnsBadRequestForNullMemberId() throws Exception {
        mockMvc.perform(
                        post("/api/chats/group")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateGroupChatRequest(
                                                "Team",
                                                null,
                                                null,
                                                false,
                                                false,
                                                Collections.singletonList(null)
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
    }

    @Test
    void createChannelReturnsBadRequestForNullSubscriberId() throws Exception {
        mockMvc.perform(
                        post("/api/chats/channel")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateChannelRequest(
                                                "News",
                                                null,
                                                null,
                                                false,
                                                Collections.singletonList(null)
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
    }

    @Test
    void addMembersReturnsBadRequestForNullUserId() throws Exception {
        mockMvc.perform(
                        post("/api/chats/{chatId}/members", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new AddMembersRequest(Collections.singletonList(null))
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
    }

    @Test
    void muteReturnsBadRequestForPastMutedUntil() throws Exception {
        mockMvc.perform(
                        post("/api/chats/{chatId}/mute", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new MuteChatRequest(Instant.parse("2000-01-01T00:00:00Z"))
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
    }

    @Test
    void createInviteLinkReturnsBadRequestForPastExpiration() throws Exception {
        mockMvc.perform(
                        post("/api/chats/{chatId}/invite-links", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new CreateInviteLinkRequest(
                                                "Guests",
                                                10,
                                                Instant.parse("2000-01-01T00:00:00Z")
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
    }

    @Test
    void updateRestrictionReturnsBadRequestForPastRestrictedUntil() throws Exception {
        mockMvc.perform(
                        post("/api/chats/{chatId}/restrictions/{userId}", UUID.randomUUID(), UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpdateMemberRestrictionRequest(
                                                false,
                                                Instant.parse("2000-01-01T00:00:00Z"),
                                                "spam"
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
    }

    @Test
    void banMemberReturnsBadRequestForPastBanEnd() throws Exception {
        mockMvc.perform(
                        post("/api/chats/{chatId}/bans/{userId}", UUID.randomUUID(), UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpdateChatBanRequest(
                                                Instant.parse("2000-01-01T00:00:00Z"),
                                                "abuse"
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
    }

    @Test
    void updateProfileReturnsBadRequestForTooLargeAutoDeleteSeconds() throws Exception {
        mockMvc.perform(
                        post("/api/chats/{chatId}/profile", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpdateChatProfileRequest(
                                                "Project",
                                                null,
                                                31_536_001,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
    }

    @Test
    void updateProfileReturnsBadRequestForTooLargeSlowModeSeconds() throws Exception {
        mockMvc.perform(
                        post("/api/chats/{chatId}/profile", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpdateChatProfileRequest(
                                                "Project",
                                                null,
                                                null,
                                                86_401,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null
                                        )
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
    }

    @Test
    void updatePublicUsernameReturnsBadRequestForInvalidFormat() throws Exception {
        mockMvc.perform(
                        post("/api/chats/{chatId}/public-username", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpdateChatPublicUsernameRequest("@bad-name")
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
    }

    @Test
    void updateRoleReturnsBadRequestForUnsupportedRole() throws Exception {
        mockMvc.perform(
                        post("/api/chats/{chatId}/members/{userId}/role", UUID.randomUUID(), UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpdateMemberRoleRequest("OWNER")
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
    }

    @Test
    void updateTopicReturnsBadRequestWhenNoChangesProvided() throws Exception {
        mockMvc.perform(
                        post("/api/chats/{chatId}/topics/{topicId}", UUID.randomUUID(), UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new UpdateForumTopicRequest(null, null, null, null)
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
    }

    @Test
    void joinByUsernameReturnsBadRequestForInvalidUsername() throws Exception {
        mockMvc.perform(
                        post("/api/chats/join-by-username")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(
                                        new JoinByPublicUsernameRequest("@bad-name")
                                ))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
    }

    @Test
    void listChatsReturnsBadRequestForNonPositiveLimit() throws Exception {
        mockMvc.perform(
                        get("/api/chats")
                                .param("limit", "0")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
    }

    @Test
    void listChatsReturnsBadRequestForTooLargeLimitInFolderView() throws Exception {
        mockMvc.perform(
                        get("/api/chats")
                                .param("folderId", UUID.randomUUID().toString())
                                .param("limit", "101")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
    }

    @Test
    void getAdminLogReturnsBadRequestForTooLargeLimit() throws Exception {
        mockMvc.perform(
                        get("/api/chats/{chatId}/admin-log", UUID.randomUUID())
                                .param("limit", "101")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
    }

    @Test
    void listPinsReturnsBadRequestForTooLargeLimit() throws Exception {
        mockMvc.perform(
                        get("/api/chats/{chatId}/pins", UUID.randomUUID())
                                .param("limit", "51")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                chatService,
                chatPinHistoryService,
                chatReadEventPublisher,
                chatPinEventPublisher,
                typingEventPublisher,
                messageDeliveryService,
                forumTopicService,
                chatFolderService
        );
    }
}
