package com.alex.messenger.chat;

import com.alex.messenger.chat.dto.PinnedMessageHistoryResponse;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ChatPinHistoryService {

    private final ChatService chatService;
    private final ChatPinEventRepository chatPinEventRepository;
    private final MessageService messageService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<PinnedMessageHistoryResponse> listPinnedMessages(UUID requesterId, UUID chatId, int limit) {
        chatService.getOwnedChat(requesterId, chatId);

        int normalizedLimit = Math.min(Math.max(limit, 1), 50);
        List<ChatPinEventEntity> pinEvents = chatPinEventRepository.findAllByChatIdOrderByPinnedAtDesc(
                chatId,
                PageRequest.of(0, normalizedLimit)
        );
        Map<UUID, UserEntity> usersById = userRepository.findAllById(
                pinEvents.stream().map(ChatPinEventEntity::getPinnedByUserId).distinct().toList()
        ).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        return pinEvents.stream()
                .map(pinEvent -> toResponse(requesterId, pinEvent, usersById.get(pinEvent.getPinnedByUserId())))
                .toList();
    }

    private PinnedMessageHistoryResponse toResponse(
            UUID requesterId,
            ChatPinEventEntity pinEvent,
            UserEntity pinnedByUser
    ) {
        ChatMessageResponse message = null;
        try {
            message = messageService.getMessage(requesterId, pinEvent.getMessageId());
        } catch (ResponseStatusException exception) {
            if (!HttpStatus.NOT_FOUND.equals(exception.getStatusCode())) {
                throw exception;
            }
        }

        return new PinnedMessageHistoryResponse(
                pinEvent.getId(),
                pinEvent.getChatId(),
                pinEvent.getMessageId(),
                pinEvent.getPinnedByUserId(),
                pinnedByUser != null ? pinnedByUser.getDisplayName() : "Unknown",
                pinEvent.getPinnedAt(),
                Boolean.TRUE.equals(pinEvent.getActive()),
                pinEvent.getUnpinnedAt(),
                message
        );
    }
}
