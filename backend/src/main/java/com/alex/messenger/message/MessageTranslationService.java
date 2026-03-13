package com.alex.messenger.message;

import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.TranslateMessageRequest;
import com.alex.messenger.message.dto.TranslatedMessageResponse;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MessageTranslationService {

    private final FeatureFlagService featureFlagService;
    private final MessageService messageService;
    private final MessageTranslationProperties properties;
    private final UserRepository userRepository;
    private final MessageTranslationProviderClient messageTranslationProviderClient;
    private final MessageTranslationCacheRepository messageTranslationCacheRepository;

    @Transactional
    public TranslatedMessageResponse translate(UUID requesterId, UUID messageId, TranslateMessageRequest request) {
        featureFlagService.requireTranslationsEnabled();
        ChatMessageResponse message = messageService.getMessage(requesterId, messageId);
        UserEntity requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String providerUrl = normalize(properties.getProviderUrl());
        if (providerUrl == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Translation provider is not configured");
        }

        TranslateMessageRequest effectiveRequest = request != null ? request : new TranslateMessageRequest(null, null);
        String sourceLanguage = normalizeLanguage(effectiveRequest.sourceLanguage(), "sourceLanguage");
        String targetLanguage = resolveTargetLanguage(requester, effectiveRequest);
        String originalText = normalize(message.text());
        String originalCaption = normalize(message.caption());
        if (originalText == null && originalCaption == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message does not contain translatable text");
        }

        String resolvedSourceLanguage = sourceLanguage != null ? sourceLanguage : "auto";
        String apiKey = normalize(properties.getApiKey());
        MessageTranslationCacheEntity cachedTranslation = messageTranslationCacheRepository
                .findByMessageIdAndTargetLanguage(message.messageId(), targetLanguage)
                .orElse(null);
        if (cachedTranslation != null
                && Objects.equals(normalize(cachedTranslation.getProvider()), providerUrl)
                && Objects.equals(normalize(cachedTranslation.getSourceLanguage()), resolvedSourceLanguage)
                && Objects.equals(normalize(cachedTranslation.getOriginalText()), originalText)
                && Objects.equals(normalize(cachedTranslation.getOriginalCaption()), originalCaption)) {
            return toResponse(cachedTranslation);
        }

        MessageTranslationCacheEntity cacheEntity = cachedTranslation != null
                ? cachedTranslation
                : new MessageTranslationCacheEntity();
        cacheEntity.setMessageId(message.messageId());
        cacheEntity.setProvider(providerUrl);
        cacheEntity.setSourceLanguage(resolvedSourceLanguage);
        cacheEntity.setTargetLanguage(targetLanguage);
        cacheEntity.setOriginalText(originalText);
        cacheEntity.setOriginalCaption(originalCaption);
        cacheEntity.setTranslatedText(
                messageTranslationProviderClient.translate(
                        providerUrl,
                        sourceLanguage,
                        targetLanguage,
                        originalText,
                        apiKey
                )
        );
        cacheEntity.setTranslatedCaption(
                messageTranslationProviderClient.translate(
                        providerUrl,
                        sourceLanguage,
                        targetLanguage,
                        originalCaption,
                        apiKey
                )
        );

        return toResponse(messageTranslationCacheRepository.save(cacheEntity));
    }

    private String resolveTargetLanguage(UserEntity requester, TranslateMessageRequest request) {
        String explicit = normalizeLanguage(request.targetLanguage(), "targetLanguage");
        if (explicit != null) {
            return explicit;
        }
        String preferredTarget = normalizeLanguage(requester.getTranslationTargetLanguage(), "translationTargetLanguage");
        if (preferredTarget != null) {
            return preferredTarget;
        }
        String preferredLanguage = normalizeLanguage(requester.getPreferredLanguage(), "preferredLanguage");
        if (preferredLanguage != null) {
            return preferredLanguage;
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "targetLanguage is required when no user translation preference is configured"
        );
    }

    private TranslatedMessageResponse toResponse(MessageTranslationCacheEntity cacheEntity) {
        return new TranslatedMessageResponse(
                cacheEntity.getMessageId(),
                cacheEntity.getProvider(),
                cacheEntity.getSourceLanguage(),
                cacheEntity.getTargetLanguage(),
                cacheEntity.getOriginalText(),
                cacheEntity.getTranslatedText(),
                cacheEntity.getOriginalCaption(),
                cacheEntity.getTranslatedCaption()
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeLanguage(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        String result = normalized.toLowerCase(Locale.ROOT);
        if (!result.matches("[a-z-]{2,16}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "%s is invalid".formatted(fieldName));
        }
        return result;
    }
}
