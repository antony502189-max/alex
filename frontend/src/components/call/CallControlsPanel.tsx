import React from "react";
import { StyleSheet, View } from "react-native";
import type { CallControlIssue } from "./callPresentation";
import type { CallSession } from "../../types";
import { appSpacing } from "../../theme/tokens";
import { AppButton } from "../ui/AppButton";
import { AppPanel } from "../ui/AppPanel";
import { SectionCard } from "../ui/SectionCard";

type CallControlsPanelProps = {
  call: CallSession;
  canToggleCamera: boolean;
  canToggleMicrophone: boolean;
  canToggleScreenShare: boolean;
  controlIssues: CallControlIssue[];
  localAudioEnabled: boolean;
  localScreenSharing: boolean;
  localVideoEnabled: boolean;
  onToggleMute: () => void;
  onToggleScreenShare: () => void;
  onToggleSpeaker: () => void;
  onToggleVideo: () => void;
  screenShareEnabled: boolean;
  screenShareSupported: boolean;
  speakerOn: boolean;
};

export function CallControlsPanel({
  call,
  canToggleCamera,
  canToggleMicrophone,
  canToggleScreenShare,
  controlIssues,
  localAudioEnabled,
  localScreenSharing,
  localVideoEnabled,
  onToggleMute,
  onToggleScreenShare,
  onToggleSpeaker,
  onToggleVideo,
  screenShareEnabled,
  screenShareSupported,
  speakerOn
}: CallControlsPanelProps) {
  return (
    <SectionCard
      description="Quick call controls stay visible here so you can react without opening nested menus."
      title="Live controls"
    >
      <View style={styles.row}>
        <AppButton
          disabled={!canToggleMicrophone}
          onPress={onToggleMute}
          style={styles.button}
          variant={localAudioEnabled ? "primary" : "secondary"}
        >
          {localAudioEnabled ? "Mic on" : "Mic off"}
        </AppButton>
        <AppButton
          onPress={onToggleSpeaker}
          style={styles.button}
          variant={speakerOn ? "primary" : "secondary"}
        >
          {speakerOn ? "Speaker" : "Earpiece"}
        </AppButton>
        {call.kind === "VIDEO" ? (
          <AppButton
            disabled={!canToggleCamera}
            onPress={onToggleVideo}
            style={styles.button}
            variant={localVideoEnabled ? "primary" : "secondary"}
          >
            {localVideoEnabled ? "Camera on" : "Camera off"}
          </AppButton>
        ) : null}
        {screenShareEnabled ? (
          <AppButton
            disabled={!canToggleScreenShare || !screenShareSupported}
            onPress={onToggleScreenShare}
            style={styles.button}
            variant={localScreenSharing ? "primary" : "secondary"}
          >
            {localScreenSharing
              ? "Stop share"
              : screenShareSupported
                ? "Share screen"
                : "Share unavailable"}
          </AppButton>
        ) : null}
      </View>

      {controlIssues.map((issue) => (
        <AppPanel
          key={`${issue.title}:${issue.description}`}
          description={issue.description}
          title={issue.title}
          tone={issue.tone}
        />
      ))}
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  button: {
    flex: 1,
    minWidth: 120
  }
});
