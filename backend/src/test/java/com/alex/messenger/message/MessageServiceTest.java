package com.alex.messenger.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.abuse.AbuseProtectionService;
import com.alex.messenger.attachment.AttachmentService;
import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.bot.BotService;
import com.alex.messenger.bot.BotUpdateService;
import com.alex.messenger.chat.ChatAdminLogService;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.forum.ForumTopicEntity;
import com.alex.messenger.chat.forum.ForumTopicService;
import com.alex.messenger.crypto.ChatEncryptionService;
import com.alex.messenger.crypto.EncryptedPayload;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.CreateRepeatingMessageRequest;
import com.alex.messenger.message.dto.DeleteMessageRequest;
import com.alex.messenger.message.dto.EditMessageRequest;
import com.alex.messenger.message.dto.ForwardMessageRequest;
import com.alex.messenger.message.dto.MessageAttachmentResponse;
import com.alex.messenger.message.dto.MessageLiveLocationPayload;
import com.alex.messenger.message.dto.MessageTextEntityPayload;
import com.alex.messenger.message.dto.RepeatingMessageResponse;
import com.alex.messenger.message.dto.SendMessageRequest;
import com.alex.messenger.message.dto.ScheduledMessageResponse;
import com.alex.messenger.message.dto.SearchMessagesResponse;
import com.alex.messenger.message.dto.UpdateLiveLocationRequest;
import com.alex.messenger.message.expiration.MessageExpirationRepository;
import com.alex.messenger.message.idempotency.MessageIdempotencyService;
import com.alex.messenger.message.repeating.RepeatingMessageRuleEntity;
import com.alex.messenger.message.repeating.RepeatingMessageRuleRepository;
import com.alex.messenger.message.scheduled.ScheduledMessageEntity;
import com.alex.messenger.message.scheduled.ScheduledMessageRepository;
import com.alex.messenger.poll.PollService;
import com.alex.messenger.search.PublicPostSearchService;
import com.alex.messenger.sticker.StickerService;
import com.alex.messenger.sync.UserSyncService;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageTopicRepository messageTopicRepository;

    @Mock
    private MessageThreadRepository messageThreadRepository;

    @Mock
    private MessageLookupRepository messageLookupRepository;

    @Mock
    private MessageReportRepository messageReportRepository;

    @Mock
    private MessageReactionService messageReactionService;

    @Mock
    private MessageExpirationRepository messageExpirationRepository;

    @Mock
    private ScheduledMessageRepository scheduledMessageRepository;

    @Mock
    private RepeatingMessageRuleRepository repeatingMessageRuleRepository;

    @Mock
    private MessageStorageService messageStorageService;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private ChatAdminLogService chatAdminLogService;

    @Mock
    private ChatService chatService;

    @Mock
    private ForumTopicService forumTopicService;

    @Mock
    private MessageLiveLocationService messageLiveLocationService;

    @Mock
    private ChatEncryptionService chatEncryptionService;

    @Mock
    private MessageContentCodec messageContentCodec;

    @Mock
    private MessageLinkPreviewService messageLinkPreviewService;

    @Mock
    private MessageSearchCorpusService messageSearchCorpusService;

    @Mock
    private MessageTranslationCacheRepository messageTranslationCacheRepository;

    @Mock
    private MessageIdempotencyService messageIdempotencyService;

    @Mock
    private PollService pollService;

    @Mock
    private StickerService stickerService;

    @Mock
    private PublicPostSearchService publicPostSearchService;

    @Mock
    private ChatMessagePublisher chatMessagePublisher;

    @Mock
    private BotService botService;

    @Mock
    private BotUpdateService botUpdateService;

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private UserSyncService userSyncService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private AbuseProtectionService abuseProtectionService;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(
                messageRepository,
                messageTopicRepository,
                messageThreadRepository,
                messageLookupRepository,
                messageReportRepository,
                messageReactionService,
                messageExpirationRepository,
                scheduledMessageRepository,
                repeatingMessageRuleRepository,
                messageStorageService,
                attachmentService,
                chatAdminLogService,
                chatService,
                forumTopicService,
                messageLiveLocationService,
                chatEncryptionService,
                messageContentCodec,
                messageLinkPreviewService,
                messageSearchCorpusService,
                messageTranslationCacheRepository,
                messageIdempotencyService,
                pollService,
                stickerService,
                publicPostSearchService,
                chatMessagePublisher,
                botService,
                botUpdateService,
                userSessionService,
                userSyncService,
                applicationEventPublisher,
                abuseProtectionService
        );

        lenient().when(chatEncryptionService.decrypt(any(UUID.class), anyString(), anyString(), anyInt())).thenReturn("decoded");
        lenient().when(messageContentCodec.decode("decoded")).thenReturn(new MessageTextContent("Hello", List.of()));
        lenient().when(messageReactionService.getSummaries(any(UUID.class))).thenReturn(List.of());
        lenient().when(messageReactionService.getSummaries(anyCollection())).thenReturn(Map.of());
        lenient().when(userSyncService.participantsIncludingActor(any(UUID.class), anyCollection()))
                .thenAnswer(invocation -> {
                    java.util.LinkedHashSet<UUID> participants = new java.util.LinkedHashSet<>();
                    UUID actorId = invocation.getArgument(0, UUID.class);
                    if (actorId != null) {
                        participants.add(actorId);
                    }
                    java.util.Collection<UUID> others = invocation.getArgument(1);
                    if (others != null) {
                        participants.addAll(others);
                    }
                    return participants;
                });
        lenient().when(chatService.resolveMessageAuthor(any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenAnswer(invocation -> new ChatService.MessageAuthorView(
                        invocation.getArgument(2, UUID.class),
                        "Author",
                        null,
                        null,
                        false
                ));
    }

    @Test
    void scheduleRepeatingMessageCreatesRuleAndFirstScheduledOccurrence() {
        UUID senderId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        UUID scheduledMessageId = UUID.randomUUID();
        Instant firstScheduledAt = Instant.parse("2026-03-20T10:00:00Z");

        ChatEntity chat = chat(chatId, "DIRECT");

        when(chatService.getOwnedChat(senderId, chatId)).thenReturn(chat);
        when(forumTopicService.resolveTopicForWrite(chat, senderId, null)).thenReturn(null);
        when(messageContentCodec.normalize(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new MessageTextContent("Reminder", List.of()));
        when(messageContentCodec.encode(any(MessageTextContent.class))).thenReturn("encoded-repeat");
        when(chatEncryptionService.encrypt(chatId, "encoded-repeat"))
                .thenReturn(new EncryptedPayload("cipher-repeat", "nonce-repeat", 1));
        when(repeatingMessageRuleRepository.save(any(RepeatingMessageRuleEntity.class))).thenAnswer(invocation -> {
            RepeatingMessageRuleEntity rule = invocation.getArgument(0);
            if (rule.getId() == null) {
                rule.setId(ruleId);
            }
            if (rule.getCreatedAt() == null) {
                rule.setCreatedAt(Instant.parse("2026-03-19T12:00:00Z"));
            }
            if (rule.getUpdatedAt() == null) {
                rule.setUpdatedAt(rule.getCreatedAt());
            }
            return rule;
        });
        when(scheduledMessageRepository.save(any(ScheduledMessageEntity.class))).thenAnswer(invocation -> {
            ScheduledMessageEntity scheduledMessage = invocation.getArgument(0);
            if (scheduledMessage.getId() == null) {
                scheduledMessage.setId(scheduledMessageId);
            }
            if (scheduledMessage.getCreatedAt() == null) {
                scheduledMessage.setCreatedAt(Instant.parse("2026-03-19T12:00:00Z"));
            }
            return scheduledMessage;
        });

        RepeatingMessageResponse response = messageService.scheduleRepeatingMessage(
                senderId,
                new CreateRepeatingMessageRequest(
                        chatId,
                        null,
                        null,
                        null,
                        "Reminder",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        false,
                        UUID.randomUUID(),
                        firstScheduledAt,
                        60,
                        3
                )
        );

        assertThat(response.ruleId()).isEqualTo(ruleId);
        assertThat(response.latestScheduledMessageId()).isEqualTo(scheduledMessageId);
        assertThat(response.emittedOccurrences()).isEqualTo(1);
        assertThat(response.lastScheduledAt()).isEqualTo(firstScheduledAt);
        assertThat(response.nextScheduledAt()).isEqualTo(firstScheduledAt.plusSeconds(3600));
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void cancelScheduledMessageCancelsRepeatingRule() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        UUID scheduledMessageId = UUID.randomUUID();

        ScheduledMessageEntity scheduledMessage = new ScheduledMessageEntity();
        scheduledMessage.setId(scheduledMessageId);
        scheduledMessage.setChatId(chatId);
        scheduledMessage.setSenderId(requesterId);
        scheduledMessage.setStatus("PENDING");
        scheduledMessage.setRepeatingRuleId(ruleId);

        RepeatingMessageRuleEntity rule = new RepeatingMessageRuleEntity();
        rule.setId(ruleId);
        rule.setChatId(chatId);
        rule.setSenderId(requesterId);
        rule.setStatus("ACTIVE");
        rule.setNextScheduledAt(Instant.parse("2026-03-20T11:00:00Z"));

        when(scheduledMessageRepository.findByIdAndSenderId(scheduledMessageId, requesterId))
                .thenReturn(Optional.of(scheduledMessage));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "DIRECT"));
        when(scheduledMessageRepository.save(any(ScheduledMessageEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(repeatingMessageRuleRepository.findByIdAndSenderId(ruleId, requesterId))
                .thenReturn(Optional.of(rule));
        when(repeatingMessageRuleRepository.save(any(RepeatingMessageRuleEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        messageService.cancelScheduledMessage(requesterId, scheduledMessageId);

        assertThat(scheduledMessage.getStatus()).isEqualTo("CANCELED");
        assertThat(rule.getStatus()).isEqualTo("CANCELED");
        assertThat(rule.getNextScheduledAt()).isNull();
    }

    @Test
    void getHistoryReturnsChronologicalMessagesWithRequesterScopedAttachments() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID olderMessageId = UUID.randomUUID();
        UUID newerMessageId = UUID.randomUUID();
        UUID olderAttachmentId = UUID.randomUUID();
        UUID newerAttachmentId = UUID.randomUUID();

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "GROUP"));
        when(forumTopicService.resolveTopicForRead(any(ChatEntity.class), any(UUID.class), any())).thenReturn(null);
        when(messageRepository.findRecentByChatId(chatId, 100)).thenReturn(new ArrayList<>(List.of(
                message(chatId, newerMessageId, newerAttachmentId, Instant.parse("2026-03-14T10:05:00Z")),
                message(chatId, olderMessageId, olderAttachmentId, Instant.parse("2026-03-14T10:00:00Z"))
        )));
        when(attachmentService.getResponses(requesterId, List.of(olderAttachmentId)))
                .thenReturn(List.of(attachment(olderAttachmentId, "PHOTO")));
        when(attachmentService.getResponses(requesterId, List.of(newerAttachmentId)))
                .thenReturn(List.of(attachment(newerAttachmentId, "VIDEO")));

        List<ChatMessageResponse> history = messageService.getHistory(requesterId, chatId, null, null, null, 100);

        assertThat(history).extracting(ChatMessageResponse::messageId)
                .containsExactly(olderMessageId, newerMessageId);
        assertThat(history.get(0).attachments()).extracting(MessageAttachmentResponse::attachmentId)
                .containsExactly(olderAttachmentId);
        assertThat(history.get(1).attachments()).extracting(MessageAttachmentResponse::attachmentId)
                .containsExactly(newerAttachmentId);
        verify(messageRepository).findRecentByChatId(chatId, 100);
        verify(attachmentService).getResponses(requesterId, List.of(olderAttachmentId));
        verify(attachmentService).getResponses(requesterId, List.of(newerAttachmentId));
    }

    @Test
    void searchMessagesReturnsChronologicalMatchesWithRequesterScopedAttachments() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID olderMessageId = UUID.randomUUID();
        UUID newerMessageId = UUID.randomUUID();
        UUID olderAttachmentId = UUID.randomUUID();
        UUID newerAttachmentId = UUID.randomUUID();

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "GROUP"));
        when(forumTopicService.resolveTopicForRead(any(ChatEntity.class), any(UUID.class), any())).thenReturn(null);
        when(messageRepository.findAllByChatId(chatId)).thenReturn(new ArrayList<>(List.of(
                message(chatId, olderMessageId, olderAttachmentId, Instant.parse("2026-03-14T10:00:00Z")),
                message(chatId, newerMessageId, newerAttachmentId, Instant.parse("2026-03-14T10:05:00Z"))
        )));
        when(messageSearchCorpusService.buildSearchCorpora(anyMap(), anyMap())).thenReturn(Map.of(
                olderMessageId, "hello telegram",
                newerMessageId, "hello telegram"
        ));
        when(attachmentService.getResponses(requesterId, List.of(olderAttachmentId)))
                .thenReturn(List.of(attachment(olderAttachmentId, "PHOTO")));
        when(attachmentService.getResponses(requesterId, List.of(newerAttachmentId)))
                .thenReturn(List.of(attachment(newerAttachmentId, "VIDEO_NOTE")));

        SearchMessagesResponse response = messageService.searchMessages(requesterId, chatId, null, null, "hello", 20);

        assertThat(response.messages()).extracting(ChatMessageResponse::messageId)
                .containsExactly(olderMessageId, newerMessageId);
        assertThat(response.messages().get(0).attachments()).extracting(MessageAttachmentResponse::attachmentId)
                .containsExactly(olderAttachmentId);
        assertThat(response.messages().get(1).attachments()).extracting(MessageAttachmentResponse::attachmentId)
                .containsExactly(newerAttachmentId);
        verify(attachmentService).getResponses(requesterId, List.of(olderAttachmentId));
        verify(attachmentService).getResponses(requesterId, List.of(newerAttachmentId));
    }

    @Test
    void searchMessagesRejectsInvalidLimit() {
        assertThatThrownBy(() -> messageService.searchMessages(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                "hello",
                0
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).isEqualTo("limit must be between 1 and 100");
                });
    }

    @Test
    void getHistoryRejectsInvalidLimit() {
        assertThatThrownBy(() -> messageService.getHistory(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                null,
                0
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).isEqualTo("limit must be between 1 and 100");
                });
    }

    @Test
    void searchMessagesRejectsTooLongQuery() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(forumTopicService.resolveTopicForRead(chat, requesterId, null)).thenReturn(null);

        assertThatThrownBy(() -> messageService.searchMessages(
                requesterId,
                chatId,
                null,
                null,
                "a".repeat(256),
                20
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
                );

        verify(messageRepository, never()).findAllByChatId(chatId);
    }

    @Test
    void searchGlobalMessagesRejectsInvalidLimit() {
        assertThatThrownBy(() -> messageService.searchGlobalMessages(
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                "hello",
                0
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).isEqualTo("limit must be between 1 and 50");
                });
    }

    @Test
    void getMessageUsesRequesterScopedAttachmentResponses() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        MessageLookupEntity lookup = new MessageLookupEntity();
        lookup.setMessageId(messageId);
        lookup.setChatId(chatId);
        lookup.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        lookup.setSenderId(UUID.randomUUID());
        lookup.setCiphertext("ciphertext");
        lookup.setNonce("nonce");
        lookup.setKeyVersion(1);
        lookup.setAttachmentIds(List.of(attachmentId));
        lookup.setDeliveryStatus("READ");

        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(lookup));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "DIRECT"));
        when(attachmentService.getResponses(requesterId, List.of(attachmentId)))
                .thenReturn(List.of(attachment(attachmentId, "VOICE_NOTE")));

        ChatMessageResponse response = messageService.getMessage(requesterId, messageId);

        assertThat(response.messageId()).isEqualTo(messageId);
        assertThat(response.attachments()).extracting(MessageAttachmentResponse::attachmentId)
                .containsExactly(attachmentId);
        verify(attachmentService).getResponses(requesterId, List.of(attachmentId));
    }

    @Test
    void forwardMessageClonesAttachmentsIntoTargetChat() {
        UUID senderId = UUID.randomUUID();
        UUID sourceChatId = UUID.randomUUID();
        UUID targetChatId = UUID.randomUUID();
        UUID sourceMessageId = UUID.randomUUID();
        UUID sourceAttachmentId = UUID.randomUUID();
        UUID clonedAttachmentId = UUID.randomUUID();

        ChatEntity sourceChat = chat(sourceChatId, "GROUP");
        ChatEntity targetChat = chat(targetChatId, "GROUP");

        MessageLookupEntity source = new MessageLookupEntity();
        source.setMessageId(sourceMessageId);
        source.setChatId(sourceChatId);
        source.setSenderId(UUID.randomUUID());
        source.setCiphertext("ciphertext");
        source.setNonce("nonce");
        source.setKeyVersion(1);
        source.setDeliveryStatus("READ");
        source.setAttachmentIds(List.of(sourceAttachmentId));

        when(messageLookupRepository.findById(sourceMessageId)).thenReturn(Optional.of(source));
        when(chatService.getOwnedChat(senderId, sourceChatId)).thenReturn(sourceChat);
        when(chatService.getOwnedChat(senderId, targetChatId)).thenReturn(targetChat);
        when(chatService.getRecipientIds(targetChat, senderId)).thenReturn(List.of(UUID.randomUUID()));
        when(forumTopicService.resolveTopicForWrite(targetChat, senderId, null)).thenReturn(null);
        when(messageContentCodec.encode(any(MessageTextContent.class))).thenReturn("encoded-forward");
        when(chatEncryptionService.encrypt(targetChatId, "encoded-forward"))
                .thenReturn(new EncryptedPayload("cipher-out", "nonce-out", 1));
        when(attachmentService.cloneAttachmentsToChat(senderId, targetChatId, List.of(sourceAttachmentId)))
                .thenReturn(List.of(clonedAttachmentId));
        when(attachmentService.getResponses(senderId, List.of(clonedAttachmentId)))
                .thenReturn(List.of(attachment(clonedAttachmentId, "PHOTO")));

        ChatMessageResponse response = messageService.forwardMessage(
                senderId,
                new ForwardMessageRequest(targetChatId, null, null, null, sourceMessageId, null)
        );

        assertThat(response.attachments()).extracting(MessageAttachmentResponse::attachmentId)
                .containsExactly(clonedAttachmentId);
        verify(attachmentService).cloneAttachmentsToChat(senderId, targetChatId, List.of(sourceAttachmentId));
        verify(attachmentService).getResponses(senderId, List.of(clonedAttachmentId));
    }

    @Test
    void sendMessageCrossPostsToDiscussionWithSystemRecipientsAndClonedAttachments() {
        UUID senderId = UUID.randomUUID();
        UUID channelChatId = UUID.randomUUID();
        UUID discussionChatId = UUID.randomUUID();
        UUID sourceAttachmentId = UUID.randomUUID();
        UUID clonedAttachmentId = UUID.randomUUID();
        UUID mainRecipientId = UUID.randomUUID();
        UUID discussionRecipientId = UUID.randomUUID();

        ChatEntity channelChat = chat(channelChatId, "CHANNEL");
        channelChat.setCommentsEnabled(true);
        channelChat.setCrossPostingEnabled(true);
        channelChat.setLinkedDiscussionChatId(discussionChatId);

        ChatEntity discussionChat = chat(discussionChatId, "GROUP");

        when(chatService.getOwnedChat(senderId, channelChatId)).thenReturn(channelChat);
        when(chatService.getChat(discussionChatId)).thenReturn(discussionChat);
        when(chatService.getRecipientIds(channelChat, senderId)).thenReturn(List.of(mainRecipientId));
        when(chatService.getRecipientIdsForSystem(discussionChat, senderId)).thenReturn(List.of(discussionRecipientId));
        when(chatService.isCrossPostingEnabled(channelChatId)).thenReturn(true);
        when(forumTopicService.resolveTopicForWrite(channelChat, senderId, null)).thenReturn(null);
        when(messageContentCodec.normalize(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new MessageTextContent("Channel post", List.of()));
        when(messageContentCodec.encode(any(MessageTextContent.class))).thenReturn("encoded-message");
        when(chatEncryptionService.encrypt(channelChatId, "encoded-message"))
                .thenReturn(new EncryptedPayload("cipher-channel", "nonce-channel", 1));
        when(chatEncryptionService.encrypt(discussionChatId, "encoded-message"))
                .thenReturn(new EncryptedPayload("cipher-discussion", "nonce-discussion", 1));
        when(attachmentService.cloneAttachmentsToChatForSystem(senderId, discussionChatId, List.of(sourceAttachmentId)))
                .thenReturn(List.of(clonedAttachmentId));
        when(attachmentService.getResponses(senderId, List.of(sourceAttachmentId)))
                .thenReturn(List.of(attachment(sourceAttachmentId, "PHOTO")));

        ChatMessageResponse response = messageService.sendMessage(
                senderId,
                new com.alex.messenger.message.dto.SendMessageRequest(
                        channelChatId,
                        null,
                        null,
                        null,
                        "Channel post",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(sourceAttachmentId),
                        null,
                        false,
                        null
                )
        );

        ArgumentCaptor<MessageLookupEntity> captor = ArgumentCaptor.forClass(MessageLookupEntity.class);
        verify(messageStorageService, org.mockito.Mockito.atLeast(3)).save(captor.capture());
        List<MessageLookupEntity> savedLookups = captor.getAllValues();
        MessageLookupEntity discussionRoot = savedLookups.stream()
                .filter(lookup -> discussionChatId.equals(lookup.getChatId()))
                .findFirst()
                .orElseThrow();

        assertThat(response.attachments()).extracting(MessageAttachmentResponse::attachmentId)
                .containsExactly(sourceAttachmentId);
        assertThat(discussionRoot.getAttachmentIds()).containsExactly(clonedAttachmentId);
        assertThat(discussionRoot.getForwardedFromChatId()).isEqualTo(channelChatId);
        verify(attachmentService).cloneAttachmentsToChatForSystem(senderId, discussionChatId, List.of(sourceAttachmentId));
        verify(chatService, never()).getRecipientIds(discussionChat, senderId);
        verify(chatService, org.mockito.Mockito.atLeastOnce()).getRecipientIdsForSystem(discussionChat, senderId);
    }

    @Test
    void sendMessagePublishesChatSummaryRefreshForActiveParticipants() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "DIRECT");

        when(chatService.getOwnedChat(senderId, chatId)).thenReturn(chat);
        when(chatService.getRecipientIds(chat, senderId)).thenReturn(List.of(recipientId));
        when(forumTopicService.resolveTopicForWrite(chat, senderId, null)).thenReturn(null);
        when(messageContentCodec.normalize(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new MessageTextContent("Hello there", List.of()));
        when(messageContentCodec.encode(any(MessageTextContent.class))).thenReturn("encoded-direct");
        when(chatEncryptionService.encrypt(chatId, "encoded-direct"))
                .thenReturn(new EncryptedPayload("cipher-direct", "nonce-direct", 1));
        when(attachmentService.getResponses(senderId, List.of())).thenReturn(List.of());

        messageService.sendMessage(
                senderId,
                new SendMessageRequest(
                        chatId,
                        null,
                        null,
                        null,
                        "Hello there",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        false,
                        null
                )
        );

        ArgumentCaptor<java.util.Collection<UUID>> recipientsCaptor = ArgumentCaptor.forClass(java.util.Collection.class);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(chatService).publishChatUpdate(eq(senderId), eq(chatId), recipientsCaptor.capture(), payloadCaptor.capture());
        assertThat(recipientsCaptor.getValue()).containsExactly(senderId, recipientId);
        assertThat(payloadCaptor.getValue()).containsEntry("chatId", chatId);
        assertThat(payloadCaptor.getValue()).containsEntry("actorUserId", senderId);
        assertThat(payloadCaptor.getValue()).containsEntry("senderId", senderId);
    }

    @Test
    void getScheduledMessagesUsesRequesterScopedAttachmentResponses() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        ScheduledMessageEntity scheduledMessage = new ScheduledMessageEntity();
        scheduledMessage.setId(UUID.randomUUID());
        scheduledMessage.setChatId(chatId);
        scheduledMessage.setSenderId(requesterId);
        scheduledMessage.setCiphertext("ciphertext");
        scheduledMessage.setNonce("nonce");
        scheduledMessage.setKeyVersion(1);
        scheduledMessage.setAttachmentIds(attachmentId.toString());
        scheduledMessage.setScheduledAt(Instant.parse("2026-03-14T12:00:00Z"));
        scheduledMessage.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));
        scheduledMessage.setStatus("PENDING");

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "DIRECT"));
        when(forumTopicService.resolveTopicForRead(any(ChatEntity.class), any(UUID.class), any())).thenReturn(null);
        when(scheduledMessageRepository.findAllBySenderIdAndChatIdAndStatusInOrderByScheduledAtAsc(
                requesterId,
                chatId,
                List.of("PENDING", "WAITING_ONLINE")
        )).thenReturn(List.of(scheduledMessage));
        when(attachmentService.getResponses(requesterId, List.of(attachmentId)))
                .thenReturn(List.of(attachment(attachmentId, "PHOTO")));

        List<ScheduledMessageResponse> response = messageService.getScheduledMessages(requesterId, chatId, null, null);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).attachments()).extracting(MessageAttachmentResponse::attachmentId)
                .containsExactly(attachmentId);
        verify(attachmentService).getResponses(requesterId, List.of(attachmentId));
    }

    @Test
    void searchGlobalMessagesReturnsChronologicalMatchesWithRequesterScopedAttachments() {
        UUID requesterId = UUID.randomUUID();
        UUID firstChatId = UUID.randomUUID();
        UUID secondChatId = UUID.randomUUID();
        UUID olderMessageId = UUID.randomUUID();
        UUID newerMessageId = UUID.randomUUID();
        UUID olderAttachmentId = UUID.randomUUID();
        UUID newerAttachmentId = UUID.randomUUID();

        when(messageRepository.findAllByChatId(firstChatId)).thenReturn(List.of(
                message(firstChatId, olderMessageId, olderAttachmentId, Instant.parse("2026-03-14T10:00:00Z"))
        ));
        when(messageRepository.findAllByChatId(secondChatId)).thenReturn(List.of(
                message(secondChatId, newerMessageId, newerAttachmentId, Instant.parse("2026-03-14T10:05:00Z"))
        ));
        when(chatService.getOwnedChat(requesterId, firstChatId)).thenReturn(chat(firstChatId, "GROUP"));
        when(chatService.getOwnedChat(requesterId, secondChatId)).thenReturn(chat(secondChatId, "GROUP"));
        when(messageSearchCorpusService.buildSearchCorpora(anyMap(), anyMap())).thenReturn(Map.of(
                olderMessageId, "voice hello",
                newerMessageId, "voice hello"
        ));
        when(attachmentService.getResponses(requesterId, List.of(olderAttachmentId)))
                .thenReturn(List.of(attachment(olderAttachmentId, "VOICE_NOTE")));
        when(attachmentService.getResponses(requesterId, List.of(newerAttachmentId)))
                .thenReturn(List.of(attachment(newerAttachmentId, "VIDEO_NOTE")));

        List<ChatMessageResponse> response = messageService.searchGlobalMessages(
                requesterId,
                List.of(firstChatId, secondChatId),
                "hello",
                20
        );

        assertThat(response).extracting(ChatMessageResponse::messageId)
                .containsExactly(olderMessageId, newerMessageId);
        assertThat(response.get(0).attachments()).extracting(MessageAttachmentResponse::attachmentId)
                .containsExactly(olderAttachmentId);
        assertThat(response.get(1).attachments()).extracting(MessageAttachmentResponse::attachmentId)
                .containsExactly(newerAttachmentId);
        verify(attachmentService).getResponses(requesterId, List.of(olderAttachmentId));
        verify(attachmentService).getResponses(requesterId, List.of(newerAttachmentId));
    }

    @Test
    void searchGlobalMessagesDeduplicatesDuplicateChatIds() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat(chatId, "GROUP"));
        when(messageRepository.findAllByChatId(chatId)).thenReturn(List.of(
                message(chatId, messageId, attachmentId, Instant.parse("2026-03-14T10:00:00Z"))
        ));
        when(messageSearchCorpusService.buildSearchCorpora(anyMap(), anyMap())).thenReturn(Map.of(
                messageId, "hello once"
        ));
        when(attachmentService.getResponses(requesterId, List.of(attachmentId)))
                .thenReturn(List.of(attachment(attachmentId, "PHOTO")));

        List<ChatMessageResponse> response = messageService.searchGlobalMessages(
                requesterId,
                List.of(chatId, chatId),
                "hello",
                20
        );

        assertThat(response).extracting(ChatMessageResponse::messageId).containsExactly(messageId);
    }

    @Test
    void searchGlobalMessagesRejectsTooLongQuery() {
        assertThatThrownBy(() -> messageService.searchGlobalMessages(
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                "a".repeat(256),
                20
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
                );

        verify(chatService, never()).getOwnedChat(any(), any());
    }

    @Test
    void getMessageRejectsHiddenForumTopicMessage() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        chat.setForumEnabled(true);

        MessageLookupEntity lookup = new MessageLookupEntity();
        lookup.setMessageId(messageId);
        lookup.setChatId(chatId);
        lookup.setTopicId(topicId);
        lookup.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        lookup.setSenderId(UUID.randomUUID());
        lookup.setCiphertext("ciphertext");
        lookup.setNonce("nonce");
        lookup.setKeyVersion(1);
        lookup.setDeliveryStatus("READ");

        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(lookup));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(forumTopicService.resolveTopicForRead(chat, requesterId, topicId))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found"));

        assertThatThrownBy(() -> messageService.getMessage(requesterId, messageId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(exception -> assertThat(((org.springframework.web.server.ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void searchGlobalMessagesSkipsHiddenForumTopicMessages() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID hiddenTopicId = UUID.randomUUID();
        UUID visibleMessageId = UUID.randomUUID();
        UUID hiddenMessageId = UUID.randomUUID();
        UUID visibleAttachmentId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        chat.setForumEnabled(true);

        MessageEntity hiddenMessage = message(chatId, hiddenMessageId, UUID.randomUUID(), Instant.parse("2026-03-14T10:05:00Z"));
        hiddenMessage.setTopicId(hiddenTopicId);
        MessageEntity visibleMessage = message(chatId, visibleMessageId, visibleAttachmentId, Instant.parse("2026-03-14T10:00:00Z"));

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(messageRepository.findAllByChatId(chatId)).thenReturn(List.of(hiddenMessage, visibleMessage));
        when(forumTopicService.resolveTopicForRead(chat, requesterId, hiddenTopicId))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found"));
        when(messageSearchCorpusService.buildSearchCorpora(anyMap(), anyMap())).thenReturn(Map.of(
                hiddenMessageId, "secret roadmap",
                visibleMessageId, "secret roadmap"
        ));
        when(attachmentService.getResponses(requesterId, List.of(visibleAttachmentId)))
                .thenReturn(List.of(attachment(visibleAttachmentId, "PHOTO")));

        List<ChatMessageResponse> response = messageService.searchGlobalMessages(
                requesterId,
                List.of(chatId),
                "roadmap",
                20
        );

        assertThat(response).extracting(ChatMessageResponse::messageId).containsExactly(visibleMessageId);
        verify(attachmentService).getResponses(requesterId, List.of(visibleAttachmentId));
    }

    @Test
    void getMessageMasksHiddenAndInaccessibleReferences() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID hiddenTopicId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID hiddenReplyId = UUID.randomUUID();
        UUID hiddenThreadRootId = UUID.randomUUID();
        UUID discussionChatId = UUID.randomUUID();
        UUID discussionRootMessageId = UUID.randomUUID();
        UUID forwardedChatId = UUID.randomUUID();
        UUID forwardedMessageId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        chat.setForumEnabled(true);

        MessageLookupEntity lookup = new MessageLookupEntity();
        lookup.setMessageId(messageId);
        lookup.setChatId(chatId);
        lookup.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        lookup.setSenderId(UUID.randomUUID());
        lookup.setCiphertext("ciphertext");
        lookup.setNonce("nonce");
        lookup.setKeyVersion(1);
        lookup.setDeliveryStatus("READ");
        lookup.setReplyToMessageId(hiddenReplyId);
        lookup.setThreadRootMessageId(hiddenThreadRootId);
        lookup.setDiscussionChatId(discussionChatId);
        lookup.setDiscussionRootMessageId(discussionRootMessageId);
        lookup.setForwardedFromChatId(forwardedChatId);
        lookup.setForwardedFromMessageId(forwardedMessageId);

        MessageLookupEntity hiddenReply = new MessageLookupEntity();
        hiddenReply.setMessageId(hiddenReplyId);
        hiddenReply.setChatId(chatId);
        hiddenReply.setTopicId(hiddenTopicId);
        hiddenReply.setDeletedAt(null);

        MessageLookupEntity hiddenThreadRoot = new MessageLookupEntity();
        hiddenThreadRoot.setMessageId(hiddenThreadRootId);
        hiddenThreadRoot.setChatId(chatId);
        hiddenThreadRoot.setTopicId(hiddenTopicId);
        hiddenThreadRoot.setDeletedAt(null);

        MessageLookupEntity discussionRoot = new MessageLookupEntity();
        discussionRoot.setMessageId(discussionRootMessageId);
        discussionRoot.setChatId(discussionChatId);
        discussionRoot.setDeletedAt(null);

        MessageLookupEntity forwardedSource = new MessageLookupEntity();
        forwardedSource.setMessageId(forwardedMessageId);
        forwardedSource.setChatId(forwardedChatId);
        forwardedSource.setDeletedAt(null);

        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(lookup));
        when(messageLookupRepository.findById(hiddenReplyId)).thenReturn(Optional.of(hiddenReply));
        when(messageLookupRepository.findById(hiddenThreadRootId)).thenReturn(Optional.of(hiddenThreadRoot));
        when(messageLookupRepository.findById(discussionRootMessageId)).thenReturn(Optional.of(discussionRoot));
        when(messageLookupRepository.findById(forwardedMessageId)).thenReturn(Optional.of(forwardedSource));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(chatService.getOwnedChat(requesterId, discussionChatId))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied"));
        when(chatService.getOwnedChat(requesterId, forwardedChatId))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied"));
        when(forumTopicService.resolveTopicForRead(chat, requesterId, hiddenTopicId))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found"));

        ChatMessageResponse response = messageService.getMessage(requesterId, messageId);

        assertThat(response.replyToMessageId()).isNull();
        assertThat(response.threadRootMessageId()).isNull();
        assertThat(response.discussionChatId()).isNull();
        assertThat(response.discussionRootMessageId()).isNull();
        assertThat(response.forwardedFromChatId()).isNull();
        assertThat(response.forwardedFromMessageId()).isNull();
    }

    @Test
    void getHistoryMasksDeletedHiddenAndInaccessibleReferences() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID hiddenTopicId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID deletedReplyId = UUID.randomUUID();
        UUID hiddenThreadRootId = UUID.randomUUID();
        UUID forwardedChatId = UUID.randomUUID();
        UUID forwardedMessageId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        chat.setForumEnabled(true);

        MessageEntity message = message(chatId, messageId, UUID.randomUUID(), Instant.parse("2026-03-14T10:00:00Z"));
        message.setReplyToMessageId(deletedReplyId);
        message.setThreadRootMessageId(hiddenThreadRootId);
        message.setForwardedFromChatId(forwardedChatId);
        message.setForwardedFromMessageId(forwardedMessageId);

        MessageLookupEntity deletedReply = new MessageLookupEntity();
        deletedReply.setMessageId(deletedReplyId);
        deletedReply.setChatId(chatId);
        deletedReply.setDeletedAt(Instant.parse("2026-03-14T09:59:00Z"));

        MessageLookupEntity hiddenThreadRoot = new MessageLookupEntity();
        hiddenThreadRoot.setMessageId(hiddenThreadRootId);
        hiddenThreadRoot.setChatId(chatId);
        hiddenThreadRoot.setTopicId(hiddenTopicId);
        hiddenThreadRoot.setDeletedAt(null);

        MessageLookupEntity forwardedSource = new MessageLookupEntity();
        forwardedSource.setMessageId(forwardedMessageId);
        forwardedSource.setChatId(forwardedChatId);
        forwardedSource.setDeletedAt(null);

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(chatService.getOwnedChat(requesterId, forwardedChatId))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied"));
        when(forumTopicService.resolveTopicForRead(chat, requesterId, null)).thenReturn(null);
        when(forumTopicService.resolveTopicForRead(chat, requesterId, hiddenTopicId))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found"));
        when(messageRepository.findRecentByChatId(chatId, 50)).thenReturn(List.of(message));
        when(messageLookupRepository.findById(deletedReplyId)).thenReturn(Optional.of(deletedReply));
        when(messageLookupRepository.findById(hiddenThreadRootId)).thenReturn(Optional.of(hiddenThreadRoot));
        when(messageLookupRepository.findById(forwardedMessageId)).thenReturn(Optional.of(forwardedSource));

        List<ChatMessageResponse> history = messageService.getHistory(requesterId, chatId, null, null, null, 50);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).replyToMessageId()).isNull();
        assertThat(history.get(0).threadRootMessageId()).isNull();
        assertThat(history.get(0).forwardedFromChatId()).isNull();
        assertThat(history.get(0).forwardedFromMessageId()).isNull();
    }

    @Test
    void getMessageCountsOnlyNonDeletedDiscussionReplies() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID rootMessageId = UUID.randomUUID();
        UUID activeReplyId = UUID.randomUUID();
        UUID deletedReplyId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");

        MessageLookupEntity rootMessage = new MessageLookupEntity();
        rootMessage.setMessageId(rootMessageId);
        rootMessage.setChatId(chatId);
        rootMessage.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        rootMessage.setSenderId(UUID.randomUUID());
        rootMessage.setCiphertext("ciphertext");
        rootMessage.setNonce("nonce");
        rootMessage.setKeyVersion(1);
        rootMessage.setDeliveryStatus("READ");
        rootMessage.setDiscussionRootMessageId(rootMessageId);

        MessageThreadEntity rootThreadEntry = new MessageThreadEntity();
        rootThreadEntry.setKey(new MessageThreadPrimaryKey(rootMessageId, rootMessageId));
        rootThreadEntry.setChatId(chatId);
        rootThreadEntry.setDeletedAt(null);

        MessageThreadEntity activeReply = new MessageThreadEntity();
        activeReply.setKey(new MessageThreadPrimaryKey(rootMessageId, activeReplyId));
        activeReply.setChatId(chatId);
        activeReply.setDeletedAt(null);

        MessageThreadEntity deletedReply = new MessageThreadEntity();
        deletedReply.setKey(new MessageThreadPrimaryKey(rootMessageId, deletedReplyId));
        deletedReply.setChatId(chatId);
        deletedReply.setDeletedAt(Instant.parse("2026-03-14T10:05:00Z"));

        when(messageLookupRepository.findById(rootMessageId)).thenReturn(Optional.of(rootMessage));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(messageThreadRepository.findAllByThreadRootMessageId(rootMessageId))
                .thenReturn(List.of(rootThreadEntry, activeReply, deletedReply));

        ChatMessageResponse response = messageService.getMessage(requesterId, rootMessageId);

        assertThat(response.commentCount()).isEqualTo(1);
    }

    @Test
    void getScheduledMessagesInfersThreadTopicWhenTopicIdOmitted() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID threadRootMessageId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        chat.setForumEnabled(true);

        ForumTopicEntity generalTopic = new ForumTopicEntity();
        generalTopic.setId(UUID.randomUUID());
        generalTopic.setChatId(chatId);
        generalTopic.setGeneralTopic(true);

        MessageLookupEntity threadRoot = new MessageLookupEntity();
        threadRoot.setMessageId(threadRootMessageId);
        threadRoot.setChatId(chatId);
        threadRoot.setTopicId(topicId);

        ScheduledMessageEntity scheduledMessage = new ScheduledMessageEntity();
        scheduledMessage.setId(UUID.randomUUID());
        scheduledMessage.setChatId(chatId);
        scheduledMessage.setSenderId(requesterId);
        scheduledMessage.setTopicId(topicId);
        scheduledMessage.setThreadRootMessageId(threadRootMessageId);
        scheduledMessage.setCiphertext("ciphertext");
        scheduledMessage.setNonce("nonce");
        scheduledMessage.setKeyVersion(1);
        scheduledMessage.setAttachmentIds(attachmentId.toString());
        scheduledMessage.setScheduledAt(Instant.parse("2026-03-14T12:00:00Z"));
        scheduledMessage.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));
        scheduledMessage.setStatus("PENDING");

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(forumTopicService.resolveTopicForRead(chat, requesterId, null)).thenReturn(generalTopic);
        when(messageLookupRepository.findById(threadRootMessageId)).thenReturn(Optional.of(threadRoot));
        when(scheduledMessageRepository.findAllBySenderIdAndChatIdAndStatusInOrderByScheduledAtAsc(
                requesterId,
                chatId,
                List.of("PENDING", "WAITING_ONLINE")
        )).thenReturn(List.of(scheduledMessage));
        when(attachmentService.getResponses(requesterId, List.of(attachmentId)))
                .thenReturn(List.of(attachment(attachmentId, "PHOTO")));

        List<ScheduledMessageResponse> response = messageService.getScheduledMessages(requesterId, chatId, null, threadRootMessageId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).threadRootMessageId()).isEqualTo(threadRootMessageId);
        assertThat(response.get(0).topicId()).isEqualTo(topicId);
    }

    @Test
    void getScheduledMessagesMasksHiddenAndInaccessibleReferences() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID replyToMessageId = UUID.randomUUID();
        UUID discussionChatId = UUID.randomUUID();
        UUID discussionRootMessageId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        chat.setForumEnabled(true);

        ForumTopicEntity topic = new ForumTopicEntity();
        topic.setId(topicId);
        topic.setChatId(chatId);
        topic.setGeneralTopic(false);

        ScheduledMessageEntity scheduledMessage = new ScheduledMessageEntity();
        scheduledMessage.setId(UUID.randomUUID());
        scheduledMessage.setChatId(chatId);
        scheduledMessage.setSenderId(requesterId);
        scheduledMessage.setTopicId(topicId);
        scheduledMessage.setReplyToMessageId(replyToMessageId);
        scheduledMessage.setDiscussionChatId(discussionChatId);
        scheduledMessage.setDiscussionRootMessageId(discussionRootMessageId);
        scheduledMessage.setCiphertext("ciphertext");
        scheduledMessage.setNonce("nonce");
        scheduledMessage.setKeyVersion(1);
        scheduledMessage.setAttachmentIds("");
        scheduledMessage.setScheduledAt(Instant.parse("2026-03-14T12:00:00Z"));
        scheduledMessage.setCreatedAt(Instant.parse("2026-03-14T11:00:00Z"));
        scheduledMessage.setStatus("PENDING");

        MessageLookupEntity deletedReply = new MessageLookupEntity();
        deletedReply.setMessageId(replyToMessageId);
        deletedReply.setChatId(chatId);
        deletedReply.setTopicId(topicId);
        deletedReply.setDeletedAt(Instant.parse("2026-03-14T10:30:00Z"));

        MessageLookupEntity discussionRoot = new MessageLookupEntity();
        discussionRoot.setMessageId(discussionRootMessageId);
        discussionRoot.setChatId(discussionChatId);
        discussionRoot.setDeletedAt(null);

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(forumTopicService.resolveTopicForRead(chat, requesterId, topicId)).thenReturn(topic);
        when(scheduledMessageRepository.findAllBySenderIdAndChatIdAndStatusInOrderByScheduledAtAsc(
                requesterId,
                chatId,
                List.of("PENDING", "WAITING_ONLINE")
        )).thenReturn(List.of(scheduledMessage));
        when(messageLookupRepository.findById(replyToMessageId)).thenReturn(Optional.of(deletedReply));
        when(messageLookupRepository.findById(discussionRootMessageId)).thenReturn(Optional.of(discussionRoot));
        when(chatService.getOwnedChat(requesterId, discussionChatId))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied"));
        when(attachmentService.getResponses(requesterId, List.of())).thenReturn(List.of());

        List<ScheduledMessageResponse> response = messageService.getScheduledMessages(requesterId, chatId, topicId, null);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).replyToMessageId()).isNull();
        assertThat(response.get(0).threadRootMessageId()).isNull();
        assertThat(response.get(0).discussionChatId()).isNull();
        assertThat(response.get(0).discussionRootMessageId()).isNull();
    }

    @Test
    void sendMessageRejectsDeletedReplyTarget() {
        UUID senderId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID replyToMessageId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");

        MessageLookupEntity deletedReplyTarget = new MessageLookupEntity();
        deletedReplyTarget.setMessageId(replyToMessageId);
        deletedReplyTarget.setChatId(chatId);
        deletedReplyTarget.setDeletedAt(Instant.parse("2026-03-14T09:59:00Z"));

        when(messageContentCodec.normalize(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new MessageTextContent("Hello", List.of()));
        when(chatService.getOwnedChat(senderId, chatId)).thenReturn(chat);
        when(forumTopicService.resolveTopicForWrite(chat, senderId, null)).thenReturn(null);
        when(messageLookupRepository.findById(replyToMessageId)).thenReturn(Optional.of(deletedReplyTarget));

        assertThatThrownBy(() -> messageService.sendMessage(
                senderId,
                new SendMessageRequest(
                        chatId,
                        null,
                        null,
                        replyToMessageId,
                        "Hello",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        false,
                        null
                )
        ))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(exception -> assertThat(((org.springframework.web.server.ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        verify(messageStorageService, never()).save(any(MessageLookupEntity.class));
    }

    @Test
    void dispatchScheduledMessageFailsWhenTopicIsNoLongerWritable() {
        UUID senderId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        chat.setForumEnabled(true);

        ScheduledMessageEntity scheduledMessage = new ScheduledMessageEntity();
        scheduledMessage.setChatId(chatId);
        scheduledMessage.setSenderId(senderId);
        scheduledMessage.setTopicId(topicId);
        scheduledMessage.setCiphertext("ciphertext");
        scheduledMessage.setNonce("nonce");
        scheduledMessage.setKeyVersion(1);
        scheduledMessage.setAttachmentIds("");
        scheduledMessage.setStatus("PENDING");

        when(chatService.getOwnedChat(senderId, chatId)).thenReturn(chat);
        when(forumTopicService.resolveTopicForWrite(chat, senderId, topicId))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found"));
        when(scheduledMessageRepository.save(any(ScheduledMessageEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        messageService.dispatchScheduledMessage(scheduledMessage);

        assertThat(scheduledMessage.getStatus()).isEqualTo("FAILED");
        assertThat(scheduledMessage.getErrorMessage()).contains("Topic not found");
        verify(messageStorageService, never()).save(any(MessageLookupEntity.class));
    }

    @Test
    void dispatchScheduledMessageFailsWhenStoredThreadMetadataIsOutOfDate() {
        UUID senderId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID replyToMessageId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");

        ScheduledMessageEntity scheduledMessage = new ScheduledMessageEntity();
        scheduledMessage.setChatId(chatId);
        scheduledMessage.setSenderId(senderId);
        scheduledMessage.setReplyToMessageId(replyToMessageId);
        scheduledMessage.setThreadRootMessageId(UUID.randomUUID());
        scheduledMessage.setDiscussionChatId(chatId);
        scheduledMessage.setDiscussionRootMessageId(replyToMessageId);
        scheduledMessage.setCiphertext("ciphertext");
        scheduledMessage.setNonce("nonce");
        scheduledMessage.setKeyVersion(1);
        scheduledMessage.setAttachmentIds("");
        scheduledMessage.setStatus("PENDING");

        MessageLookupEntity replyTarget = new MessageLookupEntity();
        replyTarget.setMessageId(replyToMessageId);
        replyTarget.setChatId(chatId);
        replyTarget.setDeletedAt(null);

        when(chatService.getOwnedChat(senderId, chatId)).thenReturn(chat);
        when(forumTopicService.resolveTopicForWrite(chat, senderId, null)).thenReturn(null);
        when(messageLookupRepository.findById(replyToMessageId)).thenReturn(Optional.of(replyTarget));
        when(scheduledMessageRepository.save(any(ScheduledMessageEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        messageService.dispatchScheduledMessage(scheduledMessage);

        assertThat(scheduledMessage.getStatus()).isEqualTo("FAILED");
        assertThat(scheduledMessage.getErrorMessage()).contains("thread metadata is out of date");
        verify(messageStorageService, never()).save(any(MessageLookupEntity.class));
    }

    @Test
    void getHistoryRejectsThreadRootFromAnotherTopicWhenExplicitTopicRequested() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID explicitTopicId = UUID.randomUUID();
        UUID actualThreadTopicId = UUID.randomUUID();
        UUID threadRootMessageId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        chat.setForumEnabled(true);

        ForumTopicEntity explicitTopic = new ForumTopicEntity();
        explicitTopic.setId(explicitTopicId);
        explicitTopic.setChatId(chatId);
        explicitTopic.setGeneralTopic(false);

        MessageLookupEntity threadRoot = new MessageLookupEntity();
        threadRoot.setMessageId(threadRootMessageId);
        threadRoot.setChatId(chatId);
        threadRoot.setTopicId(actualThreadTopicId);

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(forumTopicService.resolveTopicForRead(chat, requesterId, explicitTopicId)).thenReturn(explicitTopic);
        when(messageLookupRepository.findById(threadRootMessageId)).thenReturn(Optional.of(threadRoot));

        assertThatThrownBy(() -> messageService.getHistory(
                requesterId,
                chatId,
                explicitTopicId,
                threadRootMessageId,
                null,
                50
        ))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(exception -> assertThat(((org.springframework.web.server.ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void getHistoryAllowsDeletedThreadRootAsScopeAnchor() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID threadRootMessageId = UUID.randomUUID();
        UUID replyMessageId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");

        MessageLookupEntity deletedThreadRoot = new MessageLookupEntity();
        deletedThreadRoot.setMessageId(threadRootMessageId);
        deletedThreadRoot.setChatId(chatId);
        deletedThreadRoot.setDeletedAt(Instant.parse("2026-03-14T09:59:00Z"));

        MessageThreadEntity deletedRootEntry = new MessageThreadEntity();
        deletedRootEntry.setKey(new MessageThreadPrimaryKey(threadRootMessageId, threadRootMessageId));
        deletedRootEntry.setChatId(chatId);
        deletedRootEntry.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        deletedRootEntry.setSenderId(UUID.randomUUID());
        deletedRootEntry.setCiphertext("ciphertext");
        deletedRootEntry.setNonce("nonce");
        deletedRootEntry.setKeyVersion(1);
        deletedRootEntry.setAttachmentIds(List.of());
        deletedRootEntry.setDeliveryStatus("READ");
        deletedRootEntry.setDeletedAt(Instant.parse("2026-03-14T10:05:00Z"));

        MessageThreadEntity replyEntry = new MessageThreadEntity();
        replyEntry.setKey(new MessageThreadPrimaryKey(threadRootMessageId, replyMessageId));
        replyEntry.setChatId(chatId);
        replyEntry.setCreatedAt(Instant.parse("2026-03-14T10:06:00Z"));
        replyEntry.setSenderId(UUID.randomUUID());
        replyEntry.setCiphertext("ciphertext");
        replyEntry.setNonce("nonce");
        replyEntry.setKeyVersion(1);
        replyEntry.setAttachmentIds(List.of());
        replyEntry.setDeliveryStatus("READ");
        replyEntry.setDeletedAt(null);

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(forumTopicService.resolveTopicForRead(chat, requesterId, null)).thenReturn(null);
        when(messageLookupRepository.findById(threadRootMessageId)).thenReturn(Optional.of(deletedThreadRoot));
        when(messageThreadRepository.findRecentByThreadRootMessageId(threadRootMessageId, 50))
                .thenReturn(List.of(replyEntry, deletedRootEntry));
        when(attachmentService.getResponses(requesterId, List.of())).thenReturn(List.of());

        List<ChatMessageResponse> history = messageService.getHistory(requesterId, chatId, null, threadRootMessageId, null, 50);

        assertThat(history).extracting(ChatMessageResponse::messageId)
                .containsExactly(threadRootMessageId, replyMessageId);
        assertThat(history.get(0).deletedAt()).isEqualTo(Instant.parse("2026-03-14T10:05:00Z"));
    }

    @Test
    void searchMessagesAllowsDeletedThreadRootAsScopeAnchor() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID threadRootMessageId = UUID.randomUUID();
        UUID replyMessageId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");

        MessageLookupEntity deletedThreadRoot = new MessageLookupEntity();
        deletedThreadRoot.setMessageId(threadRootMessageId);
        deletedThreadRoot.setChatId(chatId);
        deletedThreadRoot.setDeletedAt(Instant.parse("2026-03-14T09:59:00Z"));

        MessageThreadEntity deletedRootEntry = new MessageThreadEntity();
        deletedRootEntry.setKey(new MessageThreadPrimaryKey(threadRootMessageId, threadRootMessageId));
        deletedRootEntry.setChatId(chatId);
        deletedRootEntry.setCreatedAt(Instant.parse("2026-03-14T10:00:00Z"));
        deletedRootEntry.setSenderId(UUID.randomUUID());
        deletedRootEntry.setCiphertext("ciphertext");
        deletedRootEntry.setNonce("nonce");
        deletedRootEntry.setKeyVersion(1);
        deletedRootEntry.setAttachmentIds(List.of());
        deletedRootEntry.setDeliveryStatus("READ");
        deletedRootEntry.setDeletedAt(Instant.parse("2026-03-14T10:05:00Z"));

        MessageThreadEntity replyEntry = new MessageThreadEntity();
        replyEntry.setKey(new MessageThreadPrimaryKey(threadRootMessageId, replyMessageId));
        replyEntry.setChatId(chatId);
        replyEntry.setCreatedAt(Instant.parse("2026-03-14T10:06:00Z"));
        replyEntry.setSenderId(UUID.randomUUID());
        replyEntry.setCiphertext("ciphertext");
        replyEntry.setNonce("nonce");
        replyEntry.setKeyVersion(1);
        replyEntry.setAttachmentIds(List.of());
        replyEntry.setDeliveryStatus("READ");
        replyEntry.setDeletedAt(null);

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(forumTopicService.resolveTopicForRead(chat, requesterId, null)).thenReturn(null);
        when(messageLookupRepository.findById(threadRootMessageId)).thenReturn(Optional.of(deletedThreadRoot));
        when(messageThreadRepository.findAllByThreadRootMessageId(threadRootMessageId))
                .thenReturn(List.of(deletedRootEntry, replyEntry));
        when(messageSearchCorpusService.buildSearchCorpora(anyMap(), anyMap()))
                .thenReturn(Map.of(
                        threadRootMessageId, "root",
                        replyMessageId, "hello thread"
                ));

        SearchMessagesResponse response = messageService.searchMessages(
                requesterId,
                chatId,
                null,
                threadRootMessageId,
                "hello",
                20
        );

        assertThat(response.messages()).extracting(ChatMessageResponse::messageId)
                .containsExactly(replyMessageId);
    }

    @Test
    void deleteMessageSyncsPublicPostSearchIndex() {
        UUID senderId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "CHANNEL");
        MessageLookupEntity lookup = new MessageLookupEntity();
        lookup.setMessageId(messageId);
        lookup.setChatId(chatId);
        lookup.setSenderId(senderId);
        lookup.setCiphertext("ciphertext");
        lookup.setNonce("nonce");
        lookup.setKeyVersion(1);
        lookup.setCreatedAt(Instant.parse("2026-03-19T15:00:00Z"));
        lookup.setAttachmentIds(List.of());

        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(lookup));
        when(chatService.getOwnedChat(senderId, chatId)).thenReturn(chat);
        when(chatService.getRecipientIds(chat, senderId)).thenReturn(List.of());
        when(chatService.getChat(chatId)).thenReturn(chat);
        when(messageRepository.findRecentByChatId(chatId, 200)).thenReturn(List.of());
        when(attachmentService.getResponses(senderId, List.of())).thenReturn(List.of());

        messageService.deleteMessage(senderId, messageId);

        verify(publicPostSearchService).syncMessage(lookup);
        assertThat(lookup.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteMessagePublishesChatSummaryRefreshAfterStateReconciliation() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "DIRECT");
        MessageLookupEntity lookup = new MessageLookupEntity();
        lookup.setMessageId(messageId);
        lookup.setChatId(chatId);
        lookup.setSenderId(senderId);
        lookup.setCiphertext("cipher-delete");
        lookup.setNonce("nonce-delete");
        lookup.setKeyVersion(1);
        lookup.setAttachmentIds(List.of());
        lookup.setCreatedAt(Instant.parse("2026-03-19T17:00:00Z"));

        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(lookup));
        when(chatService.getOwnedChat(senderId, chatId)).thenReturn(chat);
        when(chatService.getRecipientIds(chat, senderId)).thenReturn(List.of(recipientId));
        when(chatService.getChat(chatId)).thenReturn(chat);
        when(chatService.getRecipientIdsForSystem(chat, senderId)).thenReturn(List.of(recipientId));
        when(messageRepository.findRecentByChatId(chatId, 200)).thenReturn(List.of());
        when(attachmentService.getResponses(senderId, List.of())).thenReturn(List.of());

        messageService.deleteMessage(senderId, messageId);

        ArgumentCaptor<java.util.Collection<UUID>> recipientsCaptor = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(chatService).publishChatUpdate(eq(senderId), eq(chatId), recipientsCaptor.capture(), anyMap());
        assertThat(recipientsCaptor.getValue()).containsExactly(senderId, recipientId);
        verify(userSyncService).recordForUsers(
                argThat(userIds -> userIds != null
                        && userIds.size() == 2
                        && userIds.containsAll(List.of(senderId, recipientId))),
                eq("MESSAGE_DELETED"),
                eq("MESSAGE"),
                eq(messageId),
                eq(chatId),
                any()
        );
        verify(chatService).reconcileUnreadState(chatId);
    }

    @Test
    void updateLiveLocationPublishesUpdatedMessageEvent() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2999-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-03-19T16:05:00Z");

        ChatEntity chat = chat(chatId, "DIRECT");
        MessageLookupEntity lookup = new MessageLookupEntity();
        lookup.setMessageId(messageId);
        lookup.setChatId(chatId);
        lookup.setSenderId(senderId);
        lookup.setCreatedAt(Instant.parse("2026-03-19T16:00:00Z"));
        lookup.setCiphertext("cipher-live-original");
        lookup.setNonce("nonce-live-original");
        lookup.setKeyVersion(1);
        lookup.setAttachmentIds(List.of());
        lookup.setDeliveryStatus("READ");

        MessageLiveLocationPayload originalLiveLocation = new MessageLiveLocationPayload(
                53.9,
                27.56,
                "Downtown",
                "Old address",
                3_600,
                expiresAt,
                Instant.parse("2026-03-19T16:00:00Z"),
                null,
                true
        );
        MessageLiveLocationPayload updatedLiveLocation = new MessageLiveLocationPayload(
                54.1,
                27.7,
                "Updated point",
                "New address",
                3_600,
                expiresAt,
                updatedAt,
                null,
                true
        );
        MessageTextContent originalContent = liveLocationContent(originalLiveLocation);
        MessageTextContent updatedContent = liveLocationContent(updatedLiveLocation);
        UpdateLiveLocationRequest request = new UpdateLiveLocationRequest(54.1, 27.7, "Updated point", "New address");

        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(lookup));
        when(chatService.getOwnedChat(senderId, chatId)).thenReturn(chat);
        when(chatService.getRecipientIds(chat, senderId)).thenReturn(List.of(recipientId));
        when(chatEncryptionService.decrypt(chatId, "cipher-live-original", "nonce-live-original", 1))
                .thenReturn("decoded-live-original");
        when(chatEncryptionService.decrypt(chatId, "cipher-live-updated", "nonce-live-updated", 2))
                .thenReturn("decoded-live-updated");
        when(messageContentCodec.decode("decoded-live-original")).thenReturn(originalContent);
        when(messageContentCodec.decode("decoded-live-updated")).thenReturn(updatedContent);
        when(messageLiveLocationService.update(lookup, request)).thenReturn(updatedLiveLocation);
        when(messageContentCodec.encode(updatedContent)).thenReturn("encoded-live-updated");
        when(chatEncryptionService.encrypt(chatId, "encoded-live-updated"))
                .thenReturn(new EncryptedPayload("cipher-live-updated", "nonce-live-updated", 2));
        when(attachmentService.getResponses(senderId, List.of())).thenReturn(List.of());

        ChatMessageResponse response = messageService.updateLiveLocation(senderId, messageId, request);

        ArgumentCaptor<MessageEvent> eventCaptor = ArgumentCaptor.forClass(MessageEvent.class);
        verify(chatMessagePublisher).publish(eventCaptor.capture());
        MessageEvent event = eventCaptor.getValue();
        assertThat(event.messageId()).isEqualTo(messageId);
        assertThat(event.recipientIds()).containsExactly(recipientId);
        assertThat(event.ciphertext()).isEqualTo("cipher-live-updated");
        assertThat(event.nonce()).isEqualTo("nonce-live-updated");
        assertThat(event.keyVersion()).isEqualTo(2);
        assertThat(event.editedAt()).isNotNull();

        verify(publicPostSearchService).syncMessage(lookup);
        verify(botUpdateService).maybeEnqueueMessageEdited(chat, senderId, lookup);
        assertThat(lookup.getCiphertext()).isEqualTo("cipher-live-updated");
        assertThat(lookup.getNonce()).isEqualTo("nonce-live-updated");
        assertThat(lookup.getKeyVersion()).isEqualTo(2);
        assertThat(lookup.getEditedAt()).isNotNull();

        assertThat(response.messageId()).isEqualTo(messageId);
        assertThat(response.messageType()).isEqualTo("LIVE_LOCATION");
        assertThat(response.location()).isNull();
        assertThat(response.liveLocation()).isNotNull();
        assertThat(response.liveLocation().title()).isEqualTo("Updated point");
        assertThat(response.liveLocation().address()).isEqualTo("New address");
        assertThat(response.liveLocation().lastUpdatedAt()).isEqualTo(updatedAt);
        assertThat(response.liveLocation().active()).isTrue();
        assertThat(response.editedAt()).isNotNull();
    }

    @Test
    void stopLiveLocationPublishesStoppedMessageEvent() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2999-01-01T00:00:00Z");
        Instant stoppedAt = Instant.parse("2026-03-19T16:07:00Z");

        ChatEntity chat = chat(chatId, "DIRECT");
        MessageLookupEntity lookup = new MessageLookupEntity();
        lookup.setMessageId(messageId);
        lookup.setChatId(chatId);
        lookup.setSenderId(senderId);
        lookup.setCreatedAt(Instant.parse("2026-03-19T16:00:00Z"));
        lookup.setCiphertext("cipher-live-original");
        lookup.setNonce("nonce-live-original");
        lookup.setKeyVersion(1);
        lookup.setAttachmentIds(List.of());
        lookup.setDeliveryStatus("READ");

        MessageLiveLocationPayload originalLiveLocation = new MessageLiveLocationPayload(
                53.9,
                27.56,
                "Downtown",
                "Address",
                3_600,
                expiresAt,
                Instant.parse("2026-03-19T16:00:00Z"),
                null,
                true
        );
        MessageLiveLocationPayload stoppedLiveLocation = new MessageLiveLocationPayload(
                53.9,
                27.56,
                "Downtown",
                "Address",
                3_600,
                expiresAt,
                Instant.parse("2026-03-19T16:06:30Z"),
                stoppedAt,
                false
        );
        MessageTextContent originalContent = liveLocationContent(originalLiveLocation);
        MessageTextContent stoppedContent = liveLocationContent(stoppedLiveLocation);

        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(lookup));
        when(chatService.getOwnedChat(senderId, chatId)).thenReturn(chat);
        when(chatService.getRecipientIds(chat, senderId)).thenReturn(List.of(recipientId));
        when(chatEncryptionService.decrypt(chatId, "cipher-live-original", "nonce-live-original", 1))
                .thenReturn("decoded-live-original");
        when(chatEncryptionService.decrypt(chatId, "cipher-live-stopped", "nonce-live-stopped", 2))
                .thenReturn("decoded-live-stopped");
        when(messageContentCodec.decode("decoded-live-original")).thenReturn(originalContent);
        when(messageContentCodec.decode("decoded-live-stopped")).thenReturn(stoppedContent);
        when(messageLiveLocationService.stop(lookup)).thenReturn(stoppedLiveLocation);
        when(messageContentCodec.encode(stoppedContent)).thenReturn("encoded-live-stopped");
        when(chatEncryptionService.encrypt(chatId, "encoded-live-stopped"))
                .thenReturn(new EncryptedPayload("cipher-live-stopped", "nonce-live-stopped", 2));
        when(attachmentService.getResponses(senderId, List.of())).thenReturn(List.of());

        ChatMessageResponse response = messageService.stopLiveLocation(senderId, messageId);

        ArgumentCaptor<MessageEvent> eventCaptor = ArgumentCaptor.forClass(MessageEvent.class);
        verify(chatMessagePublisher).publish(eventCaptor.capture());
        MessageEvent event = eventCaptor.getValue();
        assertThat(event.messageId()).isEqualTo(messageId);
        assertThat(event.recipientIds()).containsExactly(recipientId);
        assertThat(event.ciphertext()).isEqualTo("cipher-live-stopped");
        assertThat(event.editedAt()).isNotNull();

        verify(publicPostSearchService).syncMessage(lookup);
        verify(botUpdateService).maybeEnqueueMessageEdited(chat, senderId, lookup);
        assertThat(response.messageType()).isEqualTo("LIVE_LOCATION");
        assertThat(response.liveLocation()).isNotNull();
        assertThat(response.liveLocation().active()).isFalse();
        assertThat(response.liveLocation().stoppedAt()).isEqualTo(stoppedAt);
        assertThat(response.editedAt()).isNotNull();
    }

    @Test
    void deleteMessageAllowsModeratorToRemoveAnotherUsersMessage() {
        UUID requesterId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");

        MessageLookupEntity lookup = new MessageLookupEntity();
        lookup.setMessageId(messageId);
        lookup.setChatId(chatId);
        lookup.setSenderId(senderId);
        lookup.setCiphertext("cipher-moderated");
        lookup.setNonce("nonce-moderated");
        lookup.setKeyVersion(1);
        lookup.setAttachmentIds(List.of());
        lookup.setCreatedAt(Instant.parse("2026-03-24T11:00:00Z"));
        lookup.setDeliveryStatus("DELIVERED");

        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(lookup));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(chatService.hasMessageModerationPermission(requesterId, chatId)).thenReturn(true);
        when(chatService.getRecipientIds(chat, requesterId)).thenReturn(List.of(recipientId));
        when(chatService.getChat(chatId)).thenReturn(chat);
        when(messageRepository.findRecentByChatId(chatId, 200)).thenReturn(List.of());
        when(attachmentService.getResponses(requesterId, List.of())).thenReturn(List.of());

        ChatMessageResponse response = messageService.deleteMessage(
                requesterId,
                messageId,
                new DeleteMessageRequest(true, "spam cleanup")
        );

        assertThat(lookup.getDeletedAt()).isNotNull();
        assertThat(response.deletedAt()).isNotNull();
        verify(chatAdminLogService).log(
                eq(chatId),
                eq(requesterId),
                eq(senderId),
                eq("MESSAGE_DELETED"),
                anyString(),
                eq(messageId),
                eq(null)
        );
        verify(userSyncService).recordForUsers(
                eq(List.of(requesterId)),
                eq("MESSAGE_DELETED"),
                eq("MESSAGE"),
                eq(messageId),
                eq(chatId),
                any()
        );
        verify(chatService).reconcileUnreadState(chatId);
    }

    @Test
    void editMessageUpdatesCaptionAndDisablesLinkPreview() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "DIRECT");

        MessageLookupEntity lookup = new MessageLookupEntity();
        lookup.setMessageId(messageId);
        lookup.setChatId(chatId);
        lookup.setSenderId(senderId);
        lookup.setCiphertext("cipher-original");
        lookup.setNonce("nonce-original");
        lookup.setKeyVersion(1);
        lookup.setAttachmentIds(List.of(attachmentId));
        lookup.setCreatedAt(Instant.parse("2026-03-24T11:10:00Z"));
        lookup.setDeliveryStatus("DELIVERED");

        MessageTextContent existingContent = new MessageTextContent(
                "Old caption",
                List.of(),
                null,
                "Old caption",
                null,
                null,
                null,
                null,
                false,
                false
        );
        MessageTextContent updatedContent = new MessageTextContent(
                "Updated caption",
                List.of(new MessageTextEntityPayload("BOLD", 0, 7, null, null)),
                null,
                "Updated caption",
                null,
                null,
                null,
                null,
                false,
                true
        );

        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(lookup));
        when(chatService.getOwnedChat(senderId, chatId)).thenReturn(chat);
        when(chatService.getRecipientIds(chat, senderId)).thenReturn(List.of(recipientId));
        when(chatEncryptionService.decrypt(chatId, "cipher-original", "nonce-original", 1)).thenReturn("decoded-original");
        when(messageContentCodec.decode("decoded-original")).thenReturn(existingContent);
        when(messageContentCodec.normalize(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(updatedContent);
        when(messageContentCodec.encode(updatedContent)).thenReturn("encoded-updated");
        when(chatEncryptionService.encrypt(chatId, "encoded-updated"))
                .thenReturn(new EncryptedPayload("cipher-updated", "nonce-updated", 2));
        when(chatEncryptionService.decrypt(chatId, "cipher-updated", "nonce-updated", 2)).thenReturn("decoded-updated");
        when(messageContentCodec.decode("decoded-updated")).thenReturn(updatedContent);
        when(attachmentService.getResponses(senderId, List.of(attachmentId)))
                .thenReturn(List.of(attachment(attachmentId, "IMAGE")));

        ChatMessageResponse response = messageService.editMessage(
                senderId,
                messageId,
                new EditMessageRequest(
                        null,
                        "Updated caption",
                        List.of(new MessageTextEntityPayload("BOLD", 0, 7, null, null)),
                        true
                )
        );

        assertThat(lookup.getEditedAt()).isNotNull();
        assertThat(response.text()).isEqualTo("Updated caption");
        assertThat(response.caption()).isEqualTo("Updated caption");
        assertThat(response.disableLinkPreview()).isTrue();
        verify(messageTranslationCacheRepository).deleteByMessageId(messageId);
    }

    @Test
    void updateLiveLocationRejectsMessagesOwnedByAnotherSender() {
        UUID requesterId = UUID.randomUUID();
        UUID actualSenderId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "DIRECT");
        MessageLookupEntity lookup = new MessageLookupEntity();
        lookup.setMessageId(messageId);
        lookup.setChatId(chatId);
        lookup.setSenderId(actualSenderId);

        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(lookup));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);

        assertThatThrownBy(() -> messageService.updateLiveLocation(
                requesterId,
                messageId,
                new UpdateLiveLocationRequest(54.1, 27.7, "Updated point", "New address")
        ))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .extracting(throwable -> ((org.springframework.web.server.ResponseStatusException) throwable).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(messageLiveLocationService, never()).update(any(MessageLookupEntity.class), any(UpdateLiveLocationRequest.class));
        verify(chatMessagePublisher, never()).publish(any(MessageEvent.class));
    }

    @Test
    void sendMessageRejectsWhenThrottleExceeded() {
        UUID senderId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatEntity chat = chat(chatId, "GROUP");

        when(messageContentCodec.normalize(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new MessageTextContent("hello", List.of()));
        when(chatService.getOwnedChat(senderId, chatId)).thenReturn(chat);
        when(messageContentCodec.encode(any(MessageTextContent.class))).thenReturn("encoded-message");
        when(chatEncryptionService.encrypt(chatId, "encoded-message"))
                .thenReturn(new EncryptedPayload("cipher-send", "nonce-send", 1));
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many messages created recently"))
                .when(abuseProtectionService)
                .assertMessageSendAllowed(senderId, chatId);

        assertThatThrownBy(() -> messageService.sendMessage(
                senderId,
                new SendMessageRequest(
                        chatId,
                        null,
                        null,
                        null,
                        "hello",
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        false,
                        null,
                        null
                )
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                );

        verify(messageStorageService, never()).save(any(MessageLookupEntity.class));
    }

    @Test
    void reportMessageRejectsWhenThrottleExceeded() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatEntity chat = chat(chatId, "GROUP");
        MessageLookupEntity lookup = new MessageLookupEntity();
        lookup.setMessageId(messageId);
        lookup.setChatId(chatId);
        lookup.setSenderId(UUID.randomUUID());

        when(messageLookupRepository.findById(messageId)).thenReturn(Optional.of(lookup));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many message reports submitted recently"))
                .when(abuseProtectionService)
                .assertMessageReportAllowed(requesterId);

        assertThatThrownBy(() -> messageService.reportMessage(
                requesterId,
                messageId,
                new com.alex.messenger.message.dto.ReportMessageRequest("spam", "bulk abuse")
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                );

        verify(messageReportRepository, never()).save(any(MessageReportEntity.class));
    }

    private ChatEntity chat(UUID chatId, String chatType) {
        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType(chatType);
        chat.setTitle("Chat");
        return chat;
    }

    private MessageTextContent liveLocationContent(MessageLiveLocationPayload payload) {
        return new MessageTextContent(
                "",
                List.of(),
                "LIVE_LOCATION",
                null,
                null,
                payload,
                null,
                null,
                false
        );
    }

    private MessageEntity message(UUID chatId, UUID messageId, UUID attachmentId, Instant createdAt) {
        MessageEntity message = new MessageEntity();
        message.setKey(new MessagePrimaryKey(chatId, messageId));
        message.setCreatedAt(createdAt);
        message.setSenderId(UUID.randomUUID());
        message.setCiphertext("ciphertext");
        message.setNonce("nonce");
        message.setKeyVersion(1);
        message.setAttachmentIds(List.of(attachmentId));
        message.setDeliveryStatus("READ");
        return message;
    }

    private MessageAttachmentResponse attachment(UUID attachmentId, String kind) {
        return new MessageAttachmentResponse(
                attachmentId,
                "file.bin",
                "application/octet-stream",
                kind,
                128,
                null,
                "https://cdn.example.test/download",
                null,
                null,
                null,
                null,
                List.of(),
                Instant.parse("2026-03-14T11:30:00Z"),
                false,
                false,
                false,
                false,
                null,
                null,
                "APPROVED",
                null,
                false,
                false,
                null,
                null,
                null,
                false
        );
    }
}
