package com.alex.messenger.chat;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatReportRepository extends JpaRepository<ChatReportEntity, UUID> {
}
