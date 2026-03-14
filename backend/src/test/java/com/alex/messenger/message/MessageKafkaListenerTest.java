package com.alex.messenger.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.attachment.AttachmentService;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.forum.ForumTopicService;
import com.alex.messenger.crypto.ChatEncryptionService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.notification.MessagePushNotificationService;
import com.alex.messenger.poll.PollService;
import com.alex.messenger.sticker.StickerService;
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
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MessageKafkaListenerTest {

    @Mock
    private ChatEncryptionService chatEncryptionService;

    @Mock
    private MessageContentCodec messageContentCodec;

    @Mock
    private MessageReactionService messageReactionService;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private ChatService chatService;

    @Mock
    private MessageDeliveryService messageDeliveryService;

    @Mock
    private MessagePushNotificationService messagePushNotificationService;

    @Mock
    private PollService pollService;

    @Mock
    private StickerService stickerService;

    @Mock
    private MessageLookupRepository messageLookupRepository;

    @Mock
    private MessageThreadRepository messageThreadRepository;

    @Mock
    private ForumTopicService forumTopicService;

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    private MessageKafkaListener messageKafkaListener;

    @BeforeEach
    void setUp() {
        messageKafkaListener = new MessageKafkaListener(
                chatEncryptionService,
                messageContentCodec,
                messageReactionService,
                attachmentService,
                chatService,
                messageDeliveryService,
                messagePushNotificationService,
                pollService,
                stickerService,
                messageLookupRepository,
                messageThreadRepository,
                forumTopicService,
                simpMessagingTemplate
        );
    }

    @Test
    void listenSkipsInaccessibleSenderAndExcludesDeletedCommentsFromCount() {
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID discussionRootMessageId = UUID.randomUUID();
        UUID activeReplyId = UUID.randomUUID();
        UUID deletedReplyId = UUID.randomUUID();

        MessageEvent event = new MessageEvent(
                chatId,
                messageId,
                null,
                senderId,
                List.of(recipientId),
                null,
                null,
                null,
                chatId,
                discussionRootMessageId,
                Instant.parse("2026-03-14T12:00:00Z"),
                "ciphertext",
                "nonce",
                1,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "DELIVERED",
                Instant.parse("2026-03-14T12:00:01Z"),
                null,
                null,
                null,
                null
        );

        MessageThreadEntity root = new MessageThreadEntity();
        root.setKey(new MessageThreadPrimaryKey(discussionRootMessageId, discussionRootMessageId));
        root.setDeletedAt(null);

        MessageThreadEntity activeReply = new MessageThreadEntity();
        activeReply.setKey(new MessageThreadPrimaryKey(discussionRootMessageId, activeReplyId));
        activeReply.setDeletedAt(null);

        MessageThreadEntity deletedReply = new MessageThreadEntity();
        deletedReply.setKey(new MessageThreadPrimaryKey(discussionRootMessageId, deletedReplyId));
        deletedReply.setDeletedAt(Instant.parse("2026-03-14T12:05:00Z"));

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);

        when(chatEncryptionService.decrypt(chatId, "ciphertext", "nonce", 1)).thenReturn("decoded");
        when(messageContentCodec.decode("decoded")).thenReturn(new MessageTextContent("hello", List.of()));
        when(messageReactionService.getSummaries(messageId)).thenReturn(List.of());
        when(attachmentService.getResponses(List.of())).thenReturn(List.of());
        when(attachmentService.getResponses(recipientId, List.of())).thenReturn(List.of());
        when(chatService.getOwnedChat(senderId, chatId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied"));
        when(chatService.getOwnedChat(recipientId, chatId)).thenReturn(chat);
        when(chatService.resolveMessageAuthor(recipientId, chatId, senderId))
                .thenReturn(new ChatService.MessageAuthorView(senderId, "Author", null, null, false));
        when(messageLookupRepository.findById(discussionRootMessageId))
                .thenReturn(Optional.of(messageLookup(discussionRootMessageId, chatId, null)));
        when(messageThreadRepository.findAllByThreadRootMessageId(discussionRootMessageId))
                .thenReturn(List.of(root, activeReply, deletedReply));

        messageKafkaListener.listen(event);

        ArgumentCaptor<ChatMessageResponse> responseCaptor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        verify(simpMessagingTemplate).convertAndSendToUser(eq(recipientId.toString()), eq("/queue/messages"), responseCaptor.capture());
        verify(simpMessagingTemplate, never()).convertAndSendToUser(eq(senderId.toString()), eq("/queue/messages"), any());
        assertThat(responseCaptor.getValue().commentCount()).isEqualTo(1);
    }

    @Test
    void listenMasksHiddenReferenceIdsForRecipient() {
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID replyToMessageId = UUID.randomUUID();
        UUID threadRootMessageId = UUID.randomUUID();
        UUID discussionChatId = UUID.randomUUID();
        UUID discussionRootMessageId = UUID.randomUUID();
        UUID forwardedFromChatId = UUID.randomUUID();
        UUID forwardedFromMessageId = UUID.randomUUID();
        UUID hiddenTopicId = UUID.randomUUID();

        MessageEvent event = new MessageEvent(
                chatId,
                messageId,
                null,
                senderId,
                List.of(recipientId),
                null,
                null,
                threadRootMessageId,
                discussionChatId,
                discussionRootMessageId,
                Instant.parse("2026-03-14T13:00:00Z"),
                "ciphertext",
                "nonce",
                1,
                replyToMessageId,
                forwardedFromChatId,
                forwardedFromMessageId,
                null,
                null,
                List.of(),
                "DELIVERED",
                Instant.parse("2026-03-14T13:00:01Z"),
                null,
                null,
                null,
                null
        );

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setForumEnabled(true);

        ChatEntity discussionChat = new ChatEntity();
        discussionChat.setId(discussionChatId);
        discussionChat.setForumEnabled(true);

        ChatEntity forwardedFromChat = new ChatEntity();
        forwardedFromChat.setId(forwardedFromChatId);
        forwardedFromChat.setForumEnabled(true);

        when(chatEncryptionService.decrypt(chatId, "ciphertext", "nonce", 1)).thenReturn("decoded");
        when(messageContentCodec.decode("decoded")).thenReturn(new MessageTextContent("hello", List.of()));
        when(messageReactionService.getSummaries(messageId)).thenReturn(List.of());
        when(attachmentService.getResponses(List.of())).thenReturn(List.of());
        when(attachmentService.getResponses(recipientId, List.of())).thenReturn(List.of());
        when(chatService.getOwnedChat(senderId, chatId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied"));
        when(chatService.getOwnedChat(recipientId, chatId)).thenReturn(chat);
        when(chatService.getOwnedChat(recipientId, discussionChatId)).thenReturn(discussionChat);
        when(chatService.getOwnedChat(recipientId, forwardedFromChatId)).thenReturn(forwardedFromChat);
        when(chatService.resolveMessageAuthor(recipientId, chatId, senderId))
                .thenReturn(new ChatService.MessageAuthorView(senderId, "Author", null, null, false));
        when(messageLookupRepository.findById(replyToMessageId)).thenReturn(Optional.of(messageLookup(replyToMessageId, chatId, hiddenTopicId)));
        when(messageLookupRepository.findById(threadRootMessageId))
                .thenReturn(Optional.of(messageLookup(threadRootMessageId, chatId, hiddenTopicId)));
        when(messageLookupRepository.findById(discussionRootMessageId))
                .thenReturn(Optional.of(messageLookup(discussionRootMessageId, discussionChatId, hiddenTopicId)));
        when(messageLookupRepository.findById(forwardedFromMessageId))
                .thenReturn(Optional.of(messageLookup(forwardedFromMessageId, forwardedFromChatId, hiddenTopicId)));
        when(forumTopicService.resolveTopicForRead(any(ChatEntity.class), eq(recipientId), eq(hiddenTopicId)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found"));

        messageKafkaListener.listen(event);

        ArgumentCaptor<ChatMessageResponse> responseCaptor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        verify(simpMessagingTemplate).convertAndSendToUser(eq(recipientId.toString()), eq("/queue/messages"), responseCaptor.capture());
        ChatMessageResponse response = responseCaptor.getValue();
        assertThat(response.replyToMessageId()).isNull();
        assertThat(response.threadRootMessageId()).isNull();
        assertThat(response.discussionChatId()).isEqualTo(discussionChatId);
        assertThat(response.discussionRootMessageId()).isNull();
        assertThat(response.commentCount()).isZero();
        assertThat(response.forwardedFromChatId()).isEqualTo(forwardedFromChatId);
        assertThat(response.forwardedFromMessageId()).isNull();
    }

    @Test
    void listenSkipsInaccessibleRecipient() {
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID removedRecipientId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        MessageEvent event = new MessageEvent(
                chatId,
                messageId,
                null,
                senderId,
                List.of(recipientId, removedRecipientId),
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-03-14T14:00:00Z"),
                "ciphertext",
                "nonce",
                1,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "DELIVERED",
                Instant.parse("2026-03-14T14:00:01Z"),
                null,
                null,
                null,
                null
        );

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);

        when(chatEncryptionService.decrypt(chatId, "ciphertext", "nonce", 1)).thenReturn("decoded");
        when(messageContentCodec.decode("decoded")).thenReturn(new MessageTextContent("hello", List.of()));
        when(messageReactionService.getSummaries(messageId)).thenReturn(List.of());
        when(attachmentService.getResponses(List.of())).thenReturn(List.of());
        when(attachmentService.getResponses(recipientId, List.of())).thenReturn(List.of());
        when(chatService.getOwnedChat(senderId, chatId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied"));
        when(chatService.getOwnedChat(recipientId, chatId)).thenReturn(chat);
        when(chatService.getOwnedChat(removedRecipientId, chatId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied"));
        when(chatService.resolveMessageAuthor(recipientId, chatId, senderId))
                .thenReturn(new ChatService.MessageAuthorView(senderId, "Author", null, null, false));

        messageKafkaListener.listen(event);

        verify(simpMessagingTemplate).convertAndSendToUser(eq(recipientId.toString()), eq("/queue/messages"), any(ChatMessageResponse.class));
        verify(simpMessagingTemplate, never()).convertAndSendToUser(eq(removedRecipientId.toString()), eq("/queue/messages"), any());
    }

    @Test
    void listenSkipsRecipientWithoutTopicAccess() {
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID hiddenTopicId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        MessageEvent event = new MessageEvent(
                chatId,
                messageId,
                null,
                senderId,
                List.of(recipientId),
                null,
                hiddenTopicId,
                null,
                null,
                null,
                Instant.parse("2026-03-14T15:00:00Z"),
                "ciphertext",
                "nonce",
                1,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "DELIVERED",
                Instant.parse("2026-03-14T15:00:01Z"),
                null,
                null,
                null,
                null
        );

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setForumEnabled(true);

        when(chatEncryptionService.decrypt(chatId, "ciphertext", "nonce", 1)).thenReturn("decoded");
        when(messageContentCodec.decode("decoded")).thenReturn(new MessageTextContent("hello", List.of()));
        when(attachmentService.getResponses(List.of())).thenReturn(List.of());
        when(chatService.getOwnedChat(senderId, chatId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied"));
        when(chatService.getOwnedChat(recipientId, chatId)).thenReturn(chat);
        when(forumTopicService.resolveTopicForRead(chat, recipientId, hiddenTopicId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found"));

        messageKafkaListener.listen(event);

        verify(simpMessagingTemplate, never()).convertAndSendToUser(eq(recipientId.toString()), eq("/queue/messages"), any());
    }

    private MessageLookupEntity messageLookup(UUID messageId, UUID chatId, UUID topicId) {
        MessageLookupEntity lookup = new MessageLookupEntity();
        lookup.setMessageId(messageId);
        lookup.setChatId(chatId);
        lookup.setTopicId(topicId);
        lookup.setDeletedAt(null);
        return lookup;
    }
}
