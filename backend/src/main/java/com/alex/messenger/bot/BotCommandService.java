package com.alex.messenger.bot;

import com.alex.messenger.bot.dto.BotApiCommandRequest;
import com.alex.messenger.bot.dto.BotCommandResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BotCommandService {

    private static final int MAX_COMMANDS = 100;

    private final BotAccountRepository botAccountRepository;
    private final BotCommandRepository botCommandRepository;

    @Transactional(readOnly = true)
    public List<BotCommandResponse> getConfiguredCommands(UUID botUserId) {
        requireBot(botUserId);
        return botCommandRepository.findAllByBotUserIdOrderByPositionAscCreatedAtAsc(botUserId).stream()
                .map(command -> new BotCommandResponse("/" + command.getCommand(), command.getDescription()))
                .toList();
    }

    @Transactional
    public List<BotCommandResponse> replaceCommands(UUID botUserId, List<BotApiCommandRequest> commands) {
        requireBot(botUserId);
        List<BotApiCommandRequest> normalizedCommands = commands == null ? List.of() : commands;
        if (normalizedCommands.size() > MAX_COMMANDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many bot commands");
        }

        List<BotCommandEntity> entities = new ArrayList<>();
        Set<String> uniqueCommands = new LinkedHashSet<>();
        for (int index = 0; index < normalizedCommands.size(); index++) {
            BotApiCommandRequest request = normalizedCommands.get(index);
            if (request == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bot command entry is required");
            }
            String normalizedCommand = normalizeCommand(request.command());
            if (!uniqueCommands.add(normalizedCommand)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate bot command: /" + normalizedCommand);
            }

            BotCommandEntity entity = new BotCommandEntity();
            entity.setBotUserId(botUserId);
            entity.setCommand(normalizedCommand);
            entity.setDescription(normalizeDescription(request.description()));
            entity.setPosition(index);
            entities.add(entity);
        }

        botCommandRepository.deleteAllByBotUserId(botUserId);
        if (!entities.isEmpty()) {
            botCommandRepository.saveAll(entities);
        }
        return getConfiguredCommands(botUserId);
    }

    public List<BotCommandResponse> fallbackCommandsForBot(BotAccountEntity account, String webAppUrl) {
        List<BotCommandResponse> configured = getConfiguredCommands(account.getBotUserId());
        if (!configured.isEmpty()) {
            return configured;
        }
        List<BotCommandResponse> fallback = new ArrayList<>();
        fallback.add(new BotCommandResponse("/start", "Open the bot conversation"));
        if (webAppUrl != null && !webAppUrl.isBlank()) {
            fallback.add(new BotCommandResponse("/app", "Open the connected mini app"));
        }
        return fallback;
    }

    private void requireBot(UUID botUserId) {
        if (botAccountRepository.findById(botUserId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found");
        }
    }

    private String normalizeCommand(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.matches("[a-z0-9_]{1,32}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Bot command must match /[a-z0-9_]{1,32}"
            );
        }
        return normalized;
    }

    private String normalizeDescription(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bot command description is required");
        }
        if (normalized.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bot command description is too long");
        }
        return normalized;
    }
}
