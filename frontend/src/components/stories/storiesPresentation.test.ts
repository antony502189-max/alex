import type { Story, StoryFeedItem, StoryViewer } from "../../types";
import {
  buildStoryLifecycleLabel,
  buildStoryArchiveTitle,
  buildStoryMediaSummary,
  resolveStoryMediaPreviewUri,
  buildStoryVideoSummary,
  buildStoryViewerMeta,
  formatStoryAudience,
  formatStoryDuration,
  getInitialStoryIndex,
  getStoryFeedStatusLabel
} from "./storiesPresentation";

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

function createFeedItem(overrides: Partial<StoryFeedItem> = {}): StoryFeedItem {
  return {
    hasUnviewed: true,
    latestStoryAt: "2026-03-27T09:00:00.000Z",
    own: false,
    ownerDisplayName: "Alex",
    ownerUserId: "user-1",
    ownerUsername: "alex",
    stories: [
      createStory({ storyId: "story-1", viewed: true }),
      createStory({ storyId: "story-2", viewed: false })
    ],
    ...overrides
  };
}

function createViewer(overrides: Partial<StoryViewer> = {}): StoryViewer {
  return {
    displayName: "Kate",
    viewedAt: "2026-03-27T10:00:00.000Z",
    viewerUserId: "viewer-1",
    username: "kate",
    ...overrides
  };
}

describe("storiesPresentation", () => {
  it("formats audience, duration and feed labels", () => {
    expect(formatStoryAudience("CLOSE_FRIENDS")).toBe("Close friends");
    expect(formatStoryDuration(65000)).toBe("1:05");
    expect(getStoryFeedStatusLabel(createFeedItem())).toBe("New");
    expect(getStoryFeedStatusLabel(createFeedItem({ own: true }))).toBe("Your story");
    expect(buildStoryLifecycleLabel(createStory())).toBe("New to you");
    expect(buildStoryLifecycleLabel(createStory({ viewed: true }))).toBe("Seen");
    expect(buildStoryLifecycleLabel(createStory({ own: true }))).toBe("Your story");
    expect(buildStoryLifecycleLabel(createStory({ expired: true }))).toBe("Expired");
  });

  it("builds story summaries and initial selection state", () => {
    const story = createStory({
      media: {
        accessExpiresAt: null,
        contentType: "video/mp4",
        downloadUrl: "https://example.com/story.mp4",
        durationMs: 65000,
        fileName: "story.mp4",
        kind: "VIDEO",
        previewUrl: null,
        requiresAuthorization: false,
        streamingSupported: true
      }
    });

    expect(getInitialStoryIndex(createFeedItem())).toBe(1);
    expect(buildStoryVideoSummary(story)).toBe("story.mp4 | 1:05");
    expect(buildStoryMediaSummary(story)).toBe("Media video | 1:05");
    expect(buildStoryArchiveTitle(story)).toBe("VIDEO story");
    expect(buildStoryViewerMeta(createViewer())).toContain("@kate | ");
  });

  it("falls back to the download URL for image story previews when no preview URL exists", () => {
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

    expect(resolveStoryMediaPreviewUri(story)).toBe("https://example.com/story.jpg");
  });

  it("uses the dedicated preview asset for video stories and never falls back to the video file URL", () => {
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

    expect(resolveStoryMediaPreviewUri(story)).toBe("https://example.com/story-poster.jpg");
  });
});
