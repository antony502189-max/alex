package com.alex.messenger.bot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
@Table(name = "bot_callback_queries")
public class BotCallbackQueryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bot_user_id", nullable = false)
    private UUID botUserId;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "from_user_id", nullable = false)
    private UUID fromUserId;

    @Column(name = "action_id")
    private UUID actionId;

    @Column(name = "callback_data", nullable = false, length = 255)
    private String callbackData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "answer_text", length = 255)
    private String answerText;

    @Column(name = "show_alert", nullable = false)
    private boolean showAlert;

    @Column(name = "redirect_url", length = 512)
    private String redirectUrl;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
