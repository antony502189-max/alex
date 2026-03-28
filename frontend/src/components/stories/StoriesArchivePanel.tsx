import React from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { appColors, appSpacing } from "../../theme/tokens";
import type { Story } from "../../types";
import { AppButton } from "../ui/AppButton";
import { SectionCard } from "../ui/SectionCard";
import {
  buildStoryArchiveTitle,
  buildStoryLifecycleLabel,
  formatStoryAudience
} from "./storiesPresentation";

type StoriesArchivePanelProps = {
  archive: Story[];
  deletingStoryId: string | null;
  loadingArchive: boolean;
  onDeleteStory: (story: Story) => Promise<void> | void;
  onOpenStory: (story: Story) => void;
  selectedStoryId: string | null;
};

export function StoriesArchivePanel({
  archive,
  deletingStoryId,
  loadingArchive,
  onDeleteStory,
  onOpenStory,
  selectedStoryId
}: StoriesArchivePanelProps) {
  return (
    <SectionCard
      title="My archive"
      description="Expired stories stay here until you remove them."
    >
      {loadingArchive ? <Text style={styles.meta}>Loading archive...</Text> : null}
      {!loadingArchive && archive.length === 0 ? (
        <Text style={styles.meta}>Expired stories will appear here.</Text>
      ) : null}
      {archive.map((story) => (
        <View key={story.storyId} style={styles.row}>
          <Pressable
            onPress={() => onOpenStory(story)}
            style={({ pressed }) => [
              styles.body,
              selectedStoryId === story.storyId && styles.bodySelected,
              pressed && styles.bodyPressed
            ]}
          >
            <Text style={styles.title}>{buildStoryArchiveTitle(story)}</Text>
            <Text style={styles.meta}>Status {buildStoryLifecycleLabel(story)}</Text>
            <Text style={styles.meta}>Expired {new Date(story.expiresAt).toLocaleString()}</Text>
            <Text style={styles.meta}>Audience {formatStoryAudience(story.audience)}</Text>
            {story.text ? (
              <Text numberOfLines={2} style={styles.snippet}>
                {story.text}
              </Text>
            ) : null}
          </Pressable>
          <View style={styles.actions}>
            <AppButton onPress={() => onOpenStory(story)} size="sm">
              Open
            </AppButton>
            <AppButton
              disabled={deletingStoryId === story.storyId}
              onPress={() => void onDeleteStory(story)}
              size="sm"
              variant="danger"
            >
              {deletingStoryId === story.storyId ? "..." : "Delete"}
            </AppButton>
          </View>
        </View>
      ))}
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  row: {
    alignItems: "flex-start",
    borderTopColor: appColors.border,
    borderTopWidth: 1,
    flexDirection: "row",
    gap: appSpacing.md,
    paddingTop: appSpacing.md
  },
  body: {
    flex: 1,
    borderRadius: 16,
    gap: appSpacing.xs,
    padding: appSpacing.sm
  },
  bodyPressed: {
    opacity: 0.92
  },
  bodySelected: {
    backgroundColor: appColors.surfaceMuted
  },
  actions: {
    gap: appSpacing.sm
  },
  title: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  meta: {
    color: appColors.textSecondary,
    fontSize: 12
  },
  snippet: {
    color: appColors.textSecondary
  }
});
