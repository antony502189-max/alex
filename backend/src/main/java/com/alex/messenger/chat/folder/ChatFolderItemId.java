package com.alex.messenger.chat.folder;

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
public class ChatFolderItemId implements Serializable {

    @Column(name = "folder_id", nullable = false)
    private UUID folderId;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    public ChatFolderItemId(UUID folderId, UUID chatId) {
        this.folderId = folderId;
        this.chatId = chatId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ChatFolderItemId that)) {
            return false;
        }
        return Objects.equals(folderId, that.folderId)
                && Objects.equals(chatId, that.chatId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(folderId, chatId);
    }
}
