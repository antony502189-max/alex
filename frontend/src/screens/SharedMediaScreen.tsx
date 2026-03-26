import React, { useEffect, useMemo, useState } from "react";
import * as Sharing from "expo-sharing";
import {
  ActivityIndicator,
  Image,
  Linking,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  View
} from "react-native";
import { Avatar } from "../components/Avatar";
import { attachmentTransfers } from "../services/attachmentTransfers";
import { api } from "../services/api";
import { useMediaStore } from "../store/useMediaStore";
import type { ChatMessage, ChatSummary, MessageAttachment, SharedMediaBuckets } from "../types";

type SharedMediaScreenProps = {
  chat: ChatSummary;
  token: string;
  onClose: () => void;
  onOpenMediaViewer: (payload: {
    attachments: MessageAttachment[];
    initialAttachmentId: string;
    chatTitle: string;
  }) => void;
};

const URL_PATTERN = /((?:https?|alex):\/\/[^\s]+)/gi;

function isMediaAttachment(attachment: MessageAttachment) {
  return (
    attachment.kind === "IMAGE" ||
    attachment.kind === "GIF" ||
    attachment.kind === "VIDEO" ||
    attachment.kind === "VIDEO_NOTE" ||
    attachment.contentType.startsWith("image/") ||
    attachment.contentType.startsWith("video/")
  );
}

function buildSharedMediaBuckets(chatId: string, messages: ChatMessage[]): SharedMediaBuckets {
  const media: SharedMediaBuckets["media"] = [];
  const files: SharedMediaBuckets["files"] = [];
  const links: SharedMediaBuckets["links"] = [];

  for (const message of messages) {
    if (message.deletedAt) {
      continue;
    }

    for (const attachment of message.attachments) {
      const entry = {
        chatId,
        messageId: message.messageId,
        createdAt: message.createdAt,
        senderDisplayName: message.displaySenderName,
        caption: message.caption ?? message.text ?? null,
        attachment
      };

      if (isMediaAttachment(attachment)) {
        media.push(entry);
      } else {
        files.push(entry);
      }
    }

    const textToScan = [message.text, message.caption].filter(Boolean).join("\n");
    if (!textToScan) {
      continue;
    }

    const matches = textToScan.match(URL_PATTERN) ?? [];
    matches.forEach((url, index) => {
      links.push({
        linkId: `${message.messageId}:${index}:${url}`,
        chatId,
        messageId: message.messageId,
        createdAt: message.createdAt,
        url,
        label: message.text || message.caption || null
      });
    });
  }

  function sortByNewest<T extends { createdAt: string }>(items: T[]) {
    return [...items].sort((left, right) => right.createdAt.localeCompare(left.createdAt));
  }

  return {
    chatId,
    media: sortByNewest(media),
    files: sortByNewest(files),
    links: sortByNewest(links),
    loadedAt: new Date().toISOString()
  };
}

