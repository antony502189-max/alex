package com.alex.messenger.chat.channeldm;

import com.alex.messenger.chat.ChatAdminLogService;
import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatMemberEntity;
import com.alex.messenger.chat.ChatMemberId;
import com.alex.messenger.chat.ChatMemberRepository;
import com.alex.messenger.chat.ChatRepository;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.channeldm.dto.ChannelDirectMessageResponse;
import com.alex.messenger.chat.channeldm.dto.ChannelDirectMessageStateResponse;
import com.alex.messenger.chat.channeldm.dto.ChannelDirectMessageTopicResponse;
import com.alex.messenger.chat.channeldm.dto.OpenChannelDirectMessageRequest;
import com.alex.messenger.chat.dto.ChatSummaryResponse;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
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
public class ChannelDirectMessageService {

    private record ChannelAccess(
            ChatEntity channel,
            UUID ownerUserId,
            boolean member,
            boolean moderator
    ) {
    }

    private final ChannelDirectMessageChatRepository channelDirectMessageChatRepository;
    private final ChannelDirectMessageTopicRepository channelDirectMessageTopicRepository;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatService chatService;
    private final ChatAdminLogService chatAdminLogService;
    private final UserRepository userRepository;

    @Transactional
    public ChannelDirectMessageStateResponse enableDirectMessages(UUID requesterId, UUID channelChatId) {
        ChannelAccess access = resolveChannelAccess(requesterId, channelChatId, false);
        ensureModerator(access);
        if (!Boolean.TRUE.equals(access.channel().getDirectMessagesEnabled())) {
            access.channel().setDirectMessagesEnabled(true);
            chatRepository.save(access.channel());
            chatAdminLogService.log(
                    channelChatId,
                    requesterId,
                    null,
                    "CHANNEL_DIRECT_MESSAGES_ENABLED",
                    "Enabled direct messages for the channel",
                    null,
                    null
            );
        }
        return new ChannelDirectMessageStateResponse(
                channelChatId,
                Boolean.TRUE.equals(access.channel().getDirectMessagesEnabled()),
                channelDirectMessageChatRepository.countByChannelChatId(channelChatId)
        );
    }

    @Transactional
    public ChannelDirectMessageResponse openDirectMessage(
            UUID requesterId,
            UUID channelChatId,
            OpenChannelDirectMessageRequest request
    ) {
        ChannelAccess access = resolveChannelAccess(requesterId, channelChatId, true);
        UUID participantUserId = resolveParticipantUserId(access, requesterId, request);
        UserEntity participant = requireUser(participantUserId);

        ChannelDirectMessageChatEntity existing = channelDirectMessageChatRepository
                .findByChannelChatIdAndParticipantUserId(channelChatId, participantUserId)
                .orElse(null);
        if (existing != null) {
            ensureModeratorMembershipIfNeeded(access, requesterId, existing.getDirectChatId());
            ChannelDirectMessageTopicEntity topic = channelDirectMessageTopicRepository
                    .findByDirectChatId(existing.getDirectChatId())
                    .map(saved -> syncTopicSnapshot(saved, chatService.getChat(existing.getDirectChatId()), participant))
                    .orElseGet(() -> createTopic(access.channel(), existing, participant));
            return toResponse(
                    requesterId,
                    access.channel(),
                    existing,
                    topic,
                    participant,
                    chatService.getChatSummary(requesterId, existing.getDirectChatId())
            );
        }

        ChatEntity directChat = createManagedChat(access.channel(), access.ownerUserId(), participant);
        ensureModeratorMembershipIfNeeded(access, requesterId, directChat.getId());

        ChannelDirectMessageChatEntity chatLink = new ChannelDirectMessageChatEntity();
        chatLink.setChannelChatId(channelChatId);
        chatLink.setDirectChatId(directChat.getId());
        chatLink.setParticipantUserId(participantUserId);
        chatLink.setCreatedByUserId(requesterId);
        chatLink.setStatus("OPEN");
        ChannelDirectMessageChatEntity savedChatLink = channelDirectMessageChatRepository.save(chatLink);

        ChannelDirectMessageTopicEntity topic = createTopic(access.channel(), savedChatLink, participant);
        chatAdminLogService.log(
                channelChatId,
                requesterId,
                participantUserId,
                "CHANNEL_DIRECT_MESSAGE_OPENED",
                "Opened a channel direct message conversation",
                null,
                null
        );
        return toResponse(
                requesterId,
                access.channel(),
                savedChatLink,
                topic,
                participant,
                chatService.getChatSummary(requesterId, directChat.getId())
        );
    }

