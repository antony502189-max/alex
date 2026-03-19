package com.alex.messenger.bot;

import com.alex.messenger.attachment.AttachmentService;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.bot.dto.BotApiAnswerInlineQueryRequest;
import com.alex.messenger.bot.dto.BotApiAnswerPreCheckoutQueryRequest;
import com.alex.messenger.bot.dto.BotApiAnswerCallbackQueryRequest;
import com.alex.messenger.bot.dto.BotApiRefundPaymentRequest;
import com.alex.messenger.bot.dto.BotApiAnswerWebAppQueryRequest;
import com.alex.messenger.bot.dto.BotApiDeleteMessageRequest;
import com.alex.messenger.bot.dto.BotApiDeleteMessageResponse;
import com.alex.messenger.bot.dto.BotApiEditMessageTextRequest;
import com.alex.messenger.bot.dto.BotApiSendInvoiceRequest;
import com.alex.messenger.bot.dto.BotApiSendAttachmentMessageRequest;
import com.alex.messenger.bot.dto.BotApiSendMediaGroupRequest;
import com.alex.messenger.bot.dto.BotApiSendMessageRequest;
import com.alex.messenger.bot.dto.BotApiSetMyCommandsRequest;
import com.alex.messenger.bot.dto.BotCallbackQueryResponse;
import com.alex.messenger.bot.dto.BotCommandResponse;
import com.alex.messenger.bot.dto.BotInlineResultResponse;
import com.alex.messenger.bot.dto.BotPaymentInvoiceResponse;
import com.alex.messenger.bot.dto.BotPaymentReceiptResponse;
import com.alex.messenger.bot.dto.BotPreCheckoutQueryResponse;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.EditMessageRequest;
import com.alex.messenger.message.dto.SendMessageRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BotApiService {

    private final MessageService messageService;
    private final AttachmentService attachmentService;
    private final ChatService chatService;
    private final BotCommandService botCommandService;
    private final BotInlineResultCacheService botInlineResultCacheService;
    private final BotMessageActionService botMessageActionService;
    private final BotCallbackQueryService botCallbackQueryService;
    private final BotWebAppService botWebAppService;
    private final BotPaymentService botPaymentService;

    @Transactional
    public ChatMessageResponse sendMessage(UUID botUserId, BotApiSendMessageRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Send message payload is required");
        }
        requireTarget(request.chatId(), request.recipientUserId());
        requireValidSendMessageRequest(request);
        ChatMessageResponse response = messageService.sendMessage(
                botUserId,
                new SendMessageRequest(
                        request.chatId(),
                        request.recipientUserId(),
                        request.topicId(),
                        request.replyToMessageId(),
                        request.text(),
                        request.caption(),
                        request.messageType(),
                        request.entities(),
                        request.location(),
                        request.contactCard(),
                        request.attachmentIds(),
                        request.stickerId(),
                        request.silent(),
                        request.clientMessageId()
                )
        );
        botMessageActionService.saveMessageActions(botUserId, response.messageId(), request.actions());
        return response;
    }

    @Transactional
    public ChatMessageResponse sendPhoto(UUID botUserId, BotApiSendAttachmentMessageRequest request) {
        return sendAttachmentMessage(botUserId, request, "PHOTO");
    }

    @Transactional
    public ChatMessageResponse sendVideo(UUID botUserId, BotApiSendAttachmentMessageRequest request) {
        return sendAttachmentMessage(botUserId, request, "VIDEO");
    }

    @Transactional
    public ChatMessageResponse sendAnimation(UUID botUserId, BotApiSendAttachmentMessageRequest request) {
        return sendAttachmentMessage(botUserId, request, "ANIMATION");
    }

    @Transactional
    public ChatMessageResponse sendDocument(UUID botUserId, BotApiSendAttachmentMessageRequest request) {
        return sendAttachmentMessage(botUserId, request, "DOCUMENT");
    }

    @Transactional
    public ChatMessageResponse sendVoice(UUID botUserId, BotApiSendAttachmentMessageRequest request) {
        return sendAttachmentMessage(botUserId, request, "VOICE");
    }

    @Transactional
    public ChatMessageResponse sendAudio(UUID botUserId, BotApiSendAttachmentMessageRequest request) {
        return sendAttachmentMessage(botUserId, request, "AUDIO");
    }

    @Transactional
    public ChatMessageResponse sendVideoNote(UUID botUserId, BotApiSendAttachmentMessageRequest request) {
        return sendAttachmentMessage(botUserId, request, "VIDEO_NOTE");
    }

    @Transactional
    public ChatMessageResponse sendMediaGroup(UUID botUserId, BotApiSendMediaGroupRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media group payload is required");
        }
        ChatEntity targetChat = resolveTargetChat(botUserId, request.chatId(), request.recipientUserId());
        List<UUID> clonedAttachmentIds = attachmentService.cloneAttachmentsToChatAsAlbum(
                botUserId,
                targetChat.getId(),
                request.attachmentIds()
        );
        ChatMessageResponse response = messageService.sendMessage(
                botUserId,
                new SendMessageRequest(
                        targetChat.getId(),
                        null,
                        request.topicId(),
                        request.replyToMessageId(),
                        null,
                        request.caption(),
                        "ALBUM",
                        request.entities(),
                        null,
                        null,
                        clonedAttachmentIds,
                        null,
                        request.silent(),
                        request.clientMessageId()
                )
        );
        botMessageActionService.saveMessageActions(botUserId, response.messageId(), request.actions());
        return response;
    }

    @Transactional
    public ChatMessageResponse editMessageText(UUID botUserId, BotApiEditMessageTextRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Edit message payload is required");
        }
        return messageService.editMessage(
                botUserId,
                request.messageId(),
                new EditMessageRequest(request.text(), request.entities())
        );
    }

    @Transactional
    public BotApiDeleteMessageResponse deleteMessage(UUID botUserId, BotApiDeleteMessageRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delete message payload is required");
        }
        ChatMessageResponse response = messageService.deleteMessage(botUserId, request.messageId());
        return new BotApiDeleteMessageResponse(response.messageId(), response.deletedAt() != null);
    }

    @Transactional(readOnly = true)
    public List<BotCommandResponse> getMyCommands(UUID botUserId) {
        return botCommandService.getConfiguredCommands(botUserId);
    }

    @Transactional
    public List<BotCommandResponse> setMyCommands(UUID botUserId, BotApiSetMyCommandsRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Commands payload is required");
        }
        return botCommandService.replaceCommands(botUserId, request.commands());
    }

    @Transactional
    public List<BotInlineResultResponse> answerInlineQuery(UUID botUserId, BotApiAnswerInlineQueryRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inline query answer payload is required");
        }
        return botInlineResultCacheService.replaceCachedResults(botUserId, request);
    }

    @Transactional
    public BotCallbackQueryResponse answerCallbackQuery(UUID botUserId, BotApiAnswerCallbackQueryRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Callback answer payload is required");
        }
        return botCallbackQueryService.answerCallbackQuery(botUserId, request);
    }

    @Transactional
    public ChatMessageResponse answerWebAppQuery(UUID botUserId, BotApiAnswerWebAppQueryRequest request) {
        return botWebAppService.answerQuery(botUserId, request);
    }

    @Transactional
    public BotPaymentInvoiceResponse sendInvoice(UUID botUserId, BotApiSendInvoiceRequest request) {
        return botPaymentService.sendInvoice(botUserId, request);
    }

    @Transactional
    public BotPreCheckoutQueryResponse answerPreCheckoutQuery(
            UUID botUserId,
            BotApiAnswerPreCheckoutQueryRequest request
    ) {
        return botPaymentService.answerPreCheckoutQuery(botUserId, request);
    }

    @Transactional
    public BotPaymentReceiptResponse refundPayment(UUID botUserId, BotApiRefundPaymentRequest request) {
        return botPaymentService.refundPayment(botUserId, request);
    }

    private ChatMessageResponse sendAttachmentMessage(
            UUID botUserId,
            BotApiSendAttachmentMessageRequest request,
            String messageType
    ) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment message payload is required");
        }
        requireTarget(request.chatId(), request.recipientUserId());
        ChatMessageResponse response = messageService.sendMessage(
                botUserId,
                new SendMessageRequest(
                        request.chatId(),
                        request.recipientUserId(),
                        request.topicId(),
                        request.replyToMessageId(),
                        null,
                        request.caption(),
                        messageType,
                        request.entities(),
                        null,
                        null,
                        List.of(request.attachmentId()),
                        null,
                        request.silent(),
                        request.clientMessageId()
                )
        );
        botMessageActionService.saveMessageActions(botUserId, response.messageId(), request.actions());
        return response;
    }

    private ChatEntity resolveTargetChat(UUID botUserId, UUID chatId, UUID recipientUserId) {
        if (chatId != null) {
            return chatService.getOwnedChat(botUserId, chatId);
        }
        if (recipientUserId != null) {
            return chatService.getOrCreateDirectChat(botUserId, recipientUserId);
        }
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "chatId or recipientUserId is required"
        );
    }

    private void requireTarget(UUID chatId, UUID recipientUserId) {
        if (chatId == null && recipientUserId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chatId or recipientUserId is required");
        }
    }

    private void requireValidSendMessageRequest(BotApiSendMessageRequest request) {
        if (!request.hasPayload()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Message must contain text, attachments, sticker, or structured payload"
            );
        }
        if (!request.hasAtMostOneStructuredPayload()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Location and contact card payloads cannot be combined"
            );
        }
        if (!request.isPublicMessageType()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Service messages cannot be sent from the bot API"
            );
        }
        if (!request.hasValidStructuredPayloadUsage()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Structured message payload and messageType combination is invalid"
            );
        }
    }
}
