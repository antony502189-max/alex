package com.alex.messenger.business;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "business_chat_automation_state")
public class BusinessChatAutomationStateEntity {

    @EmbeddedId
    private BusinessChatAutomationStateId id;

    @jakarta.persistence.Column(name = "first_customer_message_at")
    private Instant firstCustomerMessageAt;

    @jakarta.persistence.Column(name = "last_customer_message_at")
    private Instant lastCustomerMessageAt;

    @jakarta.persistence.Column(name = "last_greeting_sent_at")
    private Instant lastGreetingSentAt;

    @jakarta.persistence.Column(name = "last_away_sent_at")
    private Instant lastAwaySentAt;

    @jakarta.persistence.Column(name = "last_auto_response_at")
    private Instant lastAutoResponseAt;

    @jakarta.persistence.Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @jakarta.persistence.Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
