import React from "react";
import { StyleSheet, View } from "react-native";
import { AppButton } from "../ui/AppButton";
import { AppTextField } from "../ui/AppTextField";
import { SectionCard } from "../ui/SectionCard";
import { appSpacing } from "../../theme/tokens";
import { buildFolderSaveLabel } from "./foldersPresentation";

type FolderEditorSectionProps = {
  canDelete: boolean;
  canSave: boolean;
  onDelete: () => void;
  onSave: () => void;
  onTitleChange: (value: string) => void;
  saving: boolean;
  title: string;
};

export function FolderEditorSection({
  canDelete,
  canSave,
  onDelete,
  onSave,
  onTitleChange,
  saving,
  title
}: FolderEditorSectionProps) {
  return (
    <SectionCard
      description="Pick a title and then choose which chats should appear inside this folder."
      title="Folder settings"
    >
      <AppTextField
        onChangeText={onTitleChange}
        placeholder="Folder title"
        value={title}
      />

      <View style={styles.actions}>
        <AppButton
          disabled={saving || !canSave}
          fullWidth
          onPress={onSave}
          variant="primary"
        >
          {buildFolderSaveLabel(saving)}
        </AppButton>
        {canDelete ? (
          <AppButton
            disabled={saving}
            fullWidth
            onPress={onDelete}
            variant="danger"
          >
            Delete folder
          </AppButton>
        ) : null}
      </View>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  actions: {
    gap: appSpacing.sm
  }
});
