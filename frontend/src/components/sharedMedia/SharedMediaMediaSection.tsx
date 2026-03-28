import React from "react";
import {
  Image,
  Pressable,
  StyleSheet,
  Text,
  View
} from "react-native";
import type {
  MessageAttachment,
  SharedMediaEntry
} from "../../types";
import { resolveAttachmentPreviewUri } from "../../services/attachmentPreviews";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import { AppButton } from "../ui/AppButton";
import { SectionCard } from "../ui/SectionCard";
import { attachmentLabel } from "./sharedMediaPresentation";

type SharedMediaMediaSectionProps = {
  chatTitle: string;
  entries: SharedMediaEntry[];
  mediaAttachments: MessageAttachment[];
  onOpenMediaViewer: (payload: {
    attachments: MessageAttachment[];
    attachmentSources?: Array<{
      attachmentId: string;
      createdAt: string;
      messageId: string;
    }>;
    chatTitle: string;
    initialAttachmentId: string;
  }) => void;
  onOpenMessage: (messageId: string, createdAt: string) => void;
};

export function SharedMediaMediaSection({
  chatTitle,
  entries,
  mediaAttachments,
  onOpenMediaViewer,
  onOpenMessage
}: SharedMediaMediaSectionProps) {
  return (
    <SectionCard
      description="Recent photos, videos, GIFs, and video notes shared in this chat."
      title="Photos and videos"
    >
      {entries.length > 0 ? (
        <View style={styles.grid}>
          {entries.map((entry) => {
            const previewUri = resolveAttachmentPreviewUri(entry.attachment);

            return (
              <View
                key={`${entry.messageId}:${entry.attachment.attachmentId}`}
                style={styles.card}
              >
                <Pressable
                  onPress={() =>
                    onOpenMediaViewer({
                      attachments: mediaAttachments,
                      attachmentSources: entries.map((mediaEntry) => ({
                        attachmentId: mediaEntry.attachment.attachmentId,
                        createdAt: mediaEntry.createdAt,
                        messageId: mediaEntry.messageId
                      })),
                      initialAttachmentId: entry.attachment.attachmentId,
                      chatTitle
                    })
                  }
                  style={({ pressed }) => [styles.previewPressable, pressed && styles.pressed]}
                >
                  {previewUri ? (
                    <Image source={{ uri: previewUri }} style={styles.preview} />
                  ) : (
                    <View style={styles.fallback}>
                      <Text style={styles.fallbackText}>{attachmentLabel(entry.attachment)}</Text>
                    </View>
                  )}
                </Pressable>
                <View style={styles.meta}>
                  <Text numberOfLines={1} style={styles.label}>
                    {attachmentLabel(entry.attachment)}
                  </Text>
                  <Text style={styles.hint}>{new Date(entry.createdAt).toLocaleDateString()}</Text>
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
        <Text style={styles.emptyText}>No shared photos or videos yet.</Text>
      )}
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  grid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.md
  },
  card: {
    backgroundColor: appColors.surface,
    borderRadius: appRadii.lg,
    overflow: "hidden",
    width: "47%"
  },
  previewPressable: {
    width: "100%"
  },
  preview: {
    backgroundColor: appColors.surfaceAccent,
    height: 148,
    width: "100%"
  },
  fallback: {
    alignItems: "center",
    backgroundColor: appColors.surfaceAccent,
    height: 148,
    justifyContent: "center",
    paddingHorizontal: appSpacing.md,
    width: "100%"
  },
  fallbackText: {
    color: appColors.textSecondary,
    fontWeight: "700",
    textAlign: "center"
  },
  meta: {
    gap: appSpacing.xs,
    padding: appSpacing.md
  },
  label: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  hint: {
    color: appColors.textSecondary,
    fontSize: 12
  },
  emptyText: {
    color: appColors.textSecondary
  },
  pressed: {
    opacity: 0.9
  }
});
