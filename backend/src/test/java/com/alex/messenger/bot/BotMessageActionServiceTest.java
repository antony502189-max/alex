package com.alex.messenger.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.alex.messenger.bot.dto.BotApiMessageActionRequest;
import com.alex.messenger.bot.dto.BotCallbackQueryResponse;
import com.alex.messenger.bot.dto.BotPreCheckoutQueryResponse;
import com.alex.messenger.bot.dto.BotWebAppLaunchResponse;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.user.UserEntity;
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

@ExtendWith(MockitoExtension.class)
class BotMessageActionServiceTest {

    @Mock
    private BotMessageActionRepository botMessageActionRepository;

    @Mock
    private BotCallbackQueryService botCallbackQueryService;

    @Mock
    private BotWebAppService botWebAppService;

    @Mock
    private BotPaymentService botPaymentService;

    @Mock
    private MessageService messageService;

    @Mock
    private UserRepository userRepository;

    private BotMessageActionService botMessageActionService;

    @BeforeEach
    void setUp() {
        botMessageActionService = new BotMessageActionService(
                botMessageActionRepository,
                botCallbackQueryService,
                botWebAppService,
                botPaymentService,
                messageService,
                userRepository
        );
    }

    @Test
    void saveMessageActionsPersistsCallbackAndWebAppActions() {
        UUID botUserId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UserEntity bot = bot(botUserId, "https://example.com/app");

        when(messageService.getMessage(botUserId, messageId)).thenReturn(botMessage(messageId, botUserId, null));
        when(userRepository.findByIdAndBotTrue(botUserId)).thenReturn(Optional.of(bot));
        when(botMessageActionRepository.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<BotMessageActionEntity> entities = invocation.getArgument(0);
            for (int index = 0; index < entities.size(); index++) {
                BotMessageActionEntity entity = entities.get(index);
                entity.setId(UUID.randomUUID());
                entity.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z").plusSeconds(index));
            }
            return entities;
        });

        var response = botMessageActionService.saveMessageActions(
                botUserId,
                messageId,
                List.of(
                        new BotApiMessageActionRequest("CALLBACK", "Open", "payload", null, null),
                        new BotApiMessageActionRequest("WEB_APP", "Launch", null, null, "start")
                )
        );

