package com.alex.messenger.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.attachment.AttachmentEntity;
import com.alex.messenger.attachment.AttachmentRepository;
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
import com.alex.messenger.message.MessagePrimaryKey;
import com.alex.messenger.message.MessageTextContent;
import com.alex.messenger.chat.dto.ChatSummaryResponse;
import com.alex.messenger.chat.dto.ChatReadEventResponse;
import com.alex.messenger.chat.dto.TypingEventResponse;
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
    private AttachmentRepository attachmentRepository;

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
                attachmentRepository,
                chatEncryptionService,
                messageContentCodec,
                userRepository,
                blockedUserRepository,
                profilePhotoService,
                userPresenceService
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
    void listChatsPageRejectsMalformedCursor() {
        ResponseStatusException exception = catchThrowableOfType(
                () -> chatService.listChatsPage(UUID.randomUUID(), false, "%%%not-base64%%%", 10),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
