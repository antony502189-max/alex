package com.alex.messenger.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.ChatEntity;
import com.alex.messenger.chat.ChatService;
import com.alex.messenger.chat.forum.ForumTopicService;
import com.alex.messenger.checklist.dto.ChecklistResponse;
import com.alex.messenger.checklist.dto.ChecklistTaskResponse;
import com.alex.messenger.checklist.dto.CreateChecklistRequest;
import com.alex.messenger.checklist.dto.CreateChecklistTaskRequest;
import com.alex.messenger.checklist.dto.UpdateChecklistTaskRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ChecklistServiceTest {

    @Mock
    private ChecklistRepository checklistRepository;

    @Mock
    private ChecklistTaskRepository checklistTaskRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private ForumTopicService forumTopicService;

    private ChecklistService checklistService;

    @BeforeEach
    void setUp() {
        checklistService = new ChecklistService(
                checklistRepository,
                checklistTaskRepository,
                chatService,
                forumTopicService
        );
    }

    @Test
    void createChecklistPersistsOrderedTasksAndReturnsSummary() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID checklistId = UUID.randomUUID();
        UUID firstTaskId = UUID.randomUUID();
        UUID secondTaskId = UUID.randomUUID();
        ChatEntity chat = chat(chatId, "GROUP");

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(checklistRepository.save(any(ChecklistEntity.class))).thenAnswer(invocation -> {
            ChecklistEntity checklist = invocation.getArgument(0);
            checklist.setId(checklistId);
            checklist.setCreatedAt(Instant.parse("2026-03-19T12:00:00Z"));
            checklist.setUpdatedAt(Instant.parse("2026-03-19T12:00:00Z"));
            return checklist;
        });
        when(checklistTaskRepository.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<ChecklistTaskEntity> tasks = invocation.getArgument(0);
            for (int index = 0; index < tasks.size(); index++) {
                ChecklistTaskEntity task = tasks.get(index);
                task.setId(index == 0 ? firstTaskId : secondTaskId);
                task.setCreatedAt(Instant.parse("2026-03-19T12:05:00Z").plusSeconds(index));
                task.setUpdatedAt(Instant.parse("2026-03-19T12:05:00Z").plusSeconds(index));
            }
            return tasks;
        });

        ChecklistResponse response = checklistService.createChecklist(
                requesterId,
                new CreateChecklistRequest(
                        chatId,
                        null,
                        " Sprint checklist ",
                        " ",
                        List.of(
                                new CreateChecklistTaskRequest("Second task", null, null),
                                new CreateChecklistTaskRequest("First task", null, 0)
                        )
                )
        );

        assertThat(response.checklistId()).isEqualTo(checklistId);
        assertThat(response.title()).isEqualTo("Sprint checklist");
        assertThat(response.description()).isNull();
        assertThat(response.taskCount()).isEqualTo(2);
        assertThat(response.completedTaskCount()).isZero();
        assertThat(response.tasks())
                .extracting(ChecklistTaskResponse::text)
                .containsExactly("First task", "Second task");
        assertThat(response.tasks())
                .extracting(ChecklistTaskResponse::position)
                .containsExactly(0, 1);
    }

    @Test
    void createChecklistRejectsMissingRequest() {
        ResponseStatusException exception = catchThrowableOfType(
                () -> checklistService.createChecklist(UUID.randomUUID(), null),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("Checklist payload is required");
    }

    @Test
    void createChecklistRejectsNullTaskEntry() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        ChatEntity chat = chat(chatId, "GROUP");

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(checklistRepository.save(any(ChecklistEntity.class))).thenAnswer(invocation -> {
            ChecklistEntity checklist = invocation.getArgument(0);
            checklist.setId(UUID.randomUUID());
            return checklist;
        });

        ResponseStatusException exception = catchThrowableOfType(
                () -> checklistService.createChecklist(
                        requesterId,
                        new CreateChecklistRequest(chatId, null, "Checklist", null, java.util.Arrays.asList((CreateChecklistTaskRequest) null))
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("Checklist tasks must not contain null");
    }

    @Test
    void updateTaskCompletesAndReordersTask() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID checklistId = UUID.randomUUID();
        UUID targetTaskId = UUID.randomUUID();
        UUID firstTaskId = UUID.randomUUID();
        UUID lastTaskId = UUID.randomUUID();
        ChatEntity chat = chat(chatId, "GROUP");
        ChecklistEntity checklist = checklist(checklistId, chatId, requesterId, false);
        ChecklistTaskEntity firstTask = task(firstTaskId, checklistId, "First", 0);
        ChecklistTaskEntity targetTask = task(targetTaskId, checklistId, "Middle", 1);
        ChecklistTaskEntity lastTask = task(lastTaskId, checklistId, "Last", 2);

        when(checklistRepository.findById(checklistId)).thenReturn(Optional.of(checklist));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(checklistTaskRepository.findAllByChecklistIdOrderByPositionAscCreatedAtAsc(checklistId))
                .thenReturn(new ArrayList<>(List.of(firstTask, targetTask, lastTask)));
        when(checklistTaskRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(checklistRepository.save(any(ChecklistEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChecklistTaskResponse response = checklistService.updateTask(
                requesterId,
                checklistId,
                targetTaskId,
                new UpdateChecklistTaskRequest(" Done ", true, null, null, 0)
        );

        ArgumentCaptor<List<ChecklistTaskEntity>> tasksCaptor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(checklistTaskRepository).saveAll(tasksCaptor.capture());
        assertThat(tasksCaptor.getValue())
                .extracting(ChecklistTaskEntity::getId)
                .containsExactly(targetTaskId, firstTaskId, lastTaskId);
        assertThat(tasksCaptor.getValue())
                .extracting(ChecklistTaskEntity::getPosition)
                .containsExactly(0, 1, 2);
        assertThat(response.text()).isEqualTo("Done");
        assertThat(response.completed()).isTrue();
        assertThat(response.position()).isEqualTo(0);
        assertThat(response.completedByUserId()).isEqualTo(requesterId);
        assertThat(response.completedAt()).isNotNull();
    }

    @Test
    void addTaskRejectsArchivedChecklist() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID checklistId = UUID.randomUUID();
        ChatEntity chat = chat(chatId, "GROUP");
        ChecklistEntity checklist = checklist(checklistId, chatId, requesterId, true);

        when(checklistRepository.findById(checklistId)).thenReturn(Optional.of(checklist));
        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);

        ResponseStatusException exception = catchThrowableOfType(
                () -> checklistService.addTask(
                        requesterId,
                        checklistId,
                        new CreateChecklistTaskRequest("Blocked", null, null)
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createChecklistRejectsAssigneeOutsideChat() {
        UUID requesterId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();
        ChatEntity chat = chat(chatId, "GROUP");

        when(chatService.getOwnedChat(requesterId, chatId)).thenReturn(chat);
        when(checklistRepository.save(any(ChecklistEntity.class))).thenAnswer(invocation -> {
            ChecklistEntity checklist = invocation.getArgument(0);
            checklist.setId(UUID.randomUUID());
            checklist.setCreatedAt(Instant.parse("2026-03-19T12:00:00Z"));
            checklist.setUpdatedAt(Instant.parse("2026-03-19T12:00:00Z"));
            return checklist;
        });
        when(chatService.getMembership(chatId, outsiderId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Chat access denied"));

        ResponseStatusException exception = catchThrowableOfType(
                () -> checklistService.createChecklist(
                        requesterId,
                        new CreateChecklistRequest(
                                chatId,
                                null,
                                "Checklist",
                                null,
                                List.of(new CreateChecklistTaskRequest("Task", outsiderId, null))
                        )
                ),
                ResponseStatusException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ChatEntity chat(UUID chatId, String chatType) {
        ChatEntity chat = new ChatEntity();
        chat.setId(chatId);
        chat.setChatType(chatType);
        chat.setCreatedBy(UUID.randomUUID());
        chat.setCreatedAt(Instant.parse("2026-03-19T10:00:00Z"));
        return chat;
    }

    private ChecklistEntity checklist(UUID checklistId, UUID chatId, UUID creatorId, boolean archived) {
        ChecklistEntity checklist = new ChecklistEntity();
        checklist.setId(checklistId);
        checklist.setChatId(chatId);
        checklist.setTitle("Checklist");
        checklist.setCreatedByUserId(creatorId);
        checklist.setArchived(archived);
        checklist.setCreatedAt(Instant.parse("2026-03-19T10:00:00Z"));
        checklist.setUpdatedAt(Instant.parse("2026-03-19T10:00:00Z"));
        return checklist;
    }

    private ChecklistTaskEntity task(UUID taskId, UUID checklistId, String text, int position) {
        ChecklistTaskEntity task = new ChecklistTaskEntity();
        task.setId(taskId);
        task.setChecklistId(checklistId);
        task.setTaskText(text);
        task.setPosition(position);
        task.setCompleted(false);
        task.setCreatedByUserId(UUID.randomUUID());
        task.setCreatedAt(Instant.parse("2026-03-19T11:00:00Z").plusSeconds(position));
        task.setUpdatedAt(Instant.parse("2026-03-19T11:00:00Z").plusSeconds(position));
        return task;
    }
}
