package com.alex.messenger.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.bot.dto.BotApiAnswerCallbackQueryRequest;
import com.alex.messenger.bot.dto.BotApiAnswerWebAppQueryRequest;
import com.alex.messenger.bot.dto.BotApiAnswerPreCheckoutQueryRequest;
import com.alex.messenger.bot.dto.BotApiRefundPaymentRequest;
import com.alex.messenger.bot.dto.BotApiCommandRequest;
import com.alex.messenger.bot.dto.BotApiDeleteMessageRequest;
import com.alex.messenger.bot.dto.BotApiEditMessageTextRequest;
import com.alex.messenger.bot.dto.BotApiMessageActionRequest;
import com.alex.messenger.bot.dto.BotApiSendInvoiceRequest;
import com.alex.messenger.bot.dto.BotApiSendAttachmentMessageRequest;
import com.alex.messenger.bot.dto.BotApiSendMediaGroupRequest;
import com.alex.messenger.bot.dto.BotApiSendMessageRequest;
import com.alex.messenger.bot.dto.BotApiSetMyCommandsRequest;
import com.alex.messenger.bot.dto.BotApiAnswerInlineQueryRequest;
import com.alex.messenger.bot.dto.BotApiInlineResultRequest;
import com.alex.messenger.attachment.AttachmentService;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BotApiServiceTest {

    @Mock
    private MessageService messageService;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private ChatService chatService;

    @Mock
    private BotCommandService botCommandService;

    @Mock
    private BotInlineResultCacheService botInlineResultCacheService;

    @Mock
    private BotMessageActionService botMessageActionService;

    @Mock
    private BotCallbackQueryService botCallbackQueryService;

    @Mock
    private BotWebAppService botWebAppService;

    @Mock
    private BotPaymentService botPaymentService;

    private BotApiService botApiService;

    @BeforeEach
    void setUp() {
        botApiService = new BotApiService(
                messageService,
                attachmentService,
                chatService,
                botCommandService,
                botInlineResultCacheService,
                botMessageActionService,
                botCallbackQueryService,
                botWebAppService,
                botPaymentService
        );
    }

    @Test
    void sendMessageDelegatesToMessageService() {
        UUID botUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        BotApiMessageActionRequest action = new BotApiMessageActionRequest("CALLBACK", "Open", "launch", null, null);

        when(messageService.sendMessage(eq(botUserId), any(SendMessageRequest.class))).thenReturn(message(messageId));

        var response = botApiService.sendMessage(
                botUserId,
                new BotApiSendMessageRequest(
                        chatId,
                        null,
                        null,
                        null,
                        "hello",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(action)
                )
        );

        verify(botMessageActionService).saveMessageActions(botUserId, messageId, List.of(action));
        assertThat(response.messageId()).isEqualTo(messageId);
    }

    @Test
    void sendPhotoMapsToPhotoMessageTypeAndSingleAttachment() {
        UUID botUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        BotApiMessageActionRequest action = new BotApiMessageActionRequest("URL", "Open", null, "https://example.com", null);

        when(messageService.sendMessage(eq(botUserId), any(SendMessageRequest.class))).thenReturn(message(messageId));

        var response = botApiService.sendPhoto(
                botUserId,
                new BotApiSendAttachmentMessageRequest(
                        chatId,
                        null,
                        null,
                        null,
                        "Cover",
                        List.of(),
                        attachmentId,
                        false,
                        null,
                        List.of(action)
                )
        );

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(botUserId), requestCaptor.capture());
        SendMessageRequest captured = requestCaptor.getValue();
        assertThat(captured.messageType()).isEqualTo("PHOTO");
        assertThat(captured.attachmentIds()).containsExactly(attachmentId);
        assertThat(captured.caption()).isEqualTo("Cover");
        verify(botMessageActionService).saveMessageActions(botUserId, messageId, List.of(action));
        assertThat(response.messageId()).isEqualTo(messageId);
    }

    @Test
    void sendVideoNoteMapsToVideoNoteMessageType() {
        UUID botUserId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        when(messageService.sendMessage(eq(botUserId), any(SendMessageRequest.class))).thenReturn(message(UUID.randomUUID()));

        botApiService.sendVideoNote(
                botUserId,
                new BotApiSendAttachmentMessageRequest(
                        null,
                        UUID.randomUUID(),
                        null,
                        null,
                        null,
                        List.of(),
                        attachmentId,
                        true,
                        UUID.randomUUID(),
                        List.of()
                )
        );

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(botUserId), requestCaptor.capture());
        SendMessageRequest captured = requestCaptor.getValue();
        assertThat(captured.messageType()).isEqualTo("VIDEO_NOTE");
        assertThat(captured.attachmentIds()).containsExactly(attachmentId);
        assertThat(captured.silent()).isTrue();
    }

    @Test
    void sendAudioMapsToAudioMessageType() {
        UUID botUserId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        when(messageService.sendMessage(eq(botUserId), any(SendMessageRequest.class))).thenReturn(message(UUID.randomUUID()));

        botApiService.sendAudio(
                botUserId,
                new BotApiSendAttachmentMessageRequest(
                        UUID.randomUUID(),
                        null,
                        null,
                        null,
                        "Track",
                        List.of(),
                        attachmentId,
                        false,
                        null,
                        List.of()
                )
        );

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(botUserId), requestCaptor.capture());
        assertThat(requestCaptor.getValue().messageType()).isEqualTo("AUDIO");
    }

    @Test
    void sendMediaGroupClonesAttachmentsAsAlbumAndSendsAlbumMessage() {
        UUID botUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID firstAttachmentId = UUID.randomUUID();
        UUID secondAttachmentId = UUID.randomUUID();
        UUID clonedFirstAttachmentId = UUID.randomUUID();
        UUID clonedSecondAttachmentId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        BotApiMessageActionRequest action = new BotApiMessageActionRequest("CALLBACK", "Open", "payload", null, null);

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);

        when(chatService.getOwnedChat(botUserId, chatId)).thenReturn(chat);
        when(attachmentService.cloneAttachmentsToChatAsAlbum(botUserId, chatId, List.of(firstAttachmentId, secondAttachmentId)))
                .thenReturn(List.of(clonedFirstAttachmentId, clonedSecondAttachmentId));
        when(messageService.sendMessage(eq(botUserId), any(SendMessageRequest.class))).thenReturn(message(messageId));

        var response = botApiService.sendMediaGroup(
                botUserId,
                new BotApiSendMediaGroupRequest(
                        chatId,
                        null,
                        null,
                        null,
                        "Album caption",
                        List.of(),
                        List.of(firstAttachmentId, secondAttachmentId),
                        true,
                        null,
                        List.of(action)
                )
        );

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageService).sendMessage(eq(botUserId), requestCaptor.capture());
        SendMessageRequest captured = requestCaptor.getValue();
        assertThat(captured.chatId()).isEqualTo(chatId);
        assertThat(captured.messageType()).isEqualTo("ALBUM");
        assertThat(captured.attachmentIds()).containsExactly(clonedFirstAttachmentId, clonedSecondAttachmentId);
        assertThat(captured.caption()).isEqualTo("Album caption");
        assertThat(captured.silent()).isTrue();
        verify(botMessageActionService).saveMessageActions(botUserId, messageId, List.of(action));
        assertThat(response.messageId()).isEqualTo(messageId);
    }

    @Test
    void editAndDeleteMessageUseUnderlyingMessageService() {
        UUID botUserId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        when(messageService.editMessage(eq(botUserId), eq(messageId), any(EditMessageRequest.class))).thenReturn(message(messageId));
        when(messageService.deleteMessage(botUserId, messageId)).thenReturn(deletedMessage(messageId));

        var edited = botApiService.editMessageText(
                botUserId,
                new BotApiEditMessageTextRequest(messageId, "updated", List.of())
        );
        var deleted = botApiService.deleteMessage(botUserId, new BotApiDeleteMessageRequest(messageId));

        assertThat(edited.messageId()).isEqualTo(messageId);
        assertThat(deleted.deleted()).isTrue();
    }

    @Test
    void setMyCommandsDelegatesToBotCommandService() {
        UUID botUserId = UUID.randomUUID();
        when(botCommandService.replaceCommands(eq(botUserId), any())).thenReturn(List.of(
                new BotCommandResponse("/start", "Open")
        ));

        var response = botApiService.setMyCommands(
                botUserId,
                new BotApiSetMyCommandsRequest(List.of(new BotApiCommandRequest("/start", "Open")))
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).command()).isEqualTo("/start");
    }

    @Test
    void answerInlineQueryDelegatesToInlineCacheService() {
        UUID botUserId = UUID.randomUUID();
        when(botInlineResultCacheService.replaceCachedResults(eq(botUserId), any())).thenReturn(List.of(
                new BotInlineResultResponse("r1", botUserId, null, "Title", "Desc", "Text")
        ));

        var response = botApiService.answerInlineQuery(
                botUserId,
                new BotApiAnswerInlineQueryRequest(
                        "hello",
                        60,
                        List.of(new BotApiInlineResultRequest("r1", "Title", "Desc", "Text"))
                )
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).resultId()).isEqualTo("r1");
    }

    @Test
    void answerCallbackQueryDelegatesToCallbackService() {
        UUID botUserId = UUID.randomUUID();
        UUID callbackQueryId = UUID.randomUUID();
        when(botCallbackQueryService.answerCallbackQuery(eq(botUserId), any(BotApiAnswerCallbackQueryRequest.class)))
                .thenReturn(new BotCallbackQueryResponse(
                        callbackQueryId,
                        botUserId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "payload",
                        Instant.parse("2026-03-14T10:00:00Z"),
                        Instant.parse("2026-03-14T10:01:00Z"),
                        "done",
                        false,
                        null
                ));

        var response = botApiService.answerCallbackQuery(
                botUserId,
                new BotApiAnswerCallbackQueryRequest(callbackQueryId, "done", false, null)
        );

        assertThat(response.callbackQueryId()).isEqualTo(callbackQueryId);
        assertThat(response.answerText()).isEqualTo("done");
    }

    @Test
    void answerWebAppQueryDelegatesToWebAppService() {
        UUID botUserId = UUID.randomUUID();
        UUID queryId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(botWebAppService.answerQuery(eq(botUserId), any(BotApiAnswerWebAppQueryRequest.class)))
                .thenReturn(message(messageId));

        var response = botApiService.answerWebAppQuery(
                botUserId,
                new BotApiAnswerWebAppQueryRequest(queryId, "result", null, null, List.of(), List.of(), null, false)
        );

        assertThat(response.messageId()).isEqualTo(messageId);
    }

    @Test
    void sendInvoiceDelegatesToBotPaymentService() {
        UUID botUserId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(botPaymentService.sendInvoice(eq(botUserId), any(BotApiSendInvoiceRequest.class)))
                .thenReturn(new BotPaymentInvoiceResponse(
                        invoiceId,
                        botUserId,
                        UUID.randomUUID(),
                        messageId,
                        UUID.randomUUID(),
                        "Invoice",
                        "Desc",
                        50L,
                        "XTR",
                        "OPEN",
                        "payload",
                        "Pay",
                        false,
                        false,
                        false,
                        false,
                        false,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        Instant.parse("2026-03-14T10:00:00Z"),
                        null
                ));

        var response = botApiService.sendInvoice(
                botUserId,
                new BotApiSendInvoiceRequest(
                        null,
                        UUID.randomUUID(),
                        "Invoice",
                        "Desc",
                        50L,
                        null,
                        "payload",
                        "Pay",
                        null,
                        java.util.Map.of(),
                        false,
                        false,
                        false,
                        false,
                        false,
                        null,
                        List.of(),
                        List.of()
                )
        );

        assertThat(response.paymentInvoiceId()).isEqualTo(invoiceId);
        assertThat(response.messageId()).isEqualTo(messageId);
    }

    @Test
    void answerPreCheckoutDelegatesToBotPaymentService() {
        UUID botUserId = UUID.randomUUID();
        UUID queryId = UUID.randomUUID();
        when(botPaymentService.answerPreCheckoutQuery(eq(botUserId), any(BotApiAnswerPreCheckoutQueryRequest.class)))
                .thenReturn(new BotPreCheckoutQueryResponse(
                        queryId,
                        botUserId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        "Invoice",
                        "Desc",
                        50L,
                        "XTR",
                        "APPROVED",
                        "payload",
                        false,
                        false,
                        false,
                        false,
                        false,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0L,
                        0L,
                        50L,
                        null,
                        "ok",
                        Instant.parse("2026-03-14T10:00:00Z"),
                        Instant.parse("2026-03-14T10:01:00Z"),
                        null
                ));

        var response = botApiService.answerPreCheckoutQuery(
                botUserId,
                new BotApiAnswerPreCheckoutQueryRequest(queryId, true, "ok")
        );

        assertThat(response.preCheckoutQueryId()).isEqualTo(queryId);
        assertThat(response.status()).isEqualTo("APPROVED");
    }

    @Test
    void refundPaymentDelegatesToBotPaymentService() {
        UUID botUserId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        when(botPaymentService.refundPayment(eq(botUserId), any(BotApiRefundPaymentRequest.class)))
                .thenReturn(new BotPaymentReceiptResponse(
                        receiptId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        botUserId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Invoice",
                        "Desc",
                        "payload",
                        "XTR",
                        50L,
                        0L,
                        0L,
                        50L,
                        false,
                        false,
                        false,
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        java.util.Map.of(),
                        Instant.parse("2026-03-14T10:00:00Z"),
                        Instant.parse("2026-03-14T10:05:00Z")
                ));

        var response = botApiService.refundPayment(
                botUserId,
                new BotApiRefundPaymentRequest(receiptId, "customer request")
        );

        assertThat(response.receiptId()).isEqualTo(receiptId);
        assertThat(response.refundedAt()).isEqualTo(Instant.parse("2026-03-14T10:05:00Z"));
    }

    private ChatMessageResponse message(UUID messageId) {
        return new ChatMessageResponse(
                UUID.randomUUID(),
                messageId,
                null,
                UUID.randomUUID(),
                "Bot",
                null,
                null,
                false,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                0,
                "hello",
                List.of(),
                "TEXT",
                null,
                false,
                null,
                null,
                null,
                Instant.parse("2026-03-14T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                "SENT",
                null,
                null,
                null,
                null,
                null
        );
    }

    private ChatMessageResponse deletedMessage(UUID messageId) {
        return new ChatMessageResponse(
                UUID.randomUUID(),
                messageId,
                null,
                UUID.randomUUID(),
                "Bot",
                null,
                null,
                false,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                0,
                "hello",
                List.of(),
                "TEXT",
                null,
                false,
                null,
                null,
                null,
                Instant.parse("2026-03-14T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                "SENT",
                null,
                null,
                null,
                null,
                Instant.parse("2026-03-14T10:05:00Z")
        );
    }
}
