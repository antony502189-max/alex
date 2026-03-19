package com.alex.messenger.checklist;

import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatMemberEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.forum.ForumTopicEntity;
import com.alex.messenger.chat.forum.ForumTopicService;
import com.alex.messenger.checklist.dto.ChecklistResponse;
import com.alex.messenger.checklist.dto.ChecklistTaskResponse;
import com.alex.messenger.checklist.dto.CreateChecklistRequest;
import com.alex.messenger.checklist.dto.CreateChecklistTaskRequest;
import com.alex.messenger.checklist.dto.UpdateChecklistRequest;
import com.alex.messenger.checklist.dto.UpdateChecklistTaskRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ChecklistService {

    private final ChecklistRepository checklistRepository;
    private final ChecklistTaskRepository checklistTaskRepository;
    private final ChatService chatService;
    private final ForumTopicService forumTopicService;

    @Transactional(readOnly = true)
    public List<ChecklistResponse> listChecklists(
            UUID requesterId,
            UUID chatId,
            UUID topicId,
            boolean includeArchived
    ) {
        ChatEntity chat = chatService.getOwnedChat(requesterId, chatId);
        UUID normalizedTopicId = validateTopic(chat, requesterId, topicId, false);

        List<ChecklistEntity> checklists = normalizedTopicId != null
                ? checklistRepository.findAllByChatIdAndTopicIdOrderByUpdatedAtDescCreatedAtDesc(chatId, normalizedTopicId)
                : checklistRepository.findAllByChatIdOrderByUpdatedAtDescCreatedAtDesc(chatId);
        List<ChecklistEntity> visibleChecklists = includeArchived
                ? checklists
                : checklists.stream().filter(checklist -> !Boolean.TRUE.equals(checklist.getArchived())).toList();
        Map<UUID, List<ChecklistTaskEntity>> tasksByChecklistId = loadTasksByChecklistId(visibleChecklists);
        return visibleChecklists.stream()
                .map(checklist -> toResponse(
                        checklist,
                        tasksByChecklistId.getOrDefault(checklist.getId(), List.of())
                ))
                .toList();
    }

    @Transactional
    public ChecklistResponse createChecklist(UUID requesterId, CreateChecklistRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checklist payload is required");
        }
        ChatEntity chat = chatService.getOwnedChat(requesterId, request.chatId());
        chatService.ensureCanPost(chat, requesterId);
        UUID normalizedTopicId = validateTopic(chat, requesterId, request.topicId(), true);

        ChecklistEntity checklist = new ChecklistEntity();
        checklist.setChatId(chat.getId());
        checklist.setTopicId(normalizedTopicId);
        checklist.setTitle(normalizeTitle(request.title()));
        checklist.setDescription(normalizeDescription(request.description()));
        checklist.setCreatedByUserId(requesterId);
        checklist.setArchived(false);
        ChecklistEntity savedChecklist = checklistRepository.save(checklist);

        List<ChecklistTaskEntity> tasks = createTasks(
                savedChecklist.getId(),
                chat.getId(),
                requesterId,
                request.tasks() != null ? request.tasks() : List.of()
        );
        return toResponse(savedChecklist, tasks);
    }

    @Transactional
    public ChecklistResponse updateChecklist(UUID requesterId, UUID checklistId, UpdateChecklistRequest request) {
        ChecklistEntity checklist = getChecklist(checklistId);
        ChatEntity chat = chatService.getOwnedChat(requesterId, checklist.getChatId());
        ensureCanManageChecklist(chat, requesterId, checklist);

        if (request.title() != null) {
            checklist.setTitle(normalizeTitle(request.title()));
        }
        if (request.description() != null) {
            checklist.setDescription(normalizeDescription(request.description()));
        }
        if (request.archived() != null) {
            checklist.setArchived(request.archived());
        }

        ChecklistEntity savedChecklist = checklistRepository.save(checklist);
        List<ChecklistTaskEntity> tasks = checklistTaskRepository.findAllByChecklistIdOrderByPositionAscCreatedAtAsc(checklistId);
        return toResponse(savedChecklist, tasks);
    }

    @Transactional
    public ChecklistTaskResponse addTask(UUID requesterId, UUID checklistId, CreateChecklistTaskRequest request) {
        ChecklistEntity checklist = getChecklist(checklistId);
        ChatEntity chat = chatService.getOwnedChat(requesterId, checklist.getChatId());
        ensureChecklistWritable(chat, requesterId, checklist);

        List<ChecklistTaskEntity> tasks = new ArrayList<>(
                checklistTaskRepository.findAllByChecklistIdOrderByPositionAscCreatedAtAsc(checklistId)
        );
        ChecklistTaskEntity task = new ChecklistTaskEntity();
        task.setChecklistId(checklistId);
        task.setTaskText(normalizeTaskText(request.text()));
        task.setAssignedUserId(normalizeAssignee(chat.getId(), request.assignedUserId()));
        task.setCompleted(false);
        task.setCompletedAt(null);
        task.setCompletedByUserId(null);
        task.setCreatedByUserId(requesterId);

        int targetPosition = normalizeInsertPosition(request.position(), tasks.size());
        tasks.add(targetPosition, task);
        renumberTasks(tasks);
        List<ChecklistTaskEntity> savedTasks = checklistTaskRepository.saveAll(tasks);
        touchChecklist(checklist);
        ChecklistTaskEntity savedTask = savedTasks.stream()
                .filter(candidate -> candidate == task || Objects.equals(candidate.getId(), task.getId()))
                .findFirst()
                .orElse(task);
        return toTaskResponse(savedTask);
    }

    @Transactional
    public ChecklistTaskResponse updateTask(
            UUID requesterId,
            UUID checklistId,
            UUID taskId,
            UpdateChecklistTaskRequest request
    ) {
        ChecklistEntity checklist = getChecklist(checklistId);
        ChatEntity chat = chatService.getOwnedChat(requesterId, checklist.getChatId());
        ensureChecklistWritable(chat, requesterId, checklist);

        List<ChecklistTaskEntity> tasks = new ArrayList<>(
                checklistTaskRepository.findAllByChecklistIdOrderByPositionAscCreatedAtAsc(checklistId)
        );
        ChecklistTaskEntity target = tasks.stream()
                .filter(task -> taskId.equals(task.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist task not found"));

        if (request.text() != null) {
            target.setTaskText(normalizeTaskText(request.text()));
        }
        if (request.assignedUserId() != null) {
            target.setAssignedUserId(normalizeAssignee(chat.getId(), request.assignedUserId()));
        } else if (Boolean.TRUE.equals(request.clearAssignee())) {
            target.setAssignedUserId(null);
        }
        if (request.completed() != null) {
            applyCompletionState(target, requesterId, request.completed());
        }
        if (request.position() != null) {
            tasks.remove(target);
            tasks.add(normalizeInsertPosition(request.position(), tasks.size()), target);
        }

        renumberTasks(tasks);
        List<ChecklistTaskEntity> savedTasks = checklistTaskRepository.saveAll(tasks);
        touchChecklist(checklist);
        ChecklistTaskEntity savedTask = savedTasks.stream()
                .filter(task -> taskId.equals(task.getId()))
                .findFirst()
                .orElse(target);
        return toTaskResponse(savedTask);
    }

    @Transactional
    public void deleteTask(UUID requesterId, UUID checklistId, UUID taskId) {
        ChecklistEntity checklist = getChecklist(checklistId);
        ChatEntity chat = chatService.getOwnedChat(requesterId, checklist.getChatId());
        ensureChecklistWritable(chat, requesterId, checklist);

        List<ChecklistTaskEntity> tasks = new ArrayList<>(
                checklistTaskRepository.findAllByChecklistIdOrderByPositionAscCreatedAtAsc(checklistId)
        );
        ChecklistTaskEntity target = tasks.stream()
                .filter(task -> taskId.equals(task.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist task not found"));

        tasks.remove(target);
        checklistTaskRepository.delete(target);
        if (!tasks.isEmpty()) {
            renumberTasks(tasks);
            checklistTaskRepository.saveAll(tasks);
        }
        touchChecklist(checklist);
    }

    private List<ChecklistTaskEntity> createTasks(
            UUID checklistId,
            UUID chatId,
            UUID requesterId,
            List<CreateChecklistTaskRequest> requests
    ) {
        if (requests.isEmpty()) {
            return List.of();
        }
        if (requests.stream().anyMatch(Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checklist tasks must not contain null");
        }

        List<ChecklistTaskEntity> tasks = new ArrayList<>();
        for (CreateChecklistTaskRequest request : requests) {
            ChecklistTaskEntity task = new ChecklistTaskEntity();
            task.setChecklistId(checklistId);
            task.setTaskText(normalizeTaskText(request.text()));
            task.setAssignedUserId(normalizeAssignee(chatId, request.assignedUserId()));
            task.setCompleted(false);
            task.setCompletedAt(null);
            task.setCompletedByUserId(null);
            task.setCreatedByUserId(requesterId);
            tasks.add(normalizeInsertPosition(request.position(), tasks.size()), task);
        }
        renumberTasks(tasks);
        return checklistTaskRepository.saveAll(tasks);
    }

    private void ensureChecklistWritable(ChatEntity chat, UUID requesterId, ChecklistEntity checklist) {
        if (Boolean.TRUE.equals(checklist.getArchived())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checklist is archived");
        }
        chatService.ensureCanPost(chat, requesterId);
    }

    private void ensureCanManageChecklist(ChatEntity chat, UUID requesterId, ChecklistEntity checklist) {
        if (requesterId.equals(checklist.getCreatedByUserId())) {
            return;
        }

        if (List.of("DIRECT", "SAVED").contains(chat.getChatType())) {
            return;
        }

        ChatMemberEntity membership = chatService.getMembership(chat.getId(), requesterId);
        if (List.of("OWNER", "ADMIN").contains(membership.getRole())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only checklist creator or chat admins can manage checklist");
    }

    private ChecklistEntity touchChecklist(ChecklistEntity checklist) {
        checklist.setUpdatedAt(Instant.now());
        return checklistRepository.save(checklist);
    }

    private void applyCompletionState(ChecklistTaskEntity task, UUID requesterId, boolean completed) {
        if (completed) {
            task.setCompleted(true);
            task.setCompletedAt(Instant.now());
            task.setCompletedByUserId(requesterId);
            return;
        }
        task.setCompleted(false);
        task.setCompletedAt(null);
        task.setCompletedByUserId(null);
    }

    private UUID validateTopic(ChatEntity chat, UUID requesterId, UUID topicId, boolean write) {
        if (topicId == null) {
            return null;
        }
        ForumTopicEntity topic = write
                ? forumTopicService.resolveTopicForWrite(chat, requesterId, topicId)
                : forumTopicService.resolveTopicForRead(chat, requesterId, topicId);
        return topic != null ? topic.getId() : null;
    }

    private Map<UUID, List<ChecklistTaskEntity>> loadTasksByChecklistId(List<ChecklistEntity> checklists) {
        if (checklists.isEmpty()) {
            return Map.of();
        }
        Collection<UUID> checklistIds = checklists.stream().map(ChecklistEntity::getId).toList();
        Map<UUID, List<ChecklistTaskEntity>> tasksByChecklistId = new LinkedHashMap<>();
        for (ChecklistTaskEntity task : checklistTaskRepository
                .findAllByChecklistIdInOrderByChecklistIdAscPositionAscCreatedAtAsc(checklistIds)) {
            tasksByChecklistId.computeIfAbsent(task.getChecklistId(), ignored -> new ArrayList<>()).add(task);
        }
        return tasksByChecklistId;
    }

    private ChecklistEntity getChecklist(UUID checklistId) {
        return checklistRepository.findById(checklistId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist not found"));
    }

    private UUID normalizeAssignee(UUID chatId, UUID assignedUserId) {
        if (assignedUserId == null) {
            return null;
        }
        chatService.getMembership(chatId, assignedUserId);
        return assignedUserId;
    }

    private int normalizeInsertPosition(Integer requestedPosition, int size) {
        if (requestedPosition == null) {
            return size;
        }
        if (requestedPosition < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checklist task position must be positive");
        }
        return Math.min(requestedPosition, size);
    }

    private void renumberTasks(List<ChecklistTaskEntity> tasks) {
        for (int index = 0; index < tasks.size(); index++) {
            tasks.get(index).setPosition(index);
        }
    }

    private String normalizeTitle(String value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checklist title is required");
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checklist title is required");
        }
        return normalized;
    }

    private String normalizeDescription(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeTaskText(String value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checklist task text is required");
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checklist task text is required");
        }
        return normalized;
    }

    private ChecklistResponse toResponse(ChecklistEntity checklist, List<ChecklistTaskEntity> tasks) {
        List<ChecklistTaskResponse> taskResponses = tasks.stream()
                .map(this::toTaskResponse)
                .toList();
        int completedTaskCount = (int) tasks.stream()
                .filter(task -> Boolean.TRUE.equals(task.getCompleted()))
                .count();
        return new ChecklistResponse(
                checklist.getId(),
                checklist.getChatId(),
                checklist.getTopicId(),
                checklist.getTitle(),
                checklist.getDescription(),
                Boolean.TRUE.equals(checklist.getArchived()),
                checklist.getCreatedByUserId(),
                checklist.getCreatedAt(),
                checklist.getUpdatedAt(),
                taskResponses.size(),
                completedTaskCount,
                taskResponses
        );
    }

    private ChecklistTaskResponse toTaskResponse(ChecklistTaskEntity task) {
        return new ChecklistTaskResponse(
                task.getId(),
                task.getTaskText(),
                task.getPosition(),
                task.getAssignedUserId(),
                Boolean.TRUE.equals(task.getCompleted()),
                task.getCompletedAt(),
                task.getCompletedByUserId(),
                task.getCreatedByUserId(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
