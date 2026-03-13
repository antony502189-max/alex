package com.alex.messenger.bot;

import com.alex.messenger.bot.dto.BotApiAnswerInlineQueryRequest;
import com.alex.messenger.bot.dto.BotApiAnswerPreCheckoutQueryRequest;
import com.alex.messenger.bot.dto.BotApiAnswerCallbackQueryRequest;
import com.alex.messenger.bot.dto.BotApiRefundPaymentRequest;
import com.alex.messenger.bot.dto.BotApiAnswerWebAppQueryRequest;
import com.alex.messenger.bot.dto.BotApiDeleteMessageRequest;
import com.alex.messenger.bot.dto.BotApiDeleteMessageResponse;
import com.alex.messenger.bot.dto.BotApiEditMessageTextRequest;
import com.alex.messenger.bot.dto.BotApiSendInvoiceRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BotApiService {

    private final MessageService messageService;
    private final BotCommandService botCommandService;
    private final BotInlineResultCacheService botInlineResultCacheService;
    private final BotMessageActionService botMessageActionService;
    private final BotCallbackQueryService botCallbackQueryService;
    private final BotWebAppService botWebAppService;
    private final BotPaymentService botPaymentService;

    @Transactional
    public ChatMessageResponse sendMessage(UUID botUserId, BotApiSendMessageRequest request) {
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
    public ChatMessageResponse editMessageText(UUID botUserId, BotApiEditMessageTextRequest request) {
        return messageService.editMessage(
                botUserId,
                request.messageId(),
                new EditMessageRequest(request.text(), request.entities())
        );
    }

    @Transactional
    public BotApiDeleteMessageResponse deleteMessage(UUID botUserId, BotApiDeleteMessageRequest request) {
        ChatMessageResponse response = messageService.deleteMessage(botUserId, request.messageId());
        return new BotApiDeleteMessageResponse(response.messageId(), response.deletedAt() != null);
    }

    @Transactional(readOnly = true)
    public List<BotCommandResponse> getMyCommands(UUID botUserId) {
        return botCommandService.getConfiguredCommands(botUserId);
    }

    @Transactional
    public List<BotCommandResponse> setMyCommands(UUID botUserId, BotApiSetMyCommandsRequest request) {
        return botCommandService.replaceCommands(botUserId, request.commands());
    }

    @Transactional
    public List<BotInlineResultResponse> answerInlineQuery(UUID botUserId, BotApiAnswerInlineQueryRequest request) {
        return botInlineResultCacheService.replaceCachedResults(botUserId, request);
    }

    @Transactional
    public BotCallbackQueryResponse answerCallbackQuery(UUID botUserId, BotApiAnswerCallbackQueryRequest request) {
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
}
