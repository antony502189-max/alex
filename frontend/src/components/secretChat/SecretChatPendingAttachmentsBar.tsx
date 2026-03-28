import React from "react";
import { Image, StyleSheet, Text, View } from "react-native";
import type { PendingSecretAttachmentDraft } from "../../services/secretChatAttachments";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import { AppButton } from "../ui/AppButton";
import { SectionCard } from "../ui/SectionCard";
import {
  buildSecretAttachmentMeta,
  buildSecretAttachmentTitle
} from "./secretChatPresentation";

type SecretChatPendingAttachmentsBarProps = {
  onRemovePendingAttachment: (attachment: PendingSecretAttachmentDraft) => void;
  pendingAttachments: PendingSecretAttachmentDraft[];
};

export function SecretChatPendingAttachmentsBar({
  onRemovePendingAttachment,
  pendingAttachments
}: SecretChatPendingAttachmentsBarProps) {
  if (pendingAttachments.length === 0) {
    return null;
  }

  return (
    <SectionCard
      description="Attachments are already encrypted and staged for the next message."
      style={styles.card}
      title="Pending encrypted attachments"
    >
      <View style={styles.list}>
        {pendingAttachments.map((attachment) => (
          <View key={attachment.attachmentId} style={styles.pendingChip}>
            {attachment.kind === "IMAGE" && attachment.previewUri ? (
              <Image source={{ uri: attachment.previewUri }} style={styles.imagePreview} />
            ) : null}
            <View style={styles.textBlock}>
              <Text style={styles.name}>{buildSecretAttachmentTitle(attachment)}</Text>
              <Text style={styles.meta}>{buildSecretAttachmentMeta(attachment)}</Text>
            </View>
            <AppButton
              onPress={() => onRemovePendingAttachment(attachment)}
              size="sm"
              variant="danger"
            >
              Remove
            </AppButton>
          </View>
        ))}
      </View>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  card: {
    marginHorizontal: appSpacing.xl
  },
  list: {
    gap: appSpacing.sm
  },
  pendingChip: {
    alignItems: "center",
    backgroundColor: "#f8fbff",
    borderColor: appColors.border,
    borderRadius: appRadii.md,
    borderWidth: 1,
    flexDirection: "row",
    gap: appSpacing.md,
    padding: appSpacing.md
  },
  imagePreview: {
    borderRadius: appRadii.md,
    height: 52,
    width: 52
  },
  textBlock: {
    flex: 1,
    gap: appSpacing.xs
  },
  name: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  meta: {
    color: appColors.textSecondary,
    fontSize: 12
  }
});
