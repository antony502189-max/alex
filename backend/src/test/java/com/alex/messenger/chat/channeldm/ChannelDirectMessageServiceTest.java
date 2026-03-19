package com.alex.messenger.chat.channeldm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.ChatAdminLogService;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatMemberEntity;
import com.alex.messenger.chat.ChatMemberId;
import com.alex.messenger.chat.ChatMemberRepository;
import com.alex.messenger.chat.ChatRepository;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.channeldm.dto.ChannelDirectMessageResponse;
import com.alex.messenger.chat.channeldm.dto.ChannelDirectMessageStateResponse;
import com.alex.messenger.chat.channeldm.dto.ChannelDirectMessageTopicResponse;
import com.alex.messenger.chat.channeldm.dto.OpenChannelDirectMessageRequest;
import com.alex.messenger.chat.dto.ChatSummaryResponse;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ChannelDirectMessageServiceTest {

    @Mock
    private ChannelDirectMessageChatRepository channelDirectMessageChatRepository;

    @Mock
    private ChannelDirectMessageTopicRepository channelDirectMessageTopicRepository;

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatMemberRepository chatMemberRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private ChatAdminLogService chatAdminLogService;

    @Mock
    private UserRepository userRepository;

    private ChannelDirectMessageService channelDirectMessageService;

    @BeforeEach
    void setUp() {
        channelDirectMessageService = new ChannelDirectMessageService(
                channelDirectMessageChatRepository,
                channelDirectMessageTopicRepository,
                chatRepository,
                chatMemberRepository,
                chatService,
                chatAdminLogService,
                userRepository
        );
    }

    @Test
    void enableDirectMessagesRequiresModeratorPermissions() {
        UUID requesterId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        ChatEntity channel = channel(channelId, false, "public_channel");
        ChatMemberEntity ownerMembership = ownerMembership(channelId, ownerUserId);

        when(chatService.getChat(channelId)).thenReturn(channel);
        when(chatMemberRepository.findByIdChatIdAndRole(channelId, "OWNER")).thenReturn(Optional.of(ownerMembership));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(channelId, requesterId)).thenReturn(true);
        when(chatService.hasMessageModerationPermission(requesterId, channelId)).thenReturn(false);

        ResponseStatusException exception = catchThrowableOfType(
                () -> channelDirectMessageService.enableDirectMessages(requesterId, channelId),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void enableDirectMessagesPersistsChannelFlag() {
        UUID requesterId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        ChatEntity channel = channel(channelId, false, "public_channel");
        ChatMemberEntity ownerMembership = ownerMembership(channelId, ownerUserId);

        when(chatService.getChat(channelId)).thenReturn(channel);
        when(chatMemberRepository.findByIdChatIdAndRole(channelId, "OWNER")).thenReturn(Optional.of(ownerMembership));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(channelId, requesterId)).thenReturn(true);
        when(chatService.hasMessageModerationPermission(requesterId, channelId)).thenReturn(true);
        when(chatRepository.save(any(ChatEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(channelDirectMessageChatRepository.countByChannelChatId(channelId)).thenReturn(2L);

        ChannelDirectMessageStateResponse response = channelDirectMessageService.enableDirectMessages(requesterId, channelId);

        assertThat(response.channelChatId()).isEqualTo(channelId);
        assertThat(response.enabled()).isTrue();
        assertThat(response.conversationCount()).isEqualTo(2L);
        assertThat(channel.getDirectMessagesEnabled()).isTrue();
    }

    @Test
    void openDirectMessageCreatesManagedChannelChatForPublicChannel() {
        UUID requesterId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID directChatId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();

        ChatEntity channel = channel(channelId, true, "public_channel");
        ChatEntity directChat = managedChat(directChatId, "Channel public_channel");
        ChatMemberEntity ownerMembership = ownerMembership(channelId, ownerUserId);
        UserEntity requester = user(requesterId, "Guest User", "guest_user");

        when(chatService.getChat(channelId)).thenReturn(channel);
        when(chatMemberRepository.findByIdChatIdAndRole(channelId, "OWNER")).thenReturn(Optional.of(ownerMembership));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(channelId, requesterId)).thenReturn(false);
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(channelDirectMessageChatRepository.findByChannelChatIdAndParticipantUserId(channelId, requesterId))
                .thenReturn(Optional.empty());
        when(chatRepository.save(any(ChatEntity.class))).thenAnswer(invocation -> {
            ChatEntity chat = invocation.getArgument(0);
            if (chat.getId() == null) {
                chat.setId(directChatId);
            }
            if (chat.getCreatedAt() == null) {
                chat.setCreatedAt(Instant.parse("2026-03-19T13:00:00Z"));
            }
            return chat;
        });
        when(channelDirectMessageChatRepository.save(any(ChannelDirectMessageChatEntity.class))).thenAnswer(invocation -> {
            ChannelDirectMessageChatEntity link = invocation.getArgument(0);
            link.setId(linkId);
            link.setCreatedAt(Instant.parse("2026-03-19T13:00:01Z"));
            link.setUpdatedAt(Instant.parse("2026-03-19T13:00:01Z"));
            return link;
        });
        when(chatRepository.findById(directChatId)).thenReturn(Optional.of(directChat));
        when(channelDirectMessageTopicRepository.save(any(ChannelDirectMessageTopicEntity.class))).thenAnswer(invocation -> {
            ChannelDirectMessageTopicEntity topic = invocation.getArgument(0);
            topic.setId(topicId);
            topic.setCreatedAt(Instant.parse("2026-03-19T13:00:02Z"));
            topic.setUpdatedAt(Instant.parse("2026-03-19T13:00:02Z"));
            return topic;
        });
        when(chatService.getChatSummary(requesterId, directChatId)).thenReturn(summary(directChatId, "Channel public_channel"));

        ChannelDirectMessageResponse response = channelDirectMessageService.openDirectMessage(
                requesterId,
                channelId,
                new OpenChannelDirectMessageRequest(null)
        );

        assertThat(response.directChatId()).isEqualTo(directChatId);
        assertThat(response.participantUserId()).isEqualTo(requesterId);
        assertThat(response.participantDisplayName()).isEqualTo("Guest User");
        assertThat(response.topicId()).isEqualTo(topicId);
        assertThat(response.chat().chatId()).isEqualTo(directChatId);
        verify(chatMemberRepository).saveAll(any());
    }

    @Test
    void listDirectMessagesGrantsModeratorMembershipWhenMissing() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID participantUserId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID directChatId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();

        ChatEntity channel = channel(channelId, true, "public_channel");
        ChatEntity directChat = managedChat(directChatId, "Channel public_channel");
        directChat.setLastMessageAt(Instant.parse("2026-03-19T15:00:00Z"));
        ChatMemberEntity ownerMembership = ownerMembership(channelId, ownerUserId);
        ChannelDirectMessageChatEntity link = link(linkId, channelId, directChatId, participantUserId);
        ChannelDirectMessageTopicEntity topic = topic(topicId, channelId, directChatId, participantUserId, "Participant", directChat.getLastMessageAt());
        UserEntity participant = user(participantUserId, "Participant", "participant");

        when(chatService.getChat(channelId)).thenReturn(channel);
        when(chatMemberRepository.findByIdChatIdAndRole(channelId, "OWNER")).thenReturn(Optional.of(ownerMembership));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(channelId, requesterId)).thenReturn(true);
        when(chatService.hasMessageModerationPermission(requesterId, channelId)).thenReturn(true);
        when(channelDirectMessageChatRepository.findVisible(eq(channelId), eq((UUID) null), any())).thenReturn(List.of(link));
        when(userRepository.findAllById(List.of(participantUserId))).thenReturn(List.of(participant));
        when(chatRepository.findAllById(List.of(directChatId))).thenReturn(List.of(directChat));
        when(channelDirectMessageTopicRepository.findVisible(eq(channelId), eq((UUID) null), any())).thenReturn(List.of(topic));
        when(chatMemberRepository.existsById(new ChatMemberId(directChatId, requesterId))).thenReturn(false);
        when(chatMemberRepository.save(any(ChatMemberEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatService.getChatSummary(requesterId, directChatId)).thenReturn(summary(directChatId, "Channel public_channel"));

        List<ChannelDirectMessageResponse> response = channelDirectMessageService.listDirectMessages(requesterId, channelId, 20);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).directChatId()).isEqualTo(directChatId);
        assertThat(response.get(0).participantDisplayName()).isEqualTo("Participant");
        verify(chatMemberRepository).save(any(ChatMemberEntity.class));
    }

    @Test
    void listTopicsReturnsOwnConversationForNonModerator() {
        UUID requesterId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID directChatId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();

        ChatEntity channel = channel(channelId, true, "public_channel");
        ChatMemberEntity ownerMembership = ownerMembership(channelId, ownerUserId);
        ChannelDirectMessageTopicEntity topic = topic(topicId, channelId, directChatId, requesterId, "Guest User", Instant.parse("2026-03-19T15:00:00Z"));
        UserEntity requester = user(requesterId, "Guest User", "guest_user");
        ChatEntity directChat = managedChat(directChatId, "Channel public_channel");
        directChat.setLastMessageAt(Instant.parse("2026-03-19T15:00:00Z"));

        when(chatService.getChat(channelId)).thenReturn(channel);
        when(chatMemberRepository.findByIdChatIdAndRole(channelId, "OWNER")).thenReturn(Optional.of(ownerMembership));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(channelId, requesterId)).thenReturn(false);
        when(channelDirectMessageTopicRepository.findVisible(eq(channelId), eq(requesterId), any())).thenReturn(List.of(topic));
        when(userRepository.findAllById(List.of(requesterId))).thenReturn(List.of(requester));
        when(chatRepository.findAllById(List.of(directChatId))).thenReturn(List.of(directChat));

        List<ChannelDirectMessageTopicResponse> response = channelDirectMessageService.listDirectMessageTopics(requesterId, channelId, 10);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).participantUserId()).isEqualTo(requesterId);
        assertThat(response.get(0).title()).isEqualTo("Guest User");
    }

    private ChatEntity channel(UUID channelId, boolean directMessagesEnabled, String publicUsername) {
        ChatEntity chat = new ChatEntity();
        chat.setId(channelId);
        chat.setChatType("CHANNEL");
        chat.setTitle("Channel " + publicUsername);
        chat.setPublicUsername(publicUsername);
        chat.setDirectMessagesEnabled(directMessagesEnabled);
        chat.setCreatedAt(Instant.parse("2026-03-19T10:00:00Z"));
        return chat;
    }

    private ChatEntity managedChat(UUID chatId, String title) {
        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("CHANNEL_DIRECT");
        chat.setTitle(title);
        chat.setCreatedAt(Instant.parse("2026-03-19T13:00:00Z"));
        return chat;
    }

    private ChatMemberEntity ownerMembership(UUID chatId, UUID ownerUserId) {
        ChatMemberEntity member = new ChatMemberEntity();
        member.setId(new ChatMemberId(chatId, ownerUserId));
        member.setRole("OWNER");
        member.setCanManageMessages(true);
        member.setCanPostMessages(true);
        member.setCanSendMessages(true);
        return member;
    }

    private UserEntity user(UUID userId, String displayName, String username) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setDisplayName(displayName);
        user.setUsername(username);
        user.setPhoneNumber("+10000000000");
        return user;
    }

    private ChannelDirectMessageChatEntity link(UUID linkId, UUID channelId, UUID directChatId, UUID participantUserId) {
        ChannelDirectMessageChatEntity link = new ChannelDirectMessageChatEntity();
        link.setId(linkId);
        link.setChannelChatId(channelId);
        link.setDirectChatId(directChatId);
        link.setParticipantUserId(participantUserId);
        link.setCreatedByUserId(participantUserId);
        link.setStatus("OPEN");
        link.setCreatedAt(Instant.parse("2026-03-19T14:00:00Z"));
        link.setUpdatedAt(Instant.parse("2026-03-19T14:00:00Z"));
        return link;
    }

    private ChannelDirectMessageTopicEntity topic(
            UUID topicId,
            UUID channelId,
            UUID directChatId,
            UUID participantUserId,
            String title,
            Instant lastMessageAt
    ) {
        ChannelDirectMessageTopicEntity topic = new ChannelDirectMessageTopicEntity();
        topic.setId(topicId);
        topic.setChannelChatId(channelId);
        topic.setDirectChatId(directChatId);
        topic.setParticipantUserId(participantUserId);
        topic.setCreatedByUserId(participantUserId);
        topic.setTitle(title);
        topic.setLastMessageAt(lastMessageAt);
        topic.setCreatedAt(Instant.parse("2026-03-19T14:00:01Z"));
        topic.setUpdatedAt(Instant.parse("2026-03-19T14:00:01Z"));
        return topic;
    }

    private ChatSummaryResponse summary(UUID chatId, String title) {
        return new ChatSummaryResponse(
                chatId,
                "CHANNEL_DIRECT",
                title,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                false,
                0,
                null,
                null,
                Instant.parse("2026-03-19T15:00:00Z"),
                2,
                null,
                0,
                0,
                0,
                false,
                null,
                null,
                null,
                null,
                false,
                false,
                true,
                false,
                null
        );
    }
}
