package com.alex.messenger.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.bot.dto.BotApiAnswerCallbackQueryRequest;
import java.time.Instant;
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
class BotCallbackQueryServiceTest {

    @Mock
    private BotCallbackQueryRepository botCallbackQueryRepository;

    @Mock
    private BotUpdateRepository botUpdateRepository;

    private BotCallbackQueryService botCallbackQueryService;

    @BeforeEach
    void setUp() {
        botCallbackQueryService = new BotCallbackQueryService(botCallbackQueryRepository, botUpdateRepository);
    }

    @Test
    void createCallbackQueryEnqueuesBotUpdate() {
        UUID requesterId = UUID.randomUUID();
        UUID botUserId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        UUID callbackQueryId = UUID.randomUUID();
        BotMessageActionEntity action = new BotMessageActionEntity();
        action.setId(actionId);
        action.setBotUserId(botUserId);
        action.setMessageId(messageId);
        action.setActionType("CALLBACK");
        action.setCallbackData("payload");

        when(botCallbackQueryRepository.save(any(BotCallbackQueryEntity.class))).thenAnswer(invocation -> {
            BotCallbackQueryEntity entity = invocation.getArgument(0);
            entity.setId(callbackQueryId);
            entity.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));
            return entity;
        });
        when(botUpdateRepository.save(any(BotUpdateEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = botCallbackQueryService.createCallbackQuery(requesterId, chatId, messageId, action);

        ArgumentCaptor<BotUpdateEntity> updateCaptor = ArgumentCaptor.forClass(BotUpdateEntity.class);
        verify(botUpdateRepository).save(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getBotUserId()).isEqualTo(botUserId);
        assertThat(updateCaptor.getValue().getCallbackQueryId()).isEqualTo(callbackQueryId);
        assertThat(updateCaptor.getValue().getUpdateType()).isEqualTo("CALLBACK_QUERY");
        assertThat(response.callbackQueryId()).isEqualTo(callbackQueryId);
        assertThat(response.callbackData()).isEqualTo("payload");
    }

    @Test
    void answerCallbackQueryStoresAnswerPayload() {
        UUID botUserId = UUID.randomUUID();
        UUID callbackQueryId = UUID.randomUUID();
        BotCallbackQueryEntity callbackQuery = new BotCallbackQueryEntity();
        callbackQuery.setId(callbackQueryId);
        callbackQuery.setBotUserId(botUserId);
        callbackQuery.setChatId(UUID.randomUUID());
        callbackQuery.setMessageId(UUID.randomUUID());
        callbackQuery.setFromUserId(UUID.randomUUID());
        callbackQuery.setActionId(UUID.randomUUID());
        callbackQuery.setCallbackData("payload");
        callbackQuery.setCreatedAt(Instant.parse("2026-03-14T12:00:00Z"));

        when(botCallbackQueryRepository.findByIdAndBotUserId(callbackQueryId, botUserId))
                .thenReturn(Optional.of(callbackQuery));
        when(botCallbackQueryRepository.save(any(BotCallbackQueryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = botCallbackQueryService.answerCallbackQuery(
                botUserId,
                new BotApiAnswerCallbackQueryRequest(callbackQueryId, "Done", true, "https://example.com/done")
        );

        assertThat(response.callbackQueryId()).isEqualTo(callbackQueryId);
        assertThat(response.answerText()).isEqualTo("Done");
        assertThat(response.showAlert()).isTrue();
        assertThat(response.redirectUrl()).isEqualTo("https://example.com/done");
        assertThat(response.answeredAt()).isNotNull();
    }

    @Test
    void answerCallbackQueryRejectsMissingRequest() {
        UUID botUserId = UUID.randomUUID();

        ResponseStatusException exception = catchThrowableOfType(
                () -> botCallbackQueryService.answerCallbackQuery(botUserId, null),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("Callback answer payload is required");
    }
}
