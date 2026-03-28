import React from "react";
import { StyleSheet } from "react-native";
import { CreateChatResultsList } from "./CreateChatResultsList";
import { CreateChatSettingsSection } from "./CreateChatSettingsSection";
import {
  buildCreateChatSubmitLabel,
  buildCreateChatSubtitle,
  buildCreateChatTitle,
  type CreateChatMode
} from "./createChatPresentation";
import type { CreateChatScreenController } from "./useCreateChatController";
import { AppButton } from "../ui/AppButton";
import { AppHeader } from "../ui/AppHeader";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import { ScreenStack } from "../ui/ScreenStack";
import { AppTextField } from "../ui/AppTextField";
import { appSpacing } from "../../theme/tokens";

type CreateChatScreenContentProps = {
  controller: CreateChatScreenController;
  mode: CreateChatMode;
  onClose: () => void;
};

export function CreateChatScreenContent({
  controller,
  mode,
  onClose
}: CreateChatScreenContentProps) {
  return (
    <>
      <AppHeader
        onBack={onClose}
        subtitle={buildCreateChatSubtitle(mode)}
        title={buildCreateChatTitle(mode)}
      />

      {mode !== "direct" ? (
        <CreateChatSettingsSection
          autoDeleteSeconds={controller.autoDeleteSeconds}
          forumEnabled={controller.forumEnabled}
          groupAbout={controller.groupAbout}
          groupTitle={controller.groupTitle}
          joinRequiresApproval={controller.joinRequiresApproval}
          mode={mode}
          onAutoDeleteSecondsChange={controller.setAutoDeleteSeconds}
          onForumEnabledChange={controller.setForumEnabled}
          onGroupAboutChange={controller.setGroupAbout}
          onGroupTitleChange={controller.setGroupTitle}
          onJoinRequiresApprovalChange={controller.setJoinRequiresApproval}
        />
      ) : null}

      <AppTextField
        autoCapitalize="none"
        onChangeText={controller.setQuery}
        placeholder="Search users"
        style={styles.searchField}
        value={controller.query}
      />
      <ScreenFeedback error={controller.error} loading={controller.loading} />

      <ScreenStack flex={1}>
        <CreateChatResultsList
          loading={controller.loading}
          mode={mode}
          onSelectDirect={(userId) => void controller.handleSelectDirect(userId)}
          onToggleUser={controller.toggleUser}
          query={controller.query}
          results={controller.results}
          selectedUserIds={controller.selectedUserIds}
          submitting={controller.submitting}
        />
      </ScreenStack>

      {mode !== "direct" ? (
        <AppButton
          disabled={controller.submitting || !controller.canSubmit}
          fullWidth
          onPress={() => void controller.handleCreateCollectionChat()}
          variant="primary"
        >
          {buildCreateChatSubmitLabel(mode, controller.selectedUserIds.length, controller.submitting)}
        </AppButton>
      ) : null}
    </>
  );
}

const styles = StyleSheet.create({
  searchField: {
    marginBottom: appSpacing.md
  }
});
