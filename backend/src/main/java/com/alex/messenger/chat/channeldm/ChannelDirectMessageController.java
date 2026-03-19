package com.alex.messenger.chat.channeldm;

import com.alex.messenger.chat.channeldm.dto.ChannelDirectMessageResponse;
import com.alex.messenger.chat.channeldm.dto.ChannelDirectMessageStateResponse;
import com.alex.messenger.chat.channeldm.dto.ChannelDirectMessageTopicResponse;
import com.alex.messenger.chat.channeldm.dto.OpenChannelDirectMessageRequest;
import com.alex.messenger.shared.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/channels/{chatId}/direct-messages")
@RequiredArgsConstructor
public class ChannelDirectMessageController {

    private final ChannelDirectMessageService channelDirectMessageService;

    @PostMapping("/enable")
    public ResponseEntity<ChannelDirectMessageStateResponse> enableDirectMessages(@PathVariable UUID chatId) {
        return ResponseEntity.ok(channelDirectMessageService.enableDirectMessages(CurrentUser.id(), chatId));
    }

    @PostMapping("/open")
    public ResponseEntity<ChannelDirectMessageResponse> openDirectMessage(
            @PathVariable UUID chatId,
            @Valid @RequestBody(required = false) OpenChannelDirectMessageRequest request
    ) {
        return ResponseEntity.ok(channelDirectMessageService.openDirectMessage(CurrentUser.id(), chatId, request));
    }

    @GetMapping
    public ResponseEntity<List<ChannelDirectMessageResponse>> listDirectMessages(
            @PathVariable UUID chatId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(channelDirectMessageService.listDirectMessages(CurrentUser.id(), chatId, limit));
    }

    @GetMapping("/topics")
    public ResponseEntity<List<ChannelDirectMessageTopicResponse>> listDirectMessageTopics(
            @PathVariable UUID chatId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(channelDirectMessageService.listDirectMessageTopics(CurrentUser.id(), chatId, limit));
    }
}
