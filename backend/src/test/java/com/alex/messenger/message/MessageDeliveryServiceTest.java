package com.alex.messenger.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatMemberEntity;
import com.alex.messenger.chat.ChatMemberId;
import com.alex.messenger.chat.ChatMemberRepository;
import com.alex.messenger.chat.ChatRepository;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.message.dto.AcknowledgeMessageDeliveryRequest;
import com.alex.messenger.message.dto.AcknowledgeMessageDeliveryResponse;
import com.alex.messenger.sync.UserSyncService;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MessageDeliveryServiceTest {

    @Mock
    private MessageLookupRepository messageLookupRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageStorageService messageStorageService;

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatMemberRepository chatMemberRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private UserSyncService userSyncService;

    @Mock
    private MessageRealtimeService messageRealtimeService;

    private MessageDeliveryProperties messageDeliveryProperties;
    private MessageDeliveryService messageDeliveryService;

    @BeforeEach
    void setUp() {
        messageDeliveryProperties = new MessageDeliveryProperties();
        messageDeliveryProperties.getReconciliation().setEnabled(true);
        messageDeliveryProperties.getReconciliation().setChatBatchSize(10);
        messageDeliveryProperties.getReconciliation().setMessageBatchSize(50);
        messageDeliveryProperties.getReconciliation().setLookback(Duration.ofMinutes(10));
        messageDeliveryProperties.getReconciliation().setDeliveryGracePeriod(Duration.ofSeconds(5));
        messageDeliveryService = new MessageDeliveryService(
                messageLookupRepository,
                messageRepository,
                messageStorageService,
                chatRepository,
                chatMemberRepository,
                chatService,
                userSessionService,
                userSyncService,
                messageDeliveryProperties,
                messageRealtimeService
        );
    }

    @Test
    void acknowledgeDeliveryMarksExplicitMessagesDelivered() {
        UUID requesterId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatEntity chat = directChat(chatId);
        MessageLookupEntity lookup = lookup(chatId, messageId, senderId, requesterId, Instant.parse("2026-03-25T10:00:00Z"), "SENT");

        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(lookup));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(userSyncService.participantsIncludingActor(senderId, List.of(requesterId))).thenReturn(List.of(senderId, requesterId));

        AcknowledgeMessageDeliveryResponse response = messageDeliveryService.acknowledgeDelivery(
                requesterId,
                sessionId,
                new AcknowledgeMessageDeliveryRequest(List.of(messageId), null, null)
        );

        assertThat(response.deliveredMessageIds()).containsExactly(messageId);
        assertThat(lookup.getDeliveryStatus()).isEqualTo("DELIVERED");
        assertThat(lookup.getDeliveredAt()).isNotNull();
        verify(userSessionService).requireOwnedSession(sessionId, requesterId);
        verify(messageStorageService).save(lookup);
        verify(userSyncService).recordForUsers(
                eq(List.of(senderId, requesterId)),
                eq("MESSAGE_UPSERT"),
                eq("MESSAGE"),
                eq(messageId),
                eq(chatId),
                any(Map.class)
        );
        verify(messageRealtimeService).publishMessageUpsert(messageId, List.of(senderId, requesterId));
    }

    @Test
    void acknowledgeDeliveryRejectsBoundaryMessageFromAnotherChat() {
        UUID requesterId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID otherChatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        MessageLookupEntity target = lookup(
                otherChatId,
                messageId,
                UUID.randomUUID(),
                requesterId,
                Instant.parse("2026-03-25T10:00:00Z"),
                "SENT"
        );

        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(target));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(directChat(chatId));

        ResponseStatusException exception = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> messageDeliveryService.acknowledgeDelivery(
                        requesterId,
                        sessionId,
                        new AcknowledgeMessageDeliveryRequest(null, chatId, messageId)
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(messageRepository, never()).findAllByChatIdUpToMessageId(any(), any());
        verify(messageRepository, never()).findRecentByChatIdAtOrBeforeMessageId(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        verify(messageStorageService, never()).save(any());
    }

    @Test
    void acknowledgeDeliveryRejectsMissingBoundaryMessage() {
        UUID requesterId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.empty());
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(directChat(chatId));

        ResponseStatusException exception = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> messageDeliveryService.acknowledgeDelivery(
                        requesterId,
                        sessionId,
                        new AcknowledgeMessageDeliveryRequest(null, chatId, messageId)
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(messageRepository, never()).findAllByChatIdUpToMessageId(any(), any());
        verify(messageRepository, never()).findRecentByChatIdAtOrBeforeMessageId(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        verify(messageStorageService, never()).save(any());
    }

    @Test
    void acknowledgeDeliveryRejectsBoundaryModeForNonDirectChat() {
        UUID requesterId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatEntity groupChat = new ChatEntity();
        groupChat.setId(chatId);
        groupChat.setChatType("GROUP");
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(groupChat);

        ResponseStatusException exception = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> messageDeliveryService.acknowledgeDelivery(
                        requesterId,
                        sessionId,
                        new AcknowledgeMessageDeliveryRequest(null, chatId, messageId)
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(messageLookupRepository, never()).findById(any());
        verify(messageRepository, never()).findAllByChatIdUpToMessageId(any(), any());
        verify(messageRepository, never()).findRecentByChatIdAtOrBeforeMessageId(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        verify(messageStorageService, never()).save(any());
    }

    @Test
    void acknowledgeDeliveryPagesBoundaryAcknowledgementAcrossLargeDirectChatHistory() {
        UUID requesterId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID targetMessageId = UUID.randomUUID();
        UUID olderIncomingMessageId = UUID.randomUUID();

        ChatEntity chat = directChat(chatId);
        MessageLookupEntity target = lookup(
                chatId,
                targetMessageId,
                senderId,
                null,
                Instant.parse("2026-03-25T10:00:00Z"),
                "SENT"
        );
        List<MessageEntity> firstBatch = new java.util.ArrayList<>();
        for (int index = 0; index < 500; index++) {
            MessageEntity skippedMessage = message(
                    chatId,
                    index == 0 ? targetMessageId : UUID.randomUUID(),
                    null,
                    Instant.parse("2026-03-25T10:00:00Z").minusSeconds(index),
                    "SENT"
            );
            skippedMessage.setDeletedAt(Instant.parse("2026-03-25T11:00:00Z"));
            firstBatch.add(skippedMessage);
        }
        UUID firstBatchTailMessageId = firstBatch.get(firstBatch.size() - 1).getKey().getMessageId();
        MessageEntity olderIncomingMessage = message(
                chatId,
                olderIncomingMessageId,
                requesterId,
                Instant.parse("2026-03-25T09:00:00Z"),
                "SENT"
        );
        MessageLookupEntity olderIncomingLookup = lookup(
                chatId,
                olderIncomingMessageId,
                senderId,
                requesterId,
                olderIncomingMessage.getCreatedAt(),
                "SENT"
        );

        when(messageLookupRepository.findById(targetMessageId)).thenReturn(Optional.of(target));
        when(messageLookupRepository.findById(olderIncomingMessageId)).thenReturn(Optional.of(olderIncomingLookup));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(messageRepository.findRecentByChatIdAtOrBeforeMessageId(chatId, targetMessageId, 500))
                .thenReturn(firstBatch);
        when(messageRepository.findRecentByChatIdBeforeMessageId(chatId, firstBatchTailMessageId, 500))
                .thenReturn(List.of(olderIncomingMessage));
        when(userSyncService.participantsIncludingActor(senderId, List.of(requesterId))).thenReturn(List.of(senderId, requesterId));

        AcknowledgeMessageDeliveryResponse response = messageDeliveryService.acknowledgeDelivery(
                requesterId,
                sessionId,
                new AcknowledgeMessageDeliveryRequest(null, chatId, targetMessageId)
        );

        assertThat(response.deliveredMessageIds()).containsExactly(olderIncomingMessageId);
        assertThat(olderIncomingLookup.getDeliveryStatus()).isEqualTo("DELIVERED");
        assertThat(olderIncomingLookup.getDeliveredAt()).isNotNull();
        verify(userSessionService).requireOwnedSession(sessionId, requesterId);
        verify(messageRepository).findRecentByChatIdAtOrBeforeMessageId(chatId, targetMessageId, 500);
        verify(messageRepository).findRecentByChatIdBeforeMessageId(chatId, firstBatchTailMessageId, 500);
        verify(messageStorageService).save(olderIncomingLookup);
        verify(messageRealtimeService).publishMessageUpsert(olderIncomingMessageId, List.of(senderId, requesterId));
    }

    @Test
    void markReadUpToPersistsSyncUpsertForReadMessages() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatEntity chat = directChat(chatId);
        MessageLookupEntity lookup = lookup(chatId, messageId, senderId, requesterId, Instant.parse("2026-03-25T10:00:00Z"), "SENT");
        MessageEntity message = message(chatId, messageId, requesterId, lookup.getCreatedAt(), "SENT");

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(lookup));
        when(messageRepository.findRecentByChatIdAtOrBeforeMessageId(chatId, messageId, 500)).thenReturn(List.of(message));
        when(userSyncService.participantsIncludingActor(senderId, List.of(requesterId))).thenReturn(List.of(senderId, requesterId));

        messageDeliveryService.markReadUpTo(requesterId, chatId, messageId);

        assertThat(lookup.getDeliveryStatus()).isEqualTo("READ");
        assertThat(lookup.getDeliveredAt()).isNotNull();
        assertThat(lookup.getReadAt()).isNotNull();
        verify(messageStorageService).save(lookup);
        verify(userSyncService).recordForUsers(
                eq(List.of(senderId, requesterId)),
                eq("MESSAGE_UPSERT"),
                eq("MESSAGE"),
                eq(messageId),
                eq(chatId),
                any(Map.class)
        );
        verify(messageRealtimeService).publishMessageUpsert(messageId, List.of(senderId, requesterId));
    }

    @Test
    void markReadUpToPagesAcrossLargeDirectChatHistory() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID targetMessageId = UUID.randomUUID();
        UUID olderIncomingMessageId = UUID.randomUUID();

        ChatEntity chat = directChat(chatId);
        MessageLookupEntity target = lookup(
                chatId,
                targetMessageId,
                requesterId,
                senderId,
                Instant.parse("2026-03-25T10:00:00Z"),
                "SENT"
        );
        List<MessageEntity> firstBatch = new java.util.ArrayList<>();
        for (int index = 0; index < 500; index++) {
            MessageEntity skippedMessage = message(
                    chatId,
                    index == 0 ? targetMessageId : UUID.randomUUID(),
                    null,
                    Instant.parse("2026-03-25T10:00:00Z").minusSeconds(index),
                    "SENT"
            );
            firstBatch.add(skippedMessage);
        }
        UUID firstBatchTailMessageId = firstBatch.get(firstBatch.size() - 1).getKey().getMessageId();
        MessageEntity olderIncomingMessage = message(
                chatId,
                olderIncomingMessageId,
                requesterId,
                Instant.parse("2026-03-25T09:00:00Z"),
                "SENT"
        );
        MessageLookupEntity olderIncomingLookup = lookup(
                chatId,
                olderIncomingMessageId,
                senderId,
                requesterId,
                olderIncomingMessage.getCreatedAt(),
                "SENT"
        );

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(messageLookupRepository.findById(targetMessageId)).thenReturn(Optional.of(target));
        when(messageRepository.findRecentByChatIdAtOrBeforeMessageId(chatId, targetMessageId, 500))
                .thenReturn(firstBatch);
        when(messageRepository.findRecentByChatIdBeforeMessageId(chatId, firstBatchTailMessageId, 500))
                .thenReturn(List.of(olderIncomingMessage));
        when(messageLookupRepository.findById(olderIncomingMessageId)).thenReturn(Optional.of(olderIncomingLookup));
        when(userSyncService.participantsIncludingActor(senderId, List.of(requesterId))).thenReturn(List.of(senderId, requesterId));

        messageDeliveryService.markReadUpTo(requesterId, chatId, targetMessageId);

        assertThat(olderIncomingLookup.getDeliveryStatus()).isEqualTo("READ");
        assertThat(olderIncomingLookup.getDeliveredAt()).isNotNull();
        assertThat(olderIncomingLookup.getReadAt()).isNotNull();
        verify(messageRepository).findRecentByChatIdAtOrBeforeMessageId(chatId, targetMessageId, 500);
        verify(messageRepository).findRecentByChatIdBeforeMessageId(chatId, firstBatchTailMessageId, 500);
        verify(messageStorageService).save(olderIncomingLookup);
        verify(messageRealtimeService).publishMessageUpsert(olderIncomingMessageId, List.of(senderId, requesterId));
    }

    @Test
    void reconcileChatDeliveryStateMarksMessagesReadFromMembershipBoundary() {
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID lastReadMessageId = UUID.randomUUID();
        Instant readAt = Instant.parse("2026-03-25T10:05:00Z");

        ChatEntity chat = directChat(chatId);
        ChatMemberEntity senderMembership = membership(chatId, senderId, null, null);
        ChatMemberEntity recipientMembership = membership(chatId, recipientId, lastReadMessageId, readAt);
        MessageLookupEntity lastReadLookup = lookup(
                chatId,
                lastReadMessageId,
                senderId,
                recipientId,
                Instant.parse("2026-03-25T10:04:00Z"),
                "READ"
        );
        MessageLookupEntity pendingLookup = lookup(
                chatId,
                messageId,
                senderId,
                recipientId,
                Instant.parse("2026-03-25T10:03:00Z"),
                "SENT"
        );
        MessageEntity recentMessage = message(chatId, messageId, recipientId, pendingLookup.getCreatedAt(), "SENT");

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(senderMembership, recipientMembership));
        when(messageRepository.findRecentByChatId(chatId, 50)).thenReturn(List.of(recentMessage));
        when(messageLookupRepository.findById(lastReadMessageId)).thenReturn(Optional.of(lastReadLookup));
        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(pendingLookup));
        when(userSyncService.participantsIncludingActor(senderId, List.of(recipientId))).thenReturn(List.of(senderId, recipientId));

        int updated = messageDeliveryService.reconcileChatDeliveryState(
                chatId,
                Instant.parse("2026-03-25T09:55:00Z"),
                50,
                Duration.ofSeconds(5)
        );

        assertThat(updated).isEqualTo(1);
        assertThat(pendingLookup.getDeliveryStatus()).isEqualTo("READ");
        assertThat(pendingLookup.getDeliveredAt()).isEqualTo(readAt);
        assertThat(pendingLookup.getReadAt()).isEqualTo(readAt);
        verify(messageStorageService).save(pendingLookup);
        verify(userSyncService).recordForUsers(
                eq(List.of(senderId, recipientId)),
                eq("MESSAGE_UPSERT"),
                eq("MESSAGE"),
                eq(messageId),
                eq(chatId),
                any(Map.class)
        );
        verify(messageRealtimeService).publishMessageUpsert(messageId, List.of(senderId, recipientId));
    }

    @Test
    void reconcileChatDeliveryStateDoesNotPromoteDeliveredStatusFromOnlinePresenceAlone() {
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatEntity chat = directChat(chatId);
        ChatMemberEntity senderMembership = membership(chatId, senderId, null, null);
        ChatMemberEntity recipientMembership = membership(chatId, recipientId, null, null);
        MessageLookupEntity pendingLookup = lookup(
                chatId,
                messageId,
                senderId,
                recipientId,
                Instant.now().minusSeconds(30),
                "SENT"
        );
        MessageEntity recentMessage = message(chatId, messageId, recipientId, pendingLookup.getCreatedAt(), "SENT");

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(senderMembership, recipientMembership));
        when(messageRepository.findRecentByChatId(chatId, 50)).thenReturn(List.of(recentMessage));
        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(pendingLookup));

        int updated = messageDeliveryService.reconcileChatDeliveryState(
                chatId,
                Instant.now().minus(Duration.ofMinutes(5)),
                50,
                Duration.ofSeconds(5)
        );

        assertThat(updated).isZero();
        assertThat(pendingLookup.getDeliveryStatus()).isEqualTo("SENT");
        assertThat(pendingLookup.getDeliveredAt()).isNull();
        assertThat(pendingLookup.getReadAt()).isNull();
    }

    @Test
    void reconcileChatDeliveryStatePagesAcrossStorageBatchesWhenLimitExceedsPageSize() {
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID lastReadMessageId = UUID.randomUUID();
        UUID olderMessageId = UUID.randomUUID();
        Instant readAt = Instant.parse("2026-03-25T10:05:00Z");

        ChatEntity chat = directChat(chatId);
        ChatMemberEntity senderMembership = membership(chatId, senderId, null, null);
        ChatMemberEntity recipientMembership = membership(chatId, recipientId, lastReadMessageId, readAt);
        MessageLookupEntity lastReadLookup = lookup(
                chatId,
                lastReadMessageId,
                senderId,
                recipientId,
                Instant.parse("2026-03-25T10:05:00Z"),
                "READ"
        );
        lastReadLookup.setDeliveredAt(readAt);
        lastReadLookup.setReadAt(readAt);
        MessageLookupEntity olderLookup = lookup(
                chatId,
                olderMessageId,
                senderId,
                recipientId,
                Instant.parse("2026-03-25T10:03:00Z"),
                "SENT"
        );
        List<MessageEntity> firstBatch = new java.util.ArrayList<>();
        for (int index = 0; index < 500; index++) {
            MessageEntity skippedMessage = message(
                    chatId,
                    UUID.randomUUID(),
                    null,
                    Instant.parse("2026-03-25T10:04:00Z").minusSeconds(index),
                    "SENT"
            );
            firstBatch.add(skippedMessage);
        }
        MessageEntity olderMessage = message(chatId, olderMessageId, recipientId, olderLookup.getCreatedAt(), "SENT");
        UUID firstBatchTailMessageId = firstBatch.get(firstBatch.size() - 1).getKey().getMessageId();

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(senderMembership, recipientMembership));
        when(messageRepository.findRecentByChatId(chatId, 500)).thenReturn(firstBatch);
        when(messageRepository.findRecentByChatIdBeforeMessageId(chatId, firstBatchTailMessageId, 1))
                .thenReturn(List.of(olderMessage));
        when(messageLookupRepository.findById(lastReadMessageId)).thenReturn(Optional.of(lastReadLookup));
        when(messageLookupRepository.findById(olderMessageId)).thenReturn(Optional.of(olderLookup));
        when(userSyncService.participantsIncludingActor(senderId, List.of(recipientId))).thenReturn(List.of(senderId, recipientId));

        int updated = messageDeliveryService.reconcileChatDeliveryState(
                chatId,
                Instant.parse("2026-03-25T09:55:00Z"),
                501,
                Duration.ofSeconds(5)
        );

        assertThat(updated).isEqualTo(1);
        assertThat(olderLookup.getDeliveryStatus()).isEqualTo("READ");
        assertThat(olderLookup.getDeliveredAt()).isEqualTo(readAt);
        assertThat(olderLookup.getReadAt()).isEqualTo(readAt);
        verify(messageRepository).findRecentByChatId(chatId, 500);
        verify(messageRepository).findRecentByChatIdBeforeMessageId(chatId, firstBatchTailMessageId, 1);
    }

    @Test
    void reconcileChatDeliveryStateSkipsTooFreshMessagesInsideGracePeriod() {
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant recentCreatedAt = now.minusSeconds(1);

        ChatEntity chat = directChat(chatId);
        ChatMemberEntity senderMembership = membership(chatId, senderId, null, null);
        ChatMemberEntity recipientMembership = membership(chatId, recipientId, messageId, recentCreatedAt);
        MessageLookupEntity boundaryLookup = lookup(
                chatId,
                messageId,
                senderId,
                recipientId,
                recentCreatedAt,
                "SENT"
        );
        MessageEntity recentMessage = message(chatId, messageId, recipientId, recentCreatedAt, "SENT");

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(senderMembership, recipientMembership));
        when(messageRepository.findRecentByChatId(chatId, 50)).thenReturn(List.of(recentMessage));
        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(boundaryLookup));

        int updated = messageDeliveryService.reconcileChatDeliveryState(
                chatId,
                now.minus(Duration.ofMinutes(5)),
                50,
                Duration.ofSeconds(5)
        );

        assertThat(updated).isZero();
        assertThat(boundaryLookup.getDeliveryStatus()).isEqualTo("SENT");
        assertThat(boundaryLookup.getDeliveredAt()).isNull();
        assertThat(boundaryLookup.getReadAt()).isNull();
        verify(messageStorageService, never()).save(any());
    }

    @Test
    void reconcileChatDeliveryStateDoesNotUseLastReadAtAsBoundaryWhenLookupIsMissing() {
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID lastReadMessageId = Uuids.timeBased();
        Instant lastReadMessageCreatedAt = Instant.ofEpochMilli(Uuids.unixTimestamp(lastReadMessageId));
        Instant readAt = lastReadMessageCreatedAt.plusSeconds(30);
        UUID laterMessageId = UUID.randomUUID();
        Instant laterMessageCreatedAt = lastReadMessageCreatedAt.plusSeconds(10);

        ChatEntity chat = directChat(chatId);
        ChatMemberEntity senderMembership = membership(chatId, senderId, null, null);
        ChatMemberEntity recipientMembership = membership(chatId, recipientId, lastReadMessageId, readAt);
        MessageLookupEntity laterLookup = lookup(
                chatId,
                laterMessageId,
                senderId,
                recipientId,
                laterMessageCreatedAt,
                "SENT"
        );
        MessageEntity laterMessage = message(chatId, laterMessageId, recipientId, laterMessageCreatedAt, "SENT");

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(senderMembership, recipientMembership));
        when(messageRepository.findRecentByChatId(chatId, 50)).thenReturn(List.of(laterMessage));
        when(messageLookupRepository.findById(lastReadMessageId)).thenReturn(Optional.empty());
        when(messageLookupRepository.findById(laterMessageId)).thenReturn(Optional.of(laterLookup));

        int updated = messageDeliveryService.reconcileChatDeliveryState(
                chatId,
                lastReadMessageCreatedAt.minusSeconds(60),
                50,
                Duration.ZERO
        );

        assertThat(updated).isZero();
        assertThat(laterLookup.getDeliveryStatus()).isEqualTo("SENT");
        assertThat(laterLookup.getDeliveredAt()).isNull();
        assertThat(laterLookup.getReadAt()).isNull();
        verify(messageStorageService, never()).save(any());
    }

    @Test
    void reconcileChatDeliveryStateHonorsTotalMessageLimitPerChat() {
        UUID chatId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID newestMessageId = UUID.randomUUID();
        UUID middleMessageId = UUID.randomUUID();
        UUID olderMessageId = UUID.randomUUID();
        Instant readAt = Instant.parse("2026-03-25T10:05:00Z");

        ChatEntity chat = directChat(chatId);
        ChatMemberEntity senderMembership = membership(chatId, senderId, null, null);
        ChatMemberEntity recipientMembership = membership(chatId, recipientId, newestMessageId, readAt);

        MessageLookupEntity boundaryLookup = lookup(
                chatId,
                newestMessageId,
                senderId,
                recipientId,
                Instant.parse("2026-03-25T10:05:00Z"),
                "READ"
        );
        boundaryLookup.setDeliveredAt(readAt);
        boundaryLookup.setReadAt(readAt);
        MessageLookupEntity middleLookup = lookup(
                chatId,
                middleMessageId,
                senderId,
                recipientId,
                Instant.parse("2026-03-25T10:04:00Z"),
                "SENT"
        );
        MessageEntity newestMessage = message(chatId, newestMessageId, recipientId, boundaryLookup.getCreatedAt(), "READ");
        newestMessage.setDeliveredAt(readAt);
        MessageEntity middleMessage = message(chatId, middleMessageId, recipientId, middleLookup.getCreatedAt(), "SENT");

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMemberRepository.findAllByIdChatId(chatId)).thenReturn(List.of(senderMembership, recipientMembership));
        when(messageRepository.findRecentByChatId(chatId, 2)).thenReturn(List.of(newestMessage, middleMessage));
        when(messageLookupRepository.findById(newestMessageId)).thenReturn(Optional.of(boundaryLookup));
        when(messageLookupRepository.findById(middleMessageId)).thenReturn(Optional.of(middleLookup));
        when(userSyncService.participantsIncludingActor(senderId, List.of(recipientId))).thenReturn(List.of(senderId, recipientId));

        int updated = messageDeliveryService.reconcileChatDeliveryState(
                chatId,
                Instant.parse("2026-03-25T09:55:00Z"),
                2,
                Duration.ofSeconds(5)
        );

        assertThat(updated).isEqualTo(1);
        assertThat(middleLookup.getDeliveryStatus()).isEqualTo("READ");
        assertThat(middleLookup.getDeliveredAt()).isEqualTo(readAt);
        assertThat(middleLookup.getReadAt()).isEqualTo(readAt);
        verify(messageRepository).findRecentByChatId(chatId, 2);
        verify(messageRepository, never()).findRecentByChatIdBeforeMessageId(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        verify(messageLookupRepository, never()).findById(olderMessageId);
    }

    private ChatEntity directChat(UUID chatId) {
        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("DIRECT");
        return chat;
    }

    private ChatMemberEntity membership(UUID chatId, UUID userId, UUID lastReadMessageId, Instant lastReadAt) {
        ChatMemberEntity membership = new ChatMemberEntity();
        membership.setId(new ChatMemberId(chatId, userId));
        membership.setLastReadMessageId(lastReadMessageId);
        membership.setLastReadAt(lastReadAt);
        return membership;
    }

    private MessageLookupEntity lookup(
            UUID chatId,
            UUID messageId,
            UUID senderId,
            UUID recipientId,
            Instant createdAt,
            String deliveryStatus
    ) {
        MessageLookupEntity lookup = new MessageLookupEntity();
        lookup.setChatId(chatId);
        lookup.setMessageId(messageId);
        lookup.setSenderId(senderId);
        lookup.setRecipientId(recipientId);
        lookup.setCreatedAt(createdAt);
        lookup.setDeliveryStatus(deliveryStatus);
        return lookup;
    }

    private MessageEntity message(
            UUID chatId,
            UUID messageId,
            UUID recipientId,
            Instant createdAt,
            String deliveryStatus
    ) {
        MessageEntity message = new MessageEntity();
        message.setKey(new MessagePrimaryKey(chatId, messageId));
        message.setRecipientId(recipientId);
        message.setCreatedAt(createdAt);
        message.setDeliveryStatus(deliveryStatus);
        return message;
    }
}
