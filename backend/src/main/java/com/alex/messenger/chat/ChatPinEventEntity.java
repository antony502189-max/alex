package com.alex.messenger.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "chat_pin_events")
public class ChatPinEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "pinned_by_user_id", nullable = false)
    private UUID pinnedByUserId;

    @Column(name = "pinned_at", nullable = false)
    private Instant pinnedAt;

    @Column(name = "active", nullable = false)
    private Boolean active = false;

    @Column(name = "unpinned_at")
    private Instant unpinnedAt;
}
