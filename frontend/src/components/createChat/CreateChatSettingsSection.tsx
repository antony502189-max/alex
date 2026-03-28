import React from "react";
import { StyleSheet, View } from "react-native";
import { appSpacing } from "../../theme/tokens";
import type { CreateChatMode } from "./createChatPresentation";
import {
  buildCollectionAboutPlaceholder,
  buildCollectionTitlePlaceholder
} from "./createChatPresentation";
import { AppTextField } from "../ui/AppTextField";
import { AppToggleCard } from "../ui/AppToggleCard";
import { SectionCard } from "../ui/SectionCard";

type CreateChatSettingsSectionProps = {
  autoDeleteSeconds: string;
  forumEnabled: boolean;
  groupAbout: string;
  groupTitle: string;
  joinRequiresApproval: boolean;
  mode: Exclude<CreateChatMode, "direct">;
  onAutoDeleteSecondsChange: (value: string) => void;
  onForumEnabledChange: (value: boolean) => void;
  onGroupAboutChange: (value: string) => void;
  onGroupTitleChange: (value: string) => void;
  onJoinRequiresApprovalChange: (value: boolean) => void;
};

export function CreateChatSettingsSection({
  autoDeleteSeconds,
  forumEnabled,
  groupAbout,
  groupTitle,
  joinRequiresApproval,
  mode,
  onAutoDeleteSecondsChange,
  onForumEnabledChange,
  onGroupAboutChange,
  onGroupTitleChange,
  onJoinRequiresApprovalChange
}: CreateChatSettingsSectionProps) {
  return (
    <SectionCard
      description="Finish the collection chat settings before you create it."
      title={mode === "channel" ? "Channel settings" : "Group settings"}
    >
      <View style={styles.stack}>
        <AppTextField
          onChangeText={onGroupTitleChange}
          placeholder={buildCollectionTitlePlaceholder(mode)}
          value={groupTitle}
        />
        <AppTextField
          multiline
          onChangeText={onGroupAboutChange}
          placeholder={buildCollectionAboutPlaceholder(mode)}
          value={groupAbout}
        />
        <AppTextField
          keyboardType="number-pad"
          onChangeText={onAutoDeleteSecondsChange}
          placeholder="Auto-delete seconds (optional)"
          value={autoDeleteSeconds}
        />
        {mode === "group" ? (
          <AppToggleCard
            active={forumEnabled}
            description="Split the group into Telegram-style forum threads."
            onPress={() => onForumEnabledChange(!forumEnabled)}
            title="Enable topics"
          />
        ) : null}
        <AppToggleCard
          active={joinRequiresApproval}
          description="New members will wait for admin approval before entering this chat."
          onPress={() => onJoinRequiresApprovalChange(!joinRequiresApproval)}
          title="Join requests"
        />
      </View>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  stack: {
    gap: appSpacing.md
  }
});
