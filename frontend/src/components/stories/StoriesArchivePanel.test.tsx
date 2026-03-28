import React from "react";
import { fireEvent, render } from "@testing-library/react-native";
import { StoriesArchivePanel } from "./StoriesArchivePanel";
import type { Story } from "../../types";

function createStory(overrides: Partial<Story> = {}): Story {
  return {
    audience: "CONTACTS",
    backgroundFrom: "#111111",
    backgroundTo: "#222222",
    createdAt: "2026-03-27T09:00:00.000Z",
    expiresAt: "2026-03-28T09:00:00.000Z",
    expired: true,
    media: null,
    ownerDisplayName: "Alex",
    ownerUserId: "user-1",
    ownerUsername: "alex",
    own: true,
    storyId: "story-1",
    text: "Archived story",
    textColor: "#ffffff",
    viewed: true,
    viewsCount: 12,
    ...overrides
  };
}

describe("StoriesArchivePanel", () => {
  it("opens archived stories for preview and exposes lifecycle state", () => {
    const onOpenStory = jest.fn();
    const story = createStory();

    const screen = render(
      <StoriesArchivePanel
        archive={[story]}
        deletingStoryId={null}
        loadingArchive={false}
        onDeleteStory={jest.fn()}
        onOpenStory={onOpenStory}
        selectedStoryId={null}
      />
    );

    expect(screen.getByText("Status Expired")).toBeTruthy();

    fireEvent.press(screen.getByText("Open"));

    expect(onOpenStory).toHaveBeenCalledWith(story);
  });
});
