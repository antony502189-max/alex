package com.alex.messenger.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatRepository;
import com.alex.messenger.crypto.ChatEncryptionService;
import com.alex.messenger.message.MessageContentCodec;
import com.alex.messenger.message.MessageEntity;
import com.alex.messenger.message.MessageLookupEntity;
import com.alex.messenger.message.MessagePrimaryKey;
import com.alex.messenger.message.MessageRepository;
import com.alex.messenger.message.MessageSearchCorpusService;
import com.alex.messenger.message.MessageTextContent;
import com.alex.messenger.search.dto.PublicPostSearchResponse;
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
class PublicPostSearchServiceTest {

    @Mock
    private PublicPostSearchIndexRepository publicPostSearchIndexRepository;

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChatEncryptionService chatEncryptionService;

    @Mock
    private MessageContentCodec messageContentCodec;

    @Mock
    private MessageSearchCorpusService messageSearchCorpusService;

    private PublicPostSearchService publicPostSearchService;

    @BeforeEach
    void setUp() {
        publicPostSearchService = new PublicPostSearchService(
                publicPostSearchIndexRepository,
                chatRepository,
                messageRepository,
                chatEncryptionService,
                messageContentCodec,
                messageSearchCorpusService
        );
    }

    @Test
    void syncMessageIndexesPublicChannelPost() {
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        ChatEntity channel = publicChannel(chatId, "news");
        MessageLookupEntity lookup = new MessageLookupEntity();
        lookup.setMessageId(messageId);
        lookup.setChatId(chatId);
        lookup.setSenderId(senderId);
        lookup.setCiphertext("cipher");
        lookup.setNonce("nonce");
        lookup.setKeyVersion(1);
        lookup.setCreatedAt(Instant.parse("2026-03-19T10:00:00Z"));
        lookup.setAttachmentIds(List.of());

        MessageTextContent content = new MessageTextContent("Hello public", List.of());

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(channel));
        when(chatEncryptionService.decrypt(chatId, "cipher", "nonce", 1)).thenReturn("decoded");
        when(messageContentCodec.decode("decoded")).thenReturn(content);
        when(messageContentCodec.buildSearchText(content)).thenReturn("Hello public");
        when(messageSearchCorpusService.buildSearchCorpus(messageId, content, List.of())).thenReturn("hello public");
        when(publicPostSearchIndexRepository.findById(messageId)).thenReturn(Optional.empty());

        publicPostSearchService.syncMessage(lookup);

        ArgumentCaptor<PublicPostSearchIndexEntity> entityCaptor =
                ArgumentCaptor.forClass(PublicPostSearchIndexEntity.class);
        verify(publicPostSearchIndexRepository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getMessageId()).isEqualTo(messageId);
        assertThat(entityCaptor.getValue().getChatId()).isEqualTo(chatId);
        assertThat(entityCaptor.getValue().getSenderId()).isEqualTo(senderId);
        assertThat(entityCaptor.getValue().getExcerpt()).isEqualTo("Hello public");
        assertThat(entityCaptor.getValue().getSearchCorpus()).isEqualTo("hello public");
        assertThat(entityCaptor.getValue().getMessageType()).isEqualTo("TEXT");
    }

    @Test
    void refreshChatIndexDeletesEntriesWhenChannelIsPrivate() {
        UUID chatId = UUID.randomUUID();

        ChatEntity privateChannel = new ChatEntity();
        privateChannel.setId(chatId);
        privateChannel.setChatType("CHANNEL");
        privateChannel.setTitle("Private");
        privateChannel.setPublicUsername(null);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(privateChannel));

        publicPostSearchService.refreshChatIndex(chatId);

        verify(publicPostSearchIndexRepository).deleteByChatId(chatId);
    }

    @Test
    void refreshChatIndexBackfillsExistingPublicChannelPosts() {
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        ChatEntity channel = publicChannel(chatId, "daily");
        MessageEntity message = new MessageEntity();
        message.setKey(new MessagePrimaryKey(chatId, messageId));
        message.setSenderId(senderId);
        message.setCiphertext("cipher");
        message.setNonce("nonce");
        message.setKeyVersion(1);
        message.setCreatedAt(Instant.parse("2026-03-19T10:00:00Z"));
        message.setAttachmentIds(List.of());

        MessageTextContent content = new MessageTextContent("Backfill me", List.of());

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(channel));
        when(messageRepository.findAllByChatId(chatId)).thenReturn(List.of(message));
        when(chatEncryptionService.decrypt(chatId, "cipher", "nonce", 1)).thenReturn("decoded");
        when(messageContentCodec.decode("decoded")).thenReturn(content);
        when(messageContentCodec.buildSearchText(content)).thenReturn("Backfill me");
        when(messageSearchCorpusService.buildSearchCorpus(messageId, content, List.of())).thenReturn("backfill me");
        when(publicPostSearchIndexRepository.findById(messageId)).thenReturn(Optional.empty());

        publicPostSearchService.refreshChatIndex(chatId);

        verify(publicPostSearchIndexRepository).deleteByChatId(chatId);
        verify(publicPostSearchIndexRepository).save(any(PublicPostSearchIndexEntity.class));
    }

    @Test
    void searchPublicPostsReturnsOnlyCurrentlyPublicChannels() {
        UUID chatId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        PublicPostSearchIndexEntity entity = new PublicPostSearchIndexEntity();
        entity.setMessageId(messageId);
        entity.setChatId(chatId);
        entity.setSenderId(UUID.randomUUID());
        entity.setExcerpt("Breaking news");
        entity.setSearchCorpus("breaking news");
        entity.setMessageType("TEXT");
        entity.setAttachmentCount(0);
        entity.setHasMedia(false);
        entity.setCreatedAt(Instant.parse("2026-03-19T10:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-03-19T10:00:00Z"));

        when(publicPostSearchIndexRepository.search(any(), any())).thenReturn(List.of(entity));
        when(chatRepository.findAllById(List.of(chatId))).thenReturn(List.of(publicChannel(chatId, "channelnews")));

        PublicPostSearchResponse response = publicPostSearchService.searchPublicPosts(
                UUID.randomUUID(),
                "breaking",
                20
        );

        assertThat(response.posts()).hasSize(1);
        assertThat(response.posts().get(0).chatId()).isEqualTo(chatId);
        assertThat(response.posts().get(0).channelPublicUsername()).isEqualTo("channelnews");
        assertThat(response.posts().get(0).excerpt()).isEqualTo("Breaking news");
    }

    private ChatEntity publicChannel(UUID chatId, String publicUsername) {
        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("CHANNEL");
        chat.setTitle("Channel");
        chat.setAbout("About");
        chat.setPublicUsername(publicUsername);
        return chat;
    }
}
