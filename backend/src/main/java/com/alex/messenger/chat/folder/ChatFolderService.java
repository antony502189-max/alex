package com.alex.messenger.chat.folder;

import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.dto.ChatFolderResponse;
import com.alex.messenger.chat.dto.ChatSummaryResponse;
import com.alex.messenger.chat.dto.UpsertChatFolderRequest;
import com.alex.messenger.user.ContactEntity;
import com.alex.messenger.user.ContactRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ChatFolderService {

    private static final Set<String> SUPPORTED_CHAT_TYPES = Set.of("DIRECT", "GROUP", "CHANNEL", "SAVED");

    private final ChatFolderRepository chatFolderRepository;
    private final ChatFolderItemRepository chatFolderItemRepository;
    private final ChatFolderExcludedItemRepository chatFolderExcludedItemRepository;
    private final ChatService chatService;
    private final ContactRepository contactRepository;

    @Transactional(readOnly = true)
    public List<ChatFolderResponse> list(UUID requesterId) {
        List<ChatFolderEntity> folders = chatFolderRepository.findAllByOwnerUserIdOrderByPositionAscTitleAsc(requesterId);
        if (folders.isEmpty()) {
            return List.of();
        }

        List<UUID> folderIds = folders.stream().map(ChatFolderEntity::getId).toList();
        Map<UUID, List<UUID>> includedChatIdsByFolder = chatFolderItemRepository.findAllByIdFolderIdIn(folderIds).stream()
                .collect(Collectors.groupingBy(
                        item -> item.getId().getFolderId(),
                        Collectors.mapping(item -> item.getId().getChatId(), Collectors.toList())
                ));
        Map<UUID, List<UUID>> excludedChatIdsByFolder = chatFolderExcludedItemRepository.findAllByIdFolderIdIn(folderIds).stream()
                .collect(Collectors.groupingBy(
                        item -> item.getId().getFolderId(),
                        Collectors.mapping(item -> item.getId().getChatId(), Collectors.toList())
                ));
        List<ChatSummaryResponse> allChats = chatService.listAllChats(requesterId);
        Set<UUID> contactIds = contactRepository.findAllByIdOwnerUserIdOrderByContactNameAsc(requesterId).stream()
                .map(contact -> contact.getId().getContactUserId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return folders.stream()
                .map(folder -> toResponse(
                        requesterId,
                        folder,
                        includedChatIdsByFolder.getOrDefault(folder.getId(), List.of()),
                        excludedChatIdsByFolder.getOrDefault(folder.getId(), List.of()),
                        allChats,
                        contactIds
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatSummaryResponse> listChats(UUID requesterId, UUID folderId, Boolean archivedFilter) {
        ChatFolderEntity folder = getFolder(folderId, requesterId);
        List<UUID> includedChatIds = chatFolderItemRepository.findAllByIdFolderId(folderId).stream()
                .map(item -> item.getId().getChatId())
                .toList();
        List<UUID> excludedChatIds = chatFolderExcludedItemRepository.findAllByIdFolderId(folderId).stream()
                .map(item -> item.getId().getChatId())
                .toList();
        List<ChatSummaryResponse> allChats = chatService.listAllChats(requesterId);
        Set<UUID> contactIds = contactRepository.findAllByIdOwnerUserIdOrderByContactNameAsc(requesterId).stream()
                .map(contact -> contact.getId().getContactUserId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return resolveChats(folder, includedChatIds, excludedChatIds, allChats, contactIds, archivedFilter);
    }

    @Transactional
    public ChatFolderResponse create(UUID requesterId, UpsertChatFolderRequest request) {
        ChatFolderEntity folder = new ChatFolderEntity();
        folder.setOwnerUserId(requesterId);
        applyRequest(folder, request);
        ChatFolderEntity saved = chatFolderRepository.save(folder);
        replaceItems(requesterId, saved.getId(), resolveIncludedChatIds(request));
        replaceExcludedItems(requesterId, saved.getId(), request.excludedChatIds());
        return getResponse(requesterId, saved.getId());
    }

    @Transactional
    public ChatFolderResponse update(UUID requesterId, UUID folderId, UpsertChatFolderRequest request) {
        ChatFolderEntity folder = getFolder(folderId, requesterId);
        applyRequest(folder, request);
        chatFolderRepository.save(folder);
        replaceItems(requesterId, folderId, resolveIncludedChatIds(request));
        replaceExcludedItems(requesterId, folderId, request.excludedChatIds());
        return getResponse(requesterId, folderId);
    }

    @Transactional
    public List<ChatFolderResponse> delete(UUID requesterId, UUID folderId) {
        ChatFolderEntity folder = getFolder(folderId, requesterId);
        chatFolderRepository.delete(folder);
        return list(requesterId);
    }

    private ChatFolderResponse getResponse(UUID requesterId, UUID folderId) {
        return list(requesterId).stream()
                .filter(folder -> folder.folderId().equals(folderId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found"));
    }

    private ChatFolderEntity getFolder(UUID folderId, UUID requesterId) {
        ChatFolderEntity folder = chatFolderRepository.findById(folderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found"));
        if (!folder.getOwnerUserId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Folder access denied");
        }
        return folder;
    }

    private void applyRequest(ChatFolderEntity folder, UpsertChatFolderRequest request) {
        if (request.position() != null && request.position() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder position cannot be negative");
        }
        folder.setTitle(request.title().trim());
        folder.setPosition(request.position() != null ? request.position() : folder.getPosition());
        folder.setIncludedChatTypes(encodeChatTypes(request.includedChatTypes()));
        folder.setIncludeContacts(Boolean.TRUE.equals(request.includeContacts()));
        folder.setIncludeNonContacts(Boolean.TRUE.equals(request.includeNonContacts()));
        folder.setIncludeBots(Boolean.TRUE.equals(request.includeBots()));
        folder.setIncludeRead(Boolean.TRUE.equals(request.includeRead()));
        folder.setIncludeUnread(Boolean.TRUE.equals(request.includeUnread()));
        folder.setIncludeMuted(Boolean.TRUE.equals(request.includeMuted()));
        folder.setIncludeUnmuted(Boolean.TRUE.equals(request.includeUnmuted()));
        folder.setIncludeArchived(Boolean.TRUE.equals(request.includeArchived()));
        folder.setIncludeNonArchived(Boolean.TRUE.equals(request.includeNonArchived()));
    }

    private void replaceItems(UUID requesterId, UUID folderId, List<UUID> chatIds) {
        chatFolderItemRepository.deleteAllByIdFolderId(folderId);
        persistItems(requesterId, folderId, chatIds, true);
    }

    private void replaceExcludedItems(UUID requesterId, UUID folderId, List<UUID> chatIds) {
        chatFolderExcludedItemRepository.deleteAllByIdFolderId(folderId);
        persistItems(requesterId, folderId, chatIds, false);
    }

    private void persistItems(UUID requesterId, UUID folderId, List<UUID> chatIds, boolean included) {
        if (chatIds == null || chatIds.isEmpty()) {
            return;
        }
        for (UUID chatId : new LinkedHashSet<>(chatIds)) {
            chatService.getOwnedChat(requesterId, chatId);
            if (included) {
                ChatFolderItemEntity item = new ChatFolderItemEntity();
                item.setId(new ChatFolderItemId(folderId, chatId));
                chatFolderItemRepository.save(item);
            } else {
                ChatFolderExcludedItemEntity item = new ChatFolderExcludedItemEntity();
                item.setId(new ChatFolderExcludedItemId(folderId, chatId));
                chatFolderExcludedItemRepository.save(item);
            }
        }
    }

    private ChatFolderResponse toResponse(
            UUID requesterId,
            ChatFolderEntity folder,
            List<UUID> includedChatIds,
            List<UUID> excludedChatIds,
            List<ChatSummaryResponse> allChats,
            Set<UUID> contactIds
    ) {
        List<ChatSummaryResponse> resolvedChats = resolveChats(
                folder,
                includedChatIds,
                excludedChatIds,
                allChats,
                contactIds,
                null
        );
        return new ChatFolderResponse(
                folder.getId(),
                folder.getTitle(),
                folder.getPosition(),
                resolvedChats.stream().map(ChatSummaryResponse::chatId).toList(),
                includedChatIds,
                excludedChatIds,
                decodeChatTypes(folder.getIncludedChatTypes()),
                Boolean.TRUE.equals(folder.getIncludeContacts()),
                Boolean.TRUE.equals(folder.getIncludeNonContacts()),
                Boolean.TRUE.equals(folder.getIncludeBots()),
                Boolean.TRUE.equals(folder.getIncludeRead()),
                Boolean.TRUE.equals(folder.getIncludeUnread()),
                Boolean.TRUE.equals(folder.getIncludeMuted()),
                Boolean.TRUE.equals(folder.getIncludeUnmuted()),
                Boolean.TRUE.equals(folder.getIncludeArchived()),
                Boolean.TRUE.equals(folder.getIncludeNonArchived())
        );
    }

    private List<ChatSummaryResponse> resolveChats(
            ChatFolderEntity folder,
            List<UUID> includedChatIds,
            List<UUID> excludedChatIds,
            List<ChatSummaryResponse> allChats,
            Set<UUID> contactIds,
            Boolean archivedFilter
    ) {
        Set<UUID> explicitIncludes = new LinkedHashSet<>(includedChatIds != null ? includedChatIds : List.of());
        Set<UUID> explicitExcludes = new LinkedHashSet<>(excludedChatIds != null ? excludedChatIds : List.of());
        Set<String> includedChatTypes = new LinkedHashSet<>(decodeChatTypes(folder.getIncludedChatTypes()));
        boolean hasRuleSignals = !includedChatTypes.isEmpty()
                || Boolean.TRUE.equals(folder.getIncludeContacts())
                || Boolean.TRUE.equals(folder.getIncludeNonContacts())
                || Boolean.TRUE.equals(folder.getIncludeBots())
                || Boolean.TRUE.equals(folder.getIncludeRead())
                || Boolean.TRUE.equals(folder.getIncludeUnread())
                || Boolean.TRUE.equals(folder.getIncludeMuted())
                || Boolean.TRUE.equals(folder.getIncludeUnmuted())
                || Boolean.TRUE.equals(folder.getIncludeArchived())
                || Boolean.TRUE.equals(folder.getIncludeNonArchived());

        return allChats.stream()
                .filter(chat -> archivedFilter == null || chat.archived() == archivedFilter)
                .filter(chat -> !explicitExcludes.contains(chat.chatId()))
                .filter(chat -> explicitIncludes.contains(chat.chatId()) || !hasRuleSignals || matchesRuleSignals(
                        chat,
                        includedChatTypes,
                        folder,
                        contactIds
                ))
                .toList();
    }

    private boolean matchesRuleSignals(
            ChatSummaryResponse chat,
            Set<String> includedChatTypes,
            ChatFolderEntity folder,
            Set<UUID> contactIds
    ) {
        boolean contactChat = chat.peerUserId() != null
                && !Boolean.TRUE.equals(chat.peerIsBot())
                && contactIds.contains(chat.peerUserId());
        boolean nonContactChat = chat.peerUserId() != null
                && !Boolean.TRUE.equals(chat.peerIsBot())
                && !contactIds.contains(chat.peerUserId());
        boolean muted = chat.mutedUntil() != null && chat.mutedUntil().isAfter(java.time.Instant.now());
        boolean unread = chat.unreadCount() > 0 || chat.mentionCount() > 0 || chat.replyCount() > 0;

        return includedChatTypes.contains(chat.chatType())
                || (Boolean.TRUE.equals(folder.getIncludeContacts()) && contactChat)
                || (Boolean.TRUE.equals(folder.getIncludeNonContacts()) && nonContactChat)
                || (Boolean.TRUE.equals(folder.getIncludeBots()) && chat.peerIsBot())
                || (Boolean.TRUE.equals(folder.getIncludeRead()) && !unread)
                || (Boolean.TRUE.equals(folder.getIncludeUnread()) && unread)
                || (Boolean.TRUE.equals(folder.getIncludeMuted()) && muted)
                || (Boolean.TRUE.equals(folder.getIncludeUnmuted()) && !muted)
                || (Boolean.TRUE.equals(folder.getIncludeArchived()) && chat.archived())
                || (Boolean.TRUE.equals(folder.getIncludeNonArchived()) && !chat.archived());
    }

    private List<UUID> resolveIncludedChatIds(UpsertChatFolderRequest request) {
        if (request.includedChatIds() != null) {
            return request.includedChatIds();
        }
        return request.chatIds();
    }

    private String encodeChatTypes(List<String> rawChatTypes) {
        if (rawChatTypes == null || rawChatTypes.isEmpty()) {
            return null;
        }
        List<String> normalized = rawChatTypes.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalized.stream().anyMatch(chatType -> !SUPPORTED_CHAT_TYPES.contains(chatType))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported chat type filter");
        }
        return String.join(",", normalized);
    }

    private List<String> decodeChatTypes(String rawChatTypes) {
        if (rawChatTypes == null || rawChatTypes.isBlank()) {
            return List.of();
        }
        return List.of(rawChatTypes.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
