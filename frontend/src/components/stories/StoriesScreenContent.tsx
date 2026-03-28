import React from "react";
import { StyleSheet, Text } from "react-native";
import { StoriesArchivePanel } from "./StoriesArchivePanel";
import { StoriesFeedStrip } from "./StoriesFeedStrip";
import { StoriesSelectedStoryView } from "./StoriesSelectedStoryView";
import type { StoriesScreenController } from "./useStoriesController";
import { AppButton } from "../ui/AppButton";
import { AppHeader } from "../ui/AppHeader";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import { ScreenScrollView } from "../ui/ScreenScrollView";
import { ScreenStack } from "../ui/ScreenStack";
import { SectionCard } from "../ui/SectionCard";
import { appColors } from "../../theme/tokens";

type StoriesScreenContentProps = {
  controller: StoriesScreenController;
  onClose: () => void;
  onCreateStory: () => void;
};

export function StoriesScreenContent({
  controller,
  onClose,
  onCreateStory
}: StoriesScreenContentProps) {
  const selectedDisplayStory = controller.selectedStory ?? controller.selectedArchiveStory;
  const selectedDisplayFeedItem = controller.selectedStory ? controller.selectedFeedItem : null;
  const selectedDisplayIndex = controller.selectedStory ? controller.selectedStoryIndex : 0;

  return (
    <>
      <AppHeader
        onBack={onClose}
        rightSlot={(
          <ScreenStack direction="row" gap="sm">
            <AppButton onPress={() => void controller.handleToggleArchive()} size="sm">
              {controller.showArchive ? "Hide archive" : "Archive"}
            </AppButton>
            <AppButton onPress={onCreateStory} size="sm" variant="primary">
              New story
            </AppButton>
          </ScreenStack>
        )}
        title="Stories"
      />

      <StoriesFeedStrip
        feed={controller.feed}
        onSelectOwner={controller.handleOpenOwnerStories}
        selectedOwnerId={controller.selectedOwnerId}
      />
      <ScreenFeedback error={controller.error} loading={controller.loading} />

      <ScreenScrollView gap="lg" paddingBottom="xxl">
        {selectedDisplayStory ? (
          <StoriesSelectedStoryView
            deletingStoryId={controller.deletingStoryId}
            loadingViewers={controller.loadingViewers}
            onDeleteStory={controller.handleDeleteStory}
            onGoToRelativeStory={controller.handleGoToRelativeStory}
            onLoadViewers={controller.handleLoadViewers}
            selectedFeedItem={selectedDisplayFeedItem}
            selectedStory={selectedDisplayStory}
            selectedStoryIndex={selectedDisplayIndex}
            viewers={controller.viewers}
          />
        ) : (
          <SectionCard
            description="Publish your first story or wait for contacts to post."
            title="No active stories"
          >
            <Text style={styles.emptyText}>
              Story feed is empty right now, but archive tools and creation flow are still available.
            </Text>
            <AppButton fullWidth onPress={onCreateStory} variant="primary">
              Create story
            </AppButton>
          </SectionCard>
        )}

        {controller.showArchive ? (
          <StoriesArchivePanel
            archive={controller.archive}
            deletingStoryId={controller.deletingStoryId}
            loadingArchive={controller.loadingArchive}
            onDeleteStory={controller.handleDeleteStory}
            onOpenStory={controller.handleOpenArchivedStory}
            selectedStoryId={controller.selectedArchiveStoryId}
          />
        ) : null}
      </ScreenScrollView>
    </>
  );
}

const styles = StyleSheet.create({
  emptyText: {
    color: appColors.textSecondary,
    lineHeight: 20
  }
});
