import React from "react";
import { ResizeMode, Video } from "expo-av";
import {
  ActivityIndicator,
  Image,
  Pressable,
  StyleSheet,
  Text,
  View
} from "react-native";
import type { MessageAttachment } from "../../types";
import { appRadii, appSpacing } from "../../theme/tokens";
import {
  isImageAttachment,
  isVideoAttachment
} from "./mediaViewerPresentation";

type MediaViewerStageProps = {
  attachment: MessageAttachment | null;
  currentUri: string | null;
  loadingLocalAttachmentId: string | null;
  onToggleVideoPlayback: () => void;
  videoPlaying: boolean;
};

export function MediaViewerStage({
  attachment,
  currentUri,
  loadingLocalAttachmentId,
  onToggleVideoPlayback,
  videoPlaying
}: MediaViewerStageProps) {
  const loadingCurrentAttachment =
    !!attachment && loadingLocalAttachmentId === attachment.attachmentId;

  return (
    <View style={styles.stage}>
      {!attachment ? (
        <Text style={styles.emptyText}>No media selected.</Text>
      ) : isVideoAttachment(attachment) ? (
        currentUri ? (
          <Pressable onPress={onToggleVideoPlayback} style={styles.videoFrame}>
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
            {loadingCurrentAttachment ? (
              <ActivityIndicator color="#ffffff" />
            ) : (
              <Text style={styles.emptyText}>Video preview unavailable.</Text>
            )}
          </View>
        )
      ) : isImageAttachment(attachment) && currentUri ? (
        <Image resizeMode="contain" source={{ uri: currentUri }} style={styles.image} />
      ) : loadingCurrentAttachment ? (
        <View style={styles.loaderState}>
          <ActivityIndicator color="#ffffff" />
        </View>
      ) : (
        <View style={styles.loaderState}>
          <Text style={styles.emptyText}>Preview unavailable for this attachment.</Text>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  stage: {
    alignItems: "center",
    backgroundColor: "#0f172a",
    borderRadius: appRadii.xl + 2,
    flex: 1,
    justifyContent: "center",
    overflow: "hidden"
  },
  image: {
    height: "100%",
    width: "100%"
  },
  videoFrame: {
    alignItems: "center",
    height: "100%",
    justifyContent: "center",
    width: "100%"
  },
  video: {
    height: "100%",
    width: "100%"
  },
  videoBadge: {
    backgroundColor: "rgba(15, 23, 42, 0.8)",
    borderRadius: 999,
    bottom: appSpacing.lg,
    paddingHorizontal: 14,
    paddingVertical: 8,
    position: "absolute",
    right: appSpacing.lg
  },
  videoBadgeText: {
    color: "#f8fafc",
    fontWeight: "700"
  },
  loaderState: {
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: appSpacing.xl
  },
  emptyText: {
    color: "#cbd5e1",
    textAlign: "center"
  }
});
