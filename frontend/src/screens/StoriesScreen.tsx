import React, { useEffect, useMemo, useState } from "react";
import {
  Image,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  View
} from "react-native";
import { api } from "../services/api";
import type { Story, StoryFeedItem, StoryViewer } from "../types";

type StoriesScreenProps = {
  onClose: () => void;
  onCreateStory: () => void;
  token: string;
};

function formatAudience(audience: Story["audience"]) {
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

function formatDuration(durationMs: number | null) {
  if (!durationMs) {
    return null;
  }
  const totalSeconds = Math.max(1, Math.round(durationMs / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

export function StoriesScreen({
  onClose,
  onCreateStory,
  token
}: StoriesScreenProps) {
  const [feed, setFeed] = useState<StoryFeedItem[]>([]);
  const [archive, setArchive] = useState<Story[]>([]);
  const [selectedOwnerId, setSelectedOwnerId] = useState<string | null>(null);
  const [selectedStoryIndex, setSelectedStoryIndex] = useState(0);
  const [viewers, setViewers] = useState<StoryViewer[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingViewers, setLoadingViewers] = useState(false);
  const [loadingArchive, setLoadingArchive] = useState(false);
  const [deletingStoryId, setDeletingStoryId] = useState<string | null>(null);
  const [showArchive, setShowArchive] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function loadFeed() {
    setLoading(true);
    setError(null);
    try {
      const nextFeed = await api.getStoriesFeed(token);
      setFeed(nextFeed);
      setSelectedOwnerId((current) => {
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
  }, [token]);

  const selectedFeedItem = useMemo(
    () => feed.find((item) => item.ownerUserId === selectedOwnerId) ?? null,
    [feed, selectedOwnerId]
  );

  const selectedStory = selectedFeedItem?.stories[selectedStoryIndex] ?? null;

  useEffect(() => {
    setSelectedStoryIndex(0);
    setViewers([]);
  }, [selectedOwnerId]);

  useEffect(() => {
    if (!selectedStory || selectedStory.viewed || selectedStory.own) {
      return;
    }

    void api.markStoryViewed(token, selectedStory.storyId)
      .then((updated) => {
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
  }, [selectedStory?.storyId, token]);

  async function handleLoadViewers(story: Story) {
    if (!story.own) {
      return;
    }

    setLoadingViewers(true);
    setError(null);
    try {
      const nextViewers = await api.getStoryViewers(token, story.storyId);
      setViewers(nextViewers);
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

  function openOwnerStories(item: StoryFeedItem) {
    setSelectedOwnerId(item.ownerUserId);
    const firstUnviewedIndex = item.stories.findIndex((story) => !story.viewed);
    setSelectedStoryIndex(firstUnviewedIndex >= 0 ? firstUnviewedIndex : 0);
    setViewers([]);
  }

  function goToRelativeStory(offset: number) {
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

  function renderArchivePanel() {
    if (!showArchive) {
      return null;
    }

    return (
      <View style={styles.archiveCard}>
        <Text style={styles.storyInfoTitle}>My archive</Text>
        {loadingArchive ? <Text style={styles.storyInfoText}>Loading archive...</Text> : null}
        {!loadingArchive && archive.length === 0 ? (
          <Text style={styles.storyInfoText}>Expired stories will appear here.</Text>
        ) : null}
        {archive.map((story) => (
          <View key={story.storyId} style={styles.archiveRow}>
            <View style={styles.archiveBody}>
              <Text style={styles.archiveTitle}>
                {story.media ? `${story.media.kind} story` : story.text ?? "Story"}
              </Text>
              <Text style={styles.archiveMeta}>
                Expired {new Date(story.expiresAt).toLocaleString()}
              </Text>
              <Text style={styles.archiveMeta}>
                Audience {formatAudience(story.audience)}
              </Text>
              {story.text ? (
                <Text style={styles.archiveSnippet} numberOfLines={2}>
                  {story.text}
                </Text>
              ) : null}
            </View>
            <Pressable
              disabled={deletingStoryId === story.storyId}
              onPress={() => void handleDeleteStory(story)}
              style={[styles.archiveDeleteButton, deletingStoryId === story.storyId && styles.disabled]}
            >
              <Text style={styles.archiveDeleteText}>
                {deletingStoryId === story.storyId ? "..." : "Delete"}
              </Text>
            </Pressable>
          </View>
        ))}
      </View>
    );
  }

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable onPress={onClose} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>Back</Text>
        </Pressable>
        <Text style={styles.title}>Stories</Text>
        <Pressable onPress={() => void handleToggleArchive()} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>{showArchive ? "Hide archive" : "Archive"}</Text>
        </Pressable>
        <Pressable onPress={onCreateStory} style={styles.primaryHeaderButton}>
          <Text style={styles.primaryHeaderButtonText}>New story</Text>
        </Pressable>
      </View>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.feedStrip}>
        {feed.map((item) => (
          <Pressable
            key={item.ownerUserId}
            onPress={() => openOwnerStories(item)}
            style={[
              styles.feedChip,
              selectedOwnerId === item.ownerUserId && styles.feedChipActive
            ]}
          >
            <Text style={styles.feedChipTitle}>{item.ownerDisplayName}</Text>
            <Text style={styles.feedChipMeta}>
              {item.own ? "You" : item.hasUnviewed ? "New" : "Seen"}
            </Text>
          </Pressable>
        ))}
      </ScrollView>

      {loading ? <Text style={styles.metaText}>Loading stories...</Text> : null}
      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      {!selectedStory ? (
        <ScrollView contentContainerStyle={styles.content}>
          <View style={styles.emptyState}>
            <Text style={styles.emptyTitle}>No active stories</Text>
            <Text style={styles.emptyText}>Publish your first story or wait for contacts to post.</Text>
          </View>
          {renderArchivePanel()}
        </ScrollView>
      ) : (
        <ScrollView contentContainerStyle={styles.content}>
          <View
            style={[
              styles.storyCard,
              {
                backgroundColor: selectedStory.backgroundFrom,
                borderColor: selectedStory.backgroundTo
              }
            ]}
          >
            {selectedStory.media?.kind === "IMAGE" && selectedStory.media.previewUrl ? (
              <Image
                source={{ uri: selectedStory.media.previewUrl }}
                style={styles.storyImage}
                resizeMode="cover"
              />
            ) : null}
            {selectedStory.media?.kind === "VIDEO" ? (
              <View style={styles.storyVideoPlaceholder}>
                <Text style={styles.storyVideoTitle}>Video story</Text>
                <Text style={styles.storyVideoMeta}>
                  {selectedStory.media.fileName ?? "video"}
                  {selectedStory.media.durationMs
                    ? ` • ${formatDuration(selectedStory.media.durationMs)}`
                    : ""}
                </Text>
              </View>
            ) : null}
            <View style={styles.storyOverlay}>
              {selectedStory.text ? (
                <Text style={[styles.storyText, { color: selectedStory.textColor }]}>
                  {selectedStory.text}
                </Text>
              ) : null}
              <Text style={[styles.storyMeta, { color: selectedStory.textColor }]}>
                {selectedStory.ownerDisplayName}
              </Text>
              <Text style={[styles.storyMeta, { color: selectedStory.textColor }]}>
                {new Date(selectedStory.createdAt).toLocaleString()}
              </Text>
            </View>
          </View>

          <View style={styles.storyActions}>
            <Pressable
              disabled={selectedStoryIndex === 0}
              onPress={() => goToRelativeStory(-1)}
              style={[styles.secondaryButton, selectedStoryIndex === 0 && styles.disabled]}
            >
              <Text style={styles.secondaryButtonText}>Previous</Text>
            </Pressable>
            <Pressable
              disabled={!selectedFeedItem || selectedStoryIndex >= selectedFeedItem.stories.length - 1}
              onPress={() => goToRelativeStory(1)}
              style={[
                styles.secondaryButton,
                (!selectedFeedItem || selectedStoryIndex >= selectedFeedItem.stories.length - 1) && styles.disabled
              ]}
            >
              <Text style={styles.secondaryButtonText}>Next</Text>
            </Pressable>
          </View>

          <View style={styles.storyInfoCard}>
            <Text style={styles.storyInfoTitle}>
              {selectedStoryIndex + 1} / {selectedFeedItem?.stories.length ?? 1}
            </Text>
            <Text style={styles.storyInfoText}>
              Expires {new Date(selectedStory.expiresAt).toLocaleString()}
            </Text>
            <Text style={styles.storyInfoText}>Views {selectedStory.viewsCount}</Text>
            <Text style={styles.storyInfoText}>Audience {formatAudience(selectedStory.audience)}</Text>
            {selectedStory.media ? (
              <Text style={styles.storyInfoText}>
                Media {selectedStory.media.kind.toLowerCase()}
                {selectedStory.media.durationMs
                  ? ` • ${formatDuration(selectedStory.media.durationMs)}`
                  : ""}
              </Text>
            ) : null}
          </View>

          {selectedStory.own ? (
            <View style={styles.storyActions}>
              <Pressable
                disabled={loadingViewers}
                onPress={() => void handleLoadViewers(selectedStory)}
                style={[styles.secondaryButton, loadingViewers && styles.disabled]}
              >
                <Text style={styles.secondaryButtonText}>
                  {loadingViewers ? "Loading..." : "Viewers"}
                </Text>
              </Pressable>
              <Pressable
                disabled={deletingStoryId === selectedStory.storyId}
                onPress={() => void handleDeleteStory(selectedStory)}
                style={[styles.dangerButton, deletingStoryId === selectedStory.storyId && styles.disabled]}
              >
                <Text style={styles.dangerButtonText}>
                  {deletingStoryId === selectedStory.storyId ? "Deleting..." : "Delete"}
                </Text>
              </Pressable>
            </View>
          ) : null}

          {viewers.length > 0 ? (
            <View style={styles.viewersCard}>
              <Text style={styles.storyInfoTitle}>Viewers</Text>
              {viewers.map((viewer) => (
                <View key={`${selectedStory.storyId}-${viewer.viewerUserId}`} style={styles.viewerRow}>
                  <Text style={styles.viewerName}>{viewer.displayName}</Text>
                  <Text style={styles.viewerMeta}>
                    {viewer.username ? `@${viewer.username} • ` : ""}
                    {new Date(viewer.viewedAt).toLocaleString()}
                  </Text>
                </View>
              ))}
            </View>
          ) : null}

          {renderArchivePanel()}
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: "#f8fafc",
    padding: 20
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    marginBottom: 16,
    flexWrap: "wrap"
  },
  title: {
    flex: 1,
    minWidth: 120,
    fontSize: 24,
    fontWeight: "700",
    color: "#0f172a"
  },
  feedStrip: {
    gap: 10,
    paddingBottom: 12
  },
  feedChip: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    paddingHorizontal: 14,
    paddingVertical: 12,
    minWidth: 108
  },
  feedChipActive: {
    borderWidth: 2,
    borderColor: "#0f172a"
  },
  feedChipTitle: {
    color: "#0f172a",
    fontWeight: "700"
  },
  feedChipMeta: {
    marginTop: 4,
    color: "#64748b",
    fontSize: 12
  },
  content: {
    gap: 14,
    paddingBottom: 32
  },
  storyCard: {
    minHeight: 360,
    borderRadius: 28,
    borderWidth: 3,
    overflow: "hidden",
    justifyContent: "flex-end"
  },
  storyImage: {
    ...StyleSheet.absoluteFillObject
  },
  storyVideoPlaceholder: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "#0f172a",
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 24
  },
  storyVideoTitle: {
    color: "#ffffff",
    fontSize: 28,
    fontWeight: "700"
  },
  storyVideoMeta: {
    color: "#cbd5e1",
    marginTop: 10,
    textAlign: "center"
  },
  storyOverlay: {
    padding: 24,
    backgroundColor: "rgba(15, 23, 42, 0.28)"
  },
  storyText: {
    fontSize: 30,
    fontWeight: "700",
    textAlign: "center"
  },
  storyMeta: {
    marginTop: 10,
    fontWeight: "600",
    textAlign: "center"
  },
  storyActions: {
    flexDirection: "row",
    gap: 12
  },
  storyInfoCard: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16,
    gap: 6
  },
  storyInfoTitle: {
    color: "#0f172a",
    fontWeight: "700"
  },
  storyInfoText: {
    color: "#64748b"
  },
  viewersCard: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16,
    gap: 10
  },
  viewerRow: {
    borderTopWidth: 1,
    borderTopColor: "#e2e8f0",
    paddingTop: 10
  },
  viewerName: {
    color: "#0f172a",
    fontWeight: "600"
  },
  viewerMeta: {
    color: "#64748b",
    marginTop: 2
  },
  archiveCard: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16,
    gap: 12
  },
  archiveRow: {
    flexDirection: "row",
    gap: 12,
    alignItems: "flex-start",
    borderTopWidth: 1,
    borderTopColor: "#e2e8f0",
    paddingTop: 12
  },
  archiveBody: {
    flex: 1,
    gap: 4
  },
  archiveTitle: {
    color: "#0f172a",
    fontWeight: "700"
  },
  archiveMeta: {
    color: "#64748b",
    fontSize: 12
  },
  archiveSnippet: {
    color: "#334155"
  },
  archiveDeleteButton: {
    borderRadius: 10,
    backgroundColor: "#fee2e2",
    paddingHorizontal: 10,
    paddingVertical: 8
  },
  archiveDeleteText: {
    color: "#b91c1c",
    fontWeight: "700"
  },
  emptyState: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 8
  },
  emptyTitle: {
    fontSize: 24,
    fontWeight: "700",
    color: "#0f172a"
  },
  emptyText: {
    color: "#64748b",
    textAlign: "center",
    maxWidth: 260
  },
  primaryHeaderButton: {
    borderRadius: 12,
    backgroundColor: "#0f172a",
    paddingHorizontal: 14,
    paddingVertical: 10
  },
  primaryHeaderButtonText: {
    color: "#ffffff",
    fontWeight: "600"
  },
  secondaryButton: {
    borderRadius: 12,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 14,
    paddingVertical: 10
  },
  secondaryButtonText: {
    color: "#0f172a",
    fontWeight: "600"
  },
  dangerButton: {
    borderRadius: 12,
    backgroundColor: "#fee2e2",
    paddingHorizontal: 14,
    paddingVertical: 10
  },
  dangerButtonText: {
    color: "#b91c1c",
    fontWeight: "700"
  },
  metaText: {
    color: "#64748b"
  },
  errorText: {
    color: "#b91c1c"
  },
  disabled: {
    opacity: 0.6
  }
});
