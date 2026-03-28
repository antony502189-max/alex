import React from "react";
import { CreateStoryAudienceSection } from "./CreateStoryAudienceSection";
import { CreateStoryContactsSection } from "./CreateStoryContactsSection";
import { CreateStoryPreviewCard } from "./CreateStoryPreviewCard";
import type { CreateStoryScreenController } from "./useCreateStoryController";
import { AppButton } from "../ui/AppButton";
import { AppHeader } from "../ui/AppHeader";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import { ScreenScrollView } from "../ui/ScreenScrollView";
import { ScreenStack } from "../ui/ScreenStack";
import { AppTextField } from "../ui/AppTextField";

type CreateStoryScreenContentProps = {
  controller: CreateStoryScreenController;
  onClose: () => void;
};

export function CreateStoryScreenContent({
  controller,
  onClose
}: CreateStoryScreenContentProps) {
  return (
    <>
      <AppHeader onBack={onClose} title="New story" />

      <ScreenScrollView gap="lg" paddingBottom="xxl">
        <CreateStoryPreviewCard
          preset={controller.preset}
          selectedMedia={controller.selectedMedia}
          text={controller.text}
        />

        <ScreenStack direction="row" flexWrap="wrap" gap="md">
          <AppButton onPress={() => void controller.handlePickMedia()}>
            {controller.selectedMedia ? "Replace media" : "Add photo/video"}
          </AppButton>
          {controller.selectedMedia ? (
            <AppButton onPress={controller.handleRemoveMedia}>Remove media</AppButton>
          ) : null}
        </ScreenStack>

        <AppTextField
          multiline
          onChangeText={controller.setText}
          placeholder={controller.selectedMedia ? "Caption (optional)" : "What is happening?"}
          value={controller.text}
        />

        <CreateStoryAudienceSection
          audience={controller.audience}
          onSelectAudience={controller.setAudience}
          onSelectPreset={controller.setPreset}
          preset={controller.preset}
        />

        {controller.requiresSelectedContacts ? (
          <CreateStoryContactsSection
            audience={controller.audience}
            contacts={controller.contacts}
            loadingContacts={controller.loadingContacts}
            onToggleViewer={controller.handleToggleViewer}
            selectedContacts={controller.selectedContacts}
            selectedViewerIds={controller.selectedViewerIds}
          />
        ) : null}
        <ScreenFeedback error={controller.error} />

        <AppButton
          disabled={!controller.canSubmit}
          fullWidth
          onPress={() => void controller.handleCreate()}
          variant="primary"
        >
          {controller.submitting ? "Publishing..." : "Publish story"}
        </AppButton>
      </ScreenScrollView>
    </>
  );
}
