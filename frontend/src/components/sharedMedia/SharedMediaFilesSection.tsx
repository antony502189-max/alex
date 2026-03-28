import React from "react";
import { StyleSheet, Text, View } from "react-native";
import type {
  MessageAttachment,
  SharedMediaEntry
} from "../../types";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import { AppButton } from "../ui/AppButton";
import { SectionCard } from "../ui/SectionCard";
import {
  attachmentLabel,
  formatFileSize,
  isAudioAttachment
} from "./sharedMediaPresentation";

type SharedMediaFilesSectionProps = {
  entries: SharedMediaEntry[];
  onOpenAttachment: (attachment: MessageAttachment) => void;
  onOpenMessage: (messageId: string, createdAt: string) => void;
  onToggleAudioAttachment: (attachment: MessageAttachment) => void;
  loadingAudioAttachmentId: string | null;
  openingAttachmentId: string | null;
  playingAudioAttachmentId: string | null;
};

export function SharedMediaFilesSection({
  entries,
  onOpenAttachment,
  onOpenMessage,
  onToggleAudioAttachment,
  loadingAudioAttachmentId,
  openingAttachmentId,
  playingAudioAttachmentId
}: SharedMediaFilesSectionProps) {
  return (
    <SectionCard
      description="Documents, voice notes, audio, and other non-media attachments."
      title="Files and audio"
    >
      {entries.length > 0 ? (
        <View style={styles.list}>
          {entries.map((entry) => {
            const audioEntry = isAudioAttachment(entry.attachment);
            const loadingAudio = loadingAudioAttachmentId === entry.attachment.attachmentId;
            const openingFile = openingAttachmentId === entry.attachment.attachmentId;
            const playingAudio = playingAudioAttachmentId === entry.attachment.attachmentId;

            return (
              <View
                key={`${entry.messageId}:${entry.attachment.attachmentId}`}
                style={styles.card}
              >
                <View style={styles.copy}>
                  <Text style={styles.title}>{attachmentLabel(entry.attachment)}</Text>
                  <Text style={styles.meta}>
                    {entry.attachment.contentType} - {formatFileSize(entry.attachment.fileSizeBytes)}
                  </Text>
                  {audioEntry && entry.attachment.durationMs ? (
                    <Text style={styles.meta}>
                      {Math.max(1, Math.round(entry.attachment.durationMs / 1000))}s clip
                    </Text>
                  ) : null}
                  <Text style={styles.meta}>
                    {entry.senderDisplayName ?? "Unknown sender"} -{" "}
                    {new Date(entry.createdAt).toLocaleString()}
                  </Text>
                </View>
                <View style={styles.actions}>
                  <AppButton
                    disabled={audioEntry ? loadingAudio : openingFile}
                    onPress={() =>
                      audioEntry
                        ? onToggleAudioAttachment(entry.attachment)
                        : onOpenAttachment(entry.attachment)
                    }
                    size="sm"
                  >
                    {audioEntry
                      ? loadingAudio
                        ? "Loading..."
                        : playingAudio
                          ? "Stop"
                          : "Play"
                      : openingFile
                        ? "Opening..."
                        : "Open"}
                  </AppButton>
                  <AppButton
                    onPress={() => onOpenMessage(entry.messageId, entry.createdAt)}
                    size="sm"
                    variant="secondary"
                  >
                    View in chat
                  </AppButton>
                </View>
              </View>
            );
          })}
        </View>
      ) : (
        <Text style={styles.emptyText}>No shared files yet.</Text>
      )}
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  list: {
    gap: appSpacing.sm + 2
  },
  card: {
    alignItems: "center",
    backgroundColor: appColors.surface,
    borderRadius: appRadii.lg,
    flexDirection: "row",
    gap: appSpacing.md,
    padding: appSpacing.lg
  },
  copy: {
    flex: 1,
    gap: appSpacing.xs
  },
  actions: {
    alignItems: "flex-end",
    gap: appSpacing.sm
  },
  title: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  meta: {
    color: appColors.textSecondary
  },
  emptyText: {
    color: appColors.textSecondary
  }
});