function formatFileSize(fileSizeBytes: number) {
  if (fileSizeBytes < 1024) {
    return `${fileSizeBytes} B`;
  }
  if (fileSizeBytes < 1024 * 1024) {
    return `${(fileSizeBytes / 1024).toFixed(1)} KB`;
  }
  if (fileSizeBytes < 1024 * 1024 * 1024) {
    return `${(fileSizeBytes / (1024 * 1024)).toFixed(1)} MB`;
  }
  return `${(fileSizeBytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function attachmentLabel(attachment: MessageAttachment) {
  if (attachment.kind === "VOICE") {
    return "Voice";
  }
  if (attachment.kind === "AUDIO") {
    return "Audio";
  }
  if (attachment.kind === "IMAGE") {
    return "Photo";
  }
  if (attachment.kind === "VIDEO") {
    return "Video";
  }
  if (attachment.kind === "VIDEO_NOTE") {
    return "Video note";
  }
  if (attachment.kind === "GIF") {
    return "GIF";
  }
  return attachment.originalFileName;
}

export function SharedMediaScreen({
  chat,
  token,
  onClose,
  onOpenMediaViewer
}: SharedMediaScreenProps) {
  const cachedBuckets = useMediaStore((state) => state.bucketsByChatId[chat.chatId] ?? null);
  const setBuckets = useMediaStore((state) => state.setBuckets);
  const [buckets, setLocalBuckets] = useState<SharedMediaBuckets | null>(cachedBuckets);
  const [loading, setLoading] = useState(!cachedBuckets);
  const [refreshing, setRefreshing] = useState(false);
  const [openingAttachmentId, setOpeningAttachmentId] = useState<string | null>(null);
  const [openingLinkId, setOpeningLinkId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLocalBuckets(cachedBuckets);
    setLoading(!cachedBuckets);
  }, [cachedBuckets]);

  async function loadBuckets(showLoader = false) {
    if (showLoader) {
      setLoading(true);
    } else {
      setRefreshing(true);
    }
    setError(null);
    try {
      const messages = await api.getMessages(token, chat.chatId, 120);
      const nextBuckets = buildSharedMediaBuckets(chat.chatId, messages);
      setBuckets(chat.chatId, nextBuckets);
      setLocalBuckets(nextBuckets);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load shared media");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  useEffect(() => {
    void loadBuckets(!cachedBuckets);
  }, [cachedBuckets, chat.chatId, token]);

  const mediaAttachments = useMemo(
    () => buckets?.media.map((entry) => entry.attachment) ?? [],
    [buckets]
  );

  async function handleOpenFileAttachment(attachment: MessageAttachment) {
    setOpeningAttachmentId(attachment.attachmentId);
    setError(null);
    try {
      const uri = await attachmentTransfers.downloadAttachment(token, attachment);
      if (!uri) {
        return;
      }
      if (await Sharing.isAvailableAsync()) {
        await Sharing.shareAsync(uri);
        return;
      }
      setError("Sharing is not available on this platform.");
    } catch (openError) {
      setError(openError instanceof Error ? openError.message : "Unable to open attachment");
    } finally {
      setOpeningAttachmentId(null);
    }
  }

  async function handleOpenLink(linkId: string, url: string) {
    setOpeningLinkId(linkId);
    setError(null);
    try {
      await Linking.openURL(url);
    } catch (linkError) {
      setError(linkError instanceof Error ? linkError.message : "Unable to open link");
    } finally {
      setOpeningLinkId(null);
    }
  }

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable onPress={onClose} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>Back</Text>
        </Pressable>
        <View style={styles.headerCopy}>
          <Text style={styles.title}>Shared media</Text>
          <Text style={styles.subtitle}>{chat.title}</Text>
        </View>
        <Pressable
          disabled={refreshing || loading}
          onPress={() => void loadBuckets(false)}
          style={[styles.secondaryButton, (refreshing || loading) && styles.disabled]}
        >
          <Text style={styles.secondaryButtonText}>{refreshing ? "Refreshing..." : "Refresh"}</Text>
        </Pressable>
      </View>

      {loading ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}
      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.heroCard}>
          <Avatar uri={chat.photoUrl} size={72} title={chat.title} />
          <View style={styles.heroCopy}>
            <Text style={styles.heroTitle}>{chat.title}</Text>
            <Text style={styles.heroMeta}>
              {buckets?.media.length ?? 0} media - {buckets?.files.length ?? 0} files -{" "}
              {buckets?.links.length ?? 0} links
            </Text>
            <Text style={styles.heroMeta}>
              {buckets?.loadedAt
                ? `Updated ${new Date(buckets.loadedAt).toLocaleString()}`
                : "Shared content will appear here after sync."}
            </Text>
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Photos and videos</Text>
          {buckets && buckets.media.length > 0 ? (
            <View style={styles.mediaGrid}>
              {buckets.media.map((entry) => (
                <Pressable
                  key={`${entry.messageId}:${entry.attachment.attachmentId}`}
                  onPress={() =>
                    onOpenMediaViewer({
                      attachments: mediaAttachments,
                      initialAttachmentId: entry.attachment.attachmentId,
                      chatTitle: chat.title
                    })
                  }
                  style={styles.mediaCard}
                >
                  {entry.attachment.previewUrl || entry.attachment.thumbnailUrl ? (
                    <Image
                      source={{
                        uri: entry.attachment.previewUrl ?? entry.attachment.thumbnailUrl ?? ""
                      }}
                      style={styles.mediaPreview}
                    />
                  ) : (
                    <View style={styles.mediaFallback}>
                      <Text style={styles.mediaFallbackText}>
                        {attachmentLabel(entry.attachment)}
                      </Text>
                    </View>
                  )}
                  <View style={styles.mediaMeta}>
                    <Text numberOfLines={1} style={styles.mediaLabel}>
                      {attachmentLabel(entry.attachment)}
                    </Text>
                    <Text style={styles.mediaHint}>
                      {new Date(entry.createdAt).toLocaleDateString()}
                    </Text>
                  </View>
                </Pressable>
              ))}
            </View>
          ) : (
            <Text style={styles.emptyText}>No shared photos or videos yet.</Text>
          )}
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Files and audio</Text>
          {buckets && buckets.files.length > 0 ? (
            <View style={styles.listColumn}>
              {buckets.files.map((entry) => (
                <Pressable
                  key={`${entry.messageId}:${entry.attachment.attachmentId}`}
                  onPress={() => void handleOpenFileAttachment(entry.attachment)}
                  style={styles.fileCard}
                >
                  <View style={styles.fileCopy}>
                    <Text style={styles.fileTitle}>
                      {attachmentLabel(entry.attachment)}
                    </Text>
                    <Text style={styles.fileMeta}>
                      {entry.attachment.contentType} - {formatFileSize(entry.attachment.fileSizeBytes)}
                    </Text>
                    <Text style={styles.fileMeta}>
                      {entry.senderDisplayName ?? "Unknown sender"} -{" "}
                      {new Date(entry.createdAt).toLocaleString()}
                    </Text>
                  </View>
                  <Text style={styles.fileActionText}>
                    {openingAttachmentId === entry.attachment.attachmentId ? "Opening..." : "Open"}
                  </Text>
                </Pressable>
              ))}
            </View>
          ) : (
            <Text style={styles.emptyText}>No shared files yet.</Text>
          )}
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Links</Text>
          {buckets && buckets.links.length > 0 ? (
            <View style={styles.listColumn}>
              {buckets.links.map((entry) => (
                <Pressable
                  key={entry.linkId}
                  onPress={() => void handleOpenLink(entry.linkId, entry.url)}
                  style={styles.linkCard}
                >
                  <Text style={styles.linkUrl}>{entry.url}</Text>
                  {entry.label ? (
                    <Text numberOfLines={2} style={styles.linkLabel}>
                      {entry.label}
                    </Text>
                  ) : null}
                  <Text style={styles.linkMeta}>
                    {openingLinkId === entry.linkId
                      ? "Opening..."
                      : new Date(entry.createdAt).toLocaleString()}
                  </Text>
                </Pressable>
              ))}
            </View>
          ) : (
            <Text style={styles.emptyText}>No links found in recent history.</Text>
          )}
        </View>
      </ScrollView>
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
    gap: 12,
    marginBottom: 16
  },
  headerCopy: {
    flex: 1,
    gap: 2
  },
  title: {
    fontSize: 24,
    fontWeight: "700",
    color: "#0f172a"
  },
  subtitle: {
    color: "#64748b"
  },
  loader: {
    marginBottom: 12
  },
  content: {
    gap: 18,
    paddingBottom: 32
  },
  heroCard: {
    borderRadius: 20,
    backgroundColor: "#ffffff",
    padding: 18,
    flexDirection: "row",
    alignItems: "center",
    gap: 14
  },
  heroCopy: {
    flex: 1,
    gap: 4
  },
  heroTitle: {
    fontSize: 20,
    fontWeight: "700",
    color: "#0f172a"
  },
  heroMeta: {
    color: "#64748b"
  },
  section: {
    gap: 12
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: "700",
    color: "#0f172a"
  },
  mediaGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 12
  },
  mediaCard: {
    width: "47%",
    borderRadius: 18,
    backgroundColor: "#ffffff",
    overflow: "hidden"
  },
  mediaPreview: {
    width: "100%",
    height: 148,
    backgroundColor: "#dbeafe"
  },
  mediaFallback: {
    width: "100%",
    height: 148,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 12
  },
  mediaFallbackText: {
    color: "#334155",
    fontWeight: "700",
    textAlign: "center"
  },
  mediaMeta: {
    padding: 12,
    gap: 4
  },
  mediaLabel: {
    color: "#0f172a",
    fontWeight: "700"
  },
  mediaHint: {
    color: "#64748b",
    fontSize: 12
  },
  listColumn: {
    gap: 10
  },
  fileCard: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16,
    flexDirection: "row",
    gap: 12,
    alignItems: "center"
  },
  fileCopy: {
    flex: 1,
    gap: 4
  },
  fileTitle: {
    color: "#0f172a",
    fontWeight: "700"
  },
  fileMeta: {
    color: "#64748b"
  },
  fileActionText: {
    color: "#1d4ed8",
    fontWeight: "700"
  },
  linkCard: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16,
    gap: 6
  },
  linkUrl: {
    color: "#1d4ed8",
    fontWeight: "700"
  },
  linkLabel: {
    color: "#334155"
  },
  linkMeta: {
    color: "#64748b",
    fontSize: 12
  },
  emptyText: {
    color: "#64748b"
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
  errorText: {
    color: "#b91c1c",
    marginBottom: 12
  },
  disabled: {
    opacity: 0.6
  }
});
