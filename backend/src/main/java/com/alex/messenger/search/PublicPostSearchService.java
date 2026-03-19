package com.alex.messenger.search;

import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatRepository;
import com.alex.messenger.message.MessageEntity;
import com.alex.messenger.message.MessageLookupEntity;
import com.alex.messenger.message.MessageRepository;
import com.alex.messenger.message.MessageSearchCorpusService;
import com.alex.messenger.message.MessageTextContent;
import com.alex.messenger.message.MessageContentCodec;
import com.alex.messenger.crypto.ChatEncryptionService;
import com.alex.messenger.search.dto.PublicPostSearchResponse;
import com.alex.messenger.search.dto.PublicPostSearchResult;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicPostSearchService {

    private final PublicPostSearchIndexRepository publicPostSearchIndexRepository;
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final ChatEncryptionService chatEncryptionService;
    private final MessageContentCodec messageContentCodec;
    private final MessageSearchCorpusService messageSearchCorpusService;

    @Transactional(readOnly = true)
    public PublicPostSearchResponse searchPublicPosts(UUID requesterId, String query, int limit) {
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isBlank()) {
            return new PublicPostSearchResponse(query, List.of());
        }

        int normalizedLimit = Math.min(Math.max(limit, 1), 50);
        List<PublicPostSearchIndexEntity> matches = publicPostSearchIndexRepository.search(
                normalizedQuery,
                PageRequest.of(0, normalizedLimit)
        );
        Map<UUID, ChatEntity> chatsById = chatRepository.findAllById(
                matches.stream().map(PublicPostSearchIndexEntity::getChatId).distinct().toList()
        ).stream().collect(Collectors.toMap(ChatEntity::getId, Function.identity()));

        return new PublicPostSearchResponse(
                query,
                matches.stream()
                        .map(match -> toResult(chatsById.get(match.getChatId()), match))
                        .filter(Objects::nonNull)
                        .toList()
        );
    }

    @Transactional
    public void syncMessage(MessageLookupEntity lookup) {
        ChatEntity chat = chatRepository.findById(lookup.getChatId()).orElse(null);
        if (!isIndexable(chat) || lookup.getDeletedAt() != null) {
            publicPostSearchIndexRepository.deleteById(lookup.getMessageId());
            return;
        }
        upsertIndex(
                chat,
                lookup.getMessageId(),
                lookup.getSenderId(),
                lookup.getTopicId(),
                lookup.getDiscussionChatId(),
                lookup.getDiscussionRootMessageId(),
                lookup.getCiphertext(),
                lookup.getNonce(),
                lookup.getKeyVersion(),
                lookup.getPollId(),
                lookup.getStickerId(),
                lookup.getAttachmentIds(),
                lookup.getCreatedAt(),
                lookup.getEditedAt()
        );
    }

    @Transactional
    public void refreshChatIndex(UUID chatId) {
        ChatEntity chat = chatRepository.findById(chatId).orElse(null);
        publicPostSearchIndexRepository.deleteByChatId(chatId);
        if (!isIndexable(chat)) {
            return;
        }

        for (MessageEntity message : messageRepository.findAllByChatId(chatId)) {
            if (message.getDeletedAt() != null) {
                continue;
            }
            upsertIndex(
                    chat,
                    message.getKey().getMessageId(),
                    message.getSenderId(),
                    message.getTopicId(),
                    message.getDiscussionChatId(),
                    message.getDiscussionRootMessageId(),
                    message.getCiphertext(),
                    message.getNonce(),
                    message.getKeyVersion(),
                    message.getPollId(),
                    message.getStickerId(),
                    message.getAttachmentIds(),
                    message.getCreatedAt(),
                    message.getEditedAt()
            );
        }
    }

    private void upsertIndex(
            ChatEntity chat,
            UUID messageId,
            UUID senderId,
            UUID topicId,
            UUID discussionChatId,
            UUID discussionRootMessageId,
            String ciphertext,
            String nonce,
            Integer keyVersion,
            UUID pollId,
            UUID stickerId,
            List<UUID> attachmentIds,
            Instant createdAt,
            Instant editedAt
    ) {
        if (ciphertext == null || nonce == null || keyVersion == null) {
            publicPostSearchIndexRepository.deleteById(messageId);
            return;
        }
        String plaintext = chatEncryptionService.decrypt(chat.getId(), ciphertext, nonce, keyVersion);
        MessageTextContent content = messageContentCodec.decode(plaintext);
        List<UUID> normalizedAttachmentIds = attachmentIds != null ? attachmentIds : List.of();
        String searchCorpus = messageSearchCorpusService.buildSearchCorpus(messageId, content, normalizedAttachmentIds);
        if (searchCorpus.isBlank()) {
            publicPostSearchIndexRepository.deleteById(messageId);
            return;
        }

        PublicPostSearchIndexEntity entity = publicPostSearchIndexRepository.findById(messageId)
                .orElseGet(PublicPostSearchIndexEntity::new);
        entity.setMessageId(messageId);
        entity.setChatId(chat.getId());
        entity.setSenderId(senderId);
        entity.setTopicId(topicId);
        entity.setDiscussionChatId(discussionChatId);
        entity.setDiscussionRootMessageId(discussionRootMessageId);
        entity.setExcerpt(buildExcerpt(content, pollId, stickerId, normalizedAttachmentIds.size()));
        entity.setSearchCorpus(searchCorpus.toLowerCase(Locale.ROOT));
        entity.setMessageType(resolveMessageType(content, pollId, stickerId, normalizedAttachmentIds.size()));
        entity.setAttachmentCount(normalizedAttachmentIds.size());
        entity.setHasMedia(!normalizedAttachmentIds.isEmpty() || stickerId != null);
        entity.setCreatedAt(createdAt != null ? createdAt : Instant.now());
        entity.setUpdatedAt(editedAt != null ? editedAt : (createdAt != null ? createdAt : Instant.now()));
        publicPostSearchIndexRepository.save(entity);
    }

    private PublicPostSearchResult toResult(ChatEntity chat, PublicPostSearchIndexEntity match) {
        if (!isIndexable(chat)) {
            return null;
        }
        return new PublicPostSearchResult(
                match.getChatId(),
                chat.getTitle(),
                chat.getPublicUsername(),
                chat.getAbout(),
                match.getMessageId(),
                match.getSenderId(),
                match.getTopicId(),
                match.getDiscussionChatId(),
                match.getDiscussionRootMessageId(),
                match.getExcerpt(),
                match.getMessageType(),
                match.getAttachmentCount() != null ? match.getAttachmentCount() : 0,
                Boolean.TRUE.equals(match.getHasMedia()),
                match.getCreatedAt()
        );
    }

    private boolean isIndexable(ChatEntity chat) {
        return chat != null
                && "CHANNEL".equals(chat.getChatType())
                && chat.getPublicUsername() != null
                && !chat.getPublicUsername().isBlank();
    }

    private String buildExcerpt(MessageTextContent content, UUID pollId, UUID stickerId, int attachmentCount) {
        String base = messageContentCodec.buildSearchText(content)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
        if (!base.isBlank()) {
            return truncate(base, 600);
        }
        if (pollId != null) {
            return "Poll post";
        }
        if (stickerId != null) {
            return "Sticker post";
        }
        if (attachmentCount > 1) {
            return "Media album";
        }
        if (attachmentCount == 1) {
            return "Media post";
        }
        return "Channel post";
    }

    private String resolveMessageType(MessageTextContent content, UUID pollId, UUID stickerId, int attachmentCount) {
        if (content.messageType() != null && !content.messageType().isBlank()) {
            return content.messageType();
        }
        if (pollId != null) {
            return "POLL";
        }
        if (stickerId != null) {
            return "STICKER";
        }
        if (attachmentCount > 1) {
            return "ALBUM";
        }
        if (attachmentCount == 1) {
            return "MEDIA";
        }
        return "TEXT";
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
