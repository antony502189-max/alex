package com.alex.messenger.chat;

import com.alex.messenger.attachment.AttachmentEntity;
import com.alex.messenger.attachment.AttachmentRepository;
import com.alex.messenger.chat.draft.ChatDraftEntity;
import com.alex.messenger.chat.draft.ChatDraftId;
import com.alex.messenger.chat.draft.ChatDraftRepository;
import com.alex.messenger.chat.dto.AddMembersRequest;
import com.alex.messenger.chat.dto.ChatAdminLogResponse;
import com.alex.messenger.chat.dto.ChatAnalyticsResponse;
import com.alex.messenger.chat.dto.ChatInviteLinkResponse;
import com.alex.messenger.chat.dto.ChatBanResponse;
import com.alex.messenger.chat.dto.ChatJoinRequestResponse;
import com.alex.messenger.chat.dto.ChatLastMessagePreviewResponse;
import com.alex.messenger.chat.dto.ChatMemberResponse;
import com.alex.messenger.chat.dto.ChatReadEventResponse;
import com.alex.messenger.chat.dto.ChatSummaryResponse;
import com.alex.messenger.chat.dto.CreateChannelRequest;
import com.alex.messenger.chat.dto.CreateGroupChatRequest;
import com.alex.messenger.chat.dto.CreateInviteLinkRequest;
import com.alex.messenger.chat.dto.JoinChatResultResponse;
import com.alex.messenger.chat.dto.MemberMutationResponse;
import com.alex.messenger.chat.dto.PinMessageEventResponse;
import com.alex.messenger.chat.dto.PublicChatDiscoveryResponse;
import com.alex.messenger.chat.dto.TypingEventResponse;
import com.alex.messenger.chat.dto.UpdateChatPublicUsernameRequest;
import com.alex.messenger.chat.dto.UpdateChatBanRequest;
import com.alex.messenger.chat.dto.UpdateChatProfileRequest;
import com.alex.messenger.chat.dto.UpdateMemberPermissionsRequest;
import com.alex.messenger.chat.dto.UpdateMemberRestrictionRequest;
import com.alex.messenger.chat.dto.UpdateMemberRoleRequest;
import com.alex.messenger.chat.forum.ForumTopicEntity;
import com.alex.messenger.chat.forum.ForumTopicRepository;
import com.alex.messenger.chat.invite.ChatInviteLinkEntity;
import com.alex.messenger.chat.invite.ChatInviteLinkRepository;
import com.alex.messenger.crypto.ChatEncryptionService;
import com.alex.messenger.media.PhotoAccess;
import com.alex.messenger.media.ProfilePhotoService;
import com.alex.messenger.media.StoredPhotoReference;
import com.alex.messenger.message.MessageContentCodec;
import com.alex.messenger.message.MessageEntity;
import com.alex.messenger.message.MessageLookupEntity;
import com.alex.messenger.message.MessageLookupRepository;
import com.alex.messenger.message.MessageReactionRepository;
import com.alex.messenger.message.MessageRepository;
import com.alex.messenger.message.MessageTextContent;
import com.alex.messenger.user.BlockedUserRepository;
import com.alex.messenger.user.UserEntity;
import com.alex.messenger.user.UserPresenceService;
import com.alex.messenger.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final Pattern USERNAME_MENTION_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]{5,64})");

    public record MessageAuthorView(
            UUID senderId,
            String displayName,
            String photoUrl,
            Instant photoAccessExpiresAt,
            boolean anonymous
    ) {
    }

    public record ChatListSlice(
            List<ChatSummaryResponse> chats,
            String nextCursor,
            boolean hasMore
    ) {
    }

    private record UnreadTailCounters(
            int unreadCount,
            int mentionCount,
            int replyCount
    ) {
    }

    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatBanRepository chatBanRepository;
    private final ChatJoinRequestRepository chatJoinRequestRepository;
    private final ChatPinEventRepository chatPinEventRepository;
    private final ChatDraftRepository chatDraftRepository;
    private final ChatInviteLinkRepository chatInviteLinkRepository;
    private final ForumTopicRepository forumTopicRepository;
    private final ChatAdminLogService chatAdminLogService;
    private final MessageRepository messageRepository;
    private final MessageLookupRepository messageLookupRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final AttachmentRepository attachmentRepository;
    private final ChatEncryptionService chatEncryptionService;
    private final MessageContentCodec messageContentCodec;
    private final UserRepository userRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final ProfilePhotoService profilePhotoService;
    private final UserPresenceService userPresenceService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public List<ChatSummaryResponse> listChats(UUID userId, boolean archived) {
        List<ChatMemberEntity> memberships = chatMemberRepository.findMembershipsOrderedForUser(userId, archived);
        List<UUID> chatIds = memberships.stream().map(member -> member.getId().getChatId()).toList();
        Map<UUID, ChatEntity> chatsById = chatRepository.findAllById(
                chatIds
        ).stream().collect(Collectors.toMap(ChatEntity::getId, Function.identity()));
        Map<UUID, ChatDraftEntity> draftsByChatId = chatIds.isEmpty()
                ? Map.of()
                : chatDraftRepository.findAllByIdUserIdAndIdChatIdIn(userId, chatIds).stream()
                        .collect(Collectors.toMap(draft -> draft.getId().getChatId(), Function.identity()));

        return memberships.stream()
                .map(membership -> {
                    ChatEntity chat = chatsById.get(membership.getId().getChatId());
                    if (chat == null) {
                        return null;
                    }
                    return buildChatSummary(chat, userId, membership, draftsByChatId.get(chat.getId()));
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatListSlice listChatsPage(UUID userId, boolean archived, String cursor, Integer limit) {
        int normalizedLimit = Math.min(Math.max(limit != null ? limit : 50, 1), 100);
        int offset = decodeChatCursor(cursor);
        List<ChatSummaryResponse> chats = listChats(userId, archived);
        if (offset > chats.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat cursor is out of range");
        }

        int nextOffset = Math.min(offset + normalizedLimit, chats.size());
        boolean hasMore = nextOffset < chats.size();
        return new ChatListSlice(
                chats.subList(offset, nextOffset),
                hasMore ? encodeChatCursor(nextOffset) : null,
                hasMore
        );
    }

    @Transactional(readOnly = true)
    public List<ChatSummaryResponse> listAllChats(UUID userId) {
        Map<UUID, ChatSummaryResponse> chatsById = new LinkedHashMap<>();
        for (ChatSummaryResponse chat : listChats(userId, false)) {
            chatsById.put(chat.chatId(), chat);
        }
        for (ChatSummaryResponse chat : listChats(userId, true)) {
            chatsById.putIfAbsent(chat.chatId(), chat);
        }
        return chatsById.values().stream()
                .sorted((left, right) -> {
                    if (left.lastMessageAt() == null && right.lastMessageAt() == null) {
                        return 0;
                    }
                    if (left.lastMessageAt() == null) {
                        return 1;
                    }
                    if (right.lastMessageAt() == null) {
                        return -1;
                    }
                    return right.lastMessageAt().compareTo(left.lastMessageAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatSummaryResponse> searchChats(UUID requesterId, String query, int limit) {
        String normalizedQuery = query.trim().toLowerCase();
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        int normalizedLimit = Math.min(Math.max(limit, 1), 50);
        return listAllChats(requesterId).stream()
                .filter(chat -> matchesChatQuery(chat, normalizedQuery))
                .limit(normalizedLimit)
                .toList();
    }

    @Transactional
    public ChatSummaryResponse createDirectChat(UUID requesterId, UUID peerId) {
        ChatEntity chat = getOrCreateDirectChat(requesterId, peerId);
        return buildChatSummary(chat, requesterId, getMembership(chat.getId(), requesterId), getDraft(chat.getId(), requesterId));
    }

    @Transactional
    public ChatSummaryResponse createSavedMessagesChat(UUID requesterId) {
        ChatEntity chat = chatRepository.findByChatTypeAndCreatedBy("SAVED", requesterId)
                .orElseGet(() -> createSavedMessages(requesterId));
        return buildChatSummary(chat, requesterId, getMembership(chat.getId(), requesterId), getDraft(chat.getId(), requesterId));
    }

    @Transactional
    public ChatSummaryResponse createGroupChat(UUID requesterId, CreateGroupChatRequest request) {
        Set<UUID> memberIds = new LinkedHashSet<>(request.memberIds());
        memberIds.remove(requesterId);
        if (memberIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group must include at least one more user");
        }

        ensureUsersExist(memberIds);
        ChatEntity savedChat = createMultiMemberChat(
                "GROUP",
                requesterId,
                request.title().trim(),
                request.about(),
                request.autoDeleteSeconds(),
                Boolean.TRUE.equals(request.forumEnabled()),
                Boolean.TRUE.equals(request.joinRequiresApproval()),
                memberIds
        );
        return buildChatSummary(savedChat, requesterId, getMembership(savedChat.getId(), requesterId), getDraft(savedChat.getId(), requesterId));
    }

    @Transactional
    public ChatSummaryResponse createChannel(UUID requesterId, CreateChannelRequest request) {
        Set<UUID> subscriberIds = new LinkedHashSet<>(request.subscriberIds() != null ? request.subscriberIds() : List.of());
        subscriberIds.remove(requesterId);
        ensureUsersExist(subscriberIds);

        ChatEntity savedChat = createMultiMemberChat(
                "CHANNEL",
                requesterId,
                request.title().trim(),
                request.about(),
                request.autoDeleteSeconds(),
                false,
                Boolean.TRUE.equals(request.joinRequiresApproval()),
                subscriberIds
        );
        return buildChatSummary(savedChat, requesterId, getMembership(savedChat.getId(), requesterId), getDraft(savedChat.getId(), requesterId));
    }

    @Transactional
    public ChatSummaryResponse updateChatProfile(
            UUID requesterId,
            UUID chatId,
            UpdateChatProfileRequest request
    ) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanManageInviteLinks(chat, requesterId);

        if (request.title() != null && !request.title().isBlank()) {
            chat.setTitle(request.title().trim());
        }
        chat.setAbout(normalizeAbout(request.about()));
        chat.setAutoDeleteSeconds(normalizeAutoDeleteSeconds(request.autoDeleteSeconds()));
        chat.setSlowModeSeconds(normalizeSlowModeSeconds(request.slowModeSeconds(), chat.getChatType()));
        if (request.joinRequiresApproval() != null) {
            chat.setJoinRequiresApproval(request.joinRequiresApproval());
        }
        if (request.commentsEnabled() != null) {
            if (!"CHANNEL".equals(chat.getChatType())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Comment controls are available only for channels"
                );
            }
            chat.setCommentsEnabled(request.commentsEnabled());
        }
        if (request.reactionsEnabled() != null) {
            if (!List.of("GROUP", "CHANNEL").contains(chat.getChatType())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Reaction controls are available only for groups and channels"
                );
            }
            chat.setReactionsEnabled(request.reactionsEnabled());
        }
        if (request.crossPostingEnabled() != null) {
            if (!"CHANNEL".equals(chat.getChatType())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cross-posting controls are available only for channels"
                );
            }
            chat.setCrossPostingEnabled(request.crossPostingEnabled());
        }
        if (request.forumEnabled() != null) {
            if (!"GROUP".equals(chat.getChatType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forum mode is only available for groups");
            }
            chat.setForumEnabled(request.forumEnabled());
            if (Boolean.TRUE.equals(request.forumEnabled())) {
                ensureGeneralForumTopic(chat, requesterId);
            }
        }
        if (request.linkedDiscussionChatId() != null) {
            if (!"CHANNEL".equals(chat.getChatType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discussion groups are only available for channels");
            }
            ChatEntity discussionChat = getOwnedChat(requesterId, request.linkedDiscussionChatId());
            if (chat.getId().equals(discussionChat.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Channel cannot be linked to itself");
            }
            if (!"GROUP".equals(discussionChat.getChatType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discussion chat must be a group");
            }
            if (Boolean.TRUE.equals(discussionChat.getForumEnabled())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Forum-enabled groups cannot be linked as discussion groups"
                );
            }
            ensureCanManageInviteLinks(discussionChat, requesterId);
            chat.setLinkedDiscussionChatId(discussionChat.getId());
        }

        ChatEntity saved = chatRepository.save(chat);
        return buildChatSummary(saved, requesterId, getMembership(chatId, requesterId), getDraft(chatId, requesterId));
    }

    @Transactional
    public ChatSummaryResponse updateChatPhoto(UUID requesterId, UUID chatId, MultipartFile file) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureSupportsCustomPhoto(chat);
        ensureCanManageInviteLinks(chat, requesterId);

        StoredPhotoReference photo = profilePhotoService.uploadChatPhoto(chatId, file);
        String previousStorageProvider = chat.getPhotoStorageProvider();
        String previousBucketName = chat.getPhotoBucketName();
        String previousObjectKey = chat.getPhotoObjectKey();

        chat.setPhotoStorageProvider(photo.storageProvider());
        chat.setPhotoBucketName(photo.bucketName());
        chat.setPhotoObjectKey(photo.objectKey());
        chat.setPhotoContentType(photo.contentType());
        chat.setPhotoUpdatedAt(Instant.now());

        ChatSummaryResponse response = buildChatSummary(
                chatRepository.save(chat),
                requesterId,
                getMembership(chatId, requesterId),
                getDraft(chatId, requesterId)
        );
        profilePhotoService.deletePhoto(previousStorageProvider, previousBucketName, previousObjectKey);
        return response;
    }

    @Transactional
    public ChatSummaryResponse removeChatPhoto(UUID requesterId, UUID chatId) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureSupportsCustomPhoto(chat);
        ensureCanManageInviteLinks(chat, requesterId);

        String previousStorageProvider = chat.getPhotoStorageProvider();
        String previousBucketName = chat.getPhotoBucketName();
        String previousObjectKey = chat.getPhotoObjectKey();

        chat.setPhotoStorageProvider(null);
        chat.setPhotoBucketName(null);
        chat.setPhotoObjectKey(null);
        chat.setPhotoContentType(null);
        chat.setPhotoUpdatedAt(null);

        ChatSummaryResponse response = buildChatSummary(
                chatRepository.save(chat),
                requesterId,
                getMembership(chatId, requesterId),
                getDraft(chatId, requesterId)
        );
        profilePhotoService.deletePhoto(previousStorageProvider, previousBucketName, previousObjectKey);
        return response;
    }

    @Transactional
    public List<ChatMemberResponse> addMembers(UUID requesterId, UUID chatId, AddMembersRequest request) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanManageMembers(chat, requesterId);

        Set<UUID> userIds = new LinkedHashSet<>(request.userIds());
        userIds.remove(requesterId);
        ensureUsersExist(userIds);

        for (UUID userId : userIds) {
            assertNotBanned(chatId, userId);
            if (chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, userId)) {
                markJoinRequestApprovedIfPresent(chatId, userId, requesterId);
                continue;
            }
            chatMemberRepository.save(newMember(chat, userId, "MEMBER"));
            markJoinRequestApprovedIfPresent(chatId, userId, requesterId);
            chatAdminLogService.log(chatId, requesterId, userId, "MEMBER_ADDED", "Added member to chat", null, null);
        }

        return getMembers(requesterId, chatId);
    }

    @Transactional
    public MemberMutationResponse updateMemberRole(
            UUID requesterId,
            UUID chatId,
            UUID userId,
            UpdateMemberRoleRequest request
    ) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanManageMembers(chat, requesterId);

        ChatMemberEntity target = getMembership(chatId, userId);
        if ("OWNER".equals(target.getRole())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Owner role cannot be changed");
        }

        String normalizedRole = request.role().trim().toUpperCase();
        if (!List.of("ADMIN", "MEMBER").contains(normalizedRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported role");
        }

        applyRoleDefaults(target, chat.getChatType(), normalizedRole);
        chatMemberRepository.save(target);
        chatAdminLogService.log(
                chatId,
                requesterId,
                userId,
                "MEMBER_ROLE_UPDATED",
                "Updated member role to %s".formatted(normalizedRole),
                null,
                null
        );
        return new MemberMutationResponse(chatId, userId, normalizedRole);
    }

    @Transactional
    public ChatMemberResponse updateMemberPermissions(
            UUID requesterId,
            UUID chatId,
            UUID userId,
            UpdateMemberPermissionsRequest request
    ) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanManageMembers(chat, requesterId);

        if (requesterId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot update your own permissions");
        }

        ChatMemberEntity target = getMembership(chatId, userId);
        if ("OWNER".equals(target.getRole())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Owner permissions cannot be changed");
        }

        if (!"ADMIN".equals(target.getRole())) {
            if (Boolean.TRUE.equals(request.canManageMembers())
                    || Boolean.TRUE.equals(request.canManageInviteLinks())
                    || Boolean.TRUE.equals(request.canManageMessages())
                    || Boolean.TRUE.equals(request.canPinMessages())
                    || Boolean.TRUE.equals(request.canApproveJoinRequests())
                    || Boolean.TRUE.equals(request.anonymousAdmin())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Administrative permissions can be assigned only to admins"
                );
            }
            target.setCanManageMembers(false);
            target.setCanManageInviteLinks(false);
            target.setCanManageMessages(false);
            target.setCanPinMessages(false);
            target.setCanApproveJoinRequests(false);
            target.setAnonymousAdmin(false);
        } else {
            if (request.canManageMembers() != null) {
                target.setCanManageMembers(request.canManageMembers());
            }
            if (request.canManageInviteLinks() != null) {
                target.setCanManageInviteLinks(request.canManageInviteLinks());
            }
            if (request.canManageMessages() != null) {
                target.setCanManageMessages(request.canManageMessages());
            }
            if (request.canPinMessages() != null) {
                target.setCanPinMessages(request.canPinMessages());
            }
            if (request.canApproveJoinRequests() != null) {
                target.setCanApproveJoinRequests(request.canApproveJoinRequests());
            }
            if (request.anonymousAdmin() != null) {
                target.setAnonymousAdmin(request.anonymousAdmin());
            }
        }

        if (request.canPostMessages() != null) {
            if (!"CHANNEL".equals(chat.getChatType())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Posting rights can be customized only for channels"
                );
            }
            target.setCanPostMessages(request.canPostMessages());
        }

        ChatMemberEntity saved = chatMemberRepository.save(target);
        chatAdminLogService.log(
                chatId,
                requesterId,
                userId,
                "MEMBER_PERMISSIONS_UPDATED",
                "Updated member permissions",
                null,
                null
        );
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toChatMemberResponse(saved, user);
    }

    @Transactional
    public MemberMutationResponse removeMember(UUID requesterId, UUID chatId, UUID userId) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanManageMembers(chat, requesterId);

        ChatMemberEntity target = getMembership(chatId, userId);
        if ("OWNER".equals(target.getRole())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Owner cannot be removed");
        }

        chatMemberRepository.delete(target);
        chatAdminLogService.log(chatId, requesterId, userId, "MEMBER_REMOVED", "Removed member from chat", null, null);
        return new MemberMutationResponse(chatId, userId, "REMOVED");
    }

    @Transactional
    public ChatSummaryResponse archiveChat(UUID requesterId, UUID chatId, boolean archived) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ChatMemberEntity membership = getMembership(chatId, requesterId);
        membership.setArchived(archived);
        chatMemberRepository.save(membership);
        return buildChatSummary(chat, requesterId, membership, getDraft(chatId, requesterId));
    }

    @Transactional
    public ChatSummaryResponse muteChat(UUID requesterId, UUID chatId, Instant mutedUntil) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ChatMemberEntity membership = getMembership(chatId, requesterId);
        membership.setMutedUntil(mutedUntil);
        chatMemberRepository.save(membership);
        return buildChatSummary(chat, requesterId, membership, getDraft(chatId, requesterId));
    }

    @Transactional
    public ChatSummaryResponse saveDraft(UUID requesterId, UUID chatId, String text) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        String normalizedText = text != null ? text.trim() : "";
        if (normalizedText.isBlank()) {
            return clearDraft(requesterId, chatId);
        }

        ChatDraftEntity draft = getDraft(chatId, requesterId);
        if (draft == null) {
            draft = new ChatDraftEntity();
            draft.setId(new ChatDraftId(requesterId, chatId));
        }
        draft.setDraftText(normalizedText);
        chatDraftRepository.save(draft);
        return buildChatSummary(chat, requesterId, getMembership(chatId, requesterId), draft);
    }

    @Transactional
    public ChatSummaryResponse clearDraft(UUID requesterId, UUID chatId) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ChatDraftId draftId = new ChatDraftId(requesterId, chatId);
        chatDraftRepository.findById(draftId).ifPresent(chatDraftRepository::delete);
        return buildChatSummary(chat, requesterId, getMembership(chatId, requesterId), null);
    }

    @Transactional(readOnly = true)
    public List<ChatInviteLinkResponse> getInviteLinks(UUID requesterId, UUID chatId) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanManageInviteLinks(chat, requesterId);
        return chatInviteLinkRepository.findAllByChatIdOrderByCreatedAtDesc(chatId).stream()
                .map(this::toInviteLinkResponse)
                .toList();
    }

    @Transactional
    public ChatInviteLinkResponse createInviteLink(UUID requesterId, UUID chatId, CreateInviteLinkRequest request) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanManageInviteLinks(chat, requesterId);

        Instant expiresAt = request.expiresAt();
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invite link expiration must be in the future");
        }

        ChatInviteLinkEntity inviteLink = new ChatInviteLinkEntity();
        inviteLink.setChatId(chatId);
        inviteLink.setCreatedBy(requesterId);
        inviteLink.setLabel(request.label() != null && !request.label().isBlank() ? request.label().trim() : null);
        inviteLink.setToken(generateInviteToken());
        inviteLink.setUsageLimit(request.usageLimit());
        inviteLink.setExpiresAt(expiresAt);
        ChatInviteLinkEntity savedInviteLink = chatInviteLinkRepository.save(inviteLink);
        chatAdminLogService.log(
                chatId,
                requesterId,
                null,
                "INVITE_LINK_CREATED",
                "Created invite link",
                null,
                savedInviteLink.getId()
        );
        return toInviteLinkResponse(savedInviteLink);
    }

    @Transactional
    public ChatInviteLinkResponse revokeInviteLink(UUID requesterId, UUID chatId, UUID inviteLinkId) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanManageInviteLinks(chat, requesterId);

        ChatInviteLinkEntity inviteLink = chatInviteLinkRepository.findById(inviteLinkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite link not found"));
        if (!inviteLink.getChatId().equals(chatId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite link not found");
        }

        inviteLink.setRevoked(true);
        ChatInviteLinkEntity savedInviteLink = chatInviteLinkRepository.save(inviteLink);
        chatAdminLogService.log(
                chatId,
                requesterId,
                null,
                "INVITE_LINK_REVOKED",
                "Revoked invite link",
                null,
                savedInviteLink.getId()
        );
        return toInviteLinkResponse(savedInviteLink);
    }

    @Transactional
    public ChatSummaryResponse updatePublicUsername(
            UUID requesterId,
            UUID chatId,
            UpdateChatPublicUsernameRequest request
    ) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanManageInviteLinks(chat, requesterId);
        chat.setPublicUsername(normalizePublicUsername(request.publicUsername()));
        try {
            ChatSummaryResponse response = buildChatSummary(
                    chatRepository.save(chat),
                    requesterId,
                    getMembership(chatId, requesterId),
                    getDraft(chatId, requesterId)
            );
            chatAdminLogService.log(
                    chatId,
                    requesterId,
                    null,
                    "PUBLIC_USERNAME_UPDATED",
                    "Updated public username",
                    null,
                    null
            );
            return response;
        } catch (DataIntegrityViolationException duplicateUsername) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Public username already taken");
        }
    }

    @Transactional
    public JoinChatResultResponse joinByInviteLink(UUID requesterId, String token) {
        ChatInviteLinkEntity inviteLink = chatInviteLinkRepository.findByToken(token.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite link not found"));
        if (Boolean.TRUE.equals(inviteLink.getRevoked())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invite link revoked");
        }
        if (inviteLink.getExpiresAt() != null && !inviteLink.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invite link expired");
        }
        int usageCount = inviteLink.getUsageCount() != null ? inviteLink.getUsageCount() : 0;
        if (inviteLink.getUsageLimit() != null && usageCount >= inviteLink.getUsageLimit()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invite link usage limit reached");
        }

        ChatEntity chat = chatRepository.findById(inviteLink.getChatId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found"));
        if (!List.of("GROUP", "CHANNEL").contains(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invite links are only supported for groups and channels");
        }

        return joinChat(chat, requesterId, "INVITE_LINK", inviteLink);
    }

    @Transactional
    public JoinChatResultResponse joinByPublicUsername(UUID requesterId, String username) {
        String normalizedUsername = normalizePublicUsername(username);
        if (normalizedUsername == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is blank");
        }

        ChatEntity chat = chatRepository.findByPublicUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Public chat not found"));
        if (!List.of("GROUP", "CHANNEL").contains(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Public usernames are only supported for groups and channels");
        }

        return joinChat(chat, requesterId, "PUBLIC_USERNAME", null);
    }

    @Transactional(readOnly = true)
    public ChatEntity getOwnedChat(UUID requesterId, UUID chatId) {
        ChatEntity chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found"));

        if (!chatMemberRepository.existsByIdChatIdAndIdUserId(chatId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied");
        }

        return chat;
    }

    @Transactional(readOnly = true)
    public ChatEntity getChat(UUID chatId) {
        return chatRepository.findById(chatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found"));
    }

    @Transactional(readOnly = true)
    public List<ChatMemberResponse> getMembers(UUID requesterId, UUID chatId) {
        getOwnedChat(requesterId, chatId);
        List<ChatMemberEntity> memberships = chatMemberRepository.findAllByIdChatId(chatId);
        Map<UUID, UserEntity> usersById = userRepository.findAllById(
                memberships.stream().map(member -> member.getId().getUserId()).toList()
        ).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        return memberships.stream()
                .map(member -> {
                    UserEntity user = usersById.get(member.getId().getUserId());
                    return toChatMemberResponse(member, user);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicChatDiscoveryResponse> discoverPublicChats(UUID requesterId, String query, int limit) {
        String normalizedQuery = query.trim();
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        int normalizedLimit = Math.min(Math.max(limit, 1), 20);
        Set<UUID> joinedChatIds = chatMemberRepository.findAllByIdUserId(requesterId).stream()
                .map(member -> member.getId().getChatId())
                .collect(Collectors.toSet());

        return chatRepository.searchPublicChats(normalizedQuery).stream()
                .limit(normalizedLimit)
                .map(chat -> {
                    PhotoAccess photoAccess = buildChatPhotoAccess(chat);
                    return new PublicChatDiscoveryResponse(
                            chat.getId(),
                            chat.getChatType(),
                            resolveChatTitle(chat),
                            photoAccess.photoUrl(),
                            photoAccess.photoAccessExpiresAt(),
                            chat.getPublicUsername(),
                            chat.getAbout(),
                            Boolean.TRUE.equals(chat.getForumEnabled()),
                            chatMemberRepository.countByIdChatId(chat.getId()),
                            Boolean.TRUE.equals(chat.getJoinRequiresApproval()),
                            joinedChatIds.contains(chat.getId())
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatJoinRequestResponse> listJoinRequests(UUID requesterId, UUID chatId) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanApproveJoinRequests(chat, requesterId);

        List<ChatJoinRequestEntity> joinRequests = chatJoinRequestRepository
                .findAllByIdChatIdAndStatusOrderByRequestedAtDesc(chatId, "PENDING");
        Map<UUID, UserEntity> usersById = userRepository.findAllById(
                joinRequests.stream().map(request -> request.getId().getUserId()).toList()
        ).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        return joinRequests.stream()
                .map(request -> toJoinRequestResponse(request, usersById.get(request.getId().getUserId())))
                .toList();
    }

    @Transactional
    public ChatMemberResponse approveJoinRequest(UUID requesterId, UUID chatId, UUID userId) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanApproveJoinRequests(chat, requesterId);

        ChatJoinRequestEntity joinRequest = getPendingJoinRequest(chatId, userId);
        assertNotBanned(chatId, userId);
        ChatMemberEntity membership = chatMemberRepository.findById(new ChatMemberId(chatId, userId))
                .orElseGet(() -> chatMemberRepository.save(newMember(chat, userId, "MEMBER")));
        markJoinRequestDecision(joinRequest, "APPROVED", requesterId);
        chatAdminLogService.log(chatId, requesterId, userId, "JOIN_REQUEST_APPROVED", "Approved join request", null, null);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toChatMemberResponse(membership, user);
    }

    @Transactional
    public void declineJoinRequest(UUID requesterId, UUID chatId, UUID userId) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanApproveJoinRequests(chat, requesterId);
        markJoinRequestDecision(getPendingJoinRequest(chatId, userId), "DECLINED", requesterId);
        chatAdminLogService.log(chatId, requesterId, userId, "JOIN_REQUEST_DECLINED", "Declined join request", null, null);
    }

    @Transactional(readOnly = true)
    public List<ChatMemberResponse> listRestrictedMembers(UUID requesterId, UUID chatId) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanManageMessages(chat, requesterId);
        return getMembers(requesterId, chatId).stream()
                .filter(member -> !member.canSendMessages())
                .toList();
    }

    @Transactional
    public ChatMemberResponse updateMemberRestriction(
            UUID requesterId,
            UUID chatId,
            UUID userId,
            UpdateMemberRestrictionRequest request
    ) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanManageMessages(chat, requesterId);

        if (requesterId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot restrict your own membership");
        }

        ChatMemberEntity target = getMembership(chatId, userId);
        if ("OWNER".equals(target.getRole())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Owner cannot be restricted");
        }

        boolean canSendMessages = request.canSendMessages() != null && request.canSendMessages();
        if (canSendMessages) {
            clearMemberRestriction(target);
        } else {
            Instant normalizedRestrictedUntil = normalizeRestrictedUntil(request.restrictedUntil());
            target.setCanSendMessages(false);
            target.setRestrictedUntil(normalizedRestrictedUntil);
            target.setRestrictionReason(normalizeRestrictionReason(request.restrictionReason()));
            target.setRestrictedByUserId(requesterId);
        }
        ChatMemberEntity saved = chatMemberRepository.save(target);
        chatAdminLogService.log(
                chatId,
                requesterId,
                userId,
                canSendMessages ? "RESTRICTION_CLEARED" : "MEMBER_RESTRICTED",
                canSendMessages ? "Cleared member restriction" : "Updated member restriction",
                null,
                null
        );

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toChatMemberResponse(saved, user);
    }

    @Transactional
    public List<ChatBanResponse> listBans(UUID requesterId, UUID chatId) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanManageMessages(chat, requesterId);
        purgeExpiredBans(chatId);

        List<ChatBanEntity> bans = chatBanRepository.findAllByIdChatIdOrderByBannedAtDesc(chatId);
        Map<UUID, UserEntity> usersById = userRepository.findAllById(
                bans.stream().map(ban -> ban.getId().getUserId()).toList()
        ).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        return bans.stream()
                .filter(this::isBanActive)
                .map(ban -> toChatBanResponse(ban, usersById.get(ban.getId().getUserId())))
                .toList();
    }

    @Transactional
    public ChatBanResponse banMember(
            UUID requesterId,
            UUID chatId,
            UUID userId,
            UpdateChatBanRequest request
    ) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanManageMessages(chat, requesterId);
        if (!List.of("GROUP", "CHANNEL").contains(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bans are only available for groups and channels");
        }
        if (requesterId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot ban yourself");
        }

        ensureUserExists(userId);
        chatMemberRepository.findById(new ChatMemberId(chatId, userId)).ifPresent(member -> {
            if ("OWNER".equals(member.getRole())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Owner cannot be banned");
            }
        });

        ChatBanEntity ban = chatBanRepository.findById(new ChatBanId(chatId, userId))
                .orElseGet(ChatBanEntity::new);
        ban.setId(new ChatBanId(chatId, userId));
        ban.setBannedUntil(normalizeBanUntil(request.bannedUntil()));
        ban.setReason(normalizeRestrictionReason(request.reason()));
        ban.setBannedByUserId(requesterId);
        ban.setBannedAt(Instant.now());
        ChatBanEntity savedBan = chatBanRepository.save(ban);

        chatMemberRepository.findById(new ChatMemberId(chatId, userId)).ifPresent(chatMemberRepository::delete);
        chatJoinRequestRepository.findByIdChatIdAndIdUserId(chatId, userId)
                .ifPresent(joinRequest -> markJoinRequestDecision(joinRequest, "DECLINED", requesterId));
        chatAdminLogService.log(chatId, requesterId, userId, "MEMBER_BANNED", "Banned member from chat", null, null);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toChatBanResponse(savedBan, user);
    }

    @Transactional
    public void unbanMember(UUID requesterId, UUID chatId, UUID userId) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanManageMessages(chat, requesterId);
        chatBanRepository.deleteById(new ChatBanId(chatId, userId));
        chatAdminLogService.log(chatId, requesterId, userId, "MEMBER_UNBANNED", "Removed member ban", null, null);
    }

    @Transactional
    public void recordMessageSent(UUID chatId, UUID senderId, Instant sentAt) {
        chatMemberRepository.findById(new ChatMemberId(chatId, senderId)).ifPresent(member -> {
            member.setLastSentMessageAt(sentAt);
            chatMemberRepository.save(member);
        });
    }

    @Transactional(readOnly = true)
    public boolean hasMessageModerationPermission(UUID requesterId, UUID chatId) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        if (!List.of("GROUP", "CHANNEL").contains(chat.getChatType())) {
            return false;
        }
        return hasManageMessagesPermission(getMembership(chatId, requesterId));
    }

    @Transactional(readOnly = true)
    public boolean areReactionsEnabled(UUID chatId) {
        ChatEntity chat = getChat(chatId);
        if (!List.of("GROUP", "CHANNEL", "DIRECT").contains(chat.getChatType())) {
            return true;
        }
        return !Boolean.FALSE.equals(chat.getReactionsEnabled());
    }

    @Transactional(readOnly = true)
    public boolean areCommentsEnabled(UUID sourceChannelId) {
        ChatEntity chat = getChat(sourceChannelId);
        if (!"CHANNEL".equals(chat.getChatType())) {
            return true;
        }
        return Boolean.TRUE.equals(chat.getCommentsEnabled());
    }

    @Transactional(readOnly = true)
    public boolean isCrossPostingEnabled(UUID sourceChannelId) {
        ChatEntity chat = getChat(sourceChannelId);
        if (!"CHANNEL".equals(chat.getChatType())) {
            return false;
        }
        return Boolean.TRUE.equals(chat.getCrossPostingEnabled());
    }

    @Transactional
    public void updateLastMessageAt(ChatEntity chat, Instant timestamp) {
        chat.setLastMessageAt(timestamp);
        chatRepository.save(chat);
    }

    @Transactional
    public ChatReadEventResponse markRead(UUID requesterId, UUID chatId, UUID messageId) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ChatMemberEntity membership = getMembership(chatId, requesterId);
        MessageLookupEntity targetMessage = requireReadableMessageInChat(chat, requesterId, messageId);
        MessageLookupEntity effectiveMessage = resolveEffectiveReadMessage(chat, requesterId, membership, targetMessage);
        if (effectiveMessage == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
        }

        if (Objects.equals(membership.getLastReadMessageId(), effectiveMessage.getMessageId())) {
            return new ChatReadEventResponse(
                    chatId,
                    requesterId,
                    effectiveMessage.getMessageId(),
                    membership.getLastReadAt() != null ? membership.getLastReadAt() : Instant.now()
            );
        }

        Instant readAt = Instant.now();
        UnreadTailCounters unreadTailCounters = recalculateUnreadTailCounters(requesterId, chatId, effectiveMessage.getMessageId());
        membership.setLastReadMessageId(effectiveMessage.getMessageId());
        membership.setLastReadAt(readAt);
        membership.setUnreadCount(unreadTailCounters.unreadCount());
        membership.setMentionCount(unreadTailCounters.mentionCount());
        membership.setReplyCount(unreadTailCounters.replyCount());
        chatMemberRepository.save(membership);
        return new ChatReadEventResponse(chatId, requesterId, effectiveMessage.getMessageId(), readAt);
    }

    @Transactional
    public void incrementUnreadCounts(UUID chatId, UUID senderId) {
        incrementUnreadCounts(chatId, senderId, null, null, null);
    }

    @Transactional
    public void incrementUnreadCounts(
            UUID chatId,
            UUID senderId,
            MessageTextContent content,
            UUID replyTargetSenderId
    ) {
        incrementUnreadCounts(chatId, senderId, content, replyTargetSenderId, null);
    }

    @Transactional
    public void incrementUnreadCounts(
            UUID chatId,
            UUID senderId,
            MessageTextContent content,
            UUID replyTargetSenderId,
            UUID topicId
    ) {
        if (!shouldTrackUnreadForTopic(chatId, topicId)) {
            return;
        }
        List<ChatMemberEntity> memberships = chatMemberRepository.findAllByIdChatId(chatId);
        Set<UUID> mentionedUserIds = resolveMentionedUserIds(memberships, senderId, content);
        for (ChatMemberEntity membership : memberships) {
            if (membership.getId().getUserId().equals(senderId)) {
                continue;
            }
            membership.setUnreadCount((membership.getUnreadCount() != null ? membership.getUnreadCount() : 0) + 1);
            if (replyTargetSenderId != null && replyTargetSenderId.equals(membership.getId().getUserId())) {
                membership.setReplyCount((membership.getReplyCount() != null ? membership.getReplyCount() : 0) + 1);
            }
            if (mentionedUserIds.contains(membership.getId().getUserId())) {
                membership.setMentionCount((membership.getMentionCount() != null ? membership.getMentionCount() : 0) + 1);
            }
        }
        chatMemberRepository.saveAll(memberships);
    }

    @Transactional(readOnly = true)
    public TypingEventResponse buildTypingEvent(UUID requesterId, UUID chatId, UUID topicId, boolean typing) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanPost(chat, requesterId);
        UUID resolvedTopicId = resolveTypingTopicId(chat, topicId);
        return new TypingEventResponse(
                chatId,
                requesterId,
                typing,
                resolvedTopicId,
                Instant.now()
        );
    }

    @Transactional
    public PinMessageEventResponse pinMessage(UUID requesterId, UUID chatId, UUID messageId) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanPin(chat, requesterId);
        validatePinnableMessage(chat, requesterId, messageId);

        Instant pinnedAt = Instant.now();
        chat.setPinnedMessageId(messageId);
        chatRepository.save(chat);
        recordPinEvent(chat.getId(), messageId, requesterId, pinnedAt);
        chatAdminLogService.log(chatId, requesterId, null, "MESSAGE_PINNED", "Pinned a message", messageId, null);

        return new PinMessageEventResponse(chatId, messageId, requesterId, pinnedAt);
    }

    @Transactional(readOnly = true)
    public List<ChatAdminLogResponse> listAdminLog(UUID requesterId, UUID chatId, int limit) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanViewAnalytics(chat, requesterId);
        return chatAdminLogService.list(chatId, limit);
    }

    @Transactional
    public ChatEntity getOrCreateDirectChat(UUID requesterId, UUID peerId) {
        if (requesterId.equals(peerId)) {
            return chatRepository.findByChatTypeAndCreatedBy("SAVED", requesterId)
                    .orElseGet(() -> createSavedMessages(requesterId));
        }
        ensureUserExists(peerId);
        UUID lowId = requesterId.compareTo(peerId) <= 0 ? requesterId : peerId;
        UUID highId = requesterId.compareTo(peerId) <= 0 ? peerId : requesterId;

        return chatRepository.findByParticipantLowIdAndParticipantHighId(lowId, highId)
                .orElseGet(() -> createDirectChatEntity(lowId, highId));
    }

    @Transactional(readOnly = true)
    public UUID getPeerUserId(ChatEntity chat, UUID requesterId) {
        if (!"DIRECT".equals(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Peer user is only defined for direct chats");
        }
        if (chat.getParticipantLowId() == null || chat.getParticipantHighId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Direct chat participants are missing");
        }
        if (!chatMemberRepository.existsByIdChatIdAndIdUserId(chat.getId(), requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied");
        }
        return chat.getParticipantLowId().equals(requesterId) ? chat.getParticipantHighId() : chat.getParticipantLowId();
    }

    @Transactional(readOnly = true)
    public List<UUID> getRecipientIds(ChatEntity chat, UUID requesterId) {
        getOwnedChat(requesterId, chat.getId());
        if ("SAVED".equals(chat.getChatType())) {
            return List.of();
        }
        if ("DIRECT".equals(chat.getChatType())) {
            return List.of(getPeerUserId(chat, requesterId));
        }

        return chatMemberRepository.findAllByIdChatId(chat.getId()).stream()
                .map(member -> member.getId().getUserId())
                .filter(userId -> !userId.equals(requesterId))
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> getRecipientIdsForSystem(ChatEntity chat, UUID senderId) {
        if ("SAVED".equals(chat.getChatType())) {
            return List.of();
        }
        if ("DIRECT".equals(chat.getChatType())) {
            if (chat.getParticipantLowId() == null || chat.getParticipantHighId() == null) {
                return List.of();
            }
            return List.of(chat.getParticipantLowId(), chat.getParticipantHighId()).stream()
                    .filter(userId -> !userId.equals(senderId))
                    .distinct()
                    .toList();
        }

        return chatMemberRepository.findAllByIdChatId(chat.getId()).stream()
                .map(member -> member.getId().getUserId())
                .filter(userId -> !userId.equals(senderId))
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    @Transactional(readOnly = true)
    public void ensureCanPost(ChatEntity chat, UUID requesterId) {
        getOwnedChat(requesterId, chat.getId());
        ChatMemberEntity membership = getMembership(chat.getId(), requesterId);
        if ("DIRECT".equals(chat.getChatType()) && isDirectInteractionBlocked(requesterId, getPeerUserId(chat, requesterId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Direct chat is blocked");
        }
        if (!"DIRECT".equals(chat.getChatType()) && isMessagingRestricted(membership)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Posting is restricted for this member");
        }
        if ("CHANNEL".equals(chat.getChatType()) && !hasPostMessagesPermission(membership)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Posting is disabled for this member");
        }
        if (List.of("GROUP", "CHANNEL").contains(chat.getChatType())
                && chat.getSlowModeSeconds() != null
                && chat.getSlowModeSeconds() > 0
                && !isAdminRole(membership)
                && membership.getLastSentMessageAt() != null) {
            Instant nextAllowedAt = membership.getLastSentMessageAt().plusSeconds(chat.getSlowModeSeconds());
            if (nextAllowedAt.isAfter(Instant.now())) {
                long retryAfterSeconds = Math.max(1L, nextAllowedAt.getEpochSecond() - Instant.now().getEpochSecond());
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Slow mode is active. Try again in " + retryAfterSeconds + " seconds"
                );
            }
        }
    }

    @Transactional(readOnly = true)
    public ChatSummaryResponse getChatSummary(UUID requesterId, UUID chatId) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        return buildChatSummary(chat, requesterId, getMembership(chatId, requesterId), getDraft(chatId, requesterId));
    }

    @Transactional(readOnly = true)
    public ChatAnalyticsResponse getAnalytics(UUID requesterId, UUID chatId) {
        ChatEntity chat = getOwnedChat(requesterId, chatId);
        ensureCanViewAnalytics(chat, requesterId);

        List<ChatMemberEntity> memberships = chatMemberRepository.findAllByIdChatId(chatId);
        Instant since = Instant.now().minusSeconds(86_400);
        Instant until = Instant.now().plusSeconds(1);
        List<MessageEntity> recentMessages = messageRepository.findAllByChatIdWithinRange(chatId, since, until).stream()
                .filter(message -> message.getDeletedAt() == null)
                .toList();

        long adminCount = memberships.stream()
                .filter(this::isAdminRole)
                .count();
        long restrictedCount = memberships.stream()
                .filter(member -> !Boolean.TRUE.equals(member.getCanSendMessages()))
                .count();
        long bannedCount = chatBanRepository.findAllByIdChatIdOrderByBannedAtDesc(chatId).stream()
                .filter(this::isBanActive)
                .count();
        long pendingJoinRequestCount = chatJoinRequestRepository
                .findAllByIdChatIdAndStatusOrderByRequestedAtDesc(chatId, "PENDING")
                .size();
        long activeInviteLinkCount = chatInviteLinkRepository.findAllByChatIdOrderByCreatedAtDesc(chatId).stream()
                .filter(link -> !Boolean.TRUE.equals(link.getRevoked()))
                .count();
        long reactionsLast24h = countRecentReactions(recentMessages);
        long commentsLast24h = "CHANNEL".equals(chat.getChatType())
                ? countRecentComments(chat, since, until)
                : 0;

        return new ChatAnalyticsResponse(
                chatId,
                chat.getChatType(),
                memberships.size(),
                adminCount,
                restrictedCount,
                bannedCount,
                pendingJoinRequestCount,
                activeInviteLinkCount,
                recentMessages.size(),
                reactionsLast24h,
                commentsLast24h,
                chat.getLastMessageAt() != null ? chat.getLastMessageAt() : chat.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public ChatMemberEntity getMembership(UUID chatId, UUID userId) {
        return chatMemberRepository.findById(new ChatMemberId(chatId, userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied"));
    }

    @Transactional(readOnly = true)
    public MessageAuthorView resolveMessageAuthor(UUID requesterId, UUID chatId, UUID senderId) {
        if (senderId == null) {
            return new MessageAuthorView(null, "Unknown", null, null, false);
        }

        ChatEntity chat = getChat(chatId);
        ChatMemberEntity membership = chatMemberRepository.findById(new ChatMemberId(chatId, senderId)).orElse(null);
        boolean anonymousAdmin = membership != null
                && isAnonymousAdmin(membership)
                && !senderId.equals(requesterId);
        if (anonymousAdmin) {
            PhotoAccess photoAccess = buildChatPhotoAccess(chat);
            return new MessageAuthorView(
                    null,
                    resolveChatTitle(chat),
                    photoAccess.photoUrl(),
                    photoAccess.photoAccessExpiresAt(),
                    true
            );
        }

        UserEntity user = userRepository.findById(senderId).orElse(null);
        PhotoAccess photoAccess = user != null ? buildUserPhotoAccess(user) : null;
        return new MessageAuthorView(
                senderId,
                user != null && user.getDisplayName() != null ? user.getDisplayName() : "Unknown",
                photoAccess != null ? photoAccess.photoUrl() : null,
                photoAccess != null ? photoAccess.photoAccessExpiresAt() : null,
                false
        );
    }

    private ChatSummaryResponse buildChatSummary(
            ChatEntity chat,
            UUID requesterId,
            ChatMemberEntity membership,
            ChatDraftEntity draft
    ) {
        ChatLastMessagePreviewResponse lastMessage = buildLastMessagePreview(chat, requesterId);
        UUID pinnedMessageId = resolveVisiblePinnedMessageId(chat);
        Instant summaryLastMessageAt = resolveSummaryLastMessageAt(chat, lastMessage);
        long memberCount = chatMemberRepository.countByIdChatId(chat.getId());
        long topicCount = Boolean.TRUE.equals(chat.getForumEnabled())
                ? forumTopicRepository.countByChatIdAndHiddenFalse(chat.getId())
                : 0;
        UUID linkedDiscussionChatId = "CHANNEL".equals(chat.getChatType()) ? chat.getLinkedDiscussionChatId() : null;
        String linkedDiscussionChatTitle = linkedDiscussionChatId != null
                ? chatRepository.findById(linkedDiscussionChatId).map(linkedChat -> linkedChat.getTitle() != null ? linkedChat.getTitle() : "Discussion Group").orElse(null)
                : null;
        if ("SAVED".equals(chat.getChatType())) {
            return new ChatSummaryResponse(
                    chat.getId(),
                    chat.getChatType(),
                    "Saved Messages",
                    null,
                    null,
                    requesterId,
                    null,
                    "Saved Messages",
                    false,
                    null,
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    0,
                    null,
                    null,
                    summaryLastMessageAt,
                    memberCount,
                    membership.getLastReadAt(),
                    membership.getUnreadCount() != null ? membership.getUnreadCount() : 0,
                    membership.getMentionCount() != null ? membership.getMentionCount() : 0,
                    membership.getReplyCount() != null ? membership.getReplyCount() : 0,
                    Boolean.TRUE.equals(membership.getArchived()),
                    draft != null ? draft.getDraftText() : null,
                    draft != null ? draft.getUpdatedAt() : null,
                    membership.getMutedUntil(),
                    pinnedMessageId,
                    false,
                    false,
                    true,
                    false,
                    lastMessage
            );
        }

        if (!"DIRECT".equals(chat.getChatType())) {
            PhotoAccess chatPhotoAccess = buildChatPhotoAccess(chat);
            return new ChatSummaryResponse(
                    chat.getId(),
                    chat.getChatType(),
                    chat.getTitle() != null ? chat.getTitle() : ("CHANNEL".equals(chat.getChatType()) ? "Untitled Channel" : "Untitled Group"),
                    chatPhotoAccess.photoUrl(),
                    chatPhotoAccess.photoAccessExpiresAt(),
                    null,
                    null,
                    null,
                    false,
                    null,
                    false,
                    false,
                    null,
                    chat.getPublicUsername(),
                    chat.getAbout(),
                    chat.getAutoDeleteSeconds(),
                    chat.getSlowModeSeconds(),
                    Boolean.TRUE.equals(chat.getForumEnabled()),
                    topicCount,
                    linkedDiscussionChatId,
                    linkedDiscussionChatTitle,
                    summaryLastMessageAt,
                    memberCount,
                    membership.getLastReadAt(),
                    membership.getUnreadCount() != null ? membership.getUnreadCount() : 0,
                    membership.getMentionCount() != null ? membership.getMentionCount() : 0,
                    membership.getReplyCount() != null ? membership.getReplyCount() : 0,
                    Boolean.TRUE.equals(membership.getArchived()),
                    draft != null ? draft.getDraftText() : null,
                    draft != null ? draft.getUpdatedAt() : null,
                    membership.getMutedUntil(),
                    pinnedMessageId,
                    Boolean.TRUE.equals(chat.getJoinRequiresApproval()),
                    "CHANNEL".equals(chat.getChatType()) && Boolean.TRUE.equals(chat.getCommentsEnabled()),
                    Boolean.TRUE.equals(chat.getReactionsEnabled()),
                    "CHANNEL".equals(chat.getChatType()) && Boolean.TRUE.equals(chat.getCrossPostingEnabled()),
                    lastMessage
            );
        }

        UUID peerId = getPeerUserId(chat, requesterId);
        UserEntity peer = userRepository.findById(peerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Peer user not found"));
        PhotoAccess peerPhotoAccess = buildUserPhotoAccess(peer);
        UserPresenceService.UserPresenceView presence = userPresenceService.resolvePresence(requesterId, peer);

        return new ChatSummaryResponse(
                chat.getId(),
                chat.getChatType(),
                peer.getDisplayName(),
                peerPhotoAccess.photoUrl(),
                peerPhotoAccess.photoAccessExpiresAt(),
                peerId,
                peer.isBot() ? null : peer.getPhoneNumber(),
                peer.getDisplayName(),
                presence.online(),
                presence.lastSeenAt(),
                peer.isBot(),
                peer.isBotSupportsInline(),
                peer.getBotWebAppUrl(),
                peer.getUsername(),
                peer.isBot() && peer.getBotDescription() != null ? peer.getBotDescription() : peer.getAbout(),
                chat.getAutoDeleteSeconds(),
                chat.getSlowModeSeconds(),
                false,
                0,
                null,
                null,
                summaryLastMessageAt,
                memberCount,
                membership.getLastReadAt(),
                membership.getUnreadCount() != null ? membership.getUnreadCount() : 0,
                membership.getMentionCount() != null ? membership.getMentionCount() : 0,
                membership.getReplyCount() != null ? membership.getReplyCount() : 0,
                Boolean.TRUE.equals(membership.getArchived()),
                draft != null ? draft.getDraftText() : null,
                draft != null ? draft.getUpdatedAt() : null,
                membership.getMutedUntil(),
                pinnedMessageId,
                false,
                false,
                true,
                false,
                lastMessage
        );
    }

    private JoinChatResultResponse joinChat(
            ChatEntity chat,
            UUID requesterId,
            String source,
            ChatInviteLinkEntity inviteLink
    ) {
        assertNotBanned(chat.getId(), requesterId);
        if (chatMemberRepository.existsByIdChatIdAndIdUserId(chat.getId(), requesterId)) {
            return buildJoinedChatResult(chat, requesterId);
        }

        if (Boolean.TRUE.equals(chat.getJoinRequiresApproval())) {
            ChatJoinRequestEntity existingRequest = chatJoinRequestRepository
                    .findByIdChatIdAndIdUserId(chat.getId(), requesterId)
                    .orElse(null);
            if (existingRequest != null && "PENDING".equals(existingRequest.getStatus())) {
                return buildRequestedJoinResult(chat, existingRequest);
            }

            ChatJoinRequestEntity joinRequest = existingRequest != null ? existingRequest : new ChatJoinRequestEntity();
            joinRequest.setId(new ChatJoinRequestId(chat.getId(), requesterId));
            joinRequest.setStatus("PENDING");
            joinRequest.setSource(source);
            joinRequest.setInviteLinkId(inviteLink != null ? inviteLink.getId() : null);
            joinRequest.setRequestedAt(Instant.now());
            joinRequest.setDecidedAt(null);
            joinRequest.setDecidedByUserId(null);
            ChatJoinRequestEntity savedJoinRequest = chatJoinRequestRepository.save(joinRequest);
            if (inviteLink != null) {
                incrementInviteLinkUsage(inviteLink);
            }
            return buildRequestedJoinResult(chat, savedJoinRequest);
        }

        chatMemberRepository.save(newMember(chat, requesterId, "MEMBER"));
        if (inviteLink != null) {
            incrementInviteLinkUsage(inviteLink);
        }
        return buildJoinedChatResult(chat, requesterId);
    }

    private JoinChatResultResponse buildJoinedChatResult(ChatEntity chat, UUID requesterId) {
        ChatSummaryResponse chatSummary = buildChatSummary(
                chat,
                requesterId,
                getMembership(chat.getId(), requesterId),
                getDraft(chat.getId(), requesterId)
        );
        return new JoinChatResultResponse(
                "JOINED",
                chatSummary,
                chat.getId(),
                chatSummary.title(),
                chatSummary.publicUsername(),
                null
        );
    }

    private JoinChatResultResponse buildRequestedJoinResult(ChatEntity chat, ChatJoinRequestEntity joinRequest) {
        return new JoinChatResultResponse(
                "REQUESTED",
                null,
                chat.getId(),
                resolveChatTitle(chat),
                chat.getPublicUsername(),
                joinRequest.getRequestedAt()
        );
    }

    private void incrementInviteLinkUsage(ChatInviteLinkEntity inviteLink) {
        inviteLink.setUsageCount((inviteLink.getUsageCount() != null ? inviteLink.getUsageCount() : 0) + 1);
        inviteLink.setLastUsedAt(Instant.now());
        chatInviteLinkRepository.save(inviteLink);
    }

    private void validatePinnableMessage(ChatEntity chat, UUID requesterId, UUID messageId) {
        MessageLookupEntity message = requireMessageInChat(chat.getId(), messageId);
        if (message.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deleted message cannot be pinned");
        }
        if (!isReadableMessageForMember(chat, requesterId, message)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
        }
    }

    private MessageLookupEntity requireMessageInChat(UUID chatId, UUID messageId) {
        MessageLookupEntity message = messageLookupRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        if (!message.getChatId().equals(chatId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message belongs to another chat");
        }
        return message;
    }

    private MessageLookupEntity requireReadableMessageInChat(ChatEntity chat, UUID requesterId, UUID messageId) {
        MessageLookupEntity message = requireMessageInChat(chat.getId(), messageId);
        if (!isReadableMessageForMember(chat, requesterId, message)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
        }
        return message;
    }

    private MessageLookupEntity resolveEffectiveReadMessage(
            ChatEntity chat,
            UUID requesterId,
            ChatMemberEntity membership,
            MessageLookupEntity targetMessage
    ) {
        if (membership.getLastReadMessageId() == null) {
            return targetMessage;
        }

        MessageLookupEntity currentLastRead = messageLookupRepository.findById(membership.getLastReadMessageId()).orElse(null);
        if (currentLastRead == null
                || !chat.getId().equals(currentLastRead.getChatId())
                || !isReadableMessageForMember(chat, requesterId, currentLastRead)) {
            return targetMessage;
        }
        if (currentLastRead.getCreatedAt() == null || targetMessage.getCreatedAt() == null) {
            return targetMessage;
        }
        return targetMessage.getCreatedAt().isBefore(currentLastRead.getCreatedAt()) ? currentLastRead : targetMessage;
    }

    private ChatLastMessagePreviewResponse buildLastMessagePreview(ChatEntity chat, UUID requesterId) {
        MessageEntity message = findLastVisibleMessage(chat);
        if (message == null) {
            return null;
        }
        MessageTextContent content = safeDecodeMessageContent(chat.getId(), message);
        String messageType = resolveLastMessageType(content, message);
        MessageAuthorView author = resolveMessageAuthor(requesterId, chat.getId(), message.getSenderId());

        return new ChatLastMessagePreviewResponse(
                message.getKey().getMessageId(),
                author.senderId(),
                author.displayName(),
                author.anonymous(),
                requesterId.equals(message.getSenderId()),
                messageType,
                buildLastMessagePreviewText(content, messageType, message),
                message.getCreatedAt(),
                message.getEditedAt(),
                message.getDeletedAt()
        );
    }

    private UUID resolveVisiblePinnedMessageId(ChatEntity chat) {
        UUID pinnedMessageId = chat.getPinnedMessageId();
        if (pinnedMessageId == null) {
            return null;
        }

        MessageLookupEntity pinnedMessage = messageLookupRepository.findById(pinnedMessageId).orElse(null);
        if (pinnedMessage == null || !chat.getId().equals(pinnedMessage.getChatId()) || pinnedMessage.getDeletedAt() != null) {
            return null;
        }
        if (!isSummaryVisibleTopic(chat, pinnedMessage.getTopicId())) {
            return null;
        }
        return pinnedMessageId;
    }

    private Instant resolveSummaryLastMessageAt(ChatEntity chat, ChatLastMessagePreviewResponse lastMessage) {
        if (lastMessage != null && lastMessage.createdAt() != null) {
            return lastMessage.createdAt();
        }
        if (Boolean.TRUE.equals(chat.getForumEnabled())) {
            return chat.getCreatedAt();
        }
        return chat.getLastMessageAt() != null ? chat.getLastMessageAt() : chat.getCreatedAt();
    }

    private MessageEntity findLastVisibleMessage(ChatEntity chat) {
        int fetchLimit = Boolean.TRUE.equals(chat.getForumEnabled()) ? 100 : 1;
        List<MessageEntity> recentMessages = messageRepository.findRecentByChatId(chat.getId(), fetchLimit);
        if (recentMessages == null || recentMessages.isEmpty()) {
            return null;
        }
        if (!Boolean.TRUE.equals(chat.getForumEnabled())) {
            return recentMessages.get(0);
        }

        Set<UUID> visibleTopicIds = forumTopicRepository.findVisibleTopics(chat.getId()).stream()
                .map(ForumTopicEntity::getId)
                .collect(Collectors.toSet());
        return recentMessages.stream()
                .filter(message -> message.getTopicId() == null || visibleTopicIds.contains(message.getTopicId()))
                .findFirst()
                .orElse(null);
    }

    private boolean isSummaryVisibleTopic(ChatEntity chat, UUID topicId) {
        if (topicId == null) {
            return true;
        }
        if (!Boolean.TRUE.equals(chat.getForumEnabled())) {
            return false;
        }
        return forumTopicRepository.findByIdAndChatId(topicId, chat.getId())
                .map(topic -> !Boolean.TRUE.equals(topic.getHidden()))
                .orElse(false);
    }

    private boolean isReadableMessageForMember(ChatEntity chat, UUID requesterId, MessageLookupEntity message) {
        if (message == null || message.getDeletedAt() != null) {
            return false;
        }
        if (!chat.getId().equals(message.getChatId())) {
            return false;
        }
        return isSummaryVisibleTopic(chat, message.getTopicId());
    }

    private UnreadTailCounters recalculateUnreadTailCounters(UUID requesterId, UUID chatId, UUID lastReadMessageId) {
        ChatEntity chat = chatRepository.findById(chatId).orElse(null);
        Set<UUID> visibleTopicIds = chat != null && Boolean.TRUE.equals(chat.getForumEnabled())
                ? forumTopicRepository.findVisibleTopics(chatId).stream()
                        .map(ForumTopicEntity::getId)
                        .collect(Collectors.toSet())
                : Set.of();
        List<MessageEntity> unreadMessages = messageRepository.findAllByChatIdAfterMessageId(chatId, lastReadMessageId).stream()
                .filter(message -> isUnreadVisibleMessage(chat, visibleTopicIds, message))
                .filter(message -> message.getDeletedAt() == null)
                .filter(message -> !requesterId.equals(message.getSenderId()))
                .toList();
        if (unreadMessages.isEmpty()) {
            return new UnreadTailCounters(0, 0, 0);
        }

        Map<UUID, MessageLookupEntity> replyTargetsById = loadMessagesById(
                unreadMessages.stream()
                        .map(MessageEntity::getReplyToMessageId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()
        );
        String requesterUsername = userRepository.findById(requesterId)
                .map(UserEntity::getUsername)
                .map(String::trim)
                .filter(username -> !username.isBlank())
                .map(String::toLowerCase)
                .orElse(null);

        int mentionCount = 0;
        int replyCount = 0;
        for (MessageEntity message : unreadMessages) {
            if (isReplyToUser(message, requesterId, replyTargetsById)) {
                replyCount++;
            }
            if (mentionsUser(message, chatId, requesterUsername)) {
                mentionCount++;
            }
        }

        return new UnreadTailCounters(unreadMessages.size(), mentionCount, replyCount);
    }

    private boolean shouldTrackUnreadForTopic(UUID chatId, UUID topicId) {
        if (topicId == null) {
            return true;
        }
        ChatEntity chat = chatRepository.findById(chatId).orElse(null);
        return chat != null && isSummaryVisibleTopic(chat, topicId);
    }

    private UUID resolveTypingTopicId(ChatEntity chat, UUID topicId) {
        if (topicId == null) {
            return null;
        }
        if (!"GROUP".equals(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forum topics are only available in groups");
        }
        if (!Boolean.TRUE.equals(chat.getForumEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forum topics are disabled for this chat");
        }
        ForumTopicEntity topic = forumTopicRepository.findByIdAndChatId(topicId, chat.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found"));
        if (Boolean.TRUE.equals(topic.getHidden())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found");
        }
        if (Boolean.TRUE.equals(topic.getClosed())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Topic is closed");
        }
        return topic.getId();
    }

    private boolean isUnreadVisibleMessage(ChatEntity chat, Set<UUID> visibleTopicIds, MessageEntity message) {
        if (message.getTopicId() == null) {
            return true;
        }
        return chat != null
                && Boolean.TRUE.equals(chat.getForumEnabled())
                && visibleTopicIds.contains(message.getTopicId());
    }

    private Map<UUID, MessageLookupEntity> loadMessagesById(List<UUID> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return java.util.stream.StreamSupport.stream(messageLookupRepository.findAllById(messageIds).spliterator(), false)
                .collect(Collectors.toMap(MessageLookupEntity::getMessageId, Function.identity(), (left, right) -> left));
    }

    private boolean isReplyToUser(
            MessageEntity message,
            UUID requesterId,
            Map<UUID, MessageLookupEntity> replyTargetsById
    ) {
        if (message.getReplyToMessageId() == null) {
            return false;
        }
        MessageLookupEntity replyTarget = replyTargetsById.get(message.getReplyToMessageId());
        return replyTarget != null && requesterId.equals(replyTarget.getSenderId());
    }

    private boolean mentionsUser(MessageEntity message, UUID chatId, String username) {
        if (username == null) {
            return false;
        }
        return extractMentionUsernames(decodeMessageContent(chatId, message)).contains(username);
    }

    private MessageTextContent decodeMessageContent(UUID chatId, MessageEntity message) {
        if (message.getCiphertext() == null || message.getNonce() == null || message.getKeyVersion() == null) {
            return new MessageTextContent("", List.of());
        }
        String plaintext = chatEncryptionService.decrypt(chatId, message.getCiphertext(), message.getNonce(), message.getKeyVersion());
        return messageContentCodec.decode(plaintext);
    }

    private MessageTextContent safeDecodeMessageContent(UUID chatId, MessageEntity message) {
        try {
            return decodeMessageContent(chatId, message);
        } catch (RuntimeException ignored) {
            return new MessageTextContent("", List.of());
        }
    }

    private String resolveLastMessageType(MessageTextContent content, MessageEntity message) {
        if (content.messageType() != null && !content.messageType().isBlank()) {
            return content.messageType();
        }
        if (message.getPollId() != null) {
            return "POLL";
        }
        if (message.getStickerId() != null) {
            return "STICKER";
        }
        List<AttachmentEntity> attachments = loadAttachments(message.getAttachmentIds());
        if (attachments.size() > 1) {
            return "ALBUM";
        }
        if (!attachments.isEmpty()) {
            String kind = attachments.get(0).getKind();
            return kind != null && !kind.isBlank() ? kind : "FILE";
        }
        if (message.getAttachmentIds() != null && !message.getAttachmentIds().isEmpty()) {
            return message.getAttachmentIds().size() > 1 ? "ALBUM" : "FILE";
        }
        return "TEXT";
    }

    private List<AttachmentEntity> loadAttachments(List<UUID> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, AttachmentEntity> attachmentsById = attachmentRepository.findAllByIdIn(attachmentIds).stream()
                .collect(Collectors.toMap(AttachmentEntity::getId, Function.identity(), (left, right) -> left));
        return attachmentIds.stream()
                .map(attachmentsById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private String buildLastMessagePreviewText(MessageTextContent content, String messageType, MessageEntity message) {
        if (message.getDeletedAt() != null) {
            return "Message deleted";
        }

        String text = normalizePreviewText(content.text());
        if (!text.isBlank()) {
            return text;
        }

        String caption = normalizePreviewText(content.caption());
        if (!caption.isBlank()) {
            return caption;
        }

        return switch (messageType) {
            case "LOCATION" -> firstNonBlank(
                    normalizePreviewText(content.location() != null ? content.location().title() : null),
                    normalizePreviewText(content.location() != null ? content.location().address() : null),
                    "shared a location"
            );
            case "CONTACT_CARD" -> firstNonBlank(
                    normalizePreviewText(contactName(content)),
                    normalizePreviewText(content.contactCard() != null ? content.contactCard().phoneNumber() : null),
                    "shared a contact"
            );
            case "SERVICE_MESSAGE" -> firstNonBlank(
                    normalizePreviewText(content.serviceMessage() != null ? content.serviceMessage().text() : null),
                    "sent a service update"
            );
            case "POLL" -> "sent a poll";
            case "STICKER" -> "sent a sticker";
            case "ALBUM" -> "sent an album";
            case "VOICE" -> "sent a voice message";
            case "AUDIO" -> "sent an audio file";
            case "VIDEO" -> "sent a video";
            case "VIDEO_NOTE" -> "sent a video note";
            case "GIF" -> "sent a GIF";
            case "IMAGE" -> "sent a photo";
            case "FILE" -> "sent an attachment";
            default -> "sent a message";
        };
    }

    private String contactName(MessageTextContent content) {
        if (content.contactCard() == null) {
            return null;
        }
        String firstName = content.contactCard().firstName() != null ? content.contactCard().firstName().trim() : "";
        String lastName = content.contactCard().lastName() != null ? content.contactCard().lastName().trim() : "";
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? null : fullName;
    }

    private String normalizePreviewText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "";
        }
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private void recordPinEvent(UUID chatId, UUID messageId, UUID requesterId, Instant pinnedAt) {
        chatPinEventRepository.findFirstByChatIdAndActiveTrueOrderByPinnedAtDesc(chatId)
                .ifPresent(activePin -> {
                    activePin.setActive(false);
                    activePin.setUnpinnedAt(pinnedAt);
                    chatPinEventRepository.saveAndFlush(activePin);
                });

        ChatPinEventEntity pinEvent = new ChatPinEventEntity();
        pinEvent.setChatId(chatId);
        pinEvent.setMessageId(messageId);
        pinEvent.setPinnedByUserId(requesterId);
        pinEvent.setPinnedAt(pinnedAt);
        pinEvent.setActive(true);
        pinEvent.setUnpinnedAt(null);
        chatPinEventRepository.save(pinEvent);
    }

    private ChatJoinRequestEntity getPendingJoinRequest(UUID chatId, UUID userId) {
        ChatJoinRequestEntity joinRequest = chatJoinRequestRepository.findByIdChatIdAndIdUserId(chatId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Join request not found"));
        if (!"PENDING".equals(joinRequest.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Join request is no longer pending");
        }
        return joinRequest;
    }

    private void markJoinRequestDecision(ChatJoinRequestEntity joinRequest, String status, UUID decidedByUserId) {
        joinRequest.setStatus(status);
        joinRequest.setDecidedAt(Instant.now());
        joinRequest.setDecidedByUserId(decidedByUserId);
        chatJoinRequestRepository.save(joinRequest);
    }

    private void markJoinRequestApprovedIfPresent(UUID chatId, UUID userId, UUID decidedByUserId) {
        chatJoinRequestRepository.findByIdChatIdAndIdUserId(chatId, userId)
                .filter(request -> "PENDING".equals(request.getStatus()))
                .ifPresent(request -> markJoinRequestDecision(request, "APPROVED", decidedByUserId));
    }

    private ChatJoinRequestResponse toJoinRequestResponse(ChatJoinRequestEntity request, UserEntity user) {
        PhotoAccess photoAccess = user != null ? buildUserPhotoAccess(user) : null;
        return new ChatJoinRequestResponse(
                request.getId().getUserId(),
                user != null ? user.getPhoneNumber() : null,
                user != null ? user.getDisplayName() : "Unknown",
                user != null ? user.getUsername() : null,
                photoAccess != null ? photoAccess.photoUrl() : null,
                photoAccess != null ? photoAccess.photoAccessExpiresAt() : null,
                request.getSource(),
                request.getInviteLinkId(),
                request.getRequestedAt()
        );
    }

    private ChatBanResponse toChatBanResponse(ChatBanEntity ban, UserEntity user) {
        PhotoAccess photoAccess = user != null ? buildUserPhotoAccess(user) : null;
        return new ChatBanResponse(
                ban.getId().getUserId(),
                user != null ? user.getPhoneNumber() : null,
                user != null ? user.getDisplayName() : "Unknown",
                user != null ? user.getUsername() : null,
                photoAccess != null ? photoAccess.photoUrl() : null,
                photoAccess != null ? photoAccess.photoAccessExpiresAt() : null,
                ban.getBannedAt(),
                ban.getBannedUntil(),
                ban.getReason(),
                ban.getBannedByUserId()
        );
    }

    private ChatMemberResponse toChatMemberResponse(ChatMemberEntity member, UserEntity user) {
        PhotoAccess photoAccess = user != null ? buildUserPhotoAccess(user) : null;
        boolean canSendMessages = !isMessagingRestricted(member);
        return new ChatMemberResponse(
                member.getId().getUserId(),
                user != null ? user.getPhoneNumber() : null,
                user != null ? user.getDisplayName() : "Unknown",
                photoAccess != null ? photoAccess.photoUrl() : null,
                photoAccess != null ? photoAccess.photoAccessExpiresAt() : null,
                member.getRole(),
                member.getJoinedAt(),
                member.getLastReadAt(),
                member.getLastSentMessageAt(),
                canSendMessages,
                hasManageMembersPermission(member),
                hasManageInviteLinksPermission(member),
                hasManageMessagesPermission(member),
                hasPinMessagesPermission(member),
                hasApproveJoinRequestsPermission(member),
                hasPostMessagesPermission(member),
                isAnonymousAdmin(member),
                canSendMessages ? null : member.getRestrictedUntil(),
                canSendMessages ? null : member.getRestrictionReason()
        );
    }

    private boolean isMessagingRestricted(ChatMemberEntity member) {
        if (Boolean.TRUE.equals(member.getCanSendMessages())) {
            return false;
        }
        return member.getRestrictedUntil() == null || member.getRestrictedUntil().isAfter(Instant.now());
    }

    private void clearMemberRestriction(ChatMemberEntity member) {
        member.setCanSendMessages(true);
        member.setRestrictedUntil(null);
        member.setRestrictionReason(null);
        member.setRestrictedByUserId(null);
    }

    private Instant normalizeRestrictedUntil(Instant restrictedUntil) {
        if (restrictedUntil == null) {
            return null;
        }
        if (!restrictedUntil.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Restriction end must be in the future");
        }
        return restrictedUntil;
    }

    private String normalizeRestrictionReason(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private Instant normalizeBanUntil(Instant bannedUntil) {
        if (bannedUntil == null) {
            return null;
        }
        if (!bannedUntil.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ban end must be in the future");
        }
        return bannedUntil;
    }

    private String resolveChatTitle(ChatEntity chat) {
        if (chat.getTitle() != null && !chat.getTitle().isBlank()) {
            return chat.getTitle();
        }
        return "CHANNEL".equals(chat.getChatType()) ? "Untitled Channel" : "Untitled Group";
    }

    private ChatDraftEntity getDraft(UUID chatId, UUID requesterId) {
        return chatDraftRepository.findById(new ChatDraftId(requesterId, chatId)).orElse(null);
    }

    private ChatInviteLinkResponse toInviteLinkResponse(ChatInviteLinkEntity inviteLink) {
        return new ChatInviteLinkResponse(
                inviteLink.getId(),
                inviteLink.getChatId(),
                inviteLink.getLabel(),
                inviteLink.getToken(),
                "alex://join/" + inviteLink.getToken(),
                Boolean.TRUE.equals(inviteLink.getRevoked()),
                inviteLink.getUsageLimit(),
                inviteLink.getUsageCount() != null ? inviteLink.getUsageCount() : 0,
                inviteLink.getExpiresAt(),
                inviteLink.getCreatedAt(),
                inviteLink.getLastUsedAt()
        );
    }

    private String generateInviteToken() {
        byte[] bytes = new byte[18];
        String token;
        do {
            secureRandom.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (chatInviteLinkRepository.findByToken(token).isPresent());
        return token;
    }

    private PhotoAccess buildChatPhotoAccess(ChatEntity chat) {
        return profilePhotoService.buildPhotoAccess(
                chat.getPhotoStorageProvider(),
                chat.getPhotoBucketName(),
                chat.getPhotoObjectKey()
        );
    }

    private PhotoAccess buildUserPhotoAccess(UserEntity user) {
        return profilePhotoService.buildPhotoAccess(
                user.getPhotoStorageProvider(),
                user.getPhotoBucketName(),
                user.getPhotoObjectKey()
        );
    }

    private String normalizePublicUsername(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            return null;
        }
        if (!normalized.matches("^[A-Za-z0-9_]{5,64}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Public username must be 5-64 chars and contain only letters, digits or underscore");
        }
        return normalized;
    }

    private String normalizeAbout(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private int decodeChatCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int offset = Integer.parseInt(decoded);
            if (offset < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat cursor is invalid");
            }
            return offset;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat cursor is invalid", exception);
        }
    }

    private String encodeChatCursor(int offset) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(String.valueOf(offset).getBytes(StandardCharsets.UTF_8));
    }

    private Integer normalizeAutoDeleteSeconds(Integer value) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Auto-delete timer must be positive");
        }
        if (value > 31_536_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Auto-delete timer is too large");
        }
        return value;
    }

    private Integer normalizeSlowModeSeconds(Integer value, String chatType) {
        if (value == null) {
            return null;
        }
        if (!List.of("GROUP", "CHANNEL").contains(chatType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slow mode is only available for groups and channels");
        }
        if (value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slow mode must be positive");
        }
        if (value > 86_400) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slow mode is too large");
        }
        return value;
    }

    private ForumTopicEntity ensureGeneralForumTopic(ChatEntity chat, UUID requesterId) {
        if (!"GROUP".equals(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forum mode is only available for groups");
        }
        if (!Boolean.TRUE.equals(chat.getForumEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forum mode is disabled for this group");
        }
        return forumTopicRepository.findByChatIdAndGeneralTopicTrue(chat.getId())
                .orElseGet(() -> {
                    ForumTopicEntity topic = new ForumTopicEntity();
                    topic.setChatId(chat.getId());
                    topic.setTitle("General");
                    topic.setCreatedBy(requesterId);
                    topic.setGeneralTopic(true);
                    topic.setClosed(false);
                    topic.setHidden(false);
                    topic.setLastMessageAt(chat.getLastMessageAt());
                    return forumTopicRepository.save(topic);
                });
    }

    private ChatEntity createMultiMemberChat(
            String chatType,
            UUID requesterId,
            String title,
            String about,
            Integer autoDeleteSeconds,
            boolean forumEnabled,
            boolean joinRequiresApproval,
            Set<UUID> memberIds
    ) {
        ChatEntity chat = new ChatEntity();
        chat.setChatType(chatType);
        chat.setTitle(title);
        chat.setAbout(normalizeAbout(about));
        chat.setAutoDeleteSeconds(normalizeAutoDeleteSeconds(autoDeleteSeconds));
        chat.setForumEnabled(forumEnabled);
        chat.setJoinRequiresApproval(joinRequiresApproval);
        chat.setCreatedBy(requesterId);
        ChatEntity savedChat = chatRepository.save(chat);

        List<ChatMemberEntity> memberships = new ArrayList<>();
        memberships.add(newMember(savedChat, requesterId, "OWNER"));
        for (UUID memberId : memberIds) {
            memberships.add(newMember(savedChat, memberId, "MEMBER"));
        }
        chatMemberRepository.saveAll(memberships);
        if (forumEnabled) {
            ensureGeneralForumTopic(savedChat, requesterId);
        }
        return savedChat;
    }

    private ChatEntity createDirectChatEntity(UUID lowId, UUID highId) {
        try {
            ChatEntity chat = new ChatEntity();
            chat.setChatType("DIRECT");
            chat.setCreatedBy(lowId);
            chat.setParticipantLowId(lowId);
            chat.setParticipantHighId(highId);
            ChatEntity saved = chatRepository.save(chat);
            chatMemberRepository.saveAll(List.of(
                    newMember(saved, lowId, "OWNER"),
                    newMember(saved, highId, "MEMBER")
            ));
            return saved;
        } catch (DataIntegrityViolationException duplicateChatRace) {
            return chatRepository.findByParticipantLowIdAndParticipantHighId(lowId, highId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to create chat"));
        }
    }

    private ChatEntity createSavedMessages(UUID requesterId) {
        ChatEntity chat = new ChatEntity();
        chat.setChatType("SAVED");
        chat.setTitle("Saved Messages");
        chat.setCreatedBy(requesterId);
        ChatEntity saved = chatRepository.save(chat);
        chatMemberRepository.save(newMember(saved, requesterId, "OWNER"));
        return saved;
    }

    private ChatMemberEntity newMember(ChatEntity chat, UUID userId, String role) {
        ChatMemberEntity member = new ChatMemberEntity();
        member.setId(new ChatMemberId(chat.getId(), userId));
        member.setCanSendMessages(true);
        applyRoleDefaults(member, chat.getChatType(), role);
        return member;
    }

    private void applyRoleDefaults(ChatMemberEntity member, String chatType, String role) {
        member.setRole(role);
        if ("OWNER".equals(role) || "ADMIN".equals(role)) {
            member.setCanManageMembers(true);
            member.setCanManageInviteLinks(true);
            member.setCanManageMessages(true);
            member.setCanPinMessages(true);
            member.setCanApproveJoinRequests(true);
            member.setCanPostMessages(true);
            member.setAnonymousAdmin(false);
            return;
        }

        member.setCanManageMembers(false);
        member.setCanManageInviteLinks(false);
        member.setCanManageMessages(false);
        member.setCanPinMessages(false);
        member.setCanApproveJoinRequests(false);
        member.setCanPostMessages(!"CHANNEL".equals(chatType));
        member.setAnonymousAdmin(false);
    }

    private void ensureUsersExist(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        List<UserEntity> users = userRepository.findAllById(userIds);
        if (users.size() != userIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more members not found");
        }
    }

    private void ensureUserExists(UUID userId) {
        if (userRepository.existsById(userId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Peer user not found");
    }

    private void ensureCanPin(ChatEntity chat, UUID requesterId) {
        if ("DIRECT".equals(chat.getChatType())) {
            return;
        }

        ChatMemberEntity membership = getMembership(chat.getId(), requesterId);
        if (!hasPinMessagesPermission(membership)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can pin messages");
        }
    }

    private void ensureCanManageMembers(ChatEntity chat, UUID requesterId) {
        if (!List.of("GROUP", "CHANNEL").contains(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Member management is only available for groups and channels");
        }

        ChatMemberEntity membership = getMembership(chat.getId(), requesterId);
        if (!hasManageMembersPermission(membership)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can manage members");
        }
    }

    private void ensureCanManageInviteLinks(ChatEntity chat, UUID requesterId) {
        if (!List.of("GROUP", "CHANNEL").contains(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invite links are only available for groups and channels");
        }
        ChatMemberEntity membership = getMembership(chat.getId(), requesterId);
        if (!hasManageInviteLinksPermission(membership)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invite link management is not allowed for this member");
        }
    }

    private void ensureCanApproveJoinRequests(ChatEntity chat, UUID requesterId) {
        if (!List.of("GROUP", "CHANNEL").contains(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Join requests are only available for groups and channels");
        }
        ChatMemberEntity membership = getMembership(chat.getId(), requesterId);
        if (!hasApproveJoinRequestsPermission(membership)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Join request approval is not allowed for this member");
        }
    }

    private void ensureCanManageMessages(ChatEntity chat, UUID requesterId) {
        if (!List.of("GROUP", "CHANNEL").contains(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Restrictions are only available for groups and channels");
        }
        ChatMemberEntity membership = getMembership(chat.getId(), requesterId);
        if (!hasManageMessagesPermission(membership)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Message moderation is not allowed for this member");
        }
    }

    private void ensureCanViewAnalytics(ChatEntity chat, UUID requesterId) {
        if (!List.of("GROUP", "CHANNEL").contains(chat.getChatType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Analytics are only available for groups and channels"
            );
        }
        ChatMemberEntity membership = getMembership(chat.getId(), requesterId);
        if (!hasAnalyticsAccess(membership)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Analytics are not available for this member");
        }
    }

    private boolean hasManageMembersPermission(ChatMemberEntity member) {
        return isOwnerRole(member) || ("ADMIN".equals(member.getRole()) && Boolean.TRUE.equals(member.getCanManageMembers()));
    }

    private boolean hasManageInviteLinksPermission(ChatMemberEntity member) {
        return isOwnerRole(member) || ("ADMIN".equals(member.getRole()) && Boolean.TRUE.equals(member.getCanManageInviteLinks()));
    }

    private boolean hasManageMessagesPermission(ChatMemberEntity member) {
        return isOwnerRole(member) || ("ADMIN".equals(member.getRole()) && Boolean.TRUE.equals(member.getCanManageMessages()));
    }

    private boolean hasPinMessagesPermission(ChatMemberEntity member) {
        return isOwnerRole(member) || ("ADMIN".equals(member.getRole()) && Boolean.TRUE.equals(member.getCanPinMessages()));
    }

    private boolean hasApproveJoinRequestsPermission(ChatMemberEntity member) {
        return isOwnerRole(member) || ("ADMIN".equals(member.getRole()) && Boolean.TRUE.equals(member.getCanApproveJoinRequests()));
    }

    private boolean hasPostMessagesPermission(ChatMemberEntity member) {
        return isOwnerRole(member) || Boolean.TRUE.equals(member.getCanPostMessages());
    }

    private boolean isAnonymousAdmin(ChatMemberEntity member) {
        return "ADMIN".equals(member.getRole()) && Boolean.TRUE.equals(member.getAnonymousAdmin());
    }

    private boolean hasAnalyticsAccess(ChatMemberEntity member) {
        return isOwnerRole(member)
                || Boolean.TRUE.equals(member.getCanManageMembers())
                || Boolean.TRUE.equals(member.getCanManageInviteLinks())
                || Boolean.TRUE.equals(member.getCanManageMessages())
                || Boolean.TRUE.equals(member.getCanApproveJoinRequests())
                || Boolean.TRUE.equals(member.getCanPinMessages());
    }

    private boolean isAdminRole(ChatMemberEntity member) {
        return isOwnerRole(member) || "ADMIN".equals(member.getRole());
    }

    private boolean isOwnerRole(ChatMemberEntity member) {
        return "OWNER".equals(member.getRole());
    }

    private void assertNotBanned(UUID chatId, UUID userId) {
        chatBanRepository.findById(new ChatBanId(chatId, userId))
                .ifPresent(ban -> {
                    if (!isBanActive(ban)) {
                        chatBanRepository.delete(ban);
                        return;
                    }
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This user is banned from the chat");
                });
    }

    private boolean isBanActive(ChatBanEntity ban) {
        return ban.getBannedUntil() == null || ban.getBannedUntil().isAfter(Instant.now());
    }

    private void purgeExpiredBans(UUID chatId) {
        List<ChatBanEntity> expiredBans = chatBanRepository.findAllByIdChatIdAndBannedUntilBefore(chatId, Instant.now());
        if (!expiredBans.isEmpty()) {
            chatBanRepository.deleteAllInBatch(expiredBans);
        }
    }

    private boolean isDirectInteractionBlocked(UUID requesterId, UUID peerUserId) {
        return blockedUserRepository.existsByIdOwnerUserIdAndIdBlockedUserId(requesterId, peerUserId)
                || blockedUserRepository.existsByIdOwnerUserIdAndIdBlockedUserId(peerUserId, requesterId);
    }

    private long countRecentReactions(List<MessageEntity> recentMessages) {
        if (recentMessages.isEmpty()) {
            return 0;
        }
        List<UUID> messageIds = recentMessages.stream()
                .map(message -> message.getKey().getMessageId())
                .toList();
        return messageReactionRepository.findAllByIdMessageIdIn(messageIds).size();
    }

    private long countRecentComments(ChatEntity channelChat, Instant since, Instant until) {
        if (channelChat.getLinkedDiscussionChatId() == null) {
            return 0;
        }
        List<MessageEntity> discussionMessages = messageRepository.findAllByChatIdWithinRange(
                channelChat.getLinkedDiscussionChatId(),
                since,
                until
        ).stream()
                .filter(message -> message.getDeletedAt() == null)
                .filter(message -> message.getThreadRootMessageId() != null)
                .toList();
        if (discussionMessages.isEmpty()) {
            return 0;
        }

        List<UUID> rootIds = discussionMessages.stream()
                .map(message -> message.getThreadRootMessageId())
                .distinct()
                .toList();
        Map<UUID, MessageLookupEntity> rootsById = java.util.stream.StreamSupport
                .stream(messageLookupRepository.findAllById(rootIds).spliterator(), false)
                .collect(Collectors.toMap(MessageLookupEntity::getMessageId, java.util.function.Function.identity()));

        return discussionMessages.stream()
                .filter(message -> !message.getKey().getMessageId().equals(message.getThreadRootMessageId()))
                .filter(message -> {
                    MessageLookupEntity root = rootsById.get(message.getThreadRootMessageId());
                    return root != null && channelChat.getId().equals(root.getForwardedFromChatId());
                })
                .count();
    }

    private void ensureSupportsCustomPhoto(ChatEntity chat) {
        if (!List.of("GROUP", "CHANNEL").contains(chat.getChatType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Custom chat photo is only available for groups and channels");
        }
    }

    private boolean matchesChatQuery(ChatSummaryResponse chat, String normalizedQuery) {
        return containsIgnoreCase(chat.title(), normalizedQuery)
                || containsIgnoreCase(chat.about(), normalizedQuery)
                || containsIgnoreCase(chat.publicUsername(), normalizedQuery)
                || containsIgnoreCase(chat.peerDisplayName(), normalizedQuery)
                || containsIgnoreCase(chat.peerPhoneNumber(), normalizedQuery)
                || containsIgnoreCase(chat.lastMessage() != null ? chat.lastMessage().previewText() : null, normalizedQuery)
                || containsIgnoreCase(chat.lastMessage() != null ? chat.lastMessage().senderDisplayName() : null, normalizedQuery);
    }

    private boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase().contains(normalizedQuery);
    }

    private Set<UUID> resolveMentionedUserIds(
            List<ChatMemberEntity> memberships,
            UUID senderId,
            MessageTextContent content
    ) {
        if (content == null) {
            return Set.of();
        }

        Set<String> mentionedUsernames = extractMentionUsernames(content);
        if (mentionedUsernames.isEmpty()) {
            return Set.of();
        }

        List<UUID> memberUserIds = memberships.stream()
                .map(member -> member.getId().getUserId())
                .filter(userId -> !userId.equals(senderId))
                .distinct()
                .toList();
        Map<String, UUID> userIdsByUsername = userRepository.findAllById(memberUserIds).stream()
                .filter(user -> user.getUsername() != null && !user.getUsername().isBlank())
                .collect(Collectors.toMap(
                        user -> user.getUsername().trim().toLowerCase(),
                        UserEntity::getId,
                        (left, right) -> left
                ));

        return mentionedUsernames.stream()
                .map(userIdsByUsername::get)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<String> extractMentionUsernames(MessageTextContent content) {
        Set<String> usernames = new LinkedHashSet<>();
        collectMentionUsernames(usernames, content.text());
        collectMentionUsernames(usernames, content.caption());
        return usernames;
    }

    private void collectMentionUsernames(Set<String> usernames, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Matcher matcher = USERNAME_MENTION_PATTERN.matcher(value);
        while (matcher.find()) {
            usernames.add(matcher.group(1).toLowerCase());
        }
    }
}
