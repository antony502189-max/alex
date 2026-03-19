package com.alex.messenger.chat.suggested;

import com.alex.messenger.chat.suggested.dto.CreateSuggestedPostRequest;
import com.alex.messenger.chat.suggested.dto.DeclineSuggestedPostRequest;
import com.alex.messenger.chat.suggested.dto.SuggestedPostResponse;
import com.alex.messenger.shared.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/channels/{chatId}/suggested-posts")
@RequiredArgsConstructor
public class ChannelSuggestedPostController {

    private final ChannelSuggestedPostService channelSuggestedPostService;

    @GetMapping
    public ResponseEntity<List<SuggestedPostResponse>> listSuggestedPosts(
            @PathVariable UUID chatId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(
                channelSuggestedPostService.listSuggestedPosts(CurrentUser.id(), chatId, status, requireLimit(limit, 100))
        );
    }

    @PostMapping
    public ResponseEntity<SuggestedPostResponse> createSuggestedPost(
            @PathVariable UUID chatId,
            @Valid @RequestBody CreateSuggestedPostRequest request
    ) {
        return ResponseEntity.ok(
                channelSuggestedPostService.createSuggestedPost(CurrentUser.id(), chatId, request)
        );
    }

    @PostMapping("/{postId}/approve")
    public ResponseEntity<SuggestedPostResponse> approveSuggestedPost(
            @PathVariable UUID chatId,
            @PathVariable UUID postId
    ) {
        return ResponseEntity.ok(
                channelSuggestedPostService.approveSuggestedPost(CurrentUser.id(), chatId, postId)
        );
    }

    @PostMapping("/{postId}/decline")
    public ResponseEntity<SuggestedPostResponse> declineSuggestedPost(
            @PathVariable UUID chatId,
            @PathVariable UUID postId,
            @Valid @RequestBody(required = false) DeclineSuggestedPostRequest request
    ) {
        return ResponseEntity.ok(
                channelSuggestedPostService.declineSuggestedPost(CurrentUser.id(), chatId, postId, request)
        );
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
