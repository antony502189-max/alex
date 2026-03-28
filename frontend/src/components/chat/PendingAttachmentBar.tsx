import React, { useEffect, useState } from "react";
import { Image, StyleSheet, Text, View } from "react-native";
import { AppButton } from "../ui/AppButton";
import { AppPanel } from "../ui/AppPanel";
import { AppTextField } from "../ui/AppTextField";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { MessageAttachment } from "../../types";

type PendingAttachmentMoveDirection = "EARLIER" | "LATER";

export type PendingAttachmentBarItem = {
  attachment: MessageAttachment;
  canMoveEarlier: boolean;
  canMoveLater: boolean;
  canRetryUpload: boolean;
  canTrim: boolean;
  dimensionLabel?: string | null;
  imagePreviewUrl?: string | null;
  metaLabel: string;
  progress?: number | null;
  progressLabel?: string | null;
  statusLabel?: string | null;
  statusTone?: "brand" | "danger" | "info" | "success" | "warning";
  title: string;
  transferMeta?: string | null;
  waveform?: React.ReactNode;
};

export type PendingAttachmentBarSummary = {
  description: string;
  title: string;
  tone: "brand" | "danger" | "info" | "success" | "warning";
} | null;

type PendingAttachmentBarProps = {
  items: PendingAttachmentBarItem[];
  onMoveAttachment: (attachmentId: string, direction: PendingAttachmentMoveDirection) => void;
  onRemoveAttachment: (attachment: MessageAttachment) => void | Promise<void>;
  onRetryAttachment: (attachment: MessageAttachment) => void | Promise<void>;
  onTrimAttachment: (
    attachment: MessageAttachment,
    startMs: number,
    endMs: number
  ) => boolean | Promise<boolean>;
  summary?: PendingAttachmentBarSummary;
  trimmingAttachmentId: string | null;
  uploadingAttachments: boolean;
};

function formatSeconds(valueMs: number | null | undefined) {
  const seconds = Math.max(0, (valueMs ?? 0) / 1000);
  if (Number.isInteger(seconds)) {
    return `${seconds}`;
  }
  return seconds.toFixed(1).replace(/\.0$/, "");
}

function parseSeconds(value: string) {
  const normalized = value.trim().replace(",", ".");
  if (!normalized) {
    return null;
  }

  const parsed = Number.parseFloat(normalized);
  if (!Number.isFinite(parsed) || parsed < 0) {
    return null;
  }

  return parsed;
}

