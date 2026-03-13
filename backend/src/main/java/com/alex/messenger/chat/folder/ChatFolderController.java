package com.alex.messenger.chat.folder;

import com.alex.messenger.chat.dto.ChatFolderResponse;
import com.alex.messenger.chat.dto.UpsertChatFolderRequest;
import com.alex.messenger.shared.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class ChatFolderController {

    private final ChatFolderService chatFolderService;

    @GetMapping
    public ResponseEntity<List<ChatFolderResponse>> list() {
        return ResponseEntity.ok(chatFolderService.list(CurrentUser.id()));
    }

    @PostMapping
    public ResponseEntity<ChatFolderResponse> create(@Valid @RequestBody UpsertChatFolderRequest request) {
        return ResponseEntity.ok(chatFolderService.create(CurrentUser.id(), request));
    }

    @PatchMapping("/{folderId}")
    public ResponseEntity<ChatFolderResponse> update(
            @PathVariable UUID folderId,
            @Valid @RequestBody UpsertChatFolderRequest request
    ) {
        return ResponseEntity.ok(chatFolderService.update(CurrentUser.id(), folderId, request));
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<List<ChatFolderResponse>> delete(@PathVariable UUID folderId) {
        return ResponseEntity.ok(chatFolderService.delete(CurrentUser.id(), folderId));
    }
}
