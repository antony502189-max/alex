import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { AppButton } from "../ui/AppButton";
import { appRadii, appSpacing } from "../../theme/tokens";
import { buildMediaViewerSubtitle } from "./mediaViewerPresentation";

type MediaViewerHeaderProps = {
  attachmentCount: number;
  chatTitle: string;
  currentIndex: number;
  onClose: () => void;
  onShare: () => void;
  shareDisabled: boolean;
  sharing: boolean;
};

export function MediaViewerHeader({
  attachmentCount,
  chatTitle,
  currentIndex,
  onClose,
  onShare,
  shareDisabled,
  sharing
}: MediaViewerHeaderProps) {
  return (
    <View style={styles.header}>
      <AppButton
        onPress={onClose}
        size="sm"
        style={styles.headerButton}
        textStyle={styles.headerButtonText}
      >
        Close
      </AppButton>
      <View style={styles.headerCopy}>
        <Text style={styles.title}>Media viewer</Text>
        <Text style={styles.subtitle}>
          {buildMediaViewerSubtitle(chatTitle, currentIndex, attachmentCount)}
        </Text>
      </View>
      <AppButton
        disabled={shareDisabled}
        onPress={onShare}
        size="sm"
        style={styles.headerButton}
        textStyle={styles.headerButtonText}
      >
        {sharing ? "Sharing..." : "Share"}
      </AppButton>
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    alignItems: "center",
    flexDirection: "row",
    gap: appSpacing.md,
    marginBottom: appSpacing.lg
  },
  headerCopy: {
    flex: 1,
    gap: 2
  },
  headerButton: {
    backgroundColor: "#0f172a",
    borderColor: "#334155",
    borderRadius: appRadii.md,
    borderWidth: 1
  },
  headerButtonText: {
    color: "#f8fafc"
  },
  title: {
    color: "#f8fafc",
    fontSize: 24,
    fontWeight: "700"
  },
  subtitle: {
    color: "#94a3b8"
  }
});
