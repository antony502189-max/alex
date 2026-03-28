import React, { useEffect, useMemo, useRef, useState } from "react";
import { Audio } from "expo-av";
import {
  FlatList,
  KeyboardAvoidingView,
  Linking,
  Platform,
  StyleSheet
} from "react-native";
import {
  attachmentTitle,
  isAudioAttachment,
  isImageAttachment,
  isQueuedUploadAttachment,
  isTrimEligibleAttachment,
  isVideoAttachment
} from "./chatAttachmentHelpers";
import { ChatConversationHeader } from "./ChatConversationHeader";
import { useChatBootstrapData } from "./useChatBootstrapData";
import { useChatBootstrapLifecycle } from "./useChatBootstrapLifecycle";
import { ChatComposerSection } from "./ChatComposerSection";
import { useChatComposerControls } from "./useChatComposerControls";
import { useChatBotDiscovery } from "./useChatBotDiscovery";
import { useChatConversationCapabilities } from "./useChatConversationCapabilities";
import { useChatDerivedTimelineState } from "./useChatDerivedTimelineState";
import { useChatHeaderState } from "./useChatHeaderState";
import { useChatMessagePresentation } from "./useChatMessagePresentation";
import { useChatOperationalEffects } from "./useChatOperationalEffects";
import { useChatPendingAttachmentItems } from "./useChatPendingAttachmentItems";
import { ChatTimelineList } from "./ChatTimelineList";
import {
  buildChatComposerSectionProps,
  buildChatTimelineListProps
} from "./chatScreenSectionProps";
import { AppScreen } from "../ui/AppScreen";
import { useChatMediaComposer } from "./useChatMediaComposer";
import { useChatMessageSendActions } from "./useChatMessageSendActions";
import { useChatMessageStateActions } from "./useChatMessageStateActions";
import { useChatConversationActions } from "./useChatConversationActions";
import { useChatQuickSendActions } from "./useChatQuickSendActions";
import { useChatRealtimeHandlers } from "./useChatRealtimeHandlers";
import { ChatSearchAndDiscoverySurface } from "./ChatSearchAndDiscoverySurface";
import { useChatModerationActions } from "./useChatModerationActions";
import { useChatSelectionActions } from "./useChatSelectionActions";
import { useChatStructuredComposer } from "./useChatStructuredComposer";
import { ChatTimelinePanels } from "./ChatTimelinePanels";
import { useChatSearchTimeline } from "./useChatSearchTimeline";
import { useChatVoiceControls } from "./useChatVoiceControls";
import {
  formatContactName,
  formatFileSize
} from "./chatMessageHelpers";
import {
  formatCooldown,
  formatDuration,
  getImagePreviewHeight,
  mergeScheduledMessages,
  parseInlineBotQuery,
  type ActiveInlineBotQuery
} from "./chatScreenUtils";
import { appColors, appSpacing } from "../../theme/tokens";
import { api } from "../../services/api";
import { generateClientMessageId } from "../../services/clientMessageIds";
import { parseAlexDeepLink, type ParsedDeepLink } from "../../navigation/deepLinks";
import { localDatabase } from "../../services/localDatabase";
import { normalizeExternalLinkUrl } from "../../services/linkUtils";
import { type MessageComposerSelection } from "../../services/messageFormatting";
import { canStartCallsFromChat } from "../../services/chatCapabilities";
import { useAttachmentTransferStore } from "../../store/useAttachmentTransferStore";
import { useAppStore } from "../../store/useAppStore";
import type {
  ChatMember,
  ChatMessage,
  ChatSummary,
  ForumTopic,
  MessageSelectionState,
  MessageAttachment,
  MessageTextEntity,
  PinnedMessageHistoryEntry,
  ScheduledMessage
} from "../../types";

export type ChatScreenProps = {
  chat: ChatSummary;
  topic?: ForumTopic | null;
  threadRootMessageId?: string | null;
  threadTitle?: string | null;
  initialFocusMessage?: { messageId: string; createdAt: string } | null;
  currentUserId: string;
  token: string;
  onBack: () => void;
  onConsumeInitialFocus?: () => void;
  onOpenChatInfo?: () => void;
  onOpenMembers?: () => void;
  onOpenDiscussionThread?: (message: ChatMessage) => void;
  onRefreshChats?: () => Promise<void> | void;
  onStartCall?: (kind: "VOICE" | "VIDEO") => void;
  onOpenMediaViewer?: (payload: {
    attachments: MessageAttachment[];
    attachmentSources?: Array<{
      attachmentId: string;
      createdAt: string;
      messageId: string;
    }>;
    initialAttachmentId: string;
    chatTitle: string;
  }) => void;
  onOpenBotMiniApp?: (
    botUserId: string,
    title: string,
    chatId?: string | null,
    startParameter?: string | null
  ) => void;
  onOpenParsedLink?: (parsedLink: ParsedDeepLink) => void;
};

