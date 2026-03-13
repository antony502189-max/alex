package com.alex.messenger.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.alex.messenger.attachment.AttachmentEntity;
import com.alex.messenger.attachment.AttachmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageSearchCorpusServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private MessageTranslationCacheRepository messageTranslationCacheRepository;

    private MessageSearchCorpusService messageSearchCorpusService;

    @BeforeEach
    void setUp() {
        messageSearchCorpusService = new MessageSearchCorpusService(
                attachmentRepository,
                messageTranslationCacheRepository,
                new MessageContentCodec(new ObjectMapper())
        );
    }

    @Test
    void buildSearchCorpusIncludesAttachmentMetadataAndCachedTranslations() {
        UUID messageId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        AttachmentEntity attachment = new AttachmentEntity();
        attachment.setId(attachmentId);
        attachment.setOriginalFileName("summer-trip.jpg");
        attachment.setKind("IMAGE");
        attachment.setContentType("image/jpeg");

        MessageTranslationCacheEntity translation = new MessageTranslationCacheEntity();
        translation.setMessageId(messageId);
        translation.setOriginalText("Vacation");
        translation.setOriginalCaption("Beach");
        translation.setTargetLanguage("de");
        translation.setTranslatedText("urlaub");
        translation.setTranslatedCaption("strand");

        when(attachmentRepository.findAllByIdIn(List.of(attachmentId))).thenReturn(List.of(attachment));
        when(messageTranslationCacheRepository.findAllByMessageId(messageId)).thenReturn(List.of(translation));

        String corpus = messageSearchCorpusService.buildSearchCorpus(
                messageId,
                new MessageTextContent("Vacation", List.of(), null, "Beach", null, null, null, false),
                List.of(attachmentId)
        );

        assertThat(corpus).contains("vacation");
        assertThat(corpus).contains("beach");
        assertThat(corpus).contains("summer-trip.jpg");
        assertThat(corpus).contains("image/jpeg");
        assertThat(corpus).contains("urlaub");
        assertThat(corpus).contains("strand");
        assertThat(corpus).contains("de");
    }
}
