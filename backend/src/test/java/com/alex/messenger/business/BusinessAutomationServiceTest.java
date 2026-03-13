package com.alex.messenger.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.feature.FeatureProperties;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusinessAutomationServiceTest {

    @Mock
    private BusinessProfileRepository businessProfileRepository;

    @Mock
    private BusinessChatAutomationStateRepository businessChatAutomationStateRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.alex.messenger.chat.ChatService chatService;

    @Mock
    private MessageService messageService;

    private BusinessAutomationService businessAutomationService;

    @BeforeEach
    void setUp() {
        FeatureProperties featureProperties = new FeatureProperties();
        featureProperties.setBusiness(true);
        businessAutomationService = new BusinessAutomationService(
                featureProperties,
                businessProfileRepository,
                businessChatAutomationStateRepository,
                userRepository,
                chatService,
                messageService,
                new ObjectMapper()
        );
    }

    @Test
    void firstCustomerMessageSendsGreeting() {
        UUID senderId = UUID.randomUUID();
        UUID businessOwnerId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        Instant incomingAt = Instant.parse("2026-03-16T10:00:00Z");
        com.alex.messenger.chat.ChatEntity directChat = chat(chatId);

        when(userRepository.findById(senderId)).thenReturn(Optional.of(user(senderId, false)));
        when(chatService.getOwnedChat(senderId, chatId)).thenReturn(directChat);
        when(chatService.getPeerUserId(directChat, senderId)).thenReturn(businessOwnerId);
        when(businessProfileRepository.findById(businessOwnerId)).thenReturn(Optional.of(profile(
                businessOwnerId,
                true,
                "Hello there",
                false,
                null,
                "[{\"dayOfWeek\":\"MONDAY\",\"fromTime\":\"09:00\",\"toTime\":\"18:00\"}]",
                "UTC"
        )));
        when(businessChatAutomationStateRepository.findById(new BusinessChatAutomationStateId(businessOwnerId, chatId)))
                .thenReturn(Optional.empty());
        when(messageService.sendAutomatedBusinessMessage(businessOwnerId, chatId, "Hello there"))
                .thenReturn(message(chatId, businessOwnerId, Instant.parse("2026-03-16T10:00:05Z")));
        when(businessChatAutomationStateRepository.save(any(BusinessChatAutomationStateEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        businessAutomationService.handleIncomingDirectMessage(chatId, senderId, incomingAt);

        ArgumentCaptor<BusinessChatAutomationStateEntity> captor =
                ArgumentCaptor.forClass(BusinessChatAutomationStateEntity.class);
        verify(businessChatAutomationStateRepository).save(captor.capture());
        BusinessChatAutomationStateEntity saved = captor.getValue();

        assertThat(saved.getFirstCustomerMessageAt()).isEqualTo(incomingAt);
        assertThat(saved.getLastCustomerMessageAt()).isEqualTo(incomingAt);
        assertThat(saved.getLastGreetingSentAt()).isEqualTo(Instant.parse("2026-03-16T10:00:05Z"));
        assertThat(saved.getLastAwaySentAt()).isNull();
    }

    @Test
    void awayMessageTakesPriorityOutsideBusinessHours() {
        UUID senderId = UUID.randomUUID();
        UUID businessOwnerId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        Instant incomingAt = Instant.parse("2026-03-15T23:00:00Z");
        com.alex.messenger.chat.ChatEntity directChat = chat(chatId);

        when(userRepository.findById(senderId)).thenReturn(Optional.of(user(senderId, false)));
        when(chatService.getOwnedChat(senderId, chatId)).thenReturn(directChat);
        when(chatService.getPeerUserId(directChat, senderId)).thenReturn(businessOwnerId);
        when(businessProfileRepository.findById(businessOwnerId)).thenReturn(Optional.of(profile(
                businessOwnerId,
                true,
                "Hello there",
                true,
                "We are away",
                "[{\"dayOfWeek\":\"MONDAY\",\"fromTime\":\"09:00\",\"toTime\":\"18:00\"}]",
                "UTC"
        )));
        when(businessChatAutomationStateRepository.findById(new BusinessChatAutomationStateId(businessOwnerId, chatId)))
                .thenReturn(Optional.empty());
        when(messageService.sendAutomatedBusinessMessage(businessOwnerId, chatId, "We are away"))
                .thenReturn(message(chatId, businessOwnerId, Instant.parse("2026-03-15T23:00:05Z")));
        when(businessChatAutomationStateRepository.save(any(BusinessChatAutomationStateEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        businessAutomationService.handleIncomingDirectMessage(chatId, senderId, incomingAt);

        verify(messageService).sendAutomatedBusinessMessage(businessOwnerId, chatId, "We are away");
        ArgumentCaptor<BusinessChatAutomationStateEntity> captor =
                ArgumentCaptor.forClass(BusinessChatAutomationStateEntity.class);
        verify(businessChatAutomationStateRepository).save(captor.capture());
        BusinessChatAutomationStateEntity saved = captor.getValue();

        assertThat(saved.getLastAwaySentAt()).isEqualTo(Instant.parse("2026-03-15T23:00:05Z"));
        assertThat(saved.getLastGreetingSentAt()).isNull();
    }

    private UserEntity user(UUID userId, boolean bot) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setBot(bot);
        return user;
    }

    private com.alex.messenger.chat.ChatEntity chat(UUID chatId) {
        com.alex.messenger.chat.ChatEntity chat = new com.alex.messenger.chat.ChatEntity();
        chat.setId(chatId);
        chat.setChatType("DIRECT");
        return chat;
    }

    private BusinessProfileEntity profile(
            UUID userId,
            boolean greetingEnabled,
            String greetingMessage,
            boolean awayEnabled,
            String awayMessage,
            String businessHoursJson,
            String timeZone
    ) {
        BusinessProfileEntity entity = new BusinessProfileEntity();
        entity.setUserId(userId);
        entity.setGreetingEnabled(greetingEnabled);
        entity.setGreetingMessage(greetingMessage);
        entity.setAwayEnabled(awayEnabled);
        entity.setAwayMessage(awayMessage);
        entity.setBusinessHoursJson(businessHoursJson);
        entity.setTimeZone(timeZone);
        return entity;
    }

    private ChatMessageResponse message(UUID chatId, UUID senderId, Instant createdAt) {
        return new ChatMessageResponse(
                chatId,
                UUID.randomUUID(),
                null,
                senderId,
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
                "auto",
                List.of(),
                "TEXT",
                null,
                false,
                null,
                null,
                null,
                createdAt,
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
