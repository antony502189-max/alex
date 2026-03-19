package com.alex.messenger.chat.forum;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatMemberEntity;
import com.alex.messenger.chat.ChatMemberId;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.dto.UpdateForumTopicRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ForumTopicServiceTest {

    @Mock
    private ForumTopicRepository forumTopicRepository;

    @Mock
    private ChatService chatService;

    private ForumTopicService forumTopicService;

    @BeforeEach
    void setUp() {
        forumTopicService = new ForumTopicService(forumTopicRepository, chatService);
    }

    @Test
    void updateTopicRejectsBlankTitleOnlyRequest() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();

        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType("GROUP");
        chat.setForumEnabled(true);

        ForumTopicEntity topic = new ForumTopicEntity();
        topic.setId(topicId);
        topic.setChatId(chatId);
        topic.setCreatedBy(requesterId);
        topic.setTitle("General");

        ChatMemberEntity membership = new ChatMemberEntity();
        membership.setId(new ChatMemberId(chatId, requesterId));
        membership.setRole("MEMBER");

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(forumTopicRepository.findByIdAndChatId(topicId, chatId)).thenReturn(Optional.of(topic));
        when(chatService.getMembership(chatId, requesterId)).thenReturn(membership);

        assertThatThrownBy(() -> forumTopicService.updateTopic(
                requesterId,
                chatId,
                topicId,
                new UpdateForumTopicRequest("   ", null, null, null)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(throwable -> ((ResponseStatusException) throwable).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(forumTopicRepository, never()).save(any(ForumTopicEntity.class));
    }
}
