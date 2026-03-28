import type { Story, StoryFeedItem, StoryViewer } from "../../types";

export function formatStoryAudience(audience: Story["audience"]) {
  switch (audience) {
    case "DEFAULT":
      return "Account default";
    case "EVERYBODY":
      return "Everybody";
    case "CONTACTS":
      return "Contacts";
    case "CLOSE_FRIENDS":
      return "Close friends";
    case "CUSTOM":
      return "Custom";
    case "NOBODY":
      return "Nobody";
    default:
      return audience;
  }
}

export function formatStoryDuration(durationMs: number | null) {
  if (!durationMs) {
    return null;
  }

  const totalSeconds = Math.max(1, Math.round(durationMs / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

export function getInitialStoryIndex(item: StoryFeedItem) {
  const firstUnviewedIndex = item.stories.findIndex((story) => !story.viewed);
  return firstUnviewedIndex >= 0 ? firstUnviewedIndex : 0;
}

export function getStoryFeedStatusLabel(item: StoryFeedItem) {
  if (item.own) {
    return "Your story";
  }

  return item.hasUnviewed ? "New" : "Seen";
}

export function buildStoryLifecycleLabel(story: Story) {
  if (story.expired) {
    return "Expired";
  }

  if (story.own) {
    return "Your story";
  }

  return story.viewed ? "Seen" : "New to you";
}

export function buildStoryVideoSummary(story: Story) {
  if (story.media?.kind !== "VIDEO") {
    return null;
  }

  const duration = formatStoryDuration(story.media.durationMs);
  return `${story.media.fileName ?? "video"}${duration ? ` | ${duration}` : ""}`;
}

export function buildStoryMediaSummary(story: Story) {
  if (!story.media) {
    return null;
  }

  const duration = formatStoryDuration(story.media.durationMs);
  return `Media ${story.media.kind.toLowerCase()}${duration ? ` | ${duration}` : ""}`;
}

export function resolveStoryMediaPreviewUri(story: Story) {
  if (!story.media) {
    return null;
  }

  if (story.media.kind === "IMAGE") {
    return story.media.previewUrl ?? story.media.downloadUrl;
  }

  if (story.media.kind === "VIDEO") {
    return story.media.previewUrl ?? null;
  }

  return null;
}

export function buildStoryArchiveTitle(story: Story) {
  if (story.media) {
    return `${story.media.kind} story`;
  }

  return story.text ?? "Story";
}

export function buildStoryViewerMeta(viewer: StoryViewer) {
  return `${viewer.username ? `@${viewer.username} | ` : ""}${new Date(viewer.viewedAt).toLocaleString()}`;
}
