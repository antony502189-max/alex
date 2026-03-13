package com.alex.messenger.chat.folder;

import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.dto.ChatFolderResponse;
import com.alex.messenger.chat.dto.UpsertChatFolderRequest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ChatFolderService {

    private final ChatFolderRepository chatFolderRepository;
    private final ChatFolderItemRepository chatFolderItemRepository;
    private final ChatService chatService;

    @Transactional(readOnly = true)
    public List<ChatFolderResponse> list(UUID requesterId) {
        List<ChatFolderEntity> folders = chatFolderRepository.findAllByOwnerUserIdOrderByPositionAscTitleAsc(requesterId);
        if (folders.isEmpty()) {
            return List.of();
        }

        List<UUID> folderIds = folders.stream().map(ChatFolderEntity::getId).toList();
        Map<UUID, List<UUID>> chatIdsByFolder = chatFolderItemRepository.findAllByIdFolderIdIn(folderIds).stream()
                .collect(Collectors.groupingBy(
                        item -> item.getId().getFolderId(),
                        Collectors.mapping(item -> item.getId().getChatId(), Collectors.toList())
                ));

        return folders.stream()
                .map(folder -> new ChatFolderResponse(
                        folder.getId(),
                        folder.getTitle(),
                        folder.getPosition(),
                        chatIdsByFolder.getOrDefault(folder.getId(), List.of())
                ))
                .toList();
    }

    @Transactional
    public ChatFolderResponse create(UUID requesterId, UpsertChatFolderRequest request) {
        ChatFolderEntity folder = new ChatFolderEntity();
        folder.setOwnerUserId(requesterId);
        folder.setTitle(request.title().trim());
        folder.setPosition(request.position() != null ? request.position() : 0);
        ChatFolderEntity saved = chatFolderRepository.save(folder);
        replaceItems(requesterId, saved.getId(), request.chatIds());
        return getResponse(requesterId, saved.getId());
    }

    @Transactional
    public ChatFolderResponse update(UUID requesterId, UUID folderId, UpsertChatFolderRequest request) {
        ChatFolderEntity folder = getFolder(folderId, requesterId);
        folder.setTitle(request.title().trim());
        folder.setPosition(request.position() != null ? request.position() : folder.getPosition());
        chatFolderRepository.save(folder);
        replaceItems(requesterId, folderId, request.chatIds());
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

    private void replaceItems(UUID requesterId, UUID folderId, List<UUID> chatIds) {
        chatFolderItemRepository.deleteAllByIdFolderId(folderId);
        if (chatIds == null || chatIds.isEmpty()) {
            return;
        }

        for (UUID chatId : new LinkedHashSet<>(chatIds)) {
            chatService.getOwnedChat(requesterId, chatId);
            ChatFolderItemEntity item = new ChatFolderItemEntity();
            item.setId(new ChatFolderItemId(folderId, chatId));
            chatFolderItemRepository.save(item);
        }
    }
}
