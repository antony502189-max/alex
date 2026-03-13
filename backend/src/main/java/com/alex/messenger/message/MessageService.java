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
import com.alex.messenger.message.dto.CreatePollMessageRequest;
import com.alex.messenger.message.dto.EditMessageRequest;
import com.alex.messenger.message.dto.ForwardMessageRequest;
import com.alex.messenger.message.dto.ScheduleMessageRequest;
import com.alex.messenger.message.dto.ScheduledMessageResponse;
import com.alex.messenger.message.dto.MessageAttachmentResponse;
import com.alex.messenger.message.dto.MessageContactCardPayload;
import com.alex.messenger.message.dto.MessageLocationPayload;
import com.alex.messenger.message.dto.MessageReactionSummary;
import com.alex.messenger.message.dto.MessageServicePayload;
import com.alex.messenger.message.dto.MessageTextEntityPayload;
import com.alex.messenger.message.dto.SearchMessagesResponse;
import com.alex.messenger.message.dto.SendMessageRequest;
import com.alex.messenger.message.dto.SendInlineBotResultRequest;
import com.alex.messenger.message.dto.VotePollRequest;
import com.alex.messenger.message.idempotency.MessageIdempotencyService;
import com.alex.messenger.message.expiration.MessageExpirationEntity;
import com.alex.messenger.message.expiration.MessageExpirationRepository;
import com.alex.messenger.message.scheduled.ScheduledMessageEntity;
import com.alex.messenger.message.scheduled.ScheduledMessageRepository;
import com.alex.messenger.poll.PollEntity;
import com.alex.messenger.poll.PollService;
import com.alex.messenger.sticker.StickerService;
import java.time.Instant;
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

    private final MessageRepository messageRepository;
    private final MessageTopicRepository messageTopicRepository;
    private final MessageThreadRepository messageThreadRepository;
    private final MessageLookupRepository messageLookupRepository;
    private final MessageReactionService messageReactionService;
    private final MessageExpirationRepository messageExpirationRepository;
    private final ScheduledMessageRepository scheduledMessageRepository;
    private final MessageStorageService messageStorageService;
    private final AttachmentService attachmentService;
    private final ChatAdminLogService chatAdminLogService;
    private final ChatService chatService;
    private final ForumTopicService forumTopicService;
    private final ChatEncryptionService chatEncryptionService;
    private final MessageContentCodec messageContentCodec;
    private final MessageSearchCorpusService messageSearchCorpusService;
    private final MessageTranslationCacheRepository messageTranslationCacheRepository;
    private final MessageIdempotencyService messageIdempotencyService;
    private final PollService pollService;
    private final StickerService stickerService;
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
                        attachmentService.getResponses(existing.getAttachmentIds()),
                        request.clientMessageId()
                );
            }
        }
        List<UUID> recipientIds = chatService.getRecipientIds(chat, senderId);

        persistMessage(lookup);
        chatService.recordMessageSent(chat.getId(), senderId, lookup.getCreatedAt());
        linkDiscussionThreadIfNeeded(chat, senderId, lookup);
        chatService.updateLastMessageAt(chat, lookup.getCreatedAt());
        forumTopicService.touchTopic(lookup.getTopicId(), lookup.getCreatedAt());
        chatService.incrementUnreadCounts(
                chat.getId(),
                senderId,
                content,
                replyTarget != null ? replyTarget.getSenderId() : null
        );
        publish(lookup, recipientIds, request.clientMessageId());
        botUpdateService.maybeEnqueueIncomingMessage(chat, senderId, lookup);
        botService.maybeReplyToDirectMessage(chat, senderId, lookup);
        publishDirectMessageCreatedEvent(chat, senderId, lookup);
        if (request.clientMessageId() != null) {
            messageIdempotencyService.markCompleted(senderId, request.clientMessageId(), lookup.getMessageId());
        }

        return toResponse(senderId, lookup, List.of(), attachmentService.getResponses(attachmentIds), request.clientMessageId());
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
                        attachmentService.getResponses(existing.getAttachmentIds()),
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
                replyTarget != null ? replyTarget.getSenderId() : null
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
    public ScheduledMessageResponse sendWhenOnline(UUID senderId, SendMessageRequest request) {
        List<UUID> attachmentIds = normalizeAttachmentIds(request.attachmentIds());
        MessageTextContent content = buildUserMessageContent(
                request.text(),
                request.caption(),
                request.entities(),
                request.messageType(),
                request.location(),
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
        ForumTopicEntity topic = forumTopicService.resolveTopicForRead(chat, requesterId, topicId);
        UUID resolvedThreadRootMessageId = resolveThreadRootForRead(requesterId, chatId, threadRootMessageId);
        return scheduledMessageRepository.findAllBySenderIdAndChatIdAndStatusInOrderByScheduledAtAsc(
                        requesterId,
                        chatId,
                        List.of("PENDING", "WAITING_ONLINE")
                ).stream()
                .filter(message -> Objects.equals(message.getTopicId(), topic != null ? topic.getId() : null))
                .filter(message -> Objects.equals(message.getThreadRootMessageId(), resolvedThreadRootMessageId))
                .map(this::toScheduledResponse)
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
            ensureScheduledCommentPolicyAllowsWrite(chat, scheduledMessage);

            MessageLookupEntity lookup = buildMessageLookupFromEncrypted(
                    chat,
                    scheduledMessage.getSenderId(),
                    scheduledMessage.getTopicId(),
                    scheduledMessage.getReplyToMessageId(),
                    null,
                    null,
                    null,
                    scheduledMessage.getStickerId(),
                    parseAttachmentIds(scheduledMessage.getAttachmentIds()),
                    scheduledMessage.getCiphertext(),
                    scheduledMessage.getNonce(),
                    scheduledMessage.getKeyVersion()
            );
            applyThreadMetadata(
                    lookup,
                    null,
                    scheduledMessage.getThreadRootMessageId(),
                    scheduledMessage.getDiscussionRootMessageId() != null
                            ? scheduledMessage.getDiscussionChatId()
                            : null
            );
            lookup.setDiscussionRootMessageId(scheduledMessage.getDiscussionRootMessageId());
            List<UUID> recipientIds = chatService.getRecipientIds(chat, scheduledMessage.getSenderId());

            persistMessage(lookup);
            chatService.recordMessageSent(chat.getId(), scheduledMessage.getSenderId(), lookup.getCreatedAt());
            linkDiscussionThreadIfNeeded(chat, scheduledMessage.getSenderId(), lookup);
            chatService.updateLastMessageAt(chat, lookup.getCreatedAt());
            forumTopicService.touchTopic(lookup.getTopicId(), lookup.getCreatedAt());
        chatService.incrementUnreadCounts(
                chat.getId(),
                scheduledMessage.getSenderId(),
                decodeMessageContent(lookup),
                resolveReplyTargetSenderId(scheduledMessage.getReplyToMessageId())
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
                        attachmentService.getResponses(existing.getAttachmentIds()),
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
                replyTarget != null ? replyTarget.getSenderId() : null
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
                        attachmentService.getResponses(existing.getAttachmentIds()),
                        request.clientMessageId()
                );
            }
        }

        MessageLookupEntity lookup = buildNewMessage(
                targetChat,
                senderId,
                sourceContent,
                topic != null ? topic.getId() : null,
                request.replyToMessageId(),
                source.getChatId(),
                source.getMessageId(),
                source.getPollId(),
                source.getStickerId(),
                source.getAttachmentIds() != null ? source.getAttachmentIds() : List.of(),
                reservedMessageId
        );
        lookup.setViaBotUserId(source.getViaBotUserId());
        applyThreadMetadata(lookup, replyTarget, null, null);
        List<UUID> recipientIds = chatService.getRecipientIds(targetChat, senderId);

        persistMessage(lookup);
        chatService.recordMessageSent(targetChat.getId(), senderId, lookup.getCreatedAt());
        linkDiscussionThreadIfNeeded(targetChat, senderId, lookup);
        chatService.updateLastMessageAt(targetChat, lookup.getCreatedAt());
        forumTopicService.touchTopic(lookup.getTopicId(), lookup.getCreatedAt());
        chatService.incrementUnreadCounts(
                targetChat.getId(),
                senderId,
                sourceContent,
                replyTarget != null ? replyTarget.getSenderId() : null
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
                attachmentService.getResponses(lookup.getAttachmentIds()),
                request.clientMessageId()
        );
    }

    @Transactional
    public ChatMessageResponse editMessage(UUID senderId, UUID messageId, EditMessageRequest request) {
        MessageTextContent content = messageContentCodec.normalize(request.text().trim(), request.entities());
        if (content.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message text is blank");
        }

        MessageLookupEntity lookup = getOwnedMessage(senderId, messageId);
        if (lookup.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deleted message cannot be edited");
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
                attachmentService.getResponses(lookup.getAttachmentIds())
        );
    }

    @Transactional
    public ChatMessageResponse deleteMessage(UUID senderId, UUID messageId) {
        MessageLookupEntity lookup = getOwnedMessage(senderId, messageId);
        if (lookup.getDeletedAt() == null) {
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
                attachmentService.getResponses(lookup.getAttachmentIds())
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
                attachmentService.getResponses(lookup.getAttachmentIds())
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
                attachmentService.getResponses(lookup.getAttachmentIds())
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
                attachmentService.getResponses(lookup.getAttachmentIds())
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
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        ForumTopicEntity topic = forumTopicService.resolveTopicForRead(chat, requesterId, topicId);
        UUID resolvedThreadRootMessageId = resolveThreadRootForRead(requesterId, chatId, threadRootMessageId);
        int normalizedLimit = Math.min(Math.max(limit, 1), 100);

        if (resolvedThreadRootMessageId != null) {
            List<MessageThreadEntity> messages = before == null
                    ? messageThreadRepository.findRecentByThreadRootMessageId(resolvedThreadRootMessageId, normalizedLimit)
                    : messageThreadRepository.findRecentByThreadRootMessageIdBefore(
                            resolvedThreadRootMessageId,
                            before,
                            normalizedLimit
                    );
            Collections.reverse(messages);
            Map<UUID, List<MessageReactionSummary>> reactions =
                    messageReactionService.getSummaries(messages.stream().map(message -> message.getKey().getMessageId()).toList());

            return messages.stream()
                    .map(message -> toResponse(
                            requesterId,
                            message,
                            reactions.getOrDefault(message.getKey().getMessageId(), List.of()),
                            attachmentService.getResponses(message.getAttachmentIds())
                    ))
                    .toList();
        }

        if (topic != null) {
            List<MessageTopicEntity> messages = before == null
                    ? messageTopicRepository.findRecentByTopicId(topic.getId(), normalizedLimit)
                    : messageTopicRepository.findRecentByTopicIdBefore(topic.getId(), before, normalizedLimit);
            Collections.reverse(messages);
            Map<UUID, List<MessageReactionSummary>> reactions =
                    messageReactionService.getSummaries(messages.stream().map(message -> message.getKey().getMessageId()).toList());

            return messages.stream()
                    .map(message -> toResponse(
                            requesterId,
                            message,
                            reactions.getOrDefault(message.getKey().getMessageId(), List.of()),
                            attachmentService.getResponses(message.getAttachmentIds())
                    ))
                    .toList();
        }

        List<MessageEntity> messages = before == null
                ? messageRepository.findRecentByChatId(chat.getId(), normalizedLimit)
                : messageRepository.findRecentByChatIdBefore(chat.getId(), before, normalizedLimit);
        Collections.reverse(messages);
        Map<UUID, List<MessageReactionSummary>> reactions =
                messageReactionService.getSummaries(messages.stream().map(message -> message.getKey().getMessageId()).toList());

        return messages.stream()
                .map(message -> toResponse(
                        requesterId,
                        message,
                        reactions.getOrDefault(message.getKey().getMessageId(), List.of()),
                        attachmentService.getResponses(message.getAttachmentIds())
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
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        ForumTopicEntity topic = forumTopicService.resolveTopicForRead(chat, requesterId, topicId);
        UUID resolvedThreadRootMessageId = resolveThreadRootForRead(requesterId, chatId, threadRootMessageId);
        String normalizedQuery = query.trim().toLowerCase();
        if (normalizedQuery.isBlank()) {
            return new SearchMessagesResponse(query, List.of());
        }

        int normalizedLimit = Math.min(Math.max(limit, 1), 100);
        if (resolvedThreadRootMessageId != null) {
            List<MessageThreadEntity> threadMessages = messageThreadRepository.findAllByThreadRootMessageId(resolvedThreadRootMessageId).stream()
                    .filter(message -> message.getDeletedAt() == null)
                    .toList();
            Map<UUID, String> searchCorpora = buildThreadSearchCorpora(threadMessages);
            List<MessageThreadEntity> matches = threadMessages.stream()
                    .filter(message -> searchCorpora
                            .getOrDefault(message.getKey().getMessageId(), "")
                            .contains(normalizedQuery))
                    .sorted(java.util.Comparator.comparing(MessageThreadEntity::getCreatedAt).reversed())
                    .limit(normalizedLimit)
                    .toList();

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
                                    attachmentService.getResponses(message.getAttachmentIds())
                            ))
                            .toList()
            );
        }

        if (topic != null) {
            List<MessageTopicEntity> topicMessages = messageTopicRepository.findAllByTopicId(topic.getId()).stream()
                    .filter(message -> message.getDeletedAt() == null)
                    .toList();
            Map<UUID, String> searchCorpora = buildTopicSearchCorpora(topicMessages);
            List<MessageTopicEntity> matches = topicMessages.stream()
                    .filter(message -> searchCorpora
                            .getOrDefault(message.getKey().getMessageId(), "")
                            .contains(normalizedQuery))
                    .sorted(java.util.Comparator.comparing(MessageTopicEntity::getCreatedAt).reversed())
                    .limit(normalizedLimit)
                    .toList();

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
                                    attachmentService.getResponses(message.getAttachmentIds())
                            ))
                            .toList()
            );
        }

        List<MessageEntity> chatMessages = messageRepository.findAllByChatId(chat.getId()).stream()
                .filter(message -> message.getDeletedAt() == null)
                .toList();
        Map<UUID, String> searchCorpora = buildMessageSearchCorpora(chatMessages);
        List<MessageEntity> matches = chatMessages.stream()
                .filter(message -> searchCorpora
                        .getOrDefault(message.getKey().getMessageId(), "")
                        .contains(normalizedQuery))
                .sorted(java.util.Comparator.comparing(MessageEntity::getCreatedAt).reversed())
                .limit(normalizedLimit)
                .toList();

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
                                attachmentService.getResponses(message.getAttachmentIds())
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
        String normalizedQuery = query.trim().toLowerCase();
        if (normalizedQuery.isBlank() || chatIds.isEmpty()) {
            return List.of();
        }

        int normalizedLimit = Math.min(Math.max(limit, 1), 50);
        List<MessageEntity> candidateMessages = chatIds.stream()
                .flatMap(chatId -> messageRepository.findAllByChatId(chatId).stream())
                .filter(message -> message.getDeletedAt() == null)
                .toList();
        Map<UUID, String> searchCorpora = buildMessageSearchCorpora(candidateMessages);
        List<MessageEntity> matches = candidateMessages.stream()
                .filter(message -> searchCorpora
                        .getOrDefault(message.getKey().getMessageId(), "")
                        .contains(normalizedQuery))
                .sorted(java.util.Comparator.comparing(MessageEntity::getCreatedAt).reversed())
                .limit(normalizedLimit)
                .toList();

        Map<UUID, List<MessageReactionSummary>> reactions =
                messageReactionService.getSummaries(matches.stream().map(message -> message.getKey().getMessageId()).toList());

        return matches.stream()
                .map(message -> toResponse(
                        requesterId,
                        message,
                        reactions.getOrDefault(message.getKey().getMessageId(), List.of()),
                        attachmentService.getResponses(message.getAttachmentIds())
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
                attachmentService.getResponses(lookup.getAttachmentIds())
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
        UUID resolvedMessageId = messageId != null ? messageId : Uuids.timeBased();
        Instant createdAt = Instant.ofEpochMilli(Uuids.unixTimestamp(resolvedMessageId));
        List<UUID> recipientIds = chatService.getRecipientIds(chat, senderId);
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

    private UUID resolveThreadRootForRead(UUID requesterId, UUID chatId, UUID threadRootMessageId) {
        if (threadRootMessageId == null) {
            return null;
        }
        MessageLookupEntity root = getAccessibleMessage(requesterId, threadRootMessageId);
        if (!root.getChatId().equals(chatId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thread belongs to another chat");
        }
        return root.getThreadRootMessageId() != null ? root.getThreadRootMessageId() : root.getMessageId();
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

    private void ensureScheduledCommentPolicyAllowsWrite(ChatEntity chat, ScheduledMessageEntity scheduledMessage) {
        if (!"GROUP".equals(chat.getChatType()) || scheduledMessage.getThreadRootMessageId() == null) {
            return;
        }

        MessageLookupEntity threadRoot = messageLookupRepository.findById(scheduledMessage.getThreadRootMessageId())
                .orElse(null);
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
        MessageLookupEntity rootLookup = buildNewMessage(
                discussionChat,
                senderId,
                content,
                discussionTopicId,
                null,
                chat.getId(),
                lookup.getMessageId(),
                lookup.getPollId(),
                lookup.getStickerId(),
                lookup.getAttachmentIds() != null ? lookup.getAttachmentIds() : List.of()
        );
        rootLookup.setViaBotUserId(lookup.getViaBotUserId());
        rootLookup.setThreadRootMessageId(rootLookup.getMessageId());
        rootLookup.setDiscussionChatId(discussionChat.getId());
        rootLookup.setDiscussionRootMessageId(rootLookup.getMessageId());

        persistMessage(rootLookup);
        chatService.updateLastMessageAt(discussionChat, rootLookup.getCreatedAt());
        forumTopicService.touchTopic(rootLookup.getTopicId(), rootLookup.getCreatedAt());
        chatService.incrementUnreadCounts(discussionChat.getId(), senderId, content, null);
        publish(rootLookup, chatService.getRecipientIds(discussionChat, senderId));

        lookup.setDiscussionChatId(discussionChat.getId());
        lookup.setDiscussionRootMessageId(rootLookup.getMessageId());
        persistMessage(lookup);
    }

    private ScheduledMessageResponse toScheduledResponse(ScheduledMessageEntity scheduledMessage) {
        MessageTextContent content = decodeMessageContent(
                scheduledMessage.getChatId(),
                scheduledMessage.getCiphertext(),
                scheduledMessage.getNonce(),
                scheduledMessage.getKeyVersion()
        );
        List<MessageAttachmentResponse> attachments =
                attachmentService.getResponses(parseAttachmentIds(scheduledMessage.getAttachmentIds()));
        return new ScheduledMessageResponse(
                scheduledMessage.getId(),
                scheduledMessage.getClientMessageId(),
                scheduledMessage.getChatId(),
                scheduledMessage.getSenderId(),
                scheduledMessage.getTopicId(),
                scheduledMessage.getThreadRootMessageId(),
                scheduledMessage.getDiscussionChatId(),
                scheduledMessage.getDiscussionRootMessageId(),
                content.text(),
                content.entities(),
                resolveResponseMessageType(content, null, scheduledMessage.getStickerId(), attachments),
                content.caption(),
                content.silent(),
                content.location(),
                content.contactCard(),
                content.serviceMessage(),
                scheduledMessage.getReplyToMessageId(),
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
                return toScheduledResponse(existing);
            }
        }

        try {
            return toScheduledResponse(scheduledMessageRepository.save(scheduledMessage));
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
            return toScheduledResponse(existing);
        }
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
                lookup.getThreadRootMessageId(),
                lookup.getDiscussionChatId(),
                lookup.getDiscussionRootMessageId(),
                countComments(lookup.getDiscussionRootMessageId()),
                content.text(),
                content.entities(),
                resolveResponseMessageType(content, lookup.getPollId(), lookup.getStickerId(), attachments),
                content.caption(),
                content.silent(),
                content.location(),
                content.contactCard(),
                content.serviceMessage(),
                lookup.getCreatedAt(),
                lookup.getReplyToMessageId(),
                lookup.getForwardedFromChatId(),
                lookup.getForwardedFromMessageId(),
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
                message.getThreadRootMessageId(),
                message.getDiscussionChatId(),
                message.getDiscussionRootMessageId(),
                countComments(message.getDiscussionRootMessageId()),
                content.text(),
                content.entities(),
                resolveResponseMessageType(content, message.getPollId(), message.getStickerId(), attachments),
                content.caption(),
                content.silent(),
                content.location(),
                content.contactCard(),
                content.serviceMessage(),
                message.getCreatedAt(),
                message.getReplyToMessageId(),
                message.getForwardedFromChatId(),
                message.getForwardedFromMessageId(),
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
                message.getThreadRootMessageId(),
                message.getDiscussionChatId(),
                message.getDiscussionRootMessageId(),
                countComments(message.getDiscussionRootMessageId()),
                content.text(),
                content.entities(),
                resolveResponseMessageType(content, message.getPollId(), message.getStickerId(), attachments),
                content.caption(),
                content.silent(),
                content.location(),
                content.contactCard(),
                content.serviceMessage(),
                message.getCreatedAt(),
                message.getReplyToMessageId(),
                message.getForwardedFromChatId(),
                message.getForwardedFromMessageId(),
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
                message.getKey().getThreadRootMessageId(),
                message.getDiscussionChatId(),
                message.getDiscussionRootMessageId(),
                countComments(message.getDiscussionRootMessageId()),
                content.text(),
                content.entities(),
                resolveResponseMessageType(content, message.getPollId(), message.getStickerId(), attachments),
                content.caption(),
                content.silent(),
                content.location(),
                content.contactCard(),
                content.serviceMessage(),
                message.getCreatedAt(),
                message.getReplyToMessageId(),
                message.getForwardedFromChatId(),
                message.getForwardedFromMessageId(),
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
        return Math.max(0, messageThreadRepository.findAllByThreadRootMessageId(discussionRootMessageId).size() - 1);
    }

    private MessageTextContent decodeMessageContent(UUID chatId, String ciphertext, String nonce, int keyVersion) {
        return messageContentCodec.decode(
                chatEncryptionService.decrypt(chatId, ciphertext, nonce, keyVersion)
        );
    }

    private MessageTextContent buildUserMessageContent(
            String text,
            String caption,
            List<MessageTextEntityPayload> entities,
            String requestedMessageType,
            MessageLocationPayload location,
            MessageContactCardPayload contactCard,
            Boolean silent,
            List<UUID> attachmentIds,
            UUID stickerId
    ) {
        if (location != null && contactCard != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location and contact card cannot be combined");
        }
        String normalizedText = text != null ? text.trim() : "";
        String normalizedCaption = caption != null ? caption.trim() : "";
        String effectiveText = !normalizedText.isBlank() ? normalizedText : normalizedCaption;
        String resolvedMessageType = resolveUserMessageType(requestedMessageType, location, contactCard, attachmentIds, stickerId);
        String effectiveCaption = normalizedCaption;
        if (effectiveCaption.isBlank() && (!attachmentIds.isEmpty() || "LOCATION".equals(resolvedMessageType) || "CONTACT_CARD".equals(resolvedMessageType))) {
            effectiveCaption = effectiveText;
        }
        return messageContentCodec.normalize(
                effectiveText,
                entities,
                resolvedMessageType,
                effectiveCaption,
                location,
                contactCard,
                null,
                silent
        );
    }

    private String resolveUserMessageType(
            String requestedMessageType,
            MessageLocationPayload location,
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
            if (!"CONTACT_CARD".equals(normalizedRequestedType) && contactCard != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact card payload requires CONTACT_CARD messageType");
            }
            if ("LOCATION".equals(normalizedRequestedType) && location == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location payload is required");
            }
            if ("CONTACT_CARD".equals(normalizedRequestedType) && contactCard == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact card payload is required");
            }
            if (("LOCATION".equals(normalizedRequestedType) || "CONTACT_CARD".equals(normalizedRequestedType))
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
        chatService.getOwnedChat(requesterId, lookup.getChatId());
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
