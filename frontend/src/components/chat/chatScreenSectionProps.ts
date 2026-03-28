import type { ComponentProps, Dispatch, SetStateAction } from "react";
import { ChatComposerSection } from "./ChatComposerSection";
import { ChatTimelineList } from "./ChatTimelineList";
import type { PendingAttachmentBarSummary } from "./PendingAttachmentBar";
import type {
  ChatMessage,
  MessageSelectionState,
  ChatSummary,
  MessageAttachment,
  MessageTextEntity,
  StickerPack
} from "../../types";

type ChatTimelineListProps = ComponentProps<typeof ChatTimelineList>;
type ChatComposerSectionProps = ComponentProps<typeof ChatComposerSection>;

type BuildChatTimelineListPropsParams = {
  activeThreadRootMessageId: string | null;
  allChatMessages: ChatMessage[];
  attachmentTitle: ChatTimelineListProps["bubbleProps"]["attachmentTitle"];
  canClosePoll: ChatTimelineListProps["bubbleProps"]["canClosePoll"];
  closingPollMessageId: string | null;
  contentContainerStyle: ChatTimelineListProps["contentContainerStyle"];
  currentUserId: string;
  describeMessage: ChatTimelineListProps["bubbleProps"]["describeMessage"];
  displayedMessages: ChatMessage[];
  firstUnreadMessageId: string | null;
  formatContactName: ChatTimelineListProps["bubbleProps"]["formatContactName"];
  formatDuration: ChatTimelineListProps["bubbleProps"]["formatDuration"];
  formatFileSize: ChatTimelineListProps["bubbleProps"]["formatFileSize"];
  getAttachmentTransferMeta: ChatTimelineListProps["bubbleProps"]["getAttachmentTransferMeta"];
  getImagePreviewHeight: ChatTimelineListProps["bubbleProps"]["getImagePreviewHeight"];
  highlightedMessageId: string | null;
  isAudioAttachment: ChatTimelineListProps["bubbleProps"]["isAudioAttachment"];
  isImageAttachment: ChatTimelineListProps["bubbleProps"]["isImageAttachment"];
  isQueuedUploadAttachment: ChatTimelineListProps["bubbleProps"]["isQueuedUploadAttachment"];
  isVideoAttachment: ChatTimelineListProps["bubbleProps"]["isVideoAttachment"];
  listRef: ChatTimelineListProps["listRef"];
  onClosePoll: ChatTimelineListProps["bubbleProps"]["onClosePoll"];
  onEnsureMessageVisible: ChatTimelineListProps["bubbleProps"]["onEnsureMessageVisible"];
  onMessageLongPress: ChatTimelineListProps["onMessageLongPress"];
  onMessagePress: ChatTimelineListProps["onMessagePress"];
  onOpenLink: ChatTimelineListProps["bubbleProps"]["onOpenLink"];
  onOpenAttachment: ChatTimelineListProps["bubbleProps"]["onOpenAttachment"];
  onOpenDiscussionThread: ChatTimelineListProps["bubbleProps"]["onOpenDiscussionThread"];
  onToggleReaction: ChatTimelineListProps["bubbleProps"]["onToggleReaction"];
  onToggleVoicePlayback: ChatTimelineListProps["bubbleProps"]["onToggleVoicePlayback"];
  onVotePoll: ChatTimelineListProps["bubbleProps"]["onVotePoll"];
  openingAttachmentId: string | null;
  pinnedMessageId: string | null;
  playingVoiceAttachmentId: string | null;
  reactionsEnabled: boolean;
  renderMessageMeta: ChatTimelineListProps["bubbleProps"]["renderMessageMeta"];
  renderWaveform: ChatTimelineListProps["bubbleProps"]["renderWaveform"];
  resolveDisplaySenderName: ChatTimelineListProps["resolveDisplaySenderName"];
  searchQuery: string;
  selectionState: MessageSelectionState;
  showOpenDiscussionThread: boolean;
  summaryChatType: ChatSummary["chatType"];
  votingMessageId: string | null;
};

