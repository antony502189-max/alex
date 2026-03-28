import React from "react";
import {
  Image,
  StyleSheet,
  Text,
  View,
  type NativeSyntheticEvent,
  type TextInputSelectionChangeEventData
} from "react-native";
import { AppButton } from "../ui/AppButton";
import { AppPanel } from "../ui/AppPanel";
import { AppTextField } from "../ui/AppTextField";
import { resolveAttachmentPreviewUri } from "../../services/attachmentPreviews";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { ChatSummary, MessageAttachment, MessageTextEntity, StickerPack } from "../../types";

type FormatAction = {
  label: string;
  type: MessageTextEntity["type"];
};

type RecordingProps = {
  active: boolean;
  durationLabel: string;
  onDiscard: () => void;
  onSend: () => void;
};

type PollComposerProps = {
  creating: boolean;
  multipleChoice: boolean;
  onAddOption: () => void;
  onCancel: () => void;
  onChangeOption: (index: number, value: string) => void;
  onChangeQuestion: (value: string) => void;
  onCreate: () => void;
  onRemoveOption: (index: number) => void;
  onToggleMultipleChoice: () => void;
  options: string[];
  question: string;
  visible: boolean;
};

type LocationComposerProps = {
  address: string;
  liveEnabled: boolean;
  livePeriodMinutes: string;
  latitude: string;
  longitude: string;
  onCancel: () => void;
  onChangeAddress: (value: string) => void;
  onChangeLivePeriodMinutes: (value: string) => void;
  onChangeLatitude: (value: string) => void;
  onChangeLongitude: (value: string) => void;
  onChangeTitle: (value: string) => void;
  onToggleLiveMode: () => void;
  onUseCurrentLocation: () => void;
  resolvingDeviceLocation: boolean;
  title: string;
  visible: boolean;
};

type ContactComposerProps = {
  firstName: string;
  lastName: string;
  onCancel: () => void;
  onChangeFirstName: (value: string) => void;
  onChangeLastName: (value: string) => void;
  onChangePhoneNumber: (value: string) => void;
  onChangeUserId: (value: string) => void;
  phoneNumber: string;
  userId: string;
  visible: boolean;
};

type StickerPickerProps = {
  loading: boolean;
  onSendSticker: (stickerId: string) => void;
  packs: StickerPack[];
  visible: boolean;
};

type GifPickerProps = {
  formatFileSize: (value: number) => string;
  loading: boolean;
  onClose: () => void;
  onInsert: (attachment: MessageAttachment) => void;
  onUpload: () => void;
  recentGifs: MessageAttachment[];
  visible: boolean;
};

type ComposerProps = {
  activeStructuredMessageType: "CONTACT_CARD" | "LOCATION" | "LIVE_LOCATION" | null;
  canFormatSelection: boolean;
  canPost: boolean;
  chatType: ChatSummary["chatType"];
  draft: string;
  editingMessageId: string | null;
  formatActions: FormatAction[];
  hasComposerContent: boolean;
  status: {
    description: string;
    title: string;
    tone: "brand" | "danger" | "info" | "success" | "warning";
  } | null;
  onCapturePhoto: () => void;
  onCaptureVideo: () => void;
  onCaptureVideoNote: () => void;
  onChangeDraft: (value: string) => void;
  onComposerSelectionChange: (
    event: NativeSyntheticEvent<TextInputSelectionChangeEventData>
  ) => void;
  onPickAttachments: () => void;
  onPickAudioFiles: () => void;
  onPickPhotos: () => void;
  onPickVideos: () => void;
  onScheduleMessage: () => void;
  onSend: () => void;
  onSendWhenOnline: () => void;
  onStartVoiceRecording: () => void;
  onToggleContactComposer: () => void;
  onToggleFormatting: (type: MessageTextEntity["type"]) => void;
  onToggleGifPicker: () => void;
  onToggleLocationComposer: () => void;
  onTogglePollComposer: () => void;
  onToggleSendSilently: () => void;
  onToggleStickerPicker: () => void;
  pendingAttachmentsCount: number;
  placeholder: string;
  recordingVoice: boolean;
  scheduling: boolean;
  sendSilently: boolean;
  sending: boolean;
  showContactComposer: boolean;
  showPollComposer: boolean;
  showLocationComposer: boolean;
  uploadingAttachments: boolean;
  isFormattingActive: (type: MessageTextEntity["type"]) => boolean;
};

