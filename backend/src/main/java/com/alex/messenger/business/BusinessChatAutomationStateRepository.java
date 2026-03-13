package com.alex.messenger.business;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessChatAutomationStateRepository
        extends JpaRepository<BusinessChatAutomationStateEntity, BusinessChatAutomationStateId> {
}