type BuildChatComposerSectionPropsParams = {
  activeStructuredMessageType: "CONTACT_CARD" | "LOCATION" | "LIVE_LOCATION" | null;
  addPollOption: () => void;
  canFormatSelection: boolean;
  canPost: boolean;
  chatType: ChatSummary["chatType"];
  composerSelectionChange: ChatComposerSectionProps["composerSurface"]["composer"]["onComposerSelectionChange"];
  contactFirstName: string;
  contactLastName: string;
  contactPhoneNumber: string;
  contactUserId: string;
  creatingPoll: boolean;
  draft: string;
  editingMessageId: string | null;
  formatActions: Array<{ label: string; type: MessageTextEntity["type"] }>;
  formatFileSize: (value: number) => string;
  handleCapturePhoto: () => void;
  handleCaptureVideo: () => void;
  handleCaptureVideoNote: () => void;
  handleChangeDraft: (value: string) => void;
  handleCreatePoll: () => void | Promise<void>;
  handleInsertRecentGif: (attachment: MessageAttachment) => void;
  handlePickAttachments: () => void;
  handlePickAudioFiles: () => void;
  handlePickPhotos: () => void;
  handlePickVideos: () => void;
  handleScheduleMessage: () => void | Promise<void>;
  handleSend: () => void | Promise<void>;
  handleSendSticker: (stickerId: string) => void | Promise<void>;
  handleSendWhenOnline: () => void | Promise<void>;
  handleStartVoiceRecording: () => void;
  handleStopVoiceRecording: (discard?: boolean) => void | Promise<void>;
  handleToggleContactComposer: () => void;
  handleToggleFormatting: (type: MessageTextEntity["type"]) => void;
  handleToggleGifPicker: () => void | Promise<void>;
  handleToggleLocationComposer: () => void;
  handleTogglePollComposer: () => void;
  handleToggleStickerPicker: () => void | Promise<void>;
  handleUseCurrentLocation: () => void | Promise<void>;
  handleUploadGifFromDevice: () => void | Promise<void>;
  hasComposerContent: boolean;
  isFormattingActive: (type: MessageTextEntity["type"]) => boolean;
  items: ChatComposerSectionProps["pendingAttachmentBar"]["items"];
  pendingAttachmentSummary: ChatComposerSectionProps["pendingAttachmentBar"]["summary"];
  locationAddress: string;
  liveLocationEnabled: boolean;
  liveLocationPeriodMinutes: string;
  locationLatitude: string;
  locationLongitude: string;
  locationTitle: string;
  loadingRecentGifs: boolean;
  loadingStickerPacks: boolean;
  movePendingAttachment: ChatComposerSectionProps["pendingAttachmentBar"]["onMoveAttachment"];
  onRemoveAttachment: ChatComposerSectionProps["pendingAttachmentBar"]["onRemoveAttachment"];
  onRetryAttachment: ChatComposerSectionProps["pendingAttachmentBar"]["onRetryAttachment"];
  onTrimAttachment: ChatComposerSectionProps["pendingAttachmentBar"]["onTrimAttachment"];
  pendingAttachmentsCount: number;
  pollMultipleChoice: boolean;
  pollOptions: string[];
  pollQuestion: string;
  recentGifs: MessageAttachment[];
  recordingDurationLabel: string;
  recordingVoice: boolean;
  resolvingDeviceLocation: boolean;
  resetContactComposer: () => void;
  resetLocationComposer: () => void;
  resetPollComposer: () => void;
  scheduling: boolean;
  sendSilently: boolean;
  sending: boolean;
  setContactFirstName: (value: string) => void;
  setContactLastName: (value: string) => void;
  setContactPhoneNumber: (value: string) => void;
  setContactUserId: (value: string) => void;
  setLiveLocationEnabled: Dispatch<SetStateAction<boolean>>;
  setLiveLocationPeriodMinutes: (value: string) => void;
  setLocationAddress: (value: string) => void;
  setLocationLatitude: (value: string) => void;
  setLocationLongitude: (value: string) => void;
  setLocationTitle: (value: string) => void;
  setPollMultipleChoice: Dispatch<SetStateAction<boolean>>;
  setPollQuestion: (value: string) => void;
  setSendSilently: Dispatch<SetStateAction<boolean>>;
  setShowGifPicker: Dispatch<SetStateAction<boolean>>;
  showContactComposer: boolean;
  showGifPicker: boolean;
  showLocationComposer: boolean;
  showPollComposer: boolean;
  showStickerPicker: boolean;
  stickerPacks: StickerPack[];
  structuredMessageType: "CONTACT_CARD" | "LOCATION" | "LIVE_LOCATION" | null;
  trimmingAttachmentId: string | null;
  uploadingAttachments: boolean;
  updatePollOption: (index: number, value: string) => void;
  removePollOption: (index: number) => void;
  canSendBlockedByRestriction: boolean;
  canSendBlockedBySlowMode: boolean;
};

