package com.alex.messenger.call;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallCommentRepository extends JpaRepository<CallCommentEntity, UUID> {

    List<CallCommentEntity> findAllByCallIdOrderByCreatedAtDesc(UUID callId, Pageable pageable);
}
