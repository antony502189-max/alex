import React from "react";
import { Image, StyleSheet, Text, View } from "react-native";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { Story, StoryFeedItem, StoryViewer } from "../../types";
import { AppButton } from "../ui/AppButton";
import { SectionCard } from "../ui/SectionCard";
import {
  buildStoryLifecycleLabel,
  buildStoryMediaSummary,
  resolveStoryMediaPreviewUri,
  buildStoryVideoSummary,
  buildStoryViewerMeta,
  formatStoryAudience
} from "./storiesPresentation";

type StoriesSelectedStoryViewProps = {
  deletingStoryId: string | null;
  loadingViewers: boolean;
  onDeleteStory: (story: Story) => Promise<void> | void;
  onGoToRelativeStory: (offset: number) => void;
  onLoadViewers: (story: Story) => Promise<void> | void;
  selectedFeedItem: StoryFeedItem | null;
  selectedStory: Story;
  selectedStoryIndex: number;
  viewers: StoryViewer[];
};

export function StoriesSelectedStoryView({
  deletingStoryId,
  loadingViewers,
  onDeleteStory,
  onGoToRelativeStory,
  onLoadViewers,
  selectedFeedItem,
  selectedStory,
  selectedStoryIndex,
  viewers
}: StoriesSelectedStoryViewProps) {
  const canGoPrevious = selectedStoryIndex > 0;
  const canGoNext = Boolean(
    selectedFeedItem && selectedStoryIndex < selectedFeedItem.stories.length - 1
  );
  const mediaSummary = buildStoryMediaSummary(selectedStory);
  const storyImageUri = resolveStoryMediaPreviewUri(selectedStory);
  const videoSummary = buildStoryVideoSummary(selectedStory);

  return (
    <>
      <View
        style={[
          styles.storyCard,
          {
            backgroundColor: selectedStory.backgroundFrom,
            borderColor: selectedStory.backgroundTo
          }
        ]}
      >
        {storyImageUri ? (
          <Image
            resizeMode="cover"
            source={{ uri: storyImageUri }}
            style={styles.storyImage}
          />
        ) : null}
        {selectedStory.media?.kind === "VIDEO" ? (
          <View
            style={[
              styles.videoPlaceholder,
              !storyImageUri && styles.videoPlaceholderFallback
            ]}
          >
            <Text style={styles.videoTitle}>Video story</Text>
            {videoSummary ? <Text style={styles.videoMeta}>{videoSummary}</Text> : null}
          </View>
        ) : null}
        <View style={styles.overlay}>
          {selectedStory.text ? (
            <Text style={[styles.storyText, { color: selectedStory.textColor }]}>
              {selectedStory.text}
            </Text>
          ) : null}
          <Text style={[styles.storyMeta, { color: selectedStory.textColor }]}>
            {selectedStory.ownerDisplayName}
          </Text>
          <Text style={[styles.storyMeta, { color: selectedStory.textColor }]}>
            {new Date(selectedStory.createdAt).toLocaleString()}
          </Text>
        </View>
      </View>

      <View style={styles.actionRow}>
        <AppButton disabled={!canGoPrevious} onPress={() => onGoToRelativeStory(-1)}>
          Previous
        </AppButton>
        <AppButton disabled={!canGoNext} onPress={() => onGoToRelativeStory(1)}>
          Next
        </AppButton>
      </View>

      <SectionCard title={`${selectedStoryIndex + 1} / ${selectedFeedItem?.stories.length ?? 1}`}>
        <Text style={styles.infoText}>Status {buildStoryLifecycleLabel(selectedStory)}</Text>
        <Text style={styles.infoText}>Expires {new Date(selectedStory.expiresAt).toLocaleString()}</Text>
        <Text style={styles.infoText}>Views {selectedStory.viewsCount}</Text>
        <Text style={styles.infoText}>Audience {formatStoryAudience(selectedStory.audience)}</Text>
        {mediaSummary ? <Text style={styles.infoText}>{mediaSummary}</Text> : null}
      </SectionCard>

      {selectedStory.own ? (
        <View style={styles.actionRow}>
          <AppButton
            disabled={loadingViewers}
            onPress={() => void onLoadViewers(selectedStory)}
          >
            {loadingViewers ? "Loading..." : "Viewers"}
          </AppButton>
          <AppButton
            disabled={deletingStoryId === selectedStory.storyId}
            onPress={() => void onDeleteStory(selectedStory)}
            variant="danger"
          >
            {deletingStoryId === selectedStory.storyId ? "Deleting..." : "Delete"}
          </AppButton>
        </View>
      ) : null}

      {viewers.length > 0 ? (
        <SectionCard title="Viewers">
          {viewers.map((viewer) => (
            <View key={`${selectedStory.storyId}-${viewer.viewerUserId}`} style={styles.viewerRow}>
              <Text style={styles.viewerName}>{viewer.displayName}</Text>
              <Text style={styles.viewerMeta}>{buildStoryViewerMeta(viewer)}</Text>
            </View>
          ))}
        </SectionCard>
      ) : null}
    </>
  );
}

const styles = StyleSheet.create({
  storyCard: {
    borderRadius: appRadii.xl,
    borderWidth: 3,
    justifyContent: "flex-end",
    minHeight: 360,
    overflow: "hidden"
  },
  storyImage: {
    ...StyleSheet.absoluteFillObject
  },
  videoPlaceholder: {
    ...StyleSheet.absoluteFillObject,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: appSpacing.xxl
  },
  videoPlaceholderFallback: {
    backgroundColor: appColors.textPrimary
  },
  videoTitle: {
    color: appColors.inverse,
    fontSize: 28,
    fontWeight: "700"
  },
  videoMeta: {
    color: "#cbd5e1",
    marginTop: appSpacing.md,
    textAlign: "center"
  },
  overlay: {
    backgroundColor: "rgba(15, 23, 42, 0.28)",
    padding: appSpacing.xxl
  },
  storyText: {
    fontSize: 30,
    fontWeight: "700",
    textAlign: "center"
  },
  storyMeta: {
    fontWeight: "600",
    marginTop: appSpacing.md,
    textAlign: "center"
  },
  actionRow: {
    flexDirection: "row",
    gap: appSpacing.md
  },
  infoText: {
    color: appColors.textSecondary
  },
  viewerRow: {
    borderTopColor: appColors.border,
    borderTopWidth: 1,
    gap: appSpacing.xs,
    paddingTop: appSpacing.md
  },
  viewerName: {
    color: appColors.textPrimary,
    fontWeight: "600"
  },
  viewerMeta: {
    color: appColors.textSecondary
  }
});
