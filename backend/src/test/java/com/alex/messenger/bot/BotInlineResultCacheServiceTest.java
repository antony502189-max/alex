package com.alex.messenger.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.alex.messenger.bot.dto.BotApiAnswerInlineQueryRequest;
import com.alex.messenger.bot.dto.BotApiInlineResultRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BotInlineResultCacheServiceTest {

    @Mock
    private BotAccountRepository botAccountRepository;

    @Mock
    private BotInlineResultCacheRepository botInlineResultCacheRepository;

    private BotInlineResultCacheService botInlineResultCacheService;

    @BeforeEach
    void setUp() {
        botInlineResultCacheService = new BotInlineResultCacheService(
                botAccountRepository,
                botInlineResultCacheRepository
        );
    }

    @Test
    void replaceCachedResultsStoresAndReturnsInlineResults() {
        UUID botUserId = UUID.randomUUID();
        BotAccountEntity account = new BotAccountEntity();
        account.setBotUserId(botUserId);

        when(botAccountRepository.findById(botUserId)).thenReturn(Optional.of(account));
        when(botInlineResultCacheRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(botInlineResultCacheRepository.findAllByBotUserIdAndQueryTextAndCachedUntilAfterOrderByCreatedAtAsc(
                eq(botUserId),
                eq("weather"),
                any(Instant.class)
        )).thenAnswer(invocation -> {
            BotInlineResultCacheEntity entity = new BotInlineResultCacheEntity();
            entity.setBotUserId(botUserId);
            entity.setQueryText("weather");
            entity.setResultId("forecast");
            entity.setTitle("Forecast");
            entity.setDescription("Today");
            entity.setText("Sunny");
            entity.setCachedUntil(Instant.now().plusSeconds(60));
            return List.of(entity);
        });

        var response = botInlineResultCacheService.replaceCachedResults(
                botUserId,
                new BotApiAnswerInlineQueryRequest(
                        "weather",
                        60,
                        List.of(new BotApiInlineResultRequest("forecast", "Forecast", "Today", "Sunny"))
                )
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).title()).isEqualTo("Forecast");
        assertThat(response.get(0).text()).isEqualTo("Sunny");
    }

    @Test
    void replaceCachedResultsRejectsDuplicateIds() {
        UUID botUserId = UUID.randomUUID();
        BotAccountEntity account = new BotAccountEntity();
        account.setBotUserId(botUserId);

        when(botAccountRepository.findById(botUserId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> botInlineResultCacheService.replaceCachedResults(
                botUserId,
                new BotApiAnswerInlineQueryRequest(
                        "weather",
                        60,
                        List.of(
                                new BotApiInlineResultRequest("forecast", "Forecast", "Today", "Sunny"),
                                new BotApiInlineResultRequest("forecast", "Forecast 2", "Tomorrow", "Cloudy")
                        )
                )
        )).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void replaceCachedResultsRejectsNegativeCacheTime() {
        UUID botUserId = UUID.randomUUID();
        BotAccountEntity account = new BotAccountEntity();
        account.setBotUserId(botUserId);

        when(botAccountRepository.findById(botUserId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> botInlineResultCacheService.replaceCachedResults(
                botUserId,
                new BotApiAnswerInlineQueryRequest(
                        "weather",
                        -1,
                        List.of(new BotApiInlineResultRequest("forecast", "Forecast", "Today", "Sunny"))
                )
        )).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void replaceCachedResultsRejectsTooLargeCacheTime() {
        UUID botUserId = UUID.randomUUID();
        BotAccountEntity account = new BotAccountEntity();
        account.setBotUserId(botUserId);

        when(botAccountRepository.findById(botUserId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> botInlineResultCacheService.replaceCachedResults(
                botUserId,
                new BotApiAnswerInlineQueryRequest(
                        "weather",
                        3601,
                        List.of(new BotApiInlineResultRequest("forecast", "Forecast", "Today", "Sunny"))
                )
        )).isInstanceOf(ResponseStatusException.class);
    }
}
