jest.mock("../../services/api", () => ({
  api: {
    deleteStory: jest.fn(),
    getStoriesFeed: jest.fn(),
    getStoryArchive: jest.fn(),
    getStoryViewers: jest.fn(),
    markStoryViewed: jest.fn()
  }
}));

import { act, renderHook, waitFor } from "@testing-library/react-native";
import { api } from "../../services/api";
import type { Story, StoryFeedItem, StoryViewer } from "../../types";
import { useStoriesController } from "./useStoriesController";

function createStory(overrides: Partial<Story> = {}): Story {
  return {
    audience: "EVERYBODY",
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
    text: "Hello",
    textColor: "#ffffff",
    viewed: false,
    viewsCount: 3,
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
    stories: [createStory()],
    ...overrides
  };
}

function createViewer(overrides: Partial<StoryViewer> = {}): StoryViewer {
  return {
    displayName: "Kate",
    viewedAt: "2026-03-27T11:00:00.000Z",
    viewerUserId: "viewer-1",
    username: "kate",
    ...overrides
  };
}

describe("useStoriesController", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("loads the feed and marks the selected story as viewed", async () => {
    (api.getStoriesFeed as jest.Mock).mockResolvedValue([createFeedItem()]);
    (api.markStoryViewed as jest.Mock).mockResolvedValue(createStory({ viewed: true }));

    const { result } = renderHook(() => useStoriesController({ token: "token-1" }));

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
      expect(result.current.feed).toHaveLength(1);
      expect(result.current.selectedOwnerId).toBe("user-1");
    });

    await waitFor(() => {
      expect(api.markStoryViewed).toHaveBeenCalledWith("token-1", "story-1");
    });
  });

  it("loads archive, viewers and deletes stories", async () => {
    const ownStory = createStory({
      own: true,
      ownerUserId: "user-2",
      storyId: "story-own"
    });

    (api.getStoriesFeed as jest.Mock)
      .mockResolvedValueOnce([
        createFeedItem({
          own: true,
          ownerUserId: "user-2",
          stories: [ownStory]
        })
      ])
      .mockResolvedValueOnce([]);
    (api.getStoryArchive as jest.Mock)
      .mockResolvedValueOnce([createStory({ storyId: "archive-1", expired: true })])
      .mockResolvedValueOnce([]);
    (api.getStoryViewers as jest.Mock).mockResolvedValue([createViewer()]);
    (api.deleteStory as jest.Mock).mockResolvedValue(undefined);

    const { result } = renderHook(() => useStoriesController({ token: "token-1" }));

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
      expect(result.current.selectedStory?.storyId).toBe("story-own");
    });

    await act(async () => {
      await result.current.handleToggleArchive();
    });

    expect(api.getStoryArchive).toHaveBeenCalledWith("token-1");
    expect(result.current.showArchive).toBe(true);

    await act(async () => {
      await result.current.handleLoadViewers(ownStory);
    });

    expect(api.getStoryViewers).toHaveBeenCalledWith("token-1", "story-own");
    expect(result.current.viewers).toHaveLength(1);

    await act(async () => {
      await result.current.handleDeleteStory(ownStory);
    });

    expect(api.deleteStory).toHaveBeenCalledWith("token-1", "story-own");
    expect(api.getStoriesFeed).toHaveBeenCalledTimes(2);
    expect(api.getStoryArchive).toHaveBeenCalledTimes(2);
  });

  it("opens archived stories as the selected display story", async () => {
    const archivedStory = createStory({
      expired: true,
      own: true,
      storyId: "archive-2"
    });

    (api.getStoriesFeed as jest.Mock).mockResolvedValue([]);
    (api.getStoryArchive as jest.Mock).mockResolvedValue([archivedStory]);

    const { result } = renderHook(() => useStoriesController({ token: "token-1" }));

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    await act(async () => {
      await result.current.handleToggleArchive();
    });

    act(() => {
      result.current.handleOpenArchivedStory(archivedStory);
    });

    expect(result.current.selectedArchiveStoryId).toBe("archive-2");
    expect(result.current.selectedArchiveStory?.storyId).toBe("archive-2");
    expect(result.current.selectedOwnerId).toBeNull();
  });

  it("reloads and focuses a newly created story when the shell requests it", async () => {
    const onConsumeFocusStory = jest.fn();
    const originalFeed = [
      createFeedItem({
        ownerDisplayName: "Kate",
        ownerUserId: "user-2",
        ownerUsername: "kate",
        stories: [createStory({ ownerDisplayName: "Kate", ownerUserId: "user-2", ownerUsername: "kate" })]
      })
    ];
    const createdStory = createStory({
      own: true,
      ownerDisplayName: "Alex",
      ownerUserId: "user-1",
      ownerUsername: "alex",
      storyId: "story-new"
    });
    const refreshedFeed = [
      createFeedItem({
        own: true,
        ownerDisplayName: "Alex",
        ownerUserId: "user-1",
        ownerUsername: "alex",
        stories: [
          createStory({
            own: true,
            ownerDisplayName: "Alex",
            ownerUserId: "user-1",
            ownerUsername: "alex",
            storyId: "story-old",
            viewed: true
          }),
          createdStory
        ]
      }),
      ...originalFeed
    ];

    (api.getStoriesFeed as jest.Mock)
      .mockResolvedValueOnce(originalFeed)
      .mockResolvedValueOnce(refreshedFeed);

    const { result, rerender } = renderHook(
      (props: {
        focusStory: { ownerUserId: string; storyId: string } | null;
      }) =>
        useStoriesController({
          focusStory: props.focusStory,
          onConsumeFocusStory,
          token: "token-1"
        }),
      {
        initialProps: {
          focusStory: null
        }
      }
    );

    await waitFor(() => {
      expect(result.current.feed).toHaveLength(1);
      expect(result.current.selectedOwnerId).toBe("user-2");
    });

    rerender({
      focusStory: {
        ownerUserId: "user-1",
        storyId: "story-new"
      }
    });

    await waitFor(() => {
      expect(result.current.selectedOwnerId).toBe("user-1");
      expect(result.current.selectedStory?.storyId).toBe("story-new");
    });

    expect(result.current.showArchive).toBe(false);
    expect(onConsumeFocusStory).toHaveBeenCalledTimes(1);
  });
});