const QUICK_REACTIONS = ["👍", "❤️", "🔥", "😂"];
const PAGE_SIZE = 30;
const REACTION_CHOICES = ["+1", "heart", "fire", "lol"];
const FORMAT_ACTIONS: Array<{ label: string; type: MessageTextEntity["type"] }> = [
  { label: "B", type: "BOLD" },
  { label: "I", type: "ITALIC" },
  { label: "U", type: "UNDERLINE" },
  { label: "S", type: "STRIKETHROUGH" },
  { label: "Spoiler", type: "SPOILER" },
  { label: "</>", type: "CODE" },
  { label: "Pre", type: "PRE" }
];

export function ChatScreenContent({
  chat,
  topic,
  threadRootMessageId,
  threadTitle,
  initialFocusMessage,
  currentUserId,
  token,
  onBack,
  onConsumeInitialFocus,
  onOpenChatInfo,
  onOpenMembers,
  onOpenDiscussionThread,
  onRefreshChats,
  onStartCall,
  onOpenMediaViewer,
  onOpenBotMiniApp,
  onOpenParsedLink
}: ChatScreenProps) {
  const listRef = useRef<FlatList<ChatMessage>>(null);
  const typingResetRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const recordingRef = useRef<Audio.Recording | null>(null);
  const recordingWaveformSamplesRef = useRef<number[]>([]);
  const soundRef = useRef<Audio.Sound | null>(null);
  const persistedDraftRef = useRef("");
  const isTypingRef = useRef(false);
  const typingTimeoutsRef = useRef<Record<string, ReturnType<typeof setTimeout>>>({});
  const pendingAttachmentsRef = useRef<MessageAttachment[]>([]);

  const chatMessages = useAppStore((state) => state.messagesByChat[chat.chatId] ?? []);
  const setChatMessages = useAppStore((state) => state.setChatMessages);
  const upsertMessage = useAppStore((state) => state.upsertMessage);
  const replaceMessage = useAppStore((state) => state.replaceMessage);
  const removeMessage = useAppStore((state) => state.removeMessage);
  const upsertChat = useAppStore((state) => state.upsertChat);
  const applyReadEvent = useAppStore((state) => state.applyReadEvent);
  const attachmentTransferStates = useAttachmentTransferStore((state) => state.transfers);

  const [members, setMembers] = useState<ChatMember[]>([]);
  const [scheduledMessages, setScheduledMessages] = useState<ScheduledMessage[]>([]);
  const [sendSilently, setSendSilently] = useState(false);
  const [draft, setDraft] = useState("");
  const [draftEntities, setDraftEntities] = useState<MessageTextEntity[]>([]);
  const [composerSelection, setComposerSelection] = useState<MessageComposerSelection>({
    start: 0,
    end: 0
  });
  const [pendingAttachments, setPendingAttachments] = useState<MessageAttachment[]>([]);
  const [showScheduledPanel, setShowScheduledPanel] = useState(false);
  const [editingMessageId, setEditingMessageId] = useState<string | null>(null);
  const [selectionState, setSelectionState] = useState<MessageSelectionState>({
    active: false,
    selectedMessageIds: []
  });
  const [replyToMessageId, setReplyToMessageId] = useState<string | null>(null);
  const [typingUserIds, setTypingUserIds] = useState<string[]>([]);
  const [pinnedMessageId, setPinnedMessageId] = useState<string | null>(chat.pinnedMessageId);
  const [pinnedHistory, setPinnedHistory] = useState<PinnedMessageHistoryEntry[]>([]);
  const [showPinnedHistory, setShowPinnedHistory] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [loadingPinnedHistory, setLoadingPinnedHistory] = useState(false);
  const [uploadingAttachments, setUploadingAttachments] = useState(false);
  const [openingAttachmentId, setOpeningAttachmentId] = useState<string | null>(null);
  const [recordingVoice, setRecordingVoice] = useState(false);
  const [recordingDurationMs, setRecordingDurationMs] = useState(0);
  const [playingVoiceAttachmentId, setPlayingVoiceAttachmentId] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [scheduling, setScheduling] = useState(false);
  const [cancelingScheduledMessageId, setCancelingScheduledMessageId] = useState<string | null>(null);
  const [votingMessageId, setVotingMessageId] = useState<string | null>(null);
  const [closingPollMessageId, setClosingPollMessageId] = useState<string | null>(null);
  const [reactingMessageId, setReactingMessageId] = useState<string | null>(null);
  const [currentTimeMs, setCurrentTimeMs] = useState(() => Date.now());
  const [error, setError] = useState<string | null>(null);
  const {
    activeStructuredMessageType,
    addPollOption,
    canSendContact,
    canSendLiveLocation,
    canSendLocation,
    closeRichMediaPickers,
    contactFirstName,
    contactLastName,
    contactPhoneNumber,
    contactUserId,
    handleToggleContactComposer,
    handleToggleGifPicker,
    handleToggleLocationComposer,
    handleTogglePollComposer,
    handleToggleStickerPicker,
    handleUseCurrentLocation,
    hideStructuredComposerPanels,
    loadingRecentGifs,
    loadingStickerPacks,
    locationAddress,
    locationLatitude,
    locationLongitude,
    locationTitle,
    liveLocationEnabled,
    liveLocationPeriodMinutes,
    parsedLocation,
    parsedLiveLocation,
    pollMultipleChoice,
    pollOptions,
    pollQuestion,
    preparedContactCard,
    recentGifs,
    resetContactComposer,
    resetLocationComposer,
    resetPollComposer,
    resetStructuredComposerState,
    resetStructuredMessageInputs,
    resolvingDeviceLocation,
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
    setShowGifPicker,
    showContactComposer,
    showGifPicker,
    showLocationComposer,
    showPollComposer,
    showStickerPicker,
    stickerPacks,
    updatePollOption,
    removePollOption
  } = useChatStructuredComposer({
    setError,
    token
  });

  const topicId = topic?.topicId ?? null;
  const topicClosed = Boolean(topic?.closed);
  const activeThreadRootMessageId = threadRootMessageId ?? null;
  const setSelectedMessageId = React.useCallback<React.Dispatch<React.SetStateAction<string | null>>>(
    (value) => {
      setSelectionState((current) => {
        const currentSelectedMessageId =
          current.selectedMessageIds[current.selectedMessageIds.length - 1] ?? null;
        const nextSelectedMessageId =
          typeof value === "function" ? value(currentSelectedMessageId) : value;

        if (!nextSelectedMessageId) {
          return {
            active: false,
            selectedMessageIds: []
          };
        }

        return {
          active: true,
          selectedMessageIds: [nextSelectedMessageId]
        };
      });
    },
    []
  );
  const handleMessageLongPress = React.useCallback((messageId: string) => {
    setSelectionState((current) => {
      if (!current.active || current.selectedMessageIds.length === 0) {
        return {
          active: true,
          selectedMessageIds: [messageId]
        };
      }

      const alreadySelected = current.selectedMessageIds.includes(messageId);
      const nextSelectedMessageIds = alreadySelected
        ? current.selectedMessageIds.filter((selectedId) => selectedId !== messageId)
        : [...current.selectedMessageIds, messageId];

      return nextSelectedMessageIds.length > 0
        ? {
            active: true,
            selectedMessageIds: nextSelectedMessageIds
          }
        : {
            active: false,
            selectedMessageIds: []
          };
    });
  }, []);
  const handleMessagePress = React.useCallback((messageId: string) => {
    setSelectionState((current) => {
      if (!current.active) {
        return current;
      }

      const alreadySelected = current.selectedMessageIds.includes(messageId);
      const nextSelectedMessageIds = alreadySelected
        ? current.selectedMessageIds.filter((selectedId) => selectedId !== messageId)
        : [...current.selectedMessageIds, messageId];

      return nextSelectedMessageIds.length > 0
        ? {
            active: true,
            selectedMessageIds: nextSelectedMessageIds
          }
        : {
            active: false,
            selectedMessageIds: []
          };
    });
  }, []);

  useEffect(() => {
    setSelectionState({
      active: false,
      selectedMessageIds: []
    });
  }, [activeThreadRootMessageId, chat.chatId, topicId]);
  const activeInlineQuery = useMemo(() => {
    if (
      editingMessageId ||
      pendingAttachments.length > 0 ||
      showPollComposer ||
      showLocationComposer ||
      showContactComposer ||
      recordingVoice
    ) {
      return null;
    }
    return parseInlineBotQuery(draft);
  }, [
    draft,
    editingMessageId,
    pendingAttachments.length,
    recordingVoice,
    showContactComposer,
    showLocationComposer,
    showPollComposer
  ]);
  const {
    botCommands,
    botCommandsError,
    inlineBotResults,
    inlineBotResultsError,
    loadingBotCommands,
    loadingInlineBotResults,
    retryBotCommands,
    retryInlineBotResults,
    setInlineBotResults
  } = useChatBotDiscovery({
    activeInlineQuery,
    chatType: chat.chatType,
    peerIsBot: chat.peerIsBot,
    peerUserId: chat.peerUserId,
    token
  });

  const messages = useMemo(
    () =>
      chatMessages.filter((message) => {
        if ((message.topicId ?? null) !== topicId) {
          return false;
        }
        if (activeThreadRootMessageId != null) {
          return (message.threadRootMessageId ?? null) === activeThreadRootMessageId;
        }
        return true;
      }),
    [activeThreadRootMessageId, chatMessages, topicId]
  );

  useEffect(() => {
    setSelectionState((current) => {
      if (current.selectedMessageIds.length === 0) {
        return current.active
          ? {
              active: false,
              selectedMessageIds: []
            }
          : current;
      }

      const availableMessageIds = new Set(messages.map((message) => message.messageId));
      const nextSelectedMessageIds = current.selectedMessageIds.filter((messageId) =>
        availableMessageIds.has(messageId)
      );

      if (
        nextSelectedMessageIds.length === current.selectedMessageIds.length &&
        current.active
      ) {
        return current;
      }

      return nextSelectedMessageIds.length > 0
        ? {
            active: true,
            selectedMessageIds: nextSelectedMessageIds
          }
        : {
            active: false,
            selectedMessageIds: []
          };
    });
  }, [messages]);

  const {
    displayedMessages,
    ensureMessageVisible,
    handleLoadOlder,
    hasMoreHistory,
    highlightedMessageId,
    jumpingToMessage,
    loadingOlder,
    resetTimelineSearchState,
    searchQuery,
    searchResults,
    searching,
    setHasMoreHistory,
    setSearchResults,
    setSearchQuery
  } = useChatSearchTimeline({
    activeThreadRootMessageId,
    chatId: chat.chatId,
    currentUserId,
    initialFocusMessage,
    listRef,
    loadingHistory,
    messages,
    onConsumeInitialFocus,
    pageSize: PAGE_SIZE,
    setChatMessages,
    setError,
    token,
    topicId
  });
  const {
    appendScheduledMessage,
    applyPinnedMessageId,
    handleQueuedMessageDropped,
    handleQueuedMessageSynced,
    handleQueuedScheduledDropped,
    handleQueuedScheduledSynced,
    persistMessage,
    syncQueuedMessage,
    syncSearchResult,
    touchMyLastSentAt
  } = useChatMessageStateActions({
    chat,
    currentUserId,
    mergeScheduledMessages,
    removeMessage,
    replaceMessage,
    setCurrentTimeMs,
    setMembers,
    setPinnedMessageId,
    setScheduledMessages,
    setSearchResults,
    upsertChat,
    upsertMessage
  });
  const {
    flushPendingOutbox,
    loadInitialChatData,
    refreshPinnedHistory,
    syncScheduledMessages
  } = useChatBootstrapData({
    activeThreadRootMessageId,
    chatId: chat.chatId,
    currentUserId,
    handleQueuedMessageDropped,
    handleQueuedMessageSynced,
    handleQueuedScheduledDropped,
    handleQueuedScheduledSynced,
    mergeScheduledMessages,
    pageSize: PAGE_SIZE,
    setError,
    setLoadingPinnedHistory,
    setPinnedHistory,
    setScheduledMessages,
    token,
    topicId
  });

  const {
    activeDiscussionChatId,
    activeDiscussionRootMessageId,
    activePinnedHistoryEntry,
    firstUnreadMessage,
    pinnedPreviewMessage,
    replyTarget,
    selectedMessage,
    selectedMessages,
    threadRootMessage
  } = useChatDerivedTimelineState({
    activeThreadRootMessageId,
    chatId: chat.chatId,
    chatMessages,
    currentUserId,
    lastReadAt: chat.lastReadAt,
    messages,
    pinnedHistory,
    pinnedMessageId,
    replyToMessageId,
    selectionState,
    unreadCount: chat.unreadCount
  });
  const {
    canPinMessages,
    canPost,
    channelPostingDisabled,
    memberRestricted,
    myMembership,
    optimisticAuthor,
    reactionsEnabled,
    restrictionLabel,
    slowModeEndsAt,
    slowModeLabel
  } = useChatConversationCapabilities({
    chat,
    currentTimeMs,
    currentUserId,
    members,
    topicClosed
  });
  const {
    canClosePoll,
    describeMessage,
    getAttachmentTransferMeta,
    renderMessageMeta,
    renderWaveform,
    resolveDisplaySenderName
  } = useChatMessagePresentation({
    attachmentTransferStates,
    canManageMessages: Boolean(myMembership?.canManageMessages),
    chatType: chat.chatType,
    currentUserId,
    members
  });
  const {
    handleCapturePhoto,
    handleCaptureVideo,
    handleCaptureVideoNote,
    handleInsertRecentGif,
    handlePickAttachments,
    handlePickAudioFiles,
    handlePickGifs,
    handlePickPhotos,
    handlePickVideos,
    handleUploadGifFromDevice,
    movePendingAttachment,
    removePendingAttachment,
    resolvePendingAttachmentsForSend,
    retryPendingAttachmentUpload,
    trimPendingAttachment,
    trimmingAttachmentId,
    uploadOrStageAttachment
  } = useChatMediaComposer({
    canPost,
    chatId: chat.chatId,
    editingMessageId,
    recordingVoice,
    setError,
    setPendingAttachments,
    setShowGifPicker,
    setUploadingAttachments,
    token,
    uploadingAttachments
  });
  const {
    handleStartVoiceRecording,
    handleStopVoiceRecording,
    handleToggleVoicePlayback
  } = useChatVoiceControls({
    canPost,
    editingMessageId,
    playingVoiceAttachmentId,
    recordingDurationMs,
    recordingRef,
    recordingVoice,
    recordingWaveformSamplesRef,
    setError,
    setPendingAttachments,
    setPlayingVoiceAttachmentId,
    setRecordingDurationMs,
    setRecordingVoice,
    setUploadingAttachments,
    soundRef,
    token,
    uploadingAttachments,
    uploadOrStageAttachment
  });
  const {
    items: pendingAttachmentItems,
    summary: pendingAttachmentSummary
  } = useChatPendingAttachmentItems({
    attachmentTitle,
    formatDuration,
    formatFileSize,
    getAttachmentTransferMeta,
    isAudioAttachment,
    isImageAttachment,
    isQueuedUploadAttachment,
    isTrimEligibleAttachment,
    isVideoAttachment,
    pendingAttachments,
    renderWaveform,
    transferStates: attachmentTransferStates,
    /*
            ? `${formatDuration(attachment.durationMs)} · ${formatFileSize(attachment.fileSizeBytes)}`
              ? `${attachmentTitle(attachment)} · ${formatFileSize(attachment.fileSizeBytes)}`
    */
    uploadingAttachments
  });
  const getTimelineListProps = () => buildChatTimelineListProps({
    activeThreadRootMessageId,
    allChatMessages: chatMessages,
    attachmentTitle,
    canClosePoll,
    closingPollMessageId,
    contentContainerStyle: styles.messagesContent,
    currentUserId,
    describeMessage,
    displayedMessages,
    firstUnreadMessageId: firstUnreadMessage?.messageId ?? null,
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
    onClosePoll: (message) => void handleClosePoll(message),
    onEnsureMessageVisible: (target) => void ensureMessageVisible(target),
    onMessageLongPress: handleMessageLongPress,
    onMessagePress: handleMessagePress,
    onOpenLink: handleOpenTextLink,
    onOpenAttachment: (attachment, mediaAlbum, sourceMessage) =>
      void handleOpenAttachment(attachment, mediaAlbum, sourceMessage),
    onOpenDiscussionThread,
    onToggleReaction: (emoji, message) => void handleToggleReaction(emoji, message),
    onToggleVoicePlayback: (attachment) => void handleToggleVoicePlayback(attachment),
    onVotePoll: (message, optionId) => void handleVotePoll(message, optionId),
    openingAttachmentId,
    pinnedMessageId,
    playingVoiceAttachmentId,
    reactionsEnabled,
    renderMessageMeta,
    renderWaveform,
    resolveDisplaySenderName,
    searchQuery,
    selectionState,
    showOpenDiscussionThread: !activeThreadRootMessageId,
    summaryChatType: chat.chatType,
    votingMessageId
  });
  const getComposerSectionProps = () => buildChatComposerSectionProps({
    activeStructuredMessageType,
    addPollOption,
    canFormatSelection,
    canPost,
    canSendBlockedByRestriction: memberRestricted,
    canSendBlockedBySlowMode: Boolean(slowModeEndsAt),
    chatType: chat.chatType,
    composerSelectionChange: handleComposerSelectionChange,
    contactFirstName,
    contactLastName,
    contactPhoneNumber,
    contactUserId,
    creatingPoll: sending,
    draft,
    editingMessageId,
    formatActions: FORMAT_ACTIONS,
    formatFileSize,
    handleCapturePhoto: () => void handleCapturePhoto(),
    handleCaptureVideo: () => void handleCaptureVideo(),
    handleCaptureVideoNote: () => void handleCaptureVideoNote(),
    handleChangeDraft: handleDraftChange,
    handleCreatePoll: () => void handleCreatePoll(),
    handleInsertRecentGif,
    handlePickAttachments: () => void handlePickAttachments(),
    handlePickAudioFiles: () => void handlePickAudioFiles(),
    handlePickPhotos: () => void handlePickPhotos(),
    handlePickVideos: () => void handlePickVideos(),
    handleScheduleMessage: () => void handleScheduleMessage(),
    handleSend,
    handleSendSticker: (stickerId) => void handleSendSticker(stickerId),
    handleSendWhenOnline: () => void handleSendWhenOnline(),
    handleStartVoiceRecording: () => void handleStartVoiceRecording(),
    handleStopVoiceRecording: (discard?: boolean) => void handleStopVoiceRecording(discard),
    handleToggleContactComposer,
    handleToggleFormatting,
    handleToggleGifPicker: () => void handleToggleGifPicker(),
    handleToggleLocationComposer,
    handleTogglePollComposer,
    handleToggleStickerPicker: () => void handleToggleStickerPicker(),
    handleUseCurrentLocation: () => void handleUseCurrentLocation(),
    handleUploadGifFromDevice: () => void handleUploadGifFromDevice(),
    hasComposerContent,
    isFormattingActive,
    items: pendingAttachmentItems,
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
    onRemoveAttachment: (attachment) => void removePendingAttachment(attachment),
    onRetryAttachment: (attachment) => void retryPendingAttachmentUpload(attachment),
    onTrimAttachment: (attachment, startMs, endMs) =>
      trimPendingAttachment(attachment, startMs, endMs),
    pendingAttachmentsCount: pendingAttachments.length,
    pollMultipleChoice,
    pollOptions,
    pollQuestion,
    recentGifs,
    recordingDurationLabel: formatDuration(recordingDurationMs),
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
    structuredMessageType: activeStructuredMessageType,
    trimmingAttachmentId,
    updatePollOption,
    uploadingAttachments
  });

  const {
    canFormatSelection,
    handleComposerSelectionChange,
    handleDraftChange,
    handleInsertBotCommand,
    handleOpenBotMiniApp,
    handleToggleFormatting,
    hasComposerContent,
    isFormattingActive,
    normalizedComposerDraft,
    resetComposerState
  } = useChatComposerControls({
    canPost,
    canSendContact,
    canSendLiveLocation,
    canSendLocation,
    chatId: chat.chatId,
    chatTitle: chat.title,
    closeRichMediaPickers,
    composerSelection,
    draft,
    draftEntities,
    editingMessageId,
    hideStructuredComposerPanels,
    onOpenBotMiniApp,
    peerDisplayName: chat.peerDisplayName,
    peerUserId: chat.peerUserId,
    pendingAttachmentCount: pendingAttachments.length,
    recordingVoice,
    resetStructuredComposerState,
    sending,
    setComposerSelection,
    setDraft,
    setDraftEntities,
    setEditingMessageId,
    setInlineBotResults,
    setPendingAttachments,
    setReplyToMessageId,
    setSelectedMessageId,
    setSendSilently,
    uploadingAttachments
  });
  const {
    handlePinEvent,
    handleReadEvent,
    handleTypingEvent
  } = useChatRealtimeHandlers({
    applyPinnedMessageId,
    applyReadEvent,
    currentUserId,
    refreshPinnedHistory,
    setMembers,
    setTypingUserIds,
    typingTimeoutsRef
  });
  const { typingLabel } = useChatOperationalEffects({
    canPost,
    chatId: chat.chatId,
    currentUserId,
    draft,
    editingMessageId,
    handleReadEvent,
    isTypingRef,
    members,
    messages,
    onRefreshChats,
    persistedDraftRef,
    setCurrentTimeMs,
    setError,
    slowModeEndsAt,
    token,
    typingResetRef,
    typingUserIds,
    upsertChat
  });

  useChatBootstrapLifecycle({
    activeThreadRootMessageId,
    chatId: chat.chatId,
    chatPinnedMessageId: chat.pinnedMessageId,
    currentUserId,
    draftText: chat.draftText,
    flushPendingOutbox,
    handlePinEvent,
    handleReadEvent,
    handleTypingEvent,
    isTypingRef,
    loadInitialChatData,
    mergeScheduledMessages,
    pageSize: PAGE_SIZE,
    pendingAttachments,
    pendingAttachmentsRef,
    persistedDraftRef,
    resetStructuredComposerState,
    resetTimelineSearchState,
    setChatMessages,
    setComposerSelection,
    setDraft,
    setDraftEntities,
    setError,
    setHasMoreHistory,
    setLoadingHistory,
    setLoadingPinnedHistory,
    setMembers,
    setPendingAttachments,
    setPinnedHistory,
    setPinnedMessageId,
    setScheduledMessages,
    setSendSilently,
    setShowPinnedHistory,
    setShowScheduledPanel,
    syncScheduledMessages,
    token,
    topicId,
    typingResetRef,
    typingTimeoutsRef
  });

  const effectiveReplyToMessageId = replyToMessageId ?? activeThreadRootMessageId ?? null;
  const {
    handleCreatePoll,
    handleSendInlineResult,
    handleSendSticker
  } = useChatQuickSendActions({
    activeDiscussionChatId,
    activeDiscussionRootMessageId,
    activeInlineQuery: activeInlineQuery
      ? { query: activeInlineQuery.query }
      : null,
    activeThreadRootMessageId,
    canPost,
    chatId: chat.chatId,
    closeRichMediaPickers,
    currentUserId,
    editingMessageId,
    effectiveReplyToMessageId,
    optimisticAuthor,
    persistMessage,
    pollMultipleChoice,
    pollOptions,
    pollQuestion,
    recordingVoice,
    resetComposerState,
    resetPollComposer,
    sendSilently,
    sending,
    setError,
    setReplyToMessageId,
    setSelectedMessageId,
    setSending,
    showPollComposer,
    stickerPacks,
    syncQueuedMessage,
    token,
    topicId,
    touchMyLastSentAt,
    uploadingAttachments
  });
  const {
    handleScheduleMessage,
    handleSend,
    handleSendWhenOnline
  } = useChatMessageSendActions({
    activeDiscussionChatId,
    activeDiscussionRootMessageId,
    activeStructuredMessageType,
    activeThreadRootMessageId,
    appendScheduledMessage,
    canPost,
    canSendContact,
    canSendLiveLocation,
    canSendLocation,
    chatId: chat.chatId,
    chatType: chat.chatType,
    currentUserId,
    editingMessageId,
    effectiveReplyToMessageId,
    normalizedComposerDraft,
    optimisticAuthor,
    parsedLiveLocation,
    parsedLocation,
    pendingAttachments,
    preparedContactCard,
    recordingVoice,
    resetComposerState,
    resolvePendingAttachmentsForSend,
    scheduling,
    sending,
    sendSilently,
    setError,
    setScheduling,
    setSending,
    setShowScheduledPanel,
    showPollComposer,
    syncQueuedMessage,
    token,
    topicId,
    touchMyLastSentAt,
    uploadingAttachments,
    persistMessage
  });
  const {
    handleCancelScheduledMessage,
    handleClosePoll,
    handleVotePoll
  } = useChatModerationActions({
    cancelingScheduledMessageId,
    chatId: chat.chatId,
    closingPollMessageId,
    currentUserId,
    persistMessage,
    setCancelingScheduledMessageId,
    setClosingPollMessageId,
    setError,
    setScheduledMessages,
    setSelectedMessageId,
    setVotingMessageId,
    token,
    votingMessageId
  });
  const {
    handleArchiveChat,
    handleMuteChat,
    handleOpenAttachment
  } = useChatConversationActions({
    attachmentTransferStates,
    chatArchived: chat.archived,
    chatId: chat.chatId,
    chatMutedUntil: chat.mutedUntil,
    chatTitle: chat.title,
    onBack,
    onOpenMediaViewer,
    onRefreshChats,
    setError,
    setOpeningAttachmentId,
    token,
    upsertChat
  });
  const {
    beginEditSelected,
    beginReplySelected,
    cancelComposerModes,
    handleDeleteSelected,
    handleForwardSelected,
    handlePinSelected,
    handleRefreshLiveLocation,
    handleReportSelected,
    handleShareSelected,
    handleStopLiveLocation,
    handleToggleReaction
  } = useChatSelectionActions({
    activeDiscussionChatId,
    activeDiscussionRootMessageId,
    activeThreadRootMessageId,
    canPinMessages,
    canPost,
    chatId: chat.chatId,
    currentUserId,
    describeMessage,
    effectiveReplyToMessageId,
    optimisticAuthor,
    persistMessage,
    reactionsEnabled,
    reactingMessageId,
    resetStructuredMessageInputs,
    selectedMessage,
    selectedMessages,
    sending,
    setComposerSelection,
    setDraft,
    setDraftEntities,
    setEditingMessageId,
    setError,
    setReactingMessageId,
    setReplyToMessageId,
    setSelectedMessageId,
    setSending,
    syncQueuedMessage,
    token,
    topicId,
    touchMyLastSentAt,
    onPinEvent: handlePinEvent
  });

  const {
    archiveToggleLabel,
    headerPresentation,
    muteToggleLabel,
    pinnedHistoryToggleLabel,
    pinnedPreviewText,
    replyPanelTitle,
    scheduledPanelTitle
  } = useChatHeaderState({
    activeThreadRootMessageId,
    chat,
    describeMessage,
    editingMessageId,
    loadingPinnedHistory,
    memberCount: members.length || chat.memberCount,
    pinnedHistoryLength: pinnedHistory.length,
    pinnedPreviewMessage,
    showPinnedHistory,
    threadCommentCount: threadRootMessage?.commentCount,
    threadTitle,
    topic
  });

  function handleOpenTextLink(url: string) {
    const parsedLink = parseAlexDeepLink(url);
    if (parsedLink && onOpenParsedLink) {
      onOpenParsedLink(parsedLink);
      return;
    }

    void Linking.openURL(normalizeExternalLinkUrl(url)).catch((openError) => {
      setError(openError instanceof Error ? openError.message : "Unable to open link");
    });
  }

  return (
    <AppScreen backgroundColor={appColors.background}>
      <KeyboardAvoidingView
        behavior={Platform.select({ ios: "padding", android: "height" })}
        keyboardVerticalOffset={Platform.OS === "ios" ? 8 : 0}
        style={styles.screen}
      >
        <ChatConversationHeader
          archiveToggleLabel={archiveToggleLabel}
          chatPhotoUrl={chat.photoUrl}
          chatTitle={chat.title}
          headerSubtitle={headerPresentation.subtitle}
          headerTitle={headerPresentation.title}
          muteToggleLabel={muteToggleLabel}
          onArchive={() => void handleArchiveChat()}
          onBack={onBack}
          onOpenChatInfo={onOpenChatInfo}
          onMute={() => void handleMuteChat()}
          onOpenMembers={onOpenMembers}
          onOpenMiniApp={() => void handleOpenBotMiniApp()}
          onStartCall={onStartCall}
          onTogglePinnedHistory={() => setShowPinnedHistory((current) => !current)}
          onToggleScheduledPanel={() => setShowScheduledPanel((current) => !current)}
          pinnedHistoryToggleLabel={pinnedHistoryToggleLabel}
          showInfoAction={chat.chatType !== "SAVED" && !!onOpenChatInfo}
          showMembersAction={chat.chatType !== "DIRECT" && chat.chatType !== "SAVED" && !!onOpenMembers}
          showMiniAppAction={chat.chatType === "DIRECT" && chat.peerIsBot && !!chat.peerBotWebAppUrl}
          showStartCallActions={canStartCallsFromChat(chat) && !!onStartCall}
          typingLabel={typingLabel}
        />

        <ChatTimelinePanels
          activePinnedHistoryEntry={activePinnedHistoryEntry}
          activeThreadRootMessageId={activeThreadRootMessageId}
          cancelingScheduledMessageId={cancelingScheduledMessageId}
          canPinMessages={canPinMessages}
          channelPostingDisabled={channelPostingDisabled}
          currentUserId={currentUserId}
          describeMessage={describeMessage}
          editingMessageId={editingMessageId}
          firstUnreadMessage={firstUnreadMessage}
          loadingPinnedHistory={loadingPinnedHistory}
          onCancelComposerModes={cancelComposerModes}
          onCancelScheduledMessage={(scheduledMessageId) =>
            void handleCancelScheduledMessage(scheduledMessageId)
          }
          onCloseSelectedMessage={() => setSelectedMessageId(null)}
          onDeleteSelected={handleDeleteSelected}
          onEditSelected={beginEditSelected}
          onEnsureMessageVisible={(target) => void ensureMessageVisible(target)}
          onForwardSelected={handleForwardSelected}
          onOpenLink={handleOpenTextLink}
          onPinSelected={handlePinSelected}
          onRefreshLiveLocation={handleRefreshLiveLocation}
          onReportSelected={handleReportSelected}
          onReplySelected={beginReplySelected}
          onShareSelected={() => void handleShareSelected()}
          onStopLiveLocation={handleStopLiveLocation}
          onToggleReaction={(emoji, message) => void handleToggleReaction(emoji, message)}
          pinnedHistory={pinnedHistory}
          pinnedPreviewMessage={pinnedPreviewMessage}
          pinnedPreviewText={pinnedPreviewText}
          reactionChoices={REACTION_CHOICES}
          reactionsEnabled={reactionsEnabled}
          replyPanelTitle={replyPanelTitle}
          replyTarget={replyTarget}
          replyToMessageId={replyToMessageId}
          scheduledMessages={scheduledMessages}
          scheduledPanelTitle={scheduledPanelTitle}
          selectedMessage={selectedMessage}
          selectedMessages={selectedMessages}
          showPinnedHistory={showPinnedHistory}
          showPinnedPanel={pinnedMessageId != null}
          showReplyPanel={Boolean(editingMessageId || replyToMessageId || activeThreadRootMessageId)}
          showScheduledPanel={showScheduledPanel}
          showUnreadPanel={Boolean(firstUnreadMessage && searchQuery.trim().length < 2)}
          slowModeLabel={slowModeLabel}
          threadRootMessage={threadRootMessage}
          topicClosed={topicClosed}
          unreadCount={chat.unreadCount}
        />

        <ChatSearchAndDiscoverySurface
          activeInlineBotUsername={activeInlineQuery?.botUsername ?? null}
          botCommands={botCommands}
          botCommandsError={botCommandsError}
          error={error}
          hasMoreHistory={hasMoreHistory}
          inlineBotResults={inlineBotResults}
          inlineBotResultsError={inlineBotResultsError}
          jumpingToMessage={jumpingToMessage}
          loadingBotCommands={loadingBotCommands}
          loadingHistory={loadingHistory}
          loadingInlineBotResults={loadingInlineBotResults}
          loadingOlder={loadingOlder}
          onChangeSearchQuery={setSearchQuery}
          onClearSearch={resetTimelineSearchState}
          onInsertBotCommand={handleInsertBotCommand}
          onLoadOlder={() => void handleLoadOlder()}
          onOpenMiniApp={() => void handleOpenBotMiniApp()}
          onRetryBotCommands={retryBotCommands}
          onRetryInlineBotResults={retryInlineBotResults}
          onSendInlineResult={(result) => void handleSendInlineResult(result)}
          restrictionLabel={restrictionLabel}
          restrictionReason={myMembership?.restrictionReason ?? null}
          searchQuery={searchQuery}
          searchResultsCount={searchResults.length}
          searching={searching}
          showBotCommandsPanel={
            chat.chatType === "DIRECT" &&
            chat.peerIsBot &&
            (loadingBotCommands ||
              botCommands.length > 0 ||
              botCommandsError != null ||
              !!chat.peerBotWebAppUrl) &&
            !activeThreadRootMessageId
          }
          showLoadOlderButton={searchQuery.trim().length < 2}
          showMiniAppAction={!!chat.peerBotWebAppUrl}
        />

        <ChatTimelineList {...getTimelineListProps()} />

        <ChatComposerSection {...getComposerSectionProps()} />
      </KeyboardAvoidingView>
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  messagesContent: {
    paddingHorizontal: appSpacing.xl,
    paddingVertical: appSpacing.lg,
    gap: appSpacing.md
  }
});


