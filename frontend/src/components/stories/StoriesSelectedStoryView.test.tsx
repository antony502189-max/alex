import React from "react";
import { Image } from "react-native";
import { render } from "@testing-library/react-native";
import { StoriesSelectedStoryView } from "./StoriesSelectedStoryView";
import type { Story, StoryFeedItem } from "../../types";

function createStory(overrides: Partial<Story> = {}): Story {
  return {
    audience: "CONTACTS",
    backgroundFrom: "#111111",
    backgroundTo: "#222222",
    createdAt: "2026-03-27T09:00:00.000Z",
    expiresAt: "2026-03-28T09:00:00.000Z",
    expired: false,
    media: null,
    ownerDisplayName: "Alex",
    ownerUserId: "user-1",
    ownerUsername: "alex",
    own: false,
    storyId: "story-1",
    text: "Morning update",
    textColor: "#ffffff",
    viewed: false,
    viewsCount: 12,
    ...overrides
  };
}

function createFeedItem(story: Story): StoryFeedItem {
  return {
    hasUnviewed: true,
    latestStoryAt: story.createdAt,
    own: story.own,
    ownerDisplayName: story.ownerDisplayName,
    ownerUserId: story.ownerUserId,
    ownerUsername: story.ownerUsername,
    stories: [story]
  };
}

describe("StoriesSelectedStoryView", () => {
  it("renders image stories from the download URL when previewUrl is missing", () => {
    const story = createStory({
      media: {
        accessExpiresAt: null,
        contentType: "image/jpeg",
        downloadUrl: "https://example.com/story.jpg",
        durationMs: null,
        fileName: "story.jpg",
        kind: "IMAGE",
        previewUrl: null,
        requiresAuthorization: false,
        streamingSupported: false
      }
    });

    const screen = render(
      <StoriesSelectedStoryView
        deletingStoryId={null}
        loadingViewers={false}
        onDeleteStory={jest.fn()}
        onGoToRelativeStory={jest.fn()}
        onLoadViewers={jest.fn()}
        selectedFeedItem={createFeedItem(story)}
        selectedStory={story}
        selectedStoryIndex={0}
        viewers={[]}
      />
    );

    expect(screen.UNSAFE_getByType(Image).props.source).toEqual({
      uri: "https://example.com/story.jpg"
    });
    expect(screen.getByText("Status New to you")).toBeTruthy();
  });

  it("renders the available poster image behind video story chrome", () => {
    const story = createStory({
      media: {
        accessExpiresAt: null,
        contentType: "video/mp4",
        downloadUrl: "https://example.com/story.mp4",
        durationMs: 42000,
        fileName: "story.mp4",
        kind: "VIDEO",
        previewUrl: "https://example.com/story-poster.jpg",
        requiresAuthorization: false,
        streamingSupported: true
      }
    });

    const screen = render(
      <StoriesSelectedStoryView
        deletingStoryId={null}
        loadingViewers={false}
        onDeleteStory={jest.fn()}
        onGoToRelativeStory={jest.fn()}
        onLoadViewers={jest.fn()}
        selectedFeedItem={createFeedItem(story)}
        selectedStory={story}
        selectedStoryIndex={0}
        viewers={[]}
      />
    );

    expect(screen.UNSAFE_getByType(Image).props.source).toEqual({
      uri: "https://example.com/story-poster.jpg"
    });
    expect(screen.getByText("Video story")).toBeTruthy();
  });

  it("shows owner lifecycle state for your own story", () => {
    const story = createStory({
      own: true,
      ownerUserId: "user-me"
    });

    const screen = render(
      <StoriesSelectedStoryView
        deletingStoryId={null}
        loadingViewers={false}
        onDeleteStory={jest.fn()}
        onGoToRelativeStory={jest.fn()}
        onLoadViewers={jest.fn()}
        selectedFeedItem={createFeedItem(story)}
        selectedStory={story}
        selectedStoryIndex={0}
        viewers={[]}
      />
    );

    expect(screen.getByText("Status Your story")).toBeTruthy();
  });
});
