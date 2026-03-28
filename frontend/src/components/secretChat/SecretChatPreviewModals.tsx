import React from "react";
import { Image, Modal, Pressable, StyleSheet, Text, View } from "react-native";
import { ResizeMode, Video } from "expo-av";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";

type FocusedSecretMedia = {
  name: string;
  uri: string;
} | null;

type SecretChatPreviewModalsProps = {
  focusedImage: FocusedSecretMedia;
  focusedVideo: FocusedSecretMedia;
  focusedVideoPlaying: boolean;
  onCloseFocusedImage: () => void;
  onCloseFocusedVideo: () => void;
  onRestrictedActionNotice: () => void;
  onToggleFocusedVideoPlayback: () => void;
};

export function SecretChatPreviewModals({
  focusedImage,
  focusedVideo,
  focusedVideoPlaying,
  onCloseFocusedImage,
  onCloseFocusedVideo,
  onRestrictedActionNotice,
  onToggleFocusedVideoPlayback
}: SecretChatPreviewModalsProps) {
  return (
    <>
      <Modal
        animationType="fade"
        onRequestClose={onCloseFocusedImage}
        transparent
        visible={!!focusedImage}
      >
        <Pressable onLongPress={onRestrictedActionNotice} style={styles.backdrop}>
          <View style={styles.modalCard}>
            {focusedImage ? (
              <>
                <Image resizeMode="contain" source={{ uri: focusedImage.uri }} style={styles.focusedImage} />
                <Text style={styles.caption}>{focusedImage.name}</Text>
              </>
            ) : null}
            <Pressable onPress={onCloseFocusedImage} style={styles.closeButton}>
              <Text style={styles.closeButtonText}>Close</Text>
            </Pressable>
          </View>
        </Pressable>
      </Modal>

      <Modal
        animationType="fade"
        onRequestClose={onCloseFocusedVideo}
        transparent
        visible={!!focusedVideo}
      >
        <Pressable onLongPress={onRestrictedActionNotice} style={styles.backdrop}>
          <View style={styles.modalCard}>
            {focusedVideo ? (
              <>
                <Pressable onPress={onToggleFocusedVideoPlayback} style={styles.videoFrame}>
                  <Video
                    isLooping
                    resizeMode={ResizeMode.COVER}
                    shouldPlay={focusedVideoPlaying}
                    source={{ uri: focusedVideo.uri }}
                    style={styles.focusedVideo}
                  />
                  <View style={styles.videoBadge}>
                    <Text style={styles.videoBadgeText}>
                      {focusedVideoPlaying ? "Pause" : "Play"}
                    </Text>
                  </View>
                </Pressable>
                <Text style={styles.caption}>{focusedVideo.name}</Text>
                <Text style={styles.hint}>Local-only playback. Saving and forwarding are disabled.</Text>
              </>
            ) : null}
            <Pressable onPress={onCloseFocusedVideo} style={styles.closeButton}>
              <Text style={styles.closeButtonText}>Close</Text>
            </Pressable>
          </View>
        </Pressable>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    alignItems: "center",
    backgroundColor: "rgba(15, 23, 42, 0.82)",
    flex: 1,
    justifyContent: "center",
    padding: appSpacing.xl
  },
  modalCard: {
    alignItems: "center",
    backgroundColor: "#ffffff",
    borderRadius: appRadii.xl,
    gap: appSpacing.md,
    padding: appSpacing.lg,
    width: "100%"
  },
  focusedImage: {
    height: 360,
    width: "100%"
  },
  videoFrame: {
    borderRadius: appRadii.lg,
    overflow: "hidden",
    width: "100%"
  },
  focusedVideo: {
    backgroundColor: appColors.textPrimary,
    height: 360,
    width: "100%"
  },
  videoBadge: {
    backgroundColor: "rgba(15, 23, 42, 0.65)",
    borderRadius: appRadii.pill,
    paddingHorizontal: appSpacing.md,
    paddingVertical: appSpacing.sm,
    position: "absolute",
    right: appSpacing.md,
    top: appSpacing.md
  },
  videoBadgeText: {
    color: appColors.inverse,
    fontWeight: "700"
  },
  caption: {
    color: appColors.textPrimary,
    fontWeight: "700",
    textAlign: "center"
  },
  hint: {
    color: appColors.textSecondary,
    textAlign: "center"
  },
  closeButton: {
    backgroundColor: appColors.surfaceAccent,
    borderRadius: appRadii.md,
    paddingHorizontal: appSpacing.lg,
    paddingVertical: appSpacing.md
  },
  closeButtonText: {
    color: appColors.brandText,
    fontWeight: "700"
  }
});
