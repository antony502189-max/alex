import React from "react";
import { ScrollView, StyleSheet, View } from "react-native";
import type { ChatFolder } from "../../types";
import { appSpacing } from "../../theme/tokens";
import { AppChip } from "../ui/AppChip";

type FoldersSelectorStripProps = {
  folders: ChatFolder[];
  onSelectFolder: (folderId: string | null) => void;
  selectedFolderId: string | null;
};

export function FoldersSelectorStrip({
  folders,
  onSelectFolder,
  selectedFolderId
}: FoldersSelectorStripProps) {
  return (
    <ScrollView
      contentContainerStyle={styles.content}
      horizontal
      keyboardShouldPersistTaps="handled"
      showsHorizontalScrollIndicator={false}
    >
      <AppChip
        active={!selectedFolderId}
        onPress={() => onSelectFolder(null)}
        tone="brand"
      >
        New folder
      </AppChip>
      {folders.map((folder) => (
        <AppChip
          key={folder.folderId}
          active={selectedFolderId === folder.folderId}
          onPress={() => onSelectFolder(folder.folderId)}
          tone="brand"
        >
          {folder.title}
        </AppChip>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: {
    gap: appSpacing.sm,
    paddingBottom: appSpacing.xs
  }
});
