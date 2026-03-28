import React from "react";
import {
  FlatList,
  Image,
  Pressable,
  StyleSheet,
  Text,
  View
} from "react-native";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { SecretChatAttachment } from "../../types";
import {
  buildResolvedSecretMessageMeta,
  buildSecretAttachmentActionLabel,
  buildSecretAttachmentMeta,
  type ResolvedSecretChatMessage
} from "./secretChatPresentation";

type SecretChatMessagesListProps = {
  currentUserId: string;
  decryptedAttachmentUris: Record<string, string>;
  messages: ResolvedSecretChatMessage[];
  onOpenAttachment: (attachment: SecretChatAttachment) => void;
  onRestrictedActionNotice: () => void;
  onToggleVoicePlayback: (attachment: SecretChatAttachment) => void;
  openingAttachmentId: string | null;
  playingVoiceAttachmentId: string | null;
};

function MessageAttachmentCard({
  attachment,
  decryptedAttachmentUris,
  onOpenAttachment,
  onRestrictedActionNotice,
  onToggleVoicePlayback,
  openingAttachmentId,
  playingVoiceAttachmentId
}: {
  attachment: SecretChatAttachment;
  decryptedAttachmentUris: Record<string, string>;
  onOpenAttachment: SecretChatMessagesListProps["onOpenAttachment"];
  onRestrictedActionNotice: () => void;
  onToggleVoicePlayback: SecretChatMessagesListProps["onToggleVoicePlayback"];
  openingAttachmentId: string | null;
  playingVoiceAttachmentId: string | null;
}) {
  const localImageUri = decryptedAttachmentUris[attachment.attachmentId] ?? null;
  const imageVisible = attachment.kind === "IMAGE" && !!localImageUri;

  if (attachment.kind === "VOICE") {
    return (
      <Pressable
        delayLongPress={250}
        onLongPress={onRestrictedActionNotice}
        onPress={() => onToggleVoicePlayback(attachment)}
        style={styles.voiceCard}
      >
        <Text style={styles.voiceTitle}>Secret voice note</Text>
        <Text style={styles.attachmentMeta}>{buildSecretAttachmentMeta(attachment)}</Text>
        <Text style={styles.attachmentMeta}>
          {playingVoiceAttachmentId === attachment.attachmentId ? "Stop" : "Play"}
        </Text>
      </Pressable>
    );
  }

  if (attachment.kind === "VIDEO") {
    return (
      <Pressable
        key={attachment.attachmentId}
        onPress={() => onOpenAttachment(attachment)}
        style={styles.videoCard}
      >
        <View style={styles.videoCircle}>
          <Text style={styles.videoCircleText}>Play</Text>
        </View>
        <Text style={styles.voiceTitle}>Secret video note</Text>
        <Text style={styles.attachmentMeta}>{buildSecretAttachmentMeta(attachment)}</Text>
        <Text style={styles.attachmentMeta}>
          {buildSecretAttachmentActionLabel({
            attachment,
            imageVisible,
            opening: openingAttachmentId === attachment.attachmentId
          })}
        </Text>
      </Pressable>
    );
  }

  return (
    <Pressable
      delayLongPress={250}
      onLongPress={onRestrictedActionNotice}
      onPress={() => onOpenAttachment(attachment)}
      style={attachment.kind === "IMAGE" ? styles.imageCard : styles.attachmentCard}
    >
      {imageVisible ? (
        <Image source={{ uri: localImageUri }} style={styles.imageAttachment} />
      ) : (
        <View style={styles.attachmentPlaceholder}>
          <Text style={styles.attachmentPlaceholderText}>
            {attachment.kind === "IMAGE" ? "Encrypted photo" : "Encrypted file"}
          </Text>
        </View>
      )}
      <Text style={styles.attachmentName}>{attachment.originalFileName}</Text>
      <Text style={styles.attachmentMeta}>{buildSecretAttachmentMeta(attachment)}</Text>
      <Text style={styles.attachmentMeta}>
        {buildSecretAttachmentActionLabel({
          attachment,
          imageVisible,
          opening: openingAttachmentId === attachment.attachmentId
        })}
      </Text>
    </Pressable>
  );
}

