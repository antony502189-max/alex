package com.alex.messenger.call;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallParticipantRepository extends JpaRepository<CallParticipantEntity, CallParticipantId> {

    List<CallParticipantEntity> findAllByIdCallId(UUID callId);

    List<CallParticipantEntity> findAllByIdCallIdIn(Collection<UUID> callIds);

    long countByIdCallIdAndStateIn(UUID callId, Collection<String> states);

    boolean existsByIdCallIdAndIdUserId(UUID callId, UUID userId);
}
