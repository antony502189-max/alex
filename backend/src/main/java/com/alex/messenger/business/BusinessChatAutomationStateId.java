package com.alex.messenger.business;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Embeddable
public class BusinessChatAutomationStateId implements Serializable {

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private UUID ownerUserId;

    @Column(name = "chat_id", nullable = false, updatable = false)
    private UUID chatId;

    public BusinessChatAutomationStateId(UUID ownerUserId, UUID chatId) {
        this.ownerUserId = ownerUserId;
        this.chatId = chatId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BusinessChatAutomationStateId that)) {
            return false;
        }
        return Objects.equals(ownerUserId, that.ownerUserId) && Objects.equals(chatId, that.chatId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerUserId, chatId);
    }
}
