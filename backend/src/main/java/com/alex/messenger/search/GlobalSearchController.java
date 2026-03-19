package com.alex.messenger.search;

import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.dto.PublicChatDiscoveryResponse;
import com.alex.messenger.search.dto.GlobalSearchResponse;
import com.alex.messenger.search.dto.PublicPostSearchResponse;
import com.alex.messenger.shared.CurrentUser;
import com.alex.messenger.shared.SearchQueryValidationSupport;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;
    private final ChatService chatService;
    private final PublicPostSearchService publicPostSearchService;

    @GetMapping("/global")
    public ResponseEntity<GlobalSearchResponse> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit
    ) {
        SearchQueryValidationSupport.normalize(query);
        return ResponseEntity.ok(globalSearchService.search(CurrentUser.id(), query, requireLimit(limit, 20)));
    }

    @GetMapping("/public")
    public ResponseEntity<List<PublicChatDiscoveryResponse>> discoverPublicChats(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit
    ) {
        SearchQueryValidationSupport.normalize(query);
        return ResponseEntity.ok(chatService.discoverPublicChats(CurrentUser.id(), query, requireLimit(limit, 20)));
    }

    @GetMapping("/public-posts")
    public ResponseEntity<PublicPostSearchResponse> searchPublicPosts(
            @RequestParam String query,
            @RequestParam(defaultValue = "20") int limit
    ) {
        SearchQueryValidationSupport.normalize(query);
        return ResponseEntity.ok(publicPostSearchService.searchPublicPosts(CurrentUser.id(), query, requireLimit(limit, 50)));
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
