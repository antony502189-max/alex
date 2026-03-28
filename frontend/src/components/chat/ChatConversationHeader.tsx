import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { Avatar } from "../Avatar";
import { AppButton } from "../ui/AppButton";

type ChatConversationHeaderProps = {
  archiveToggleLabel: string;
  chatPhotoUrl: string | null;
  chatTitle: string;
  headerSubtitle: string;
  headerTitle: string;
  muteToggleLabel: string;
  onArchive: () => void | Promise<void>;
  onBack: () => void;
  onOpenChatInfo?: () => void;
  onMute: () => void | Promise<void>;
  onOpenMembers?: () => void;
  onOpenMiniApp?: () => void | Promise<void>;
  onStartCall?: (kind: "VOICE" | "VIDEO") => void;
  onTogglePinnedHistory: () => void;
  onToggleScheduledPanel: () => void;
  pinnedHistoryToggleLabel: string;
  showInfoAction: boolean;
  showMembersAction: boolean;
  showMiniAppAction: boolean;
  showStartCallActions: boolean;
  typingLabel: string | null;
};

export function ChatConversationHeader({
  archiveToggleLabel,
  chatPhotoUrl,
  chatTitle,
  headerSubtitle,
  headerTitle,
  muteToggleLabel,
  onArchive,
  onBack,
  onOpenChatInfo,
  onMute,
  onOpenMembers,
  onOpenMiniApp,
  onStartCall,
  onTogglePinnedHistory,
  onToggleScheduledPanel,
  pinnedHistoryToggleLabel,
  showInfoAction,
  showMembersAction,
  showMiniAppAction,
  showStartCallActions,
  typingLabel
}: ChatConversationHeaderProps) {
  return (
    <View style={styles.header}>
      <AppButton onPress={onBack} size="sm" variant="secondary">
        Back
      </AppButton>
      <Avatar uri={chatPhotoUrl} title={chatTitle} size={48} />
      <View style={styles.headerText}>
        <Text style={styles.title}>{headerTitle}</Text>
        <Text style={styles.subtitle}>{headerSubtitle}</Text>
        {typingLabel ? <Text style={styles.typingLabel}>{typingLabel}</Text> : null}
      </View>
      <View style={styles.headerActions}>
        {showInfoAction && onOpenChatInfo ? (
          <AppButton onPress={onOpenChatInfo} size="sm" variant="secondary">
            Info
          </AppButton>
        ) : null}
        {showMembersAction && onOpenMembers ? (
          <AppButton onPress={onOpenMembers} size="sm" variant="secondary">
            Members
          </AppButton>
        ) : null}
        {showStartCallActions && onStartCall ? (
          <AppButton onPress={() => onStartCall("VOICE")} size="sm" variant="secondary">
            Call
          </AppButton>
        ) : null}
        {showStartCallActions && onStartCall ? (
          <AppButton onPress={() => onStartCall("VIDEO")} size="sm" variant="secondary">
            Video
          </AppButton>
        ) : null}
        {showMiniAppAction && onOpenMiniApp ? (
          <AppButton onPress={() => void onOpenMiniApp()} size="sm" variant="secondary">
            Mini App
          </AppButton>
        ) : null}
        <AppButton onPress={() => void onMute()} size="sm" variant="secondary">
          {muteToggleLabel}
        </AppButton>
        <AppButton onPress={() => void onArchive()} size="sm" variant="secondary">
          {archiveToggleLabel}
        </AppButton>
        <AppButton onPress={onToggleScheduledPanel} size="sm" variant="secondary">
          Scheduled
        </AppButton>
        <AppButton onPress={onTogglePinnedHistory} size="sm" variant="secondary">
          {pinnedHistoryToggleLabel}
        </AppButton>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    alignItems: "center",
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 12,
    paddingBottom: 12,
    paddingHorizontal: 20,
    paddingTop: 16
  },
  headerActions: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8
  },
  headerText: {
    flex: 1,
    minWidth: 160
  },
  subtitle: {
    color: "#64748b",
    marginTop: 2
  },
  title: {
    color: "#0f172a",
    fontSize: 20,
    fontWeight: "700"
  },
  typingLabel: {
    color: "#0f766e",
    fontSize: 13,
    marginTop: 4
  }
});
