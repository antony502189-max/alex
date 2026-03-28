import React from "react";
import { StyleSheet, View } from "react-native";
import { AppButton } from "../ui/AppButton";
import { AppTextField } from "../ui/AppTextField";
import { SectionCard } from "../ui/SectionCard";
import { appSpacing } from "../../theme/tokens";

type ForumTopicComposerProps = {
  iconEmoji: string;
  onCreateTopic: () => void;
  onIconEmojiChange: (value: string) => void;
  onTitleChange: (value: string) => void;
  saving: boolean;
  title: string;
};

export function ForumTopicComposer({
  iconEmoji,
  onCreateTopic,
  onIconEmojiChange,
  onTitleChange,
  saving,
  title
}: ForumTopicComposerProps) {
  return (
    <SectionCard
      description="Create a new topic for Telegram-style forum threads in this group."
      title="New topic"
    >
      <View style={styles.row}>
        <AppTextField
          onChangeText={onTitleChange}
          placeholder="Topic title"
          style={styles.titleInput}
          value={title}
        />
        <AppTextField
          autoCapitalize="none"
          onChangeText={onIconEmojiChange}
          placeholder="Emoji"
          style={styles.emojiInput}
          value={iconEmoji}
        />
      </View>
      <AppButton
        disabled={saving || !title.trim()}
        fullWidth
        onPress={onCreateTopic}
        variant="primary"
      >
        {saving ? "..." : "Create topic"}
      </AppButton>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    gap: appSpacing.sm
  },
  titleInput: {
    flex: 1
  },
  emojiInput: {
    width: 88
  }
});
