package com.alex.messenger.chat;

import com.alex.messenger.chat.dto.ChatMemberResponse;
import com.alex.messenger.chat.dto.ChatAdminLogResponse;
import com.alex.messenger.chat.dto.ChatInviteLinkResponse;
import com.alex.messenger.chat.dto.ChatJoinRequestResponse;
import com.alex.messenger.chat.dto.ChatBanResponse;
import com.alex.messenger.chat.dto.ChatAnalyticsResponse;
import com.alex.messenger.chat.dto.ChatReadEventResponse;
import com.alex.messenger.chat.dto.ChatSummaryResponse;
import com.alex.messenger.chat.dto.PinnedMessageHistoryResponse;
import com.alex.messenger.chat.dto.AddMembersRequest;
import com.alex.messenger.chat.dto.ArchiveChatRequest;
import com.alex.messenger.chat.dto.CreateInviteLinkRequest;
import com.alex.messenger.chat.dto.CreateChannelRequest;
import com.alex.messenger.chat.dto.CreateDirectChatRequest;
import com.alex.messenger.chat.dto.CreateGroupChatRequest;
import com.alex.messenger.chat.dto.JoinByInviteLinkRequest;
import com.alex.messenger.chat.dto.JoinByPublicUsernameRequest;
import com.alex.messenger.chat.dto.JoinChatResultResponse;
import com.alex.messenger.chat.dto.MarkReadRequest;
import com.alex.messenger.chat.dto.MemberMutationResponse;
import com.alex.messenger.chat.dto.CreateForumTopicRequest;
import com.alex.messenger.chat.dto.ForumTopicResponse;
import com.alex.messenger.chat.dto.MuteChatRequest;
import com.alex.messenger.chat.dto.PinMessageEventResponse;
import com.alex.messenger.chat.dto.PinMessageRequest;
import com.alex.messenger.chat.dto.TypingEventRequest;
import com.alex.messenger.chat.dto.TypingEventResponse;
import com.alex.messenger.chat.dto.TransferChatOwnershipRequest;
import com.alex.messenger.chat.dto.TransferChatOwnershipResponse;
import com.alex.messenger.chat.dto.UpdateChatPublicUsernameRequest;
import com.alex.messenger.chat.dto.UpdateChatProfileRequest;
import com.alex.messenger.chat.dto.UpdateChatBanRequest;
import com.alex.messenger.chat.dto.UpdateForumTopicRequest;
import com.alex.messenger.chat.dto.UpdateMemberPermissionsRequest;
import com.alex.messenger.chat.dto.UpdateMemberRestrictionRequest;
import com.alex.messenger.chat.dto.UpsertChatDraftRequest;
import com.alex.messenger.chat.dto.UpdateMemberRoleRequest;
import com.alex.messenger.chat.folder.ChatFolderService;
import com.alex.messenger.chat.forum.ForumTopicService;
import com.alex.messenger.message.MessageDeliveryService;
import com.alex.messenger.shared.CurrentUser;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatPinHistoryService chatPinHistoryService;
    private final ChatReadEventPublisher chatReadEventPublisher;
    private final ChatPinEventPublisher chatPinEventPublisher;
    private final TypingEventPublisher typingEventPublisher;
    private final MessageDeliveryService messageDeliveryService;
    private final ForumTopicService forumTopicService;
    private final ChatFolderService chatFolderService;

    @GetMapping
    public ResponseEntity<List<ChatSummaryResponse>> listChats(
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) UUID folderId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        Integer validatedLimit = limit != null ? requireLimit(limit, 100) : null;
        if (folderId == null && archived != null && limit == null && cursor == null) {
            return ResponseEntity.ok(chatService.listChats(CurrentUser.id(), archived));
        }

        if (folderId == null && archived == null) {
            boolean effectiveArchived = false;
            if (limit == null && cursor == null) {
                return ResponseEntity.ok(chatService.listChats(CurrentUser.id(), effectiveArchived));
            }
            return buildPagedResponse(
                    chatService.listChatsPage(CurrentUser.id(), effectiveArchived, cursor, validatedLimit)
            );
        }

        if (folderId == null) {
            return buildPagedResponse(
                    chatService.listChatsPage(CurrentUser.id(), Boolean.TRUE.equals(archived), cursor, validatedLimit)
            );
        }

        List<ChatSummaryResponse> chats = chatFolderService.listChats(CurrentUser.id(), folderId, archived);
        if (limit == null && cursor == null) {
            return ResponseEntity.ok(chats);
        }

        int normalizedLimit = validatedLimit != null ? validatedLimit : 50;
        int offset = decodeChatCursor(cursor);
        if (offset > chats.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat cursor is out of range");
        }
        int nextOffset = Math.min(offset + normalizedLimit, chats.size());
        boolean hasMore = nextOffset < chats.size();
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header("X-Chat-Has-More", String.valueOf(hasMore))
                .header("X-Chat-Limit", String.valueOf(normalizedLimit));
        if (hasMore) {
            builder.header("X-Chat-Next-Cursor", encodeChatCursor(nextOffset));
        }
        return builder.body(chats.subList(offset, nextOffset));
    }

    @PostMapping("/direct")
    public ResponseEntity<ChatSummaryResponse> createDirect(@Valid @RequestBody CreateDirectChatRequest request) {
        return ResponseEntity.ok(chatService.createDirectChat(CurrentUser.id(), request.peerUserId()));
    }

    @PostMapping("/group")
    public ResponseEntity<ChatSummaryResponse> createGroup(@Valid @RequestBody CreateGroupChatRequest request) {
        return ResponseEntity.ok(chatService.createGroupChat(CurrentUser.id(), request));
    }

    @PostMapping("/channel")
    public ResponseEntity<ChatSummaryResponse> createChannel(@Valid @RequestBody CreateChannelRequest request) {
        return ResponseEntity.ok(chatService.createChannel(CurrentUser.id(), request));
    }

    @PostMapping("/saved")
    public ResponseEntity<ChatSummaryResponse> createSavedMessages() {
        return ResponseEntity.ok(chatService.createSavedMessagesChat(CurrentUser.id()));
    }

    @PostMapping("/{chatId}/archive")
    public ResponseEntity<ChatSummaryResponse> archive(
            @PathVariable UUID chatId,
            @RequestBody ArchiveChatRequest request
    ) {
        return ResponseEntity.ok(chatService.archiveChat(CurrentUser.id(), chatId, request.archived()));
    }

    @PostMapping("/{chatId}/mute")
    public ResponseEntity<ChatSummaryResponse> mute(
            @PathVariable UUID chatId,
            @Valid @RequestBody MuteChatRequest request
    ) {
        return ResponseEntity.ok(chatService.muteChat(CurrentUser.id(), chatId, request.mutedUntil()));
    }

    @PutMapping("/{chatId}/list-pin")
    public ResponseEntity<ChatSummaryResponse> pinChatToList(@PathVariable UUID chatId) {
        return ResponseEntity.ok(chatService.pinChatToList(CurrentUser.id(), chatId));
    }

    @DeleteMapping("/{chatId}/list-pin")
    public ResponseEntity<ChatSummaryResponse> unpinChatFromList(@PathVariable UUID chatId) {
        return ResponseEntity.ok(chatService.unpinChatFromList(CurrentUser.id(), chatId));
    }

    @PutMapping("/{chatId}/draft")
    public ResponseEntity<ChatSummaryResponse> saveDraft(
            @PathVariable UUID chatId,
            @Valid @RequestBody UpsertChatDraftRequest request
    ) {
        return ResponseEntity.ok(chatService.saveDraft(CurrentUser.id(), chatId, request.text()));
    }

    @DeleteMapping("/{chatId}/draft")
    public ResponseEntity<ChatSummaryResponse> clearDraft(@PathVariable UUID chatId) {
        return ResponseEntity.ok(chatService.clearDraft(CurrentUser.id(), chatId));
    }

    @GetMapping("/{chatId}/members")
    public ResponseEntity<List<ChatMemberResponse>> listMembers(@PathVariable UUID chatId) {
        return ResponseEntity.ok(chatService.getMembers(CurrentUser.id(), chatId));
    }

    @GetMapping("/{chatId}/permissions")
    public ResponseEntity<List<ChatMemberResponse>> listPermissions(@PathVariable UUID chatId) {
        return ResponseEntity.ok(chatService.getMembers(CurrentUser.id(), chatId));
    }

    @GetMapping("/{chatId}/join-requests")
    public ResponseEntity<List<ChatJoinRequestResponse>> listJoinRequests(@PathVariable UUID chatId) {
        return ResponseEntity.ok(chatService.listJoinRequests(CurrentUser.id(), chatId));
    }

    @PostMapping("/{chatId}/join-requests/{userId}/approve")
    public ResponseEntity<ChatMemberResponse> approveJoinRequest(
            @PathVariable UUID chatId,
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(chatService.approveJoinRequest(CurrentUser.id(), chatId, userId));
    }

    @PostMapping("/{chatId}/join-requests/{userId}/decline")
    public ResponseEntity<Void> declineJoinRequest(
            @PathVariable UUID chatId,
            @PathVariable UUID userId
    ) {
        chatService.declineJoinRequest(CurrentUser.id(), chatId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{chatId}/restrictions")
    public ResponseEntity<List<ChatMemberResponse>> listRestrictions(@PathVariable UUID chatId) {
        return ResponseEntity.ok(chatService.listRestrictedMembers(CurrentUser.id(), chatId));
    }

    @PostMapping("/{chatId}/restrictions/{userId}")
    public ResponseEntity<ChatMemberResponse> updateRestriction(
            @PathVariable UUID chatId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberRestrictionRequest request
    ) {
        return ResponseEntity.ok(chatService.updateMemberRestriction(CurrentUser.id(), chatId, userId, request));
    }

    @GetMapping("/{chatId}/bans")
    public ResponseEntity<List<ChatBanResponse>> listBans(@PathVariable UUID chatId) {
        return ResponseEntity.ok(chatService.listBans(CurrentUser.id(), chatId));
    }

    @GetMapping("/{chatId}/analytics")
    public ResponseEntity<ChatAnalyticsResponse> getAnalytics(@PathVariable UUID chatId) {
        return ResponseEntity.ok(chatService.getAnalytics(CurrentUser.id(), chatId));
    }

    @GetMapping("/{chatId}/admin-log")
    public ResponseEntity<List<ChatAdminLogResponse>> getAdminLog(
            @PathVariable UUID chatId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(chatService.listAdminLog(CurrentUser.id(), chatId, requireLimit(limit, 100)));
    }

    @PostMapping("/{chatId}/bans/{userId}")
    public ResponseEntity<ChatBanResponse> banMember(
            @PathVariable UUID chatId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateChatBanRequest request
    ) {
        return ResponseEntity.ok(chatService.banMember(CurrentUser.id(), chatId, userId, request));
    }

    @DeleteMapping("/{chatId}/bans/{userId}")
    public ResponseEntity<Void> unbanMember(
            @PathVariable UUID chatId,
            @PathVariable UUID userId
    ) {
        chatService.unbanMember(CurrentUser.id(), chatId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{chatId}/topics")
    public ResponseEntity<List<ForumTopicResponse>> listTopics(@PathVariable UUID chatId) {
        return ResponseEntity.ok(forumTopicService.listTopics(CurrentUser.id(), chatId));
    }

    @PostMapping("/{chatId}/topics")
    public ResponseEntity<ForumTopicResponse> createTopic(
            @PathVariable UUID chatId,
            @Valid @RequestBody CreateForumTopicRequest request
    ) {
        return ResponseEntity.ok(forumTopicService.createTopic(CurrentUser.id(), chatId, request));
    }

    @PostMapping("/{chatId}/topics/{topicId}")
    public ResponseEntity<ForumTopicResponse> updateTopic(
            @PathVariable UUID chatId,
            @PathVariable UUID topicId,
            @Valid @RequestBody UpdateForumTopicRequest request
    ) {
        return ResponseEntity.ok(forumTopicService.updateTopic(CurrentUser.id(), chatId, topicId, request));
    }

    @GetMapping("/{chatId}/invite-links")
    public ResponseEntity<List<ChatInviteLinkResponse>> listInviteLinks(@PathVariable UUID chatId) {
        return ResponseEntity.ok(chatService.getInviteLinks(CurrentUser.id(), chatId));
    }

    @PostMapping("/{chatId}/invite-links")
    public ResponseEntity<ChatInviteLinkResponse> createInviteLink(
            @PathVariable UUID chatId,
            @Valid @RequestBody CreateInviteLinkRequest request
    ) {
        return ResponseEntity.ok(chatService.createInviteLink(CurrentUser.id(), chatId, request));
    }

    @PostMapping("/{chatId}/invite-links/{inviteLinkId}/revoke")
    public ResponseEntity<ChatInviteLinkResponse> revokeInviteLink(
            @PathVariable UUID chatId,
            @PathVariable UUID inviteLinkId
    ) {
        return ResponseEntity.ok(chatService.revokeInviteLink(CurrentUser.id(), chatId, inviteLinkId));
    }

    @PostMapping("/{chatId}/profile")
    public ResponseEntity<ChatSummaryResponse> updateProfile(
            @PathVariable UUID chatId,
            @Valid @RequestBody UpdateChatProfileRequest request
    ) {
        return ResponseEntity.ok(chatService.updateChatProfile(CurrentUser.id(), chatId, request));
    }

    @PostMapping("/{chatId}/photo")
    public ResponseEntity<ChatSummaryResponse> uploadPhoto(
            @PathVariable UUID chatId,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(chatService.updateChatPhoto(CurrentUser.id(), chatId, file));
    }

    @DeleteMapping("/{chatId}/photo")
    public ResponseEntity<ChatSummaryResponse> removePhoto(@PathVariable UUID chatId) {
        return ResponseEntity.ok(chatService.removeChatPhoto(CurrentUser.id(), chatId));
    }

    @PostMapping("/{chatId}/public-username")
    public ResponseEntity<ChatSummaryResponse> updatePublicUsername(
            @PathVariable UUID chatId,
            @Valid @RequestBody UpdateChatPublicUsernameRequest request
    ) {
        return ResponseEntity.ok(chatService.updatePublicUsername(CurrentUser.id(), chatId, request));
    }

    @PostMapping("/{chatId}/members")
    public ResponseEntity<List<ChatMemberResponse>> addMembers(
            @PathVariable UUID chatId,
            @Valid @RequestBody AddMembersRequest request
    ) {
        return ResponseEntity.ok(chatService.addMembers(CurrentUser.id(), chatId, request));
    }

    @PostMapping("/{chatId}/members/{userId}/role")
    public ResponseEntity<MemberMutationResponse> updateRole(
            @PathVariable UUID chatId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberRoleRequest request
    ) {
        return ResponseEntity.ok(chatService.updateMemberRole(CurrentUser.id(), chatId, userId, request));
    }

    @PostMapping("/{chatId}/ownership")
    public ResponseEntity<TransferChatOwnershipResponse> transferOwnership(
            @PathVariable UUID chatId,
            @Valid @RequestBody TransferChatOwnershipRequest request
    ) {
        return ResponseEntity.ok(chatService.transferOwnership(CurrentUser.id(), chatId, request.newOwnerUserId()));
    }

    @PostMapping("/{chatId}/permissions/{userId}")
    public ResponseEntity<ChatMemberResponse> updatePermissions(
            @PathVariable UUID chatId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberPermissionsRequest request
    ) {
        return ResponseEntity.ok(chatService.updateMemberPermissions(CurrentUser.id(), chatId, userId, request));
    }

    @DeleteMapping("/{chatId}/members/{userId}")
    public ResponseEntity<MemberMutationResponse> removeMember(
            @PathVariable UUID chatId,
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(chatService.removeMember(CurrentUser.id(), chatId, userId));
    }

    @PostMapping("/{chatId}/read")
    public ResponseEntity<ChatReadEventResponse> markRead(
            @PathVariable UUID chatId,
            @Valid @RequestBody MarkReadRequest request
    ) {
        ChatReadEventResponse event = chatService.markRead(CurrentUser.id(), chatId, request.messageId());
        messageDeliveryService.markReadUpTo(CurrentUser.id(), chatId, event.messageId());
        chatReadEventPublisher.publish(event);
        return ResponseEntity.ok(event);
    }

    @PostMapping("/{chatId}/typing")
    public ResponseEntity<TypingEventResponse> typing(
            @PathVariable UUID chatId,
            @Valid @RequestBody TypingEventRequest request
    ) {
        TypingEventResponse event = chatService.buildTypingEvent(
                CurrentUser.id(),
                chatId,
                request.topicId(),
                request.typing()
        );
        typingEventPublisher.publish(event);
        return ResponseEntity.ok(event);
    }

    @PostMapping("/{chatId}/pin")
    public ResponseEntity<PinMessageEventResponse> pin(
            @PathVariable UUID chatId,
            @Valid @RequestBody PinMessageRequest request
    ) {
        PinMessageEventResponse event = chatService.pinMessage(CurrentUser.id(), chatId, request.messageId());
        chatPinEventPublisher.publish(event);
        return ResponseEntity.ok(event);
    }

    @GetMapping("/{chatId}/pins")
    public ResponseEntity<List<PinnedMessageHistoryResponse>> listPins(
            @PathVariable UUID chatId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(chatPinHistoryService.listPinnedMessages(CurrentUser.id(), chatId, requireLimit(limit, 50)));
    }

    @PostMapping("/join-by-link")
    public ResponseEntity<JoinChatResultResponse> joinByLink(@Valid @RequestBody JoinByInviteLinkRequest request) {
        return ResponseEntity.ok(chatService.joinByInviteLink(CurrentUser.id(), request.token()));
    }

    @PostMapping("/join-by-username")
    public ResponseEntity<JoinChatResultResponse> joinByUsername(@Valid @RequestBody JoinByPublicUsernameRequest request) {
        return ResponseEntity.ok(chatService.joinByPublicUsername(CurrentUser.id(), request.username()));
    }

    private ResponseEntity<List<ChatSummaryResponse>> buildPagedResponse(ChatService.ChatListSlice page) {
        int normalizedLimit = page.chats().size();
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header("X-Chat-Has-More", String.valueOf(page.hasMore()))
                .header("X-Chat-Limit", String.valueOf(normalizedLimit));
        if (page.nextCursor() != null) {
            builder.header("X-Chat-Next-Cursor", page.nextCursor());
        }
        return builder.body(page.chats());
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

    private int requireLimit(int limit, int max) {
        if (limit < 1 || limit > max) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and " + max
            );
        }
        return limit;
    }
}
