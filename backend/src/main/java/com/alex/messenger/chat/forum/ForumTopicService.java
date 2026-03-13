package com.alex.messenger.chat.forum;

import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatMemberEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.dto.CreateForumTopicRequest;
import com.alex.messenger.chat.dto.ForumTopicResponse;
import com.alex.messenger.chat.dto.UpdateForumTopicRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ForumTopicService {

    private final ForumTopicRepository forumTopicRepository;
    private final ChatService chatService;

    @Transactional(readOnly = true)
    public List<ForumTopicResponse> listTopics(UUID requesterId, UUID chatId) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        ensureForumEnabled(chat);
        return forumTopicRepository.findVisibleTopics(chatId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ForumTopicResponse createTopic(UUID requesterId, UUID chatId, CreateForumTopicRequest request) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        ensureForumEnabled(chat);
        chatService.ensureCanPost(chat, requesterId);

        ForumTopicEntity topic = new ForumTopicEntity();
        topic.setChatId(chatId);
        topic.setTitle(request.title().trim());
        topic.setIconEmoji(normalizeIconEmoji(request.iconEmoji()));
        topic.setCreatedBy(requesterId);
        topic.setGeneralTopic(false);
        topic.setClosed(false);
        topic.setHidden(false);
        topic.setLastMessageAt(null);
        return toResponse(forumTopicRepository.save(topic));
    }

    @Transactional
    public ForumTopicResponse updateTopic(UUID requesterId, UUID chatId, UUID topicId, UpdateForumTopicRequest request) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        ensureForumEnabled(chat);
        ForumTopicEntity topic = getTopic(chatId, topicId);
        ensureCanManageTopic(requesterId, topic);
        if (Boolean.TRUE.equals(topic.getGeneralTopic()) && Boolean.TRUE.equals(request.hidden())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "General topic cannot be hidden");
        }
        if (request.title() != null && !request.title().isBlank()) {
            topic.setTitle(request.title().trim());
        }
        if (request.iconEmoji() != null) {
            topic.setIconEmoji(normalizeIconEmoji(request.iconEmoji()));
        }
        if (request.closed() != null) {
            topic.setClosed(request.closed());
        }
        if (request.hidden() != null) {
            topic.setHidden(request.hidden());
        }
        return toResponse(forumTopicRepository.save(topic));
    }

    @Transactional(readOnly = true)
    public ForumTopicEntity resolveTopicForRead(ChatEntity chat, UUID requesterId, UUID topicId) {
        chatService.getOwnedChat(requesterId, chat.getId());
        if (!Boolean.TRUE.equals(chat.getForumEnabled())) {
            if (topicId != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forum topics are disabled for this chat");
            }
            return null;
        }
        ensureForumEnabled(chat);
        return topicId != null
                ? getVisibleTopic(chat.getId(), topicId)
                : getGeneralTopic(chat.getId());
    }

    @Transactional(readOnly = true)
    public ForumTopicEntity resolveTopicForWrite(ChatEntity chat, UUID requesterId, UUID topicId) {
        chatService.ensureCanPost(chat, requesterId);
        ForumTopicEntity topic = resolveTopicForRead(chat, requesterId, topicId);
        if (topic != null && Boolean.TRUE.equals(topic.getClosed())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Topic is closed");
        }
        return topic;
    }

    @Transactional
    public ForumTopicEntity ensureGeneralTopic(ChatEntity chat) {
        ensureForumEnabled(chat);
        return forumTopicRepository.findByChatIdAndGeneralTopicTrue(chat.getId())
                .orElseGet(() -> {
                    ForumTopicEntity topic = new ForumTopicEntity();
                    topic.setChatId(chat.getId());
                    topic.setTitle("General");
                    topic.setCreatedBy(chat.getCreatedBy());
                    topic.setGeneralTopic(true);
                    topic.setClosed(false);
                    topic.setHidden(false);
                    topic.setLastMessageAt(chat.getLastMessageAt());
                    return forumTopicRepository.save(topic);
                });
    }

    @Transactional
    public void touchTopic(UUID topicId, Instant timestamp) {
        if (topicId == null) {
            return;
        }
        forumTopicRepository.findById(topicId).ifPresent(topic -> {
            topic.setLastMessageAt(timestamp);
            forumTopicRepository.save(topic);
        });
    }

    @Transactional(readOnly = true)
    public long countVisibleTopics(UUID chatId) {
        return forumTopicRepository.countByChatIdAndHiddenFalse(chatId);
    }

    @Transactional(readOnly = true)
    public ForumTopicEntity getTopic(UUID chatId, UUID topicId) {
        return forumTopicRepository.findByIdAndChatId(topicId, chatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found"));
    }

    private ForumTopicEntity getVisibleTopic(UUID chatId, UUID topicId) {
        ForumTopicEntity topic = getTopic(chatId, topicId);
        if (Boolean.TRUE.equals(topic.getHidden())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found");
        }
        return topic;
    }

    private ForumTopicEntity getGeneralTopic(UUID chatId) {
        return forumTopicRepository.findByChatIdAndGeneralTopicTrue(chatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "General topic not found"));
    }

    private void ensureForumEnabled(ChatEntity chat) {
        if (!"GROUP".equals(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forum topics are only available in groups");
        }
        if (!Boolean.TRUE.equals(chat.getForumEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forum topics are disabled for this chat");
        }
    }

    private void ensureCanManageTopic(UUID requesterId, ForumTopicEntity topic) {
        ChatMemberEntity membership = chatService.getMembership(topic.getChatId(), requesterId);
        if (requesterId.equals(topic.getCreatedBy())) {
            return;
        }
        if (List.of("OWNER", "ADMIN").contains(membership.getRole())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only topic creator or admins can manage topics");
    }

    private String normalizeIconEmoji(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private ForumTopicResponse toResponse(ForumTopicEntity topic) {
        return new ForumTopicResponse(
                topic.getId(),
                topic.getChatId(),
                topic.getTitle(),
                topic.getIconEmoji(),
                Boolean.TRUE.equals(topic.getGeneralTopic()),
                Boolean.TRUE.equals(topic.getClosed()),
                Boolean.TRUE.equals(topic.getHidden()),
                topic.getCreatedBy(),
                topic.getCreatedAt(),
                topic.getUpdatedAt(),
                topic.getLastMessageAt()
        );
    }
}
