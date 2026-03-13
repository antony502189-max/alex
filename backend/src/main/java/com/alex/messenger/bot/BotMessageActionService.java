package com.alex.messenger.bot;

import com.alex.messenger.bot.dto.BotApiMessageActionRequest;
import com.alex.messenger.bot.dto.BotMessageActionResponse;
import com.alex.messenger.bot.dto.BotMessageActionTriggerResponse;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BotMessageActionService {

    private static final int MAX_ACTIONS_PER_MESSAGE = 8;

    private final BotMessageActionRepository botMessageActionRepository;
    private final BotCallbackQueryService botCallbackQueryService;
    private final BotWebAppService botWebAppService;
    private final BotPaymentService botPaymentService;
    private final MessageService messageService;
    private final UserRepository userRepository;

    @Transactional
    public List<BotMessageActionResponse> saveMessageActions(
            UUID botUserId,
            UUID messageId,
            List<BotApiMessageActionRequest> requests
    ) {
        ChatMessageResponse message = messageService.getMessage(botUserId, messageId);
        ensureMessageOwnedByBot(botUserId, message);

        botMessageActionRepository.deleteAllByMessageId(messageId);
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        if (requests.size() > MAX_ACTIONS_PER_MESSAGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many bot message actions");
        }

        UserEntity bot = requireBot(botUserId);
        List<BotMessageActionEntity> actions = new ArrayList<>(requests.size());
        for (int index = 0; index < requests.size(); index++) {
            BotApiMessageActionRequest request = requests.get(index);
            actions.add(buildAction(bot, messageId, request, index));
        }
        return botMessageActionRepository.saveAll(actions).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BotMessageActionResponse> listMessageActions(UUID requesterId, UUID messageId) {
        messageService.getMessage(requesterId, messageId);
        return botMessageActionRepository.findAllByMessageIdOrderBySortOrderAsc(messageId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BotMessageActionTriggerResponse triggerAction(UUID requesterId, UUID messageId, UUID actionId) {
        ChatMessageResponse message = messageService.getMessage(requesterId, messageId);
        BotMessageActionEntity action = botMessageActionRepository.findById(actionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot message action not found"));
        if (!messageId.equals(action.getMessageId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot message action not found");
        }
        ensureMessageOwnedByBot(action.getBotUserId(), message);

        return switch (action.getActionType()) {
            case "CALLBACK" -> new BotMessageActionTriggerResponse(
                    toResponse(action),
                    botCallbackQueryService.createCallbackQuery(requesterId, message.chatId(), messageId, action),
                    null,
                    null,
                    null
            );
            case "URL" -> new BotMessageActionTriggerResponse(toResponse(action), null, null, null, action.getTargetUrl());
            case "WEB_APP" -> new BotMessageActionTriggerResponse(
                    toResponse(action),
                    null,
                    botWebAppService.createLaunch(
                            requesterId,
                            action.getBotUserId(),
                            message.chatId(),
                            action.getWebAppStartParameter()
                    ),
                    null,
                    null
            );
            case "PAY" -> new BotMessageActionTriggerResponse(
                    toResponse(action),
                    null,
                    null,
                    botPaymentService.createPreCheckoutQuery(requesterId, message, action),
                    null
            );
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported bot message action type");
        };
    }

    private BotMessageActionEntity buildAction(
            UserEntity bot,
            UUID messageId,
            BotApiMessageActionRequest request,
            int sortOrder
    ) {
        String actionType = normalizeActionType(request.actionType());
        BotMessageActionEntity action = new BotMessageActionEntity();
        action.setBotUserId(bot.getId());
        action.setMessageId(messageId);
        action.setActionType(actionType);
        action.setButtonText(normalizeRequired(request.buttonText(), "Button text", 64));
        action.setSortOrder(sortOrder);

        switch (actionType) {
            case "CALLBACK" -> action.setCallbackData(normalizeRequired(request.callbackData(), "Callback data", 255));
            case "URL" -> action.setTargetUrl(normalizeHttpUrl(request.targetUrl(), "Bot action URL"));
            case "WEB_APP" -> {
                if (bot.getBotWebAppUrl() == null || bot.getBotWebAppUrl().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bot does not provide a mini app");
                }
                action.setWebAppStartParameter(normalizeStartParameter(request.webAppStartParameter()));
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported bot message action type");
        }
        return action;
    }

    private void ensureMessageOwnedByBot(UUID botUserId, ChatMessageResponse message) {
        boolean matchesSender = botUserId.equals(message.senderId());
        boolean matchesViaBot = botUserId.equals(message.viaBotUserId());
        if (!matchesSender && !matchesViaBot) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message does not belong to this bot");
        }
    }

    private UserEntity requireBot(UUID botUserId) {
        return userRepository.findByIdAndBotTrue(botUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found"));
    }

    private BotMessageActionResponse toResponse(BotMessageActionEntity action) {
        return new BotMessageActionResponse(
                action.getId(),
                action.getBotUserId(),
                action.getMessageId(),
                action.getActionType(),
                action.getButtonText(),
                action.getCallbackData(),
                action.getTargetUrl(),
                action.getWebAppStartParameter(),
                action.getPaymentInvoiceId(),
                action.getSortOrder() != null ? action.getSortOrder() : 0,
                action.getCreatedAt()
        );
    }

    private String normalizeActionType(String value) {
        String normalized = normalizeRequired(value, "Action type", 16).toUpperCase(Locale.ROOT);
        if (!List.of("CALLBACK", "URL", "WEB_APP").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported bot message action type");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String field, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value is too long");
        }
        return normalized;
    }

    private String normalizeHttpUrl(String value, String fieldName) {
        String normalized = normalizeRequired(value, fieldName, 512);
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
            if (!uri.isAbsolute() || scheme == null || (!"http".equals(scheme) && !"https".equals(scheme))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be a valid http(s) URL");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be a valid http(s) URL");
        }
    }

    private String normalizeStartParameter(String value) {
        String normalized = normalizeOptional(value, 128);
        if (normalized == null) {
            return null;
        }
        if (!normalized.matches("[A-Za-z0-9_\\-]{1,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid mini app start parameter");
        }
        return normalized;
    }
}
