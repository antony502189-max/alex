package com.alex.messenger.call;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallReactionRepository extends JpaRepository<CallReactionEntity, UUID> {

    List<CallReactionEntity> findAllByCallIdOrderByCreatedAtDesc(UUID callId, Pageable pageable);
}
