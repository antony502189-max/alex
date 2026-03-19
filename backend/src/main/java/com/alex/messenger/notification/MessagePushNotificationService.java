package com.alex.messenger.notification;

import com.alex.messenger.attachment.AttachmentService;
import com.alex.messenger.auth.session.PushSessionTarget;
import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatMemberEntity;
import com.alex.messenger.chat.ChatMemberRepository;
import com.alex.messenger.chat.ChatRepository;
import com.alex.messenger.chat.forum.ForumTopicService;
import com.alex.messenger.message.MessageEvent;
import com.alex.messenger.message.MessageTextContent;
import com.alex.messenger.message.dto.MessageAttachmentResponse;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MessagePushNotificationService {

    private final PushNotificationService pushNotificationService;
    private final UserSessionService userSessionService;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final UserRepository userRepository;
    private final AttachmentService attachmentService;
    private final ForumTopicService forumTopicService;

    public void notifyNewMessage(
            MessageEvent event,
            MessageTextContent content,
            List<MessageAttachmentResponse> attachments
    ) {
        if (!"SENT".equals(event.deliveryStatus()) || event.recipientIds().isEmpty()) {
            return;
        }
        if (content != null && content.silent()) {
            return;
        }

        ChatEntity chat = chatRepository.findById(event.chatId()).orElse(null);
        UserEntity sender = userRepository.findById(event.senderId()).orElse(null);
        if (chat == null || sender == null) {
            return;
        }

        Map<UUID, ChatMemberEntity> memberships = chatMemberRepository.findAllByIdChatId(chat.getId()).stream()
                .collect(Collectors.toMap(member -> member.getId().getUserId(), Function.identity()));
        String preview = buildPreview(event, content, attachments);
        List<PushNotificationCommand> commands = new ArrayList<>();

        for (UUID recipientId : event.recipientIds()) {
            ChatMemberEntity membership = memberships.get(recipientId);
            if (membership == null || isMuted(membership) || !canNotifyRecipient(chat, recipientId, event.topicId())) {
                continue;
            }

            String title = buildTitle(chat, sender);
            String body = buildBody(chat, sender, preview);
            for (PushSessionTarget target : userSessionService.getPushTargets(recipientId)) {
                commands.add(new PushNotificationCommand(
                        target.provider(),
                        target.pushToken(),
                        title,
                        body,
                        Map.of(
                                "chatId", event.chatId().toString(),
                                "messageId", event.messageId().toString(),
                                "topicId", event.topicId() != null ? event.topicId().toString() : "",
                                "senderId", event.senderId().toString()
                        )
                ));
            }
        }

        pushNotificationService.send(commands);
    }

    private boolean canNotifyRecipient(ChatEntity chat, UUID recipientId, UUID topicId) {
        if (chat == null || topicId == null || !Boolean.TRUE.equals(chat.getForumEnabled())) {
            return true;
        }
        try {
            forumTopicService.resolveTopicForRead(chat, recipientId, topicId);
            return true;
        } catch (ResponseStatusException exception) {
            return false;
        }
    }

    private boolean isMuted(ChatMemberEntity membership) {
        return membership.getMutedUntil() != null && membership.getMutedUntil().isAfter(Instant.now());
    }

    private String buildTitle(ChatEntity chat, UserEntity sender) {
        String senderName = resolveSenderName(sender);
        if ("DIRECT".equals(chat.getChatType())) {
            return senderName;
        }
        return chat.getTitle() != null && !chat.getTitle().isBlank()
                ? chat.getTitle()
                : "Alex";
    }

    private String buildBody(ChatEntity chat, UserEntity sender, String preview) {
        String senderName = resolveSenderName(sender);
        if ("GROUP".equals(chat.getChatType())) {
            return senderName + ": " + preview;
        }
        if ("CHANNEL".equals(chat.getChatType())) {
            return preview;
        }
        return preview;
    }

    private String buildPreview(MessageEvent event, MessageTextContent content, List<MessageAttachmentResponse> attachments) {
        String normalizedText = content != null && content.text() != null ? content.text().trim() : "";
        if (!normalizedText.isBlank()) {
            return normalizedText.length() > 120 ? normalizedText.substring(0, 120) + "..." : normalizedText;
        }
        if (content != null && "LOCATION".equals(content.messageType())) {
            return "shared a location";
        }
        if (content != null && "LIVE_LOCATION".equals(content.messageType())) {
            String label = content.liveLocation() != null && content.liveLocation().title() != null
                    ? content.liveLocation().title().trim()
                    : "";
            return label.isBlank()
                    ? "is sharing live location"
                    : "is sharing live location: " + label;
        }
        if (content != null && "CONTACT_CARD".equals(content.messageType())) {
            return "shared a contact";
        }
        if (content != null && "SERVICE_MESSAGE".equals(content.messageType()) && content.serviceMessage() != null) {
            if (content.serviceMessage().text() != null && !content.serviceMessage().text().isBlank()) {
                return content.serviceMessage().text();
            }
            return "sent a service update";
        }
        if (event.pollId() != null) {
            return "sent a poll";
        }
        if (event.stickerId() != null) {
            return "sent a sticker";
        }
        if (!attachments.isEmpty()) {
            if (attachments.size() == 1) {
                return switch (attachments.get(0).kind()) {
                    case "VOICE" -> "sent a voice message";
                    case "AUDIO" -> "sent an audio file";
                    case "VIDEO" -> "sent a video";
                    case "VIDEO_NOTE" -> "sent a video note";
                    case "GIF" -> "sent a GIF";
                    case "IMAGE" -> "sent a photo";
                    default -> "sent an attachment";
                };
            }
            return "sent an attachment";
        }
        return "sent a message";
    }

    private String resolveSenderName(UserEntity sender) {
        if (sender == null || sender.getDisplayName() == null || sender.getDisplayName().isBlank()) {
            return "Someone";
        }
        return sender.getDisplayName().trim();
    }
}
