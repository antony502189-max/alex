package com.alex.messenger.chat;

import com.alex.messenger.chat.dto.PinnedMessageHistoryResponse;
import com.alex.messenger.message.MessageService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        int normalizedLimit = requireLimit(limit, 50);
        chatService.getOwnedChat(requesterId, chatId);

        int pageSize = Math.min(Math.max(normalizedLimit * 2, normalizedLimit + 5), 50);
        int scanLimit = Math.min(Math.max(normalizedLimit * 4, pageSize * 2), 200);
        List<PinnedMessageHistoryResponse> visiblePins = new ArrayList<>();

        for (int page = 0; page * pageSize < scanLimit && visiblePins.size() < normalizedLimit; page++) {
            List<ChatPinEventEntity> pinEvents = chatPinEventRepository.findAllByChatIdOrderByPinnedAtDesc(
                    chatId,
                    PageRequest.of(page, pageSize)
            );
            if (pinEvents.isEmpty()) {
                break;
            }

            Map<UUID, UserEntity> usersById = userRepository.findAllById(
                    pinEvents.stream().map(ChatPinEventEntity::getPinnedByUserId).distinct().toList()
            ).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));

            pinEvents.stream()
                    .map(pinEvent -> toResponse(requesterId, pinEvent, usersById.get(pinEvent.getPinnedByUserId())))
                    .filter(Objects::nonNull)
                    .limit(normalizedLimit - visiblePins.size())
                    .forEach(visiblePins::add);

            if (pinEvents.size() < pageSize) {
                break;
            }
        }

        return visiblePins;
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
            return null;
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

    private int requireLimit(int limit, int max) {
        if (limit < 1 || limit > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and " + max);
        }
        return limit;
    }
}
