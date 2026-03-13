package com.alex.messenger.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.draft.ChatDraftRepository;
import com.alex.messenger.chat.forum.ForumTopicRepository;
import com.alex.messenger.chat.invite.ChatInviteLinkEntity;
import com.alex.messenger.chat.invite.ChatInviteLinkRepository;
import com.alex.messenger.media.ProfilePhotoService;
import com.alex.messenger.message.MessageEntity;
import com.alex.messenger.message.MessageReactionRepository;
import com.alex.messenger.message.MessageRepository;
import com.alex.messenger.message.MessageLookupEntity;
import com.alex.messenger.message.MessageLookupRepository;
import com.alex.messenger.message.MessagePrimaryKey;
import com.alex.messenger.message.MessageTextContent;
import com.alex.messenger.message.dto.MessageTextEntityPayload;
import com.alex.messenger.media.PhotoAccess;
import com.alex.messenger.user.BlockedUserRepository;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserPresenceService;
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
class ChatServiceTest {

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatMemberRepository chatMemberRepository;

    @Mock
    private ChatBanRepository chatBanRepository;

    @Mock
    private ChatJoinRequestRepository chatJoinRequestRepository;

    @Mock
    private ChatPinEventRepository chatPinEventRepository;

    @Mock
    private ChatDraftRepository chatDraftRepository;

    @Mock
    private ChatInviteLinkRepository chatInviteLinkRepository;

    @Mock
    private ForumTopicRepository forumTopicRepository;

    @Mock
    private ChatAdminLogService chatAdminLogService;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageLookupRepository messageLookupRepository;

    @Mock
    private MessageReactionRepository messageReactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BlockedUserRepository blockedUserRepository;

    @Mock
    private ProfilePhotoService profilePhotoService;

