package com.alex.messenger.search;

import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.dto.PublicChatDiscoveryResponse;
import com.alex.messenger.search.dto.GlobalSearchResponse;
import com.alex.messenger.shared.CurrentUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;
    private final ChatService chatService;

    @GetMapping("/global")
    public ResponseEntity<GlobalSearchResponse> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(globalSearchService.search(CurrentUser.id(), query, limit));
    }

    @GetMapping("/public")
    public ResponseEntity<List<PublicChatDiscoveryResponse>> discoverPublicChats(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(chatService.discoverPublicChats(CurrentUser.id(), query, limit));
    }
}
