package com.alex.messenger.chat;

import com.alex.messenger.chat.dto.ChatAdminLogResponse;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatAdminLogService {

    private final ChatAdminLogRepository chatAdminLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void log(
            UUID chatId,
            UUID actorUserId,
            UUID subjectUserId,
            String eventType,
            String summary,
            UUID messageId,
            UUID inviteLinkId
    ) {
        ChatAdminLogEntity entity = new ChatAdminLogEntity();
        entity.setChatId(chatId);
        entity.setActorUserId(actorUserId);
        entity.setSubjectUserId(subjectUserId);
        entity.setEventType(eventType);
        entity.setSummary(truncate(summary, 500));
        entity.setMessageId(messageId);
        entity.setInviteLinkId(inviteLinkId);
        chatAdminLogRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<ChatAdminLogResponse> list(UUID chatId, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        List<ChatAdminLogEntity> events = chatAdminLogRepository.findAllByChatIdOrderByCreatedAtDesc(
                chatId,
                PageRequest.of(0, normalizedLimit)
        );
        Set<UUID> userIds = new LinkedHashSet<>();
        for (ChatAdminLogEntity event : events) {
            userIds.add(event.getActorUserId());
            if (event.getSubjectUserId() != null) {
                userIds.add(event.getSubjectUserId());
            }
        }
        Map<UUID, UserEntity> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, user -> user));

        return events.stream()
                .map(event -> new ChatAdminLogResponse(
                        event.getId(),
                        event.getEventType(),
                        event.getActorUserId(),
                        displayName(usersById.get(event.getActorUserId())),
                        event.getSubjectUserId(),
                        displayName(usersById.get(event.getSubjectUserId())),
                        event.getMessageId(),
                        event.getInviteLinkId(),
                        event.getSummary(),
                        event.getCreatedAt()
                ))
                .toList();
    }

    private String displayName(UserEntity user) {
        return user != null ? user.getDisplayName() : "Unknown";
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