    @Transactional
    public List<ChannelDirectMessageResponse> listDirectMessages(UUID requesterId, UUID channelChatId, int limit) {
        int normalizedLimit = requireLimit(limit, 100);
        ChannelAccess access = resolveChannelAccess(requesterId, channelChatId, true);
        UUID participantFilter = access.moderator() ? null : requesterId;

        List<ChannelDirectMessageChatEntity> links = channelDirectMessageChatRepository.findVisible(
                channelChatId,
                participantFilter,
                PageRequest.of(0, normalizedLimit)
        );
        if (links.isEmpty()) {
            return List.of();
        }

        Map<UUID, UserEntity> participantsById = loadParticipantsById(links);
        Map<UUID, ChatEntity> directChatsById = loadDirectChats(links);
        Map<UUID, ChannelDirectMessageTopicEntity> topicsByChatId = loadTopicsByDirectChatId(links);

        return links.stream()
                .sorted(Comparator.comparing(
                        (ChannelDirectMessageChatEntity link) -> {
                            ChatEntity directChat = directChatsById.get(link.getDirectChatId());
                            return directChat != null && directChat.getLastMessageAt() != null
                                    ? directChat.getLastMessageAt()
                                    : link.getCreatedAt();
                        },
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .map(link -> {
                    ensureModeratorMembershipIfNeeded(access, requesterId, link.getDirectChatId());
                    ChatEntity directChat = directChatsById.get(link.getDirectChatId());
                    UserEntity participant = participantsById.get(link.getParticipantUserId());
                    ChannelDirectMessageTopicEntity topic = topicsByChatId.get(link.getDirectChatId());
                    if (topic == null) {
                        topic = createTopic(access.channel(), link, participant);
                    } else {
                        topic = syncTopicSnapshot(topic, directChat, participant);
                    }
                    ChatSummaryResponse summary = chatService.getChatSummary(requesterId, link.getDirectChatId());
                    return toResponse(requesterId, access.channel(), link, topic, participant, summary);
                })
                .toList();
    }

    @Transactional
    public List<ChannelDirectMessageTopicResponse> listDirectMessageTopics(
            UUID requesterId,
            UUID channelChatId,
            int limit
    ) {
        int normalizedLimit = requireLimit(limit, 100);
        ChannelAccess access = resolveChannelAccess(requesterId, channelChatId, true);
        UUID participantFilter = access.moderator() ? null : requesterId;

        List<ChannelDirectMessageTopicEntity> topics = channelDirectMessageTopicRepository.findVisible(
                channelChatId,
                participantFilter,
                PageRequest.of(0, normalizedLimit)
        );
        if (topics.isEmpty()) {
            return List.of();
        }

        Map<UUID, UserEntity> participantsById = userRepository.findAllById(
                topics.stream().map(ChannelDirectMessageTopicEntity::getParticipantUserId).distinct().toList()
        ).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        Map<UUID, ChatEntity> directChatsById = chatRepository.findAllById(
                topics.stream().map(ChannelDirectMessageTopicEntity::getDirectChatId).distinct().toList()
        ).stream().collect(Collectors.toMap(ChatEntity::getId, Function.identity()));

        return topics.stream()
                .map(topic -> syncTopicSnapshot(
                        topic,
                        directChatsById.get(topic.getDirectChatId()),
                        participantsById.get(topic.getParticipantUserId())
                ))
                .map(topic -> toTopicResponse(topic, participantsById.get(topic.getParticipantUserId())))
                .toList();
    }

    private ChannelAccess resolveChannelAccess(UUID requesterId, UUID channelChatId, boolean requireEnabledForNonModerators) {
        ChatEntity channel = chatService.getChat(channelChatId);
        if (!"CHANNEL".equals(channel.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Channel direct messages are available only for channels");
        }

        UUID ownerUserId = chatMemberRepository.findByIdChatIdAndRole(channelChatId, "OWNER")
                .map(member -> member.getId().getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Channel owner not found"));

        boolean member = chatMemberRepository.existsByIdChatIdAndIdUserId(channelChatId, requesterId);
        boolean moderator = member && chatService.hasMessageModerationPermission(requesterId, channelChatId);

        if (!moderator) {
            if (requireEnabledForNonModerators && !Boolean.TRUE.equals(channel.getDirectMessagesEnabled())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Channel direct messages are disabled");
            }
            if (!member && (channel.getPublicUsername() == null || channel.getPublicUsername().isBlank())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Private channel direct messages require channel membership");
            }
        }

        return new ChannelAccess(channel, ownerUserId, member, moderator);
    }

    private void ensureModerator(ChannelAccess access) {
        if (!access.moderator()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only channel owners or admins with message moderation rights can manage channel direct messages"
            );
        }
    }

    private UUID resolveParticipantUserId(
            ChannelAccess access,
            UUID requesterId,
            OpenChannelDirectMessageRequest request
    ) {
        UUID requestedParticipantUserId = request != null ? request.participantUserId() : null;
        if (!access.moderator()) {
            return requesterId;
        }

        UUID participantUserId = requestedParticipantUserId != null ? requestedParticipantUserId : requesterId;
        if (participantUserId.equals(access.ownerUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Channel owner cannot open a direct message with themselves");
        }
        return participantUserId;
    }

    private ChatEntity createManagedChat(ChatEntity channel, UUID ownerUserId, UserEntity participant) {
        ChatEntity directChat = new ChatEntity();
        directChat.setChatType("CHANNEL_DIRECT");
        directChat.setTitle(resolveManagedChatTitle(channel));
        directChat.setAbout(resolveManagedChatAbout(channel, participant));
        directChat.setCreatedBy(ownerUserId);
        directChat.setDirectMessagesEnabled(false);
        directChat.setPhotoStorageProvider(channel.getPhotoStorageProvider());
        directChat.setPhotoBucketName(channel.getPhotoBucketName());
        directChat.setPhotoObjectKey(channel.getPhotoObjectKey());
        directChat.setPhotoContentType(channel.getPhotoContentType());
        directChat.setPhotoUpdatedAt(channel.getPhotoUpdatedAt());
        ChatEntity savedDirectChat = chatRepository.save(directChat);

        List<ChatMemberEntity> members = new ArrayList<>();
        members.add(newManagedMember(savedDirectChat.getId(), ownerUserId, "OWNER"));
        members.add(newManagedMember(savedDirectChat.getId(), participant.getId(), "MEMBER"));
        chatMemberRepository.saveAll(members);
        return savedDirectChat;
    }

    private ChatMemberEntity newManagedMember(UUID chatId, UUID userId, String role) {
        ChatMemberEntity member = new ChatMemberEntity();
        member.setId(new ChatMemberId(chatId, userId));
        member.setRole(role);
        member.setArchived(false);
        member.setUnreadCount(0);
        member.setMentionCount(0);
        member.setReplyCount(0);
        member.setCanSendMessages(true);
        member.setCanManageMembers("OWNER".equals(role) || "ADMIN".equals(role));
        member.setCanManageInviteLinks("OWNER".equals(role) || "ADMIN".equals(role));
        member.setCanManageMessages("OWNER".equals(role) || "ADMIN".equals(role));
        member.setCanPinMessages("OWNER".equals(role) || "ADMIN".equals(role));
        member.setCanApproveJoinRequests(false);
        member.setCanPostMessages(true);
        member.setAnonymousAdmin(false);
        member.setRestrictedUntil(null);
        member.setRestrictionReason(null);
        member.setRestrictedByUserId(null);
        return member;
    }

    private void ensureModeratorMembershipIfNeeded(ChannelAccess access, UUID requesterId, UUID directChatId) {
        if (!access.moderator()) {
            return;
        }
        ChatMemberId membershipId = new ChatMemberId(directChatId, requesterId);
        if (chatMemberRepository.existsById(membershipId)) {
            return;
        }
        if (requesterId.equals(access.ownerUserId())) {
            return;
        }
        chatMemberRepository.save(newManagedMember(directChatId, requesterId, "ADMIN"));
    }

    private ChannelDirectMessageTopicEntity createTopic(
            ChatEntity channel,
            ChannelDirectMessageChatEntity chatLink,
            UserEntity participant
    ) {
        ChannelDirectMessageTopicEntity topic = new ChannelDirectMessageTopicEntity();
        topic.setChannelChatId(channel.getId());
        topic.setDirectChatId(chatLink.getDirectChatId());
        topic.setParticipantUserId(chatLink.getParticipantUserId());
        topic.setCreatedByUserId(chatLink.getCreatedByUserId());
        topic.setTitle(resolveTopicTitle(participant));
        topic.setLastMessageAt(chatRepository.findById(chatLink.getDirectChatId()).map(ChatEntity::getLastMessageAt).orElse(null));
        return channelDirectMessageTopicRepository.save(topic);
    }

    private ChannelDirectMessageTopicEntity syncTopicSnapshot(
            ChannelDirectMessageTopicEntity topic,
            ChatEntity directChat,
            UserEntity participant
    ) {
        boolean changed = false;
        String expectedTitle = resolveTopicTitle(participant);
        if (!Objects.equals(topic.getTitle(), expectedTitle)) {
            topic.setTitle(expectedTitle);
            changed = true;
        }
        Instant expectedLastMessageAt = directChat != null ? directChat.getLastMessageAt() : null;
        if (!Objects.equals(topic.getLastMessageAt(), expectedLastMessageAt)) {
            topic.setLastMessageAt(expectedLastMessageAt);
            changed = true;
        }
        if (changed) {
            return channelDirectMessageTopicRepository.save(topic);
        }
        return topic;
    }

    private Map<UUID, UserEntity> loadParticipantsById(Collection<ChannelDirectMessageChatEntity> links) {
        if (links.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(
                links.stream().map(ChannelDirectMessageChatEntity::getParticipantUserId).distinct().toList()
        ).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    }

    private Map<UUID, ChatEntity> loadDirectChats(Collection<ChannelDirectMessageChatEntity> links) {
        if (links.isEmpty()) {
            return Map.of();
        }
        return chatRepository.findAllById(
                links.stream().map(ChannelDirectMessageChatEntity::getDirectChatId).distinct().toList()
        ).stream().collect(Collectors.toMap(ChatEntity::getId, Function.identity()));
    }

    private Map<UUID, ChannelDirectMessageTopicEntity> loadTopicsByDirectChatId(
            Collection<ChannelDirectMessageChatEntity> links
    ) {
        if (links.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ChannelDirectMessageTopicEntity> topicsByDirectChatId = new LinkedHashMap<>();
        for (ChannelDirectMessageTopicEntity topic : channelDirectMessageTopicRepository.findVisible(
                links.iterator().next().getChannelChatId(),
                null,
                PageRequest.of(0, Math.max(links.size(), 1))
        )) {
            topicsByDirectChatId.put(topic.getDirectChatId(), topic);
        }
        return topicsByDirectChatId;
    }

    private ChannelDirectMessageResponse toResponse(
            UUID requesterId,
            ChatEntity channel,
            ChannelDirectMessageChatEntity chatLink,
            ChannelDirectMessageTopicEntity topic,
            UserEntity participant,
            ChatSummaryResponse chatSummary
    ) {
        return new ChannelDirectMessageResponse(
                chatLink.getId(),
                channel.getId(),
                chatLink.getDirectChatId(),
                topic != null ? topic.getId() : null,
                chatLink.getParticipantUserId(),
                participant != null ? participant.getDisplayName() : "Unknown",
                participant != null ? participant.getUsername() : null,
                chatLink.getStatus(),
                topic != null ? topic.getTitle() : resolveTopicTitle(participant),
                chatLink.getCreatedAt(),
                chatLink.getUpdatedAt(),
                topic != null ? topic.getLastMessageAt() : null,
                chatSummary
        );
    }

    private ChannelDirectMessageTopicResponse toTopicResponse(
            ChannelDirectMessageTopicEntity topic,
            UserEntity participant
    ) {
        return new ChannelDirectMessageTopicResponse(
                topic.getId(),
                topic.getChannelChatId(),
                topic.getDirectChatId(),
                topic.getParticipantUserId(),
                participant != null ? participant.getDisplayName() : "Unknown",
                participant != null ? participant.getUsername() : null,
                topic.getTitle(),
                topic.getCreatedAt(),
                topic.getUpdatedAt(),
                topic.getLastMessageAt()
        );
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String resolveManagedChatTitle(ChatEntity channel) {
        String title = channel.getTitle() != null ? channel.getTitle().trim() : "";
        if (!title.isBlank()) {
            return title;
        }
        if (channel.getPublicUsername() != null && !channel.getPublicUsername().isBlank()) {
            return "@" + channel.getPublicUsername().trim();
        }
        return "Channel messages";
    }

    private String resolveManagedChatAbout(ChatEntity channel, UserEntity participant) {
        String participantLabel = participant.getDisplayName() != null && !participant.getDisplayName().isBlank()
                ? participant.getDisplayName().trim()
                : participant.getId().toString();
        return truncate("Channel direct messages with " + participantLabel + " for " + resolveManagedChatTitle(channel), 500);
    }

    private String resolveTopicTitle(UserEntity participant) {
        if (participant == null) {
            return "Unknown";
        }
        if (participant.getDisplayName() != null && !participant.getDisplayName().isBlank()) {
            return participant.getDisplayName().trim();
        }
        if (participant.getUsername() != null && !participant.getUsername().isBlank()) {
            return "@" + participant.getUsername().trim();
        }
        return participant.getId().toString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private int requireLimit(int limit, int max) {
        if (limit < 1 || limit > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and " + max);
        }
        return limit;
    }
}