export function buildComposerPlaceholder({
  canPost,
  canSendBlockedByRestriction,
  canSendBlockedBySlowMode,
  editingMessageId,
  structuredMessageType
}: Pick<
  BuildChatComposerSectionPropsParams,
  | "canPost"
  | "canSendBlockedByRestriction"
  | "canSendBlockedBySlowMode"
  | "editingMessageId"
  | "structuredMessageType"
>) {
  if (canPost) {
    if (editingMessageId) {
      return "Edit text";
    }
    if (structuredMessageType) {
      return "Optional note";
    }
    return "Type a message";
  }
  if (canSendBlockedByRestriction) {
    return "Posting restricted";
  }
  if (canSendBlockedBySlowMode) {
    return "Slow mode active";
  }
  return "Posting disabled";
}

export function buildComposerStatus({
  activeStructuredMessageType,
  canPost,
  canSendBlockedByRestriction,
  canSendBlockedBySlowMode,
  editingMessageId,
  liveLocationEnabled,
  pendingAttachmentSummary,
  sendSilently,
  showContactComposer,
  showLocationComposer,
  showPollComposer,
  uploadingAttachments
}: Pick<
  BuildChatComposerSectionPropsParams,
  | "activeStructuredMessageType"
  | "canPost"
  | "canSendBlockedByRestriction"
  | "canSendBlockedBySlowMode"
  | "editingMessageId"
  | "liveLocationEnabled"
  | "pendingAttachmentSummary"
  | "sendSilently"
  | "showContactComposer"
  | "showLocationComposer"
  | "showPollComposer"
  | "uploadingAttachments"
>): PendingAttachmentBarSummary {
  if (!canPost) {
    return {
      description: canSendBlockedByRestriction
        ? "Posting is restricted for this account in the current conversation."
        : canSendBlockedBySlowMode
          ? "Slow mode is active. Wait for the cooldown before sending again."
          : "Sending is unavailable in this conversation right now.",
      title: canSendBlockedByRestriction
        ? "Posting restricted"
        : canSendBlockedBySlowMode
          ? "Slow mode active"
          : "Posting disabled",
      tone: "warning"
    };
  }

  if (editingMessageId) {
    return {
      description: "Saving will update the selected message instead of sending a new one.",
      title: "Editing message",
      tone: "info"
    };
  }

  if (showPollComposer) {
    return {
      description: "Finish the poll fields above before sending a regular message from this composer.",
      title: "Poll mode active",
      tone: "warning"
    };
  }

  if (activeStructuredMessageType === "LIVE_LOCATION" || (showLocationComposer && liveLocationEnabled)) {
    return {
      description: "Live location sends with the period configured above. The main composer text stays optional.",
      title: "Live location message",
      tone: "brand"
    };
  }

  if (activeStructuredMessageType === "LOCATION" || showLocationComposer) {
    return {
      description: "The main composer text will be sent as an optional note with this location.",
      title: "Location message",
      tone: "info"
    };
  }

  if (showContactComposer || activeStructuredMessageType === "CONTACT_CARD") {
    return {
      description: "The contact card will include any optional note currently typed in the composer.",
      title: "Contact card ready",
      tone: "info"
    };
  }

  if (pendingAttachmentSummary) {
    return uploadingAttachments
      ? {
          description: `${pendingAttachmentSummary.description} You can keep typing while uploads finish.`,
          title: "Preparing attachments",
          tone: "brand"
        }
      : pendingAttachmentSummary;
  }

  if (sendSilently) {
    return {
      description: "The next message will be delivered without a notification sound when supported by the server.",
      title: "Silent send enabled",
      tone: "brand"
    };
  }

  return null;
}

