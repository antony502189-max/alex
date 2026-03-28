import { useEffect, useMemo, useState } from "react";
import { api } from "../../services/api";
import type { Story, StoryFeedItem, StoryViewer } from "../../types";
import type { StoryFocusTarget } from "../../navigation/rootScreenRendererTypes";
import { getInitialStoryIndex } from "./storiesPresentation";

type UseStoriesControllerParams = {
  focusStory?: StoryFocusTarget | null;
  onConsumeFocusStory?: () => void;
  token: string;
};

export function useStoriesController({
  focusStory = null,
  onConsumeFocusStory,
  token
}: UseStoriesControllerParams) {
  const [feed, setFeed] = useState<StoryFeedItem[]>([]);
  const [archive, setArchive] = useState<Story[]>([]);
  const [selectedOwnerId, setSelectedOwnerId] = useState<string | null>(null);
  const [selectedStoryIndex, setSelectedStoryIndex] = useState(0);
  const [viewers, setViewers] = useState<StoryViewer[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingViewers, setLoadingViewers] = useState(false);
  const [loadingArchive, setLoadingArchive] = useState(false);
  const [deletingStoryId, setDeletingStoryId] = useState<string | null>(null);
  const [selectedArchiveStoryId, setSelectedArchiveStoryId] = useState<string | null>(null);
  const [showArchive, setShowArchive] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function loadFeed() {
    setLoading(true);
    setError(null);
    try {
      const nextFeed = await api.getStoriesFeed(token);
      setFeed(nextFeed);
      setSelectedOwnerId((current) => {
        if (focusStory && nextFeed.some((item) => item.ownerUserId === focusStory.ownerUserId)) {
          return focusStory.ownerUserId;
        }

        if (current && nextFeed.some((item) => item.ownerUserId === current)) {
          return current;
        }

        return nextFeed[0]?.ownerUserId ?? null;
      });
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load stories");
    } finally {
      setLoading(false);
    }
  }

  async function loadArchive() {
    setLoadingArchive(true);
    setError(null);
    try {
      setArchive(await api.getStoryArchive(token));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load archive");
    } finally {
      setLoadingArchive(false);
    }
  }

  useEffect(() => {
    void loadFeed();
  }, [focusStory?.ownerUserId, focusStory?.storyId, token]);

  const selectedFeedItem = useMemo(
    () => feed.find((item) => item.ownerUserId === selectedOwnerId) ?? null,
    [feed, selectedOwnerId]
  );

  useEffect(() => {
    if (!selectedFeedItem) {
      setSelectedStoryIndex(0);
      setViewers([]);
      return;
    }

    if (selectedStoryIndex >= selectedFeedItem.stories.length) {
      setSelectedStoryIndex(0);
    }
  }, [selectedFeedItem, selectedStoryIndex]);

  const selectedStory = selectedFeedItem?.stories[selectedStoryIndex] ?? null;

  useEffect(() => {
    if (!focusStory) {
      return;
    }

    const focusedFeedItem = feed.find((item) => item.ownerUserId === focusStory.ownerUserId);
    if (!focusedFeedItem) {
      return;
    }

    const focusedStoryIndex = focusedFeedItem.stories.findIndex(
      (story) => story.storyId === focusStory.storyId
    );
    if (focusedStoryIndex < 0) {
      return;
    }

    setSelectedOwnerId(focusStory.ownerUserId);
    setSelectedStoryIndex(focusedStoryIndex);
    setSelectedArchiveStoryId(null);
    setShowArchive(false);
    setViewers([]);
    onConsumeFocusStory?.();
  }, [feed, focusStory, onConsumeFocusStory]);

  useEffect(() => {
    if (!selectedStory || selectedStory.viewed || selectedStory.own) {
      return;
    }

    let cancelled = false;
    void api.markStoryViewed(token, selectedStory.storyId)
      .then((updated) => {
        if (cancelled) {
          return;
        }

        setFeed((current) =>
          current.map((item) =>
            item.ownerUserId !== updated.ownerUserId
              ? item
              : {
                  ...item,
                  hasUnviewed: item.stories.some((story) =>
                    story.storyId === updated.storyId ? false : !story.viewed
                  ),
                  stories: item.stories.map((story) =>
                    story.storyId === updated.storyId ? updated : story
                  )
                }
          )
        );
      })
      .catch(() => undefined);

    return () => {
      cancelled = true;
    };
  }, [selectedStory?.own, selectedStory?.storyId, selectedStory?.viewed, token]);

  async function handleLoadViewers(story: Story) {
    if (!story.own) {
      return;
    }

    setLoadingViewers(true);
    setError(null);
    try {
      setViewers(await api.getStoryViewers(token, story.storyId));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load viewers");
    } finally {
      setLoadingViewers(false);
    }
  }

  async function handleDeleteStory(story: Story) {
    setDeletingStoryId(story.storyId);
    setError(null);
    try {
      await api.deleteStory(token, story.storyId);
      setSelectedArchiveStoryId((current) => (current === story.storyId ? null : current));
      await Promise.all([
        loadFeed(),
        showArchive ? loadArchive() : Promise.resolve()
      ]);
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "Unable to delete story");
    } finally {
      setDeletingStoryId(null);
    }
  }

  async function handleToggleArchive() {
    const nextValue = !showArchive;
    setShowArchive(nextValue);
    if (nextValue && archive.length === 0) {
      await loadArchive();
    }
  }

  function handleOpenOwnerStories(item: StoryFeedItem) {
    setSelectedOwnerId(item.ownerUserId);
    setSelectedStoryIndex(getInitialStoryIndex(item));
    setSelectedArchiveStoryId(null);
    setViewers([]);
  }

  function handleOpenArchivedStory(story: Story) {
    setSelectedArchiveStoryId(story.storyId);
    setSelectedOwnerId(null);
    setSelectedStoryIndex(0);
    setViewers([]);
  }

  function handleGoToRelativeStory(offset: number) {
    if (!selectedFeedItem) {
      return;
    }

    const nextIndex = selectedStoryIndex + offset;
    if (nextIndex < 0 || nextIndex >= selectedFeedItem.stories.length) {
      return;
    }

    setSelectedStoryIndex(nextIndex);
    setViewers([]);
  }

  const selectedArchiveStory = useMemo(
    () => archive.find((story) => story.storyId === selectedArchiveStoryId) ?? null,
    [archive, selectedArchiveStoryId]
  );

  return {
    archive,
    deletingStoryId,
    error,
    feed,
    handleDeleteStory,
    handleGoToRelativeStory,
    handleLoadViewers,
    handleOpenArchivedStory,
    handleOpenOwnerStories,
    handleToggleArchive,
    loading,
    loadingArchive,
    loadingViewers,
    selectedFeedItem,
    selectedArchiveStory,
    selectedArchiveStoryId,
    selectedOwnerId,
    selectedStory,
    selectedStoryIndex,
    showArchive,
    viewers
  };
}

export type StoriesScreenController = ReturnType<typeof useStoriesController>;