export function SecretChatMessagesList({
  currentUserId,
  decryptedAttachmentUris,
  messages,
  onOpenAttachment,
  onRestrictedActionNotice,
  onToggleVoicePlayback,
  openingAttachmentId,
  playingVoiceAttachmentId
}: SecretChatMessagesListProps) {
  return (
    <FlatList
      contentContainerStyle={styles.messagesContent}
      data={messages}
      keyExtractor={(item) => item.raw.secretMessageId}
      keyboardShouldPersistTaps="handled"
      ListEmptyComponent={
        <SectionCard
          description="Encrypted messages will appear here after the device handshake is complete."
          style={styles.emptyCard}
          title="No secret messages yet"
        />
      }
      renderItem={({ item }) => {
        const isMine = item.raw.senderUserId === currentUserId;
        return (
          <Pressable
            delayLongPress={250}
            onLongPress={onRestrictedActionNotice}
            style={[styles.messageBubble, isMine ? styles.ownBubble : styles.peerBubble]}
          >
            {item.text ? (
              <Text selectable={false} style={[styles.messageText, isMine && styles.ownMessageText]}>
                {item.text}
              </Text>
            ) : null}
            {item.attachments.length > 0 ? (
              <View style={styles.attachmentsColumn}>
                {item.attachments.map((attachment) => (
                  <MessageAttachmentCard
                    key={attachment.attachmentId}
                    attachment={attachment}
                    decryptedAttachmentUris={decryptedAttachmentUris}
                    onOpenAttachment={onOpenAttachment}
                    onRestrictedActionNotice={onRestrictedActionNotice}
                    onToggleVoicePlayback={onToggleVoicePlayback}
                    openingAttachmentId={openingAttachmentId}
                    playingVoiceAttachmentId={playingVoiceAttachmentId}
                  />
                ))}
              </View>
            ) : null}
            <Text style={[styles.messageMeta, isMine && styles.ownMessageMeta]}>
              {buildResolvedSecretMessageMeta(item, currentUserId)}
            </Text>
          </Pressable>
        );
      }}
      style={styles.list}
    />
  );
}

const styles = StyleSheet.create({
  list: {
    flex: 1
  },
  messagesContent: {
    gap: appSpacing.md,
    paddingHorizontal: appSpacing.xl,
    paddingVertical: appSpacing.md
  },
  emptyCard: {
    marginTop: appSpacing.md
  },
  messageBubble: {
    borderRadius: appRadii.lg,
    gap: appSpacing.sm,
    maxWidth: "88%",
    padding: appSpacing.md
  },
  ownBubble: {
    alignSelf: "flex-end",
    backgroundColor: "#dbeafe"
  },
  peerBubble: {
    alignSelf: "flex-start",
    backgroundColor: "#ffffff"
  },
  messageText: {
    color: appColors.textPrimary,
    fontSize: 15,
    lineHeight: 21
  },
  ownMessageText: {
    color: "#0f172a"
  },
  attachmentsColumn: {
    gap: appSpacing.sm
  },
  voiceCard: {
    backgroundColor: "#eff6ff",
    borderRadius: appRadii.md,
    gap: appSpacing.xs,
    padding: appSpacing.md
  },
  voiceTitle: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  videoCard: {
    alignItems: "center",
    backgroundColor: "#eff6ff",
    borderRadius: appRadii.md,
    gap: appSpacing.xs,
    padding: appSpacing.md
  },
  videoCircle: {
    alignItems: "center",
    backgroundColor: appColors.textPrimary,
    borderRadius: 999,
    height: 72,
    justifyContent: "center",
    width: 72
  },
  videoCircleText: {
    color: appColors.inverse,
    fontWeight: "700"
  },
  imageCard: {
    backgroundColor: "#f8fafc",
    borderRadius: appRadii.md,
    gap: appSpacing.sm,
    overflow: "hidden",
    paddingBottom: appSpacing.md
  },
  attachmentCard: {
    backgroundColor: "#f8fafc",
    borderRadius: appRadii.md,
    gap: appSpacing.xs,
    padding: appSpacing.md
  },
  attachmentPlaceholder: {
    alignItems: "center",
    backgroundColor: "#e2e8f0",
    height: 160,
    justifyContent: "center"
  },
  attachmentPlaceholderText: {
    color: appColors.textSecondary,
    fontWeight: "600"
  },
  imageAttachment: {
    height: 200,
    width: "100%"
  },
  attachmentName: {
    color: appColors.textPrimary,
    fontWeight: "700",
    paddingHorizontal: appSpacing.md
  },
  attachmentMeta: {
    color: appColors.textSecondary,
    fontSize: 12,
    paddingHorizontal: appSpacing.md
  },
  messageMeta: {
    color: "#475569",
    fontSize: 12
  },
  ownMessageMeta: {
    color: "#1d4ed8"
  }
});