export function buildChatTimelineListProps({
  activeThreadRootMessageId,
  allChatMessages,
  attachmentTitle,
  canClosePoll,
  closingPollMessageId,
  contentContainerStyle,
  currentUserId,
  describeMessage,
  displayedMessages,
  firstUnreadMessageId,
  formatContactName,
  formatDuration,
  formatFileSize,
  getAttachmentTransferMeta,
  getImagePreviewHeight,
  highlightedMessageId,
  isAudioAttachment,
  isImageAttachment,
  isQueuedUploadAttachment,
  isVideoAttachment,
  listRef,
  onClosePoll,
  onEnsureMessageVisible,
  onMessageLongPress,
  onMessagePress,
  onOpenLink,
  onOpenAttachment,
  onOpenDiscussionThread,
  onToggleReaction,
  onToggleVoicePlayback,
  onVotePoll,
  openingAttachmentId,
  pinnedMessageId,
  playingVoiceAttachmentId,
  reactionsEnabled,
  renderMessageMeta,
  renderWaveform,
  resolveDisplaySenderName,
  searchQuery,
  selectionState,
  showOpenDiscussionThread,
  summaryChatType,
  votingMessageId
}: BuildChatTimelineListPropsParams): ChatTimelineListProps {
  return {
    activeThreadRootMessageId,
    allChatMessages,
    bubbleProps: {
      attachmentTitle,
      canClosePoll,
      canOpenDiscussionThread: showOpenDiscussionThread,
      closingPollMessageId,
      currentUserId,
      describeMessage,
      formatContactName,
      formatDuration,
      formatFileSize,
      getAttachmentTransferMeta,
      getImagePreviewHeight,
      isAudioAttachment,
      isImageAttachment,
      isQueuedUploadAttachment,
      isVideoAttachment,
      onClosePoll,
      onEnsureMessageVisible,
      onOpenLink,
      onOpenAttachment,
      onOpenDiscussionThread,
      onToggleReaction,
      onToggleVoicePlayback,
      onVotePoll,
      openingAttachmentId,
      playingVoiceAttachmentId,
      reactionsEnabled,
      renderMessageMeta,
      renderWaveform,
      votingMessageId
    },
    chatType: summaryChatType,
    contentContainerStyle,
    currentUserId,
    firstUnreadMessageId,
    highlightedMessageId,
    listRef,
    messages: displayedMessages,
    onMessageLongPress,
    onMessagePress,
    pinnedMessageId,
    resolveDisplaySenderName,
    searchQuery,
    selectionState
  };
}

