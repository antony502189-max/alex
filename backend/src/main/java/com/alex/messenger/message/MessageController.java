package com.alex.messenger.message;

import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.message.dto.CreateRepeatingMessageRequest;
import com.alex.messenger.message.dto.CreatePollMessageRequest;
import com.alex.messenger.message.dto.EditMessageRequest;
import com.alex.messenger.message.dto.ForwardMessageRequest;
import com.alex.messenger.message.dto.RepeatingMessageResponse;
import com.alex.messenger.message.dto.ScheduleMessageRequest;
import com.alex.messenger.message.dto.ScheduledMessageResponse;
import com.alex.messenger.message.dto.SearchMessagesResponse;
import com.alex.messenger.message.dto.SendMessageRequest;
import com.alex.messenger.message.dto.SendInlineBotResultRequest;
import com.alex.messenger.message.dto.ToggleReactionRequest;
import com.alex.messenger.message.dto.TranslateMessageRequest;
import com.alex.messenger.message.dto.TranslatedMessageResponse;
import com.alex.messenger.message.dto.VotePollRequest;
import com.alex.messenger.shared.CurrentUser;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final MessageTranslationService messageTranslationService;

    @PostMapping
    public ResponseEntity<ChatMessageResponse> sendMessage(@Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(messageService.sendMessage(CurrentUser.id(), request));
    }

    @PostMapping("/send-when-online")
    public ResponseEntity<ScheduledMessageResponse> sendWhenOnline(@Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(messageService.sendWhenOnline(CurrentUser.id(), request));
    }

    @PostMapping("/scheduled")
    public ResponseEntity<ScheduledMessageResponse> scheduleMessage(@Valid @RequestBody ScheduleMessageRequest request) {
        return ResponseEntity.ok(messageService.scheduleMessage(CurrentUser.id(), request));
    }

    @PostMapping("/repeating")
    public ResponseEntity<RepeatingMessageResponse> createRepeatingMessage(
            @Valid @RequestBody CreateRepeatingMessageRequest request
    ) {
        return ResponseEntity.ok(messageService.scheduleRepeatingMessage(CurrentUser.id(), request));
    }

    @PostMapping("/poll")
    public ResponseEntity<ChatMessageResponse> sendPoll(@Valid @RequestBody CreatePollMessageRequest request) {
        return ResponseEntity.ok(messageService.sendPollMessage(CurrentUser.id(), request));
    }

    @PostMapping("/forward")
    public ResponseEntity<ChatMessageResponse> forwardMessage(@Valid @RequestBody ForwardMessageRequest request) {
        return ResponseEntity.ok(messageService.forwardMessage(CurrentUser.id(), request));
    }

    @PostMapping("/inline-bot-result")
    public ResponseEntity<ChatMessageResponse> sendInlineBotResult(
            @Valid @RequestBody SendInlineBotResultRequest request
    ) {
        return ResponseEntity.ok(messageService.sendInlineBotResult(CurrentUser.id(), request));
    }

    @PostMapping("/{messageId}/poll/vote")
    public ResponseEntity<ChatMessageResponse> votePoll(
            @PathVariable UUID messageId,
            @RequestBody VotePollRequest request
    ) {
        return ResponseEntity.ok(messageService.votePoll(CurrentUser.id(), messageId, request));
    }

    @PostMapping("/{messageId}/poll/close")
    public ResponseEntity<ChatMessageResponse> closePoll(@PathVariable UUID messageId) {
        return ResponseEntity.ok(messageService.closePoll(CurrentUser.id(), messageId));
    }

    @PostMapping("/{messageId}/reactions")
    public ResponseEntity<ChatMessageResponse> toggleReaction(
            @PathVariable UUID messageId,
            @Valid @RequestBody ToggleReactionRequest request
    ) {
        return ResponseEntity.ok(messageService.toggleReaction(CurrentUser.id(), messageId, request.emoji()));
    }

    @PatchMapping("/{messageId}")
    public ResponseEntity<ChatMessageResponse> editMessage(
            @PathVariable UUID messageId,
            @Valid @RequestBody EditMessageRequest request
    ) {
        return ResponseEntity.ok(messageService.editMessage(CurrentUser.id(), messageId, request));
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<ChatMessageResponse> deleteMessage(@PathVariable UUID messageId) {
        return ResponseEntity.ok(messageService.deleteMessage(CurrentUser.id(), messageId));
    }

    @GetMapping("/chat/{chatId}")
    public ResponseEntity<List<ChatMessageResponse>> getHistory(
            @PathVariable UUID chatId,
            @RequestParam(required = false) UUID topicId,
            @RequestParam(required = false) UUID threadRootMessageId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(
                messageService.getHistory(CurrentUser.id(), chatId, topicId, threadRootMessageId, before, limit)
        );
    }

    @GetMapping("/chat/{chatId}/scheduled")
    public ResponseEntity<List<ScheduledMessageResponse>> getScheduledMessages(
            @PathVariable UUID chatId,
            @RequestParam(required = false) UUID topicId,
            @RequestParam(required = false) UUID threadRootMessageId
    ) {
        return ResponseEntity.ok(
                messageService.getScheduledMessages(CurrentUser.id(), chatId, topicId, threadRootMessageId)
        );
    }

    @GetMapping("/chat/{chatId}/search")
    public ResponseEntity<SearchMessagesResponse> searchMessages(
            @PathVariable UUID chatId,
            @RequestParam(required = false) UUID topicId,
            @RequestParam(required = false) UUID threadRootMessageId,
            @RequestParam String query,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(
                messageService.searchMessages(CurrentUser.id(), chatId, topicId, threadRootMessageId, query, limit)
        );
    }

    @GetMapping("/{messageId}")
    public ResponseEntity<ChatMessageResponse> getMessage(@PathVariable UUID messageId) {
        return ResponseEntity.ok(messageService.getMessage(CurrentUser.id(), messageId));
    }

    @PostMapping("/{messageId}/translate")
    public ResponseEntity<TranslatedMessageResponse> translateMessage(
            @PathVariable UUID messageId,
            @Valid @RequestBody(required = false) TranslateMessageRequest request
    ) {
        return ResponseEntity.ok(messageTranslationService.translate(CurrentUser.id(), messageId, request));
    }

    @DeleteMapping("/scheduled/{scheduledMessageId}")
    public ResponseEntity<Void> cancelScheduledMessage(@PathVariable UUID scheduledMessageId) {
        messageService.cancelScheduledMessage(CurrentUser.id(), scheduledMessageId);
        return ResponseEntity.noContent().build();
    }
}