    @Mock
    private UserPresenceService userPresenceService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                chatRepository,
                chatMemberRepository,
                chatBanRepository,
                chatJoinRequestRepository,
                chatPinEventRepository,
                chatDraftRepository,
                chatInviteLinkRepository,
                forumTopicRepository,
                chatAdminLogService,
                messageRepository,
                messageLookupRepository,
                messageReactionRepository,
                userRepository,
                blockedUserRepository,
                profilePhotoService,
                userPresenceService
        );
    }

    @Test
    void pinMessageRejectsMessagesFromAnotherChat() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID otherChatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");

        ChatMemberEntity membership = new ChatMemberEntity();
        membership.setId(new ChatMemberId(chatId, requesterId));
        membership.setRole("ADMIN");
        membership.setCanPinMessages(true);

        MessageLookupEntity message = new MessageLookupEntity();
        message.setMessageId(messageId);
        message.setChatId(otherChatId);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(any(ChatMemberId.class))).thenReturn(Optional.of(membership));
        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(message));

        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.pinMessage(requesterId, chatId, messageId),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(chatPinEventRepository, never()).save(any(ChatPinEventEntity.class));
    }

    @Test
    void incrementUnreadCountsTracksMentionsAndReplies() {
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID mentionedUserId = UUID.randomUUID();
        UUID repliedUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        ChatMemberEntity mentionedMember = member(chatId, mentionedUserId);
        ChatMemberEntity repliedMember = member(chatId, repliedUserId);
        ChatMemberEntity otherMember = member(chatId, otherUserId);

        UserEntity mentionedUser = new UserEntity();
        mentionedUser.setId(mentionedUserId);
        mentionedUser.setUsername("alex_mentioned");

        UserEntity repliedUser = new UserEntity();
        repliedUser.setId(repliedUserId);
        repliedUser.setUsername("reply_target");

        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(
                mentionedMember,
                repliedMember,
                otherMember
        ));
        when(userRepository.findAllById(List.of(mentionedUserId, repliedUserId, otherUserId))).thenReturn(List.of(
                mentionedUser,
                repliedUser
        ));

        chatService.incrementUnreadCounts(
                chatId,
                senderId,
                new MessageTextContent(
                        "Hello @alex_mentioned",
                        List.<MessageTextEntityPayload>of()
                ),
                repliedUserId
        );

        assertThat(mentionedMember.getUnreadCount()).isEqualTo(1);
        assertThat(mentionedMember.getMentionCount()).isEqualTo(1);
        assertThat(mentionedMember.getReplyCount()).isZero();

        assertThat(repliedMember.getUnreadCount()).isEqualTo(1);
        assertThat(repliedMember.getMentionCount()).isZero();
        assertThat(repliedMember.getReplyCount()).isEqualTo(1);

        assertThat(otherMember.getUnreadCount()).isEqualTo(1);
        assertThat(otherMember.getMentionCount()).isZero();
        assertThat(otherMember.getReplyCount()).isZero();
    }

    @Test
    void channelMemberWithPostingRightsCanPost() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("CHANNEL");

        ChatMemberEntity membership = member(chatId, requesterId);
        membership.setCanPostMessages(true);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));

        chatService.ensureCanPost(chat, requesterId);
    }

    @Test
    void channelMemberWithoutPostingRightsIsRejected() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("CHANNEL");

        ChatMemberEntity membership = member(chatId, requesterId);
        membership.setCanPostMessages(false);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));

        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.ensureCanPost(chat, requesterId),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void pinMessageRequiresPinPermission() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");

        ChatMemberEntity membership = member(chatId, requesterId);
        membership.setRole("ADMIN");
        membership.setCanPinMessages(false);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));

        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.pinMessage(requesterId, chatId, messageId),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(messageLookupRepository, never()).findById(messageId);
    }

    @Test
    void approveJoinRequestRequiresApprovalPermission() {
        UUID requesterId = UUID.randomUUID();
        UUID requesterChatId = UUID.randomUUID();
        UUID pendingUserId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(requesterChatId);
        chat.setChatType("GROUP");

        ChatMemberEntity membership = member(requesterChatId, requesterId);
        membership.setRole("ADMIN");
        membership.setCanApproveJoinRequests(false);

        when(chatRepository.findById(requesterChatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(requesterChatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(requesterChatId, requesterId))).thenReturn(Optional.of(membership));

        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.approveJoinRequest(requesterId, requesterChatId, pendingUserId),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chatJoinRequestRepository, never()).findByIdChatIdAndIdUserId(requesterChatId, pendingUserId);
    }

    @Test
    void slowModeBlocksMemberPostingUntilCooldownExpires() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setSlowModeSeconds(60);

        ChatMemberEntity membership = member(chatId, requesterId);
        membership.setLastSentMessageAt(java.time.Instant.now().minusSeconds(15));

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));

        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.ensureCanPost(chat, requesterId),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void addMembersRejectsBannedUsers() {
        UUID requesterId = UUID.randomUUID();
        UUID bannedUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");

        ChatMemberEntity requesterMembership = member(chatId, requesterId);
        requesterMembership.setRole("ADMIN");
        requesterMembership.setCanManageMembers(true);

        ChatBanEntity ban = new ChatBanEntity();
        ban.setId(new ChatBanId(chatId, bannedUserId));
        ban.setBannedUntil(null);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(requesterMembership));
        UserEntity bannedUser = new UserEntity();
        bannedUser.setId(bannedUserId);
        when(userRepository.findAllById(any())).thenReturn(List.of(bannedUser));
        when(chatBanRepository.findById(new ChatBanId(chatId, bannedUserId))).thenReturn(Optional.of(ban));

        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.addMembers(
                        requesterId,
                        chatId,
                        new com.alex.messenger.chat.dto.AddMembersRequest(List.of(bannedUserId))
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chatMemberRepository, never()).save(any(ChatMemberEntity.class));
    }

    @Test
    void analyticsAggregatesCoreCounts() {
        UUID requesterId = UUID.randomUUID();
        UUID memberUserId = UUID.randomUUID();
        UUID restrictedUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");

        ChatMemberEntity requesterMembership = member(chatId, requesterId);
        requesterMembership.setRole("ADMIN");
        requesterMembership.setCanManageMessages(true);

        ChatMemberEntity regularMember = member(chatId, memberUserId);
        ChatMemberEntity restrictedMember = member(chatId, restrictedUserId);
        restrictedMember.setCanSendMessages(false);

        MessageEntity firstMessage = new MessageEntity();
        firstMessage.setKey(new MessagePrimaryKey(chatId, UUID.randomUUID()));
        firstMessage.setDeletedAt(null);

        MessageEntity secondMessage = new MessageEntity();
        secondMessage.setKey(new MessagePrimaryKey(chatId, UUID.randomUUID()));
        secondMessage.setDeletedAt(null);

        ChatBanEntity activeBan = new ChatBanEntity();
        activeBan.setId(new ChatBanId(chatId, UUID.randomUUID()));
        activeBan.setBannedUntil(null);

        ChatJoinRequestEntity pendingJoinRequest = new ChatJoinRequestEntity();
        pendingJoinRequest.setId(new ChatJoinRequestId(chatId, UUID.randomUUID()));
        pendingJoinRequest.setStatus("PENDING");

        ChatInviteLinkEntity inviteLink = new ChatInviteLinkEntity();
        inviteLink.setChatId(chatId);
        inviteLink.setRevoked(false);

        ChatInviteLinkEntity revokedInviteLink = new ChatInviteLinkEntity();
        revokedInviteLink.setChatId(chatId);
        revokedInviteLink.setRevoked(true);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(requesterMembership));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(
                requesterMembership,
                regularMember,
                restrictedMember
        ));
        when(chatBanRepository.findAllByIdChatIdOrderByBannedAtDesc(chatId)).thenReturn(List.of(activeBan));
        when(chatJoinRequestRepository.findAllByIdChatIdAndStatusOrderByRequestedAtDesc(chatId, "PENDING"))
                .thenReturn(List.of(pendingJoinRequest));
        when(chatInviteLinkRepository.findAllByChatIdOrderByCreatedAtDesc(chatId))
                .thenReturn(List.of(inviteLink, revokedInviteLink));
        when(messageRepository.findAllByChatIdWithinRange(any(UUID.class), any(java.time.Instant.class), any(java.time.Instant.class)))
                .thenReturn(List.of(firstMessage, secondMessage));
        when(messageReactionRepository.findAllByIdMessageIdIn(any()))
                .thenReturn(List.of(
                        new com.alex.messenger.message.MessageReactionEntity(),
                        new com.alex.messenger.message.MessageReactionEntity(),
                        new com.alex.messenger.message.MessageReactionEntity()
                ));

        var analytics = chatService.getAnalytics(requesterId, chatId);

        assertThat(analytics.memberCount()).isEqualTo(3);
        assertThat(analytics.adminCount()).isEqualTo(1);
        assertThat(analytics.restrictedCount()).isEqualTo(1);
        assertThat(analytics.bannedCount()).isEqualTo(1);
        assertThat(analytics.pendingJoinRequestCount()).isEqualTo(1);
        assertThat(analytics.activeInviteLinkCount()).isEqualTo(1);
        assertThat(analytics.messagesLast24h()).isEqualTo(2);
        assertThat(analytics.reactionsLast24h()).isEqualTo(3);
        assertThat(analytics.commentsLast24h()).isZero();
    }

    @Test
    void resolveMessageAuthorMasksAnonymousAdminsForOtherMembers() {
        UUID requesterId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setTitle("Ops room");
        chat.setPhotoStorageProvider("s3");
        chat.setPhotoBucketName("chat-photos");
        chat.setPhotoObjectKey("ops-room.jpg");

        ChatMemberEntity senderMembership = member(chatId, senderId);
        senderMembership.setRole("ADMIN");
        senderMembership.setAnonymousAdmin(true);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.findById(new ChatMemberId(chatId, senderId)))
                .thenReturn(Optional.of(senderMembership));
        when(profilePhotoService.buildPhotoAccess("s3", "chat-photos", "ops-room.jpg"))
                .thenReturn(new PhotoAccess("https://cdn.example/ops-room.jpg", Instant.parse("2026-03-12T10:15:30Z")));

        ChatService.MessageAuthorView author = chatService.resolveMessageAuthor(requesterId, chatId, senderId);

        assertThat(author.senderId()).isNull();
        assertThat(author.displayName()).isEqualTo("Ops room");
        assertThat(author.photoUrl()).isEqualTo("https://cdn.example/ops-room.jpg");
        assertThat(author.photoAccessExpiresAt()).isEqualTo(Instant.parse("2026-03-12T10:15:30Z"));
        assertThat(author.anonymous()).isTrue();
        verify(userRepository, never()).findById(senderId);
    }

    private ChatMemberEntity member(UUID chatId, UUID userId) {
        ChatMemberEntity member = new ChatMemberEntity();
        member.setId(new ChatMemberId(chatId, userId));
        member.setRole("MEMBER");
        member.setCanSendMessages(true);
        member.setCanPostMessages(true);
        member.setCanManageMembers(false);
        member.setCanManageInviteLinks(false);
        member.setCanManageMessages(false);
        member.setCanPinMessages(false);
        member.setCanApproveJoinRequests(false);
        member.setAnonymousAdmin(false);
        member.setUnreadCount(0);
        member.setMentionCount(0);
        member.setReplyCount(0);
        return member;
    }
}