export function buildChatComposerSectionProps({
  activeStructuredMessageType,
  addPollOption,
  canFormatSelection,
  canPost,
  canSendBlockedByRestriction,
  canSendBlockedBySlowMode,
  chatType,
  composerSelectionChange,
  contactFirstName,
  contactLastName,
  contactPhoneNumber,
  contactUserId,
  creatingPoll,
  draft,
  editingMessageId,
  formatActions,
  formatFileSize,
  handleCapturePhoto,
  handleCaptureVideo,
  handleCaptureVideoNote,
  handleChangeDraft,
  handleCreatePoll,
  handleInsertRecentGif,
  handlePickAttachments,
  handlePickAudioFiles,
  handlePickPhotos,
  handlePickVideos,
  handleScheduleMessage,
  handleSend,
  handleSendSticker,
  handleSendWhenOnline,
  handleStartVoiceRecording,
  handleStopVoiceRecording,
  handleToggleContactComposer,
  handleToggleFormatting,
  handleToggleGifPicker,
  handleToggleLocationComposer,
  handleTogglePollComposer,
  handleToggleStickerPicker,
  handleUseCurrentLocation,
  handleUploadGifFromDevice,
  hasComposerContent,
  isFormattingActive,
  items,
  pendingAttachmentSummary,
  loadingRecentGifs,
  loadingStickerPacks,
  locationAddress,
  liveLocationEnabled,
  liveLocationPeriodMinutes,
  locationLatitude,
  locationLongitude,
  locationTitle,
  movePendingAttachment,
  onRemoveAttachment,
  onRetryAttachment,
  onTrimAttachment,
  pendingAttachmentsCount,
  pollMultipleChoice,
  pollOptions,
  pollQuestion,
  recentGifs,
  recordingDurationLabel,
  recordingVoice,
  resolvingDeviceLocation,
  removePollOption,
  resetContactComposer,
  resetLocationComposer,
  resetPollComposer,
  scheduling,
  sendSilently,
  sending,
  setContactFirstName,
  setContactLastName,
  setContactPhoneNumber,
  setContactUserId,
  setLiveLocationEnabled,
  setLiveLocationPeriodMinutes,
  setLocationAddress,
  setLocationLatitude,
  setLocationLongitude,
  setLocationTitle,
  setPollMultipleChoice,
  setPollQuestion,
  setSendSilently,
  setShowGifPicker,
  showContactComposer,
  showGifPicker,
  showLocationComposer,
  showPollComposer,
  showStickerPicker,
  stickerPacks,
  structuredMessageType,
  trimmingAttachmentId,
  updatePollOption,
  uploadingAttachments
}: BuildChatComposerSectionPropsParams): ChatComposerSectionProps {
  return {
    pendingAttachmentBar: {
      items,
      onMoveAttachment: movePendingAttachment,
      onRemoveAttachment,
      onRetryAttachment,
      summary: pendingAttachmentSummary,
      onTrimAttachment,
      trimmingAttachmentId,
      uploadingAttachments
    },
    composerSurface: {
      composer: {
        activeStructuredMessageType,
        canFormatSelection,
        canPost,
        chatType,
        draft,
        editingMessageId,
        formatActions,
        hasComposerContent,
        onCapturePhoto: handleCapturePhoto,
        onCaptureVideo: handleCaptureVideo,
        onCaptureVideoNote: handleCaptureVideoNote,
        onChangeDraft: handleChangeDraft,
        onComposerSelectionChange: composerSelectionChange,
        onPickAttachments: handlePickAttachments,
        onPickAudioFiles: handlePickAudioFiles,
        onPickPhotos: handlePickPhotos,
        onPickVideos: handlePickVideos,
        onScheduleMessage: handleScheduleMessage,
        onSend: handleSend,
        onSendWhenOnline: handleSendWhenOnline,
        onStartVoiceRecording: handleStartVoiceRecording,
        onToggleContactComposer: handleToggleContactComposer,
        onToggleFormatting: handleToggleFormatting,
        onToggleGifPicker: handleToggleGifPicker,
        onToggleLocationComposer: handleToggleLocationComposer,
        onTogglePollComposer: handleTogglePollComposer,
        onToggleSendSilently: () => setSendSilently((current) => !current),
        onToggleStickerPicker: handleToggleStickerPicker,
        pendingAttachmentsCount,
        placeholder: buildComposerPlaceholder({
          canPost,
          canSendBlockedByRestriction,
          canSendBlockedBySlowMode,
          editingMessageId,
          structuredMessageType
        }),
        status: buildComposerStatus({
          activeStructuredMessageType,
          canPost,
          canSendBlockedByRestriction,
          canSendBlockedBySlowMode,
          editingMessageId,
          liveLocationEnabled,
          pendingAttachmentSummary,
          sendSilently,
          showContactComposer,
          showLocationComposer,
          showPollComposer,
          uploadingAttachments
        }),
        recordingVoice,
        scheduling,
        sendSilently,
        sending,
        showContactComposer,
        showLocationComposer,
        showPollComposer,
        uploadingAttachments,
        isFormattingActive
      },
      contactComposer: {
        firstName: contactFirstName,
        lastName: contactLastName,
        onCancel: resetContactComposer,
        onChangeFirstName: setContactFirstName,
        onChangeLastName: setContactLastName,
        onChangePhoneNumber: setContactPhoneNumber,
        onChangeUserId: setContactUserId,
        phoneNumber: contactPhoneNumber,
        userId: contactUserId,
        visible: showContactComposer
      },
      gifPicker: {
        formatFileSize,
        loading: loadingRecentGifs,
        onClose: () => setShowGifPicker(false),
        onInsert: handleInsertRecentGif,
        onUpload: handleUploadGifFromDevice,
        recentGifs,
        visible: showGifPicker
      },
      locationComposer: {
        address: locationAddress,
        liveEnabled: liveLocationEnabled,
        livePeriodMinutes: liveLocationPeriodMinutes,
        latitude: locationLatitude,
        longitude: locationLongitude,
        onCancel: resetLocationComposer,
        onChangeAddress: setLocationAddress,
        onChangeLivePeriodMinutes: setLiveLocationPeriodMinutes,
        onChangeLatitude: setLocationLatitude,
        onChangeLongitude: setLocationLongitude,
        onChangeTitle: setLocationTitle,
        onToggleLiveMode: () => setLiveLocationEnabled((current) => !current),
        onUseCurrentLocation: handleUseCurrentLocation,
        resolvingDeviceLocation,
        title: locationTitle,
        visible: showLocationComposer
      },
      pollComposer: {
        creating: creatingPoll,
        multipleChoice: pollMultipleChoice,
        onAddOption: addPollOption,
        onCancel: resetPollComposer,
        onChangeOption: updatePollOption,
        onChangeQuestion: setPollQuestion,
        onCreate: handleCreatePoll,
        onRemoveOption: removePollOption,
        onToggleMultipleChoice: () => setPollMultipleChoice((current) => !current),
        options: pollOptions,
        question: pollQuestion,
        visible: showPollComposer
      },
      recording: {
        active: recordingVoice,
        durationLabel: recordingDurationLabel,
        onDiscard: () => handleStopVoiceRecording(true),
        onSend: () => handleStopVoiceRecording(false)
      },
      stickerPicker: {
        loading: loadingStickerPacks,
        onSendSticker: handleSendSticker,
        packs: stickerPacks,
        visible: showStickerPicker
      }
    }
  };
}
