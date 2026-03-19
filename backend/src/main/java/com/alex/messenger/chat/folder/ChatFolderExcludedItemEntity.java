package com.alex.messenger.chat.folder;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "chat_folder_excluded_items")
public class ChatFolderExcludedItemEntity {

    @EmbeddedId
    private ChatFolderExcludedItemId id;
}
