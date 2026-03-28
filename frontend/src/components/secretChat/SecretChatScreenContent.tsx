import React from "react";
import { KeyboardAvoidingView, Platform, StyleSheet } from "react-native";
import { SecretChatComposerSection } from "./SecretChatComposerSection";
import { SecretChatHeader } from "./SecretChatHeader";
import { SecretChatMessagesList } from "./SecretChatMessagesList";
import { SecretChatPendingAttachmentsBar } from "./SecretChatPendingAttachmentsBar";
import { SecretChatPreviewModals } from "./SecretChatPreviewModals";
import { SecretChatSecuritySection } from "./SecretChatSecuritySection";
import {
  buildSecretChatDisabledComposerNotice
} from "./secretChatPresentation";
import type { SecretChatScreenController } from "./useSecretChatController";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import { ScreenStack } from "../ui/ScreenStack";
import { appSpacing } from "../../theme/tokens";
import type { SecretChatSummary } from "../../types";

type SecretChatScreenContentProps = {
  controller: SecretChatScreenController;
  currentUserId: string;
  onBack: () => void;
  secretChat: SecretChatSummary;
};

export function SecretChatScreenContent({
  controller,
  currentUserId,
  onBack
}: SecretChatScreenContentProps) {
  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === "ios" ? "padding" : undefined}
      keyboardVerticalOffset={Platform.OS === "ios" ? 18 : 0}
      style={styles.screen}
    >
      <SecretChatHeader
        closing={controller.closing}
        onBack={onBack}
        onCloseChat={() => void controller.handleCloseChat()}
        secretChat={controller.secretChat}
        statusText={controller.statusText}
      />

      <SecretChatSecuritySection
        fingerprintMismatch={controller.fingerprintMismatch}
        localFingerprint={controller.localFingerprint}
        onUpdateTimer={(value) => void controller.handleUpdateTimer(value)}
        secretChat={controller.secretChat}
        updatingTimer={controller.updatingTimer}
      />

      <ScreenFeedback
        error={controller.error}
        errorStyle={styles.banner}
        notice={controller.activityNotice}
        noticeStyle={styles.banner}
        noticeTone="info"
      />

      <ScreenStack flex={1}>
        <SecretChatMessagesList
          currentUserId={currentUserId}
          decryptedAttachmentUris={controller.decryptedAttachmentUris}
          messages={controller.resolvedMessages}
          onOpenAttachment={(attachment) => void controller.handleOpenAttachment(attachment)}
          onRestrictedActionNotice={controller.handleRestrictedActionNotice}
          onToggleVoicePlayback={(attachment) => void controller.handleToggleVoicePlayback(attachment)}
          openingAttachmentId={controller.openingAttachmentId}
          playingVoiceAttachmentId={controller.playingVoiceAttachmentId}
        />
      </ScreenStack>

      <SecretChatPreviewModals
        focusedImage={controller.focusedImage}
        focusedVideo={controller.focusedVideo}
        focusedVideoPlaying={controller.focusedVideoPlaying}
        onCloseFocusedImage={controller.closeFocusedImage}
        onCloseFocusedVideo={controller.closeFocusedVideo}
        onRestrictedActionNotice={controller.handleRestrictedActionNotice}
        onToggleFocusedVideoPlayback={controller.toggleFocusedVideoPlayback}
      />

      <SecretChatPendingAttachmentsBar
        onRemovePendingAttachment={(attachment) => void controller.handleRemovePendingAttachment(attachment)}
        pendingAttachments={controller.pendingAttachments}
      />

      <SecretChatComposerSection
        active={controller.active}
        blockedNotice={buildSecretChatDisabledComposerNotice(controller.secretChat)}
        draft={controller.draft}
        onDraftChange={controller.setDraft}
        onPickFile={() => void controller.handlePickAttachments("FILE", "*/*")}
        onPickImage={() => void controller.handlePickAttachments("IMAGE", "image/*")}
        onSend={() => void controller.handleSend()}
        onStartVoiceRecording={() => void controller.handleStartVoiceRecording()}
        onStopVoiceRecording={(cancel) => void controller.handleStopVoiceRecording(cancel)}
        recordingDurationMs={controller.recordingDurationMs}
        recordingVoice={controller.recordingVoice}
        sendDisabled={controller.sendDisabled}
        sending={controller.sending}
        uploadingAttachments={controller.uploadingAttachments}
      />
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1
  },
  banner: {
    marginHorizontal: appSpacing.xl,
    marginTop: appSpacing.md
  }
});
