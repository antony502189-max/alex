package com.alex.messenger.chat.folder;

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
@Table(name = "chat_folders")
public class ChatFolderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "title", nullable = false, length = 64)
    private String title;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "included_chat_types", length = 128)
    private String includedChatTypes;

    @Column(name = "include_contacts", nullable = false)
    private Boolean includeContacts;

    @Column(name = "include_non_contacts", nullable = false)
    private Boolean includeNonContacts;

    @Column(name = "include_bots", nullable = false)
    private Boolean includeBots;

    @Column(name = "include_read", nullable = false)
    private Boolean includeRead;

    @Column(name = "include_unread", nullable = false)
    private Boolean includeUnread;

    @Column(name = "include_muted", nullable = false)
    private Boolean includeMuted;

    @Column(name = "include_unmuted", nullable = false)
    private Boolean includeUnmuted;

    @Column(name = "include_archived", nullable = false)
    private Boolean includeArchived;

    @Column(name = "include_non_archived", nullable = false)
    private Boolean includeNonArchived;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (position == null) {
            position = 0;
        }
        if (includeContacts == null) {
            includeContacts = false;
        }
        if (includeNonContacts == null) {
            includeNonContacts = false;
        }
        if (includeBots == null) {
            includeBots = false;
        }
        if (includeRead == null) {
            includeRead = false;
        }
        if (includeUnread == null) {
            includeUnread = false;
        }
        if (includeMuted == null) {
            includeMuted = false;
        }
        if (includeUnmuted == null) {
            includeUnmuted = false;
        }
        if (includeArchived == null) {
            includeArchived = false;
        }
        if (includeNonArchived == null) {
            includeNonArchived = false;
        }
    }
}
