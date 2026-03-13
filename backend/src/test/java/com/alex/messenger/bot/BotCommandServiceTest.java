package com.alex.messenger.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.alex.messenger.bot.dto.BotApiCommandRequest;
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
class BotCommandServiceTest {

    @Mock
    private BotAccountRepository botAccountRepository;

    @Mock
    private BotCommandRepository botCommandRepository;

    private BotCommandService botCommandService;

    @BeforeEach
    void setUp() {
        botCommandService = new BotCommandService(botAccountRepository, botCommandRepository);
    }

    @Test
    void replaceCommandsNormalizesSlashPrefixAndReturnsConfiguredCommands() {
        UUID botUserId = UUID.randomUUID();
        BotAccountEntity account = new BotAccountEntity();
        account.setBotUserId(botUserId);

        when(botAccountRepository.findById(botUserId)).thenReturn(Optional.of(account));
        when(botCommandRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(botCommandRepository.findAllByBotUserIdOrderByPositionAscCreatedAtAsc(botUserId)).thenAnswer(invocation -> {
            BotCommandEntity first = new BotCommandEntity();
            first.setBotUserId(botUserId);
            first.setCommand("start");
            first.setDescription("Open the bot");
            first.setPosition(0);
            BotCommandEntity second = new BotCommandEntity();
            second.setBotUserId(botUserId);
            second.setCommand("status");
            second.setDescription("Get status");
            second.setPosition(1);
            return List.of(first, second);
        });

        var response = botCommandService.replaceCommands(
                botUserId,
                List.of(
                        new BotApiCommandRequest("/start", "Open the bot"),
                        new BotApiCommandRequest("status", "Get status")
                )
        );

        assertThat(response)
                .extracting(command -> command.command())
                .containsExactly("/start", "/status");
    }

    @Test
    void replaceCommandsRejectsDuplicateNormalizedCommands() {
        UUID botUserId = UUID.randomUUID();
        BotAccountEntity account = new BotAccountEntity();
        account.setBotUserId(botUserId);

        when(botAccountRepository.findById(botUserId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> botCommandService.replaceCommands(
                botUserId,
                List.of(
                        new BotApiCommandRequest("/start", "Open"),
                        new BotApiCommandRequest("start", "Duplicate")
                )
        )).isInstanceOf(ResponseStatusException.class);
    }
}
