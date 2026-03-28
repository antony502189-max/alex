import React from "react";
import { CreateStoryScreenContent } from "../components/stories/CreateStoryScreenContent";
import { AppScreen } from "../components/ui/AppScreen";
import { useCreateStoryController } from "../components/stories/useCreateStoryController";
import type { Story } from "../types";

type CreateStoryScreenProps = {
  onClose: () => void;
  onCreated: (story: Story) => void;
  token: string;
};

export function CreateStoryScreen({
  onClose,
  onCreated,
  token
}: CreateStoryScreenProps) {
  const controller = useCreateStoryController({
    onCreated,
    token
  });

  return (
    <AppScreen padding="xl">
      <CreateStoryScreenContent
        controller={controller}
        onClose={onClose}
      />
    </AppScreen>
  );
}
