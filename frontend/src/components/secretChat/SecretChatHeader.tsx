import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { Avatar } from "../Avatar";
import { AppButton } from "../ui/AppButton";
import { appColors, appSpacing } from "../../theme/tokens";
import type { SecretChatSummary } from "../../types";

type SecretChatHeaderProps = {
  closing: boolean;
  onBack: () => void;
  onCloseChat: () => void;
  secretChat: SecretChatSummary;
  statusText: string;
};

export function SecretChatHeader({
  closing,
  onBack,
  onCloseChat,
  secretChat,
  statusText
}: SecretChatHeaderProps) {
  return (
    <View style={styles.header}>
      <AppButton onPress={onBack} size="sm">
        Back
      </AppButton>
      <Avatar size={48} title={secretChat.peerDisplayName} uri={secretChat.peerPhotoUrl} />
      <View style={styles.headerText}>
        <Text style={styles.title}>{secretChat.peerDisplayName}</Text>
        <Text style={styles.subtitle}>{statusText}</Text>
        <Text style={styles.subtitle}>{secretChat.peerDeviceName ?? "Device not bound yet"}</Text>
      </View>
      <AppButton disabled={closing} onPress={onCloseChat} size="sm" variant="danger">
        {closing ? "..." : "Close"}
      </AppButton>
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    alignItems: "center",
    flexDirection: "row",
    gap: appSpacing.md,
    paddingBottom: appSpacing.md,
    paddingHorizontal: appSpacing.xl,
    paddingTop: appSpacing.lg
  },
  headerText: {
    flex: 1
  },
  title: {
    color: appColors.textPrimary,
    fontSize: 20,
    fontWeight: "700"
  },
  subtitle: {
    color: appColors.textSecondary,
    marginTop: 2
  }
});
