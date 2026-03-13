package com.alex.messenger.business;

import com.alex.messenger.business.dto.AssignBusinessOperatorRequest;
import com.alex.messenger.business.dto.BusinessChatTagResponse;
import com.alex.messenger.business.dto.BusinessOperatorAssignmentResponse;
import com.alex.messenger.business.dto.BusinessProfileResponse;
import com.alex.messenger.business.dto.BusinessQuickReplyResponse;
import com.alex.messenger.business.dto.ReplaceBusinessChatTagsRequest;
import com.alex.messenger.business.dto.UpdateBusinessProfileRequest;
import com.alex.messenger.business.dto.UpsertBusinessQuickReplyRequest;
import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.message.dto.ChatMessageResponse;
import com.alex.messenger.shared.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business")
@RequiredArgsConstructor
public class BusinessController {

    private final FeatureFlagService featureFlagService;
    private final BusinessService businessService;

    @GetMapping("/profile")
    public ResponseEntity<BusinessProfileResponse> profile() {
        featureFlagService.requireBusinessEnabled();
        return ResponseEntity.ok(businessService.getProfile(CurrentUser.id()));
    }

    @PutMapping("/profile")
    public ResponseEntity<BusinessProfileResponse> updateProfile(
            @Valid @RequestBody UpdateBusinessProfileRequest request
    ) {
        featureFlagService.requireBusinessEnabled();
        return ResponseEntity.ok(businessService.updateProfile(CurrentUser.id(), request));
    }

    @GetMapping("/quick-replies")
    public ResponseEntity<List<BusinessQuickReplyResponse>> quickReplies() {
        featureFlagService.requireBusinessEnabled();
        return ResponseEntity.ok(businessService.listQuickReplies(CurrentUser.id()));
    }

    @PostMapping("/quick-replies")
    public ResponseEntity<List<BusinessQuickReplyResponse>> upsertQuickReply(
            @Valid @RequestBody UpsertBusinessQuickReplyRequest request
    ) {
        featureFlagService.requireBusinessEnabled();
        return ResponseEntity.ok(businessService.upsertQuickReply(CurrentUser.id(), request));
    }

    @DeleteMapping("/quick-replies/{quickReplyId}")
    public ResponseEntity<List<BusinessQuickReplyResponse>> deleteQuickReply(@PathVariable UUID quickReplyId) {
        featureFlagService.requireBusinessEnabled();
        return ResponseEntity.ok(businessService.deleteQuickReply(CurrentUser.id(), quickReplyId));
    }

    @PostMapping("/chats/{chatId}/quick-replies/{quickReplyId}/send")
    public ResponseEntity<ChatMessageResponse> sendQuickReply(
            @PathVariable UUID chatId,
            @PathVariable UUID quickReplyId
    ) {
        featureFlagService.requireBusinessEnabled();
        return ResponseEntity.ok(businessService.sendQuickReply(CurrentUser.id(), chatId, quickReplyId));
    }

    @GetMapping("/chats/{chatId}/tags")
    public ResponseEntity<List<BusinessChatTagResponse>> tags(@PathVariable UUID chatId) {
        featureFlagService.requireBusinessEnabled();
        return ResponseEntity.ok(businessService.listChatTags(CurrentUser.id(), chatId));
    }

    @PutMapping("/chats/{chatId}/tags")
    public ResponseEntity<List<BusinessChatTagResponse>> replaceTags(
            @PathVariable UUID chatId,
            @Valid @RequestBody ReplaceBusinessChatTagsRequest request
    ) {
        featureFlagService.requireBusinessEnabled();
        return ResponseEntity.ok(businessService.replaceChatTags(CurrentUser.id(), chatId, request));
    }

    @GetMapping("/chats/{chatId}/operator")
    public ResponseEntity<BusinessOperatorAssignmentResponse> operator(@PathVariable UUID chatId) {
        featureFlagService.requireBusinessEnabled();
        return ResponseEntity.ok(businessService.getOperatorAssignment(CurrentUser.id(), chatId));
    }

    @PutMapping("/chats/{chatId}/operator")
    public ResponseEntity<BusinessOperatorAssignmentResponse> assignOperator(
            @PathVariable UUID chatId,
            @Valid @RequestBody AssignBusinessOperatorRequest request
    ) {
        featureFlagService.requireBusinessEnabled();
        return ResponseEntity.ok(businessService.assignOperator(CurrentUser.id(), chatId, request));
    }

    @DeleteMapping("/chats/{chatId}/operator")
    public ResponseEntity<Void> clearOperator(@PathVariable UUID chatId) {
        featureFlagService.requireBusinessEnabled();
        businessService.clearOperatorAssignment(CurrentUser.id(), chatId);
        return ResponseEntity.noContent().build();
    }
}
