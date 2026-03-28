import React from "react";
import { FolderChatList } from "./FolderChatList";
import { FolderEditorSection } from "./FolderEditorSection";
import { FoldersSelectorStrip } from "./FoldersSelectorStrip";
import type { FoldersScreenController } from "./useFoldersController";
import { AppHeader } from "../ui/AppHeader";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import { ScreenStack } from "../ui/ScreenStack";

type FoldersScreenContentProps = {
  controller: FoldersScreenController;
  onClose: () => void;
};

export function FoldersScreenContent({
  controller,
  onClose
}: FoldersScreenContentProps) {
  return (
    <>
      <AppHeader
        onBack={onClose}
        subtitle="Organize chats into custom tabs"
        title="Folders"
      />
      <ScreenFeedback error={controller.error} loading={controller.loading} />

      <FoldersSelectorStrip
        folders={controller.folders}
        onSelectFolder={controller.handleSelectFolder}
        selectedFolderId={controller.selectedFolderId}
      />

      <FolderEditorSection
        canDelete={!!controller.selectedFolderId}
        canSave={controller.canSave}
        onDelete={() => void controller.handleDelete()}
        onSave={() => void controller.handleSave()}
        onTitleChange={controller.setTitle}
        saving={controller.saving}
        title={controller.title}
      />

      <ScreenStack flex={1} marginTop="md">
        <FolderChatList
          chats={controller.chats}
          onToggleChat={controller.toggleChat}
          selectedChatIds={controller.selectedChatIds}
        />
      </ScreenStack>
    </>
  );
}
