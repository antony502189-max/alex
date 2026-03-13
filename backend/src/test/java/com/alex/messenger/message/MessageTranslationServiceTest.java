package com.alex.messenger.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.feature.FeatureProperties;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.TranslateMessageRequest;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageTranslationServiceTest {

    @Mock
    private MessageService messageService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageTranslationProviderClient messageTranslationProviderClient;

    @Mock
    private MessageTranslationCacheRepository messageTranslationCacheRepository;

    private MessageTranslationService messageTranslationService;

    @BeforeEach
    void setUp() {
        FeatureProperties featureProperties = new FeatureProperties();
        featureProperties.setTranslations(true);
        MessageTranslationProperties properties = new MessageTranslationProperties();
        properties.setProviderUrl("https://translate.internal");
        properties.setApiKey("test-api-key");
        messageTranslationService = new MessageTranslationService(
                new FeatureFlagService(featureProperties),
                messageService,
                properties,
                userRepository,
                messageTranslationProviderClient,
                messageTranslationCacheRepository
        );
    }

    @Test
    void translateFallsBackToUserTranslationTargetLanguageAndCachesResult() {
        UUID requesterId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UserEntity requester = new UserEntity();
        requester.setId(requesterId);
        requester.setTranslationTargetLanguage("de");

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(messageService.getMessage(requesterId, messageId)).thenReturn(message(messageId, "Hello", "Photo"));
        when(messageTranslationCacheRepository.findByMessageIdAndTargetLanguage(messageId, "de"))
                .thenReturn(Optional.empty());
        when(messageTranslationProviderClient.translate("https://translate.internal", null, "de", "Hello", "test-api-key"))
                .thenReturn("Hallo");
        when(messageTranslationProviderClient.translate("https://translate.internal", null, "de", "Photo", "test-api-key"))
                .thenReturn("Foto");
        when(messageTranslationCacheRepository.save(any(MessageTranslationCacheEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = messageTranslationService.translate(
                requesterId,
                messageId,
                new TranslateMessageRequest(null, null)
        );

        ArgumentCaptor<MessageTranslationCacheEntity> captor =
                ArgumentCaptor.forClass(MessageTranslationCacheEntity.class);
        verify(messageTranslationCacheRepository).save(captor.capture());
        MessageTranslationCacheEntity saved = captor.getValue();

        assertThat(response.messageId()).isEqualTo(messageId);
        assertThat(response.sourceLanguage()).isEqualTo("auto");
        assertThat(response.targetLanguage()).isEqualTo("de");
        assertThat(response.translatedText()).isEqualTo("Hallo");
        assertThat(response.translatedCaption()).isEqualTo("Foto");
        assertThat(saved.getSourceLanguage()).isEqualTo("auto");
        assertThat(saved.getTargetLanguage()).isEqualTo("de");
        assertThat(saved.getOriginalText()).isEqualTo("Hello");
        assertThat(saved.getOriginalCaption()).isEqualTo("Photo");
        assertThat(saved.getTranslatedText()).isEqualTo("Hallo");
    }

    @Test
    void translateReturnsCachedVersionWhenMessageDidNotChange() {
        UUID requesterId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UserEntity requester = new UserEntity();
        requester.setId(requesterId);
        requester.setPreferredLanguage("es");

        MessageTranslationCacheEntity cached = new MessageTranslationCacheEntity();
        cached.setMessageId(messageId);
        cached.setProvider("https://translate.internal");
        cached.setSourceLanguage("en");
        cached.setTargetLanguage("es");
        cached.setOriginalText("Hello");
        cached.setOriginalCaption("Photo");
        cached.setTranslatedText("Hola");
        cached.setTranslatedCaption("Foto");

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(messageService.getMessage(requesterId, messageId)).thenReturn(message(messageId, "Hello", "Photo"));
        when(messageTranslationCacheRepository.findByMessageIdAndTargetLanguage(messageId, "es"))
                .thenReturn(Optional.of(cached));

        var response = messageTranslationService.translate(
                requesterId,
                messageId,
                new TranslateMessageRequest(null, "EN")
        );

        assertThat(response.translatedText()).isEqualTo("Hola");
        assertThat(response.translatedCaption()).isEqualTo("Foto");
        verify(messageTranslationProviderClient, never()).translate(any(), any(), any(), any(), any());
        verify(messageTranslationCacheRepository, never()).save(any(MessageTranslationCacheEntity.class));
    }

    private ChatMessageResponse message(UUID messageId, String text, String caption) {
        return new ChatMessageResponse(
                UUID.randomUUID(),
                messageId,
                null,
                UUID.randomUUID(),
                "Sender",
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
                text,
                List.of(),
                "TEXT",
                caption,
                false,
                null,
                null,
                null,
                Instant.parse("2026-03-14T12:00:00Z"),
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
