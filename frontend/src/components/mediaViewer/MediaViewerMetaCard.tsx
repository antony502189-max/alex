import React from "react";
import { StyleSheet, Text, View } from "react-native";
import type { MessageAttachment } from "../../types";
import { appRadii, appSpacing } from "../../theme/tokens";
import { formatMediaViewerFileSize } from "./mediaViewerPresentation";

type MediaViewerMetaCardProps = {
  attachment: MessageAttachment | null;
  actionSlot?: React.ReactNode;
};

export function MediaViewerMetaCard({
  attachment,
  actionSlot
}: MediaViewerMetaCardProps) {
  if (!attachment) {
    return null;
  }

  return (
    <View style={styles.metaCard}>
      <Text style={styles.metaTitle}>{attachment.originalFileName}</Text>
      <Text style={styles.metaText}>
        {attachment.kind} - {formatMediaViewerFileSize(attachment.fileSizeBytes)}
      </Text>
      {attachment.width && attachment.height ? (
        <Text style={styles.metaText}>
          {attachment.width}x{attachment.height}
        </Text>
      ) : null}
      {attachment.durationMs ? (
        <Text style={styles.metaText}>{Math.round(attachment.durationMs / 1000)}s</Text>
      ) : null}
      {actionSlot ? <View style={styles.actionRow}>{actionSlot}</View> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  metaCard: {
    backgroundColor: "#0f172a",
    borderRadius: appRadii.lg,
    gap: 4,
    marginTop: appSpacing.lg,
    padding: appSpacing.lg
  },
  metaTitle: {
    color: "#f8fafc",
    fontWeight: "700"
  },
  metaText: {
    color: "#94a3b8"
  },
  actionRow: {
    marginTop: appSpacing.sm
  }
});