        assertThat(response).hasSize(2);
        assertThat(response.get(0).actionType()).isEqualTo("CALLBACK");
        assertThat(response.get(0).callbackData()).isEqualTo("payload");
        assertThat(response.get(1).actionType()).isEqualTo("WEB_APP");
        assertThat(response.get(1).webAppStartParameter()).isEqualTo("start");
    }

    @Test
    void triggerCallbackActionCreatesCallbackQuery() {
        UUID requesterId = UUID.randomUUID();
        UUID botUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        BotMessageActionEntity action = callbackAction(actionId, botUserId, messageId);

        when(messageService.getMessage(requesterId, messageId)).thenReturn(botMessage(messageId, botUserId, chatId));
        when(botMessageActionRepository.findById(actionId)).thenReturn(Optional.of(action));
        when(botCallbackQueryService.createCallbackQuery(eq(requesterId), eq(chatId), eq(messageId), eq(action)))
                .thenReturn(new BotCallbackQueryResponse(
                        UUID.randomUUID(),
                        botUserId,
                        chatId,
                        messageId,
                        requesterId,
                        actionId,
                        "payload",
                        Instant.parse("2026-03-14T12:00:00Z"),
                        null,
                        null,
                        false,
                        null
                ));

        var response = botMessageActionService.triggerAction(requesterId, messageId, actionId);

        assertThat(response.action().actionId()).isEqualTo(actionId);
        assertThat(response.callbackQuery()).isNotNull();
        assertThat(response.callbackQuery().callbackData()).isEqualTo("payload");
        assertThat(response.webAppLaunch()).isNull();
        assertThat(response.preCheckoutQuery()).isNull();
    }

    @Test
    void triggerWebAppActionCreatesLaunchResponse() {
        UUID requesterId = UUID.randomUUID();
        UUID botUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        BotMessageActionEntity action = webAppAction(actionId, botUserId, messageId);

        when(messageService.getMessage(requesterId, messageId)).thenReturn(botMessage(messageId, botUserId, chatId));
        when(botMessageActionRepository.findById(actionId)).thenReturn(Optional.of(action));
        when(botWebAppService.createLaunch(requesterId, botUserId, chatId, "start"))
                .thenReturn(new BotWebAppLaunchResponse(
                        botUserId,
                        "samplebot",
                        chatId,
                        "https://example.com/app?token=1",
                        Instant.parse("2026-03-14T12:00:00Z"),
                        Instant.parse("2026-03-14T12:10:00Z")
                ));

        var response = botMessageActionService.triggerAction(requesterId, messageId, actionId);

        assertThat(response.action().actionId()).isEqualTo(actionId);
        assertThat(response.webAppLaunch()).isNotNull();
        assertThat(response.webAppLaunch().launchUrl()).contains("token=1");
        assertThat(response.callbackQuery()).isNull();
        assertThat(response.preCheckoutQuery()).isNull();
    }

    @Test
    void triggerPayActionCreatesPreCheckoutQuery() {
        UUID requesterId = UUID.randomUUID();
        UUID botUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        UUID paymentInvoiceId = UUID.randomUUID();
        BotMessageActionEntity action = payAction(actionId, botUserId, messageId, paymentInvoiceId);

        when(messageService.getMessage(requesterId, messageId)).thenReturn(botMessage(messageId, botUserId, chatId));
        when(botMessageActionRepository.findById(actionId)).thenReturn(Optional.of(action));
        when(botPaymentService.createPreCheckoutQuery(eq(requesterId), any(ChatMessageResponse.class), eq(action)))
                .thenReturn(new BotPreCheckoutQueryResponse(
                        UUID.randomUUID(),
                        botUserId,
                        chatId,
                        messageId,
                        paymentInvoiceId,
                        requesterId,
                        null,
                        "Invoice",
                        "Desc",
                        50L,
                        "XTR",
                        "PENDING",
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
                        null,
                        Instant.parse("2026-03-14T12:00:00Z"),
                        null,
                        null
                ));

        var response = botMessageActionService.triggerAction(requesterId, messageId, actionId);

        assertThat(response.action().paymentInvoiceId()).isEqualTo(paymentInvoiceId);
        assertThat(response.preCheckoutQuery()).isNotNull();
        assertThat(response.preCheckoutQuery().status()).isEqualTo("PENDING");
        assertThat(response.callbackQuery()).isNull();
        assertThat(response.webAppLaunch()).isNull();
    }

    private UserEntity bot(UUID botUserId, String webAppUrl) {
        UserEntity user = new UserEntity();
        user.setId(botUserId);
        user.setDisplayName("Bot");
        user.setPhoneNumber("+1234567890");
        user.setBot(true);
        user.setBotWebAppUrl(webAppUrl);
        return user;
    }

    private BotMessageActionEntity callbackAction(UUID actionId, UUID botUserId, UUID messageId) {
        BotMessageActionEntity action = new BotMessageActionEntity();
        action.setId(actionId);
        action.setBotUserId(botUserId);
        action.setMessageId(messageId);
        action.setActionType("CALLBACK");
        action.setButtonText("Open");
        action.setCallbackData("payload");
        action.setSortOrder(0);
        action.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
        return action;
    }

    private BotMessageActionEntity webAppAction(UUID actionId, UUID botUserId, UUID messageId) {
        BotMessageActionEntity action = new BotMessageActionEntity();
        action.setId(actionId);
        action.setBotUserId(botUserId);
        action.setMessageId(messageId);
        action.setActionType("WEB_APP");
        action.setButtonText("Launch");
        action.setWebAppStartParameter("start");
        action.setSortOrder(1);
        action.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
        return action;
    }

    private BotMessageActionEntity payAction(UUID actionId, UUID botUserId, UUID messageId, UUID paymentInvoiceId) {
        BotMessageActionEntity action = new BotMessageActionEntity();
        action.setId(actionId);
        action.setBotUserId(botUserId);
        action.setMessageId(messageId);
        action.setActionType("PAY");
        action.setButtonText("Pay");
        action.setPaymentInvoiceId(paymentInvoiceId);
        action.setSortOrder(2);
        action.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
        return action;
    }

    private ChatMessageResponse botMessage(UUID messageId, UUID botUserId, UUID chatId) {
        return new ChatMessageResponse(
                chatId != null ? chatId : UUID.randomUUID(),
                messageId,
                null,
                botUserId,
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
}
