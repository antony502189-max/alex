package com.alex.messenger.chat.folder;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ChatFolderExcludedItemId implements Serializable {

    @Column(name = "folder_id", nullable = false)
    private UUID folderId;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ChatFolderExcludedItemId other)) {
            return false;
        }
        return Objects.equals(folderId, other.folderId) && Objects.equals(chatId, other.chatId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(folderId, chatId);
    }
}
