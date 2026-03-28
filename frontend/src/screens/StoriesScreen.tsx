import React from "react";
import { StoriesScreenContent } from "../components/stories/StoriesScreenContent";
import { AppScreen } from "../components/ui/AppScreen";
import { useStoriesController } from "../components/stories/useStoriesController";
import type { StoryFocusTarget } from "../navigation/rootScreenRendererTypes";

type StoriesScreenProps = {
  focusStory?: StoryFocusTarget | null;
  onClose: () => void;
  onConsumeFocusStory?: () => void;
  onCreateStory: () => void;
  token: string;
};

export function StoriesScreen({
  focusStory = null,
  onClose,
  onConsumeFocusStory,
  onCreateStory,
  token
}: StoriesScreenProps) {
  const controller = useStoriesController({
    focusStory,
    onConsumeFocusStory,
    token
  });

  return (
    <AppScreen padding="xl">
      <StoriesScreenContent
        controller={controller}
        onClose={onClose}
        onCreateStory={onCreateStory}
      />
    </AppScreen>
  );
}
