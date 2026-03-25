package com.alex.messenger.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.abuse.AbuseProtectionService;
import com.alex.messenger.attachment.AttachmentEntity;
import com.alex.messenger.attachment.AttachmentRepository;
import com.alex.messenger.chat.draft.ChatDraftId;
import com.alex.messenger.chat.draft.ChatDraftRepository;
import com.alex.messenger.chat.forum.ForumTopicEntity;
import com.alex.messenger.chat.forum.ForumTopicRepository;
import com.alex.messenger.chat.invite.ChatInviteLinkEntity;
import com.alex.messenger.chat.invite.ChatInviteLinkRepository;
import com.alex.messenger.crypto.ChatEncryptionService;
import com.alex.messenger.media.ProfilePhotoService;
import com.alex.messenger.message.MessageContentCodec;
import com.alex.messenger.message.MessageEntity;
import com.alex.messenger.message.MessageReactionRepository;
import com.alex.messenger.message.MessageRepository;
import com.alex.messenger.message.MessageLookupEntity;
import com.alex.messenger.message.MessageLookupRepository;
import com.alex.messenger.message.MessageStorageService;
import com.alex.messenger.message.MessagePrimaryKey;
import com.alex.messenger.message.MessageTextContent;
import com.alex.messenger.chat.dto.AddMembersRequest;
import com.alex.messenger.chat.dto.ChatSummaryResponse;
import com.alex.messenger.chat.dto.UpdateMemberPermissionsRequest;
import com.alex.messenger.chat.dto.ChatReadEventResponse;
import com.alex.messenger.chat.dto.TransferChatOwnershipResponse;
import com.alex.messenger.chat.dto.TypingEventResponse;
import com.alex.messenger.message.dto.MessageTextEntityPayload;
import com.alex.messenger.media.PhotoAccess;
import com.alex.messenger.search.PublicPostSearchService;
import com.alex.messenger.sync.UserSyncService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
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
    private ChatReportRepository chatReportRepository;

    @Mock
    private ChatAdminLogService chatAdminLogService;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageLookupRepository messageLookupRepository;

    @Mock
    private MessageReactionRepository messageReactionRepository;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private MessageStorageService messageStorageService;

    @Mock
    private ChatEncryptionService chatEncryptionService;

    @Mock
    private MessageContentCodec messageContentCodec;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BlockedUserRepository blockedUserRepository;

    @Mock
    private ProfilePhotoService profilePhotoService;

    @Mock
    private UserPresenceService userPresenceService;

    @Mock
    private PublicPostSearchService publicPostSearchService;

    @Mock
    private UserSyncService userSyncService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private AbuseProtectionService abuseProtectionService;

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
                chatReportRepository,
                chatAdminLogService,
                messageRepository,
                messageLookupRepository,
                messageReactionRepository,
                attachmentRepository,
                messageStorageService,
                chatEncryptionService,
                messageContentCodec,
                userRepository,
                blockedUserRepository,
                profilePhotoService,
                userPresenceService,
                publicPostSearchService,
                userSyncService,
                applicationEventPublisher,
                abuseProtectionService
        );
        lenient().when(profilePhotoService.buildPhotoAccess(any(), any(), any())).thenReturn(new PhotoAccess(null, null));
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
    void updateMemberPermissionsRejectsMissingChanges() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");

        ChatMemberEntity requesterMembership = member(chatId, requesterId);
        requesterMembership.setRole("OWNER");
        requesterMembership.setCanManageMembers(true);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId)))
                .thenReturn(Optional.of(requesterMembership));

        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.updateMemberPermissions(
                        requesterId,
                        chatId,
                        targetUserId,
                        new UpdateMemberPermissionsRequest(null, null, null, null, null, null, null)
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(chatMemberRepository, never()).save(any(ChatMemberEntity.class));
    }

    @Test
    void muteChatRejectsPastMuteDeadline() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");

        ChatMemberEntity membership = member(chatId, requesterId);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId)))
                .thenReturn(Optional.of(membership));

        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.muteChat(requesterId, chatId, Instant.parse("2000-01-01T00:00:00Z")),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(chatMemberRepository, never()).save(any(ChatMemberEntity.class));
    }

    @Test
    void listChatsPageRejectsInvalidLimit() {
        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.listChatsPage(UUID.randomUUID(), false, null, 0),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("limit must be between 1 and 100");
    }

    @Test
    void searchChatsRejectsInvalidLimit() {
        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.searchChats(UUID.randomUUID(), "alpha", 0),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("limit must be between 1 and 50");
    }

    @Test
    void createGroupChatRejectsWhenChatCreationThrottleExceeded() {
        UUID requesterId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many chats created recently"))
                .when(abuseProtectionService)
                .assertChatCreationAllowed(requesterId);

        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.createGroupChat(
                        requesterId,
                        new com.alex.messenger.chat.dto.CreateGroupChatRequest(
                                "Ops room",
                                null,
                                null,
                                false,
                                false,
                                List.of(memberId)
                        )
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(chatRepository, never()).save(any(ChatEntity.class));
    }

    @Test
    void getOrCreateDirectChatPublishesCreatedEventWhenImplicitlyCreatingDirectChat() {
        UUID requesterId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID peerId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        UUID chatId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-03-25T01:00:00Z");
        UUID lowId = requesterId.compareTo(peerId) <= 0 ? requesterId : peerId;
        UUID highId = requesterId.compareTo(peerId) <= 0 ? peerId : requesterId;

        when(userRepository.existsById(peerId)).thenReturn(true);
        when(chatRepository.findByParticipantLowIdAndParticipantHighId(lowId, highId)).thenReturn(Optional.empty());
        when(chatRepository.save(any(ChatEntity.class))).thenAnswer(invocation -> {
            ChatEntity chat = invocation.getArgument(0);
            chat.setId(chatId);
            chat.setCreatedAt(createdAt);
            return chat;
        });

        ChatEntity result = chatService.getOrCreateDirectChat(requesterId, peerId);

        assertThat(result.getId()).isEqualTo(chatId);
        verify(userSyncService).recordForUsers(
                eq(List.of(requesterId, peerId)),
                eq("CHAT_CREATED"),
                eq("CHAT"),
                eq(chatId),
                eq(chatId),
                any()
        );
        verify(userSyncService).recordForUsers(
                eq(List.of(requesterId, peerId)),
                eq("CHAT_UPSERT"),
                eq("CHAT"),
                eq(chatId),
                eq(chatId),
                any()
        );
        ArgumentCaptor<ChatInboxFanoutEvent> eventCaptor = ArgumentCaptor.forClass(ChatInboxFanoutEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("CHAT_CREATED");
        assertThat(eventCaptor.getValue().chatId()).isEqualTo(chatId);
        assertThat(eventCaptor.getValue().userIds()).containsExactly(requesterId, peerId);
        assertThat(eventCaptor.getValue().removedUserIds()).isEmpty();
    }

    @Test
    void getOrCreateDirectChatSkipsCreatedEventWhenDuplicateRaceReturnsExistingChat() {
        UUID requesterId = UUID.fromString("00000000-0000-0000-0000-000000000030");
        UUID peerId = UUID.fromString("00000000-0000-0000-0000-000000000040");
        UUID lowId = requesterId.compareTo(peerId) <= 0 ? requesterId : peerId;
        UUID highId = requesterId.compareTo(peerId) <= 0 ? peerId : requesterId;

        ChatEntity existingChat = new ChatEntity();
        existingChat.setId(UUID.randomUUID());
        existingChat.setChatType("DIRECT");
        existingChat.setCreatedAt(Instant.parse("2026-03-25T01:05:00Z"));
        existingChat.setParticipantLowId(lowId);
        existingChat.setParticipantHighId(highId);

        when(userRepository.existsById(peerId)).thenReturn(true);
        when(chatRepository.findByParticipantLowIdAndParticipantHighId(lowId, highId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingChat));
        when(chatRepository.save(any(ChatEntity.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        ChatEntity result = chatService.getOrCreateDirectChat(requesterId, peerId);

        assertThat(result).isSameAs(existingChat);
        verify(userSyncService, never()).recordForUsers(
                any(),
                eq("CHAT_CREATED"),
                eq("CHAT"),
                any(),
                any(),
                any()
        );
        verify(applicationEventPublisher, never()).publishEvent(any(ChatInboxFanoutEvent.class));
    }

    @Test
    void getOrCreateDirectChatPublishesCreatedEventWhenImplicitlyCreatingSavedMessages() {
        UUID requesterId = UUID.fromString("00000000-0000-0000-0000-000000000050");
        UUID chatId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-03-25T01:10:00Z");

        when(chatRepository.findByChatTypeAndCreatedBy("SAVED", requesterId)).thenReturn(Optional.empty());
        when(chatRepository.save(any(ChatEntity.class))).thenAnswer(invocation -> {
            ChatEntity chat = invocation.getArgument(0);
            chat.setId(chatId);
            chat.setCreatedAt(createdAt);
            return chat;
        });
        when(chatMemberRepository.save(any(ChatMemberEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatEntity result = chatService.getOrCreateDirectChat(requesterId, requesterId);

        assertThat(result.getId()).isEqualTo(chatId);
        verify(userSyncService).recordForUsers(
                eq(List.of(requesterId)),
                eq("CHAT_CREATED"),
                eq("CHAT"),
                eq(chatId),
                eq(chatId),
                any()
        );
        ArgumentCaptor<ChatInboxFanoutEvent> eventCaptor = ArgumentCaptor.forClass(ChatInboxFanoutEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("CHAT_CREATED");
        assertThat(eventCaptor.getValue().chatId()).isEqualTo(chatId);
        assertThat(eventCaptor.getValue().userIds()).containsExactly(requesterId);
        assertThat(eventCaptor.getValue().removedUserIds()).isEmpty();
    }

    @Test
    void leaveChatRejectsOwnerUntilOwnershipIsTransferred() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");

        ChatMemberEntity membership = member(chatId, requesterId);
        membership.setRole("OWNER");

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));

        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.leaveChat(requesterId, chatId),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getReason()).isEqualTo("Transfer ownership before leaving the chat");
        verify(chatMemberRepository, never()).delete(any(ChatMemberEntity.class));
    }

    @Test
    void markChatUnreadPersistsManualUnreadFlagInSummary() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setTitle("Ops room");
        chat.setCreatedAt(Instant.parse("2026-03-24T10:00:00Z"));

        ChatMemberEntity membership = member(chatId, requesterId);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));
        when(chatMemberRepository.save(any(ChatMemberEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatDraftRepository.findById(new ChatDraftId(requesterId, chatId))).thenReturn(Optional.empty());
        when(chatMemberRepository.countByIdChatId(chatId)).thenReturn(3L);
        when(messageRepository.findRecentByChatId(chatId, 1)).thenReturn(List.of());

        ChatSummaryResponse response = chatService.markChatUnread(requesterId, chatId, true);

        assertThat(membership.getManuallyMarkedUnread()).isTrue();
        assertThat(response.markedUnread()).isTrue();
        verify(userSyncService).recordForUsers(
                any(),
                eq("CHAT_MARKED_UNREAD"),
                eq("CHAT"),
                eq(chatId),
                eq(chatId),
                any()
        );
        verify(userSyncService).recordForUsers(
                eq(List.of(requesterId)),
                eq("CHAT_UPSERT"),
                eq("CHAT"),
                eq(chatId),
                eq(chatId),
                any()
        );
        ArgumentCaptor<ChatInboxFanoutEvent> eventCaptor = ArgumentCaptor.forClass(ChatInboxFanoutEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("CHAT_MARKED_UNREAD");
        assertThat(eventCaptor.getValue().chatId()).isEqualTo(chatId);
        assertThat(eventCaptor.getValue().userIds()).containsExactly(requesterId);
    }

    @Test
    void archiveChatRecordsCanonicalSyncUpsertForRequester() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setTitle("Ops room");
        chat.setCreatedAt(Instant.parse("2026-03-24T10:00:00Z"));

        ChatMemberEntity membership = member(chatId, requesterId);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));
        when(chatMemberRepository.save(any(ChatMemberEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatDraftRepository.findById(new ChatDraftId(requesterId, chatId))).thenReturn(Optional.empty());
        when(chatMemberRepository.countByIdChatId(chatId)).thenReturn(3L);
        when(messageRepository.findRecentByChatId(chatId, 1)).thenReturn(List.of());

        ChatSummaryResponse response = chatService.archiveChat(requesterId, chatId, true);

        assertThat(response.archived()).isTrue();
        assertThat(membership.getArchived()).isTrue();
        verify(userSyncService).recordForUsers(
                eq(List.of(requesterId)),
                eq("CHAT_UPSERT"),
                eq("CHAT"),
                eq(chatId),
                eq(chatId),
                any()
        );
        ArgumentCaptor<ChatInboxFanoutEvent> eventCaptor = ArgumentCaptor.forClass(ChatInboxFanoutEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("CHAT_UPDATED");
        assertThat(eventCaptor.getValue().userIds()).containsExactly(requesterId);
    }

    @Test
    void createInviteLinkRejectsWhenThrottleExceeded() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");

        ChatMemberEntity membership = member(chatId, requesterId);
        membership.setRole("OWNER");
        membership.setCanManageInviteLinks(true);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many invite links created recently"))
                .when(abuseProtectionService)
                .assertInviteLinkCreationAllowed(requesterId, chatId);

        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.createInviteLink(
                        requesterId,
                        chatId,
                        new com.alex.messenger.chat.dto.CreateInviteLinkRequest(
                                "Primary",
                                5,
                                Instant.now().plusSeconds(3600)
                        )
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(chatInviteLinkRepository, never()).save(any(ChatInviteLinkEntity.class));
    }

    @Test
    void reportChatRejectsWhenThrottleExceeded() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many chat reports submitted recently"))
                .when(abuseProtectionService)
                .assertChatReportAllowed(requesterId);

        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.reportChat(
                        requesterId,
                        chatId,
                        new com.alex.messenger.chat.dto.ReportChatRequest("spam", "bulk abuse")
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(chatReportRepository, never()).save(any(ChatReportEntity.class));
    }

    @Test
    void joinByInviteLinkRejectsWhenJoinRequestThrottleExceeded() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatInviteLinkEntity inviteLink = new ChatInviteLinkEntity();
        inviteLink.setChatId(chatId);
        inviteLink.setToken("invite-token");
        inviteLink.setExpiresAt(Instant.now().plusSeconds(3600));
        inviteLink.setUsageCount(0);

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setJoinRequiresApproval(true);

        when(chatInviteLinkRepository.findByToken("invite-token")).thenReturn(Optional.of(inviteLink));
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatBanRepository.findById(any(ChatBanId.class))).thenReturn(Optional.empty());
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(false);
        when(chatJoinRequestRepository.findByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(Optional.empty());
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many join requests created recently"))
                .when(abuseProtectionService)
                .assertJoinRequestCreationAllowed(requesterId, chatId);

        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.joinByInviteLink(requesterId, "invite-token"),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(chatJoinRequestRepository, never()).save(any(ChatJoinRequestEntity.class));
    }

    @Test
    void discoverPublicChatsRejectsInvalidLimit() {
        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.discoverPublicChats(UUID.randomUUID(), "alpha", 0),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("limit must be between 1 and 20");
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
    void buildTypingEventIncludesVisibleTopicId() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setForumEnabled(true);

        ChatMemberEntity membership = member(chatId, requesterId);
        membership.setCanSendMessages(true);

        ForumTopicEntity topic = new ForumTopicEntity();
        topic.setId(topicId);
        topic.setChatId(chatId);
        topic.setHidden(false);
        topic.setClosed(false);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));
        when(forumTopicRepository.findByIdAndChatId(topicId, chatId)).thenReturn(Optional.of(topic));

        TypingEventResponse response = chatService.buildTypingEvent(requesterId, chatId, topicId, true);

        assertThat(response.chatId()).isEqualTo(chatId);
        assertThat(response.userId()).isEqualTo(requesterId);
        assertThat(response.typing()).isTrue();
        assertThat(response.topicId()).isEqualTo(topicId);
    }

    @Test
    void incrementUnreadCountsSkipsHiddenForumTopicMessages() {
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID hiddenTopicId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setForumEnabled(true);

        ForumTopicEntity hiddenTopic = new ForumTopicEntity();
        hiddenTopic.setId(hiddenTopicId);
        hiddenTopic.setChatId(chatId);
        hiddenTopic.setHidden(true);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(forumTopicRepository.findByIdAndChatId(hiddenTopicId, chatId)).thenReturn(Optional.of(hiddenTopic));

        chatService.incrementUnreadCounts(
                chatId,
                senderId,
                new MessageTextContent("hidden update", List.<MessageTextEntityPayload>of()),
                null,
                hiddenTopicId
        );

        verify(chatMemberRepository, never()).findAllByIdChatId(chatId);
        verify(chatMemberRepository, never()).saveAll(any());
    }

    @Test
    void markReadRejectsMessagesFromAnotherChat() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID otherChatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");

        ChatMemberEntity membership = member(chatId, requesterId);
        membership.setUnreadCount(3);
        membership.setMentionCount(1);
        membership.setReplyCount(1);

        MessageLookupEntity message = new MessageLookupEntity();
        message.setMessageId(messageId);
        message.setChatId(otherChatId);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));
        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(message));

        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.markRead(requesterId, chatId, messageId),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(chatMemberRepository, never()).save(any(ChatMemberEntity.class));
    }

    @Test
    void markReadRejectsHiddenForumTopicMessages() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setForumEnabled(true);

        ChatMemberEntity membership = member(chatId, requesterId);

        MessageLookupEntity message = new MessageLookupEntity();
        message.setMessageId(messageId);
        message.setChatId(chatId);
        message.setTopicId(topicId);

        ForumTopicEntity hiddenTopic = new ForumTopicEntity();
        hiddenTopic.setId(topicId);
        hiddenTopic.setChatId(chatId);
        hiddenTopic.setHidden(true);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));
        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(forumTopicRepository.findByIdAndChatId(topicId, chatId)).thenReturn(Optional.of(hiddenTopic));

        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.markRead(requesterId, chatId, messageId),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(chatMemberRepository, never()).save(any(ChatMemberEntity.class));
    }

    @Test
    void markReadDoesNotMoveLastReadBackwards() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID currentLastReadMessageId = UUID.randomUUID();
        UUID staleMessageId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");

        ChatMemberEntity membership = member(chatId, requesterId);
        membership.setLastReadMessageId(currentLastReadMessageId);
        membership.setLastReadAt(Instant.parse("2026-03-14T12:00:00Z"));
        membership.setUnreadCount(0);
        membership.setMentionCount(0);
        membership.setReplyCount(0);

        MessageLookupEntity currentLastRead = new MessageLookupEntity();
        currentLastRead.setMessageId(currentLastReadMessageId);
        currentLastRead.setChatId(chatId);
        currentLastRead.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));

        MessageLookupEntity staleMessage = new MessageLookupEntity();
        staleMessage.setMessageId(staleMessageId);
        staleMessage.setChatId(chatId);
        staleMessage.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));
        when(messageLookupRepository.findById(staleMessageId)).thenReturn(Optional.of(staleMessage));
        when(messageLookupRepository.findById(currentLastReadMessageId)).thenReturn(Optional.of(currentLastRead));

        ChatReadEventResponse response = chatService.markRead(requesterId, chatId, staleMessageId);

        assertThat(response.messageId()).isEqualTo(currentLastReadMessageId);
        assertThat(response.readAt()).isEqualTo(Instant.parse("2026-03-14T12:00:00Z"));
        assertThat(membership.getLastReadMessageId()).isEqualTo(currentLastReadMessageId);
        verify(chatMemberRepository, never()).save(any(ChatMemberEntity.class));
    }

    @Test
    void markReadRecordsSyncEventWhenClearingManualUnreadAtSameBoundary() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant readAt = Instant.parse("2026-03-14T12:00:00Z");

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");

        ChatMemberEntity membership = member(chatId, requesterId);
        membership.setLastReadMessageId(messageId);
        membership.setLastReadAt(readAt);
        membership.setManuallyMarkedUnread(true);

        MessageLookupEntity currentReadMessage = new MessageLookupEntity();
        currentReadMessage.setMessageId(messageId);
        currentReadMessage.setChatId(chatId);
        currentReadMessage.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));
        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(currentReadMessage));

        ChatReadEventResponse response = chatService.markRead(requesterId, chatId, messageId);

        assertThat(response.messageId()).isEqualTo(messageId);
        assertThat(response.readAt()).isEqualTo(readAt);
        assertThat(membership.getManuallyMarkedUnread()).isFalse();
        verify(chatMemberRepository).save(membership);
        ArgumentCaptor<java.util.Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(java.util.Map.class);
        verify(userSyncService).recordForUsers(
                eq(List.of(requesterId)),
                eq("CHAT_READ"),
                eq("MESSAGE"),
                eq(messageId),
                eq(chatId),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue()).containsEntry("chatId", chatId);
        assertThat(payloadCaptor.getValue()).containsEntry("messageId", messageId);
        assertThat(payloadCaptor.getValue()).containsEntry("userId", requesterId);
        assertThat(payloadCaptor.getValue()).containsEntry("readAt", readAt);
        assertThat(payloadCaptor.getValue()).containsEntry("unreadCount", 0);
        assertThat(payloadCaptor.getValue()).containsEntry("mentionCount", 0);
        assertThat(payloadCaptor.getValue()).containsEntry("replyCount", 0);
        assertThat(payloadCaptor.getValue()).containsEntry("manuallyMarkedUnread", false);
        ArgumentCaptor<ChatInboxFanoutEvent> eventCaptor = ArgumentCaptor.forClass(ChatInboxFanoutEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("CHAT_READ");
        assertThat(eventCaptor.getValue().chatId()).isEqualTo(chatId);
        assertThat(eventCaptor.getValue().userIds()).containsExactly(requesterId);
        assertThat(eventCaptor.getValue().removedUserIds()).isEmpty();
    }

    @Test
    void markReadIgnoresHiddenCurrentLastReadMessage() {
        UUID requesterId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID hiddenCurrentLastReadMessageId = UUID.randomUUID();
        UUID visibleTargetMessageId = UUID.randomUUID();
        UUID hiddenTopicId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setForumEnabled(true);

        ChatMemberEntity membership = member(chatId, requesterId);
        membership.setLastReadMessageId(hiddenCurrentLastReadMessageId);
        membership.setUnreadCount(1);
        membership.setMentionCount(0);
        membership.setReplyCount(0);

        MessageLookupEntity hiddenCurrentLastRead = new MessageLookupEntity();
        hiddenCurrentLastRead.setMessageId(hiddenCurrentLastReadMessageId);
        hiddenCurrentLastRead.setChatId(chatId);
        hiddenCurrentLastRead.setTopicId(hiddenTopicId);
        hiddenCurrentLastRead.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));

        MessageLookupEntity visibleTarget = new MessageLookupEntity();
        visibleTarget.setMessageId(visibleTargetMessageId);
        visibleTarget.setChatId(chatId);
        visibleTarget.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));

        MessageEntity visibleUnreadMessage = new MessageEntity();
        visibleUnreadMessage.setKey(new MessagePrimaryKey(chatId, UUID.randomUUID()));
        visibleUnreadMessage.setSenderId(otherUserId);
        visibleUnreadMessage.setCreatedAt(Instant.parse("2026-03-14T10:05:00Z"));

        ForumTopicEntity hiddenTopic = new ForumTopicEntity();
        hiddenTopic.setId(hiddenTopicId);
        hiddenTopic.setChatId(chatId);
        hiddenTopic.setHidden(true);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));
        when(messageLookupRepository.findById(visibleTargetMessageId)).thenReturn(Optional.of(visibleTarget));
        when(messageLookupRepository.findById(hiddenCurrentLastReadMessageId)).thenReturn(Optional.of(hiddenCurrentLastRead));
        when(forumTopicRepository.findByIdAndChatId(hiddenTopicId, chatId)).thenReturn(Optional.of(hiddenTopic));
        when(messageRepository.findAllByChatIdAfterMessageId(chatId, visibleTargetMessageId)).thenReturn(List.of(visibleUnreadMessage));
        when(forumTopicRepository.findVisibleTopics(chatId)).thenReturn(List.of());
        when(userRepository.findById(requesterId)).thenReturn(Optional.empty());

        ChatReadEventResponse response = chatService.markRead(requesterId, chatId, visibleTargetMessageId);

        assertThat(response.messageId()).isEqualTo(visibleTargetMessageId);
        assertThat(membership.getLastReadMessageId()).isEqualTo(visibleTargetMessageId);
        assertThat(membership.getUnreadCount()).isEqualTo(1);
        verify(chatMemberRepository).save(membership);
    }

    @Test
    void markReadRecalculatesRemainingUnreadCountersAfterPartialRead() {
        UUID requesterId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID readBoundaryMessageId = UUID.randomUUID();
        UUID replyTargetMessageId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");

        ChatMemberEntity membership = member(chatId, requesterId);
        membership.setUnreadCount(4);
        membership.setMentionCount(2);
        membership.setReplyCount(1);

        MessageLookupEntity readBoundary = new MessageLookupEntity();
        readBoundary.setMessageId(readBoundaryMessageId);
        readBoundary.setChatId(chatId);
        readBoundary.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));

        MessageLookupEntity replyTarget = new MessageLookupEntity();
        replyTarget.setMessageId(replyTargetMessageId);
        replyTarget.setChatId(chatId);
        replyTarget.setSenderId(requesterId);

        MessageEntity mentionReplyMessage = new MessageEntity();
        mentionReplyMessage.setKey(new MessagePrimaryKey(chatId, UUID.randomUUID()));
        mentionReplyMessage.setSenderId(otherUserId);
        mentionReplyMessage.setReplyToMessageId(replyTargetMessageId);
        mentionReplyMessage.setCiphertext("cipher-mention-reply");
        mentionReplyMessage.setNonce("nonce-mention-reply");
        mentionReplyMessage.setKeyVersion(1);

        MessageEntity plainUnreadMessage = new MessageEntity();
        plainUnreadMessage.setKey(new MessagePrimaryKey(chatId, UUID.randomUUID()));
        plainUnreadMessage.setSenderId(otherUserId);
        plainUnreadMessage.setCiphertext("cipher-plain");
        plainUnreadMessage.setNonce("nonce-plain");
        plainUnreadMessage.setKeyVersion(1);

        MessageEntity ownUnreadMessage = new MessageEntity();
        ownUnreadMessage.setKey(new MessagePrimaryKey(chatId, UUID.randomUUID()));
        ownUnreadMessage.setSenderId(requesterId);
        ownUnreadMessage.setCiphertext("cipher-own");
        ownUnreadMessage.setNonce("nonce-own");
        ownUnreadMessage.setKeyVersion(1);

        MessageEntity deletedUnreadMessage = new MessageEntity();
        deletedUnreadMessage.setKey(new MessagePrimaryKey(chatId, UUID.randomUUID()));
        deletedUnreadMessage.setSenderId(otherUserId);
        deletedUnreadMessage.setDeletedAt(Instant.parse("2026-03-14T10:05:00Z"));
        deletedUnreadMessage.setCiphertext("cipher-deleted");
        deletedUnreadMessage.setNonce("nonce-deleted");
        deletedUnreadMessage.setKeyVersion(1);

        UserEntity requester = new UserEntity();
        requester.setId(requesterId);
        requester.setUsername("reader_user");

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));
        when(messageLookupRepository.findById(readBoundaryMessageId)).thenReturn(Optional.of(readBoundary));
        when(messageRepository.findAllByChatIdAfterMessageId(chatId, readBoundaryMessageId)).thenReturn(List.of(
                mentionReplyMessage,
                plainUnreadMessage,
                ownUnreadMessage,
                deletedUnreadMessage
        ));
        when(messageLookupRepository.findAllById(List.of(replyTargetMessageId))).thenReturn(List.of(replyTarget));
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(chatEncryptionService.decrypt(chatId, "cipher-mention-reply", "nonce-mention-reply", 1)).thenReturn("plain-mention-reply");
        when(chatEncryptionService.decrypt(chatId, "cipher-plain", "nonce-plain", 1)).thenReturn("plain-plain");
        when(messageContentCodec.decode("plain-mention-reply")).thenReturn(new MessageTextContent("hello @reader_user", List.of()));
        when(messageContentCodec.decode("plain-plain")).thenReturn(new MessageTextContent("just text", List.of()));

        ChatReadEventResponse response = chatService.markRead(requesterId, chatId, readBoundaryMessageId);

        assertThat(response.messageId()).isEqualTo(readBoundaryMessageId);
        assertThat(membership.getUnreadCount()).isEqualTo(2);
        assertThat(membership.getMentionCount()).isEqualTo(1);
        assertThat(membership.getReplyCount()).isEqualTo(1);
        verify(chatMemberRepository).save(membership);
    }

    @Test
    void markReadIgnoresHiddenForumTopicMessagesInUnreadTailCounters() {
        UUID requesterId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID readBoundaryMessageId = UUID.randomUUID();
        UUID hiddenTopicId = UUID.randomUUID();
        UUID visibleTopicId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setForumEnabled(true);

        ChatMemberEntity membership = member(chatId, requesterId);
        membership.setUnreadCount(2);
        membership.setMentionCount(0);
        membership.setReplyCount(0);

        MessageLookupEntity readBoundary = new MessageLookupEntity();
        readBoundary.setMessageId(readBoundaryMessageId);
        readBoundary.setChatId(chatId);
        readBoundary.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));

        MessageEntity hiddenUnreadMessage = new MessageEntity();
        hiddenUnreadMessage.setKey(new MessagePrimaryKey(chatId, UUID.randomUUID()));
        hiddenUnreadMessage.setSenderId(otherUserId);
        hiddenUnreadMessage.setTopicId(hiddenTopicId);

        MessageEntity visibleUnreadMessage = new MessageEntity();
        visibleUnreadMessage.setKey(new MessagePrimaryKey(chatId, UUID.randomUUID()));
        visibleUnreadMessage.setSenderId(otherUserId);
        visibleUnreadMessage.setTopicId(visibleTopicId);

        ForumTopicEntity visibleTopic = new ForumTopicEntity();
        visibleTopic.setId(visibleTopicId);
        visibleTopic.setChatId(chatId);
        visibleTopic.setHidden(false);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));
        when(messageLookupRepository.findById(readBoundaryMessageId)).thenReturn(Optional.of(readBoundary));
        when(messageRepository.findAllByChatIdAfterMessageId(chatId, readBoundaryMessageId))
                .thenReturn(List.of(hiddenUnreadMessage, visibleUnreadMessage));
        when(forumTopicRepository.findVisibleTopics(chatId)).thenReturn(List.of(visibleTopic));
        when(userRepository.findById(requesterId)).thenReturn(Optional.empty());

        ChatReadEventResponse response = chatService.markRead(requesterId, chatId, readBoundaryMessageId);

        assertThat(response.messageId()).isEqualTo(readBoundaryMessageId);
        assertThat(membership.getUnreadCount()).isEqualTo(1);
        assertThat(membership.getMentionCount()).isZero();
        assertThat(membership.getReplyCount()).isZero();
        verify(chatMemberRepository).save(membership);
    }

    @Test
    void listChatsIncludesLastMessageSnapshotForMediaMessage() {
        UUID requesterId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setTitle("Media room");
        chat.setCreatedAt(Instant.parse("2026-03-12T10:00:00Z"));
        chat.setLastMessageAt(Instant.parse("2026-03-12T12:00:00Z"));

        ChatMemberEntity requesterMembership = member(chatId, requesterId);
        ChatMemberEntity senderMembership = member(chatId, senderId);

        MessageEntity message = new MessageEntity();
        message.setKey(new MessagePrimaryKey(chatId, messageId));
        message.setSenderId(senderId);
        message.setAttachmentIds(List.of(attachmentId));
        message.setCreatedAt(Instant.parse("2026-03-12T12:00:00Z"));

        AttachmentEntity attachment = new AttachmentEntity();
        attachment.setId(attachmentId);
        attachment.setKind("IMAGE");

        UserEntity sender = new UserEntity();
        sender.setId(senderId);
        sender.setDisplayName("Alice");

        when(chatMemberRepository.findMembershipsOrderedForUser(requesterId, false))
                .thenReturn(List.of(requesterMembership));
        when(chatDraftRepository.findAllByIdUserIdAndIdChatIdIn(requesterId, List.of(chatId)))
                .thenReturn(List.of());
        when(chatRepository.findAllById(List.of(chatId))).thenReturn(List.of(chat));
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.countByIdChatId(chatId)).thenReturn(2L);
        when(messageRepository.findRecentByChatId(chatId, 1)).thenReturn(List.of(message));
        when(attachmentRepository.findAllByIdIn(List.of(attachmentId))).thenReturn(List.of(attachment));
        when(chatMemberRepository.findById(new ChatMemberId(chatId, senderId))).thenReturn(Optional.of(senderMembership));
        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(profilePhotoService.buildPhotoAccess(any(), any(), any())).thenReturn(new PhotoAccess(null, null));

        List<ChatSummaryResponse> chats = chatService.listChats(requesterId, false);

        assertThat(chats).hasSize(1);
        assertThat(chats.get(0).lastMessage()).isNotNull();
        assertThat(chats.get(0).lastMessage().messageId()).isEqualTo(messageId);
        assertThat(chats.get(0).lastMessage().senderDisplayName()).isEqualTo("Alice");
        assertThat(chats.get(0).lastMessage().messageType()).isEqualTo("IMAGE");
        assertThat(chats.get(0).lastMessage().previewText()).isEqualTo("sent a photo");
        assertThat(chats.get(0).lastMessage().outgoing()).isFalse();
    }

    @Test
    void listChatsMarksDeletedLastMessageInSnapshot() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setTitle("Ops");
        chat.setCreatedAt(Instant.parse("2026-03-12T10:00:00Z"));
        chat.setLastMessageAt(Instant.parse("2026-03-12T12:00:00Z"));

        ChatMemberEntity requesterMembership = member(chatId, requesterId);

        MessageEntity message = new MessageEntity();
        message.setKey(new MessagePrimaryKey(chatId, messageId));
        message.setSenderId(requesterId);
        message.setCreatedAt(Instant.parse("2026-03-12T12:00:00Z"));
        message.setDeletedAt(Instant.parse("2026-03-12T12:05:00Z"));

        when(chatMemberRepository.findMembershipsOrderedForUser(requesterId, false))
                .thenReturn(List.of(requesterMembership));
        when(chatDraftRepository.findAllByIdUserIdAndIdChatIdIn(requesterId, List.of(chatId)))
                .thenReturn(List.of());
        when(chatRepository.findAllById(List.of(chatId))).thenReturn(List.of(chat));
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.countByIdChatId(chatId)).thenReturn(1L);
        when(messageRepository.findRecentByChatId(chatId, 1)).thenReturn(List.of(message));
        when(profilePhotoService.buildPhotoAccess(any(), any(), any())).thenReturn(new PhotoAccess(null, null));

        List<ChatSummaryResponse> chats = chatService.listChats(requesterId, false);

        assertThat(chats).hasSize(1);
        assertThat(chats.get(0).lastMessage()).isNotNull();
        assertThat(chats.get(0).lastMessage().messageId()).isEqualTo(messageId);
        assertThat(chats.get(0).lastMessage().previewText()).isEqualTo("Message deleted");
        assertThat(chats.get(0).lastMessage().deletedAt()).isEqualTo(Instant.parse("2026-03-12T12:05:00Z"));
        assertThat(chats.get(0).lastMessage().outgoing()).isTrue();
    }

    @Test
    void searchChatsMatchesLastMessagePreviewText() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setTitle("Budget room");
        chat.setCreatedAt(Instant.parse("2026-03-12T10:00:00Z"));
        chat.setLastMessageAt(Instant.parse("2026-03-12T12:00:00Z"));

        ChatMemberEntity requesterMembership = member(chatId, requesterId);

        MessageEntity message = new MessageEntity();
        message.setKey(new MessagePrimaryKey(chatId, messageId));
        message.setSenderId(requesterId);
        message.setCiphertext("ciphertext");
        message.setNonce("nonce");
        message.setKeyVersion(1);
        message.setCreatedAt(Instant.parse("2026-03-12T12:00:00Z"));

        when(chatMemberRepository.findMembershipsOrderedForUser(requesterId, false))
                .thenReturn(List.of(requesterMembership));
        when(chatMemberRepository.findMembershipsOrderedForUser(requesterId, true))
                .thenReturn(List.of());
        when(chatDraftRepository.findAllByIdUserIdAndIdChatIdIn(requesterId, List.of(chatId)))
                .thenReturn(List.of());
        when(chatRepository.findAllById(List.of(chatId))).thenReturn(List.of(chat));
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.countByIdChatId(chatId)).thenReturn(1L);
        when(messageRepository.findRecentByChatId(chatId, 1)).thenReturn(List.of(message));
        when(chatEncryptionService.decrypt(chatId, "ciphertext", "nonce", 1)).thenReturn("decoded-search");
        when(messageContentCodec.decode("decoded-search")).thenReturn(new MessageTextContent("Alpha budget update", List.of()));
        when(profilePhotoService.buildPhotoAccess(any(), any(), any())).thenReturn(new PhotoAccess(null, null));

        List<ChatSummaryResponse> chats = chatService.searchChats(requesterId, "alpha", 10);

        assertThat(chats).extracting(ChatSummaryResponse::chatId).containsExactly(chatId);
        assertThat(chats.get(0).lastMessage()).isNotNull();
        assertThat(chats.get(0).lastMessage().previewText()).isEqualTo("Alpha budget update");
    }

    @Test
    void listChatsSkipsHiddenForumTopicMessageInLastMessageSnapshot() {
        UUID requesterId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID hiddenTopicId = UUID.randomUUID();
        UUID visibleTopicId = UUID.randomUUID();
        UUID hiddenMessageId = UUID.randomUUID();
        UUID visibleMessageId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setTitle("Forum room");
        chat.setForumEnabled(true);
        chat.setCreatedAt(Instant.parse("2026-03-12T10:00:00Z"));
        chat.setLastMessageAt(Instant.parse("2026-03-12T12:00:00Z"));

        ChatMemberEntity requesterMembership = member(chatId, requesterId);
        ChatMemberEntity senderMembership = member(chatId, senderId);

        ForumTopicEntity visibleTopic = new ForumTopicEntity();
        visibleTopic.setId(visibleTopicId);
        visibleTopic.setChatId(chatId);
        visibleTopic.setGeneralTopic(false);
        visibleTopic.setHidden(false);

        MessageEntity hiddenMessage = new MessageEntity();
        hiddenMessage.setKey(new MessagePrimaryKey(chatId, hiddenMessageId));
        hiddenMessage.setSenderId(senderId);
        hiddenMessage.setTopicId(hiddenTopicId);
        hiddenMessage.setCiphertext("cipher-hidden");
        hiddenMessage.setNonce("nonce-hidden");
        hiddenMessage.setKeyVersion(1);
        hiddenMessage.setCreatedAt(Instant.parse("2026-03-12T12:00:00Z"));

        MessageEntity visibleMessage = new MessageEntity();
        visibleMessage.setKey(new MessagePrimaryKey(chatId, visibleMessageId));
        visibleMessage.setSenderId(senderId);
        visibleMessage.setTopicId(visibleTopicId);
        visibleMessage.setCiphertext("cipher-visible");
        visibleMessage.setNonce("nonce-visible");
        visibleMessage.setKeyVersion(1);
        visibleMessage.setCreatedAt(Instant.parse("2026-03-12T11:55:00Z"));

        UserEntity sender = new UserEntity();
        sender.setId(senderId);
        sender.setDisplayName("Alice");

        when(chatMemberRepository.findMembershipsOrderedForUser(requesterId, false))
                .thenReturn(List.of(requesterMembership));
        when(chatDraftRepository.findAllByIdUserIdAndIdChatIdIn(requesterId, List.of(chatId)))
                .thenReturn(List.of());
        when(chatRepository.findAllById(List.of(chatId))).thenReturn(List.of(chat));
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.countByIdChatId(chatId)).thenReturn(2L);
        when(messageRepository.findRecentByChatId(chatId, 100)).thenReturn(List.of(hiddenMessage, visibleMessage));
        when(forumTopicRepository.findVisibleTopics(chatId)).thenReturn(List.of(visibleTopic));
        when(chatMemberRepository.findById(new ChatMemberId(chatId, senderId))).thenReturn(Optional.of(senderMembership));
        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(chatEncryptionService.decrypt(chatId, "cipher-visible", "nonce-visible", 1)).thenReturn("decoded-visible");
        when(messageContentCodec.decode("decoded-visible")).thenReturn(new MessageTextContent("Visible topic update", List.of()));
        when(profilePhotoService.buildPhotoAccess(any(), any(), any())).thenReturn(new PhotoAccess(null, null));

        List<ChatSummaryResponse> chats = chatService.listChats(requesterId, false);

        assertThat(chats).hasSize(1);
        assertThat(chats.get(0).lastMessage()).isNotNull();
        assertThat(chats.get(0).lastMessage().messageId()).isEqualTo(visibleMessageId);
        assertThat(chats.get(0).lastMessage().previewText()).isEqualTo("Visible topic update");
        assertThat(chats.get(0).lastMessageAt()).isEqualTo(Instant.parse("2026-03-12T11:55:00Z"));
    }

    @Test
    void listChatsDoesNotLeakHiddenForumTopicTimestampWithoutVisibleMessages() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID hiddenTopicId = UUID.randomUUID();
        UUID hiddenMessageId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setTitle("Forum room");
        chat.setForumEnabled(true);
        chat.setCreatedAt(Instant.parse("2026-03-12T10:00:00Z"));
        chat.setLastMessageAt(Instant.parse("2026-03-12T12:00:00Z"));

        ChatMemberEntity requesterMembership = member(chatId, requesterId);

        MessageEntity hiddenMessage = new MessageEntity();
        hiddenMessage.setKey(new MessagePrimaryKey(chatId, hiddenMessageId));
        hiddenMessage.setTopicId(hiddenTopicId);
        hiddenMessage.setCreatedAt(Instant.parse("2026-03-12T12:00:00Z"));

        when(chatMemberRepository.findMembershipsOrderedForUser(requesterId, false))
                .thenReturn(List.of(requesterMembership));
        when(chatDraftRepository.findAllByIdUserIdAndIdChatIdIn(requesterId, List.of(chatId)))
                .thenReturn(List.of());
        when(chatRepository.findAllById(List.of(chatId))).thenReturn(List.of(chat));
        when(chatMemberRepository.countByIdChatId(chatId)).thenReturn(1L);
        when(forumTopicRepository.countByChatIdAndHiddenFalse(chatId)).thenReturn(0L);
        when(messageRepository.findRecentByChatId(chatId, 100)).thenReturn(List.of(hiddenMessage));
        when(forumTopicRepository.findVisibleTopics(chatId)).thenReturn(List.of());

        List<ChatSummaryResponse> chats = chatService.listChats(requesterId, false);

        assertThat(chats).hasSize(1);
        assertThat(chats.get(0).lastMessage()).isNull();
        assertThat(chats.get(0).lastMessageAt()).isEqualTo(Instant.parse("2026-03-12T10:00:00Z"));
    }

    @Test
    void listChatsSkipsHiddenForumTopicPinnedMessageInSummary() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID hiddenTopicId = UUID.randomUUID();
        UUID pinnedMessageId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setTitle("Forum room");
        chat.setForumEnabled(true);
        chat.setPinnedMessageId(pinnedMessageId);
        chat.setCreatedAt(Instant.parse("2026-03-12T10:00:00Z"));

        ChatMemberEntity requesterMembership = member(chatId, requesterId);

        MessageLookupEntity pinnedMessage = new MessageLookupEntity();
        pinnedMessage.setMessageId(pinnedMessageId);
        pinnedMessage.setChatId(chatId);
        pinnedMessage.setTopicId(hiddenTopicId);
        pinnedMessage.setDeletedAt(null);

        ForumTopicEntity hiddenTopic = new ForumTopicEntity();
        hiddenTopic.setId(hiddenTopicId);
        hiddenTopic.setChatId(chatId);
        hiddenTopic.setHidden(true);
        hiddenTopic.setGeneralTopic(false);

        when(chatMemberRepository.findMembershipsOrderedForUser(requesterId, false))
                .thenReturn(List.of(requesterMembership));
        when(chatDraftRepository.findAllByIdUserIdAndIdChatIdIn(requesterId, List.of(chatId)))
                .thenReturn(List.of());
        when(chatRepository.findAllById(List.of(chatId))).thenReturn(List.of(chat));
        when(chatMemberRepository.countByIdChatId(chatId)).thenReturn(1L);
        when(forumTopicRepository.countByChatIdAndHiddenFalse(chatId)).thenReturn(0L);
        when(messageRepository.findRecentByChatId(chatId, 100)).thenReturn(List.of());
        when(messageLookupRepository.findById(pinnedMessageId)).thenReturn(Optional.of(pinnedMessage));
        when(forumTopicRepository.findByIdAndChatId(hiddenTopicId, chatId)).thenReturn(Optional.of(hiddenTopic));

        List<ChatSummaryResponse> chats = chatService.listChats(requesterId, false);

        assertThat(chats).hasSize(1);
        assertThat(chats.get(0).pinnedMessageId()).isNull();
    }

    @Test
    void listChatsSkipsDeletedPinnedMessageInSummary() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID pinnedMessageId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setTitle("Ops room");
        chat.setPinnedMessageId(pinnedMessageId);
        chat.setCreatedAt(Instant.parse("2026-03-12T10:00:00Z"));

        ChatMemberEntity requesterMembership = member(chatId, requesterId);

        MessageLookupEntity pinnedMessage = new MessageLookupEntity();
        pinnedMessage.setMessageId(pinnedMessageId);
        pinnedMessage.setChatId(chatId);
        pinnedMessage.setDeletedAt(Instant.parse("2026-03-12T10:30:00Z"));

        when(chatMemberRepository.findMembershipsOrderedForUser(requesterId, false))
                .thenReturn(List.of(requesterMembership));
        when(chatDraftRepository.findAllByIdUserIdAndIdChatIdIn(requesterId, List.of(chatId)))
                .thenReturn(List.of());
        when(chatRepository.findAllById(List.of(chatId))).thenReturn(List.of(chat));
        when(chatMemberRepository.countByIdChatId(chatId)).thenReturn(1L);
        when(messageRepository.findRecentByChatId(chatId, 1)).thenReturn(List.of());
        when(messageLookupRepository.findById(pinnedMessageId)).thenReturn(Optional.of(pinnedMessage));

        List<ChatSummaryResponse> chats = chatService.listChats(requesterId, false);

        assertThat(chats).hasSize(1);
        assertThat(chats.get(0).pinnedMessageId()).isNull();
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
    void listChatsPageUsesOpaqueCursorWithoutChangingOrdering() {
        UUID requesterId = UUID.randomUUID();
        UUID firstChatId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID secondChatId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        ChatMemberEntity firstMembership = member(firstChatId, requesterId);
        ChatMemberEntity secondMembership = member(secondChatId, requesterId);

        ChatEntity firstChat = new ChatEntity();
        firstChat.setId(firstChatId);
        firstChat.setChatType("GROUP");
        firstChat.setTitle("First");
        firstChat.setCreatedAt(Instant.parse("2026-03-12T10:00:00Z"));
        firstChat.setLastMessageAt(Instant.parse("2026-03-12T12:00:00Z"));

        ChatEntity secondChat = new ChatEntity();
        secondChat.setId(secondChatId);
        secondChat.setChatType("GROUP");
        secondChat.setTitle("Second");
        secondChat.setCreatedAt(Instant.parse("2026-03-12T09:00:00Z"));
        secondChat.setLastMessageAt(Instant.parse("2026-03-12T11:00:00Z"));

        when(chatMemberRepository.findMembershipsOrderedForUser(requesterId, false))
                .thenReturn(List.of(firstMembership, secondMembership));
        when(chatDraftRepository.findAllByIdUserIdAndIdChatIdIn(requesterId, List.of(firstChatId, secondChatId)))
                .thenReturn(List.of());
        when(chatRepository.findAllById(any()))
                .thenReturn(List.of(firstChat, secondChat));

        ChatService.ChatListSlice firstPage = chatService.listChatsPage(requesterId, false, null, 1);
        ChatService.ChatListSlice secondPage = chatService.listChatsPage(
                requesterId,
                false,
                firstPage.nextCursor(),
                1
        );

        assertThat(firstPage.chats()).extracting(ChatSummaryResponse::chatId).containsExactly(firstChatId);
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isNotBlank();

        assertThat(secondPage.chats()).extracting(ChatSummaryResponse::chatId).containsExactly(secondChatId);
        assertThat(secondPage.hasMore()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void sliceChatListUsesChatIdTieBreakerForStableSeekPagination() {
        ChatSummaryResponse firstChat = chatSummary(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                false,
                null,
                Instant.parse("2026-03-12T12:00:00Z")
        );
        ChatSummaryResponse secondChat = chatSummary(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                false,
                null,
                Instant.parse("2026-03-12T12:00:00Z")
        );
        ChatSummaryResponse thirdChat = chatSummary(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                false,
                null,
                Instant.parse("2026-03-12T12:00:00Z")
        );

        List<ChatSummaryResponse> chats = List.of(firstChat, secondChat, thirdChat);

        ChatService.ChatListSlice firstPage = chatService.sliceChatList(chats, null, 2);
        ChatService.ChatListSlice secondPage = chatService.sliceChatList(chats, firstPage.nextCursor(), 2);

        assertThat(firstPage.chats()).extracting(ChatSummaryResponse::chatId)
                .containsExactly(firstChat.chatId(), secondChat.chatId());
        assertThat(firstPage.nextCursor()).isNotBlank();
        assertThat(firstPage.hasMore()).isTrue();

        assertThat(secondPage.chats()).extracting(ChatSummaryResponse::chatId)
                .containsExactly(thirdChat.chatId());
        assertThat(secondPage.hasMore()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void listAllChatsSortsPinnedChatsAheadOfNewerUnpinnedEntries() {
        UUID requesterId = UUID.randomUUID();
        UUID pinnedChatId = UUID.randomUUID();
        UUID unpinnedChatId = UUID.randomUUID();

        ChatMemberEntity unpinnedMembership = member(unpinnedChatId, requesterId);
        unpinnedMembership.setArchived(false);
        unpinnedMembership.setListPinned(false);

        ChatMemberEntity pinnedMembership = member(pinnedChatId, requesterId);
        pinnedMembership.setArchived(false);
        pinnedMembership.setListPinned(true);
        pinnedMembership.setListPinOrder(0);

        ChatEntity pinnedChat = new ChatEntity();
        pinnedChat.setId(pinnedChatId);
        pinnedChat.setChatType("GROUP");
        pinnedChat.setTitle("Pinned");
        pinnedChat.setCreatedAt(Instant.parse("2026-03-19T09:00:00Z"));
        pinnedChat.setLastMessageAt(Instant.parse("2026-03-19T09:30:00Z"));

        ChatEntity unpinnedChat = new ChatEntity();
        unpinnedChat.setId(unpinnedChatId);
        unpinnedChat.setChatType("GROUP");
        unpinnedChat.setTitle("Unpinned");
        unpinnedChat.setCreatedAt(Instant.parse("2026-03-19T10:00:00Z"));
        unpinnedChat.setLastMessageAt(Instant.parse("2026-03-19T11:00:00Z"));

        when(chatMemberRepository.findMembershipsOrderedForUser(requesterId, false))
                .thenReturn(List.of(unpinnedMembership, pinnedMembership));
        when(chatMemberRepository.findMembershipsOrderedForUser(requesterId, true)).thenReturn(List.of());
        when(chatDraftRepository.findAllByIdUserIdAndIdChatIdIn(requesterId, List.of(unpinnedChatId, pinnedChatId)))
                .thenReturn(List.of());
        when(chatRepository.findAllById(List.of(unpinnedChatId, pinnedChatId))).thenReturn(List.of(unpinnedChat, pinnedChat));
        when(chatMemberRepository.countByIdChatId(pinnedChatId)).thenReturn(1L);
        when(chatMemberRepository.countByIdChatId(unpinnedChatId)).thenReturn(1L);
        when(messageRepository.findRecentByChatId(pinnedChatId, 1)).thenReturn(List.of());
        when(messageRepository.findRecentByChatId(unpinnedChatId, 1)).thenReturn(List.of());

        List<ChatSummaryResponse> chats = chatService.listAllChats(requesterId);

        assertThat(chats).extracting(ChatSummaryResponse::chatId)
                .containsExactly(pinnedChatId, unpinnedChatId);
        assertThat(chats.get(0).pinned()).isTrue();
        assertThat(chats.get(0).pinOrder()).isZero();
        assertThat(chats.get(1).pinned()).isFalse();
    }

    @Test
    void pinChatToListAssignsNextPinOrderAfterExistingPins() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID existingPinnedChatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setTitle("Target");
        chat.setCreatedAt(Instant.parse("2026-03-19T10:00:00Z"));
        chat.setLastMessageAt(Instant.parse("2026-03-19T10:10:00Z"));

        ChatMemberEntity membership = member(chatId, requesterId);
        membership.setArchived(false);
        membership.setListPinned(false);

        ChatMemberEntity existingPinnedMembership = member(existingPinnedChatId, requesterId);
        existingPinnedMembership.setArchived(false);
        existingPinnedMembership.setListPinned(true);
        existingPinnedMembership.setListPinOrder(2);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));
        when(chatMemberRepository.findAllByIdUserIdAndArchivedOrderByListPinOrderAsc(requesterId, false))
                .thenReturn(List.of(existingPinnedMembership));
        when(chatMemberRepository.save(any(ChatMemberEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatDraftRepository.findById(any(ChatDraftId.class))).thenReturn(Optional.empty());
        when(chatMemberRepository.countByIdChatId(chatId)).thenReturn(1L);
        when(messageRepository.findRecentByChatId(chatId, 1)).thenReturn(List.of());

        ChatSummaryResponse response = chatService.pinChatToList(requesterId, chatId);

        assertThat(response.pinned()).isTrue();
        assertThat(response.pinOrder()).isEqualTo(3);
        assertThat(membership.getListPinned()).isTrue();
        assertThat(membership.getListPinOrder()).isEqualTo(3);
    }

    @Test
    void unpinChatFromListRenormalizesRemainingPinnedChats() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID anotherPinnedChatId = UUID.randomUUID();
        UUID thirdPinnedChatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setTitle("Target");
        chat.setCreatedAt(Instant.parse("2026-03-19T10:00:00Z"));
        chat.setLastMessageAt(Instant.parse("2026-03-19T10:10:00Z"));

        ChatMemberEntity membership = member(chatId, requesterId);
        membership.setArchived(false);
        membership.setListPinned(true);
        membership.setListPinOrder(1);

        ChatMemberEntity firstRemainingPinned = member(anotherPinnedChatId, requesterId);
        firstRemainingPinned.setArchived(false);
        firstRemainingPinned.setListPinned(true);
        firstRemainingPinned.setListPinOrder(2);

        ChatMemberEntity secondRemainingPinned = member(thirdPinnedChatId, requesterId);
        secondRemainingPinned.setArchived(false);
        secondRemainingPinned.setListPinned(true);
        secondRemainingPinned.setListPinOrder(5);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));
        when(chatMemberRepository.save(any(ChatMemberEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatMemberRepository.findAllByIdUserIdAndArchivedOrderByListPinOrderAsc(requesterId, false))
                .thenReturn(List.of(firstRemainingPinned, secondRemainingPinned));
        when(chatDraftRepository.findById(any(ChatDraftId.class))).thenReturn(Optional.empty());
        when(chatMemberRepository.countByIdChatId(chatId)).thenReturn(1L);
        when(messageRepository.findRecentByChatId(chatId, 1)).thenReturn(List.of());

        ChatSummaryResponse response = chatService.unpinChatFromList(requesterId, chatId);

        assertThat(response.pinned()).isFalse();
        assertThat(response.pinOrder()).isNull();
        assertThat(membership.getListPinned()).isFalse();
        assertThat(membership.getListPinOrder()).isNull();

        ArgumentCaptor<List<ChatMemberEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatMemberRepository).saveAll(captor.capture());
        List<ChatMemberEntity> normalizedMemberships = captor.getValue();
        assertThat(normalizedMemberships).containsExactly(firstRemainingPinned, secondRemainingPinned);
        assertThat(firstRemainingPinned.getListPinOrder()).isEqualTo(0);
        assertThat(secondRemainingPinned.getListPinOrder()).isEqualTo(1);
    }

    @Test
    void listChatsPageRejectsMalformedCursor() {
        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.listChatsPage(UUID.randomUUID(), false, "%%%not-base64%%%", 10),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updatePublicUsernameRefreshesPublicPostIndex() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("CHANNEL");
        chat.setTitle("News");

        ChatMemberEntity membership = member(chatId, requesterId);
        membership.setRole("OWNER");
        membership.setCanManageInviteLinks(true);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));
        when(chatRepository.save(any(ChatEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatDraftRepository.findById(any(ChatDraftId.class))).thenReturn(Optional.empty());
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(membership));

        chatService.updatePublicUsername(
                requesterId,
                chatId,
                new com.alex.messenger.chat.dto.UpdateChatPublicUsernameRequest("newsroom")
        );

        org.mockito.Mockito.verify(publicPostSearchService).refreshChatIndex(chatId);
        ArgumentCaptor<ChatInboxFanoutEvent> eventCaptor = ArgumentCaptor.forClass(ChatInboxFanoutEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("CHAT_UPDATED");
        assertThat(eventCaptor.getValue().userIds()).containsExactly(requesterId);
    }

    @Test
    void transferOwnershipPromotesTargetOwnerAndDemotesRequesterToAdmin() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID newOwnerUserId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");

        ChatMemberEntity owner = member(chatId, requesterId);
        owner.setRole("OWNER");
        owner.setCanManageMembers(true);
        owner.setCanManageInviteLinks(true);
        owner.setCanManageMessages(true);
        owner.setCanPinMessages(true);
        owner.setCanApproveJoinRequests(true);
        owner.setCanPostMessages(true);

        ChatMemberEntity target = member(chatId, newOwnerUserId);
        target.setRole("MEMBER");
        target.setCanManageMembers(false);
        target.setCanManageInviteLinks(false);
        target.setCanManageMessages(false);
        target.setCanPinMessages(false);
        target.setCanApproveJoinRequests(false);
        target.setCanPostMessages(true);
        target.setCanSendMessages(false);
        target.setRestrictedUntil(Instant.parse("2026-03-20T10:00:00Z"));

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(owner));
        when(chatMemberRepository.findById(new ChatMemberId(chatId, newOwnerUserId))).thenReturn(Optional.of(target));
        when(chatMemberRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(owner, target));

        TransferChatOwnershipResponse response = chatService.transferOwnership(requesterId, chatId, newOwnerUserId);

        assertThat(response.chatId()).isEqualTo(chatId);
        assertThat(response.previousOwnerUserId()).isEqualTo(requesterId);
        assertThat(response.newOwnerUserId()).isEqualTo(newOwnerUserId);
        assertThat(owner.getRole()).isEqualTo("ADMIN");
        assertThat(target.getRole()).isEqualTo("OWNER");
        assertThat(target.getCanManageMembers()).isTrue();
        assertThat(target.getCanManageInviteLinks()).isTrue();
        assertThat(target.getCanManageMessages()).isTrue();
        assertThat(target.getCanPinMessages()).isTrue();
        assertThat(target.getCanApproveJoinRequests()).isTrue();
        assertThat(target.getCanPostMessages()).isTrue();
        assertThat(target.getCanSendMessages()).isTrue();
        assertThat(target.getRestrictedUntil()).isNull();
        ArgumentCaptor<ChatInboxFanoutEvent> eventCaptor = ArgumentCaptor.forClass(ChatInboxFanoutEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("CHAT_OWNERSHIP_TRANSFERRED");
        assertThat(eventCaptor.getValue().userIds()).containsExactlyInAnyOrder(requesterId, newOwnerUserId);
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
    void pinMessageRejectsHiddenTopicMessage() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID hiddenTopicId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setForumEnabled(true);

        ChatMemberEntity membership = member(chatId, requesterId);
        membership.setRole("ADMIN");
        membership.setCanPinMessages(true);

        MessageLookupEntity message = new MessageLookupEntity();
        message.setMessageId(messageId);
        message.setChatId(chatId);
        message.setTopicId(hiddenTopicId);
        message.setDeletedAt(null);

        ForumTopicEntity hiddenTopic = new ForumTopicEntity();
        hiddenTopic.setId(hiddenTopicId);
        hiddenTopic.setChatId(chatId);
        hiddenTopic.setHidden(true);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(membership));
        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(forumTopicRepository.findByIdAndChatId(hiddenTopicId, chatId)).thenReturn(Optional.of(hiddenTopic));

        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.pinMessage(requesterId, chatId, messageId),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(chatRepository, never()).save(chat);
        verify(chatPinEventRepository, never()).save(any(ChatPinEventEntity.class));
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
    void approveJoinRequestPublishesApprovalEvent() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID pendingUserId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");

        ChatMemberEntity requesterMembership = member(chatId, requesterId);
        requesterMembership.setRole("ADMIN");
        requesterMembership.setCanApproveJoinRequests(true);

        ChatJoinRequestEntity joinRequest = new ChatJoinRequestEntity();
        joinRequest.setId(new ChatJoinRequestId(chatId, pendingUserId));
        joinRequest.setStatus("PENDING");

        UserEntity pendingUser = new UserEntity();
        pendingUser.setId(pendingUserId);
        pendingUser.setDisplayName("Pending");

        final ChatMemberEntity[] approvedMembership = new ChatMemberEntity[1];

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(requesterMembership));
        when(chatJoinRequestRepository.findByIdChatIdAndIdUserId(chatId, pendingUserId)).thenReturn(Optional.of(joinRequest));
        when(chatBanRepository.findById(new ChatBanId(chatId, pendingUserId))).thenReturn(Optional.empty());
        when(chatMemberRepository.findById(new ChatMemberId(chatId, pendingUserId))).thenReturn(Optional.empty());
        when(chatMemberRepository.save(any(ChatMemberEntity.class))).thenAnswer(invocation -> {
            ChatMemberEntity saved = invocation.getArgument(0);
            approvedMembership[0] = saved;
            return saved;
        });
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenAnswer(invocation ->
                approvedMembership[0] == null ? List.of(requesterMembership) : List.of(requesterMembership, approvedMembership[0]));
        when(userRepository.findById(pendingUserId)).thenReturn(Optional.of(pendingUser));

        chatService.approveJoinRequest(requesterId, chatId, pendingUserId);

        ArgumentCaptor<ChatInboxFanoutEvent> eventCaptor = ArgumentCaptor.forClass(ChatInboxFanoutEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("CHAT_JOIN_REQUEST_APPROVED");
        assertThat(eventCaptor.getValue().userIds()).containsExactlyInAnyOrder(requesterId, pendingUserId);
        verify(userSyncService).recordForUsers(
                any(),
                eq("CHAT_JOIN_REQUEST_APPROVED"),
                eq("CHAT"),
                eq(chatId),
                eq(chatId),
                any()
        );
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
    void addMembersRejectsRequestsThatOnlyContainRequester() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");

        ChatMemberEntity requesterMembership = member(chatId, requesterId);
        requesterMembership.setRole("ADMIN");
        requesterMembership.setCanManageMembers(true);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId)))
                .thenReturn(Optional.of(requesterMembership));

        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.addMembers(
                        requesterId,
                        chatId,
                        new AddMembersRequest(List.of(requesterId))
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).findAllById(any());
        verify(chatMemberRepository, never()).save(any(ChatMemberEntity.class));
    }

    @Test
    void addMembersPublishesMemberAddedEventForParticipants() {
        UUID requesterId = UUID.randomUUID();
        UUID addedUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setTitle("Ops");
        chat.setCreatedAt(Instant.parse("2026-03-25T10:00:00Z"));

        ChatMemberEntity requesterMembership = member(chatId, requesterId);
        requesterMembership.setRole("ADMIN");
        requesterMembership.setCanManageMembers(true);

        UserEntity requesterUser = new UserEntity();
        requesterUser.setId(requesterId);
        requesterUser.setDisplayName("Requester");

        UserEntity addedUser = new UserEntity();
        addedUser.setId(addedUserId);
        addedUser.setDisplayName("Added");

        final ChatMemberEntity[] addedMembership = new ChatMemberEntity[1];

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, addedUserId)).thenReturn(false);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(requesterMembership));
        when(chatMemberRepository.save(any(ChatMemberEntity.class))).thenAnswer(invocation -> {
            ChatMemberEntity saved = invocation.getArgument(0);
            addedMembership[0] = saved;
            return saved;
        });
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenAnswer(invocation ->
                addedMembership[0] == null ? List.of(requesterMembership) : List.of(requesterMembership, addedMembership[0]));
        when(userRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<UUID> ids = invocation.getArgument(0);
            java.util.List<UUID> resolvedIds = new java.util.ArrayList<>();
            ids.forEach(resolvedIds::add);
            java.util.List<UserEntity> users = new java.util.ArrayList<>();
            if (resolvedIds.contains(requesterId)) {
                users.add(requesterUser);
            }
            if (resolvedIds.contains(addedUserId)) {
                users.add(addedUser);
            }
            return users;
        });
        when(chatBanRepository.findById(new ChatBanId(chatId, addedUserId))).thenReturn(Optional.empty());

        chatService.addMembers(requesterId, chatId, new AddMembersRequest(List.of(addedUserId)));

        ArgumentCaptor<ChatInboxFanoutEvent> eventCaptor = ArgumentCaptor.forClass(ChatInboxFanoutEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("CHAT_MEMBER_ADDED");
        assertThat(eventCaptor.getValue().userIds()).containsExactlyInAnyOrder(requesterId, addedUserId);
        verify(userSyncService).recordForUsers(
                any(),
                eq("CHAT_MEMBER_ADDED"),
                eq("CHAT"),
                eq(chatId),
                eq(chatId),
                any()
        );
        verify(userSyncService).recordForUsers(
                any(),
                eq("CHAT_UPSERT"),
                eq("CHAT"),
                eq(chatId),
                eq(chatId),
                any()
        );
        verify(userSyncService).recordForUsers(
                any(),
                eq("MEMBER_STATE_CHANGED"),
                eq("CHAT"),
                eq(chatId),
                eq(chatId),
                any()
        );
    }

    @Test
    void discoverPublicChatsRejectsTooLongQuery() {
        UUID requesterId = UUID.randomUUID();

        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.discoverPublicChats(requesterId, "a".repeat(256), 20),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(chatRepository, never()).searchPublicChats(anyString());
    }

    @Test
    void removeMemberPublishesRemovalEventForRemainingAndRemovedUsers() {
        UUID requesterId = UUID.randomUUID();
        UUID removedUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");

        ChatMemberEntity requesterMembership = member(chatId, requesterId);
        requesterMembership.setRole("ADMIN");
        requesterMembership.setCanManageMembers(true);

        ChatMemberEntity targetMembership = member(chatId, removedUserId);

        final boolean[] removed = {false};

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(requesterMembership));
        when(chatMemberRepository.findById(new ChatMemberId(chatId, removedUserId))).thenReturn(Optional.of(targetMembership));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenAnswer(invocation ->
                removed[0] ? List.of(requesterMembership) : List.of(requesterMembership, targetMembership));
        org.mockito.Mockito.doAnswer(invocation -> {
            removed[0] = true;
            return null;
        }).when(chatMemberRepository).delete(targetMembership);

        chatService.removeMember(requesterId, chatId, removedUserId);

        ArgumentCaptor<ChatInboxFanoutEvent> eventCaptor = ArgumentCaptor.forClass(ChatInboxFanoutEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("CHAT_MEMBER_REMOVED");
        assertThat(eventCaptor.getValue().userIds()).containsExactly(requesterId);
        assertThat(eventCaptor.getValue().removedUserIds()).containsExactly(removedUserId);
        verify(userSyncService).recordForUsers(
                eq(List.of(removedUserId)),
                eq("CHAT_REMOVED"),
                eq("CHAT"),
                eq(chatId),
                eq(chatId),
                any()
        );
    }

    @Test
    void banMemberPublishesBanEventForRemainingAndRemovedUsers() {
        UUID requesterId = UUID.randomUUID();
        UUID bannedUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");

        ChatMemberEntity requesterMembership = member(chatId, requesterId);
        requesterMembership.setRole("ADMIN");
        requesterMembership.setCanManageMessages(true);

        ChatMemberEntity bannedMembership = member(chatId, bannedUserId);

        UserEntity bannedUser = new UserEntity();
        bannedUser.setId(bannedUserId);
        bannedUser.setDisplayName("Banned");

        final boolean[] removed = {false};

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)).thenReturn(true);
        when(chatMemberRepository.findById(new ChatMemberId(chatId, requesterId))).thenReturn(Optional.of(requesterMembership));
        when(chatMemberRepository.findById(new ChatMemberId(chatId, bannedUserId))).thenReturn(Optional.of(bannedMembership));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenAnswer(invocation ->
                removed[0] ? List.of(requesterMembership) : List.of(requesterMembership, bannedMembership));
        org.mockito.Mockito.doAnswer(invocation -> {
            removed[0] = true;
            return null;
        }).when(chatMemberRepository).delete(bannedMembership);
        when(chatBanRepository.findById(new ChatBanId(chatId, bannedUserId))).thenReturn(Optional.empty());
        when(chatBanRepository.save(any(ChatBanEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatJoinRequestRepository.findByIdChatIdAndIdUserId(chatId, bannedUserId)).thenReturn(Optional.empty());
        when(userRepository.existsById(bannedUserId)).thenReturn(true);
        when(userRepository.findById(bannedUserId)).thenReturn(Optional.of(bannedUser));

        chatService.banMember(
                requesterId,
                chatId,
                bannedUserId,
                new com.alex.messenger.chat.dto.UpdateChatBanRequest(null, "spam")
        );

        ArgumentCaptor<ChatInboxFanoutEvent> eventCaptor = ArgumentCaptor.forClass(ChatInboxFanoutEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("CHAT_MEMBER_BANNED");
        assertThat(eventCaptor.getValue().userIds()).containsExactly(requesterId);
        assertThat(eventCaptor.getValue().removedUserIds()).containsExactly(bannedUserId);
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

    private ChatSummaryResponse chatSummary(UUID chatId, boolean pinned, Integer pinOrder, Instant lastMessageAt) {
        return new ChatSummaryResponse(
                chatId,
                "GROUP",
                "Chat",
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
                lastMessageAt,
                1,
                null,
                0,
                0,
                0,
                false,
                null,
                null,
                null,
                pinned,
                pinOrder,
                null,
                false,
                true,
                true,
                true,
                null
        );
    }
}
