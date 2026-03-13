package com.alex.messenger.poll;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollRepository extends JpaRepository<PollEntity, UUID> {
}