type ChatComposerSurfaceProps = {
  composer: ComposerProps;
  contactComposer: ContactComposerProps;
  gifPicker: GifPickerProps;
  locationComposer: LocationComposerProps;
  pollComposer: PollComposerProps;
  recording: RecordingProps;
  stickerPicker: StickerPickerProps;
};

function renderGifPreviewLabel(
  attachment: MessageAttachment,
  formatFileSize: (value: number) => string
) {
  return formatFileSize(attachment.fileSizeBytes);
}

export function ChatComposerSurface({
  composer,
  contactComposer,
  gifPicker,
  locationComposer,
  pollComposer,
  recording,
  stickerPicker
}: ChatComposerSurfaceProps) {
  return (
    <>
      {recording.active ? (
        <AppPanel style={styles.infoPanel} title="Recording voice message" titleStyle={styles.infoTitle} tone="info">
          <Text style={styles.infoText}>{recording.durationLabel}</Text>
          <View style={styles.rowWrap}>
            <AppButton onPress={recording.onSend} size="sm" variant="secondary">
              Send voice
            </AppButton>
            <AppButton onPress={recording.onDiscard} size="sm" variant="danger">
              Discard
            </AppButton>
          </View>
        </AppPanel>
      ) : null}

      {pollComposer.visible ? (
        <AppPanel style={styles.warningPanel} title="New poll" titleStyle={styles.warningTitle} tone="warning">
          <AppTextField
            onChangeText={pollComposer.onChangeQuestion}
            placeholder="Question"
            style={styles.field}
            value={pollComposer.question}
          />
          {pollComposer.options.map((option, index) => (
            <View key={`poll-option-${index}`} style={styles.optionEditorRow}>
              <AppTextField
                onChangeText={(value) => pollComposer.onChangeOption(index, value)}
                placeholder={`Option ${index + 1}`}
                style={[styles.field, styles.optionEditorInput]}
                value={option}
              />
              {pollComposer.options.length > 2 ? (
                <AppButton onPress={() => pollComposer.onRemoveOption(index)} size="sm" variant="danger">
                  Remove
                </AppButton>
              ) : null}
            </View>
          ))}
          <View style={styles.rowWrap}>
            <AppButton onPress={pollComposer.onAddOption} size="sm" variant="secondary">
              Add option
            </AppButton>
            <AppButton onPress={pollComposer.onToggleMultipleChoice} size="sm" variant="secondary">
              {pollComposer.multipleChoice ? "Multiple choice" : "Single choice"}
            </AppButton>
            <AppButton onPress={pollComposer.onCancel} size="sm" variant="secondary">
              Cancel
            </AppButton>
            <AppButton
              disabled={pollComposer.creating || !pollComposer.question.trim()}
              onPress={pollComposer.onCreate}
              size="sm"
              variant="primary"
            >
              {pollComposer.creating ? "..." : "Create poll"}
            </AppButton>
          </View>
        </AppPanel>
      ) : null}

      {locationComposer.visible ? (
        <AppPanel
          style={styles.warningPanel}
          title={locationComposer.liveEnabled ? "Share live location" : "Share location"}
          titleStyle={styles.warningTitle}
          tone="warning"
        >
          <AppTextField
            keyboardType="decimal-pad"
            onChangeText={locationComposer.onChangeLatitude}
            placeholder="Latitude"
            style={styles.field}
            value={locationComposer.latitude}
          />
          <AppTextField
            keyboardType="decimal-pad"
            onChangeText={locationComposer.onChangeLongitude}
            placeholder="Longitude"
            style={styles.field}
            value={locationComposer.longitude}
          />
          <AppTextField
            onChangeText={locationComposer.onChangeTitle}
            placeholder="Label (optional)"
            style={styles.field}
            value={locationComposer.title}
          />
          <AppTextField
            onChangeText={locationComposer.onChangeAddress}
            placeholder="Address (optional)"
            style={styles.field}
            value={locationComposer.address}
          />
          {locationComposer.liveEnabled ? (
            <AppTextField
              keyboardType="number-pad"
              onChangeText={locationComposer.onChangeLivePeriodMinutes}
              placeholder="Live period in minutes"
              style={styles.field}
              value={locationComposer.livePeriodMinutes}
            />
          ) : null}
          <Text style={styles.warningBody}>Optional note goes in the main composer field below.</Text>
          <View style={styles.rowWrap}>
            <AppButton
              onPress={locationComposer.onUseCurrentLocation}
              size="sm"
              variant="secondary"
            >
              {locationComposer.resolvingDeviceLocation ? "Locating..." : "Use current location"}
            </AppButton>
            <AppButton onPress={locationComposer.onToggleLiveMode} size="sm" variant="secondary">
              {locationComposer.liveEnabled ? "Use static location" : "Use live location"}
            </AppButton>
            <AppButton onPress={locationComposer.onCancel} size="sm" variant="secondary">
              Cancel
            </AppButton>
          </View>
        </AppPanel>
      ) : null}

      {contactComposer.visible ? (
        <AppPanel
          style={styles.warningPanel}
          title="Share contact"
          titleStyle={styles.warningTitle}
          tone="warning"
        >
          <AppTextField
            onChangeText={contactComposer.onChangeFirstName}
            placeholder="First name"
            style={styles.field}
            value={contactComposer.firstName}
          />
          <AppTextField
            onChangeText={contactComposer.onChangeLastName}
            placeholder="Last name"
            style={styles.field}
            value={contactComposer.lastName}
          />
          <AppTextField
            keyboardType="phone-pad"
            onChangeText={contactComposer.onChangePhoneNumber}
            placeholder="Phone number"
            style={styles.field}
            value={contactComposer.phoneNumber}
          />
          <AppTextField
            onChangeText={contactComposer.onChangeUserId}
            placeholder="Linked user id (optional)"
            style={styles.field}
            value={contactComposer.userId}
          />
          <Text style={styles.warningBody}>Optional note goes in the main composer field below.</Text>
          <View style={styles.rowWrap}>
            <AppButton onPress={contactComposer.onCancel} size="sm" variant="secondary">
              Cancel
            </AppButton>
          </View>
        </AppPanel>
      ) : null}

      {stickerPicker.visible ? (
        <AppPanel
          style={styles.warningPanel}
          title="Sticker packs"
          titleStyle={styles.warningTitle}
          tone="warning"
        >
          {stickerPicker.loading ? (
            <Text style={styles.warningBody}>Loading stickers...</Text>
          ) : (
            <View style={styles.stickerPackList}>
              {stickerPicker.packs.map((pack) => (
                <View key={pack.packId} style={styles.stickerPackSection}>
                  <Text style={styles.stickerPackTitle}>{pack.title}</Text>
                  <View style={styles.stickerPickerGrid}>
                    {pack.stickers.map((sticker) => (
                      <View key={sticker.stickerId} style={styles.stickerPickerWrapper}>
                        <AppButton
                          onPress={() => stickerPicker.onSendSticker(sticker.stickerId)}
                          size="sm"
                          style={[
                            styles.stickerPickerCard,
                            {
                              backgroundColor: sticker.backgroundFrom,
                              borderColor: sticker.backgroundTo
                            }
                          ]}
                          variant="secondary"
                        >
                          {sticker.emoji}
                        </AppButton>
                        <Text style={[styles.stickerPickerLabel, { color: sticker.textColor }]}>
                          {sticker.label}
                        </Text>
                      </View>
                    ))}
                  </View>
                </View>
              ))}
            </View>
          )}
        </AppPanel>
      ) : null}

      {gifPicker.visible ? (
        <AppPanel
          style={styles.warningPanel}
          title="Recent GIFs"
          titleStyle={styles.warningTitle}
          tone="warning"
        >
          <Text style={styles.warningBody}>
            Pick a recent GIF from your account history or upload a new one from this device.
          </Text>
          <View style={styles.rowWrap}>
            <AppButton onPress={gifPicker.onUpload} size="sm" variant="secondary">
              Upload GIF
            </AppButton>
            <AppButton onPress={gifPicker.onClose} size="sm" variant="secondary">
              Close
            </AppButton>
          </View>
          {gifPicker.loading ? (
            <Text style={styles.warningBody}>Loading recent GIFs...</Text>
          ) : gifPicker.recentGifs.length === 0 ? (
            <Text style={styles.warningBody}>No recent GIFs yet.</Text>
          ) : (
            <View style={styles.gifPickerGrid}>
              {gifPicker.recentGifs.map((attachment) => (
                <View key={attachment.attachmentId} style={styles.gifPickerCard}>
                  {(() => {
                    const previewUri = resolveAttachmentPreviewUri(attachment);

                    return (
                      <>
                        <AppButton
                          onPress={() => gifPicker.onInsert(attachment)}
                          style={styles.gifButton}
                          variant="secondary"
                        >
                          {previewUri ? (
                            <Image source={{ uri: previewUri }} style={styles.gifPickerImage} />
                          ) : (
                            <View style={styles.gifPickerFallback}>
                              <Text style={styles.gifPickerFallbackText}>GIF</Text>
                            </View>
                          )}
                        </AppButton>
                        <Text style={styles.gifPickerMeta}>
                          {renderGifPreviewLabel(attachment, gifPicker.formatFileSize)}
                        </Text>
                      </>
                    );
                  })()}
                </View>
              ))}
            </View>
          )}
        </AppPanel>
      ) : null}

      {composer.status ? (
        <AppPanel
          description={composer.status.description}
          descriptionStyle={styles.statusDescription}
          style={styles.statusPanel}
          title={composer.status.title}
          titleStyle={styles.statusTitle}
          tone={composer.status.tone}
        />
      ) : null}

      <View style={styles.composerSection}>
        <View style={styles.formatBar}>
          {composer.formatActions.map((action) => (
            <AppButton
              key={action.type}
              disabled={!composer.canPost || composer.recordingVoice || !composer.canFormatSelection}
              onPress={() => composer.onToggleFormatting(action.type)}
              size="sm"
              variant={composer.isFormattingActive(action.type) ? "primary" : "secondary"}
            >
              {action.label}
            </AppButton>
          ))}
          <AppButton
            disabled={!composer.canPost || composer.recordingVoice || !!composer.editingMessageId || composer.showPollComposer}
            onPress={composer.onToggleSendSilently}
            size="sm"
            variant={composer.sendSilently ? "primary" : "secondary"}
          >
            Silent
          </AppButton>
        </View>

        <View style={styles.composer}>
          <AppButton
            disabled={!composer.canPost || composer.uploadingAttachments || !!composer.editingMessageId || composer.showPollComposer || composer.recordingVoice || !!composer.activeStructuredMessageType}
            onPress={composer.onPickAttachments}
            size="sm"
            variant="secondary"
          >
            {composer.uploadingAttachments ? "..." : "File"}
          </AppButton>
          <AppButton
            disabled={!composer.canPost || composer.uploadingAttachments || !!composer.editingMessageId || composer.showPollComposer || composer.recordingVoice || !!composer.activeStructuredMessageType}
            onPress={composer.onCapturePhoto}
            size="sm"
            variant="secondary"
          >
            Photo
          </AppButton>
          <AppButton
            disabled={!composer.canPost || composer.uploadingAttachments || !!composer.editingMessageId || composer.showPollComposer || composer.recordingVoice || !!composer.activeStructuredMessageType}
            onPress={composer.onCaptureVideo}
            size="sm"
            variant="secondary"
          >
            Camera
          </AppButton>
          <AppButton
            disabled={!composer.canPost || composer.uploadingAttachments || !!composer.editingMessageId || composer.showPollComposer || composer.recordingVoice || !!composer.activeStructuredMessageType}
            onPress={composer.onCaptureVideoNote}
            size="sm"
            variant="secondary"
          >
            Video note
          </AppButton>
          <AppButton
            disabled={!composer.canPost || composer.uploadingAttachments || !!composer.editingMessageId || composer.showPollComposer || composer.recordingVoice || !!composer.activeStructuredMessageType}
            onPress={composer.onPickPhotos}
            size="sm"
            variant="secondary"
          >
            Gallery
          </AppButton>
          <AppButton
            disabled={!composer.canPost || composer.uploadingAttachments || !!composer.editingMessageId || composer.showPollComposer || composer.recordingVoice || !!composer.activeStructuredMessageType}
            onPress={composer.onPickVideos}
            size="sm"
            variant="secondary"
          >
            Video
          </AppButton>
          <AppButton
            disabled={!composer.canPost || composer.uploadingAttachments || !!composer.editingMessageId || composer.showPollComposer || composer.recordingVoice || !!composer.activeStructuredMessageType}
            onPress={composer.onPickAudioFiles}
            size="sm"
            variant="secondary"
          >
            Audio
          </AppButton>
          <AppButton
            disabled={!composer.canPost || composer.uploadingAttachments || !!composer.editingMessageId || composer.showPollComposer || composer.recordingVoice || !!composer.activeStructuredMessageType}
            onPress={composer.onToggleGifPicker}
            size="sm"
            variant="secondary"
          >
            GIFs
          </AppButton>
          <AppButton
            disabled={!composer.canPost || !!composer.editingMessageId || composer.pendingAttachmentsCount > 0 || composer.recordingVoice || composer.showPollComposer || composer.showContactComposer}
            onPress={composer.onToggleLocationComposer}
            size="sm"
            variant="secondary"
          >
            Location
          </AppButton>
          <AppButton
            disabled={!composer.canPost || !!composer.editingMessageId || composer.pendingAttachmentsCount > 0 || composer.recordingVoice || composer.showPollComposer || composer.showLocationComposer}
            onPress={composer.onToggleContactComposer}
            size="sm"
            variant="secondary"
          >
            Contact
          </AppButton>
          <AppButton
            disabled={!composer.canPost || !!composer.editingMessageId || !!composer.activeStructuredMessageType}
            onPress={composer.onTogglePollComposer}
            size="sm"
            variant="secondary"
          >
            Poll
          </AppButton>
          <AppButton
            disabled={!composer.canPost || !!composer.editingMessageId || composer.uploadingAttachments || composer.recordingVoice || composer.pendingAttachmentsCount > 0 || !!composer.activeStructuredMessageType}
            onPress={composer.onToggleStickerPicker}
            size="sm"
            variant="secondary"
          >
            Sticker
          </AppButton>
          <AppButton
            disabled={!composer.canPost || !!composer.editingMessageId || composer.uploadingAttachments || composer.showPollComposer || !!composer.activeStructuredMessageType}
            onPress={composer.onStartVoiceRecording}
            size="sm"
            variant="secondary"
          >
            Mic
          </AppButton>
          <AppTextField
            editable={composer.canPost && !composer.recordingVoice}
            multiline
            onChangeText={composer.onChangeDraft}
            onSelectionChange={composer.onComposerSelectionChange}
            placeholder={composer.placeholder}
            style={[styles.field, styles.composerInput]}
            value={composer.draft}
          />
          {composer.chatType === "DIRECT" ? (
            <AppButton
              disabled={
                composer.scheduling ||
                composer.sending ||
                composer.uploadingAttachments ||
                !composer.hasComposerContent ||
                !composer.canPost ||
                composer.showPollComposer ||
                composer.recordingVoice ||
                !!composer.editingMessageId ||
                composer.activeStructuredMessageType === "LIVE_LOCATION"
              }
              onPress={composer.onSendWhenOnline}
              size="sm"
              variant="secondary"
            >
              {composer.scheduling ? "..." : "Online"}
            </AppButton>
          ) : null}
          <AppButton
            disabled={
              composer.scheduling ||
              composer.sending ||
              composer.uploadingAttachments ||
              !composer.hasComposerContent ||
              !composer.canPost ||
              composer.showPollComposer ||
              composer.recordingVoice ||
              !!composer.editingMessageId ||
              composer.activeStructuredMessageType === "LIVE_LOCATION"
            }
            onPress={composer.onScheduleMessage}
            size="sm"
            variant="secondary"
          >
            {composer.scheduling ? "..." : "10m"}
          </AppButton>
          <AppButton
            disabled={composer.sending || composer.uploadingAttachments || !composer.hasComposerContent || !composer.canPost || composer.showPollComposer || composer.recordingVoice}
            onPress={composer.onSend}
            variant="primary"
          >
            {composer.sending ? "..." : composer.editingMessageId ? "Save" : "Send"}
          </AppButton>
        </View>
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  infoPanel: {
    marginHorizontal: appSpacing.lg,
    marginBottom: appSpacing.sm
  },
  infoTitle: {
    color: "#075985",
    fontWeight: "700"
  },
  infoText: {
    color: "#0c4a6e",
    marginTop: appSpacing.xs
  },
  warningPanel: {
    marginHorizontal: appSpacing.lg,
    marginBottom: appSpacing.sm
  },
  warningTitle: {
    color: "#92400e",
    fontWeight: "700"
  },
  warningBody: {
    color: "#92400e",
    marginTop: appSpacing.xs
  },
  statusPanel: {
    marginHorizontal: appSpacing.lg,
    marginBottom: appSpacing.sm
  },
  statusTitle: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  statusDescription: {
    color: appColors.textSecondary
  },
  rowWrap: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm,
    marginTop: appSpacing.sm
  },
  field: {
    marginTop: appSpacing.sm
  },
  optionEditorRow: {
    alignItems: "center",
    flexDirection: "row",
    gap: appSpacing.sm,
    marginTop: appSpacing.sm
  },
  optionEditorInput: {
    flex: 1
  },
  stickerPackList: {
    gap: appSpacing.md,
    marginTop: appSpacing.sm
  },
  stickerPackSection: {
    gap: appSpacing.sm
  },
  stickerPackTitle: {
    color: "#92400e",
    fontWeight: "700"
  },
  stickerPickerGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  stickerPickerWrapper: {
    alignItems: "center",
    width: 88
  },
  stickerPickerCard: {
    alignItems: "center",
    borderColor: appColors.border,
    borderRadius: appRadii.lg,
    borderWidth: 2,
    minHeight: 72,
    width: "100%"
  },
  stickerPickerLabel: {
    fontSize: 11,
    fontWeight: "700",
    marginTop: 6,
    textAlign: "center"
  },
  gifPickerGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm,
    marginTop: appSpacing.sm
  },
  gifPickerCard: {
    backgroundColor: appColors.surface,
    borderRadius: appRadii.lg,
    overflow: "hidden",
    width: 104
  },
  gifButton: {
    backgroundColor: appColors.surface,
    borderRadius: 0,
    paddingHorizontal: 0,
    paddingVertical: 0
  },
  gifPickerImage: {
    backgroundColor: appColors.surfaceAccent,
    height: 104,
    width: "100%"
  },
  gifPickerFallback: {
    alignItems: "center",
    backgroundColor: appColors.surfaceAccent,
    height: 104,
    justifyContent: "center",
    width: "100%"
  },
  gifPickerFallbackText: {
    color: appColors.brandText,
    fontWeight: "700"
  },
  gifPickerMeta: {
    color: appColors.textSecondary,
    fontSize: 11,
    paddingHorizontal: appSpacing.sm + 2,
    paddingVertical: appSpacing.sm
  },
  composerSection: {
    backgroundColor: appColors.surface,
    borderTopColor: "#e2e8f0",
    borderTopWidth: 1,
    paddingTop: appSpacing.sm + 2
  },
  formatBar: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm,
    paddingBottom: appSpacing.sm + 2,
    paddingHorizontal: appSpacing.lg
  },
  composer: {
    alignItems: "flex-end",
    backgroundColor: appColors.surface,
    flexDirection: "row",
    gap: appSpacing.md,
    paddingBottom: appSpacing.lg,
    paddingHorizontal: appSpacing.lg
  },
  composerInput: {
    flex: 1,
    maxHeight: 120,
    minHeight: 48
  }
});
