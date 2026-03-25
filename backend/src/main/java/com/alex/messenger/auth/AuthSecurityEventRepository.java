package com.alex.messenger.auth;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSecurityEventRepository extends JpaRepository<AuthSecurityEventEntity, UUID> {

    List<AuthSecurityEventEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
