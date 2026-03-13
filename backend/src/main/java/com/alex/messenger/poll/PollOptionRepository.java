package com.alex.messenger.poll;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollOptionRepository extends JpaRepository<PollOptionEntity, UUID> {

    List<PollOptionEntity> findAllByPollIdOrderByPositionAsc(UUID pollId);
}
