package com.alex.messenger.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.alex.messenger.business.dto.AssignBusinessOperatorRequest;
import com.alex.messenger.business.dto.BusinessChatTagPayload;
import com.alex.messenger.business.dto.BusinessHourSlotPayload;
import com.alex.messenger.business.dto.ReplaceBusinessChatTagsRequest;
import com.alex.messenger.business.dto.UpdateBusinessProfileRequest;
import com.alex.messenger.business.dto.UpsertBusinessQuickReplyRequest;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.SendMessageRequest;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusinessServiceTest {

    @Mock
    private BusinessProfileRepository businessProfileRepository;

    @Mock
    private BusinessQuickReplyRepository businessQuickReplyRepository;

    @Mock
    private BusinessChatTagRepository businessChatTagRepository;

    @Mock
    private BusinessOperatorAssignmentRepository businessOperatorAssignmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private MessageService messageService;

    private BusinessService businessService;

    @BeforeEach
    void setUp() {
        businessService = new BusinessService(
                businessProfileRepository,
                businessQuickReplyRepository,
                businessChatTagRepository,
                businessOperatorAssignmentRepository,
                userRepository,
                chatService,
                messageService,
                new ObjectMapper()
        );
    }

    @Test
    void updateProfileStoresBusinessHours() {
        UUID userId = UUID.randomUUID();
        UserEntity user = user(userId, "Business");
        BusinessProfileEntity profile = new BusinessProfileEntity();
        profile.setUserId(userId);
        profile.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        profile.setUpdatedAt(Instant.parse("2026-03-14T10:00:00Z"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(businessProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(businessProfileRepository.save(any(BusinessProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = businessService.updateProfile(
                userId,
                new UpdateBusinessProfileRequest(
                        true,
                        "Hello",
                        true,
                        "Away",
                        List.of(new BusinessHourSlotPayload("monday", "09:00", "18:00")),
                        "Europe/Minsk"
                )
        );

        assertThat(response.greetingEnabled()).isTrue();
        assertThat(response.businessHours()).hasSize(1);
        assertThat(response.businessHours().get(0).dayOfWeek()).isEqualTo("MONDAY");
        assertThat(response.timeZone()).isEqualTo("Europe/Minsk");
    }

    @Test
    void sendQuickReplyUsesMessageService() {
        UUID userId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID quickReplyId = UUID.randomUUID();

        when(chatService.getOwnedChat(userId, chatId)).thenReturn(chat(chatId));
        when(businessQuickReplyRepository.findByIdAndUserId(quickReplyId, userId)).thenReturn(Optional.of(quickReply(userId, quickReplyId)));
        when(messageService.sendMessage(eq(userId), any(SendMessageRequest.class))).thenReturn(message(chatId));

        var response = businessService.sendQuickReply(userId, chatId, quickReplyId);

        assertThat(response.chatId()).isEqualTo(chatId);
        assertThat(response.text()).isEqualTo("Welcome");
    }

    @Test
    void replaceChatTagsNormalizesAndReturnsTags() {
        UUID userId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        when(chatService.getOwnedChat(userId, chatId)).thenReturn(chat(chatId));
        when(businessChatTagRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(businessChatTagRepository.findAllByOwnerUserIdAndChatIdOrderByPositionAscCreatedAtAsc(userId, chatId)).thenAnswer(invocation -> {
            BusinessChatTagEntity first = new BusinessChatTagEntity();
            first.setId(UUID.randomUUID());
            first.setOwnerUserId(userId);
            first.setChatId(chatId);
            first.setTagName("Lead");
            first.setColor("#ff0000");
            first.setPosition(0);
            first.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
            return List.of(first);
        });

        var response = businessService.replaceChatTags(
                userId,
                chatId,
                new ReplaceBusinessChatTagsRequest(List.of(new BusinessChatTagPayload("Lead", "#ff0000")))
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).tagName()).isEqualTo("Lead");
        assertThat(response.get(0).color()).isEqualTo("#ff0000");
    }

    @Test
    void assignOperatorReturnsOperatorMetadata() {
        UUID ownerUserId = UUID.randomUUID();
        UUID operatorUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        when(chatService.getOwnedChat(ownerUserId, chatId)).thenReturn(chat(chatId));
        when(userRepository.findById(operatorUserId)).thenReturn(Optional.of(user(operatorUserId, "Operator")));
        when(businessOperatorAssignmentRepository.findByIdOwnerUserIdAndIdChatId(ownerUserId, chatId)).thenReturn(Optional.empty());
        when(businessOperatorAssignmentRepository.save(any(BusinessOperatorAssignmentEntity.class))).thenAnswer(invocation -> {
            BusinessOperatorAssignmentEntity entity = invocation.getArgument(0);
            entity.setAssignedAt(Instant.parse("2026-03-14T10:00:00Z"));
            entity.setUpdatedAt(Instant.parse("2026-03-14T10:05:00Z"));
            return entity;
        });

        var response = businessService.assignOperator(
                ownerUserId,
                chatId,
                new AssignBusinessOperatorRequest(operatorUserId, "Primary")
        );

        assertThat(response.operatorUserId()).isEqualTo(operatorUserId);
        assertThat(response.operatorDisplayName()).isEqualTo("Operator");
    }

    private UserEntity user(UUID userId, String displayName) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setDisplayName(displayName);
        user.setUsername(displayName.toLowerCase());
        return user;
    }

    private BusinessQuickReplyEntity quickReply(UUID userId, UUID quickReplyId) {
        BusinessQuickReplyEntity entity = new BusinessQuickReplyEntity();
        entity.setId(quickReplyId);
        entity.setUserId(userId);
        entity.setShortcut("welcome");
        entity.setMessageText("Welcome");
        entity.setPosition(0);
        return entity;
    }

    private ChatEntity chat(UUID chatId) {
        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("DIRECT");
        return chat;
    }

    private ChatMessageResponse message(UUID chatId) {
        return new ChatMessageResponse(
                chatId,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                "Business",
                null,
                null,
                false,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                0,
                "Welcome",
                List.of(),
                "TEXT",
                null,
                false,
                null,
                null,
                null,
                Instant.parse("2026-03-14T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                "SENT",
                null,
                null,
                null,
                null,
                null
        );
    }
}
