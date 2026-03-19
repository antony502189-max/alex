package com.alex.messenger.bot;

import com.alex.messenger.bot.dto.BotApiAnswerInlineQueryRequest;
import com.alex.messenger.bot.dto.BotApiInlineResultRequest;
import com.alex.messenger.bot.dto.BotInlineResultResponse;
import java.time.Instant;
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
public class BotInlineResultCacheService {

    private static final int MAX_RESULTS = 50;
    private static final int DEFAULT_CACHE_SECONDS = 300;
    private static final int MAX_CACHE_SECONDS = 3600;

    private final BotAccountRepository botAccountRepository;
    private final BotInlineResultCacheRepository botInlineResultCacheRepository;

    @Transactional(readOnly = true)
    public List<BotInlineResultResponse> getCachedResults(UUID botUserId, String query) {
        requireBot(botUserId);
        String normalizedQuery = normalizeQuery(query);
        return botInlineResultCacheRepository
                .findAllByBotUserIdAndQueryTextAndCachedUntilAfterOrderByCreatedAtAsc(
                        botUserId,
                        normalizedQuery,
                        Instant.now()
                ).stream()
                .map(entry -> new BotInlineResultResponse(
                        entry.getResultId(),
                        entry.getBotUserId(),
                        null,
                        entry.getTitle(),
                        entry.getDescription(),
                        entry.getText()
                ))
                .toList();
    }

    @Transactional
    public List<BotInlineResultResponse> replaceCachedResults(UUID botUserId, BotApiAnswerInlineQueryRequest request) {
        requireBot(botUserId);
        String normalizedQuery = normalizeQuery(request.query());
        List<BotApiInlineResultRequest> results = request.results() == null ? List.of() : request.results();
        if (results.size() > MAX_RESULTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many inline results");
        }

        int cacheSeconds = normalizeCacheSeconds(request.cacheTimeSeconds());
        Instant cachedUntil = Instant.now().plusSeconds(cacheSeconds);
        Set<String> uniqueIds = new LinkedHashSet<>();
        List<BotInlineResultCacheEntity> entities = new ArrayList<>();
        for (BotApiInlineResultRequest result : results) {
            if (result == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inline result entry is required");
            }
            String resultId = normalizeResultId(result.resultId());
            if (!uniqueIds.add(resultId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate inline result id");
            }
            BotInlineResultCacheEntity entity = new BotInlineResultCacheEntity();
            entity.setBotUserId(botUserId);
            entity.setQueryText(normalizedQuery);
            entity.setResultId(resultId);
            entity.setTitle(normalizeTitle(result.title()));
            entity.setDescription(normalizeOptional(result.description(), 255));
            entity.setText(normalizeRequired(result.text(), "Inline result text", 4000));
            entity.setCachedUntil(cachedUntil);
            entities.add(entity);
        }

        botInlineResultCacheRepository.deleteAllByBotUserIdAndQueryText(botUserId, normalizedQuery);
        if (!entities.isEmpty()) {
            botInlineResultCacheRepository.saveAll(entities);
        }
        return getCachedResults(botUserId, normalizedQuery);
    }

    private void requireBot(UUID botUserId) {
        if (botAccountRepository.findById(botUserId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bot not found");
        }
    }

    private String normalizeQuery(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > 512) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inline query is too long");
        }
        return normalized;
    }

    private int normalizeCacheSeconds(Integer value) {
        if (value == null) {
            return DEFAULT_CACHE_SECONDS;
        }
        if (value < 0 || value > MAX_CACHE_SECONDS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "cacheTimeSeconds must be between 0 and " + MAX_CACHE_SECONDS
            );
        }
        return value;
    }

    private String normalizeResultId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_\\-]{1,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inline result id is invalid");
        }
        return normalized;
    }

    private String normalizeTitle(String value) {
        return normalizeRequired(value, "Inline result title", 120);
    }

    private String normalizeRequired(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is too long");
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inline result description is too long");
        }
        return normalized;
    }
}
