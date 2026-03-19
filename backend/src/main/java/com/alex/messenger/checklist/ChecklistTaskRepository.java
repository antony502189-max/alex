package com.alex.messenger.checklist;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistTaskRepository extends JpaRepository<ChecklistTaskEntity, UUID> {

    List<ChecklistTaskEntity> findAllByChecklistIdOrderByPositionAscCreatedAtAsc(UUID checklistId);

    List<ChecklistTaskEntity> findAllByChecklistIdInOrderByChecklistIdAscPositionAscCreatedAtAsc(
            Collection<UUID> checklistIds
    );
}
