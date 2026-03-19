package com.alex.messenger.call;

import com.alex.messenger.auth.session.PushSessionTarget;
import com.alex.messenger.auth.session.UserSessionService;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatMemberRepository;
import com.alex.messenger.chat.ChatRepository;
import com.alex.messenger.notification.PushNotificationCommand;
import com.alex.messenger.notification.PushNotificationService;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CallNotificationService {

    private static final List<String> PUSH_ELIGIBLE_PARTICIPANT_STATES = List.of("RINGING", "INVITED");
    private static final List<String> PUSH_ELIGIBLE_SESSION_STATUSES = List.of("RINGING", "ACTIVE");

    private final PushNotificationService pushNotificationService;
    private final UserSessionService userSessionService;
    private final CallParticipantRepository callParticipantRepository;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final UserRepository userRepository;

    public void notifyStartedCall(CallSessionEntity session) {
        if (session == null
                || session.getId() == null
                || session.getChatId() == null
                || session.getMode() == null
                || !PUSH_ELIGIBLE_SESSION_STATUSES.contains(session.getStatus())) {
            return;
        }

        ChatEntity chat = chatRepository.findById(session.getChatId()).orElse(null);
        if (chat == null) {
            return;
        }

        Set<UUID> memberIds = chatMemberRepository.findAllByIdChatId(chat.getId()).stream()
                .map(member -> member.getId().getUserId())
                .collect(Collectors.toSet());
        if (memberIds.isEmpty()) {
            return;
        }

        List<CallParticipantEntity> participants = callParticipantRepository.findAllByIdCallId(session.getId());
        if (participants.isEmpty()) {
            return;
        }

        List<UUID> userIds = participants.stream()
                .map(participant -> participant.getId().getUserId())
                .filter(memberIds::contains)
                .distinct()
                .toList();
        Map<UUID, UserEntity> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        UserEntity initiator = usersById.get(session.getCreatedByUserId());

        String title = buildTitle(chat, initiator, session);
        String body = buildBody(chat, initiator, session);
        List<PushNotificationCommand> commands = new ArrayList<>();

        for (CallParticipantEntity participant : participants) {
            UUID recipientUserId = participant.getId().getUserId();
            if (recipientUserId.equals(session.getCreatedByUserId())
                    || !memberIds.contains(recipientUserId)
                    || !PUSH_ELIGIBLE_PARTICIPANT_STATES.contains(participant.getState())
                    || userSessionService.isUserOnline(recipientUserId)) {
                continue;
            }

            for (PushSessionTarget target : userSessionService.getPushTargets(recipientUserId)) {
                commands.add(new PushNotificationCommand(
                        target.provider(),
                        target.pushToken(),
                        title,
                        body,
                        Map.of(
                                "callId", session.getId().toString(),
                                "chatId", session.getChatId().toString(),
                                "mode", session.getMode(),
                                "kind", session.getKind(),
                                "status", session.getStatus()
                        )
                ));
            }
        }

        if (!commands.isEmpty()) {
            pushNotificationService.send(commands);
        }
    }

    private String buildTitle(ChatEntity chat, UserEntity initiator, CallSessionEntity session) {
        return switch (session.getMode()) {
            case "DIRECT" -> actorName(initiator);
            case "VOICE_CHAT" -> resolveChatTitle(chat, "Voice chat");
            case "LIVE_STREAM" -> resolveChatTitle(chat, "Live stream");
            case "GROUP" -> resolveChatTitle(chat, "Group call");
            default -> "Call";
        };
    }

    private String buildBody(ChatEntity chat, UserEntity initiator, CallSessionEntity session) {
        String actorName = actorName(initiator);
        String kind = describeKind(session.getKind());
        return switch (session.getMode()) {
            case "DIRECT" -> "Incoming " + kind + " call";
            case "GROUP" -> actorName + " started a group " + kind + " call in " + resolveChatTitle(chat, "your group");
            case "VOICE_CHAT" -> actorName + " started a voice chat in " + resolveChatTitle(chat, "your group");
            case "LIVE_STREAM" -> actorName + " started a livestream in " + resolveChatTitle(chat, "your channel");
            default -> actorName + " started a call";
        };
    }

    private String resolveChatTitle(ChatEntity chat, String fallback) {
        if (chat == null || chat.getTitle() == null || chat.getTitle().isBlank()) {
            return fallback;
        }
        return chat.getTitle().trim();
    }

    private String actorName(UserEntity initiator) {
        if (initiator == null || initiator.getDisplayName() == null || initiator.getDisplayName().isBlank()) {
            return "Someone";
        }
        return initiator.getDisplayName().trim();
    }

    private String describeKind(String kind) {
        String normalized = kind == null ? "VOICE" : kind.trim().toUpperCase(Locale.ROOT);
        return "VIDEO".equals(normalized) ? "video" : "voice";
    }
}
