package com.alex.messenger.checklist;

import com.alex.messenger.checklist.dto.ChecklistResponse;
import com.alex.messenger.checklist.dto.ChecklistTaskResponse;
import com.alex.messenger.checklist.dto.CreateChecklistRequest;
import com.alex.messenger.checklist.dto.CreateChecklistTaskRequest;
import com.alex.messenger.checklist.dto.UpdateChecklistRequest;
import com.alex.messenger.checklist.dto.UpdateChecklistTaskRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checklists")
@RequiredArgsConstructor
public class ChecklistController {

    private final ChecklistService checklistService;

    @GetMapping
    public ResponseEntity<List<ChecklistResponse>> listChecklists(
            @RequestParam UUID chatId,
            @RequestParam(required = false) UUID topicId,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        return ResponseEntity.ok(
                checklistService.listChecklists(CurrentUser.id(), chatId, topicId, includeArchived)
        );
    }

    @PostMapping
    public ResponseEntity<ChecklistResponse> createChecklist(@Valid @RequestBody CreateChecklistRequest request) {
        return ResponseEntity.ok(checklistService.createChecklist(CurrentUser.id(), request));
    }

    @PatchMapping("/{checklistId}")
    public ResponseEntity<ChecklistResponse> updateChecklist(
            @PathVariable UUID checklistId,
            @Valid @RequestBody UpdateChecklistRequest request
    ) {
        return ResponseEntity.ok(checklistService.updateChecklist(CurrentUser.id(), checklistId, request));
    }

    @PostMapping("/{checklistId}/tasks")
    public ResponseEntity<ChecklistTaskResponse> addTask(
            @PathVariable UUID checklistId,
            @Valid @RequestBody CreateChecklistTaskRequest request
    ) {
        return ResponseEntity.ok(checklistService.addTask(CurrentUser.id(), checklistId, request));
    }

    @PatchMapping("/{checklistId}/tasks/{taskId}")
    public ResponseEntity<ChecklistTaskResponse> updateTask(
            @PathVariable UUID checklistId,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateChecklistTaskRequest request
    ) {
        return ResponseEntity.ok(checklistService.updateTask(CurrentUser.id(), checklistId, taskId, request));
    }

    @DeleteMapping("/{checklistId}/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable UUID checklistId,
            @PathVariable UUID taskId
    ) {
        checklistService.deleteTask(CurrentUser.id(), checklistId, taskId);
        return ResponseEntity.noContent().build();
    }
}