export function PendingAttachmentBar({
  items,
  onMoveAttachment,
  onRemoveAttachment,
  onRetryAttachment,
  onTrimAttachment,
  summary = null,
  trimmingAttachmentId,
  uploadingAttachments
}: PendingAttachmentBarProps) {
  const [activeTrimAttachmentId, setActiveTrimAttachmentId] = useState<string | null>(null);
  const [trimStartSeconds, setTrimStartSeconds] = useState("0");
  const [trimEndSeconds, setTrimEndSeconds] = useState("");

  useEffect(() => {
    if (!activeTrimAttachmentId) {
      return;
    }

    if (!items.some((item) => item.attachment.attachmentId === activeTrimAttachmentId)) {
      setActiveTrimAttachmentId(null);
      setTrimStartSeconds("0");
      setTrimEndSeconds("");
    }
  }, [activeTrimAttachmentId, items]);

  if (items.length === 0) {
    return null;
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Pending attachments</Text>
      <Text style={styles.caption}>
        Reorder attachments before send, trim uploaded clips, or retry any upload that fell back
        to local staging.
      </Text>
      {summary ? (
        <AppPanel
          description={summary.description}
          descriptionStyle={styles.summaryDescription}
          style={styles.summaryPanel}
          title={summary.title}
          titleStyle={styles.summaryTitle}
          tone={summary.tone}
        />
      ) : null}
      <View style={styles.list}>
        {items.map((item) => {
          const durationSeconds = Math.max(0, (item.attachment.durationMs ?? 0) / 1000);
          const trimEditorVisible = activeTrimAttachmentId === item.attachment.attachmentId;
          const parsedStartSeconds = trimEditorVisible ? parseSeconds(trimStartSeconds) : null;
          const parsedEndSeconds = trimEditorVisible ? parseSeconds(trimEndSeconds) : null;
          const trimValidationMessage = trimEditorVisible
            ? parsedStartSeconds == null || parsedEndSeconds == null
              ? "Enter start and end in seconds."
              : parsedEndSeconds <= parsedStartSeconds
                ? "End time must be greater than start time."
                : parsedEndSeconds > durationSeconds
                  ? `End time must stay within ${formatSeconds(item.attachment.durationMs)}s.`
                  : parsedStartSeconds === 0 && parsedEndSeconds === durationSeconds
                    ? "Pick a shorter range before applying trim."
                    : null
            : null;
          const trimApplyDisabled =
            !trimEditorVisible ||
            trimValidationMessage !== null ||
            uploadingAttachments ||
            trimmingAttachmentId === item.attachment.attachmentId;

          return (
            <View key={item.attachment.attachmentId} style={styles.card}>
              <View style={styles.cardHeader}>
                {item.imagePreviewUrl ? (
                  <Image source={{ uri: item.imagePreviewUrl }} style={styles.imagePreview} />
                ) : null}
                <View style={styles.textBlock}>
                  <Text style={styles.name}>{item.title}</Text>
                  <Text style={styles.meta}>{item.metaLabel}</Text>
                  {item.dimensionLabel ? <Text style={styles.meta}>{item.dimensionLabel}</Text> : null}
                  {item.waveform}
                  {item.transferMeta ? <Text style={styles.meta}>{item.transferMeta}</Text> : null}
                  {item.statusLabel ? (
                    <View
                      style={[
                        styles.statusBadge,
                        item.statusTone === "danger"
                          ? styles.statusBadgeDanger
                          : item.statusTone === "warning"
                            ? styles.statusBadgeWarning
                            : item.statusTone === "success"
                              ? styles.statusBadgeSuccess
                              : item.statusTone === "brand"
                                ? styles.statusBadgeBrand
                                : styles.statusBadgeInfo
                      ]}
                    >
                      <Text style={styles.statusBadgeText}>{item.statusLabel}</Text>
                    </View>
                  ) : null}
                  {item.progress != null ? (
                    <View style={styles.progressBlock}>
                      <View style={styles.progressTrack}>
                        <View
                          style={[
                            styles.progressFill,
                            { width: `${Math.max(0, Math.min(1, item.progress)) * 100}%` }
                          ]}
                        />
                      </View>
                      {item.progressLabel ? (
                        <Text style={styles.progressLabel}>{item.progressLabel}</Text>
                      ) : null}
                    </View>
                  ) : null}
                </View>
                <View style={styles.actionsColumn}>
                  <View style={styles.actionRow}>
                    <AppButton
                      disabled={!item.canMoveEarlier}
                      onPress={() => onMoveAttachment(item.attachment.attachmentId, "EARLIER")}
                      size="sm"
                      variant="secondary"
                    >
                      Earlier
                    </AppButton>
                    <AppButton
                      disabled={!item.canMoveLater}
                      onPress={() => onMoveAttachment(item.attachment.attachmentId, "LATER")}
                      size="sm"
                      variant="secondary"
                    >
                      Later
                    </AppButton>
                  </View>
                  {item.canTrim ? (
                    <AppButton
                      disabled={uploadingAttachments || trimmingAttachmentId !== null}
                      onPress={() => {
                        if (trimEditorVisible) {
                          setActiveTrimAttachmentId(null);
                          setTrimStartSeconds("0");
                          setTrimEndSeconds("");
                          return;
                        }
                        setActiveTrimAttachmentId(item.attachment.attachmentId);
                        setTrimStartSeconds("0");
                        setTrimEndSeconds(formatSeconds(item.attachment.durationMs));
                      }}
                      size="sm"
                      variant="secondary"
                    >
                      {trimEditorVisible ? "Hide trim" : "Trim clip"}
                    </AppButton>
                  ) : null}
                  {item.canRetryUpload ? (
                    <AppButton
                      disabled={uploadingAttachments}
                      onPress={() => void onRetryAttachment(item.attachment)}
                      size="sm"
                      variant="secondary"
                    >
                      {uploadingAttachments ? "..." : "Retry"}
                    </AppButton>
                  ) : null}
                  <AppButton
                    onPress={() => void onRemoveAttachment(item.attachment)}
                    size="sm"
                    variant="danger"
                  >
                    Remove
                  </AppButton>
                </View>
              </View>
              {trimEditorVisible ? (
                <View style={styles.trimEditor}>
                  <Text style={styles.trimTitle}>Trim before send</Text>
                  <Text style={styles.trimCaption}>
                    Clip this attachment to a shorter range. Current duration:{" "}
                    {formatSeconds(item.attachment.durationMs)}s
                  </Text>
                  <View style={styles.trimFieldsRow}>
                    <View style={styles.trimField}>
                      <Text style={styles.trimFieldLabel}>Start (s)</Text>
                      <AppTextField
                        keyboardType="decimal-pad"
                        onChangeText={setTrimStartSeconds}
                        placeholder="Start (s)"
                        value={trimStartSeconds}
                      />
                    </View>
                    <View style={styles.trimField}>
                      <Text style={styles.trimFieldLabel}>End (s)</Text>
                      <AppTextField
                        keyboardType="decimal-pad"
                        onChangeText={setTrimEndSeconds}
                        placeholder="End (s)"
                        value={trimEndSeconds}
                      />
                    </View>
                  </View>
                  {trimValidationMessage ? (
                    <Text style={styles.trimError}>{trimValidationMessage}</Text>
                  ) : null}
                  <View style={styles.actionRow}>
                    <AppButton
                      disabled={trimApplyDisabled}
                      onPress={() => {
                        if (parsedStartSeconds == null || parsedEndSeconds == null) {
                          return;
                        }
                        void Promise.resolve(
                          onTrimAttachment(
                            item.attachment,
                            Math.round(parsedStartSeconds * 1000),
                            Math.round(parsedEndSeconds * 1000)
                          )
                        )
                          .then((trimmed) => {
                            if (!trimmed) {
                              return;
                            }
                            setActiveTrimAttachmentId(null);
                            setTrimStartSeconds("0");
                            setTrimEndSeconds("");
                          })
                          .catch(() => undefined);
                      }}
                      size="sm"
                      variant="primary"
                    >
                      {trimmingAttachmentId === item.attachment.attachmentId
                        ? "Trimming..."
                        : "Apply trim"}
                    </AppButton>
                    <AppButton
                      onPress={() => {
                        setActiveTrimAttachmentId(null);
                        setTrimStartSeconds("0");
                        setTrimEndSeconds("");
                      }}
                      size="sm"
                      variant="secondary"
                    >
                      Cancel
                    </AppButton>
                  </View>
                </View>
              ) : null}
            </View>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: appColors.surface,
    borderTopColor: appColors.border,
    borderTopWidth: 1,
    paddingHorizontal: appSpacing.lg,
    paddingTop: appSpacing.md
  },
  title: {
    color: appColors.textPrimary,
    fontWeight: "700",
    marginBottom: appSpacing.sm
  },
  caption: {
    color: appColors.textSecondary,
    fontSize: 12,
    lineHeight: 18,
    marginBottom: appSpacing.md
  },
  list: {
    gap: appSpacing.sm,
    paddingBottom: appSpacing.md
  },
  summaryPanel: {
    marginBottom: appSpacing.md
  },
  summaryTitle: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  summaryDescription: {
    color: appColors.textSecondary
  },
  card: {
    backgroundColor: appColors.background,
    borderRadius: appRadii.lg,
    gap: appSpacing.md,
    paddingHorizontal: appSpacing.md,
    paddingVertical: appSpacing.sm + 2
  },
  cardHeader: {
    alignItems: "center",
    flexDirection: "row",
    gap: appSpacing.md,
    justifyContent: "space-between"
  },
  imagePreview: {
    backgroundColor: appColors.surfaceAccent,
    borderRadius: appRadii.md,
    height: 48,
    width: 48
  },
  textBlock: {
    flex: 1
  },
  name: {
    color: appColors.textPrimary,
    fontWeight: "600"
  },
  meta: {
    color: appColors.textSecondary,
    fontSize: 12,
    marginTop: 2
  },
  statusBadge: {
    alignSelf: "flex-start",
    borderRadius: appRadii.pill,
    marginTop: appSpacing.xs,
    paddingHorizontal: appSpacing.sm,
    paddingVertical: appSpacing.xs
  },
  statusBadgeInfo: {
    backgroundColor: "#e0f2fe"
  },
  statusBadgeBrand: {
    backgroundColor: "#dbeafe"
  },
  statusBadgeSuccess: {
    backgroundColor: "#dcfce7"
  },
  statusBadgeWarning: {
    backgroundColor: "#fef3c7"
  },
  statusBadgeDanger: {
    backgroundColor: "#fee2e2"
  },
  statusBadgeText: {
    color: appColors.textPrimary,
    fontSize: 11,
    fontWeight: "700"
  },
  progressBlock: {
    gap: appSpacing.xs,
    marginTop: appSpacing.xs
  },
  progressTrack: {
    backgroundColor: appColors.border,
    borderRadius: appRadii.pill,
    height: 6,
    overflow: "hidden",
    width: "100%"
  },
  progressFill: {
    backgroundColor: appColors.brand,
    borderRadius: appRadii.pill,
    height: "100%"
  },
  progressLabel: {
    color: appColors.textSecondary,
    fontSize: 11
  },
  actionsColumn: {
    alignItems: "flex-end",
    gap: appSpacing.sm
  },
  actionRow: {
    flexDirection: "row",
    gap: appSpacing.sm
  },
  trimEditor: {
    borderTopColor: appColors.border,
    borderTopWidth: 1,
    gap: appSpacing.sm,
    marginTop: appSpacing.md,
    paddingTop: appSpacing.md
  },
  trimTitle: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  trimCaption: {
    color: appColors.textSecondary,
    fontSize: 12,
    lineHeight: 18
  },
  trimFieldsRow: {
    flexDirection: "row",
    gap: appSpacing.sm
  },
  trimField: {
    flex: 1,
    gap: appSpacing.xs
  },
  trimFieldLabel: {
    color: appColors.textSecondary,
    fontSize: 12,
    fontWeight: "600"
  },
  trimError: {
    color: appColors.danger,
    fontSize: 12
  }
});
