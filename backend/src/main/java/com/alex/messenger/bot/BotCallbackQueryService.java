package com.alex.messenger.bot;

import com.alex.messenger.bot.dto.BotApiAnswerCallbackQueryRequest;
import com.alex.messenger.bot.dto.BotCallbackQueryResponse;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BotCallbackQueryService {

    private final BotCallbackQueryRepository botCallbackQueryRepository;
    private final BotUpdateRepository botUpdateRepository;

    @Transactional
    public BotCallbackQueryResponse createCallbackQuery(
            UUID requesterId,
            UUID chatId,
            UUID messageId,
            BotMessageActionEntity action
    ) {
        BotCallbackQueryEntity callbackQuery = new BotCallbackQueryEntity();
        callbackQuery.setBotUserId(action.getBotUserId());
        callbackQuery.setChatId(chatId);
        callbackQuery.setMessageId(messageId);
        callbackQuery.setFromUserId(requesterId);
        callbackQuery.setActionId(action.getId());
        callbackQuery.setCallbackData(normalizeRequired(action.getCallbackData(), "Callback data", 255));
        BotCallbackQueryEntity savedCallbackQuery = botCallbackQueryRepository.save(callbackQuery);

        BotUpdateEntity update = new BotUpdateEntity();
        update.setBotUserId(action.getBotUserId());
        update.setChatId(chatId);
        update.setMessageId(messageId);
        update.setUpdateType("CALLBACK_QUERY");
        update.setCallbackQueryId(savedCallbackQuery.getId());
        botUpdateRepository.save(update);
        return toResponse(savedCallbackQuery);
    }

    @Transactional
    public BotCallbackQueryResponse answerCallbackQuery(UUID botUserId, BotApiAnswerCallbackQueryRequest request) {
        BotCallbackQueryEntity callbackQuery = botCallbackQueryRepository.findByIdAndBotUserId(
                        request.callbackQueryId(),
                        botUserId
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Callback query not found"));
        callbackQuery.setAnswerText(normalizeOptional(request.text(), 255));
        callbackQuery.setShowAlert(Boolean.TRUE.equals(request.showAlert()));
        callbackQuery.setRedirectUrl(normalizeHttpUrl(request.redirectUrl()));
        callbackQuery.setAnsweredAt(Instant.now());
        return toResponse(botCallbackQueryRepository.save(callbackQuery));
    }

    @Transactional(readOnly = true)
    public BotCallbackQueryResponse getCallbackQuery(UUID callbackQueryId) {
        return botCallbackQueryRepository.findById(callbackQueryId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Callback query not found"));
    }

    private BotCallbackQueryResponse toResponse(BotCallbackQueryEntity callbackQuery) {
        return new BotCallbackQueryResponse(
                callbackQuery.getId(),
                callbackQuery.getBotUserId(),
                callbackQuery.getChatId(),
                callbackQuery.getMessageId(),
                callbackQuery.getFromUserId(),
                callbackQuery.getActionId(),
                callbackQuery.getCallbackData(),
                callbackQuery.getCreatedAt(),
                callbackQuery.getAnsweredAt(),
                callbackQuery.getAnswerText(),
                callbackQuery.isShowAlert(),
                callbackQuery.getRedirectUrl()
        );
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

    private String normalizeHttpUrl(String value) {
        String normalized = normalizeOptional(value, 512);
        if (normalized == null) {
            return null;
        }
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
            if (!uri.isAbsolute() || scheme == null || (!"http".equals(scheme) && !"https".equals(scheme))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Callback redirect URL must be a valid http(s) URL");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Callback redirect URL must be a valid http(s) URL");
        }
    }
}
