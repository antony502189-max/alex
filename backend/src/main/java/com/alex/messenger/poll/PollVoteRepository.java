package com.alex.messenger.poll;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollVoteRepository extends JpaRepository<PollVoteEntity, PollVoteId> {

    List<PollVoteEntity> findAllByIdPollId(UUID pollId);

    void deleteAllByIdPollIdAndIdUserId(UUID pollId, UUID userId);
}
