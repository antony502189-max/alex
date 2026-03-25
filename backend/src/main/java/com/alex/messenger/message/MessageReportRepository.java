package com.alex.messenger.message;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReportRepository extends JpaRepository<MessageReportEntity, UUID> {
}
