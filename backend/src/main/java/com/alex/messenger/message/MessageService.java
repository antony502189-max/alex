package com.alex.messenger.message;

import com.alex.messenger.attachment.AttachmentService;
import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.bot.BotService;
import com.alex.messenger.bot.BotUpdateService;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatAdminLogService;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.forum.ForumTopicEntity;
import com.alex.messenger.chat.forum.ForumTopicService;
import com.alex.messenger.crypto.ChatEncryptionService;
import com.alex.messenger.crypto.EncryptedPayload;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.CreateRepeatingMessageRequest;
import com.alex.messenger.message.dto.CreatePollMessageRequest;
import com.alex.messenger.message.dto.EditMessageRequest;
import com.alex.messenger.message.dto.ForwardMessageRequest;
import com.alex.messenger.message.dto.MessageAttachmentResponse;
import com.alex.messenger.message.dto.MessageContactCardPayload;
import com.alex.messenger.message.dto.MessageLiveLocationPayload;
import com.alex.messenger.message.dto.MessageLocationPayload;
import com.alex.messenger.message.dto.MessageReactionSummary;
import com.alex.messenger.message.dto.MessageServicePayload;
import com.alex.messenger.message.dto.MessageTextEntityPayload;
import com.alex.messenger.message.dto.RepeatingMessageResponse;
import com.alex.messenger.message.dto.ScheduleMessageRequest;
import com.alex.messenger.message.dto.ScheduledMessageResponse;
import com.alex.messenger.message.dto.SearchMessagesResponse;
import com.alex.messenger.message.dto.SendMessageRequest;
import com.alex.messenger.message.dto.SendInlineBotResultRequest;
import com.alex.messenger.message.dto.UpdateLiveLocationRequest;
import com.alex.messenger.message.dto.VotePollRequest;
import com.alex.messenger.message.idempotency.MessageIdempotencyService;
import com.alex.messenger.message.expiration.MessageExpirationEntity;
import com.alex.messenger.message.expiration.MessageExpirationRepository;
import com.alex.messenger.message.repeating.RepeatingMessageRuleEntity;
import com.alex.messenger.message.repeating.RepeatingMessageRuleRepository;
import com.alex.messenger.message.scheduled.ScheduledMessageEntity;
import com.alex.messenger.message.scheduled.ScheduledMessageRepository;
import com.alex.messenger.poll.PollEntity;
import com.alex.messenger.poll.PollService;
import com.alex.messenger.search.PublicPostSearchService;
import com.alex.messenger.shared.SearchQueryValidationSupport;
import com.alex.messenger.sticker.StickerService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MessageService {

    private record ResolvedReadScope(
            ForumTopicEntity topic,
            UUID threadRootMessageId
    ) {
    }

    private record VisibleMessageReferences(
            UUID replyToMessageId,
            UUID threadRootMessageId,
            UUID discussionChatId,
            UUID discussionRootMessageId,
            UUID forwardedFromChatId,
            UUID forwardedFromMessageId
    ) {
    }

    private final MessageRepository messageRepository;
    private final MessageTopicRepository messageTopicRepository;
    private final MessageThreadRepository messageThreadRepository;
    private final MessageLookupRepository messageLookupRepository;
    private final MessageReactionService messageReactionService;
    private final MessageExpirationRepository messageExpirationRepository;
    private final ScheduledMessageRepository scheduledMessageRepository;
    private final RepeatingMessageRuleRepository repeatingMessageRuleRepository;
    private final MessageStorageService messageStorageService;
    private final AttachmentService attachmentService;
    private final ChatAdminLogService chatAdminLogService;
    private final ChatService chatService;
    private final ForumTopicService forumTopicService;
    private final MessageLiveLocationService messageLiveLocationService;
    private final ChatEncryptionService chatEncryptionService;
    private final MessageContentCodec messageContentCodec;
    private final MessageSearchCorpusService messageSearchCorpusService;
    private final MessageTranslationCacheRepository messageTranslationCacheRepository;
    private final MessageIdempotencyService messageIdempotencyService;
    private final PollService pollService;
    private final StickerService stickerService;
    private final PublicPostSearchService publicPostSearchService;
    private final ChatMessagePublisher chatMessagePublisher;
    private final BotService botService;
    private final BotUpdateService botUpdateService;
    private final UserSessionService userSessionService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public ChatMessageResponse sendMessage(UUID senderId, SendMessageRequest request) {
        List<UUID> attachmentIds = normalizeAttachmentIds(request.attachmentIds());
        MessageTextContent content = buildUserMessageContent(
                request.text(),
                request.caption(),
                request.entities(),
                request.messageType(),
                request.location(),
                request.liveLocation(),
                request.contactCard(),
                request.silent(),
                attachmentIds,
                request.stickerId()
        );
        if (isMessageEmpty(content, attachmentIds, request.stickerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must contain text, attachments or a sticker");
        }

        ChatEntity chat = resolveTargetChat(senderId, request.chatId(), request.recipientUserId());
        chatService.ensureCanPost(chat, senderId);
        ForumTopicEntity topic = forumTopicService.resolveTopicForWrite(chat, senderId, request.topicId());
        MessageLookupEntity replyTarget = resolveReplyTarget(chat.getId(), topic != null ? topic.getId() : null, request.replyToMessageId());
        ensureCommentsAllowedForReply(chat, replyTarget);
        attachmentService.assertUsableAttachments(senderId, chat.getId(), attachmentIds);
        stickerService.assertStickerExists(request.stickerId());

        MessageLookupEntity lookup = buildNewMessage(
                chat,
                senderId,
                content,
                topic != null ? topic.getId() : null,
                request.replyToMessageId(),
                null,
                null,
                null,
                request.stickerId(),
                attachmentIds
        );
        applyThreadMetadata(lookup, replyTarget, null, null);
        if (request.clientMessageId() != null) {
            MessageIdempotencyService.Reservation reservation = messageIdempotencyService.reserve(
                    senderId,
                    chat.getId(),
                    request.clientMessageId(),
                    lookup.getMessageId()
            );
            if (!reservation.proceed()) {
                MessageLookupEntity existing = reservation.existingMessage();
                return toResponse(
                        senderId,
                        existing,
                        messageReactionService.getSummaries(existing.getMessageId()),
                        getAttachmentResponses(senderId, existing.getAttachmentIds()),
                        request.clientMessageId()
                );
            }
        }
        List<UUID> recipientIds = chatService.getRecipientIds(chat, senderId);
        content = activateLiveLocationIfNeeded(lookup, content);

        persistMessage(lookup);
        chatService.recordMessageSent(chat.getId(), senderId, lookup.getCreatedAt());
        linkDiscussionThreadIfNeeded(chat, senderId, lookup);
        chatService.updateLastMessageAt(chat, lookup.getCreatedAt());
        forumTopicService.touchTopic(lookup.getTopicId(), lookup.getCreatedAt());
        chatService.incrementUnreadCounts(
                chat.getId(),
                senderId,
                content,
                replyTarget != null ? replyTarget.getSenderId() : null,
                lookup.getTopicId()
        );
        publish(lookup, recipientIds, request.clientMessageId());
        botUpdateService.maybeEnqueueIncomingMessage(chat, senderId, lookup);
        botService.maybeReplyToDirectMessage(chat, senderId, lookup);
        publishDirectMessageCreatedEvent(chat, senderId, lookup);
        if (request.clientMessageId() != null) {
            messageIdempotencyService.markCompleted(senderId, request.clientMessageId(), lookup.getMessageId());
        }

        return toResponse(senderId, lookup, List.of(), getAttachmentResponses(senderId, attachmentIds), request.clientMessageId());
    }

    @Transactional
    public ChatMessageResponse sendInternalServiceMessage(UUID senderId, UUID chatId, String serviceType, String text) {
        ChatEntity chat = chatService.getOwnedChat(senderId, chatId);
        chatService.ensureCanPost(chat, senderId);

        MessageTextContent content = messageContentCodec.normalize(
                "",
                List.of(),
                "SERVICE_MESSAGE",
                null,
                null,
                null,
                new MessageServicePayload(serviceType, text),
                false
        );
        MessageLookupEntity lookup = buildNewMessage(
                chat,
                senderId,
                content,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
        List<UUID> recipientIds = chatService.getRecipientIds(chat, senderId);

        persistMessage(lookup);
        chatService.recordMessageSent(chat.getId(), senderId, lookup.getCreatedAt());
        linkDiscussionThreadIfNeeded(chat, senderId, lookup);
        chatService.updateLastMessageAt(chat, lookup.getCreatedAt());
        chatService.incrementUnreadCounts(chat.getId(), senderId, content, null);
        publish(lookup, recipientIds);

        return toResponse(senderId, lookup, List.of(), List.of());
    }

    @Transactional
    public ChatMessageResponse sendAutomatedBusinessMessage(UUID senderId, UUID chatId, String text) {
        String normalizedText = text != null ? text.trim() : "";
        if (normalizedText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Automated business message text is blank");
        }

        ChatEntity chat = chatService.getOwnedChat(senderId, chatId);
        chatService.ensureCanPost(chat, senderId);
        MessageTextContent content = messageContentCodec.plain(normalizedText);
        MessageLookupEntity lookup = buildNewMessage(
                chat,
                senderId,
                content,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
        List<UUID> recipientIds = chatService.getRecipientIds(chat, senderId);

        persistMessage(lookup);
        chatService.recordMessageSent(chat.getId(), senderId, lookup.getCreatedAt());
        linkDiscussionThreadIfNeeded(chat, senderId, lookup);
        chatService.updateLastMessageAt(chat, lookup.getCreatedAt());
        chatService.incrementUnreadCounts(chat.getId(), senderId, content, null);
        publish(lookup, recipientIds);

        return toResponse(senderId, lookup, List.of(), List.of());
    }

    @Transactional
    public ChatMessageResponse sendInlineBotResult(UUID senderId, SendInlineBotResultRequest request) {
        BotService.InlineBotSelection selection = botService.resolveInlineResult(
                request.botUsername(),
                request.resultId(),
                request.query()
        );

        ChatEntity chat = resolveTargetChat(senderId, request.chatId(), request.recipientUserId());
        chatService.ensureCanPost(chat, senderId);
        ForumTopicEntity topic = forumTopicService.resolveTopicForWrite(chat, senderId, request.topicId());
        MessageLookupEntity replyTarget = resolveReplyTarget(
                chat.getId(),
                topic != null ? topic.getId() : null,
                request.replyToMessageId()
        );
        ensureCommentsAllowedForReply(chat, replyTarget);

        UUID reservedMessageId = request.clientMessageId() != null ? Uuids.timeBased() : null;
        if (request.clientMessageId() != null) {
            MessageIdempotencyService.Reservation reservation = messageIdempotencyService.reserve(
                    senderId,
                    chat.getId(),
                    request.clientMessageId(),
                    reservedMessageId
            );
            if (!reservation.proceed()) {
                MessageLookupEntity existing = reservation.existingMessage();
                return toResponse(
                        senderId,
                        existing,
                        messageReactionService.getSummaries(existing.getMessageId()),
                        getAttachmentResponses(senderId, existing.getAttachmentIds()),
                        request.clientMessageId()
                );
            }
        }

        MessageLookupEntity lookup = buildNewMessage(
                chat,
                senderId,
                messageContentCodec.plain(selection.text()),
                topic != null ? topic.getId() : null,
                request.replyToMessageId(),
                null,
                null,
                null,
                null,
                List.of(),
                reservedMessageId
        );
        lookup.setViaBotUserId(selection.botUserId());
        applyThreadMetadata(lookup, replyTarget, null, null);
        List<UUID> recipientIds = chatService.getRecipientIds(chat, senderId);

        persistMessage(lookup);
        chatService.recordMessageSent(chat.getId(), senderId, lookup.getCreatedAt());
        linkDiscussionThreadIfNeeded(chat, senderId, lookup);
        chatService.updateLastMessageAt(chat, lookup.getCreatedAt());
        forumTopicService.touchTopic(lookup.getTopicId(), lookup.getCreatedAt());
        chatService.incrementUnreadCounts(
                chat.getId(),
                senderId,
                messageContentCodec.plain(selection.text()),
                replyTarget != null ? replyTarget.getSenderId() : null,
                lookup.getTopicId()
        );
        publish(lookup, recipientIds, request.clientMessageId());
        if (request.clientMessageId() != null) {
            messageIdempotencyService.markCompleted(senderId, request.clientMessageId(), lookup.getMessageId());
        }
        publishDirectMessageCreatedEvent(chat, senderId, lookup);

        return toResponse(senderId, lookup, List.of(), List.of(), request.clientMessageId());
    }

    @Transactional
    public ScheduledMessageResponse scheduleMessage(UUID senderId, ScheduleMessageRequest request) {
        List<UUID> attachmentIds = normalizeAttachmentIds(request.attachmentIds());
        MessageTextContent content = buildUserMessageContent(
                request.text(),
                request.caption(),
                request.entities(),
                request.messageType(),
                request.location(),
                request.liveLocation(),
                request.contactCard(),
                request.silent(),
                attachmentIds,
                request.stickerId()
        );
        if (isMessageEmpty(content, attachmentIds, request.stickerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must contain text, attachments or a sticker");
        }

        ChatEntity chat = resolveTargetChat(senderId, request.chatId(), request.recipientUserId());
        chatService.ensureCanPost(chat, senderId);
        ForumTopicEntity topic = forumTopicService.resolveTopicForWrite(chat, senderId, request.topicId());
        MessageLookupEntity replyTarget = resolveReplyTarget(chat.getId(), topic != null ? topic.getId() : null, request.replyToMessageId());
        ensureCommentsAllowedForReply(chat, replyTarget);
        attachmentService.assertUsableAttachments(senderId, chat.getId(), attachmentIds);
        stickerService.assertStickerExists(request.stickerId());

        EncryptedPayload encryptedPayload = chatEncryptionService.encrypt(
                chat.getId(),
                messageContentCodec.encode(content)
        );
        ScheduledMessageEntity scheduledMessage = buildScheduledMessageEntity(
                chat,
                senderId,
                topic != null ? topic.getId() : null,
                replyTarget,
                request.replyToMessageId(),
                request.stickerId(),
                attachmentIds,
                encryptedPayload,
                request.scheduledAt(),
                "PENDING",
                request.clientMessageId()
        );

        return saveScheduledMessage(senderId, request.clientMessageId(), chat.getId(), scheduledMessage);
    }

    @Transactional
    public RepeatingMessageResponse scheduleRepeatingMessage(UUID senderId, CreateRepeatingMessageRequest request) {
        List<UUID> attachmentIds = normalizeAttachmentIds(request.attachmentIds());
        MessageTextContent content = buildUserMessageContent(
                request.text(),
                request.caption(),
                request.entities(),
                request.messageType(),
                request.location(),
                request.liveLocation(),
                request.contactCard(),
                request.silent(),
                attachmentIds,
                request.stickerId()
        );
        if (isMessageEmpty(content, attachmentIds, request.stickerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must contain text, attachments or a sticker");
        }

        ChatEntity chat = resolveTargetChat(senderId, request.chatId(), request.recipientUserId());
        chatService.ensureCanPost(chat, senderId);
        ForumTopicEntity topic = forumTopicService.resolveTopicForWrite(chat, senderId, request.topicId());
        MessageLookupEntity replyTarget = resolveReplyTarget(chat.getId(), topic != null ? topic.getId() : null, request.replyToMessageId());
        ensureCommentsAllowedForReply(chat, replyTarget);
        attachmentService.assertUsableAttachments(senderId, chat.getId(), attachmentIds);
        stickerService.assertStickerExists(request.stickerId());

        UUID clientRuleId = request.clientRuleId();
        if (clientRuleId != null) {
            RepeatingMessageRuleEntity existing = repeatingMessageRuleRepository
                    .findBySenderIdAndClientRuleId(senderId, clientRuleId)
                    .orElse(null);
            if (existing != null) {
                if (!existing.getChatId().equals(chat.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "clientRuleId is already bound to another chat");
                }
                return toRepeatingResponse(existing);
            }
        }

        EncryptedPayload encryptedPayload = chatEncryptionService.encrypt(
                chat.getId(),
                messageContentCodec.encode(content)
        );
        RepeatingMessageRuleEntity rule = new RepeatingMessageRuleEntity();
        rule.setChatId(chat.getId());
        rule.setSenderId(senderId);
        rule.setClientRuleId(clientRuleId);
        rule.setTopicId(topic != null ? topic.getId() : null);
        rule.setThreadRootMessageId(resolveThreadRootMessageId(replyTarget));
        rule.setDiscussionChatId(resolveDiscussionChatId(chat, replyTarget));
        rule.setDiscussionRootMessageId(resolveDiscussionRootMessageId(replyTarget));
        rule.setCiphertext(encryptedPayload.ciphertext());
        rule.setNonce(encryptedPayload.nonce());
        rule.setKeyVersion(encryptedPayload.keyVersion());
        rule.setReplyToMessageId(request.replyToMessageId());
        rule.setStickerId(request.stickerId());
        rule.setAttachmentIds(encodeAttachmentIds(attachmentIds));
        rule.setIntervalMinutes(normalizeRepeatingIntervalMinutes(request.intervalMinutes()));
        rule.setMaxOccurrences(normalizeRepeatingMaxOccurrences(request.maxOccurrences()));
        rule.setNextScheduledAt(request.firstScheduledAt());
        rule.setStatus("ACTIVE");

        try {
            RepeatingMessageRuleEntity savedRule = repeatingMessageRuleRepository.save(rule);
            materializeNextRepeatingOccurrence(savedRule);
            return toRepeatingResponse(repeatingMessageRuleRepository.save(savedRule));
        } catch (DataIntegrityViolationException duplicateRuleRace) {
            if (clientRuleId == null) {
                throw duplicateRuleRace;
            }
            RepeatingMessageRuleEntity existing = repeatingMessageRuleRepository
                    .findBySenderIdAndClientRuleId(senderId, clientRuleId)
                    .orElseThrow(() -> duplicateRuleRace);
            if (!existing.getChatId().equals(chat.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "clientRuleId is already bound to another chat");
            }
            return toRepeatingResponse(existing);
        }
    }

    @Transactional
    public ScheduledMessageResponse sendWhenOnline(UUID senderId, SendMessageRequest request) {
        List<UUID> attachmentIds = normalizeAttachmentIds(request.attachmentIds());
        MessageTextContent content = buildUserMessageContent(
                request.text(),
                request.caption(),
                request.entities(),
                request.messageType(),
                request.location(),
                request.liveLocation(),
                request.contactCard(),
                request.silent(),
                attachmentIds,
                request.stickerId()
        );
        if (isMessageEmpty(content, attachmentIds, request.stickerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must contain text, attachments or a sticker");
        }

        ChatEntity chat = resolveTargetChat(senderId, request.chatId(), request.recipientUserId());
        if (!"DIRECT".equals(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Send when online is available only for direct chats");
        }
        chatService.ensureCanPost(chat, senderId);
        ForumTopicEntity topic = forumTopicService.resolveTopicForWrite(chat, senderId, request.topicId());
        MessageLookupEntity replyTarget = resolveReplyTarget(chat.getId(), topic != null ? topic.getId() : null, request.replyToMessageId());
        ensureCommentsAllowedForReply(chat, replyTarget);
        attachmentService.assertUsableAttachments(senderId, chat.getId(), attachmentIds);
        stickerService.assertStickerExists(request.stickerId());

        EncryptedPayload encryptedPayload = chatEncryptionService.encrypt(
                chat.getId(),
                messageContentCodec.encode(content)
        );
        ScheduledMessageEntity waitingMessage = buildScheduledMessageEntity(
                chat,
                senderId,
                topic != null ? topic.getId() : null,
                replyTarget,
                request.replyToMessageId(),
                request.stickerId(),
                attachmentIds,
                encryptedPayload,
                Instant.now(),
                "WAITING_ONLINE",
                request.clientMessageId()
        );
        return saveScheduledMessage(senderId, request.clientMessageId(), chat.getId(), waitingMessage);
    }

    @Transactional(readOnly = true)
    public List<ScheduledMessageResponse> getScheduledMessages(
            UUID requesterId,
            UUID chatId,
            UUID topicId,
            UUID threadRootMessageId
    ) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        ResolvedReadScope readScope = resolveReadScope(chat, requesterId, topicId, threadRootMessageId);
        return scheduledMessageRepository.findAllBySenderIdAndChatIdAndStatusInOrderByScheduledAtAsc(
                        requesterId,
                        chatId,
                        List.of("PENDING", "WAITING_ONLINE")
                ).stream()
                .filter(message -> Objects.equals(message.getTopicId(), readScope.topic() != null ? readScope.topic().getId() : null))
                .filter(message -> Objects.equals(message.getThreadRootMessageId(), readScope.threadRootMessageId()))
                .map(message -> toScheduledResponse(requesterId, message))
                .toList();
    }

    @Transactional
    public void cancelScheduledMessage(UUID requesterId, UUID scheduledMessageId) {
        ScheduledMessageEntity scheduledMessage = scheduledMessageRepository.findByIdAndSenderId(scheduledMessageId, requesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scheduled message not found"));
        chatService.getOwnedChat(requesterId, scheduledMessage.getChatId());
        if (!List.of("PENDING", "WAITING_ONLINE").contains(scheduledMessage.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Scheduled message can no longer be canceled");
        }
        scheduledMessage.setStatus("CANCELED");
        scheduledMessageRepository.save(scheduledMessage);
        cancelRepeatingRuleIfNeeded(requesterId, scheduledMessage);
    }

    @Transactional
    public List<ScheduledMessageEntity> lockDueScheduledMessages(Instant now, int batchSize) {
        return scheduledMessageRepository.lockDuePendingMessages(now, batchSize);
    }

    @Transactional
    public List<ScheduledMessageEntity> lockWaitingForOnlineMessages(int batchSize) {
        return scheduledMessageRepository.lockWaitingForOnlineMessages(batchSize);
    }

    @Transactional
    public void dispatchWhenRecipientOnline(ScheduledMessageEntity scheduledMessage) {
        if (!"WAITING_ONLINE".equals(scheduledMessage.getStatus())) {
            return;
        }

        ChatEntity chat = chatService.getOwnedChat(scheduledMessage.getSenderId(), scheduledMessage.getChatId());
        if (!"DIRECT".equals(chat.getChatType())) {
            scheduledMessage.setStatus("FAILED");
            scheduledMessage.setErrorMessage("Send when online is available only for direct chats");
            scheduledMessageRepository.save(scheduledMessage);
            return;
        }

        UUID recipientId = chatService.getRecipientIds(chat, scheduledMessage.getSenderId()).stream()
                .findFirst()
                .orElse(null);
        if (recipientId == null || !userSessionService.isUserOnline(recipientId)) {
            return;
        }

        dispatchScheduledMessage(scheduledMessage);
    }

    @Transactional
    public void dispatchScheduledMessage(ScheduledMessageEntity scheduledMessage) {
        if (!List.of("PENDING", "WAITING_ONLINE").contains(scheduledMessage.getStatus())) {
            return;
        }

        try {
            ChatEntity chat = chatService.getOwnedChat(scheduledMessage.getSenderId(), scheduledMessage.getChatId());
            chatService.ensureCanPost(chat, scheduledMessage.getSenderId());
            ForumTopicEntity topic = forumTopicService.resolveTopicForWrite(
                    chat,
                    scheduledMessage.getSenderId(),
                    scheduledMessage.getTopicId()
            );
            List<UUID> attachmentIds = parseAttachmentIds(scheduledMessage.getAttachmentIds());
            attachmentService.assertUsableAttachments(scheduledMessage.getSenderId(), chat.getId(), attachmentIds);
            MessageLookupEntity replyTarget = resolveScheduledReplyTargetForDispatch(
                    chat,
                    topic != null ? topic.getId() : null,
                    scheduledMessage
            );
            ensureScheduledCommentPolicyAllowsWrite(chat, replyTarget);

            MessageLookupEntity lookup = buildMessageLookupFromEncrypted(
                    chat,
                    scheduledMessage.getSenderId(),
                    topic != null ? topic.getId() : null,
                    scheduledMessage.getReplyToMessageId(),
                    null,
                    null,
                    null,
                    scheduledMessage.getStickerId(),
                    attachmentIds,
                    scheduledMessage.getCiphertext(),
                    scheduledMessage.getNonce(),
                    scheduledMessage.getKeyVersion()
            );
            applyThreadMetadata(lookup, replyTarget, null, null);
            List<UUID> recipientIds = chatService.getRecipientIds(chat, scheduledMessage.getSenderId());
            MessageTextContent content = activateLiveLocationIfNeeded(lookup, decodeMessageContent(lookup));

            persistMessage(lookup);
            chatService.recordMessageSent(chat.getId(), scheduledMessage.getSenderId(), lookup.getCreatedAt());
            linkDiscussionThreadIfNeeded(chat, scheduledMessage.getSenderId(), lookup);
            chatService.updateLastMessageAt(chat, lookup.getCreatedAt());
            forumTopicService.touchTopic(lookup.getTopicId(), lookup.getCreatedAt());
            chatService.incrementUnreadCounts(
                    chat.getId(),
                    scheduledMessage.getSenderId(),
                    content,
                    resolveReplyTargetSenderId(scheduledMessage.getReplyToMessageId()),
                    lookup.getTopicId()
            );
            publish(lookup, recipientIds);
            botUpdateService.maybeEnqueueIncomingMessage(chat, scheduledMessage.getSenderId(), lookup);
            botService.maybeReplyToDirectMessage(chat, scheduledMessage.getSenderId(), lookup);
            publishDirectMessageCreatedEvent(chat, scheduledMessage.getSenderId(), lookup);

            scheduledMessage.setStatus("DELIVERED");
            scheduledMessage.setDeliveredMessageId(lookup.getMessageId());
            scheduledMessage.setErrorMessage(null);
        } catch (RuntimeException exception) {
            scheduledMessage.setStatus("FAILED");
            scheduledMessage.setErrorMessage(
                    exception.getMessage() != null
                            ? exception.getMessage().substring(0, Math.min(255, exception.getMessage().length()))
                            : "Unknown dispatch error"
            );
        }

        scheduledMessageRepository.save(scheduledMessage);
        advanceRepeatingRuleIfNeeded(scheduledMessage);
    }

    @Transactional
    public int autoDeleteExpiredMessages(Instant now, int batchSize) {
        int processed = 0;
        List<MessageExpirationEntity> expirations = messageExpirationRepository.lockDueExpirations(now, batchSize);
        for (MessageExpirationEntity expiration : expirations) {
            MessageLookupEntity lookup = messageLookupRepository.findById(expiration.getMessageId()).orElse(null);
            if (lookup != null && lookup.getDeletedAt() == null) {
                lookup.setDeletedAt(now);
                persistMessage(lookup);
                publishExpiredDeletion(lookup);
            }
            expiration.setProcessedAt(now);
            messageExpirationRepository.save(expiration);
            processed++;
        }
        return processed;
    }

    @Transactional
    public ChatMessageResponse sendPollMessage(UUID senderId, CreatePollMessageRequest request) {
        ChatEntity chat = resolveTargetChat(senderId, request.chatId(), request.recipientUserId());
        chatService.ensureCanPost(chat, senderId);
        ForumTopicEntity topic = forumTopicService.resolveTopicForWrite(chat, senderId, request.topicId());
        MessageLookupEntity replyTarget = resolveReplyTarget(
                chat.getId(),
                topic != null ? topic.getId() : null,
                request.replyToMessageId()
        );
        ensureCommentsAllowedForReply(chat, replyTarget);

        UUID reservedMessageId = request.clientMessageId() != null ? Uuids.timeBased() : null;
        if (request.clientMessageId() != null) {
            MessageIdempotencyService.Reservation reservation = messageIdempotencyService.reserve(
                    senderId,
                    chat.getId(),
                    request.clientMessageId(),
                    reservedMessageId
            );
            if (!reservation.proceed()) {
                MessageLookupEntity existing = reservation.existingMessage();
                return toResponse(
                        senderId,
                        existing,
                        messageReactionService.getSummaries(existing.getMessageId()),
                        getAttachmentResponses(senderId, existing.getAttachmentIds()),
                        request.clientMessageId()
                );
            }
        }

        PollEntity poll = pollService.createPoll(chat, senderId, request);
        MessageLookupEntity lookup = buildNewMessage(
                chat,
                senderId,
                messageContentCodec.plain(request.question().trim()),
                topic != null ? topic.getId() : null,
                request.replyToMessageId(),
                null,
                null,
                poll.getId(),
                null,
                List.of(),
                reservedMessageId
        );
        applyThreadMetadata(lookup, replyTarget, null, null);
        List<UUID> recipientIds = chatService.getRecipientIds(chat, senderId);

        persistMessage(lookup);
        chatService.recordMessageSent(chat.getId(), senderId, lookup.getCreatedAt());
        linkDiscussionThreadIfNeeded(chat, senderId, lookup);
        chatService.updateLastMessageAt(chat, lookup.getCreatedAt());
        forumTopicService.touchTopic(lookup.getTopicId(), lookup.getCreatedAt());
        chatService.incrementUnreadCounts(
                chat.getId(),
                senderId,
                messageContentCodec.plain(request.question().trim()),
                replyTarget != null ? replyTarget.getSenderId() : null,
                lookup.getTopicId()
        );
        publish(lookup, recipientIds, request.clientMessageId());
        botUpdateService.maybeEnqueueIncomingMessage(chat, senderId, lookup);
        botService.maybeReplyToDirectMessage(chat, senderId, lookup);
        publishDirectMessageCreatedEvent(chat, senderId, lookup);
        if (request.clientMessageId() != null) {
            messageIdempotencyService.markCompleted(senderId, request.clientMessageId(), lookup.getMessageId());
        }
        return toResponse(senderId, lookup, List.of(), List.of(), request.clientMessageId());
    }

    @Transactional
    public ChatMessageResponse forwardMessage(UUID senderId, ForwardMessageRequest request) {
        MessageLookupEntity source = getAccessibleMessage(senderId, request.sourceMessageId());
        if (source.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deleted message cannot be forwarded");
        }

        ChatEntity targetChat = resolveTargetChat(senderId, request.chatId(), request.recipientUserId());
        chatService.ensureCanPost(targetChat, senderId);
        ForumTopicEntity topic = forumTopicService.resolveTopicForWrite(targetChat, senderId, request.topicId());
        MessageLookupEntity replyTarget = resolveReplyTarget(
                targetChat.getId(),
                topic != null ? topic.getId() : null,
                request.replyToMessageId()
        );
        ensureCommentsAllowedForReply(targetChat, replyTarget);

        MessageTextContent sourceContent = messageContentCodec.decode(chatEncryptionService.decrypt(
                source.getChatId(),
                source.getCiphertext(),
                source.getNonce(),
                source.getKeyVersion()
        ));
        MessageTextContent forwardedContent = normalizeForwardedContent(sourceContent);

        UUID reservedMessageId = request.clientMessageId() != null ? Uuids.timeBased() : null;
        if (request.clientMessageId() != null) {
            MessageIdempotencyService.Reservation reservation = messageIdempotencyService.reserve(
                    senderId,
                    targetChat.getId(),
                    request.clientMessageId(),
                    reservedMessageId
            );
            if (!reservation.proceed()) {
                MessageLookupEntity existing = reservation.existingMessage();
                return toResponse(
                        senderId,
                        existing,
                        messageReactionService.getSummaries(existing.getMessageId()),
                        getAttachmentResponses(senderId, existing.getAttachmentIds()),
                        request.clientMessageId()
                );
            }
        }
        List<UUID> forwardedAttachmentIds = attachmentService.cloneAttachmentsToChat(
                senderId,
                targetChat.getId(),
                source.getAttachmentIds() != null ? source.getAttachmentIds() : List.of()
        );

        MessageLookupEntity lookup = buildNewMessage(
                targetChat,
                senderId,
                forwardedContent,
                topic != null ? topic.getId() : null,
                request.replyToMessageId(),
                source.getChatId(),
                source.getMessageId(),
                source.getPollId(),
                source.getStickerId(),
                forwardedAttachmentIds,
                reservedMessageId
        );
        lookup.setViaBotUserId(source.getViaBotUserId());
        applyThreadMetadata(lookup, replyTarget, null, null);
        List<UUID> recipientIds = chatService.getRecipientIds(targetChat, senderId);
        forwardedContent = activateLiveLocationIfNeeded(lookup, forwardedContent);

        persistMessage(lookup);
        chatService.recordMessageSent(targetChat.getId(), senderId, lookup.getCreatedAt());
        linkDiscussionThreadIfNeeded(targetChat, senderId, lookup);
        chatService.updateLastMessageAt(targetChat, lookup.getCreatedAt());
        forumTopicService.touchTopic(lookup.getTopicId(), lookup.getCreatedAt());
        chatService.incrementUnreadCounts(
                targetChat.getId(),
                senderId,
                forwardedContent,
                replyTarget != null ? replyTarget.getSenderId() : null,
                lookup.getTopicId()
        );
        publish(lookup, recipientIds, request.clientMessageId());
        botUpdateService.maybeEnqueueIncomingMessage(targetChat, senderId, lookup);
        botService.maybeReplyToDirectMessage(targetChat, senderId, lookup);
        publishDirectMessageCreatedEvent(targetChat, senderId, lookup);
        if (request.clientMessageId() != null) {
            messageIdempotencyService.markCompleted(senderId, request.clientMessageId(), lookup.getMessageId());
        }
        return toResponse(
                senderId,
                lookup,
                List.of(),
                getAttachmentResponses(senderId, lookup.getAttachmentIds()),
                request.clientMessageId()
        );
    }

    @Transactional
    public ChatMessageResponse editMessage(UUID senderId, UUID messageId, EditMessageRequest request) {
        MessageLookupEntity lookup = getOwnedMessage(senderId, messageId);
        if (lookup.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deleted message cannot be edited");
        }
        if ("LIVE_LOCATION".equals(decodeMessageContent(lookup).messageType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use live location update endpoints for this message");
        }

        MessageTextContent content = messageContentCodec.normalize(request.text().trim(), request.entities());
        if (content.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message text is blank");
        }

        EncryptedPayload encryptedPayload = chatEncryptionService.encrypt(
                lookup.getChatId(),
                messageContentCodec.encode(content)
        );
        lookup.setCiphertext(encryptedPayload.ciphertext());
        lookup.setNonce(encryptedPayload.nonce());
        lookup.setKeyVersion(encryptedPayload.keyVersion());
        lookup.setEditedAt(Instant.now());
        messageTranslationCacheRepository.deleteByMessageId(messageId);

        persistMessage(lookup);
        publishToChatMembers(senderId, lookup);
        chatAdminLogService.log(
                lookup.getChatId(),
                senderId,
                null,
                "MESSAGE_EDITED",
                "Edited a message",
                messageId,
                null
        );
        botUpdateService.maybeEnqueueMessageEdited(chatService.getOwnedChat(senderId, lookup.getChatId()), senderId, lookup);
        return toResponse(
                senderId,
                lookup,
                messageReactionService.getSummaries(lookup.getMessageId()),
                getAttachmentResponses(senderId, lookup.getAttachmentIds())
        );
    }

    @Transactional
    public ChatMessageResponse updateLiveLocation(UUID senderId, UUID messageId, UpdateLiveLocationRequest request) {
        MessageLookupEntity lookup = getOwnedMessage(senderId, messageId);
        if (lookup.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deleted message cannot be updated");
        }

        MessageTextContent content = requireLiveLocationContent(lookup);
        MessageLiveLocationPayload liveLocation = messageLiveLocationService.update(lookup, request);
        syncLiveLocationState(lookup, content, liveLocation);
        lookup.setEditedAt(Instant.now());

        persistMessage(lookup);
        publishToChatMembers(senderId, lookup);
        botUpdateService.maybeEnqueueMessageEdited(chatService.getOwnedChat(senderId, lookup.getChatId()), senderId, lookup);
        return toResponse(
                senderId,
                lookup,
                messageReactionService.getSummaries(lookup.getMessageId()),
                getAttachmentResponses(senderId, lookup.getAttachmentIds())
        );
    }

    @Transactional
    public ChatMessageResponse stopLiveLocation(UUID senderId, UUID messageId) {
        MessageLookupEntity lookup = getOwnedMessage(senderId, messageId);
        if (lookup.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deleted message cannot be updated");
        }

        MessageTextContent content = requireLiveLocationContent(lookup);
        MessageLiveLocationPayload liveLocation = messageLiveLocationService.stop(lookup);
        syncLiveLocationState(lookup, content, liveLocation);
        lookup.setEditedAt(Instant.now());

        persistMessage(lookup);
        publishToChatMembers(senderId, lookup);
        botUpdateService.maybeEnqueueMessageEdited(chatService.getOwnedChat(senderId, lookup.getChatId()), senderId, lookup);
        return toResponse(
                senderId,
                lookup,
                messageReactionService.getSummaries(lookup.getMessageId()),
                getAttachmentResponses(senderId, lookup.getAttachmentIds())
        );
    }

    @Transactional
    public ChatMessageResponse deleteMessage(UUID senderId, UUID messageId) {
        MessageLookupEntity lookup = getOwnedMessage(senderId, messageId);
        if (lookup.getDeletedAt() == null) {
            MessageTextContent content = decodeMessageContent(lookup);
            if ("LIVE_LOCATION".equals(content.messageType()) && content.liveLocation() != null) {
                try {
                    messageLiveLocationService.stop(lookup);
                } catch (ResponseStatusException ignored) {
                    // Deletion should still succeed even if the live-location state is already gone.
                }
            }
            lookup.setDeletedAt(Instant.now());
            persistMessage(lookup);
            publishToChatMembers(senderId, lookup);
            chatAdminLogService.log(
                    lookup.getChatId(),
                    senderId,
                    null,
                    "MESSAGE_DELETED",
                    "Deleted a message",
                    messageId,
                null
            );
        }
        botUpdateService.maybeEnqueueMessageDeleted(chatService.getOwnedChat(senderId, lookup.getChatId()), senderId, lookup);
        return toResponse(
                senderId,
                lookup,
                messageReactionService.getSummaries(lookup.getMessageId()),
                getAttachmentResponses(senderId, lookup.getAttachmentIds())
        );
    }

    @Transactional
    public ChatMessageResponse toggleReaction(UUID requesterId, UUID messageId, String emoji) {
        MessageLookupEntity lookup = getAccessibleMessage(requesterId, messageId);
        ensureReactionsAllowed(lookup);
        messageReactionService.toggle(messageId, requesterId, emoji.trim());
        publishToChatMembers(requesterId, lookup);
        return toResponse(
                requesterId,
                lookup,
                messageReactionService.getSummaries(lookup.getMessageId()),
                getAttachmentResponses(requesterId, lookup.getAttachmentIds())
        );
    }

    @Transactional
    public ChatMessageResponse votePoll(UUID requesterId, UUID messageId, VotePollRequest request) {
        MessageLookupEntity lookup = getAccessibleMessage(requesterId, messageId);
        if (lookup.getPollId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is not a poll");
        }

        pollService.vote(requesterId, lookup.getPollId(), request.optionIds());
        publishToChatMembers(requesterId, lookup);
        return toResponse(
                requesterId,
                lookup,
                messageReactionService.getSummaries(lookup.getMessageId()),
                getAttachmentResponses(requesterId, lookup.getAttachmentIds())
        );
    }

    @Transactional
    public ChatMessageResponse closePoll(UUID requesterId, UUID messageId) {
        MessageLookupEntity lookup = getAccessibleMessage(requesterId, messageId);
        if (lookup.getPollId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is not a poll");
        }

        PollEntity poll = pollService.getPollEntity(lookup.getPollId());
        ensureCanClosePoll(requesterId, lookup, poll);
        pollService.closePoll(lookup.getPollId(), requesterId);
        publishToChatMembers(requesterId, lookup);
        return toResponse(
                requesterId,
                lookup,
                messageReactionService.getSummaries(lookup.getMessageId()),
                getAttachmentResponses(requesterId, lookup.getAttachmentIds())
        );
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getHistory(
            UUID requesterId,
            UUID chatId,
            UUID topicId,
            UUID threadRootMessageId,
            Instant before,
            int limit
    ) {
        int normalizedLimit = requireLimit(limit, 100);
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        ResolvedReadScope readScope = resolveReadScope(chat, requesterId, topicId, threadRootMessageId);

        if (readScope.threadRootMessageId() != null) {
            List<MessageThreadEntity> messages = new ArrayList<>(before == null
                    ? messageThreadRepository.findRecentByThreadRootMessageId(readScope.threadRootMessageId(), normalizedLimit)
                    : messageThreadRepository.findRecentByThreadRootMessageIdBefore(
                            readScope.threadRootMessageId(),
                            before,
                            normalizedLimit
                    ));
            Collections.reverse(messages);
            Map<UUID, List<MessageReactionSummary>> reactions =
                    messageReactionService.getSummaries(messages.stream().map(message -> message.getKey().getMessageId()).toList());

            return messages.stream()
                    .map(message -> toResponse(
                            requesterId,
                            message,
                            reactions.getOrDefault(message.getKey().getMessageId(), List.of()),
                            getAttachmentResponses(requesterId, message.getAttachmentIds())
                    ))
                    .toList();
        }

        if (readScope.topic() != null) {
            List<MessageTopicEntity> messages = new ArrayList<>(before == null
                    ? messageTopicRepository.findRecentByTopicId(readScope.topic().getId(), normalizedLimit)
                    : messageTopicRepository.findRecentByTopicIdBefore(readScope.topic().getId(), before, normalizedLimit));
            Collections.reverse(messages);
            Map<UUID, List<MessageReactionSummary>> reactions =
                    messageReactionService.getSummaries(messages.stream().map(message -> message.getKey().getMessageId()).toList());

            return messages.stream()
                    .map(message -> toResponse(
                            requesterId,
                            message,
                            reactions.getOrDefault(message.getKey().getMessageId(), List.of()),
                            getAttachmentResponses(requesterId, message.getAttachmentIds())
                    ))
                    .toList();
        }

        List<MessageEntity> messages = new ArrayList<>(before == null
                ? messageRepository.findRecentByChatId(chat.getId(), normalizedLimit)
                : messageRepository.findRecentByChatIdBefore(chat.getId(), before, normalizedLimit));
        Collections.reverse(messages);
        Map<UUID, List<MessageReactionSummary>> reactions =
                messageReactionService.getSummaries(messages.stream().map(message -> message.getKey().getMessageId()).toList());

        return messages.stream()
                .map(message -> toResponse(
                        requesterId,
                        message,
                        reactions.getOrDefault(message.getKey().getMessageId(), List.of()),
                        getAttachmentResponses(requesterId, message.getAttachmentIds())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public SearchMessagesResponse searchMessages(
            UUID requesterId,
            UUID chatId,
            UUID topicId,
            UUID threadRootMessageId,
            String query,
            int limit
    ) {
        int normalizedLimit = requireLimit(limit, 100);
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        ResolvedReadScope readScope = resolveReadScope(chat, requesterId, topicId, threadRootMessageId);
        String normalizedQuery = SearchQueryValidationSupport.normalize(query).toLowerCase();
        if (normalizedQuery.isBlank()) {
            return new SearchMessagesResponse(query, List.of());
        }

        if (readScope.threadRootMessageId() != null) {
            List<MessageThreadEntity> threadMessages = messageThreadRepository.findAllByThreadRootMessageId(readScope.threadRootMessageId()).stream()
                    .filter(message -> message.getDeletedAt() == null)
                    .toList();
            Map<UUID, String> searchCorpora = buildThreadSearchCorpora(threadMessages);
            List<MessageThreadEntity> matches = new ArrayList<>(threadMessages.stream()
                    .filter(message -> searchCorpora
                            .getOrDefault(message.getKey().getMessageId(), "")
                            .contains(normalizedQuery))
                    .sorted(java.util.Comparator.comparing(MessageThreadEntity::getCreatedAt).reversed())
                    .limit(normalizedLimit)
                    .toList());

            Collections.reverse(matches);
            Map<UUID, List<MessageReactionSummary>> reactions =
                    messageReactionService.getSummaries(matches.stream().map(message -> message.getKey().getMessageId()).toList());

            return new SearchMessagesResponse(
                    query,
                    matches.stream()
                            .map(message -> toResponse(
                                    requesterId,
                                    message,
                                    reactions.getOrDefault(message.getKey().getMessageId(), List.of()),
                                    getAttachmentResponses(requesterId, message.getAttachmentIds())
                            ))
                            .toList()
            );
        }

        if (readScope.topic() != null) {
            List<MessageTopicEntity> topicMessages = messageTopicRepository.findAllByTopicId(readScope.topic().getId()).stream()
                    .filter(message -> message.getDeletedAt() == null)
                    .toList();
            Map<UUID, String> searchCorpora = buildTopicSearchCorpora(topicMessages);
            List<MessageTopicEntity> matches = new ArrayList<>(topicMessages.stream()
                    .filter(message -> searchCorpora
                            .getOrDefault(message.getKey().getMessageId(), "")
                            .contains(normalizedQuery))
                    .sorted(java.util.Comparator.comparing(MessageTopicEntity::getCreatedAt).reversed())
                    .limit(normalizedLimit)
                    .toList());

            Collections.reverse(matches);
            Map<UUID, List<MessageReactionSummary>> reactions =
                    messageReactionService.getSummaries(matches.stream().map(message -> message.getKey().getMessageId()).toList());

            return new SearchMessagesResponse(
                    query,
                    matches.stream()
                            .map(message -> toResponse(
                                    requesterId,
                                    message,
                                    reactions.getOrDefault(message.getKey().getMessageId(), List.of()),
                                    getAttachmentResponses(requesterId, message.getAttachmentIds())
                            ))
                            .toList()
            );
        }

        List<MessageEntity> chatMessages = messageRepository.findAllByChatId(chat.getId()).stream()
                .filter(message -> message.getDeletedAt() == null)
                .toList();
        Map<UUID, String> searchCorpora = buildMessageSearchCorpora(chatMessages);
        List<MessageEntity> matches = new ArrayList<>(chatMessages.stream()
                .filter(message -> searchCorpora
                        .getOrDefault(message.getKey().getMessageId(), "")
                        .contains(normalizedQuery))
                .sorted(java.util.Comparator.comparing(MessageEntity::getCreatedAt).reversed())
                .limit(normalizedLimit)
                .toList());

        Collections.reverse(matches);
        Map<UUID, List<MessageReactionSummary>> reactions =
                messageReactionService.getSummaries(matches.stream().map(message -> message.getKey().getMessageId()).toList());

        return new SearchMessagesResponse(
                query,
                matches.stream()
                        .map(message -> toResponse(
                                requesterId,
                                message,
                                reactions.getOrDefault(message.getKey().getMessageId(), List.of()),
                                getAttachmentResponses(requesterId, message.getAttachmentIds())
                        ))
                .toList()
        );
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> searchGlobalMessages(
            UUID requesterId,
            List<UUID> chatIds,
            String query,
            int limit
    ) {
        int normalizedLimit = requireLimit(limit, 50);
        String normalizedQuery = SearchQueryValidationSupport.normalize(query).toLowerCase();
        if (normalizedQuery.isBlank() || chatIds.isEmpty()) {
            return List.of();
        }

        List<UUID> uniqueChatIds = new ArrayList<>(new LinkedHashSet<>(chatIds));
        Map<UUID, ChatEntity> chatsById = new LinkedHashMap<>();
        for (UUID chatId : uniqueChatIds) {
            chatsById.put(chatId, chatService.getOwnedChat(requesterId, chatId));
        }
        List<MessageEntity> candidateMessages = uniqueChatIds.stream()
                .flatMap(chatId -> messageRepository.findAllByChatId(chatId).stream()
                        .filter(message -> isMessageVisibleToRequester(chatsById.get(chatId), requesterId, message.getTopicId())))
                .filter(message -> message.getDeletedAt() == null)
                .toList();
        Map<UUID, String> searchCorpora = buildMessageSearchCorpora(candidateMessages);
        List<MessageEntity> matches = new ArrayList<>(candidateMessages.stream()
                .filter(message -> searchCorpora
                        .getOrDefault(message.getKey().getMessageId(), "")
                        .contains(normalizedQuery))
                .sorted(java.util.Comparator.comparing(MessageEntity::getCreatedAt).reversed())
                .limit(normalizedLimit)
                .toList());

        Collections.reverse(matches);

        Map<UUID, List<MessageReactionSummary>> reactions =
                messageReactionService.getSummaries(matches.stream().map(message -> message.getKey().getMessageId()).toList());

        return matches.stream()
                .map(message -> toResponse(
                        requesterId,
                        message,
                        reactions.getOrDefault(message.getKey().getMessageId(), List.of()),
                        getAttachmentResponses(requesterId, message.getAttachmentIds())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatMessageResponse getMessage(UUID requesterId, UUID messageId) {
        MessageLookupEntity lookup = getAccessibleMessage(requesterId, messageId);
        return toResponse(
                requesterId,
                lookup,
                messageReactionService.getSummaries(lookup.getMessageId()),
                getAttachmentResponses(requesterId, lookup.getAttachmentIds())
        );
    }

    private int requireLimit(int limit, int max) {
        if (limit < 1 || limit > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and " + max);
        }
        return limit;
    }

    private MessageLookupEntity buildNewMessage(
            ChatEntity chat,
            UUID senderId,
            String plaintext,
            UUID topicId,
            UUID replyToMessageId,
            UUID forwardedFromChatId,
            UUID forwardedFromMessageId,
            UUID pollId,
            UUID stickerId,
            List<UUID> attachmentIds
    ) {
        return buildNewMessage(
                chat,
                senderId,
                messageContentCodec.plain(plaintext),
                topicId,
                replyToMessageId,
                forwardedFromChatId,
                forwardedFromMessageId,
                pollId,
                stickerId,
                attachmentIds
        );
    }

    private MessageLookupEntity buildNewMessage(
            ChatEntity chat,
            UUID senderId,
            MessageTextContent content,
            UUID topicId,
            UUID replyToMessageId,
            UUID forwardedFromChatId,
            UUID forwardedFromMessageId,
            UUID pollId,
            UUID stickerId,
            List<UUID> attachmentIds
    ) {
        return buildNewMessage(
                chat,
                senderId,
                content,
                topicId,
                replyToMessageId,
                forwardedFromChatId,
                forwardedFromMessageId,
                pollId,
                stickerId,
                attachmentIds,
                null
        );
    }

    private MessageLookupEntity buildNewMessage(
            ChatEntity chat,
            UUID senderId,
            String plaintext,
            UUID topicId,
            UUID replyToMessageId,
            UUID forwardedFromChatId,
            UUID forwardedFromMessageId,
            UUID pollId,
            UUID stickerId,
            List<UUID> attachmentIds,
            UUID messageId
    ) {
        return buildNewMessage(
                chat,
                senderId,
                messageContentCodec.plain(plaintext),
                topicId,
                replyToMessageId,
                forwardedFromChatId,
                forwardedFromMessageId,
                pollId,
                stickerId,
                attachmentIds,
                messageId
        );
    }

    private MessageLookupEntity buildNewMessage(
            ChatEntity chat,
            UUID senderId,
            MessageTextContent content,
            UUID topicId,
            UUID replyToMessageId,
            UUID forwardedFromChatId,
            UUID forwardedFromMessageId,
            UUID pollId,
            UUID stickerId,
            List<UUID> attachmentIds,
            UUID messageId
    ) {
        EncryptedPayload encryptedPayload = chatEncryptionService.encrypt(
                chat.getId(),
                messageContentCodec.encode(content)
        );
        return buildMessageLookupFromEncrypted(
                chat,
                senderId,
                topicId,
                replyToMessageId,
                forwardedFromChatId,
                forwardedFromMessageId,
                pollId,
                stickerId,
                attachmentIds,
                encryptedPayload.ciphertext(),
                encryptedPayload.nonce(),
                encryptedPayload.keyVersion(),
                messageId
        );
    }

    private MessageLookupEntity buildSystemMessage(
            ChatEntity chat,
            UUID senderId,
            MessageTextContent content,
            UUID topicId,
            UUID replyToMessageId,
            UUID forwardedFromChatId,
            UUID forwardedFromMessageId,
            UUID pollId,
            UUID stickerId,
            List<UUID> attachmentIds
    ) {
        EncryptedPayload encryptedPayload = chatEncryptionService.encrypt(
                chat.getId(),
                messageContentCodec.encode(content)
        );
        return buildMessageLookupFromEncrypted(
                chat,
                senderId,
                topicId,
                replyToMessageId,
                forwardedFromChatId,
                forwardedFromMessageId,
                pollId,
                stickerId,
                attachmentIds,
                encryptedPayload.ciphertext(),
                encryptedPayload.nonce(),
                encryptedPayload.keyVersion(),
                null,
                chatService.getRecipientIdsForSystem(chat, senderId)
        );
    }

    private MessageLookupEntity buildMessageLookupFromEncrypted(
            ChatEntity chat,
            UUID senderId,
            UUID topicId,
            UUID replyToMessageId,
            UUID forwardedFromChatId,
            UUID forwardedFromMessageId,
            UUID pollId,
            UUID stickerId,
            List<UUID> attachmentIds,
            String ciphertext,
            String nonce,
            int keyVersion
    ) {
        return buildMessageLookupFromEncrypted(
                chat,
                senderId,
                topicId,
                replyToMessageId,
                forwardedFromChatId,
                forwardedFromMessageId,
                pollId,
                stickerId,
                attachmentIds,
                ciphertext,
                nonce,
                keyVersion,
                null
        );
    }

    private MessageLookupEntity buildMessageLookupFromEncrypted(
            ChatEntity chat,
            UUID senderId,
            UUID topicId,
            UUID replyToMessageId,
            UUID forwardedFromChatId,
            UUID forwardedFromMessageId,
            UUID pollId,
            UUID stickerId,
            List<UUID> attachmentIds,
            String ciphertext,
            String nonce,
            int keyVersion,
            UUID messageId
    ) {
        return buildMessageLookupFromEncrypted(
                chat,
                senderId,
                topicId,
                replyToMessageId,
                forwardedFromChatId,
                forwardedFromMessageId,
                pollId,
                stickerId,
                attachmentIds,
                ciphertext,
                nonce,
                keyVersion,
                messageId,
                chatService.getRecipientIds(chat, senderId)
        );
    }

    private MessageLookupEntity buildMessageLookupFromEncrypted(
            ChatEntity chat,
            UUID senderId,
            UUID topicId,
            UUID replyToMessageId,
            UUID forwardedFromChatId,
            UUID forwardedFromMessageId,
            UUID pollId,
            UUID stickerId,
            List<UUID> attachmentIds,
            String ciphertext,
            String nonce,
            int keyVersion,
            UUID messageId,
            List<UUID> recipientIds
    ) {
        UUID resolvedMessageId = messageId != null ? messageId : Uuids.timeBased();
        Instant createdAt = Instant.ofEpochMilli(Uuids.unixTimestamp(resolvedMessageId));
        MessageLookupEntity lookup = new MessageLookupEntity();
        lookup.setMessageId(resolvedMessageId);
        lookup.setChatId(chat.getId());
        lookup.setCreatedAt(createdAt);
        lookup.setSenderId(senderId);
        lookup.setRecipientId(recipientIds.size() == 1 ? recipientIds.get(0) : null);
        lookup.setTopicId(topicId);
        lookup.setCiphertext(ciphertext);
        lookup.setNonce(nonce);
        lookup.setKeyVersion(keyVersion);
        lookup.setReplyToMessageId(replyToMessageId);
        lookup.setForwardedFromChatId(forwardedFromChatId);
        lookup.setForwardedFromMessageId(forwardedFromMessageId);
        lookup.setPollId(pollId);
        lookup.setStickerId(stickerId);
        lookup.setAttachmentIds(attachmentIds);
        lookup.setExpiresAt(chat.getAutoDeleteSeconds() != null ? createdAt.plusSeconds(chat.getAutoDeleteSeconds()) : null);
        if ("SAVED".equals(chat.getChatType())) {
            lookup.setDeliveryStatus("READ");
            lookup.setDeliveredAt(lookup.getCreatedAt());
            lookup.setReadAt(lookup.getCreatedAt());
        } else {
            lookup.setDeliveryStatus("SENT");
        }
        return lookup;
    }

    private MessageLookupEntity resolveReplyTarget(UUID chatId, UUID topicId, UUID replyToMessageId) {
        if (replyToMessageId == null) {
            return null;
        }
        MessageLookupEntity replyTo = messageLookupRepository.findById(replyToMessageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reply target not found"));
        if (replyTo.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reply target not found");
        }
        if (!replyTo.getChatId().equals(chatId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply target belongs to another chat");
        }
        if (!Objects.equals(replyTo.getTopicId(), topicId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply target belongs to another topic");
        }
        return replyTo;
    }

    private UUID resolveThreadRootMessageId(MessageLookupEntity replyTarget) {
        if (replyTarget == null) {
            return null;
        }
        return replyTarget.getThreadRootMessageId() != null
                ? replyTarget.getThreadRootMessageId()
                : replyTarget.getMessageId();
    }

    private UUID resolveDiscussionChatId(ChatEntity chat, MessageLookupEntity replyTarget) {
        if (replyTarget == null) {
            return null;
        }
        return replyTarget.getDiscussionChatId() != null ? replyTarget.getDiscussionChatId() : chat.getId();
    }

    private UUID resolveDiscussionRootMessageId(MessageLookupEntity replyTarget) {
        if (replyTarget == null) {
            return null;
        }
        return replyTarget.getDiscussionRootMessageId() != null
                ? replyTarget.getDiscussionRootMessageId()
                : replyTarget.getMessageId();
    }

    private void applyThreadMetadata(
            MessageLookupEntity lookup,
            MessageLookupEntity replyTarget,
            UUID explicitThreadRootMessageId,
            UUID explicitDiscussionChatId
    ) {
        UUID threadRootMessageId = explicitThreadRootMessageId != null
                ? explicitThreadRootMessageId
                : resolveThreadRootMessageId(replyTarget);
        lookup.setThreadRootMessageId(threadRootMessageId);

        UUID discussionRootMessageId = replyTarget != null
                ? resolveDiscussionRootMessageId(replyTarget)
                : null;
        lookup.setDiscussionRootMessageId(discussionRootMessageId);

        UUID discussionChatId = explicitDiscussionChatId != null
                ? explicitDiscussionChatId
                : (discussionRootMessageId != null ? resolveDiscussionChatId(chatService.getChat(lookup.getChatId()), replyTarget) : null);
        lookup.setDiscussionChatId(discussionChatId);
    }

    private ResolvedReadScope resolveReadScope(
            ChatEntity chat,
            UUID requesterId,
            UUID topicId,
            UUID threadRootMessageId
    ) {
        ForumTopicEntity topic = forumTopicService.resolveTopicForRead(chat, requesterId, topicId);
        if (threadRootMessageId == null) {
            return new ResolvedReadScope(topic, null);
        }

        MessageLookupEntity threadRoot = getAccessibleThreadScopeMessage(requesterId, threadRootMessageId);
        if (!threadRoot.getChatId().equals(chat.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thread belongs to another chat");
        }

        UUID normalizedThreadRootMessageId = threadRoot.getThreadRootMessageId() != null
                ? threadRoot.getThreadRootMessageId()
                : threadRoot.getMessageId();
        if (topicId != null && !Objects.equals(threadRoot.getTopicId(), topic != null ? topic.getId() : null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thread belongs to another topic");
        }
        if (topicId == null && topic != null && !Boolean.TRUE.equals(topic.getGeneralTopic()) && !Objects.equals(topic.getId(), threadRoot.getTopicId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thread belongs to another topic");
        }
        if (topicId == null && threadRoot.getTopicId() != null && Boolean.TRUE.equals(chat.getForumEnabled())) {
            ForumTopicEntity inferredTopic = new ForumTopicEntity();
            inferredTopic.setId(threadRoot.getTopicId());
            inferredTopic.setChatId(chat.getId());
            topic = inferredTopic;
        }
        return new ResolvedReadScope(topic, normalizedThreadRootMessageId);
    }

    private MessageLookupEntity getAccessibleThreadScopeMessage(UUID requesterId, UUID messageId) {
        MessageLookupEntity lookup = messageLookupRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        ChatEntity chat = chatService.getOwnedChat(requesterId, lookup.getChatId());
        ensureMessageVisibleToRequester(chat, requesterId, lookup.getTopicId());
        return lookup;
    }

    private boolean isMessageVisibleToRequester(ChatEntity chat, UUID requesterId, UUID topicId) {
        if (chat == null) {
            return false;
        }
        if (topicId == null || !Boolean.TRUE.equals(chat.getForumEnabled())) {
            return true;
        }
        try {
            forumTopicService.resolveTopicForRead(chat, requesterId, topicId);
            return true;
        } catch (ResponseStatusException exception) {
            return false;
        }
    }

    private void ensureMessageVisibleToRequester(ChatEntity chat, UUID requesterId, UUID topicId) {
        if (!isMessageVisibleToRequester(chat, requesterId, topicId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
        }
    }

    private void ensureReactionsAllowed(MessageLookupEntity lookup) {
        if (!chatService.areReactionsEnabled(lookup.getChatId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reactions are disabled for this chat");
        }
    }

    private void ensureCommentsAllowedForReply(ChatEntity chat, MessageLookupEntity replyTarget) {
        if (replyTarget == null || !"GROUP".equals(chat.getChatType())) {
            return;
        }

        MessageLookupEntity threadRoot = resolveCommentThreadRoot(replyTarget);
        if (threadRoot == null || threadRoot.getForwardedFromChatId() == null) {
            return;
        }
        if (!chatService.areCommentsEnabled(threadRoot.getForwardedFromChatId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Comments are disabled for this channel");
        }
    }

    private MessageLookupEntity resolveScheduledReplyTargetForDispatch(
            ChatEntity chat,
            UUID topicId,
            ScheduledMessageEntity scheduledMessage
    ) {
        MessageLookupEntity replyTarget = resolveReplyTarget(chat.getId(), topicId, scheduledMessage.getReplyToMessageId());
        if (replyTarget == null) {
            if (scheduledMessage.getThreadRootMessageId() != null
                    || scheduledMessage.getDiscussionChatId() != null
                    || scheduledMessage.getDiscussionRootMessageId() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Scheduled message thread metadata is out of date");
            }
            return null;
        }

        if (!Objects.equals(scheduledMessage.getThreadRootMessageId(), resolveThreadRootMessageId(replyTarget))
                || !Objects.equals(scheduledMessage.getDiscussionChatId(), resolveDiscussionChatId(chat, replyTarget))
                || !Objects.equals(scheduledMessage.getDiscussionRootMessageId(), resolveDiscussionRootMessageId(replyTarget))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Scheduled message thread metadata is out of date");
        }
        return replyTarget;
    }

    private void ensureScheduledCommentPolicyAllowsWrite(ChatEntity chat, MessageLookupEntity replyTarget) {
        if (!"GROUP".equals(chat.getChatType()) || replyTarget == null) {
            return;
        }

        MessageLookupEntity threadRoot = resolveCommentThreadRoot(replyTarget);
        if (threadRoot == null || threadRoot.getForwardedFromChatId() == null) {
            return;
        }
        if (!chatService.areCommentsEnabled(threadRoot.getForwardedFromChatId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Comments are disabled for this channel");
        }
    }

    private MessageLookupEntity resolveCommentThreadRoot(MessageLookupEntity replyTarget) {
        UUID threadRootMessageId = replyTarget.getThreadRootMessageId() != null
                ? replyTarget.getThreadRootMessageId()
                : replyTarget.getMessageId();
        if (threadRootMessageId.equals(replyTarget.getMessageId())) {
            return replyTarget;
        }
        return messageLookupRepository.findById(threadRootMessageId).orElse(replyTarget);
    }

    private void linkDiscussionThreadIfNeeded(ChatEntity chat, UUID senderId, MessageLookupEntity lookup) {
        if (!"CHANNEL".equals(chat.getChatType())
                || chat.getLinkedDiscussionChatId() == null
                || lookup.getDiscussionRootMessageId() != null
                || !Boolean.TRUE.equals(chat.getCommentsEnabled())
                || !chatService.isCrossPostingEnabled(chat.getId())) {
            return;
        }

        ChatEntity discussionChat = chatService.getChat(chat.getLinkedDiscussionChatId());
        UUID discussionTopicId = Boolean.TRUE.equals(discussionChat.getForumEnabled())
                ? forumTopicService.ensureGeneralTopic(discussionChat).getId()
                : null;
        MessageTextContent content = decodeMessageContent(lookup);
        List<UUID> discussionAttachmentIds = attachmentService.cloneAttachmentsToChatForSystem(
                senderId,
                discussionChat.getId(),
                lookup.getAttachmentIds() != null ? lookup.getAttachmentIds() : List.of()
        );
        MessageLookupEntity rootLookup = buildSystemMessage(
                discussionChat,
                senderId,
                content,
                discussionTopicId,
                null,
                chat.getId(),
                lookup.getMessageId(),
                lookup.getPollId(),
                lookup.getStickerId(),
                discussionAttachmentIds
        );
        rootLookup.setViaBotUserId(lookup.getViaBotUserId());
        rootLookup.setThreadRootMessageId(rootLookup.getMessageId());
        rootLookup.setDiscussionChatId(discussionChat.getId());
        rootLookup.setDiscussionRootMessageId(rootLookup.getMessageId());

        persistMessage(rootLookup);
        chatService.updateLastMessageAt(discussionChat, rootLookup.getCreatedAt());
        forumTopicService.touchTopic(rootLookup.getTopicId(), rootLookup.getCreatedAt());
        chatService.incrementUnreadCounts(discussionChat.getId(), senderId, content, null, rootLookup.getTopicId());
        publish(rootLookup, chatService.getRecipientIdsForSystem(discussionChat, senderId));

        lookup.setDiscussionChatId(discussionChat.getId());
        lookup.setDiscussionRootMessageId(rootLookup.getMessageId());
        persistMessage(lookup);
    }

    private ScheduledMessageResponse toScheduledResponse(UUID requesterId, ScheduledMessageEntity scheduledMessage) {
        MessageTextContent content = decodeMessageContent(
                scheduledMessage.getChatId(),
                scheduledMessage.getCiphertext(),
                scheduledMessage.getNonce(),
                scheduledMessage.getKeyVersion()
        );
        List<MessageAttachmentResponse> attachments =
                getAttachmentResponses(requesterId, parseAttachmentIds(scheduledMessage.getAttachmentIds()));
        VisibleMessageReferences references = resolveVisibleReferences(
                requesterId,
                scheduledMessage.getReplyToMessageId(),
                scheduledMessage.getThreadRootMessageId(),
                scheduledMessage.getDiscussionChatId(),
                scheduledMessage.getDiscussionRootMessageId(),
                null,
                null
        );
        return new ScheduledMessageResponse(
                scheduledMessage.getId(),
                scheduledMessage.getClientMessageId(),
                scheduledMessage.getChatId(),
                scheduledMessage.getSenderId(),
                scheduledMessage.getTopicId(),
                references.threadRootMessageId(),
                references.discussionChatId(),
                references.discussionRootMessageId(),
                content.text(),
                content.entities(),
                resolveResponseMessageType(content, null, scheduledMessage.getStickerId(), attachments),
                content.caption(),
                content.silent(),
                content.location(),
                content.liveLocation(),
                content.contactCard(),
                content.serviceMessage(),
                references.replyToMessageId(),
                scheduledMessage.getStickerId(),
                attachments,
                scheduledMessage.getScheduledAt(),
                scheduledMessage.getCreatedAt(),
                scheduledMessage.getStatus()
        );
    }

    private ScheduledMessageEntity buildScheduledMessageEntity(
            ChatEntity chat,
            UUID senderId,
            UUID topicId,
            MessageLookupEntity replyTarget,
            UUID replyToMessageId,
            UUID stickerId,
            List<UUID> attachmentIds,
            EncryptedPayload encryptedPayload,
            Instant scheduledAt,
            String status,
            UUID clientMessageId
    ) {
        ScheduledMessageEntity scheduledMessage = new ScheduledMessageEntity();
        scheduledMessage.setChatId(chat.getId());
        scheduledMessage.setSenderId(senderId);
        scheduledMessage.setClientMessageId(clientMessageId);
        scheduledMessage.setTopicId(topicId);
        scheduledMessage.setThreadRootMessageId(resolveThreadRootMessageId(replyTarget));
        scheduledMessage.setDiscussionChatId(resolveDiscussionChatId(chat, replyTarget));
        scheduledMessage.setDiscussionRootMessageId(resolveDiscussionRootMessageId(replyTarget));
        scheduledMessage.setCiphertext(encryptedPayload.ciphertext());
        scheduledMessage.setNonce(encryptedPayload.nonce());
        scheduledMessage.setKeyVersion(encryptedPayload.keyVersion());
        scheduledMessage.setReplyToMessageId(replyToMessageId);
        scheduledMessage.setStickerId(stickerId);
        scheduledMessage.setAttachmentIds(encodeAttachmentIds(attachmentIds));
        scheduledMessage.setScheduledAt(scheduledAt);
        scheduledMessage.setStatus(status);
        return scheduledMessage;
    }

    private ScheduledMessageEntity buildScheduledMessageFromRule(
            RepeatingMessageRuleEntity rule,
            Instant scheduledAt,
            int occurrence
    ) {
        ScheduledMessageEntity scheduledMessage = new ScheduledMessageEntity();
        scheduledMessage.setChatId(rule.getChatId());
        scheduledMessage.setSenderId(rule.getSenderId());
        scheduledMessage.setTopicId(rule.getTopicId());
        scheduledMessage.setThreadRootMessageId(rule.getThreadRootMessageId());
        scheduledMessage.setDiscussionChatId(rule.getDiscussionChatId());
        scheduledMessage.setDiscussionRootMessageId(rule.getDiscussionRootMessageId());
        scheduledMessage.setCiphertext(rule.getCiphertext());
        scheduledMessage.setNonce(rule.getNonce());
        scheduledMessage.setKeyVersion(rule.getKeyVersion());
        scheduledMessage.setReplyToMessageId(rule.getReplyToMessageId());
        scheduledMessage.setStickerId(rule.getStickerId());
        scheduledMessage.setAttachmentIds(rule.getAttachmentIds());
        scheduledMessage.setScheduledAt(scheduledAt);
        scheduledMessage.setStatus("PENDING");
        scheduledMessage.setRepeatingRuleId(rule.getId());
        scheduledMessage.setRepeatingOccurrence(occurrence);
        return scheduledMessage;
    }

    private ScheduledMessageResponse saveScheduledMessage(
            UUID senderId,
            UUID clientMessageId,
            UUID expectedChatId,
            ScheduledMessageEntity scheduledMessage
    ) {
        if (clientMessageId != null) {
            ScheduledMessageEntity existing = scheduledMessageRepository
                    .findBySenderIdAndClientMessageId(senderId, clientMessageId)
                    .orElse(null);
            if (existing != null) {
                if (!existing.getChatId().equals(expectedChatId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "clientMessageId is already bound to another scheduled chat");
                }
                return toScheduledResponse(senderId, existing);
            }
        }

        try {
            return toScheduledResponse(senderId, scheduledMessageRepository.save(scheduledMessage));
        } catch (DataIntegrityViolationException duplicateScheduleRace) {
            if (clientMessageId == null) {
                throw duplicateScheduleRace;
            }
            ScheduledMessageEntity existing = scheduledMessageRepository
                    .findBySenderIdAndClientMessageId(senderId, clientMessageId)
                    .orElseThrow(() -> duplicateScheduleRace);
            if (!existing.getChatId().equals(expectedChatId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "clientMessageId is already bound to another scheduled chat");
            }
            return toScheduledResponse(senderId, existing);
        }
    }

    private RepeatingMessageResponse toRepeatingResponse(RepeatingMessageRuleEntity rule) {
        return new RepeatingMessageResponse(
                rule.getId(),
                rule.getClientRuleId(),
                rule.getChatId(),
                rule.getSenderId(),
                rule.getTopicId(),
                rule.getReplyToMessageId(),
                rule.getStickerId(),
                rule.getIntervalMinutes(),
                rule.getMaxOccurrences(),
                rule.getEmittedOccurrences() != null ? rule.getEmittedOccurrences() : 0,
                rule.getLastScheduledAt(),
                rule.getNextScheduledAt(),
                rule.getLastScheduledMessageId(),
                rule.getStatus(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }

    private void materializeNextRepeatingOccurrence(RepeatingMessageRuleEntity rule) {
        if (rule.getNextScheduledAt() == null || !"ACTIVE".equals(rule.getStatus())) {
            return;
        }
        int nextOccurrence = (rule.getEmittedOccurrences() != null ? rule.getEmittedOccurrences() : 0) + 1;
        ScheduledMessageEntity scheduledMessage = scheduledMessageRepository.save(
                buildScheduledMessageFromRule(rule, rule.getNextScheduledAt(), nextOccurrence)
        );
        rule.setEmittedOccurrences(nextOccurrence);
        rule.setLastScheduledAt(scheduledMessage.getScheduledAt());
        rule.setLastScheduledMessageId(scheduledMessage.getId());
        rule.setNextScheduledAt(computeNextRepeatingScheduledAt(rule, scheduledMessage.getScheduledAt(), nextOccurrence));
    }

    private Instant computeNextRepeatingScheduledAt(
            RepeatingMessageRuleEntity rule,
            Instant currentScheduledAt,
            int emittedOccurrences
    ) {
        if (rule.getMaxOccurrences() != null && emittedOccurrences >= rule.getMaxOccurrences()) {
            return null;
        }
        return currentScheduledAt.plusSeconds(rule.getIntervalMinutes().longValue() * 60L);
    }

    private void cancelRepeatingRuleIfNeeded(UUID senderId, ScheduledMessageEntity scheduledMessage) {
        if (scheduledMessage.getRepeatingRuleId() == null) {
            return;
        }
        repeatingMessageRuleRepository.findByIdAndSenderId(scheduledMessage.getRepeatingRuleId(), senderId)
                .ifPresent(rule -> {
                    rule.setStatus("CANCELED");
                    rule.setNextScheduledAt(null);
                    repeatingMessageRuleRepository.save(rule);
                });
    }

    private void advanceRepeatingRuleIfNeeded(ScheduledMessageEntity scheduledMessage) {
        if (scheduledMessage.getRepeatingRuleId() == null) {
            return;
        }
        RepeatingMessageRuleEntity rule = repeatingMessageRuleRepository.findById(scheduledMessage.getRepeatingRuleId())
                .orElse(null);
        if (rule == null || !"ACTIVE".equals(rule.getStatus())) {
            return;
        }
        if ("FAILED".equals(scheduledMessage.getStatus())) {
            rule.setStatus("FAILED");
            rule.setNextScheduledAt(null);
            repeatingMessageRuleRepository.save(rule);
            return;
        }
        if (!"DELIVERED".equals(scheduledMessage.getStatus())) {
            return;
        }
        if (rule.getNextScheduledAt() == null) {
            rule.setStatus("COMPLETED");
            repeatingMessageRuleRepository.save(rule);
            return;
        }
        try {
            materializeNextRepeatingOccurrence(rule);
            repeatingMessageRuleRepository.save(rule);
        } catch (RuntimeException exception) {
            rule.setStatus("FAILED");
            rule.setNextScheduledAt(null);
            repeatingMessageRuleRepository.save(rule);
        }
    }
    private List<MessageAttachmentResponse> getAttachmentResponses(UUID requesterId, List<UUID> attachmentIds) {
        return attachmentService.getResponses(requesterId, attachmentIds);
    }

    private ChatMessageResponse toResponse(
            UUID requesterId,
            MessageLookupEntity lookup,
            List<MessageReactionSummary> reactions,
            List<MessageAttachmentResponse> attachments
    ) {
        return toResponse(requesterId, lookup, reactions, attachments, null);
    }

    private ChatMessageResponse toResponse(
            UUID requesterId,
            MessageLookupEntity lookup,
            List<MessageReactionSummary> reactions,
            List<MessageAttachmentResponse> attachments,
            UUID clientMessageId
    ) {
        MessageTextContent content = lookup.getDeletedAt() != null
                ? new MessageTextContent("", List.of())
                : decodeMessageContent(lookup);
        ChatService.MessageAuthorView author = chatService.resolveMessageAuthor(
                requesterId,
                lookup.getChatId(),
                lookup.getSenderId()
        );
        VisibleMessageReferences references = resolveVisibleReferences(
                requesterId,
                lookup.getReplyToMessageId(),
                lookup.getThreadRootMessageId(),
                lookup.getDiscussionChatId(),
                lookup.getDiscussionRootMessageId(),
                lookup.getForwardedFromChatId(),
                lookup.getForwardedFromMessageId()
        );
        return new ChatMessageResponse(
                lookup.getChatId(),
                lookup.getMessageId(),
                clientMessageId,
                author.senderId(),
                author.displayName(),
                author.photoUrl(),
                author.photoAccessExpiresAt(),
                author.anonymous(),
                lookup.getRecipientId(),
                lookup.getViaBotUserId(),
                lookup.getTopicId(),
                references.threadRootMessageId(),
                references.discussionChatId(),
                references.discussionRootMessageId(),
                countComments(references.discussionRootMessageId()),
                content.text(),
                content.entities(),
                resolveResponseMessageType(content, lookup.getPollId(), lookup.getStickerId(), attachments),
                content.caption(),
                content.silent(),
                content.location(),
                resolveLiveLocationPayload(content),
                content.contactCard(),
                content.serviceMessage(),
                lookup.getCreatedAt(),
                references.replyToMessageId(),
                references.forwardedFromChatId(),
                references.forwardedFromMessageId(),
                pollService.getPollResponse(lookup.getPollId(), requesterId),
                stickerService.getStickerResponse(lookup.getStickerId()),
                attachments,
                reactions,
                lookup.getDeliveryStatus(),
                lookup.getDeliveredAt(),
                lookup.getReadAt(),
                lookup.getExpiresAt(),
                lookup.getEditedAt(),
                lookup.getDeletedAt()
        );
    }

    private ChatMessageResponse toResponse(
            UUID requesterId,
            MessageEntity message,
            List<MessageReactionSummary> reactions,
            List<MessageAttachmentResponse> attachments
    ) {
        MessageTextContent content = message.getDeletedAt() != null
                ? new MessageTextContent("", List.of())
                : decodeMessageContent(message);
        ChatService.MessageAuthorView author = chatService.resolveMessageAuthor(
                requesterId,
                message.getKey().getChatId(),
                message.getSenderId()
        );
        VisibleMessageReferences references = resolveVisibleReferences(
                requesterId,
                message.getReplyToMessageId(),
                message.getThreadRootMessageId(),
                message.getDiscussionChatId(),
                message.getDiscussionRootMessageId(),
                message.getForwardedFromChatId(),
                message.getForwardedFromMessageId()
        );
        return new ChatMessageResponse(
                message.getKey().getChatId(),
                message.getKey().getMessageId(),
                null,
                author.senderId(),
                author.displayName(),
                author.photoUrl(),
                author.photoAccessExpiresAt(),
                author.anonymous(),
                message.getRecipientId(),
                message.getViaBotUserId(),
                message.getTopicId(),
                references.threadRootMessageId(),
                references.discussionChatId(),
                references.discussionRootMessageId(),
                countComments(references.discussionRootMessageId()),
                content.text(),
                content.entities(),
                resolveResponseMessageType(content, message.getPollId(), message.getStickerId(), attachments),
                content.caption(),
                content.silent(),
                content.location(),
                resolveLiveLocationPayload(content),
                content.contactCard(),
                content.serviceMessage(),
                message.getCreatedAt(),
                references.replyToMessageId(),
                references.forwardedFromChatId(),
                references.forwardedFromMessageId(),
                pollService.getPollResponse(message.getPollId(), requesterId),
                stickerService.getStickerResponse(message.getStickerId()),
                attachments,
                reactions,
                message.getDeliveryStatus(),
                message.getDeliveredAt(),
                message.getReadAt(),
                message.getExpiresAt(),
                message.getEditedAt(),
                message.getDeletedAt()
        );
    }

    private ChatMessageResponse toResponse(
            UUID requesterId,
            MessageTopicEntity message,
            List<MessageReactionSummary> reactions,
            List<MessageAttachmentResponse> attachments
    ) {
        MessageTextContent content = message.getDeletedAt() != null
                ? new MessageTextContent("", List.of())
                : decodeMessageContent(message);
        ChatService.MessageAuthorView author = chatService.resolveMessageAuthor(
                requesterId,
                message.getChatId(),
                message.getSenderId()
        );
        VisibleMessageReferences references = resolveVisibleReferences(
                requesterId,
                message.getReplyToMessageId(),
                message.getThreadRootMessageId(),
                message.getDiscussionChatId(),
                message.getDiscussionRootMessageId(),
                message.getForwardedFromChatId(),
                message.getForwardedFromMessageId()
        );
        return new ChatMessageResponse(
                message.getChatId(),
                message.getKey().getMessageId(),
                null,
                author.senderId(),
                author.displayName(),
                author.photoUrl(),
                author.photoAccessExpiresAt(),
                author.anonymous(),
                message.getRecipientId(),
                message.getViaBotUserId(),
                message.getKey().getTopicId(),
                references.threadRootMessageId(),
                references.discussionChatId(),
                references.discussionRootMessageId(),
                countComments(references.discussionRootMessageId()),
                content.text(),
                content.entities(),
                resolveResponseMessageType(content, message.getPollId(), message.getStickerId(), attachments),
                content.caption(),
                content.silent(),
                content.location(),
                resolveLiveLocationPayload(content),
                content.contactCard(),
                content.serviceMessage(),
                message.getCreatedAt(),
                references.replyToMessageId(),
                references.forwardedFromChatId(),
                references.forwardedFromMessageId(),
                pollService.getPollResponse(message.getPollId(), requesterId),
                stickerService.getStickerResponse(message.getStickerId()),
                attachments,
                reactions,
                message.getDeliveryStatus(),
                message.getDeliveredAt(),
                message.getReadAt(),
                message.getExpiresAt(),
                message.getEditedAt(),
                message.getDeletedAt()
        );
    }

    private ChatMessageResponse toResponse(
            UUID requesterId,
            MessageThreadEntity message,
            List<MessageReactionSummary> reactions,
            List<MessageAttachmentResponse> attachments
    ) {
        MessageTextContent content = message.getDeletedAt() != null
                ? new MessageTextContent("", List.of())
                : decodeMessageContent(message);
        ChatService.MessageAuthorView author = chatService.resolveMessageAuthor(
                requesterId,
                message.getChatId(),
                message.getSenderId()
        );
        VisibleMessageReferences references = resolveVisibleReferences(
                requesterId,
                message.getReplyToMessageId(),
                message.getKey().getThreadRootMessageId(),
                message.getDiscussionChatId(),
                message.getDiscussionRootMessageId(),
                message.getForwardedFromChatId(),
                message.getForwardedFromMessageId()
        );
        return new ChatMessageResponse(
                message.getChatId(),
                message.getKey().getMessageId(),
                null,
                author.senderId(),
                author.displayName(),
                author.photoUrl(),
                author.photoAccessExpiresAt(),
                author.anonymous(),
                message.getRecipientId(),
                message.getViaBotUserId(),
                message.getTopicId(),
                references.threadRootMessageId(),
                references.discussionChatId(),
                references.discussionRootMessageId(),
                countComments(references.discussionRootMessageId()),
                content.text(),
                content.entities(),
                resolveResponseMessageType(content, message.getPollId(), message.getStickerId(), attachments),
                content.caption(),
                content.silent(),
                content.location(),
                resolveLiveLocationPayload(content),
                content.contactCard(),
                content.serviceMessage(),
                message.getCreatedAt(),
                references.replyToMessageId(),
                references.forwardedFromChatId(),
                references.forwardedFromMessageId(),
                pollService.getPollResponse(message.getPollId(), requesterId),
                stickerService.getStickerResponse(message.getStickerId()),
                attachments,
                reactions,
                message.getDeliveryStatus(),
                message.getDeliveredAt(),
                message.getReadAt(),
                message.getExpiresAt(),
                message.getEditedAt(),
                message.getDeletedAt()
        );
    }

    private VisibleMessageReferences resolveVisibleReferences(
            UUID requesterId,
            UUID replyToMessageId,
            UUID threadRootMessageId,
            UUID discussionChatId,
            UUID discussionRootMessageId,
            UUID forwardedFromChatId,
            UUID forwardedFromMessageId
    ) {
        UUID visibleReplyToMessageId = resolveVisibleMessageReferenceId(requesterId, replyToMessageId);
        UUID visibleThreadRootMessageId = resolveVisibleMessageReferenceId(requesterId, threadRootMessageId);
        UUID visibleDiscussionRootMessageId = resolveVisibleMessageReferenceId(requesterId, discussionRootMessageId);
        UUID visibleDiscussionChatId = visibleDiscussionRootMessageId != null
                ? discussionChatId
                : resolveVisibleChatReferenceId(requesterId, discussionChatId);
        UUID visibleForwardedFromMessageId = resolveVisibleForwardedMessageReferenceId(
                requesterId,
                forwardedFromChatId,
                forwardedFromMessageId
        );
        UUID visibleForwardedFromChatId = visibleForwardedFromMessageId != null
                ? forwardedFromChatId
                : resolveVisibleChatReferenceId(requesterId, forwardedFromChatId);
        return new VisibleMessageReferences(
                visibleReplyToMessageId,
                visibleThreadRootMessageId,
                visibleDiscussionChatId,
                visibleDiscussionRootMessageId,
                visibleForwardedFromChatId,
                visibleForwardedFromMessageId
        );
    }

    private UUID resolveVisibleMessageReferenceId(UUID requesterId, UUID messageId) {
        if (messageId == null) {
            return null;
        }
        MessageLookupEntity reference = messageLookupRepository.findById(messageId).orElse(null);
        if (reference == null || reference.getDeletedAt() != null) {
            return null;
        }
        try {
            ChatEntity chat = chatService.getOwnedChat(requesterId, reference.getChatId());
            ensureMessageVisibleToRequester(chat, requesterId, reference.getTopicId());
            return messageId;
        } catch (ResponseStatusException exception) {
            return null;
        }
    }

    private UUID resolveVisibleForwardedMessageReferenceId(UUID requesterId, UUID forwardedFromChatId, UUID forwardedFromMessageId) {
        if (forwardedFromMessageId == null) {
            return null;
        }
        MessageLookupEntity reference = messageLookupRepository.findById(forwardedFromMessageId).orElse(null);
        if (reference == null || reference.getDeletedAt() != null) {
            return null;
        }
        if (forwardedFromChatId != null && !forwardedFromChatId.equals(reference.getChatId())) {
            return null;
        }
        try {
            ChatEntity chat = chatService.getOwnedChat(requesterId, reference.getChatId());
            ensureMessageVisibleToRequester(chat, requesterId, reference.getTopicId());
            return forwardedFromMessageId;
        } catch (ResponseStatusException exception) {
            return null;
        }
    }

    private UUID resolveVisibleChatReferenceId(UUID requesterId, UUID chatId) {
        if (chatId == null) {
            return null;
        }
        try {
            chatService.getOwnedChat(requesterId, chatId);
            return chatId;
        } catch (ResponseStatusException exception) {
            return null;
        }
    }

    private MessageTextContent decodeMessageContent(MessageLookupEntity lookup) {
        return decodeMessageContent(
                lookup.getChatId(),
                lookup.getCiphertext(),
                lookup.getNonce(),
                lookup.getKeyVersion()
        );
    }

    private MessageTextContent decodeMessageContent(MessageEntity message) {
        return decodeMessageContent(
                message.getKey().getChatId(),
                message.getCiphertext(),
                message.getNonce(),
                message.getKeyVersion()
        );
    }

    private MessageTextContent decodeMessageContent(MessageTopicEntity message) {
        return decodeMessageContent(
                message.getChatId(),
                message.getCiphertext(),
                message.getNonce(),
                message.getKeyVersion()
        );
    }

    private MessageTextContent decodeMessageContent(MessageThreadEntity message) {
        return decodeMessageContent(
                message.getChatId(),
                message.getCiphertext(),
                message.getNonce(),
                message.getKeyVersion()
        );
    }

    private int countComments(UUID discussionRootMessageId) {
        if (discussionRootMessageId == null) {
            return 0;
        }
        return (int) messageThreadRepository.findAllByThreadRootMessageId(discussionRootMessageId).stream()
                .filter(message -> !discussionRootMessageId.equals(message.getKey().getMessageId()))
                .filter(message -> message.getDeletedAt() == null)
                .count();
    }

    private MessageTextContent decodeMessageContent(UUID chatId, String ciphertext, String nonce, int keyVersion) {
        return messageContentCodec.decode(
                chatEncryptionService.decrypt(chatId, ciphertext, nonce, keyVersion)
        );
    }

    private MessageTextContent activateLiveLocationIfNeeded(MessageLookupEntity lookup, MessageTextContent content) {
        if (!"LIVE_LOCATION".equals(content.messageType()) || content.liveLocation() == null) {
            return content;
        }
        MessageLiveLocationPayload liveLocation = messageLiveLocationService.activate(
                lookup.getMessageId(),
                lookup.getChatId(),
                lookup.getSenderId(),
                content.liveLocation()
        );
        return syncLiveLocationState(lookup, content, liveLocation);
    }

    private MessageTextContent syncLiveLocationState(
            MessageLookupEntity lookup,
            MessageTextContent content,
            MessageLiveLocationPayload liveLocation
    ) {
        MessageTextContent updatedContent = toLiveLocationContent(
                content,
                mergeLiveLocationPayload(content.liveLocation(), liveLocation)
        );
        EncryptedPayload encryptedPayload = chatEncryptionService.encrypt(
                lookup.getChatId(),
                messageContentCodec.encode(updatedContent)
        );
        lookup.setCiphertext(encryptedPayload.ciphertext());
        lookup.setNonce(encryptedPayload.nonce());
        lookup.setKeyVersion(encryptedPayload.keyVersion());
        return updatedContent;
    }

    private MessageTextContent requireLiveLocationContent(MessageLookupEntity lookup) {
        MessageTextContent content = decodeMessageContent(lookup);
        if (!"LIVE_LOCATION".equals(content.messageType()) || content.liveLocation() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is not a live location");
        }
        return content;
    }

    private MessageTextContent normalizeForwardedContent(MessageTextContent sourceContent) {
        if (!"LIVE_LOCATION".equals(sourceContent.messageType()) || sourceContent.liveLocation() == null) {
            return sourceContent;
        }
        MessageLiveLocationPayload liveLocation = sourceContent.liveLocation();
        return toLiveLocationContent(
                sourceContent,
                new MessageLiveLocationPayload(
                        liveLocation.latitude(),
                        liveLocation.longitude(),
                        liveLocation.title(),
                        liveLocation.address(),
                        resolveForwardedLiveLocationDurationSeconds(liveLocation),
                        null,
                        null,
                        null,
                        null
                )
        );
    }

    private int resolveForwardedLiveLocationDurationSeconds(MessageLiveLocationPayload liveLocation) {
        if (liveLocation.livePeriodSeconds() != null
                && liveLocation.livePeriodSeconds() >= 60
                && liveLocation.livePeriodSeconds() <= 86_400) {
            return liveLocation.livePeriodSeconds();
        }
        if (liveLocation.expiresAt() != null) {
            long remainingSeconds = java.time.Duration.between(Instant.now(), liveLocation.expiresAt()).getSeconds();
            if (remainingSeconds > 0) {
                return (int) Math.max(60L, Math.min(86_400L, remainingSeconds));
            }
        }
        return 3_600;
    }

    private MessageTextContent toLiveLocationContent(
            MessageTextContent content,
            MessageLiveLocationPayload liveLocation
    ) {
        return new MessageTextContent(
                content.text(),
                content.entities(),
                content.messageType(),
                content.caption(),
                content.location(),
                liveLocation,
                content.contactCard(),
                content.serviceMessage(),
                content.silent()
        );
    }

    private MessageLiveLocationPayload resolveLiveLocationPayload(MessageTextContent content) {
        if (content == null || content.liveLocation() == null) {
            return null;
        }
        MessageLiveLocationPayload liveLocation = content.liveLocation();
        boolean active = liveLocation.stoppedAt() == null
                && liveLocation.expiresAt() != null
                && liveLocation.expiresAt().isAfter(Instant.now());
        return new MessageLiveLocationPayload(
                liveLocation.latitude(),
                liveLocation.longitude(),
                liveLocation.title(),
                liveLocation.address(),
                liveLocation.livePeriodSeconds(),
                liveLocation.expiresAt(),
                liveLocation.lastUpdatedAt(),
                liveLocation.stoppedAt(),
                active
        );
    }

    private MessageLiveLocationPayload mergeLiveLocationPayload(
            MessageLiveLocationPayload base,
            MessageLiveLocationPayload state
    ) {
        if (base == null) {
            return state;
        }
        if (state == null) {
            return base;
        }
        return new MessageLiveLocationPayload(
                state.latitude() != null ? state.latitude() : base.latitude(),
                state.longitude() != null ? state.longitude() : base.longitude(),
                state.title() != null ? state.title() : base.title(),
                state.address() != null ? state.address() : base.address(),
                base.livePeriodSeconds() != null ? base.livePeriodSeconds() : state.livePeriodSeconds(),
                state.expiresAt() != null ? state.expiresAt() : base.expiresAt(),
                state.lastUpdatedAt() != null ? state.lastUpdatedAt() : base.lastUpdatedAt(),
                state.stoppedAt() != null ? state.stoppedAt() : base.stoppedAt(),
                state.active() != null ? state.active() : base.active()
        );
    }

    private void validateLiveLocationRequestPayload(MessageLiveLocationPayload liveLocation) {
        if (liveLocation == null) {
            return;
        }
        Integer livePeriodSeconds = liveLocation.livePeriodSeconds();
        if (livePeriodSeconds == null || livePeriodSeconds < 60 || livePeriodSeconds > 86_400) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Live location duration is invalid");
        }
    }

    private MessageTextContent buildUserMessageContent(
            String text,
            String caption,
            List<MessageTextEntityPayload> entities,
            String requestedMessageType,
            MessageLocationPayload location,
            MessageLiveLocationPayload liveLocation,
            MessageContactCardPayload contactCard,
            Boolean silent,
            List<UUID> attachmentIds,
            UUID stickerId
    ) {
        int structuredPayloadCount = 0;
        structuredPayloadCount += location != null ? 1 : 0;
        structuredPayloadCount += liveLocation != null ? 1 : 0;
        structuredPayloadCount += contactCard != null ? 1 : 0;
        if (structuredPayloadCount > 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Location, live location, and contact card payloads cannot be combined"
            );
        }
        validateLiveLocationRequestPayload(liveLocation);
        String normalizedText = text != null ? text.trim() : "";
        String normalizedCaption = caption != null ? caption.trim() : "";
        String effectiveText = !normalizedText.isBlank() ? normalizedText : normalizedCaption;
        String resolvedMessageType = resolveUserMessageType(
                requestedMessageType,
                location,
                liveLocation,
                contactCard,
                attachmentIds,
                stickerId
        );
        String effectiveCaption = normalizedCaption;
        if (effectiveCaption.isBlank()
                && (!attachmentIds.isEmpty()
                || "LOCATION".equals(resolvedMessageType)
                || "LIVE_LOCATION".equals(resolvedMessageType)
                || "CONTACT_CARD".equals(resolvedMessageType))) {
            effectiveCaption = effectiveText;
        }
        return messageContentCodec.normalize(
                effectiveText,
                entities,
                resolvedMessageType,
                effectiveCaption,
                location,
                liveLocation,
                contactCard,
                null,
                silent
        );
    }

    private String resolveUserMessageType(
            String requestedMessageType,
            MessageLocationPayload location,
            MessageLiveLocationPayload liveLocation,
            MessageContactCardPayload contactCard,
            List<UUID> attachmentIds,
            UUID stickerId
    ) {
        String normalizedRequestedType = requestedMessageType != null ? requestedMessageType.trim().toUpperCase() : "";
        if (!normalizedRequestedType.isBlank()) {
            if ("SERVICE_MESSAGE".equals(normalizedRequestedType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service messages cannot be sent from the public API");
            }
            if (!"LOCATION".equals(normalizedRequestedType) && location != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location payload requires LOCATION messageType");
            }
            if (!"LIVE_LOCATION".equals(normalizedRequestedType) && liveLocation != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Live location payload requires LIVE_LOCATION messageType");
            }
            if (!"CONTACT_CARD".equals(normalizedRequestedType) && contactCard != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact card payload requires CONTACT_CARD messageType");
            }
            if ("LOCATION".equals(normalizedRequestedType) && location == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location payload is required");
            }
            if ("LIVE_LOCATION".equals(normalizedRequestedType) && liveLocation == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Live location payload is required");
            }
            if ("CONTACT_CARD".equals(normalizedRequestedType) && contactCard == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact card payload is required");
            }
            if (("LOCATION".equals(normalizedRequestedType)
                    || "LIVE_LOCATION".equals(normalizedRequestedType)
                    || "CONTACT_CARD".equals(normalizedRequestedType))
                    && (!attachmentIds.isEmpty() || stickerId != null)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Structured messages cannot be combined with stickers or attachments");
            }
            return normalizedRequestedType;
        }
        if (location != null) {
            if (!attachmentIds.isEmpty() || stickerId != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location messages cannot be combined with stickers or attachments");
            }
            return "LOCATION";
        }
        if (liveLocation != null) {
            if (!attachmentIds.isEmpty() || stickerId != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Live location messages cannot be combined with stickers or attachments");
            }
            return "LIVE_LOCATION";
        }
        if (contactCard != null) {
            if (!attachmentIds.isEmpty() || stickerId != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact cards cannot be combined with stickers or attachments");
            }
            return "CONTACT_CARD";
        }
        return null;
    }

    private boolean isMessageEmpty(MessageTextContent content, List<UUID> attachmentIds, UUID stickerId) {
        return content.text().isBlank()
                && content.location() == null
                && content.liveLocation() == null
                && content.contactCard() == null
                && content.serviceMessage() == null
                && attachmentIds.isEmpty()
                && stickerId == null;
    }

    private void publishDirectMessageCreatedEvent(ChatEntity chat, UUID senderId, MessageLookupEntity lookup) {
        if (chat == null || senderId == null || lookup == null || !"DIRECT".equals(chat.getChatType())) {
            return;
        }
        applicationEventPublisher.publishEvent(new DirectMessageCreatedEvent(chat.getId(), senderId, lookup.getCreatedAt()));
    }

    private Map<UUID, String> buildMessageSearchCorpora(List<MessageEntity> messages) {
        Map<UUID, MessageTextContent> contentByMessageId = new LinkedHashMap<>();
        Map<UUID, List<UUID>> attachmentIdsByMessageId = new LinkedHashMap<>();
        for (MessageEntity message : messages) {
            UUID messageId = message.getKey().getMessageId();
            contentByMessageId.put(messageId, decodeMessageContent(message));
            attachmentIdsByMessageId.put(messageId, message.getAttachmentIds() != null ? message.getAttachmentIds() : List.of());
        }
        return messageSearchCorpusService.buildSearchCorpora(contentByMessageId, attachmentIdsByMessageId);
    }

    private Map<UUID, String> buildTopicSearchCorpora(List<MessageTopicEntity> messages) {
        Map<UUID, MessageTextContent> contentByMessageId = new LinkedHashMap<>();
        Map<UUID, List<UUID>> attachmentIdsByMessageId = new LinkedHashMap<>();
        for (MessageTopicEntity message : messages) {
            UUID messageId = message.getKey().getMessageId();
            contentByMessageId.put(messageId, decodeMessageContent(message));
            attachmentIdsByMessageId.put(messageId, message.getAttachmentIds() != null ? message.getAttachmentIds() : List.of());
        }
        return messageSearchCorpusService.buildSearchCorpora(contentByMessageId, attachmentIdsByMessageId);
    }

    private Map<UUID, String> buildThreadSearchCorpora(List<MessageThreadEntity> messages) {
        Map<UUID, MessageTextContent> contentByMessageId = new LinkedHashMap<>();
        Map<UUID, List<UUID>> attachmentIdsByMessageId = new LinkedHashMap<>();
        for (MessageThreadEntity message : messages) {
            UUID messageId = message.getKey().getMessageId();
            contentByMessageId.put(messageId, decodeMessageContent(message));
            attachmentIdsByMessageId.put(messageId, message.getAttachmentIds() != null ? message.getAttachmentIds() : List.of());
        }
        return messageSearchCorpusService.buildSearchCorpora(contentByMessageId, attachmentIdsByMessageId);
    }

    private String resolveResponseMessageType(
            MessageTextContent content,
            UUID pollId,
            UUID stickerId,
            List<MessageAttachmentResponse> attachments
    ) {
        if (content.messageType() != null && !content.messageType().isBlank()) {
            return content.messageType();
        }
        if (pollId != null) {
            return "POLL";
        }
        if (stickerId != null) {
            return "STICKER";
        }
        if (attachments != null && !attachments.isEmpty()) {
            if (attachments.size() > 1) {
                return "ALBUM";
            }
            return attachments.get(0).kind();
        }
        return "TEXT";
    }

    private void persistMessage(MessageLookupEntity lookup) {
        messageStorageService.save(lookup);
        syncExpiration(lookup);
        publicPostSearchService.syncMessage(lookup);
    }

    private void publish(MessageLookupEntity lookup, List<UUID> recipientIds) {
        publish(lookup, recipientIds, null);
    }

    private void publish(MessageLookupEntity lookup, List<UUID> recipientIds, UUID clientMessageId) {
        MessageEvent event = new MessageEvent(
                lookup.getChatId(),
                lookup.getMessageId(),
                clientMessageId,
                lookup.getSenderId(),
                recipientIds,
                lookup.getViaBotUserId(),
                lookup.getTopicId(),
                lookup.getThreadRootMessageId(),
                lookup.getDiscussionChatId(),
                lookup.getDiscussionRootMessageId(),
                lookup.getCreatedAt(),
                lookup.getCiphertext(),
                lookup.getNonce(),
                lookup.getKeyVersion(),
                lookup.getReplyToMessageId(),
                lookup.getForwardedFromChatId(),
                lookup.getForwardedFromMessageId(),
                lookup.getPollId(),
                lookup.getStickerId(),
                lookup.getAttachmentIds(),
                lookup.getDeliveryStatus(),
                lookup.getDeliveredAt(),
                lookup.getReadAt(),
                lookup.getExpiresAt(),
                lookup.getEditedAt(),
                lookup.getDeletedAt()
        );
        chatMessagePublisher.publish(event);
    }

    private void publishToChatMembers(UUID requesterId, MessageLookupEntity lookup) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, lookup.getChatId());
        publish(lookup, chatService.getRecipientIds(chat, requesterId));
    }

    private void publishExpiredDeletion(MessageLookupEntity lookup) {
        ChatEntity chat = chatService.getChat(lookup.getChatId());
        publish(lookup, chatService.getRecipientIdsForSystem(chat, lookup.getSenderId()));
    }

    private void validateReplyTarget(UUID chatId, UUID topicId, UUID replyToMessageId) {
        if (replyToMessageId == null) {
            return;
        }
        MessageLookupEntity replyTo = messageLookupRepository.findById(replyToMessageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reply target not found"));
        if (replyTo.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reply target not found");
        }
        if (!replyTo.getChatId().equals(chatId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply target belongs to another chat");
        }
        if (!Objects.equals(replyTo.getTopicId(), topicId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply target belongs to another topic");
        }
    }

    private UUID resolveReplyTargetSenderId(UUID replyToMessageId) {
        if (replyToMessageId == null) {
            return null;
        }
        return messageLookupRepository.findById(replyToMessageId)
                .map(MessageLookupEntity::getSenderId)
                .orElse(null);
    }

    private MessageLookupEntity getOwnedMessage(UUID senderId, UUID messageId) {
        MessageLookupEntity lookup = getAccessibleMessage(senderId, messageId);
        if (!lookup.getSenderId().equals(senderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only sender can modify the message");
        }
        return lookup;
    }

    private MessageLookupEntity getAccessibleMessage(UUID requesterId, UUID messageId) {
        MessageLookupEntity lookup = messageLookupRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        ChatEntity chat = chatService.getOwnedChat(requesterId, lookup.getChatId());
        ensureMessageVisibleToRequester(chat, requesterId, lookup.getTopicId());
        return lookup;
    }

    private ChatEntity resolveTargetChat(UUID senderId, UUID chatId, UUID recipientUserId) {
        if (chatId != null) {
            return chatService.getOwnedChat(senderId, chatId);
        }
        if (recipientUserId != null) {
            return chatService.getOrCreateDirectChat(senderId, recipientUserId);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chatId or recipientUserId is required");
    }

    private int normalizeRepeatingIntervalMinutes(Integer intervalMinutes) {
        if (intervalMinutes == null || intervalMinutes < 1 || intervalMinutes > 10_080) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Repeating interval must be between 1 and 10080 minutes"
            );
        }
        return intervalMinutes;
    }

    private Integer normalizeRepeatingMaxOccurrences(Integer maxOccurrences) {
        if (maxOccurrences == null) {
            return null;
        }
        if (maxOccurrences < 1 || maxOccurrences > 365) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Repeating maxOccurrences must be between 1 and 365"
            );
        }
        return maxOccurrences;
    }

    private List<UUID> normalizeAttachmentIds(List<UUID> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(attachmentIds));
    }

    private String encodeAttachmentIds(List<UUID> attachmentIds) {
        return normalizeAttachmentIds(attachmentIds).stream()
                .map(UUID::toString)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private List<UUID> parseAttachmentIds(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(encoded.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(UUID::fromString)
                .toList();
    }

    private void syncExpiration(MessageLookupEntity lookup) {
        if (lookup.getExpiresAt() == null) {
            return;
        }

        MessageExpirationEntity expiration = messageExpirationRepository.findById(lookup.getMessageId())
                .orElseGet(() -> {
                    MessageExpirationEntity entity = new MessageExpirationEntity();
                    entity.setMessageId(lookup.getMessageId());
                    return entity;
                });
        expiration.setChatId(lookup.getChatId());
        expiration.setExpiresAt(lookup.getExpiresAt());
        if (lookup.getDeletedAt() != null && expiration.getProcessedAt() == null) {
            expiration.setProcessedAt(lookup.getDeletedAt());
        }
        messageExpirationRepository.save(expiration);
    }

    private void ensureCanClosePoll(UUID requesterId, MessageLookupEntity lookup, PollEntity poll) {
        if (poll.getCreatedByUserId().equals(requesterId)) {
            return;
        }

        ChatEntity chat = chatService.getOwnedChat(requesterId, lookup.getChatId());
        if (!List.of("GROUP", "CHANNEL").contains(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only poll creator can close this poll");
        }

        if (!chatService.hasMessageModerationPermission(requesterId, chat.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only poll creator or chat admins can close this poll");
        }
    }
}
