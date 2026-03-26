import React, { useEffect, useMemo, useState } from "react";
import { ResizeMode, Video } from "expo-av";
import * as Sharing from "expo-sharing";
import {
  ActivityIndicator,
  Image,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  View
} from "react-native";
import { attachmentTransfers } from "../services/attachmentTransfers";
import type { MessageAttachment } from "../types";

type MediaViewerScreenProps = {
  attachments: MessageAttachment[];
  initialAttachmentId: string;
  chatTitle: string;
  token: string;
  onClose: () => void;
};

function isImageAttachment(attachment: MessageAttachment) {
  return (
    attachment.kind === "IMAGE" ||
    attachment.kind === "GIF" ||
    attachment.contentType.startsWith("image/")
  );
}

function isVideoAttachment(attachment: MessageAttachment) {
  return (
    attachment.kind === "VIDEO" ||
    attachment.kind === "VIDEO_NOTE" ||
    attachment.contentType.startsWith("video/")
  );
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

export function MediaViewerScreen({
  attachments,
  initialAttachmentId,
  chatTitle,
  token,
  onClose
}: MediaViewerScreenProps) {
  const initialIndex = useMemo(() => {
    const resolvedIndex = attachments.findIndex(
      (attachment) => attachment.attachmentId === initialAttachmentId
    );
    return resolvedIndex >= 0 ? resolvedIndex : 0;
  }, [attachments, initialAttachmentId]);

  const [currentIndex, setCurrentIndex] = useState(initialIndex);
  const [resolvedUris, setResolvedUris] = useState<Record<string, string>>({});
  const [loadingLocalAttachmentId, setLoadingLocalAttachmentId] = useState<string | null>(null);
  const [sharingAttachmentId, setSharingAttachmentId] = useState<string | null>(null);
  const [videoPlaying, setVideoPlaying] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setCurrentIndex(initialIndex);
  }, [initialIndex]);

  const currentAttachment = attachments[currentIndex] ?? null;

  useEffect(() => {
    setVideoPlaying(true);
    if (!currentAttachment || !isVideoAttachment(currentAttachment)) {
      return;
    }

    const existingUri =
      currentAttachment.localUri ?? resolvedUris[currentAttachment.attachmentId] ?? null;
    if (existingUri) {
      return;
    }

    let cancelled = false;
    setLoadingLocalAttachmentId(currentAttachment.attachmentId);
    setError(null);
    attachmentTransfers
      .downloadAttachment(token, currentAttachment)
      .then((uri) => {
        if (!uri || cancelled) {
          return;
        }
        setResolvedUris((current) => ({
          ...current,
          [currentAttachment.attachmentId]: uri
        }));
      })
      .catch((loadError) => {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : "Unable to prepare media");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingLocalAttachmentId(null);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [currentAttachment, resolvedUris, token]);

  async function handleShareCurrent() {
    if (!currentAttachment) {
      return;
    }

    setSharingAttachmentId(currentAttachment.attachmentId);
    setError(null);
    try {
      const uri =
        currentAttachment.localUri ??
        resolvedUris[currentAttachment.attachmentId] ??
        (await attachmentTransfers.downloadAttachment(token, currentAttachment));
      if (!uri) {
        return;
      }
      if (await Sharing.isAvailableAsync()) {
        await Sharing.shareAsync(uri);
        return;
      }
      setError("Sharing is not available on this platform.");
    } catch (shareError) {
      setError(shareError instanceof Error ? shareError.message : "Unable to share attachment");
    } finally {
      setSharingAttachmentId(null);
    }
  }

  const currentUri = currentAttachment
    ? currentAttachment.localUri ??
      resolvedUris[currentAttachment.attachmentId] ??
      currentAttachment.previewUrl ??
      currentAttachment.thumbnailUrl ??
      null
    : null;

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable onPress={onClose} style={styles.headerButton}>
          <Text style={styles.headerButtonText}>Close</Text>
        </Pressable>
        <View style={styles.headerCopy}>
          <Text style={styles.title}>Media viewer</Text>
          <Text style={styles.subtitle}>
            {chatTitle}
            {attachments.length > 0 ? ` - ${currentIndex + 1}/${attachments.length}` : ""}
          </Text>
        </View>
        <Pressable
          disabled={!currentAttachment || sharingAttachmentId !== null}
          onPress={() => void handleShareCurrent()}
          style={[styles.headerButton, (!currentAttachment || sharingAttachmentId !== null) && styles.disabled]}
        >
          <Text style={styles.headerButtonText}>
            {sharingAttachmentId ? "Sharing..." : "Share"}
          </Text>
        </Pressable>
      </View>

      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      <View style={styles.stage}>
        {!currentAttachment ? (
          <Text style={styles.emptyText}>No media selected.</Text>
        ) : isVideoAttachment(currentAttachment) ? (
          currentUri ? (
            <Pressable
              onPress={() => setVideoPlaying((current) => !current)}
              style={styles.videoFrame}
            >
              <Video
                isLooping
                resizeMode={ResizeMode.CONTAIN}
                shouldPlay={videoPlaying}
                source={{ uri: currentUri }}
                style={styles.video}
              />
              <View style={styles.videoBadge}>
                <Text style={styles.videoBadgeText}>{videoPlaying ? "Pause" : "Play"}</Text>
              </View>
            </Pressable>
          ) : (
            <View style={styles.loaderState}>
              {loadingLocalAttachmentId === currentAttachment.attachmentId ? (
                <ActivityIndicator color="#ffffff" />
              ) : (
                <Text style={styles.emptyText}>Video preview unavailable.</Text>
              )}
            </View>
          )
        ) : isImageAttachment(currentAttachment) && currentUri ? (
          <Image resizeMode="contain" source={{ uri: currentUri }} style={styles.image} />
        ) : (
          <View style={styles.loaderState}>
            <Text style={styles.emptyText}>Preview unavailable for this attachment.</Text>
          </View>
        )}
      </View>

      {currentAttachment ? (
        <View style={styles.metaCard}>
          <Text style={styles.metaTitle}>{currentAttachment.originalFileName}</Text>
          <Text style={styles.metaText}>
            {currentAttachment.kind} - {formatFileSize(currentAttachment.fileSizeBytes)}
          </Text>
          {currentAttachment.width && currentAttachment.height ? (
            <Text style={styles.metaText}>
              {currentAttachment.width}x{currentAttachment.height}
            </Text>
          ) : null}
          {currentAttachment.durationMs ? (
            <Text style={styles.metaText}>
              {Math.round(currentAttachment.durationMs / 1000)}s
            </Text>
          ) : null}
        </View>
      ) : null}

      {attachments.length > 1 ? (
        <View style={styles.navigationRow}>
          <Pressable
            disabled={currentIndex === 0}
            onPress={() => setCurrentIndex((current) => Math.max(0, current - 1))}
            style={[styles.navButton, currentIndex === 0 && styles.disabled]}
          >
            <Text style={styles.navButtonText}>Previous</Text>
          </Pressable>
          <Pressable
            disabled={currentIndex >= attachments.length - 1}
            onPress={() =>
              setCurrentIndex((current) => Math.min(attachments.length - 1, current + 1))
            }
            style={[styles.navButton, currentIndex >= attachments.length - 1 && styles.disabled]}
          >
            <Text style={styles.navButtonText}>Next</Text>
          </Pressable>
        </View>
      ) : null}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: "#020617",
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
  headerButton: {
    borderRadius: 12,
    backgroundColor: "#0f172a",
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderWidth: 1,
    borderColor: "#334155"
  },
  headerButtonText: {
    color: "#f8fafc",
    fontWeight: "700"
  },
  title: {
    fontSize: 24,
    fontWeight: "700",
    color: "#f8fafc"
  },
  subtitle: {
    color: "#94a3b8"
  },
  stage: {
    flex: 1,
    borderRadius: 24,
    backgroundColor: "#0f172a",
    overflow: "hidden",
    alignItems: "center",
    justifyContent: "center"
  },
  image: {
    width: "100%",
    height: "100%"
  },
  videoFrame: {
    width: "100%",
    height: "100%",
    alignItems: "center",
    justifyContent: "center"
  },
  video: {
    width: "100%",
    height: "100%"
  },
  videoBadge: {
    position: "absolute",
    bottom: 16,
    right: 16,
    borderRadius: 999,
    backgroundColor: "rgba(15, 23, 42, 0.8)",
    paddingHorizontal: 14,
    paddingVertical: 8
  },
  videoBadgeText: {
    color: "#f8fafc",
    fontWeight: "700"
  },
  loaderState: {
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 20
  },
  metaCard: {
    marginTop: 16,
    borderRadius: 18,
    backgroundColor: "#0f172a",
    padding: 16,
    gap: 4
  },
  metaTitle: {
    color: "#f8fafc",
    fontWeight: "700"
  },
  metaText: {
    color: "#94a3b8"
  },
  navigationRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    gap: 12,
    marginTop: 16
  },
  navButton: {
    flex: 1,
    borderRadius: 14,
    backgroundColor: "#1e293b",
    paddingVertical: 14,
    alignItems: "center"
  },
  navButtonText: {
    color: "#f8fafc",
    fontWeight: "700"
  },
  errorText: {
    color: "#fca5a5",
    marginBottom: 12
  },
  emptyText: {
    color: "#cbd5e1",
    textAlign: "center"
  },
  disabled: {
    opacity: 0.5
  }
});
