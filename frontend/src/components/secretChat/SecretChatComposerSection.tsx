import React from "react";
import { StyleSheet, View } from "react-native";
import { AppButton } from "../ui/AppButton";
import { AppPanel } from "../ui/AppPanel";
import { AppTextField } from "../ui/AppTextField";
import { SectionCard } from "../ui/SectionCard";
import { appSpacing } from "../../theme/tokens";
import { formatSecretDuration } from "./secretChatPresentation";

type SecretChatComposerSectionProps = {
  active: boolean;
  blockedNotice: string;
  draft: string;
  onDraftChange: (value: string) => void;
  onPickFile: () => void;
  onPickImage: () => void;
  onSend: () => void;
  onStartVoiceRecording: () => void;
  onStopVoiceRecording: (cancel?: boolean) => void;
  recordingDurationMs: number;
  recordingVoice: boolean;
  sendDisabled: boolean;
  sending: boolean;
  uploadingAttachments: boolean;
};

export function SecretChatComposerSection({
  active,
  blockedNotice,
  draft,
  onDraftChange,
  onPickFile,
  onPickImage,
  onSend,
  onStartVoiceRecording,
  onStopVoiceRecording,
  recordingDurationMs,
  recordingVoice,
  sendDisabled,
  sending,
  uploadingAttachments
}: SecretChatComposerSectionProps) {
  if (!active) {
    return (
      <SectionCard
        description={blockedNotice}
        style={styles.card}
        title="Secret chat pending"
      />
    );
  }

  return (
    <View style={styles.wrapper}>
      <SectionCard
        description="Messages and attachments are encrypted on this device before they leave the composer."
        style={styles.card}
        title="Encrypted composer"
      >
        <View style={styles.actionsRow}>
          <AppButton
            disabled={uploadingAttachments || sending || recordingVoice}
            onPress={onPickFile}
            style={styles.actionButton}
          >
            {uploadingAttachments ? "..." : "File/Video"}
          </AppButton>
          <AppButton
            disabled={uploadingAttachments || sending || recordingVoice}
            onPress={onPickImage}
            style={styles.actionButton}
          >
            Photo
          </AppButton>
          <AppButton
            disabled={uploadingAttachments || sending}
            onPress={() => {
              if (recordingVoice) {
                onStopVoiceRecording(false);
                return;
              }
              onStartVoiceRecording();
            }}
            style={styles.actionButton}
          >
            {recordingVoice ? "Send voice" : "Mic"}
          </AppButton>
        </View>

        <AppTextField
          editable={!sending && !uploadingAttachments && !recordingVoice}
          multiline
          onChangeText={onDraftChange}
          placeholder="Type an end-to-end encrypted message"
          value={draft}
        />

        <AppButton disabled={sendDisabled} fullWidth onPress={onSend} variant="primary">
          {sending ? "..." : "Send"}
        </AppButton>
      </SectionCard>

      {recordingVoice ? (
        <AppPanel
          description={formatSecretDuration(recordingDurationMs)}
          style={styles.recordingBar}
          title="Recording secret voice note"
          tone="brand"
        >
          <View style={styles.actionsRow}>
            <AppButton onPress={() => onStopVoiceRecording(false)} style={styles.actionButton}>
              Send
            </AppButton>
            <AppButton
              onPress={() => onStopVoiceRecording(true)}
              style={styles.actionButton}
              variant="danger"
            >
              Discard
            </AppButton>
          </View>
        </AppPanel>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    gap: appSpacing.md
  },
  card: {
    marginHorizontal: appSpacing.xl
  },
  actionsRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  actionButton: {
    flex: 1,
    minWidth: 104
  },
  recordingBar: {
    marginHorizontal: appSpacing.xl
  }
});
