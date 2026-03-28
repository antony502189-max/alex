import React from "react";
import { Image, StyleSheet, Text, View } from "react-native";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { PickedMediaFile } from "../../services/imagePicker";
import type { StoryPreset } from "./createStoryPresentation";
import {
  getCreateStoryMediaKind,
  getCreateStoryPreviewLabel
} from "./createStoryPresentation";

type CreateStoryPreviewCardProps = {
  preset: StoryPreset;
  selectedMedia: PickedMediaFile | null;
  text: string;
};

export function CreateStoryPreviewCard({
  preset,
  selectedMedia,
  text
}: CreateStoryPreviewCardProps) {
  const mediaKind = getCreateStoryMediaKind(selectedMedia);

  return (
    <View
      style={[
        styles.card,
        {
          backgroundColor: preset.backgroundFrom,
          borderColor: preset.backgroundTo
        }
      ]}
    >
      {mediaKind === "IMAGE" ? (
        <Image resizeMode="cover" source={{ uri: selectedMedia?.uri }} style={styles.image} />
      ) : null}
      {mediaKind === "VIDEO" ? (
        <View style={styles.videoPreview}>
          <Text style={styles.videoTitle}>Video story</Text>
          <Text style={styles.videoText}>{selectedMedia?.name}</Text>
          <Text style={styles.videoMeta}>Playback-ready after upload</Text>
        </View>
      ) : null}
      <View style={styles.overlay}>
        <Text style={[styles.previewText, { color: preset.textColor }]}>
          {getCreateStoryPreviewLabel(text, selectedMedia)}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: appRadii.xl,
    borderWidth: 3,
    justifyContent: "flex-end",
    minHeight: 320,
    overflow: "hidden"
  },
  image: {
    ...StyleSheet.absoluteFillObject
  },
  videoPreview: {
    ...StyleSheet.absoluteFillObject,
    alignItems: "center",
    backgroundColor: appColors.textPrimary,
    justifyContent: "center",
    paddingHorizontal: appSpacing.xxl
  },
  videoTitle: {
    color: appColors.inverse,
    fontSize: 26,
    fontWeight: "700"
  },
  videoText: {
    color: "#cbd5e1",
    marginTop: appSpacing.md,
    textAlign: "center"
  },
  videoMeta: {
    color: "#94a3b8",
    marginTop: appSpacing.sm
  },
  overlay: {
    backgroundColor: "rgba(15, 23, 42, 0.28)",
    padding: appSpacing.xxl
  },
  previewText: {
    fontSize: 26,
    fontWeight: "700",
    textAlign: "center"
  }
});
