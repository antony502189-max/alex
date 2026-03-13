import React, { useEffect, useMemo, useRef, useState } from "react";
import * as DocumentPicker from "expo-document-picker";
import { Audio } from "expo-av";
import * as Sharing from "expo-sharing";
import {
  ActivityIndicator,
  FlatList,
  Image,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  View
} from "react-native";
import type {
  NativeSyntheticEvent,
  TextInputSelectionChangeEventData
} from "react-native";
import { Avatar } from "../components/Avatar";
import { FormattedMessageText } from "../components/FormattedMessageText";
import { api } from "../services/api";
import {
  cleanupStagedAttachment,
  isPendingLocalAttachment,
  stageAttachment,
  uploadPendingAttachment
} from "../services/attachmentDrafts";
import { attachmentTransfers } from "../services/attachmentTransfers";
import { formatPresenceStatus } from "../services/presence";
import {
  fromQueuedScheduledMessageId,
  generateClientMessageId
} from "../services/clientMessageIds";
import { localDatabase } from "../services/localDatabase";
import {
  toggleMessageEntity,
  trimFormattedMessage,
  type MessageComposerSelection
} from "../services/messageFormatting";
import { messageOutbox } from "../services/messageOutbox";
import { scheduledMessageOutbox } from "../services/scheduledMessageOutbox";
import { wsService } from "../services/ws";
import { useAttachmentTransferStore } from "../store/useAttachmentTransferStore";
import { useAppStore } from "../store/useAppStore";
import type {
  BotCommand,
  ChatMember,
  ChatMessage,
  ChatReadEvent,
  ChatSummary,
  ForumTopic,
  InlineBotResult,
  MessageAttachment,
  MessageContactCard,
  MessageLocation,
  MessageTextEntity,
  PinMessageEvent,
  PinnedMessageHistoryEntry,
  ScheduledMessage,
  StickerPack,
  TypingEvent
} from "../types";

type ChatScreenProps = {
  chat: ChatSummary;
  topic?: ForumTopic | null;
  threadRootMessageId?: string | null;
  threadTitle?: string | null;
  currentUserId: string;
  token: string;
  onBack: () => void;
  onOpenMembers?: () => void;
  onOpenDiscussionThread?: (message: ChatMessage) => void;
  onRefreshChats?: () => Promise<void> | void;
  onStartCall?: (kind: "VOICE" | "VIDEO") => void;
  onOpenSecretChat?: () => void;
  onOpenBotMiniApp?: (
    botUserId: string,
    title: string,
    chatId?: string | null,
    startParameter?: string | null
  ) => void;
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

function mergeScheduledMessages(messages: ScheduledMessage[]) {
  const map = new Map<string, ScheduledMessage>();

  function buildKey(message: ScheduledMessage) {
    return message.clientMessageId
      ? `client:${message.clientMessageId}`
      : `scheduled:${message.scheduledMessageId}`;
  }

  function preferMessage(left: ScheduledMessage, right: ScheduledMessage) {
    if (left.status === "QUEUED" && right.status !== "QUEUED") {
      return right;
    }
    if (right.status === "QUEUED" && left.status !== "QUEUED") {
      return left;
    }
    return right.createdAt.localeCompare(left.createdAt) >= 0 ? right : left;
  }

  for (const message of messages) {
    const key = buildKey(message);
    const existing = map.get(key);
    map.set(key, existing ? preferMessage(existing, message) : message);
  }

  return [...map.values()].sort((left, right) =>
    left.scheduledAt.localeCompare(right.scheduledAt)
  );
}

class PendingAttachmentUploadError extends Error {
  constructor(
    message: string,
    public readonly attachments: MessageAttachment[],
    public readonly cause: unknown
  ) {
    super(message);
  }
}

type ActiveInlineBotQuery = {
  botUsername: string;
  query: string;
};

function parseInlineBotQuery(value: string): ActiveInlineBotQuery | null {
  const trimmed = value.trimStart();
  const match = trimmed.match(/^@([A-Za-z0-9_]{3,64})(?:\s+(.*))?$/s);
  if (!match) {
    return null;
  }
  return {
    botUsername: match[1].toLowerCase(),
    query: match[2]?.trim() ?? ""
  };
}

function loadImageDimensions(uri: string): Promise<{ width: number; height: number } | null> {
  return new Promise((resolve) => {
    Image.getSize(
      uri,
      (width, height) => {
        if (width > 0 && height > 0) {
          resolve({ width, height });
          return;
        }
        resolve(null);
      },
      () => resolve(null)
    );
  });
}

function compactWaveformSamples(samples: number[], targetSize = 24) {
  if (samples.length === 0) {
    return [];
  }
  if (samples.length <= targetSize) {
    return samples.map((sample) => Math.max(0, Math.min(100, Math.round(sample))));
  }

  const compacted: number[] = [];
  const bucketSize = samples.length / targetSize;
  for (let index = 0; index < targetSize; index += 1) {
    const start = Math.floor(index * bucketSize);
    const end = Math.min(samples.length, Math.floor((index + 1) * bucketSize));
    const chunk = samples.slice(start, Math.max(start + 1, end));
    const average = chunk.reduce((sum, sample) => sum + sample, 0) / chunk.length;
    compacted.push(Math.max(0, Math.min(100, Math.round(average))));
  }
  return compacted;
}

function meteringToWaveformSample(metering: number) {
  const normalized = ((Math.max(-60, Math.min(0, metering)) + 60) / 60) * 100;
  return Math.max(4, Math.min(100, Math.round(normalized)));
}

function getImagePreviewHeight(attachment: MessageAttachment) {
  if (!attachment.width || !attachment.height || attachment.width <= 0) {
    return 220;
  }
  const scaled = Math.round((220 * attachment.height) / attachment.width);
  return Math.max(120, Math.min(360, scaled));
}

function formatProgressPercent(progress: number) {
  return `${Math.max(0, Math.min(100, Math.round(progress * 100)))}%`;
}

export function ChatScreen({
  chat,
  topic,
  threadRootMessageId,
  threadTitle,
  currentUserId,
  token,
  onBack,
  onOpenMembers,
  onOpenDiscussionThread,
  onRefreshChats,
  onStartCall,
  onOpenSecretChat,
  onOpenBotMiniApp
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
  const [botCommands, setBotCommands] = useState<BotCommand[]>([]);
  const [inlineBotResults, setInlineBotResults] = useState<InlineBotResult[]>([]);
  const [scheduledMessages, setScheduledMessages] = useState<ScheduledMessage[]>([]);
  const [sendSilently, setSendSilently] = useState(false);
  const [draft, setDraft] = useState("");
  const [draftEntities, setDraftEntities] = useState<MessageTextEntity[]>([]);
  const [composerSelection, setComposerSelection] = useState<MessageComposerSelection>({
    start: 0,
    end: 0
  });
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<ChatMessage[]>([]);
  const [pendingAttachments, setPendingAttachments] = useState<MessageAttachment[]>([]);
  const [showPollComposer, setShowPollComposer] = useState(false);
  const [showLocationComposer, setShowLocationComposer] = useState(false);
  const [showContactComposer, setShowContactComposer] = useState(false);
  const [showStickerPicker, setShowStickerPicker] = useState(false);
  const [showScheduledPanel, setShowScheduledPanel] = useState(false);
  const [stickerPacks, setStickerPacks] = useState<StickerPack[]>([]);
  const [pollQuestion, setPollQuestion] = useState("");
  const [pollOptions, setPollOptions] = useState<string[]>(["", ""]);
  const [pollMultipleChoice, setPollMultipleChoice] = useState(false);
  const [locationLatitude, setLocationLatitude] = useState("");
  const [locationLongitude, setLocationLongitude] = useState("");
  const [locationTitle, setLocationTitle] = useState("");
  const [locationAddress, setLocationAddress] = useState("");
  const [contactFirstName, setContactFirstName] = useState("");
  const [contactLastName, setContactLastName] = useState("");
  const [contactPhoneNumber, setContactPhoneNumber] = useState("");
  const [contactUserId, setContactUserId] = useState("");
  const [editingMessageId, setEditingMessageId] = useState<string | null>(null);
  const [selectedMessageId, setSelectedMessageId] = useState<string | null>(null);
  const [replyToMessageId, setReplyToMessageId] = useState<string | null>(null);
  const [typingUserIds, setTypingUserIds] = useState<string[]>([]);
  const [pinnedMessageId, setPinnedMessageId] = useState<string | null>(chat.pinnedMessageId);
  const [pinnedHistory, setPinnedHistory] = useState<PinnedMessageHistoryEntry[]>([]);
  const [showPinnedHistory, setShowPinnedHistory] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [loadingPinnedHistory, setLoadingPinnedHistory] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [hasMoreHistory, setHasMoreHistory] = useState(true);
  const [searching, setSearching] = useState(false);
  const [loadingInlineBotResults, setLoadingInlineBotResults] = useState(false);
  const [uploadingAttachments, setUploadingAttachments] = useState(false);
  const [loadingStickerPacks, setLoadingStickerPacks] = useState(false);
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

  const topicId = topic?.topicId ?? null;
  const topicClosed = Boolean(topic?.closed);
  const activeThreadRootMessageId = threadRootMessageId ?? null;
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

  const latestMessageId = messages[messages.length - 1]?.messageId ?? null;

  async function syncScheduledMessages() {
    const nextScheduledMessages = await api.getScheduledMessages(
      token,
      chat.chatId,
      topicId,
      activeThreadRootMessageId
    );
    setScheduledMessages(mergeScheduledMessages(nextScheduledMessages));
    await localDatabase.replaceScheduledMessages(
      currentUserId,
      chat.chatId,
      nextScheduledMessages,
      topicId,
      activeThreadRootMessageId
    );
    return nextScheduledMessages;
  }

  async function flushPendingOutbox() {
    await messageOutbox.flush(token, currentUserId, {
      onSynced: handleQueuedMessageSynced,
      onDropped: handleQueuedMessageDropped
    });
    await scheduledMessageOutbox.flush(token, currentUserId, {
      onSynced: handleQueuedScheduledSynced,
      onDropped: handleQueuedScheduledDropped
    });
  }

  async function refreshPinnedHistory() {
    setLoadingPinnedHistory(true);
    try {
      const nextPinnedHistory = await api.getPinnedMessages(token, chat.chatId, 20);
      setPinnedHistory(nextPinnedHistory);
      return nextPinnedHistory;
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load pinned history");
      return [];
    } finally {
      setLoadingPinnedHistory(false);
    }
  }

  function formatDuration(durationMs: number | null | undefined) {
    const totalSeconds = Math.max(0, Math.round((durationMs ?? 0) / 1000));
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${seconds.toString().padStart(2, "0")}`;
  }

  function formatCooldown(totalMs: number) {
    const totalSeconds = Math.max(1, Math.ceil(totalMs / 1000));
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return minutes > 0 ? `${minutes}m ${seconds}s` : `${seconds}s`;
  }

  function renderWaveform(
    attachment: MessageAttachment,
    color: string
  ) {
    if (!attachment.waveform || attachment.waveform.length === 0) {
      return null;
    }
    return (
      <View style={styles.waveformRow}>
        {attachment.waveform.map((sample, index) => (
          <View
            key={`${attachment.attachmentId}-wave-${index}`}
            style={[
              styles.waveformBar,
              {
                backgroundColor: color,
                height: Math.max(6, Math.round((24 * sample) / 100))
              }
            ]}
          />
        ))}
      </View>
    );
  }

  function getAttachmentTransferMeta(attachment: MessageAttachment) {
    const transfer = attachmentTransferStates[attachment.attachmentId];
    if (!transfer) {
      return null;
    }

    if (transfer.direction === "UPLOAD") {
      if (transfer.status === "RUNNING") {
        return `Uploading ${formatProgressPercent(transfer.progress)}`;
      }
      if (transfer.status === "FAILED") {
        return "Upload interrupted. Retry on send.";
      }
      return null;
    }

    if (transfer.status === "RUNNING") {
      return `Downloading ${formatProgressPercent(transfer.progress)} - tap to pause`;
    }
    if (transfer.status === "PAUSED") {
      return `Download paused at ${formatProgressPercent(transfer.progress)} - tap to resume`;
    }
    if (transfer.status === "FAILED") {
      return "Download failed - tap to retry";
    }
    if (transfer.status === "COMPLETED") {
      return "Open local copy";
    }
    return null;
  }

  function isImageAttachment(attachment: MessageAttachment) {
    return (
      attachment.kind === "IMAGE" ||
      attachment.kind === "GIF" ||
      attachment.contentType.startsWith("image/")
    );
  }

  function isAudioAttachment(attachment: MessageAttachment) {
    return (
      attachment.kind === "VOICE" ||
      attachment.kind === "AUDIO" ||
      attachment.contentType.startsWith("audio/")
    );
  }

  function isVideoAttachment(attachment: MessageAttachment) {
    return (
      attachment.kind === "VIDEO" ||
      attachment.kind === "VIDEO_NOTE" ||
      attachment.contentType.startsWith("video/")
    );
  }

  function attachmentTitle(attachment: MessageAttachment) {
    switch (attachment.kind) {
      case "VOICE":
        return "Voice message";
      case "AUDIO":
        return "Audio";
      case "VIDEO":
        return "Video";
      case "VIDEO_NOTE":
        return "Video note";
      case "GIF":
        return "GIF";
      case "IMAGE":
        return "Photo";
      default:
        return attachment.originalFileName;
    }
  }

  function isQueuedUploadAttachment(attachment: MessageAttachment) {
    return isPendingLocalAttachment(attachment);
  }

  useEffect(() => {
    pendingAttachmentsRef.current = pendingAttachments;
  }, [pendingAttachments]);

  useEffect(() => {
    let cancelled = false;
    let cachedHistory: ChatMessage[] = [];
    let cachedScheduledMessages: ScheduledMessage[] = [];

    async function loadState() {
      setLoadingHistory(true);
      setError(null);
      try {
        cachedHistory = await localDatabase.getMessages(
          currentUserId,
          chat.chatId,
          PAGE_SIZE,
          topicId,
          activeThreadRootMessageId
        );
        cachedScheduledMessages = await localDatabase.getScheduledMessages(
          currentUserId,
          chat.chatId,
          topicId,
          activeThreadRootMessageId
        );
        if (!cancelled && cachedHistory.length > 0) {
          setChatMessages(chat.chatId, cachedHistory);
        }
        if (!cancelled && cachedScheduledMessages.length > 0) {
          setScheduledMessages(cachedScheduledMessages);
        }

        await flushPendingOutbox().catch(() => undefined);
        setLoadingPinnedHistory(true);
        const [history, nextMembers, nextScheduledMessages, nextPinnedHistory] = await Promise.all([
          api.getMessages(token, chat.chatId, PAGE_SIZE, topicId, activeThreadRootMessageId),
          api.getChatMembers(token, chat.chatId),
          api.getScheduledMessages(token, chat.chatId, topicId, activeThreadRootMessageId),
          api.getPinnedMessages(token, chat.chatId, 20).catch(() => [])
        ]);
        if (!cancelled) {
          setChatMessages(chat.chatId, history);
          void localDatabase.upsertMessages(currentUserId, history).catch(() => undefined);
          setMembers(nextMembers);
          setScheduledMessages(mergeScheduledMessages(nextScheduledMessages));
          setPinnedHistory(nextPinnedHistory);
          void localDatabase.replaceScheduledMessages(
            currentUserId,
            chat.chatId,
            nextScheduledMessages,
            topicId,
            activeThreadRootMessageId
          ).catch(() => undefined);
          setHasMoreHistory(history.length === PAGE_SIZE);
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(
            cachedHistory.length > 0 || cachedScheduledMessages.length > 0
              ? "Offline mode. Showing cached messages."
              : loadError instanceof Error
                ? loadError.message
                : "Unable to load chat"
          );
        }
      } finally {
        if (!cancelled) {
          setLoadingHistory(false);
          setLoadingPinnedHistory(false);
        }
      }
    }

    const unsubscribe = wsService.subscribeToChat(chat.chatId, {
      onTyping: handleTypingEvent,
      onRead: handleReadEvent,
      onPin: handlePinEvent
    });
    const unsubscribeConnection = wsService.onConnectionChange((connected) => {
      if (!connected || cancelled) {
        return;
      }

      void flushPendingOutbox()
        .then(() => syncScheduledMessages())
        .catch(() => undefined);
    });

    setPinnedMessageId(chat.pinnedMessageId);
    setSearchQuery("");
    setSearchResults([]);
    setPendingAttachments([]);
    setShowPollComposer(false);
    setShowLocationComposer(false);
    setShowContactComposer(false);
    setShowStickerPicker(false);
    setShowScheduledPanel(false);
    setPollQuestion("");
    setPollOptions(["", ""]);
    setPollMultipleChoice(false);
    setLocationLatitude("");
    setLocationLongitude("");
    setLocationTitle("");
    setLocationAddress("");
    setContactFirstName("");
    setContactLastName("");
    setContactPhoneNumber("");
    setContactUserId("");
    setPinnedHistory([]);
    setShowPinnedHistory(false);
    setSendSilently(false);
    persistedDraftRef.current = chat.draftText?.trim() ?? "";
    setDraft(chat.draftText ?? "");
    setDraftEntities([]);
    setComposerSelection({ start: 0, end: 0 });
    setHasMoreHistory(true);
    void loadState();

    return () => {
      cancelled = true;
      unsubscribe();
      unsubscribeConnection();
      if (isTypingRef.current) {
        void api.sendTyping(token, chat.chatId, false).catch(() => undefined);
      }
      if (typingResetRef.current) {
        clearTimeout(typingResetRef.current);
      }
      Object.values(typingTimeoutsRef.current).forEach((timeoutId) => {
        clearTimeout(timeoutId);
      });
      typingTimeoutsRef.current = {};
      void Promise.all(
        pendingAttachmentsRef.current
          .filter((attachment) => isQueuedUploadAttachment(attachment))
          .map((attachment) => cleanupStagedAttachment(attachment).catch(() => undefined))
      );
    };
  }, [
    activeThreadRootMessageId,
    chat.chatId,
    chat.pinnedMessageId,
    currentUserId,
    setChatMessages,
    token,
    topicId
  ]);

  useEffect(() => {
    if (chat.chatType !== "DIRECT" || !chat.peerIsBot || !chat.peerUserId) {
      setBotCommands([]);
      return;
    }

    let cancelled = false;
    api.getBotCommands(token, chat.peerUserId)
      .then((commands) => {
        if (!cancelled) {
          setBotCommands(commands);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setBotCommands([]);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [chat.chatType, chat.peerIsBot, chat.peerUserId, token]);

  useEffect(() => {
    if (!activeInlineQuery) {
      setInlineBotResults([]);
      setLoadingInlineBotResults(false);
      return;
    }

    let cancelled = false;
    const timeoutId = setTimeout(() => {
      setLoadingInlineBotResults(true);
      api.getInlineBotResults(token, activeInlineQuery.botUsername, activeInlineQuery.query)
        .then((results) => {
          if (!cancelled) {
            setInlineBotResults(results);
          }
        })
        .catch(() => {
          if (!cancelled) {
            setInlineBotResults([]);
          }
        })
        .finally(() => {
          if (!cancelled) {
            setLoadingInlineBotResults(false);
          }
        });
    }, 250);

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [activeInlineQuery, token]);

  useEffect(() => {
    return () => {
      const cleanup = async () => {
        try {
          if (recordingRef.current) {
            const status = await recordingRef.current.getStatusAsync();
            if ("isRecording" in status && status.isRecording) {
              await recordingRef.current.stopAndUnloadAsync();
            }
          }
        } catch {
        } finally {
          recordingRef.current = null;
          setRecordingVoice(false);
          setRecordingDurationMs(0);
        }

        try {
          if (soundRef.current) {
            await soundRef.current.unloadAsync();
          }
        } catch {
        } finally {
          soundRef.current = null;
          setPlayingVoiceAttachmentId(null);
        }
      };

      void cleanup();
    };
  }, []);

  useEffect(() => {
    if (!latestMessageId || searchQuery.trim().length >= 2) {
      return;
    }
    listRef.current?.scrollToEnd({ animated: true });
  }, [latestMessageId, searchQuery]);

  useEffect(() => {
    let cancelled = false;
    const normalized = searchQuery.trim();
    if (normalized.length < 2) {
      setSearchResults([]);
      setSearching(false);
      return;
    }
    const timeoutId = setTimeout(() => {
      setSearching(true);
      api.searchMessages(token, chat.chatId, normalized, 20, topicId, activeThreadRootMessageId)
        .then((response) => {
          if (!cancelled) {
            setSearchResults(response.messages);
          }
        })
        .catch((searchError) => {
          if (!cancelled) {
            setError(searchError instanceof Error ? searchError.message : "Unable to search messages");
          }
        })
        .finally(() => {
          if (!cancelled) {
            setSearching(false);
          }
        });
    }, 250);
    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [activeThreadRootMessageId, chat.chatId, searchQuery, token, topicId]);

  const myMembership = useMemo(
    () => members.find((member) => member.userId === currentUserId) ?? null,
    [members, currentUserId]
  );

  const memberRestricted = Boolean(myMembership && !myMembership.canSendMessages);
  const channelPostingDisabled =
    chat.chatType === "CHANNEL" && !Boolean(myMembership?.canPostMessages);
  const slowModeEndsAt = useMemo(() => {
    if (
      !chat.slowModeSeconds ||
      chat.chatType === "DIRECT" ||
      chat.chatType === "SAVED" ||
      !myMembership?.lastSentMessageAt ||
      myMembership.role === "OWNER" ||
      myMembership.role === "ADMIN"
    ) {
      return null;
    }
    const nextAllowedAt =
      new Date(myMembership.lastSentMessageAt).getTime() + chat.slowModeSeconds * 1000;
    return nextAllowedAt > currentTimeMs ? nextAllowedAt : null;
  }, [
    chat.chatType,
    chat.slowModeSeconds,
    currentTimeMs,
    myMembership?.lastSentMessageAt,
    myMembership?.role
  ]);
  const restrictionLabel = memberRestricted
    ? myMembership?.restrictedUntil
      ? `Posting restricted until ${new Date(myMembership.restrictedUntil).toLocaleString()}`
      : "Posting restricted by an admin"
    : null;
  const slowModeLabel = slowModeEndsAt
    ? `Slow mode active. You can send again in ${formatCooldown(
        slowModeEndsAt - currentTimeMs
      )}.`
    : null;

  const canPost =
    !topicClosed &&
    !memberRestricted &&
    !channelPostingDisabled &&
    !slowModeEndsAt;
  const canPinMessages =
    chat.chatType === "DIRECT" || Boolean(myMembership?.canPinMessages);
  const reactionsEnabled = chat.reactionsEnabled !== false;
  const myAnonymousAdmin =
    (chat.chatType === "GROUP" || chat.chatType === "CHANNEL") &&
    Boolean(myMembership?.anonymousAdmin);
  const optimisticAuthor = useMemo(
    () => ({
      displaySenderName: myAnonymousAdmin ? chat.title : null,
      displaySenderPhotoUrl: myAnonymousAdmin ? chat.photoUrl : null,
      displaySenderPhotoAccessExpiresAt: myAnonymousAdmin ? chat.photoAccessExpiresAt : null,
      anonymousSender: myAnonymousAdmin
    }),
    [chat.photoAccessExpiresAt, chat.photoUrl, chat.title, myAnonymousAdmin]
  );

  const displayedMessages = useMemo(
    () => (searchQuery.trim().length >= 2 ? searchResults : messages),
    [messages, searchQuery, searchResults]
  );

  const normalizedComposerSelection = useMemo(() => {
    const start = Math.max(0, Math.min(composerSelection.start, composerSelection.end));
    const end = Math.min(draft.length, Math.max(composerSelection.start, composerSelection.end));
    return { start, end };
  }, [composerSelection, draft.length]);

  const canFormatSelection = normalizedComposerSelection.end > normalizedComposerSelection.start;

  const normalizedComposerDraft = useMemo(
    () => trimFormattedMessage(draft, draftEntities),
    [draft, draftEntities]
  );

  useEffect(() => {
    if (!slowModeEndsAt) {
      return;
    }
    const intervalId = setInterval(() => {
      setCurrentTimeMs(Date.now());
    }, 1000);
    return () => {
      clearInterval(intervalId);
    };
  }, [slowModeEndsAt]);

  const parsedLocation = useMemo(() => {
    const latitude = Number.parseFloat(locationLatitude);
    const longitude = Number.parseFloat(locationLongitude);
    if (Number.isNaN(latitude) || Number.isNaN(longitude)) {
      return null;
    }
    return {
      latitude,
      longitude,
      title: locationTitle.trim() || null,
      address: locationAddress.trim() || null
    } satisfies MessageLocation;
  }, [locationAddress, locationLatitude, locationLongitude, locationTitle]);

  const preparedContactCard = useMemo(() => {
    const firstName = contactFirstName.trim();
    const lastName = contactLastName.trim();
    const phoneNumber = contactPhoneNumber.trim();
    const userId = contactUserId.trim();
    if (!firstName && !phoneNumber && !lastName && !userId) {
      return null;
    }
    return {
      firstName: firstName || null,
      lastName: lastName || null,
      phoneNumber: phoneNumber || null,
      userId: userId || null,
      vcard: null
    } satisfies MessageContactCard;
  }, [contactFirstName, contactLastName, contactPhoneNumber, contactUserId]);

  const canSendLocation = Boolean(
    parsedLocation &&
      parsedLocation.latitude >= -90 &&
      parsedLocation.latitude <= 90 &&
      parsedLocation.longitude >= -180 &&
      parsedLocation.longitude <= 180
  );

  const canSendContact = Boolean(
    preparedContactCard &&
      (preparedContactCard.firstName || preparedContactCard.phoneNumber)
  );

  const hasComposerContent =
    normalizedComposerDraft.text.length > 0 ||
    pendingAttachments.length > 0 ||
    canSendLocation ||
    canSendContact;

  useEffect(() => {
    if (!canPost || editingMessageId) {
      return;
    }

    const normalizedDraft = draft.trim();
    if (normalizedDraft === persistedDraftRef.current) {
      return;
    }

    let cancelled = false;
    const timeoutId = setTimeout(() => {
      const syncRequest = normalizedDraft
        ? api.saveDraft(token, chat.chatId, normalizedDraft)
        : api.clearDraft(token, chat.chatId);

      syncRequest
        .then((summary) => {
          if (cancelled) {
            return;
          }
          persistedDraftRef.current = summary.draftText?.trim() ?? "";
          upsertChat(summary);
        })
        .catch((draftError) => {
          if (!cancelled) {
            setError(draftError instanceof Error ? draftError.message : "Unable to sync draft");
          }
        });
    }, 450);

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [canPost, chat.chatId, draft, editingMessageId, token, upsertChat]);

  useEffect(() => {
    const latest = messages[messages.length - 1];
    if (!latest || latest.senderId === currentUserId) {
      return;
    }
    void api.markRead(token, chat.chatId, latest.messageId)
      .then(async (event) => {
        handleReadEvent(event);
        await onRefreshChats?.();
      })
      .catch(() => undefined);
  }, [messages, currentUserId, token, chat.chatId, onRefreshChats]);

  useEffect(() => {
    if (!canPost) {
      return;
    }
    if (!draft.trim()) {
      if (isTypingRef.current) {
        void api.sendTyping(token, chat.chatId, false).catch(() => undefined);
        isTypingRef.current = false;
      }
      if (typingResetRef.current) {
        clearTimeout(typingResetRef.current);
      }
      return;
    }
    if (!isTypingRef.current) {
      isTypingRef.current = true;
      void api.sendTyping(token, chat.chatId, true).catch(() => undefined);
    }
    if (typingResetRef.current) {
      clearTimeout(typingResetRef.current);
    }
    typingResetRef.current = setTimeout(() => {
      void api.sendTyping(token, chat.chatId, false).catch(() => undefined);
      isTypingRef.current = false;
    }, 1200);
  }, [draft, token, chat.chatId, canPost]);

  useEffect(() => {
    if (!recordingVoice || !recordingRef.current) {
      return;
    }

    const intervalId = setInterval(() => {
      void recordingRef.current?.getStatusAsync().then((status) => {
        if ("isRecording" in status && status.isRecording && typeof status.durationMillis === "number") {
          setRecordingDurationMs(status.durationMillis);
        }
        if ("metering" in status && typeof status.metering === "number") {
          const samples = recordingWaveformSamplesRef.current;
          samples.push(meteringToWaveformSample(status.metering));
          if (samples.length > 240) {
            samples.splice(0, samples.length - 240);
          }
        }
      }).catch(() => undefined);
    }, 250);

    return () => {
      clearInterval(intervalId);
    };
  }, [recordingVoice]);

  const typingLabel = useMemo(() => {
    const names = typingUserIds
      .map((userId) => members.find((member) => member.userId === userId)?.displayName)
      .filter(Boolean);
    return names.length > 0 ? `${names.join(", ")} typing...` : null;
  }, [members, typingUserIds]);

  const selectedMessage = useMemo(
    () => messages.find((message) => message.messageId === selectedMessageId) ?? null,
    [messages, selectedMessageId]
  );

  const threadRootMessage = useMemo(
    () =>
      activeThreadRootMessageId
        ? chatMessages.find((message) => message.messageId === activeThreadRootMessageId) ?? null
        : null,
    [activeThreadRootMessageId, chatMessages]
  );

  const activeDiscussionRootMessageId =
    activeThreadRootMessageId != null
      ? threadRootMessage?.discussionRootMessageId ?? activeThreadRootMessageId
      : null;

  const activeDiscussionChatId =
    activeThreadRootMessageId != null
      ? threadRootMessage?.discussionChatId ?? chat.chatId
      : null;

  const replyTarget = useMemo(
    () => chatMessages.find((message) => message.messageId === replyToMessageId) ?? null,
    [chatMessages, replyToMessageId]
  );

  const pinnedMessage = useMemo(
    () => chatMessages.find((message) => message.messageId === pinnedMessageId) ?? null,
    [chatMessages, pinnedMessageId]
  );

  const activePinnedHistoryEntry = useMemo(
    () =>
      pinnedHistory.find((entry) => entry.active) ??
      pinnedHistory.find((entry) => entry.messageId === pinnedMessageId) ??
      null,
    [pinnedHistory, pinnedMessageId]
  );

  const pinnedPreviewMessage = pinnedMessage ?? activePinnedHistoryEntry?.message ?? null;

  function applyPinnedMessageId(nextPinnedMessageId: string | null) {
    setPinnedMessageId(nextPinnedMessageId);
    const currentChat = useAppStore
      .getState()
      .chats.find((candidate) => candidate.chatId === chat.chatId);
    upsertChat({
      ...(currentChat ?? chat),
      pinnedMessageId: nextPinnedMessageId
    });
  }

  function syncSearchResult(updatedMessage: ChatMessage) {
    setSearchResults((current) =>
      current.map((message) =>
        message.messageId === updatedMessage.messageId ? updatedMessage : message
      )
    );
  }

  function persistMessage(message: ChatMessage) {
    upsertMessage(message);
    syncSearchResult(message);
    void localDatabase.upsertMessages(currentUserId, [message]).catch(() => undefined);
  }

  function touchMyLastSentAt(sentAt: string) {
    setMembers((current) =>
      current.map((member) =>
        member.userId === currentUserId
          ? {
              ...member,
              lastSentMessageAt: sentAt
            }
          : member
      )
    );
    setCurrentTimeMs(Date.now());
  }

  function handleQueuedMessageSynced(queuedMessageId: string, message: ChatMessage) {
    replaceMessage(message.chatId, queuedMessageId, message);
    if (message.chatId === chat.chatId) {
      syncSearchResult(message);
      if (message.senderId === currentUserId) {
        touchMyLastSentAt(message.createdAt);
      }
    }
    void localDatabase.upsertMessages(currentUserId, [message]).catch(() => undefined);
  }

  function handleQueuedMessageDropped(queuedMessageId: string, queuedChatId: string) {
    removeMessage(queuedChatId, queuedMessageId);
    void localDatabase.removeMessage(currentUserId, queuedChatId, queuedMessageId).catch(() => undefined);
    setSearchResults((current) => current.filter((message) => message.messageId !== queuedMessageId));
  }

  function handleQueuedScheduledSynced(queuedScheduledMessageId: string, message: ScheduledMessage) {
    if (message.chatId === chat.chatId) {
      setScheduledMessages((current) => {
        const hasQueuedMessage = current.some(
          (item) => item.scheduledMessageId === queuedScheduledMessageId
        );
        return mergeScheduledMessages(
          hasQueuedMessage
            ? current.map((item) =>
                item.scheduledMessageId === queuedScheduledMessageId ? message : item
              )
            : [...current, message]
        );
      });
    }
    void localDatabase.upsertScheduledMessages(currentUserId, [message]).catch(() => undefined);
  }

  function handleQueuedScheduledDropped(queuedScheduledMessageId: string, queuedChatId: string) {
    if (queuedChatId === chat.chatId) {
      setScheduledMessages((current) =>
        current.filter((item) => item.scheduledMessageId !== queuedScheduledMessageId)
      );
    }
    void localDatabase.removeScheduledMessage(currentUserId, queuedChatId, queuedScheduledMessageId).catch(
      () => undefined
    );
  }

  function handleDraftChange(nextDraft: string) {
    setDraft(nextDraft);
    if (nextDraft !== draft && draftEntities.length > 0) {
      setDraftEntities([]);
    }
  }

  function handleInsertBotCommand(command: string) {
    if (!canPost || sending || uploadingAttachments || recordingVoice || editingMessageId) {
      return;
    }
    setDraft(command);
    setDraftEntities([]);
    setComposerSelection({ start: command.length, end: command.length });
    setShowStickerPicker(false);
    setShowPollComposer(false);
    setShowLocationComposer(false);
    setShowContactComposer(false);
  }

  function handleComposerSelectionChange(
    event: NativeSyntheticEvent<TextInputSelectionChangeEventData>
  ) {
    setComposerSelection(event.nativeEvent.selection);
  }

  function isFormattingActive(type: MessageTextEntity["type"]) {
    if (!canFormatSelection) {
      return false;
    }

    return draftEntities.some(
      (entity) =>
        entity.type === type &&
        entity.offset <= normalizedComposerSelection.start &&
        entity.offset + entity.length >= normalizedComposerSelection.end
    );
  }

  function handleToggleFormatting(type: MessageTextEntity["type"]) {
    if (!canFormatSelection) {
      return;
    }
    setDraftEntities((current) =>
      toggleMessageEntity(draft, current, type, normalizedComposerSelection)
    );
  }

  function resetComposerState() {
    setDraft("");
    setDraftEntities([]);
    setComposerSelection({ start: 0, end: 0 });
    setPendingAttachments([]);
    setSendSilently(false);
    resetPollComposer();
    resetLocationComposer();
    resetContactComposer();
    setShowStickerPicker(false);
    setEditingMessageId(null);
    setReplyToMessageId(null);
    setSelectedMessageId(null);
    setInlineBotResults([]);
  }

  async function handleOpenBotMiniApp() {
    if (!chat.peerUserId) {
      return;
    }
    onOpenBotMiniApp?.(chat.peerUserId, chat.peerDisplayName ?? chat.title, chat.chatId, null);
  }

  function clearTypingTimeout(userId: string) {
    const timeoutId = typingTimeoutsRef.current[userId];
    if (!timeoutId) {
      return;
    }
    clearTimeout(timeoutId);
    delete typingTimeoutsRef.current[userId];
  }

  async function removePendingAttachment(attachment: MessageAttachment) {
    if (isQueuedUploadAttachment(attachment)) {
      await cleanupStagedAttachment(attachment).catch(() => undefined);
    }
    setPendingAttachments((current) =>
      current.filter((item) => item.attachmentId !== attachment.attachmentId)
    );
  }

  function handleTypingEvent(event: TypingEvent) {
    if (event.userId === currentUserId) {
      return;
    }
    clearTypingTimeout(event.userId);
    setTypingUserIds((current) => {
      if (event.typing) {
        return current.includes(event.userId) ? current : [...current, event.userId];
      }
      return current.filter((userId) => userId !== event.userId);
    });

    if (!event.typing) {
      return;
    }

    typingTimeoutsRef.current[event.userId] = setTimeout(() => {
      delete typingTimeoutsRef.current[event.userId];
      setTypingUserIds((current) => current.filter((userId) => userId !== event.userId));
    }, 3000);
  }

  function handleReadEvent(event: ChatReadEvent) {
    applyReadEvent(event);
    setMembers((current) =>
      current.map((member) =>
        member.userId === event.userId ? { ...member, lastReadAt: event.readAt } : member
      )
    );
    void localDatabase
      .upsertMessages(currentUserId, useAppStore.getState().messagesByChat[event.chatId] ?? [])
      .catch(() => undefined);
    if (event.userId === currentUserId) {
      const updatedChat = useAppStore.getState().chats.find((item) => item.chatId === event.chatId);
      if (updatedChat) {
        void localDatabase.upsertChats(currentUserId, [updatedChat]).catch(() => undefined);
      }
    }
  }

  function handlePinEvent(event: PinMessageEvent) {
    applyPinnedMessageId(event.messageId);
    void refreshPinnedHistory();
  }

  function canClosePoll(message: ChatMessage) {
    if (!message.poll || message.poll.closed || message.deliveryStatus === "QUEUED") {
      return false;
    }
    if (message.senderId === currentUserId) {
      return true;
    }
    return chat.chatType !== "DIRECT" &&
      chat.chatType !== "SAVED" &&
      Boolean(myMembership?.canManageMessages);
  }

  async function handleLoadOlder() {
    const oldest = messages[0];
    if (!oldest || loadingOlder || !hasMoreHistory) {
      return;
    }
    setLoadingOlder(true);
    setError(null);
    try {
      const older = await api.getMessagesBefore(
        token,
        chat.chatId,
        oldest.createdAt,
        PAGE_SIZE,
        topicId,
        activeThreadRootMessageId
      );
      setChatMessages(chat.chatId, older);
      void localDatabase.upsertMessages(currentUserId, older).catch(() => undefined);
      setHasMoreHistory(older.length === PAGE_SIZE);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load older messages");
    } finally {
      setLoadingOlder(false);
    }
  }

  function resetPollComposer() {
    setShowPollComposer(false);
    setPollQuestion("");
    setPollOptions(["", ""]);
    setPollMultipleChoice(false);
  }

  function resetLocationComposer() {
    setShowLocationComposer(false);
    setLocationLatitude("");
    setLocationLongitude("");
    setLocationTitle("");
    setLocationAddress("");
  }

  function resetContactComposer() {
    setShowContactComposer(false);
    setContactFirstName("");
    setContactLastName("");
    setContactPhoneNumber("");
    setContactUserId("");
  }

  async function handleToggleStickerPicker() {
    const nextVisible = !showStickerPicker;
    if (nextVisible) {
      resetPollComposer();
      resetLocationComposer();
      resetContactComposer();
    }
    setShowStickerPicker(nextVisible);
    if (!nextVisible || stickerPacks.length > 0 || loadingStickerPacks) {
      return;
    }

    setLoadingStickerPacks(true);
    setError(null);
    try {
      const packs = await api.getStickerPacks(token);
      setStickerPacks(packs);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load stickers");
    } finally {
      setLoadingStickerPacks(false);
    }
  }

  function handleToggleLocationComposer() {
    const nextVisible = !showLocationComposer;
    if (nextVisible) {
      resetPollComposer();
      resetContactComposer();
      setShowStickerPicker(false);
    }
    setShowLocationComposer(nextVisible);
  }

  function handleToggleContactComposer() {
    const nextVisible = !showContactComposer;
    if (nextVisible) {
      resetPollComposer();
      resetLocationComposer();
      setShowStickerPicker(false);
    }
    setShowContactComposer(nextVisible);
  }

  function updatePollOption(index: number, value: string) {
    setPollOptions((current) =>
      current.map((option, currentIndex) =>
        currentIndex === index ? value : option
      )
    );
  }

  function addPollOption() {
    setPollOptions((current) =>
      current.length >= 10 ? current : [...current, ""]
    );
  }

  function removePollOption(index: number) {
    setPollOptions((current) =>
      current.length <= 2
        ? current
        : current.filter((_, currentIndex) => currentIndex !== index)
    );
  }

  async function handleCreatePoll() {
    const normalizedQuestion = pollQuestion.trim();
    const normalizedOptions = pollOptions
      .map((option) => option.trim())
      .filter(Boolean);

    if (!normalizedQuestion || normalizedOptions.length < 2 || sending || !canPost) {
      return;
    }

    setSending(true);
    setError(null);
    const clientMessageId = generateClientMessageId();
    const payload = {
      chatId: chat.chatId,
      topicId: topicId ?? undefined,
      replyToMessageId: effectiveReplyToMessageId ?? undefined,
      question: normalizedQuestion,
      options: normalizedOptions,
      multipleChoice: pollMultipleChoice,
      clientMessageId
    };
    try {
      const message = await api.createPollMessage(token, payload);
      persistMessage(message);
      touchMyLastSentAt(message.createdAt);
      resetPollComposer();
      setSelectedMessageId(null);
    } catch (pollError) {
      if (messageOutbox.isRetryable(pollError)) {
        try {
          const queuedMessage = await messageOutbox.queueMessage({
            chatId: chat.chatId,
            currentUserId,
            operation: {
              kind: "CREATE_POLL_MESSAGE",
              request: payload
            },
            attachments: [],
            optimistic: {
              ...optimisticAuthor,
              text: normalizedQuestion,
              replyToMessageId: effectiveReplyToMessageId ?? null,
              threadRootMessageId: activeThreadRootMessageId,
              discussionChatId: activeDiscussionChatId,
              discussionRootMessageId: activeDiscussionRootMessageId,
              poll: {
                pollId: `queued-poll:${clientMessageId}`,
                question: normalizedQuestion,
                multipleChoice: pollMultipleChoice,
                closed: false,
                totalVoters: 0,
                options: normalizedOptions.map((option, index) => ({
                  optionId: `queued-poll-option:${clientMessageId}:${index}`,
                  text: option,
                  voteCount: 0,
                  selectedByMe: false
                }))
              }
            }
          });
          upsertMessage(queuedMessage);
          syncSearchResult(queuedMessage);
          resetPollComposer();
          setSelectedMessageId(null);
          setError("No connection. Poll queued.");
        } catch (queueError) {
          setError(queueError instanceof Error ? queueError.message : "Unable to queue poll");
        }
      } else {
        setError(pollError instanceof Error ? pollError.message : "Unable to create poll");
      }
    } finally {
      setSending(false);
    }
  }

  async function handleSendSticker(stickerId: string) {
    if (sending || uploadingAttachments || !canPost || editingMessageId || recordingVoice) {
      return;
    }

    setSending(true);
    setError(null);
    const payload = {
      chatId: chat.chatId,
      clientMessageId: generateClientMessageId(),
      topicId: topicId ?? undefined,
      replyToMessageId: effectiveReplyToMessageId ?? undefined,
      silent: sendSilently || undefined,
      stickerId
    };
    const sticker =
      stickerPacks
        .flatMap((pack) => pack.stickers)
        .find((item) => item.stickerId === stickerId) ?? null;
    try {
      const message = await api.sendMessage(token, payload);
      persistMessage(message);
      touchMyLastSentAt(message.createdAt);
      setReplyToMessageId(null);
      setSelectedMessageId(null);
      setShowStickerPicker(false);
    } catch (sendError) {
      if (messageOutbox.isRetryable(sendError)) {
        try {
          const queuedMessage = await messageOutbox.queueMessage({
            chatId: chat.chatId,
            currentUserId,
            operation: {
              kind: "SEND_MESSAGE",
              request: payload
            },
            attachments: [],
            optimistic: {
              ...optimisticAuthor,
              silent: sendSilently,
              replyToMessageId: effectiveReplyToMessageId ?? null,
              threadRootMessageId: activeThreadRootMessageId,
              discussionChatId: activeDiscussionChatId,
              discussionRootMessageId: activeDiscussionRootMessageId,
              sticker
            }
          });
          upsertMessage(queuedMessage);
          syncSearchResult(queuedMessage);
          setReplyToMessageId(null);
          setSelectedMessageId(null);
          setShowStickerPicker(false);
          setError("No connection. Sticker queued.");
        } catch (queueError) {
          setError(queueError instanceof Error ? queueError.message : "Unable to queue sticker");
        }
      } else {
        setError(sendError instanceof Error ? sendError.message : "Unable to send sticker");
      }
    } finally {
      setSending(false);
    }
  }

  async function handleSendInlineResult(result: InlineBotResult) {
    if (
      sending ||
      uploadingAttachments ||
      !canPost ||
      showPollComposer ||
      editingMessageId ||
      recordingVoice
    ) {
      return;
    }

    setSending(true);
    setError(null);
    const clientMessageId = generateClientMessageId();
    const payload = {
      botUsername: result.botUsername,
      chatId: chat.chatId,
      clientMessageId,
      query: activeInlineQuery?.query || "",
      replyToMessageId: effectiveReplyToMessageId ?? undefined,
      resultId: result.resultId,
      topicId: topicId ?? undefined
    };

    try {
      const message = await api.sendInlineBotResult(token, payload);
      persistMessage(message);
      touchMyLastSentAt(message.createdAt);
      resetComposerState();
    } catch (sendError) {
      if (messageOutbox.isRetryable(sendError)) {
        try {
          const queuedMessage = await messageOutbox.queueMessage({
            chatId: chat.chatId,
            currentUserId,
            operation: {
              kind: "SEND_INLINE_BOT_RESULT",
              request: payload
            },
            attachments: [],
            optimistic: {
              ...optimisticAuthor,
              text: result.text,
              replyToMessageId: effectiveReplyToMessageId ?? null,
              viaBotUserId: result.botUserId,
              threadRootMessageId: activeThreadRootMessageId,
              discussionChatId: activeDiscussionChatId,
              discussionRootMessageId: activeDiscussionRootMessageId
            }
          });
          upsertMessage(queuedMessage);
          syncSearchResult(queuedMessage);
          resetComposerState();
          setError("No connection. Inline result queued.");
        } catch (queueError) {
          setError(
            queueError instanceof Error ? queueError.message : "Unable to queue inline result"
          );
        }
      } else {
        setError(sendError instanceof Error ? sendError.message : "Unable to send inline result");
      }
    } finally {
      setSending(false);
    }
  }

  async function handleSend() {
    const { text, entities } = normalizedComposerDraft;
    const caption = pendingAttachments.length > 0 || activeStructuredMessageType ? text || undefined : undefined;
    if (
      (!text && pendingAttachments.length === 0 && !canSendLocation && !canSendContact) ||
      sending ||
      uploadingAttachments ||
      !canPost ||
      showPollComposer
    ) {
      return;
    }
    if (activeStructuredMessageType === "LOCATION" && !canSendLocation) {
      setError("Enter valid latitude and longitude");
      return;
    }
    if (activeStructuredMessageType === "CONTACT_CARD" && !canSendContact) {
      setError("Enter at least a contact name or phone number");
      return;
    }
    setSending(true);
    setError(null);
    let resolvedAttachments = pendingAttachments;
    try {
      resolvedAttachments = await resolvePendingAttachmentsForSend(pendingAttachments);
    } catch (resolveError) {
      if (resolveError instanceof PendingAttachmentUploadError) {
        if (!messageOutbox.isRetryable(resolveError.cause)) {
          setSending(false);
          setError(
            resolveError.cause instanceof Error
              ? resolveError.cause.message
              : "Unable to upload attachment"
          );
          return;
        }
        resolvedAttachments = resolveError.attachments;
      } else {
        setSending(false);
        setError(resolveError instanceof Error ? resolveError.message : "Unable to prepare attachments");
        return;
      }
    }

    const payload = {
      chatId: chat.chatId,
      attachmentIds: resolvedAttachments
        .filter((attachment) => !isQueuedUploadAttachment(attachment))
        .map((item) => item.attachmentId),
      clientMessageId: editingMessageId ? undefined : generateClientMessageId(),
      entities: entities.length > 0 ? entities : undefined,
      topicId: topicId ?? undefined,
      replyToMessageId: effectiveReplyToMessageId ?? undefined,
      text: text || undefined,
      caption,
      silent: sendSilently || undefined,
      messageType: activeStructuredMessageType ?? undefined,
      location: activeStructuredMessageType === "LOCATION" ? parsedLocation ?? undefined : undefined,
      contactCard:
        activeStructuredMessageType === "CONTACT_CARD"
          ? preparedContactCard ?? undefined
          : undefined
    };
    try {
      const message = editingMessageId
        ? await api.editMessage(token, editingMessageId, {
            text,
            entities
          })
        : await api.sendMessage(token, payload);
      persistMessage(message);
      if (!editingMessageId) {
        touchMyLastSentAt(message.createdAt);
      }
      resetComposerState();
    } catch (sendError) {
      if (!editingMessageId && messageOutbox.isRetryable(sendError)) {
        try {
          const queuedMessage = await messageOutbox.queueMessage({
            chatId: chat.chatId,
            currentUserId,
            operation: {
              kind: "SEND_MESSAGE",
              request: payload
            },
            attachments: resolvedAttachments,
            optimistic: {
              ...optimisticAuthor,
              text,
              entities,
              messageType: activeStructuredMessageType ?? (resolvedAttachments.length > 0 ? (resolvedAttachments.length > 1 ? "ALBUM" : resolvedAttachments[0].kind) : "TEXT"),
              caption: caption ?? null,
              silent: sendSilently,
              location:
                activeStructuredMessageType === "LOCATION" ? parsedLocation ?? null : null,
              contactCard:
                activeStructuredMessageType === "CONTACT_CARD"
                  ? preparedContactCard ?? null
                  : null,
              replyToMessageId: effectiveReplyToMessageId ?? null,
              threadRootMessageId: activeThreadRootMessageId,
              discussionChatId: activeDiscussionChatId,
              discussionRootMessageId: activeDiscussionRootMessageId,
              attachments: resolvedAttachments
            }
          });
          upsertMessage(queuedMessage);
          syncSearchResult(queuedMessage);
          resetComposerState();
          setError("No connection. Message queued.");
        } catch (queueError) {
          setError(queueError instanceof Error ? queueError.message : "Unable to queue message");
        }
      } else {
        setError(sendError instanceof Error ? sendError.message : "Unable to send message");
      }
    } finally {
      setSending(false);
    }
  }

  async function handleScheduleMessage() {
    const { text, entities } = normalizedComposerDraft;
    const caption = pendingAttachments.length > 0 || activeStructuredMessageType ? text || undefined : undefined;
    if (
      (!text && pendingAttachments.length === 0 && !canSendLocation && !canSendContact) ||
      scheduling ||
      sending ||
      uploadingAttachments ||
      !canPost ||
      showPollComposer ||
      editingMessageId ||
      recordingVoice
    ) {
      return;
    }
    if (activeStructuredMessageType === "LOCATION" && !canSendLocation) {
      setError("Enter valid latitude and longitude");
      return;
    }
    if (activeStructuredMessageType === "CONTACT_CARD" && !canSendContact) {
      setError("Enter at least a contact name or phone number");
      return;
    }
    if (pendingAttachments.some((attachment) => isQueuedUploadAttachment(attachment))) {
      setError("Scheduled messages with offline attachments are not supported yet");
      return;
    }

    setScheduling(true);
    setError(null);
    const clientMessageId = generateClientMessageId();
    const scheduledAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();
    const payload = {
      chatId: chat.chatId,
      attachmentIds: pendingAttachments.map((item) => item.attachmentId),
      clientMessageId,
      entities: entities.length > 0 ? entities : undefined,
      topicId: topicId ?? undefined,
      replyToMessageId: effectiveReplyToMessageId ?? undefined,
      stickerId: undefined,
      text: text || undefined,
      caption,
      silent: sendSilently || undefined,
      messageType: activeStructuredMessageType ?? undefined,
      location: activeStructuredMessageType === "LOCATION" ? parsedLocation ?? undefined : undefined,
      contactCard:
        activeStructuredMessageType === "CONTACT_CARD"
          ? preparedContactCard ?? undefined
          : undefined,
      scheduledAt
    };
    try {
      const scheduledMessage = await api.scheduleMessage(token, payload);
      setScheduledMessages((current) =>
        mergeScheduledMessages([...current, scheduledMessage])
      );
      void localDatabase.upsertScheduledMessages(currentUserId, [scheduledMessage]).catch(() => undefined);
      resetComposerState();
      setShowScheduledPanel(true);
    } catch (scheduleError) {
      if (scheduledMessageOutbox.isRetryable(scheduleError)) {
        try {
          const queuedMessage = await scheduledMessageOutbox.queueMessage({
            chatId: chat.chatId,
            currentUserId,
            payload,
            attachments: pendingAttachments,
            threadRootMessageId: activeThreadRootMessageId,
            discussionChatId: activeDiscussionChatId,
            discussionRootMessageId: activeDiscussionRootMessageId
          });
          setScheduledMessages((current) => mergeScheduledMessages([...current, queuedMessage]));
          resetComposerState();
          setShowScheduledPanel(true);
          setError("No connection. Scheduled message queued.");
        } catch (queueError) {
          setError(queueError instanceof Error ? queueError.message : "Unable to queue scheduled message");
        }
      } else {
        setError(scheduleError instanceof Error ? scheduleError.message : "Unable to schedule message");
      }
    } finally {
      setScheduling(false);
    }
  }

  async function handleSendWhenOnline() {
    const { text, entities } = normalizedComposerDraft;
    const caption = pendingAttachments.length > 0 || activeStructuredMessageType ? text || undefined : undefined;
    if (
      chat.chatType !== "DIRECT" ||
      (!text && pendingAttachments.length === 0 && !canSendLocation && !canSendContact) ||
      scheduling ||
      sending ||
      uploadingAttachments ||
      !canPost ||
      showPollComposer ||
      editingMessageId ||
      recordingVoice
    ) {
      return;
    }
    if (activeStructuredMessageType === "LOCATION" && !canSendLocation) {
      setError("Enter valid latitude and longitude");
      return;
    }
    if (activeStructuredMessageType === "CONTACT_CARD" && !canSendContact) {
      setError("Enter at least a contact name or phone number");
      return;
    }

    setScheduling(true);
    setError(null);
    let resolvedAttachments = pendingAttachments;
    try {
      resolvedAttachments = await resolvePendingAttachmentsForSend(pendingAttachments);
    } catch (resolveError) {
      if (resolveError instanceof PendingAttachmentUploadError) {
        if (!scheduledMessageOutbox.isRetryable(resolveError.cause)) {
          setScheduling(false);
          setError(
            resolveError.cause instanceof Error
              ? resolveError.cause.message
              : "Unable to upload attachment"
          );
          return;
        }
        resolvedAttachments = resolveError.attachments;
      } else {
        setScheduling(false);
        setError(resolveError instanceof Error ? resolveError.message : "Unable to prepare attachments");
        return;
      }
    }

    const clientMessageId = generateClientMessageId();
    const payload = {
      chatId: chat.chatId,
      attachmentIds: resolvedAttachments
        .filter((attachment) => !isQueuedUploadAttachment(attachment))
        .map((item) => item.attachmentId),
      clientMessageId,
      entities: entities.length > 0 ? entities : undefined,
      topicId: topicId ?? undefined,
      replyToMessageId: effectiveReplyToMessageId ?? undefined,
      text: text || undefined,
      caption,
      silent: sendSilently || undefined,
      messageType: activeStructuredMessageType ?? undefined,
      location: activeStructuredMessageType === "LOCATION" ? parsedLocation ?? undefined : undefined,
      contactCard:
        activeStructuredMessageType === "CONTACT_CARD"
          ? preparedContactCard ?? undefined
          : undefined
    };

    try {
      const deferredMessage = await api.sendWhenOnlineMessage(token, payload);
      setScheduledMessages((current) =>
        mergeScheduledMessages([...current, deferredMessage])
      );
      void localDatabase.upsertScheduledMessages(currentUserId, [deferredMessage]).catch(() => undefined);
      resetComposerState();
      setShowScheduledPanel(true);
    } catch (sendError) {
      if (scheduledMessageOutbox.isRetryable(sendError)) {
        try {
          const queuedMessage = await scheduledMessageOutbox.queueMessage({
            chatId: chat.chatId,
            currentUserId,
            payload,
            attachments: resolvedAttachments,
            threadRootMessageId: activeThreadRootMessageId,
            discussionChatId: activeDiscussionChatId,
            discussionRootMessageId: activeDiscussionRootMessageId,
            mode: "WHEN_ONLINE"
          });
          setScheduledMessages((current) => mergeScheduledMessages([...current, queuedMessage]));
          resetComposerState();
          setShowScheduledPanel(true);
          setError("No connection. Message will wait for the recipient after sync.");
        } catch (queueError) {
          setError(queueError instanceof Error ? queueError.message : "Unable to queue online delivery");
        }
      } else {
        setError(sendError instanceof Error ? sendError.message : "Unable to queue online delivery");
      }
    } finally {
      setScheduling(false);
    }
  }

  async function handleCancelScheduledMessage(scheduledMessageId: string) {
    if (cancelingScheduledMessageId) {
      return;
    }

    setCancelingScheduledMessageId(scheduledMessageId);
    setError(null);
    try {
      const queuedClientMessageId = fromQueuedScheduledMessageId(scheduledMessageId);
      if (queuedClientMessageId) {
        await scheduledMessageOutbox.removeQueuedMessage(currentUserId, chat.chatId, queuedClientMessageId);
      } else {
        await api.cancelScheduledMessage(token, scheduledMessageId);
      }
      setScheduledMessages((current) =>
        current.filter((message) => message.scheduledMessageId !== scheduledMessageId)
      );
      void localDatabase.removeScheduledMessage(currentUserId, chat.chatId, scheduledMessageId).catch(() => undefined);
    } catch (cancelError) {
      setError(cancelError instanceof Error ? cancelError.message : "Unable to cancel scheduled message");
    } finally {
      setCancelingScheduledMessageId(null);
    }
  }

  async function handleVotePoll(message: ChatMessage, optionId: string) {
    if (!message.poll || message.poll.closed || message.deliveryStatus === "QUEUED" || votingMessageId) {
      return;
    }

    const nextOptionIds = message.poll.multipleChoice
      ? message.poll.options
          .filter((option) =>
            option.optionId === optionId ? !option.selectedByMe : option.selectedByMe
          )
          .map((option) => option.optionId)
      : [optionId];

    setVotingMessageId(message.messageId);
    setError(null);
    try {
      const updated = await api.votePoll(token, message.messageId, nextOptionIds);
      persistMessage(updated);
    } catch (voteError) {
      setError(voteError instanceof Error ? voteError.message : "Unable to vote in poll");
    } finally {
      setVotingMessageId(null);
    }
  }

  async function handleClosePoll(message: ChatMessage) {
    if (!message.poll || message.poll.closed || message.deliveryStatus === "QUEUED" || closingPollMessageId) {
      return;
    }

    setClosingPollMessageId(message.messageId);
    setError(null);
    try {
      const updated = await api.closePoll(token, message.messageId);
      persistMessage(updated);
      setSelectedMessageId(null);
    } catch (closeError) {
      setError(closeError instanceof Error ? closeError.message : "Unable to close poll");
    } finally {
      setClosingPollMessageId(null);
    }
  }

  async function uploadOrStageAttachment(params: {
    uri: string;
    name: string;
    contentType?: string;
    kind: "FILE" | "VOICE" | "IMAGE" | "VIDEO" | "AUDIO" | "GIF" | "VIDEO_NOTE";
    durationMs?: number;
    width?: number | null;
    height?: number | null;
    waveform?: number[] | null;
  }) {
    try {
      return await api.uploadAttachment(token, chat.chatId, {
        uri: params.uri,
        name: params.name,
        type: params.contentType,
        kind: params.kind,
        durationMs: params.durationMs,
        width: params.width ?? undefined,
        height: params.height ?? undefined,
        waveform: params.waveform ?? undefined
      });
    } catch (uploadError) {
      if (!messageOutbox.isRetryable(uploadError)) {
        throw uploadError;
      }

        return stageAttachment({
          uri: params.uri,
          name: params.name,
          contentType: params.contentType,
          kind: params.kind,
          durationMs: params.durationMs,
          width: params.width ?? null,
          height: params.height ?? null,
          waveform: params.waveform ?? null
        });
      }
  }

  async function resolvePendingAttachmentsForSend(attachments: MessageAttachment[]) {
    const resolvedAttachments: MessageAttachment[] = [];

    for (let index = 0; index < attachments.length; index += 1) {
      const attachment = attachments[index];
      if (!isQueuedUploadAttachment(attachment)) {
        resolvedAttachments.push(attachment);
        continue;
      }

      try {
        const uploadedAttachment = await uploadPendingAttachment(token, chat.chatId, attachment);
        resolvedAttachments.push(uploadedAttachment);
      } catch (uploadError) {
        throw new PendingAttachmentUploadError(
          "Unable to upload queued attachment",
          [...resolvedAttachments, attachment, ...attachments.slice(index + 1)],
          uploadError
        );
      }
    }

    return resolvedAttachments;
  }

  async function handleStartVoiceRecording() {
    if (!canPost || editingMessageId || uploadingAttachments || recordingVoice) {
      return;
    }

    setError(null);
    try {
      const permission = await Audio.requestPermissionsAsync();
      if (!permission.granted) {
        setError("Microphone permission is required for voice messages");
        return;
      }

      await Audio.setAudioModeAsync({
        allowsRecordingIOS: true,
        playsInSilentModeIOS: true
      });

      const recording = new Audio.Recording();
        await recording.prepareToRecordAsync(Audio.RecordingOptionsPresets.HIGH_QUALITY);
        await recording.startAsync();
        recordingRef.current = recording;
        recordingWaveformSamplesRef.current = [];
        setRecordingVoice(true);
        setRecordingDurationMs(0);
    } catch (recordError) {
      setError(recordError instanceof Error ? recordError.message : "Unable to start recording");
      recordingRef.current = null;
      setRecordingVoice(false);
      setRecordingDurationMs(0);
    }
  }

  async function handleStopVoiceRecording(cancel = false) {
    const recording = recordingRef.current;
    if (!recording) {
      return;
    }

    setUploadingAttachments(!cancel);
    setError(null);

    try {
      let durationMs = recordingDurationMs;
      const statusBeforeStop = await recording.getStatusAsync();
      if ("durationMillis" in statusBeforeStop && typeof statusBeforeStop.durationMillis === "number") {
        durationMs = statusBeforeStop.durationMillis;
      }

      if ("isRecording" in statusBeforeStop && statusBeforeStop.isRecording) {
        await recording.stopAndUnloadAsync();
      }

      const finalStatus = await recording.getStatusAsync();
      if ("durationMillis" in finalStatus && typeof finalStatus.durationMillis === "number") {
        durationMs = finalStatus.durationMillis;
      }

      const uri = recording.getURI();
      if (!cancel && uri) {
        const waveform = compactWaveformSamples(recordingWaveformSamplesRef.current);
        const attachment = await uploadOrStageAttachment({
          uri,
          name: `voice-${Date.now()}.m4a`,
          contentType: "audio/mp4",
          kind: "VOICE",
          durationMs: Math.max(durationMs, 1),
          waveform
        });
        setPendingAttachments((current) => [...current, attachment]);
        if (isQueuedUploadAttachment(attachment)) {
          setError("No connection. Voice message queued for upload.");
        }
      }
    } catch (recordError) {
      if (!cancel) {
        setError(recordError instanceof Error ? recordError.message : "Unable to finalize recording");
      }
      } finally {
        recordingRef.current = null;
        recordingWaveformSamplesRef.current = [];
        setRecordingVoice(false);
        setRecordingDurationMs(0);
        setUploadingAttachments(false);
      try {
        await Audio.setAudioModeAsync({
          allowsRecordingIOS: false,
          playsInSilentModeIOS: true
        });
      } catch {
      }
    }
  }

  async function handleToggleVoicePlayback(attachment: MessageAttachment) {
    if (!isAudioAttachment(attachment)) {
      return;
    }

    setError(null);
    try {
      if (playingVoiceAttachmentId === attachment.attachmentId && soundRef.current) {
        await soundRef.current.unloadAsync();
        soundRef.current = null;
        setPlayingVoiceAttachmentId(null);
        return;
      }

      if (soundRef.current) {
        await soundRef.current.unloadAsync();
        soundRef.current = null;
      }

      const uri = isQueuedUploadAttachment(attachment)
        ? attachment.localUri ?? null
        : await attachmentTransfers.downloadAttachment(token, attachment);
      if (!uri) {
        return;
      }
      const { sound } = await Audio.Sound.createAsync(
        { uri },
        { shouldPlay: true }
      );
      soundRef.current = sound;
      setPlayingVoiceAttachmentId(attachment.attachmentId);
      sound.setOnPlaybackStatusUpdate((status) => {
        if ("isLoaded" in status && status.isLoaded && status.didJustFinish) {
          void sound.unloadAsync();
          if (soundRef.current === sound) {
            soundRef.current = null;
          }
          setPlayingVoiceAttachmentId(null);
        }
      });
    } catch (playbackError) {
      setError(playbackError instanceof Error ? playbackError.message : "Unable to play audio");
      setPlayingVoiceAttachmentId(null);
      if (soundRef.current) {
        try {
          await soundRef.current.unloadAsync();
        } catch {
        }
        soundRef.current = null;
      }
    }
  }

  async function handlePickDocuments(
    kind: "FILE" | "IMAGE" | "VIDEO" | "AUDIO" | "GIF",
    type: string
  ) {
    if (!canPost || uploadingAttachments || editingMessageId || recordingVoice) {
      return;
    }
    setError(null);
    const selection = await DocumentPicker.getDocumentAsync({
      multiple: true,
      copyToCacheDirectory: true,
      type
    });
    if (selection.canceled) {
      return;
    }
    setUploadingAttachments(true);
    try {
      const uploaded: MessageAttachment[] = [];
      let stagedCount = 0;
      for (const asset of selection.assets) {
        const contentType =
          asset.mimeType ??
          (
            kind === "IMAGE"
              ? "image/jpeg"
              : kind === "VIDEO"
                ? "video/mp4"
                : kind === "AUDIO"
                  ? "audio/mpeg"
                  : kind === "GIF"
                    ? "image/gif"
                    : undefined
          );
        const dimensions =
          kind === "IMAGE" || kind === "GIF"
            ? await loadImageDimensions(asset.uri)
            : null;
        const attachment = await uploadOrStageAttachment({
          uri: asset.uri,
          name:
            asset.name ??
            (
              kind === "IMAGE"
                ? "photo.jpg"
                : kind === "VIDEO"
                  ? "video.mp4"
                  : kind === "AUDIO"
                    ? "audio.m4a"
                    : kind === "GIF"
                      ? "animation.gif"
                      : "attachment"
            ),
          contentType,
          width: dimensions?.width ?? null,
          height: dimensions?.height ?? null,
          kind
        });
        if (isQueuedUploadAttachment(attachment)) {
          stagedCount += 1;
        }
        uploaded.push(attachment);
      }
      setPendingAttachments((current) => [...current, ...uploaded]);
      if (stagedCount > 0) {
        setError("No connection. Attachment upload queued until send.");
      }
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : "Unable to upload attachment");
    } finally {
      setUploadingAttachments(false);
    }
  }

  async function handlePickAttachments() {
    await handlePickDocuments("FILE", "*/*");
  }

  async function handlePickPhotos() {
    await handlePickDocuments("IMAGE", "image/*");
  }

  async function handlePickVideos() {
    await handlePickDocuments("VIDEO", "video/*");
  }

  async function handlePickAudioFiles() {
    await handlePickDocuments("AUDIO", "audio/*");
  }

  async function handlePickGifs() {
    await handlePickDocuments("GIF", "image/gif");
  }

  async function handleOpenAttachment(attachment: MessageAttachment) {
    const transfer = attachmentTransferStates[attachment.attachmentId];
    if (transfer?.direction === "DOWNLOAD" && transfer.status === "RUNNING") {
      setError(null);
      try {
        await attachmentTransfers.pauseDownload(attachment.attachmentId);
      } catch (downloadError) {
        setError(
          downloadError instanceof Error ? downloadError.message : "Unable to pause download"
        );
      }
      return;
    }

    setOpeningAttachmentId(attachment.attachmentId);
    setError(null);
    try {
      const uri = isQueuedUploadAttachment(attachment)
        ? attachment.localUri ?? null
        : await attachmentTransfers.downloadAttachment(token, attachment);
      if (!uri) {
        return;
      }
      if (await Sharing.isAvailableAsync()) {
        await Sharing.shareAsync(uri);
      } else {
        setError("Opening attachments is not available on this platform");
      }
    } catch (openError) {
      setError(openError instanceof Error ? openError.message : "Unable to open attachment");
    } finally {
      setOpeningAttachmentId(null);
    }
  }

  async function handleArchiveChat() {
    setError(null);
    try {
      await api.setChatArchived(token, chat.chatId, !chat.archived);
      await onRefreshChats?.();
      onBack();
    } catch (archiveError) {
      setError(archiveError instanceof Error ? archiveError.message : "Unable to update archive state");
    }
  }

  async function handleMuteChat() {
    setError(null);
    try {
      const isMuted = !!chat.mutedUntil && new Date(chat.mutedUntil).getTime() > Date.now();
      const mutedUntil = isMuted
        ? null
        : new Date(Date.now() + 60 * 60 * 1000).toISOString();
      upsertChat(await api.muteChat(token, chat.chatId, mutedUntil));
      await onRefreshChats?.();
    } catch (muteError) {
      setError(muteError instanceof Error ? muteError.message : "Unable to update mute state");
    }
  }

  async function handleDeleteSelected() {
    if (!selectedMessage || selectedMessage.senderId !== currentUserId || sending) {
      return;
    }
    setSending(true);
    setError(null);
    try {
      const message = await api.deleteMessage(token, selectedMessage.messageId);
      persistMessage(message);
      setDraft("");
      setEditingMessageId(null);
      setSelectedMessageId(null);
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "Unable to delete message");
    } finally {
      setSending(false);
    }
  }

  async function handleForwardSelected() {
    if (!selectedMessage || selectedMessage.deletedAt || sending || !canPost) {
      return;
    }
    if (selectedMessage.deliveryStatus === "QUEUED") {
      setError("Queued messages can be forwarded only after they are sent");
      return;
    }
    setSending(true);
    setError(null);
    const payload = {
      sourceMessageId: selectedMessage.messageId,
      chatId: chat.chatId,
      topicId: topicId ?? undefined,
      replyToMessageId: effectiveReplyToMessageId ?? undefined,
      clientMessageId: generateClientMessageId()
    };
    try {
      const message = await api.forwardMessage(token, payload);
      persistMessage(message);
      touchMyLastSentAt(message.createdAt);
      setSelectedMessageId(null);
    } catch (forwardError) {
      if (messageOutbox.isRetryable(forwardError)) {
        try {
          const queuedMessage = await messageOutbox.queueMessage({
            chatId: chat.chatId,
            currentUserId,
            operation: {
              kind: "FORWARD_MESSAGE",
              request: payload
            },
            attachments: [],
            optimistic: {
              ...optimisticAuthor,
              text: selectedMessage.text,
              entities: selectedMessage.entities,
              replyToMessageId: effectiveReplyToMessageId ?? null,
              threadRootMessageId: activeThreadRootMessageId,
              discussionChatId: activeDiscussionChatId,
              discussionRootMessageId: activeDiscussionRootMessageId,
              forwardedFromChatId: selectedMessage.chatId,
              forwardedFromMessageId: selectedMessage.messageId,
              poll: selectedMessage.poll,
              sticker: selectedMessage.sticker,
              attachments: selectedMessage.attachments
            }
          });
          upsertMessage(queuedMessage);
          syncSearchResult(queuedMessage);
          setSelectedMessageId(null);
          setError("No connection. Forward queued.");
        } catch (queueError) {
          setError(queueError instanceof Error ? queueError.message : "Unable to queue forward");
        }
      } else {
        setError(forwardError instanceof Error ? forwardError.message : "Unable to forward message");
      }
    } finally {
      setSending(false);
    }
  }

  async function handlePinSelected() {
    if (!selectedMessage || !canPinMessages) {
      return;
    }
    setError(null);
    try {
      handlePinEvent(await api.pinMessage(token, chat.chatId, selectedMessage.messageId));
      setSelectedMessageId(null);
    } catch (pinError) {
      setError(pinError instanceof Error ? pinError.message : "Unable to pin message");
    }
  }

  async function handleToggleReaction(emoji: string, message: ChatMessage | null = selectedMessage) {
    if (!message || message.deletedAt || reactingMessageId) {
      return;
    }
    if (!reactionsEnabled) {
      setError("Reactions are disabled for this chat");
      return;
    }
    setReactingMessageId(message.messageId);
    setError(null);
    try {
      const updated = await api.toggleReaction(token, message.messageId, emoji);
      persistMessage(updated);
    } catch (reactionError) {
      setError(reactionError instanceof Error ? reactionError.message : "Unable to toggle reaction");
    } finally {
      setReactingMessageId(null);
    }
  }

  function beginEditSelected() {
    if (!selectedMessage || selectedMessage.senderId !== currentUserId || selectedMessage.deletedAt) {
      return;
    }
    resetPollComposer();
    resetLocationComposer();
    resetContactComposer();
    setEditingMessageId(selectedMessage.messageId);
    setDraft(selectedMessage.text);
    setDraftEntities(selectedMessage.entities);
    setComposerSelection({
      start: selectedMessage.text.length,
      end: selectedMessage.text.length
    });
  }

  function beginReplySelected() {
    if (!selectedMessage) {
      return;
    }
    resetPollComposer();
    resetLocationComposer();
    resetContactComposer();
    setReplyToMessageId(selectedMessage.messageId);
    setEditingMessageId(null);
    setDraftEntities([]);
    setComposerSelection({ start: 0, end: 0 });
    setSelectedMessageId(null);
  }

  const effectiveReplyToMessageId = replyToMessageId ?? activeThreadRootMessageId ?? null;
  const activeStructuredMessageType: "LOCATION" | "CONTACT_CARD" | null = showLocationComposer
    ? "LOCATION"
    : showContactComposer
      ? "CONTACT_CARD"
      : null;

  function cancelComposerModes() {
    setEditingMessageId(null);
    setReplyToMessageId(null);
    setSelectedMessageId(null);
    setDraft("");
    setDraftEntities([]);
    setComposerSelection({ start: 0, end: 0 });
    resetPollComposer();
    resetLocationComposer();
    resetContactComposer();
  }

  function formatLocationSummary(location: MessageLocation | null) {
    if (!location) {
      return "Location";
    }
    const title = location.title?.trim();
    const address = location.address?.trim();
    if (title && address) {
      return `${title} - ${address}`;
    }
    if (title) {
      return title;
    }
    if (address) {
      return address;
    }
    return `${location.latitude.toFixed(5)}, ${location.longitude.toFixed(5)}`;
  }

  function formatContactName(contactCard: MessageContactCard | null) {
    if (!contactCard) {
      return "Contact";
    }
    const fullName = [contactCard.firstName, contactCard.lastName]
      .filter((part) => !!part)
      .join(" ")
      .trim();
    if (fullName) {
      return fullName;
    }
    return contactCard.phoneNumber ?? "Contact";
  }

  function describeMessage(message: ChatMessage | ScheduledMessage) {
    if (message.serviceMessage?.text) {
      return message.serviceMessage.text;
    }
    if (message.messageType === "LOCATION") {
      return formatLocationSummary(message.location);
    }
    if (message.messageType === "CONTACT_CARD") {
      return formatContactName(message.contactCard);
    }
    if (message.text) {
      return message.text;
    }
    if (message.attachments.length > 0) {
      return message.attachments.length > 1
        ? "Attachment album"
        : attachmentTitle(message.attachments[0]);
    }
    if ("stickerId" in message && message.stickerId) {
      return "Sticker";
    }
    return "Message";
  }

  function resolveDisplaySenderName(message: ChatMessage | null | undefined) {
    if (!message) {
      return null;
    }
    if (message.displaySenderName) {
      return message.displaySenderName;
    }
    if (message.senderId === currentUserId) {
      return "You";
    }
    return members.find((member) => member.userId === message.senderId)?.displayName ?? null;
  }

  function seenCount(message: ChatMessage) {
    if (message.senderId !== currentUserId) {
      return 0;
    }
    return members.filter(
      (member) =>
        member.userId !== currentUserId &&
        member.lastReadAt &&
        new Date(member.lastReadAt).getTime() >= new Date(message.createdAt).getTime()
    ).length;
  }

  function renderMessageMeta(message: ChatMessage) {
    const parts: string[] = [new Date(message.createdAt).toLocaleTimeString()];
    if (message.forwardedFromMessageId) {
      parts.push("forwarded");
    }
    if (message.silent) {
      parts.push("silent");
    }
    if (message.anonymousSender) {
      parts.push("anonymous admin");
    }
    if (message.senderId === currentUserId) {
      parts.push(message.deliveryStatus.toLowerCase());
    }
    if (message.editedAt) {
      parts.push("edited");
    }
    if (message.expiresAt && !message.deletedAt) {
      parts.push(`expires ${new Date(message.expiresAt).toLocaleTimeString()}`);
    }
    const readCount = seenCount(message);
    if (readCount > 0) {
      parts.push(`seen by ${readCount}`);
    }
    return parts.join(" - ");
  }

  function formatFileSize(fileSizeBytes: number) {
    if (fileSizeBytes < 1024) {
      return `${fileSizeBytes} B`;
    }
    if (fileSizeBytes < 1024 * 1024) {
      return `${(fileSizeBytes / 1024).toFixed(1)} KB`;
    }
    return `${(fileSizeBytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  function formatAutoDelete(seconds: number | null) {
    if (!seconds) {
      return null;
    }
    if (seconds < 60) {
      return `auto-delete ${seconds}s`;
    }
    if (seconds < 3600) {
      return `auto-delete ${Math.round(seconds / 60)}m`;
    }
    if (seconds < 86400) {
      return `auto-delete ${Math.round(seconds / 3600)}h`;
    }
    return `auto-delete ${Math.round(seconds / 86400)}d`;
  }

  const headerTitle = topic ? `${topic.iconEmoji ? `${topic.iconEmoji} ` : ""}${topic.title}` : chat.title;
  const resolvedHeaderTitle = activeThreadRootMessageId ? threadTitle ?? "Comments" : headerTitle;

  const headerSubtitle =
    activeThreadRootMessageId
      ? [
          chat.title,
          threadRootMessage?.commentCount ? `${threadRootMessage.commentCount} comments` : "comment thread",
          formatAutoDelete(chat.autoDeleteSeconds)
        ]
          .filter(Boolean)
          .join(" | ")
      : topic
      ? [
          chat.title,
          topic.closed ? "closed topic" : "open topic",
          formatAutoDelete(chat.autoDeleteSeconds)
        ]
          .filter(Boolean)
          .join(" · ")
      :
    chat.chatType === "SAVED"
      ? [
          "private notes",
          formatAutoDelete(chat.autoDeleteSeconds)
        ]
          .filter(Boolean)
          .join(" Р’В· ")
      : chat.chatType === "DIRECT"
      ? [
          chat.peerIsBot
            ? "bot"
            : formatPresenceStatus(
                { online: chat.peerOnline, lastSeenAt: chat.peerLastSeenAt },
                "status hidden"
              ),
          chat.peerBotSupportsInline ? "inline" : null,
          chat.publicUsername ? `@${chat.publicUsername}` : null,
          chat.about,
          chat.peerPhoneNumber ?? "phone-hidden",
          formatAutoDelete(chat.autoDeleteSeconds)
        ]
          .filter(Boolean)
          .join(" В· ")
      : [
          chat.publicUsername ? `@${chat.publicUsername}` : null,
          chat.about,
          `${members.length || chat.memberCount} members`,
          formatAutoDelete(chat.autoDeleteSeconds)
        ]
          .filter(Boolean)
          .join(" · ");

  return (
    <SafeAreaView style={styles.screen}>
      <KeyboardAvoidingView
        behavior={Platform.select({ ios: "padding", android: "height" })}
        keyboardVerticalOffset={Platform.OS === "ios" ? 8 : 0}
        style={styles.screen}
      >
        <View style={styles.header}>
          <Pressable onPress={onBack} style={styles.secondaryButton}><Text style={styles.secondaryButtonText}>Back</Text></Pressable>
          <Avatar uri={chat.photoUrl} title={chat.title} size={48} />
          <View style={styles.headerText}>
            <Text style={styles.title}>{resolvedHeaderTitle}</Text>
            <Text style={styles.subtitle}>{headerSubtitle}</Text>
            {typingLabel ? <Text style={styles.typingLabel}>{typingLabel}</Text> : null}
          </View>
          {chat.chatType !== "DIRECT" && chat.chatType !== "SAVED" && onOpenMembers ? (
            <Pressable onPress={onOpenMembers} style={styles.secondaryButton}><Text style={styles.secondaryButtonText}>Members</Text></Pressable>
          ) : null}
          {chat.chatType !== "SAVED" && onStartCall ? (
            <Pressable onPress={() => onStartCall("VOICE")} style={styles.secondaryButton}>
              <Text style={styles.secondaryButtonText}>Call</Text>
            </Pressable>
          ) : null}
          {chat.chatType !== "SAVED" && onStartCall ? (
            <Pressable onPress={() => onStartCall("VIDEO")} style={styles.secondaryButton}>
              <Text style={styles.secondaryButtonText}>Video</Text>
            </Pressable>
          ) : null}
          {chat.chatType === "DIRECT" && onOpenSecretChat ? (
            <Pressable onPress={onOpenSecretChat} style={styles.secondaryButton}>
              <Text style={styles.secondaryButtonText}>Secret</Text>
            </Pressable>
          ) : null}
          {chat.chatType === "DIRECT" && chat.peerIsBot && chat.peerBotWebAppUrl ? (
            <Pressable onPress={() => void handleOpenBotMiniApp()} style={styles.secondaryButton}>
              <Text style={styles.secondaryButtonText}>Mini App</Text>
            </Pressable>
          ) : null}
          <Pressable onPress={() => void handleMuteChat()} style={styles.secondaryButton}>
            <Text style={styles.secondaryButtonText}>
              {chat.mutedUntil && new Date(chat.mutedUntil).getTime() > Date.now()
                ? "Unmute"
                : "Mute 1h"}
            </Text>
          </Pressable>
          <Pressable onPress={() => void handleArchiveChat()} style={styles.secondaryButton}>
            <Text style={styles.secondaryButtonText}>{chat.archived ? "Unarchive" : "Archive"}</Text>
          </Pressable>
          <Pressable onPress={() => setShowScheduledPanel((current) => !current)} style={styles.secondaryButton}>
            <Text style={styles.secondaryButtonText}>Scheduled</Text>
          </Pressable>
          <Pressable onPress={() => setShowPinnedHistory((current) => !current)} style={styles.secondaryButton}>
            <Text style={styles.secondaryButtonText}>
              {showPinnedHistory ? "Hide pins" : `Pins${pinnedHistory.length > 0 ? ` (${pinnedHistory.length})` : ""}`}
            </Text>
          </Pressable>
        </View>

        {pinnedMessageId ? (
          <View style={styles.infoBar}>
            <Text style={styles.infoTitle}>Pinned</Text>
            <Text style={styles.infoText}>
              {loadingPinnedHistory && !pinnedPreviewMessage
                ? "Loading pinned message..."
                : pinnedPreviewMessage?.deletedAt
                  ? "Message deleted"
                  : pinnedPreviewMessage
                    ? describeMessage(pinnedPreviewMessage)
                    : "Pinned message is outside the loaded window"}
            </Text>
            {activePinnedHistoryEntry ? (
              <Text style={styles.infoMetaText}>
                {activePinnedHistoryEntry.pinnedByDisplayName} pinned this on{" "}
                {new Date(activePinnedHistoryEntry.pinnedAt).toLocaleString()}
              </Text>
            ) : null}
          </View>
        ) : null}

        {showPinnedHistory ? (
          <View style={styles.selectionBar}>
            <Text style={styles.selectionTitle}>Pinned history</Text>
            <Text style={styles.selectionBody}>
              Recent pin actions for this chat. The active pin is marked separately.
            </Text>
            {loadingPinnedHistory ? (
              <ActivityIndicator color="#92400e" style={styles.loader} />
            ) : pinnedHistory.length === 0 ? (
              <Text style={styles.selectionBody}>No pinned messages yet.</Text>
            ) : (
              <View style={styles.scheduledList}>
                {pinnedHistory.map((entry) => (
                  <View
                    key={entry.pinEventId}
                    style={[
                      styles.scheduledCard,
                      entry.active && styles.activePinnedHistoryCard
                    ]}
                  >
                    <View style={styles.pinnedHistoryHeader}>
                      <Text style={styles.scheduledText}>
                        {entry.active ? "Current pin" : "Pinned"}
                      </Text>
                      <Text style={styles.scheduledMeta}>
                        {new Date(entry.pinnedAt).toLocaleString()}
                      </Text>
                    </View>
                    <Text style={styles.selectionBody}>
                      {entry.message?.deletedAt
                        ? "Message deleted"
                        : entry.message
                          ? describeMessage(entry.message)
                          : "Message preview unavailable"}
                    </Text>
                    <Text style={styles.scheduledMeta}>
                      by {entry.pinnedByDisplayName}
                      {entry.unpinnedAt
                        ? ` - replaced ${new Date(entry.unpinnedAt).toLocaleString()}`
                        : ""}
                    </Text>
                  </View>
                ))}
              </View>
            )}
          </View>
        ) : null}

        {topicClosed ? (
          <View style={styles.infoBar}>
            <Text style={styles.infoTitle}>Topic locked</Text>
            <Text style={styles.infoText}>
              This topic is closed. Reopen it from Topics to send new messages.
            </Text>
          </View>
        ) : null}

        {showScheduledPanel ? (
          <View style={styles.selectionBar}>
            <Text style={styles.selectionTitle}>
              {activeThreadRootMessageId
                ? threadTitle ? `Scheduled in ${threadTitle}` : "Scheduled comments"
                : topic ? `Scheduled in ${topic.title}` : "Scheduled messages"}
            </Text>
            <Text style={styles.selectionBody}>
              Messages below will be delivered automatically at the scheduled time. Direct chats can also hold messages until the recipient comes online.
            </Text>
            <View style={styles.scheduledList}>
              {scheduledMessages.length === 0 ? (
                <Text style={styles.selectionBody}>No pending scheduled messages.</Text>
              ) : (
                scheduledMessages.map((message) => (
                  <View key={message.scheduledMessageId} style={styles.scheduledCard}>
                    {message.text ? (
                      <FormattedMessageText
                        entities={message.entities}
                        style={styles.scheduledText}
                        text={message.text}
                      />
                    ) : (
                      <Text style={styles.scheduledText}>{describeMessage(message)}</Text>
                    )}
                    <Text style={styles.scheduledMeta}>
                      {new Date(message.scheduledAt).toLocaleString()}
                      {message.status === "WAITING_ONLINE" ? " · when recipient is online" : ""}
                      {message.silent ? " · silent" : ""}
                      {message.status === "QUEUED" ? " · queued offline" : ""}
                    </Text>
                    <Pressable
                      disabled={cancelingScheduledMessageId === message.scheduledMessageId}
                      onPress={() => void handleCancelScheduledMessage(message.scheduledMessageId)}
                      style={[styles.inlineDangerButton, cancelingScheduledMessageId === message.scheduledMessageId && styles.disabled]}
                    >
                      <Text style={styles.inlineDangerText}>
                        {cancelingScheduledMessageId === message.scheduledMessageId ? "Canceling..." : "Cancel"}
                      </Text>
                    </Pressable>
                  </View>
                ))
              )}
            </View>
          </View>
        ) : null}

        {selectedMessage ? (
          <View style={styles.selectionBar}>
            <Text style={styles.selectionTitle}>Selected message</Text>
            <View style={styles.rowWrap}>
              <Pressable onPress={beginReplySelected} style={styles.inlineButton}><Text style={styles.inlineButtonText}>Reply</Text></Pressable>
              <Pressable onPress={handleForwardSelected} style={styles.inlineButton}><Text style={styles.inlineButtonText}>Forward</Text></Pressable>
              {!selectedMessage.deletedAt && selectedMessage.deliveryStatus !== "QUEUED" && canPinMessages ? (
                <Pressable onPress={handlePinSelected} style={styles.inlineButton}><Text style={styles.inlineButtonText}>Pin</Text></Pressable>
              ) : null}
              {selectedMessage.senderId === currentUserId && !selectedMessage.deletedAt ? (
                <Pressable onPress={beginEditSelected} style={styles.inlineButton}><Text style={styles.inlineButtonText}>Edit</Text></Pressable>
              ) : null}
              {selectedMessage.senderId === currentUserId ? (
                <Pressable onPress={handleDeleteSelected} style={styles.inlineDangerButton}><Text style={styles.inlineDangerText}>Delete</Text></Pressable>
              ) : null}
              <Pressable onPress={() => setSelectedMessageId(null)} style={styles.inlineButton}><Text style={styles.inlineButtonText}>Close</Text></Pressable>
            </View>
            {!selectedMessage.deletedAt && reactionsEnabled ? (
              <View style={styles.rowWrap}>
                {REACTION_CHOICES.map((emoji) => (
                  <Pressable key={emoji} onPress={() => void handleToggleReaction(emoji, selectedMessage)} style={styles.inlineButton}>
                    <Text style={styles.reactionText}>{emoji}</Text>
                  </Pressable>
                ))}
              </View>
            ) : null}
          </View>
        ) : null}

        {(editingMessageId || replyToMessageId || activeThreadRootMessageId) ? (
          <View style={styles.selectionBar}>
            <Text style={styles.selectionTitle}>
              {editingMessageId ? "Editing message" : activeThreadRootMessageId ? "Comment thread" : "Replying"}
            </Text>
            {editingMessageId ? (
              <Text style={styles.selectionBody}>Update the selected message</Text>
            ) : activeThreadRootMessageId && !replyToMessageId ? (
              threadRootMessage?.text ? (
                <FormattedMessageText
                  entities={threadRootMessage.entities}
                  style={styles.selectionBody}
                  text={threadRootMessage.text}
                  variant="muted"
                />
              ) : (
                <Text style={styles.selectionBody}>Send a comment to this post.</Text>
              )
            ) : replyTarget ? (
              replyTarget.deletedAt ? (
                <Text style={styles.selectionBody}>Message deleted</Text>
              ) : replyTarget.text ? (
                <FormattedMessageText
                  entities={replyTarget.entities}
                  style={styles.selectionBody}
                  text={replyTarget.text}
                  variant="muted"
                />
              ) : (
                <Text style={styles.selectionBody}>
                  {describeMessage(replyTarget)}
                </Text>
              )
            ) : (
              <Text style={styles.selectionBody}>Reply target is outside the loaded window</Text>
            )}
            <View style={styles.rowWrap}>
              <Pressable onPress={cancelComposerModes} style={styles.inlineButton}><Text style={styles.inlineButtonText}>Cancel</Text></Pressable>
            </View>
          </View>
        ) : null}

        {channelPostingDisabled ? (
          <View style={styles.infoBar}>
            <Text style={styles.infoTitle}>Channel</Text>
            <Text style={styles.infoText}>Publishing is disabled for this member in the channel feed.</Text>
          </View>
        ) : null}

        {slowModeLabel ? (
          <View style={styles.infoBar}>
            <Text style={styles.infoTitle}>Slow mode</Text>
            <Text style={styles.infoText}>{slowModeLabel}</Text>
          </View>
        ) : null}

        {!reactionsEnabled ? (
          <View style={styles.infoBar}>
            <Text style={styles.infoTitle}>Reactions</Text>
            <Text style={styles.infoText}>Reactions are disabled by chat admins.</Text>
          </View>
        ) : null}

        <View style={styles.searchRow}>
          <TextInput autoCapitalize="none" onChangeText={setSearchQuery} placeholder="Search messages" style={styles.input} value={searchQuery} />
          {searchQuery.trim().length >= 2 ? (
            <Pressable onPress={() => setSearchQuery("")} style={styles.inlineButton}><Text style={styles.inlineButtonText}>Clear</Text></Pressable>
          ) : null}
        </View>

        {searchQuery.trim().length >= 2 ? (
          <View style={styles.searchInfoBar}>
            <Text style={styles.searchInfoText}>{searching ? "Searching..." : `${searchResults.length} result${searchResults.length === 1 ? "" : "s"} for "${searchQuery.trim()}"`}</Text>
          </View>
        ) : null}

        {chat.chatType === "DIRECT" && chat.peerIsBot && botCommands.length > 0 && !activeThreadRootMessageId ? (
          <View style={styles.botCommandBar}>
            <Text style={styles.selectionTitle}>Bot commands</Text>
            {chat.peerBotWebAppUrl ? (
              <View style={styles.rowWrap}>
                <Pressable onPress={() => void handleOpenBotMiniApp()} style={styles.inlineButton}>
                  <Text style={styles.inlineButtonText}>Open mini app</Text>
                </Pressable>
              </View>
            ) : null}
            <View style={styles.rowWrap}>
              {botCommands.map((command) => (
                <Pressable
                  key={command.command}
                  onPress={() => handleInsertBotCommand(command.command)}
                  style={styles.inlineButton}
                >
                  <Text style={styles.inlineButtonText}>{command.command}</Text>
                </Pressable>
              ))}
            </View>
          </View>
        ) : null}

        {activeInlineQuery ? (
          <View style={styles.inlineResultsBar}>
            <Text style={styles.inlineResultsTitle}>
              Inline results for @{activeInlineQuery.botUsername}
            </Text>
            {loadingInlineBotResults ? (
              <Text style={styles.inlineResultsMeta}>Loading inline results...</Text>
            ) : inlineBotResults.length === 0 ? (
              <Text style={styles.inlineResultsMeta}>No inline results.</Text>
            ) : (
              <View style={styles.inlineResultsList}>
                {inlineBotResults.map((result) => (
                  <Pressable
                    key={`${result.botUserId}:${result.resultId}`}
                    onPress={() => void handleSendInlineResult(result)}
                    style={styles.inlineResultCard}
                  >
                    <Text style={styles.inlineResultTitle}>{result.title}</Text>
                    <Text style={styles.inlineResultDescription}>{result.description}</Text>
                  </Pressable>
                ))}
              </View>
            )}
          </View>
        ) : null}

        {!loadingHistory && searchQuery.trim().length < 2 && hasMoreHistory ? (
          <Pressable disabled={loadingOlder} onPress={() => void handleLoadOlder()} style={[styles.loadOlderButton, loadingOlder && styles.disabled]}>
            <Text style={styles.loadOlderText}>{loadingOlder ? "Loading..." : "Load earlier messages"}</Text>
          </Pressable>
        ) : null}

        {loadingHistory ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}
        {error ? <Text style={styles.errorText}>{error}</Text> : null}
        {restrictionLabel ? (
          <View style={styles.restrictionBar}>
            <Text style={styles.restrictionBarTitle}>Read-only mode</Text>
            <Text style={styles.restrictionBarText}>{restrictionLabel}</Text>
            {myMembership?.restrictionReason ? (
              <Text style={styles.restrictionBarText}>{myMembership.restrictionReason}</Text>
            ) : null}
          </View>
        ) : null}

        <FlatList
          ref={listRef}
          contentContainerStyle={styles.messagesContent}
          data={displayedMessages}
          keyboardShouldPersistTaps="handled"
          keyExtractor={(item) => item.messageId}
          renderItem={({ item }) => {
            const isMine = item.senderId === currentUserId;
            const replyPreview = item.replyToMessageId
              ? chatMessages.find((message) => message.messageId === item.replyToMessageId) ?? null
              : null;
            const displaySenderName = resolveDisplaySenderName(item);
            const replyPreviewSenderName = resolveDisplaySenderName(replyPreview);
            const shouldShowSenderLabel =
              !isMine &&
              Boolean(displaySenderName) &&
              (chat.chatType === "GROUP" || activeThreadRootMessageId != null);

            return (
              <Pressable onLongPress={() => setSelectedMessageId(item.messageId)} style={[styles.messageBubble, isMine ? styles.ownBubble : styles.peerBubble, selectedMessageId === item.messageId && styles.selectedBubble, pinnedMessageId === item.messageId && styles.pinnedBubble]}>
                {shouldShowSenderLabel ? (
                  <Text style={styles.authorLabel}>
                    {displaySenderName}
                    {item.anonymousSender ? " · anonymous admin" : ""}
                  </Text>
                ) : null}
                {item.forwardedFromMessageId ? <Text style={[styles.badgeText, isMine && styles.ownMessageText]}>Forwarded</Text> : null}
                {item.viaBotUserId ? <Text style={[styles.badgeText, isMine && styles.ownMessageText]}>Via bot</Text> : null}
                {replyPreview ? (
                  <View style={styles.replyPreview}>
                    {replyPreviewSenderName ? (
                      <Text style={styles.replyPreviewAuthor}>
                        {replyPreviewSenderName}
                        {replyPreview.anonymousSender ? " · anonymous admin" : ""}
                      </Text>
                    ) : null}
                    {replyPreview.deletedAt ? (
                      <Text style={styles.replyPreviewText}>Message deleted</Text>
                    ) : replyPreview.text ? (
                      <FormattedMessageText
                        entities={replyPreview.entities}
                        style={styles.replyPreviewText}
                        text={replyPreview.text}
                        variant="muted"
                        numberOfLines={2}
                      />
                    ) : (
                      <Text style={styles.replyPreviewText}>{describeMessage(replyPreview)}</Text>
                    )}
                  </View>
                ) : null}
                {item.deletedAt ? (
                  <Text style={[styles.messageText, isMine && styles.ownMessageText]}>Message deleted</Text>
                ) : item.text && !item.poll ? (
                  <FormattedMessageText
                    entities={item.entities}
                    style={[styles.messageText, isMine && styles.ownMessageText]}
                    text={item.text}
                    variant={isMine ? "inverse" : "default"}
                  />
                ) : null}
                {!item.deletedAt && item.serviceMessage ? (
                  <View style={styles.serviceCard}>
                    <Text style={styles.serviceTitle}>
                      {item.serviceMessage.serviceType ?? "Service update"}
                    </Text>
                    {item.serviceMessage.text ? (
                      <Text style={styles.serviceText}>{item.serviceMessage.text}</Text>
                    ) : null}
                  </View>
                ) : null}
                {!item.deletedAt && item.poll ? (
                  <View style={styles.pollCard}>
                    <Text style={styles.pollQuestion}>{item.poll.question}</Text>
                    {item.poll.options.map((option) => {
                      const percent = item.poll && item.poll.totalVoters > 0
                        ? Math.round((option.voteCount * 100) / item.poll.totalVoters)
                        : 0;
                      return (
                        <Pressable
                          key={option.optionId}
                          disabled={
                            item.poll?.closed ||
                            item.deliveryStatus === "QUEUED" ||
                            votingMessageId === item.messageId
                          }
                          onPress={() => void handleVotePoll(item, option.optionId)}
                          style={[
                            styles.pollOption,
                            option.selectedByMe && styles.pollOptionSelected,
                            (
                              item.poll?.closed ||
                              item.deliveryStatus === "QUEUED" ||
                              votingMessageId === item.messageId
                            ) && styles.disabled
                          ]}
                        >
                          <Text style={styles.pollOptionText}>{option.text}</Text>
                          <Text style={styles.pollOptionMeta}>
                            {option.voteCount} votes · {percent}%{option.selectedByMe ? " · you" : ""}
                          </Text>
                        </Pressable>
                      );
                    })}
                    <Text style={styles.pollFooter}>
                      {item.poll.totalVoters} voters
                      {item.poll.multipleChoice ? " · multiple choice" : ""}
                      {item.poll.closed ? " · closed" : ""}
                    </Text>
                    {canClosePoll(item) ? (
                      <Pressable
                        disabled={closingPollMessageId === item.messageId}
                        onPress={() => void handleClosePoll(item)}
                        style={[styles.inlineDangerButton, closingPollMessageId === item.messageId && styles.disabled]}
                      >
                        <Text style={styles.inlineDangerText}>
                          {closingPollMessageId === item.messageId ? "Closing..." : "Close poll"}
                        </Text>
                      </Pressable>
                    ) : null}
                  </View>
                ) : null}
                {!item.deletedAt && item.location ? (
                  <View style={styles.structuredCard}>
                    <Text style={styles.structuredTitle}>
                      {item.location.title ?? "Location"}
                    </Text>
                    {item.location.address ? (
                      <Text style={styles.structuredBody}>{item.location.address}</Text>
                    ) : null}
                    <Text style={styles.structuredMeta}>
                      {item.location.latitude.toFixed(5)}, {item.location.longitude.toFixed(5)}
                    </Text>
                  </View>
                ) : null}
                {!item.deletedAt && item.contactCard ? (
                  <View style={styles.structuredCard}>
                    <Text style={styles.structuredTitle}>{formatContactName(item.contactCard)}</Text>
                    {item.contactCard.phoneNumber ? (
                      <Text style={styles.structuredBody}>{item.contactCard.phoneNumber}</Text>
                    ) : null}
                    {item.contactCard.userId ? (
                      <Text style={styles.structuredMeta}>User id: {item.contactCard.userId}</Text>
                    ) : null}
                  </View>
                ) : null}
                {!item.deletedAt && item.sticker ? (
                  <View
                    style={[
                      styles.stickerBubble,
                      {
                        backgroundColor: item.sticker.backgroundFrom,
                        borderColor: item.sticker.backgroundTo
                      }
                    ]}
                  >
                    <Text style={styles.stickerEmoji}>{item.sticker.emoji}</Text>
                    <Text style={[styles.stickerLabel, { color: item.sticker.textColor }]}>
                      {item.sticker.label}
                    </Text>
                    <Text style={[styles.stickerPackLabel, { color: item.sticker.textColor }]}>
                      {item.sticker.packTitle}
                    </Text>
                  </View>
                ) : null}
                {!item.deletedAt && item.attachments.length > 0 ? (
                  <View style={styles.attachmentsColumn}>
                    {item.attachments.map((attachment) => (
                      isAudioAttachment(attachment) ? (
                        <Pressable key={attachment.attachmentId} onPress={() => void handleToggleVoicePlayback(attachment)} style={styles.voiceCard}>
                          <Text style={styles.voiceTitle}>{attachmentTitle(attachment)}</Text>
                          {renderWaveform(attachment, "#166534")}
                          <Text style={styles.voiceMeta}>
                            {formatDuration(attachment.durationMs)} · {formatFileSize(attachment.fileSizeBytes)}
                          </Text>
                          <Text style={styles.voiceMeta}>
                            {getAttachmentTransferMeta(attachment) ??
                              (isQueuedUploadAttachment(attachment)
                                ? "Queued upload"
                                : playingVoiceAttachmentId === attachment.attachmentId ? "Stop" : "Play")}
                          </Text>
                        </Pressable>
                      ) : isImageAttachment(attachment) && attachment.previewUrl ? (
                        <Pressable key={attachment.attachmentId} onPress={() => void handleOpenAttachment(attachment)} style={styles.imageCard}>
                          <Image
                            source={{ uri: attachment.previewUrl }}
                            style={[styles.imageAttachment, { height: getImagePreviewHeight(attachment) }]}
                          />
                          <Text style={styles.attachmentMeta}>
                            {openingAttachmentId === attachment.attachmentId
                              ? "Opening..."
                              : `${attachmentTitle(attachment)} · ${formatFileSize(attachment.fileSizeBytes)}`}
                          </Text>
                          {attachment.width && attachment.height ? (
                            <Text style={styles.attachmentMeta}>
                              {attachment.width}x{attachment.height}
                            </Text>
                          ) : null}
                          {getAttachmentTransferMeta(attachment) ? (
                            <Text style={styles.attachmentMeta}>
                              {getAttachmentTransferMeta(attachment)}
                            </Text>
                          ) : null}
                        </Pressable>
                      ) : (
                        <Pressable key={attachment.attachmentId} onPress={() => void handleOpenAttachment(attachment)} style={styles.attachmentCard}>
                          <Text style={styles.attachmentName}>{attachmentTitle(attachment)}</Text>
                          <Text style={styles.attachmentMeta}>{attachment.contentType} - {formatFileSize(attachment.fileSizeBytes)}</Text>
                          <Text style={styles.attachmentMeta}>
                            {getAttachmentTransferMeta(attachment) ??
                              (isQueuedUploadAttachment(attachment)
                                ? "Queued upload"
                                : openingAttachmentId === attachment.attachmentId ? "Opening..." : (attachment.streamingSupported || isVideoAttachment(attachment)) ? "Open / stream" : "Open")}
                          </Text>
                        </Pressable>
                      )
                    ))}
                  </View>
                ) : null}
                {!item.deletedAt && item.reactions.length > 0 ? (
                  <View style={styles.rowWrap}>
                    {item.reactions.map((reaction) => (
                      <Pressable
                        disabled={!reactionsEnabled}
                        key={`${item.messageId}-${reaction.emoji}`}
                        onPress={() => void handleToggleReaction(reaction.emoji, item)}
                        style={[styles.reactionChip, !reactionsEnabled && styles.disabled]}
                      >
                        <Text style={styles.reactionChipText}>{reaction.emoji} {reaction.count}</Text>
                      </Pressable>
                    ))}
                  </View>
                ) : null}
                {!activeThreadRootMessageId &&
                item.discussionChatId &&
                item.discussionRootMessageId &&
                onOpenDiscussionThread ? (
                  <View style={styles.rowWrap}>
                    <Pressable
                      onPress={() => onOpenDiscussionThread(item)}
                      style={styles.inlineButton}
                    >
                      <Text style={styles.inlineButtonText}>
                        {item.commentCount > 0
                          ? `${item.commentCount} comment${item.commentCount === 1 ? "" : "s"}`
                          : "Discuss"}
                      </Text>
                    </Pressable>
                  </View>
                ) : null}
                <Text style={[styles.messageTime, isMine && styles.ownMessageTime]}>{renderMessageMeta(item)}</Text>
              </Pressable>
            );
          }}
        />

        {pendingAttachments.length > 0 ? (
          <View style={styles.pendingBar}>
            <Text style={styles.pendingTitle}>Pending attachments</Text>
            <View style={styles.pendingList}>
              {pendingAttachments.map((attachment) => (
                <View key={attachment.attachmentId} style={styles.pendingChip}>
                  {isImageAttachment(attachment) && attachment.previewUrl ? (
                    <Image source={{ uri: attachment.previewUrl }} style={styles.pendingImagePreview} />
                  ) : null}
                  <View style={styles.pendingText}>
                    <Text style={styles.pendingName}>
                      {attachmentTitle(attachment)}
                    </Text>
                    <Text style={styles.pendingMeta}>
                      {isAudioAttachment(attachment)
                        ? `${formatDuration(attachment.durationMs)} · ${formatFileSize(attachment.fileSizeBytes)}`
                        : isImageAttachment(attachment)
                          ? `${attachmentTitle(attachment)} · ${formatFileSize(attachment.fileSizeBytes)}`
                          : formatFileSize(attachment.fileSizeBytes)}
                    </Text>
                    {attachment.width && attachment.height && isImageAttachment(attachment) ? (
                      <Text style={styles.pendingMeta}>
                        {attachment.width}x{attachment.height}
                      </Text>
                    ) : null}
                    {isAudioAttachment(attachment) ? renderWaveform(attachment, "#166534") : null}
                    {getAttachmentTransferMeta(attachment) ? (
                      <Text style={styles.pendingMeta}>
                        {getAttachmentTransferMeta(attachment)}
                      </Text>
                    ) : null}
                  </View>
                  <Pressable onPress={() => void removePendingAttachment(attachment)} style={styles.inlineDangerButton}>
                    <Text style={styles.inlineDangerText}>Remove</Text>
                  </Pressable>
                </View>
              ))}
            </View>
          </View>
        ) : null}

        {recordingVoice ? (
          <View style={styles.infoBar}>
            <Text style={styles.infoTitle}>Recording voice message</Text>
            <Text style={styles.infoText}>{formatDuration(recordingDurationMs)}</Text>
            <View style={styles.rowWrap}>
              <Pressable onPress={() => void handleStopVoiceRecording(false)} style={styles.inlineButton}>
                <Text style={styles.inlineButtonText}>Send voice</Text>
              </Pressable>
              <Pressable onPress={() => void handleStopVoiceRecording(true)} style={styles.inlineDangerButton}>
                <Text style={styles.inlineDangerText}>Discard</Text>
              </Pressable>
            </View>
          </View>
        ) : null}

        {showPollComposer ? (
          <View style={styles.selectionBar}>
            <Text style={styles.selectionTitle}>New poll</Text>
            <TextInput
              onChangeText={setPollQuestion}
              placeholder="Question"
              style={[styles.input, styles.pollInput]}
              value={pollQuestion}
            />
            {pollOptions.map((option, index) => (
              <View key={`poll-option-${index}`} style={styles.pollOptionEditorRow}>
                <TextInput
                  onChangeText={(value) => updatePollOption(index, value)}
                  placeholder={`Option ${index + 1}`}
                  style={[styles.input, styles.pollOptionEditorInput]}
                  value={option}
                />
                {pollOptions.length > 2 ? (
                  <Pressable onPress={() => removePollOption(index)} style={styles.inlineDangerButton}>
                    <Text style={styles.inlineDangerText}>Remove</Text>
                  </Pressable>
                ) : null}
              </View>
            ))}
            <View style={styles.rowWrap}>
              <Pressable onPress={addPollOption} style={styles.inlineButton}>
                <Text style={styles.inlineButtonText}>Add option</Text>
              </Pressable>
              <Pressable
                onPress={() => setPollMultipleChoice((current) => !current)}
                style={styles.inlineButton}
              >
                <Text style={styles.inlineButtonText}>
                  {pollMultipleChoice ? "Multiple choice" : "Single choice"}
                </Text>
              </Pressable>
              <Pressable onPress={resetPollComposer} style={styles.inlineButton}>
                <Text style={styles.inlineButtonText}>Cancel</Text>
              </Pressable>
              <Pressable
                disabled={sending || !pollQuestion.trim()}
                onPress={() => void handleCreatePoll()}
                style={[styles.primaryButton, (sending || !pollQuestion.trim()) && styles.disabled]}
              >
                <Text style={styles.primaryButtonText}>{sending ? "..." : "Create poll"}</Text>
              </Pressable>
            </View>
          </View>
        ) : null}

        {showLocationComposer ? (
          <View style={styles.selectionBar}>
            <Text style={styles.selectionTitle}>Share location</Text>
            <TextInput
              keyboardType="decimal-pad"
              onChangeText={setLocationLatitude}
              placeholder="Latitude"
              style={[styles.input, styles.pollInput]}
              value={locationLatitude}
            />
            <TextInput
              keyboardType="decimal-pad"
              onChangeText={setLocationLongitude}
              placeholder="Longitude"
              style={[styles.input, styles.pollInput]}
              value={locationLongitude}
            />
            <TextInput
              onChangeText={setLocationTitle}
              placeholder="Label (optional)"
              style={[styles.input, styles.pollInput]}
              value={locationTitle}
            />
            <TextInput
              onChangeText={setLocationAddress}
              placeholder="Address (optional)"
              style={[styles.input, styles.pollInput]}
              value={locationAddress}
            />
            <Text style={styles.selectionBody}>
              Optional note goes in the main composer field below.
            </Text>
            <View style={styles.rowWrap}>
              <Pressable onPress={resetLocationComposer} style={styles.inlineButton}>
                <Text style={styles.inlineButtonText}>Cancel</Text>
              </Pressable>
            </View>
          </View>
        ) : null}

        {showContactComposer ? (
          <View style={styles.selectionBar}>
            <Text style={styles.selectionTitle}>Share contact</Text>
            <TextInput
              onChangeText={setContactFirstName}
              placeholder="First name"
              style={[styles.input, styles.pollInput]}
              value={contactFirstName}
            />
            <TextInput
              onChangeText={setContactLastName}
              placeholder="Last name"
              style={[styles.input, styles.pollInput]}
              value={contactLastName}
            />
            <TextInput
              keyboardType="phone-pad"
              onChangeText={setContactPhoneNumber}
              placeholder="Phone number"
              style={[styles.input, styles.pollInput]}
              value={contactPhoneNumber}
            />
            <TextInput
              onChangeText={setContactUserId}
              placeholder="Linked user id (optional)"
              style={[styles.input, styles.pollInput]}
              value={contactUserId}
            />
            <Text style={styles.selectionBody}>
              Optional note goes in the main composer field below.
            </Text>
            <View style={styles.rowWrap}>
              <Pressable onPress={resetContactComposer} style={styles.inlineButton}>
                <Text style={styles.inlineButtonText}>Cancel</Text>
              </Pressable>
            </View>
          </View>
        ) : null}

        {showStickerPicker ? (
          <View style={styles.selectionBar}>
            <Text style={styles.selectionTitle}>Sticker packs</Text>
            {loadingStickerPacks ? (
              <Text style={styles.selectionBody}>Loading stickers...</Text>
            ) : (
              <View style={styles.stickerPackList}>
                {stickerPacks.map((pack) => (
                  <View key={pack.packId} style={styles.stickerPackSection}>
                    <Text style={styles.stickerPackTitle}>{pack.title}</Text>
                    <View style={styles.stickerPickerGrid}>
                      {pack.stickers.map((sticker) => (
                        <Pressable
                          key={sticker.stickerId}
                          onPress={() => void handleSendSticker(sticker.stickerId)}
                          style={[
                            styles.stickerPickerCard,
                            {
                              backgroundColor: sticker.backgroundFrom,
                              borderColor: sticker.backgroundTo
                            }
                          ]}
                        >
                          <Text style={styles.stickerPickerEmoji}>{sticker.emoji}</Text>
                          <Text style={[styles.stickerPickerLabel, { color: sticker.textColor }]}>
                            {sticker.label}
                          </Text>
                        </Pressable>
                      ))}
                    </View>
                  </View>
                ))}
              </View>
            )}
          </View>
        ) : null}

        <View style={styles.composerSection}>
          <View style={styles.formatBar}>
            {FORMAT_ACTIONS.map((action) => (
              <Pressable
                key={action.type}
                disabled={!canPost || recordingVoice || !canFormatSelection}
                onPress={() => handleToggleFormatting(action.type)}
                style={[
                  styles.formatButton,
                  isFormattingActive(action.type) && styles.formatButtonActive,
                  (!canPost || recordingVoice || !canFormatSelection) && styles.disabled
                ]}
              >
                <Text
                  style={[
                    styles.formatButtonText,
                    isFormattingActive(action.type) && styles.formatButtonTextActive
                  ]}
                >
                  {action.label}
                </Text>
              </Pressable>
            ))}
            <Pressable
              disabled={!canPost || recordingVoice || !!editingMessageId || showPollComposer}
              onPress={() => setSendSilently((current) => !current)}
              style={[
                styles.formatButton,
                sendSilently && styles.formatButtonActive,
                (!canPost || recordingVoice || !!editingMessageId || showPollComposer) &&
                  styles.disabled
              ]}
            >
              <Text
                style={[
                  styles.formatButtonText,
                  sendSilently && styles.formatButtonTextActive
                ]}
              >
                Silent
              </Text>
            </Pressable>
          </View>

          <View style={styles.composer}>
          <Pressable disabled={!canPost || uploadingAttachments || !!editingMessageId || showPollComposer || recordingVoice || !!activeStructuredMessageType} onPress={() => void handlePickAttachments()} style={[styles.secondaryButton, (!canPost || uploadingAttachments || !!editingMessageId || showPollComposer || recordingVoice || !!activeStructuredMessageType) && styles.disabled]}>
            <Text style={styles.secondaryButtonText}>{uploadingAttachments ? "..." : "File"}</Text>
          </Pressable>
          <Pressable disabled={!canPost || uploadingAttachments || !!editingMessageId || showPollComposer || recordingVoice || !!activeStructuredMessageType} onPress={() => void handlePickPhotos()} style={[styles.secondaryButton, (!canPost || uploadingAttachments || !!editingMessageId || showPollComposer || recordingVoice || !!activeStructuredMessageType) && styles.disabled]}>
            <Text style={styles.secondaryButtonText}>Photo</Text>
          </Pressable>
          <Pressable disabled={!canPost || uploadingAttachments || !!editingMessageId || showPollComposer || recordingVoice || !!activeStructuredMessageType} onPress={() => void handlePickVideos()} style={[styles.secondaryButton, (!canPost || uploadingAttachments || !!editingMessageId || showPollComposer || recordingVoice || !!activeStructuredMessageType) && styles.disabled]}>
            <Text style={styles.secondaryButtonText}>Video</Text>
          </Pressable>
          <Pressable disabled={!canPost || uploadingAttachments || !!editingMessageId || showPollComposer || recordingVoice || !!activeStructuredMessageType} onPress={() => void handlePickAudioFiles()} style={[styles.secondaryButton, (!canPost || uploadingAttachments || !!editingMessageId || showPollComposer || recordingVoice || !!activeStructuredMessageType) && styles.disabled]}>
            <Text style={styles.secondaryButtonText}>Audio</Text>
          </Pressable>
          <Pressable disabled={!canPost || uploadingAttachments || !!editingMessageId || showPollComposer || recordingVoice || !!activeStructuredMessageType} onPress={() => void handlePickGifs()} style={[styles.secondaryButton, (!canPost || uploadingAttachments || !!editingMessageId || showPollComposer || recordingVoice || !!activeStructuredMessageType) && styles.disabled]}>
            <Text style={styles.secondaryButtonText}>GIF</Text>
          </Pressable>
          <Pressable disabled={!canPost || !!editingMessageId || pendingAttachments.length > 0 || recordingVoice || showPollComposer || showContactComposer} onPress={handleToggleLocationComposer} style={[styles.secondaryButton, (!canPost || !!editingMessageId || pendingAttachments.length > 0 || recordingVoice || showPollComposer || showContactComposer) && styles.disabled]}>
            <Text style={styles.secondaryButtonText}>Location</Text>
          </Pressable>
          <Pressable disabled={!canPost || !!editingMessageId || pendingAttachments.length > 0 || recordingVoice || showPollComposer || showLocationComposer} onPress={handleToggleContactComposer} style={[styles.secondaryButton, (!canPost || !!editingMessageId || pendingAttachments.length > 0 || recordingVoice || showPollComposer || showLocationComposer) && styles.disabled]}>
            <Text style={styles.secondaryButtonText}>Contact</Text>
          </Pressable>
          <Pressable disabled={!canPost || !!editingMessageId || !!activeStructuredMessageType} onPress={() => {
            setShowStickerPicker(false);
            resetLocationComposer();
            resetContactComposer();
            setShowPollComposer((current) => !current);
          }} style={[styles.secondaryButton, (!canPost || !!editingMessageId || !!activeStructuredMessageType) && styles.disabled]}>
            <Text style={styles.secondaryButtonText}>Poll</Text>
          </Pressable>
          <Pressable disabled={!canPost || !!editingMessageId || uploadingAttachments || recordingVoice || pendingAttachments.length > 0 || !!activeStructuredMessageType} onPress={() => void handleToggleStickerPicker()} style={[styles.secondaryButton, (!canPost || !!editingMessageId || uploadingAttachments || recordingVoice || pendingAttachments.length > 0 || !!activeStructuredMessageType) && styles.disabled]}>
            <Text style={styles.secondaryButtonText}>Sticker</Text>
          </Pressable>
          <Pressable disabled={!canPost || !!editingMessageId || uploadingAttachments || showPollComposer || !!activeStructuredMessageType} onPress={() => void handleStartVoiceRecording()} style={[styles.secondaryButton, (!canPost || !!editingMessageId || uploadingAttachments || showPollComposer || !!activeStructuredMessageType) && styles.disabled]}>
            <Text style={styles.secondaryButtonText}>Mic</Text>
          </Pressable>
          <TextInput
            editable={canPost && !recordingVoice}
            multiline
            onChangeText={handleDraftChange}
            onSelectionChange={handleComposerSelectionChange}
            placeholder={
              canPost
                ? editingMessageId
                  ? "Edit text"
                  : activeStructuredMessageType
                    ? "Optional note"
                  : "Type a message"
                : memberRestricted
                  ? "Posting restricted"
                  : slowModeEndsAt
                    ? "Slow mode active"
                  : "Posting disabled"
            }
            style={[styles.input, styles.composerInput]}
            value={draft}
          />
          {chat.chatType === "DIRECT" ? (
            <Pressable disabled={scheduling || sending || uploadingAttachments || !hasComposerContent || !canPost || showPollComposer || recordingVoice || !!editingMessageId} onPress={() => void handleSendWhenOnline()} style={[styles.secondaryButton, (scheduling || sending || uploadingAttachments || !hasComposerContent || !canPost || showPollComposer || recordingVoice || !!editingMessageId) && styles.disabled]}>
              <Text style={styles.secondaryButtonText}>{scheduling ? "..." : "Online"}</Text>
            </Pressable>
          ) : null}
          <Pressable disabled={scheduling || sending || uploadingAttachments || !hasComposerContent || !canPost || showPollComposer || recordingVoice || !!editingMessageId} onPress={() => void handleScheduleMessage()} style={[styles.secondaryButton, (scheduling || sending || uploadingAttachments || !hasComposerContent || !canPost || showPollComposer || recordingVoice || !!editingMessageId) && styles.disabled]}>
            <Text style={styles.secondaryButtonText}>{scheduling ? "..." : "10m"}</Text>
          </Pressable>
          <Pressable disabled={sending || uploadingAttachments || !hasComposerContent || !canPost || showPollComposer || recordingVoice} onPress={handleSend} style={[styles.primaryButton, (sending || uploadingAttachments || !hasComposerContent || !canPost || showPollComposer || recordingVoice) && styles.disabled]}>
            <Text style={styles.primaryButtonText}>{sending ? "..." : editingMessageId ? "Save" : "Send"}</Text>
          </Pressable>
        </View>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#f8fafc" },
  header: {
    flexDirection: "row",
    gap: 12,
    alignItems: "center",
    flexWrap: "wrap",
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 12
  },
  headerText: { flex: 1, minWidth: 160 },
  title: { fontSize: 20, fontWeight: "700", color: "#0f172a" },
  subtitle: { color: "#64748b", marginTop: 2 },
  typingLabel: { color: "#0f766e", marginTop: 4, fontSize: 13 },
  secondaryButton: { borderRadius: 12, backgroundColor: "#e2e8f0", paddingHorizontal: 14, paddingVertical: 10 },
  secondaryButtonText: { color: "#0f172a", fontWeight: "600" },
  primaryButton: { borderRadius: 14, backgroundColor: "#0f172a", paddingHorizontal: 16, paddingVertical: 14, alignItems: "center", justifyContent: "center" },
  primaryButtonText: { color: "#ffffff", fontWeight: "600" },
  disabled: { opacity: 0.6 },
  infoBar: { marginHorizontal: 16, marginBottom: 8, borderRadius: 14, backgroundColor: "#e0f2fe", padding: 12 },
  infoTitle: { color: "#075985", fontWeight: "700" },
  infoText: { color: "#0c4a6e", marginTop: 4 },
  infoMetaText: { color: "#0c4a6e", marginTop: 6, fontSize: 12, fontWeight: "600" },
  selectionBar: { marginHorizontal: 16, marginBottom: 8, borderRadius: 14, backgroundColor: "#fef3c7", padding: 12 },
  selectionTitle: { color: "#92400e", fontWeight: "700" },
  selectionBody: { color: "#92400e", marginTop: 4 },
  scheduledList: { gap: 8, marginTop: 10 },
  scheduledCard: { borderRadius: 12, backgroundColor: "#ffffff", padding: 10, gap: 4 },
  activePinnedHistoryCard: { borderWidth: 2, borderColor: "#f59e0b" },
  pinnedHistoryHeader: { flexDirection: "row", justifyContent: "space-between", gap: 12, alignItems: "center" },
  scheduledText: { color: "#0f172a", fontWeight: "600" },
  scheduledMeta: { color: "#64748b", fontSize: 12 },
  rowWrap: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginTop: 8 },
  reactionText: { fontSize: 16 },
  searchRow: { flexDirection: "row", gap: 8, alignItems: "center", paddingHorizontal: 16, marginBottom: 8 },
  input: { borderWidth: 1, borderColor: "#cbd5e1", borderRadius: 14, backgroundColor: "#ffffff", paddingHorizontal: 14, paddingVertical: 12 },
  searchInfoBar: { marginHorizontal: 16, marginBottom: 8, borderRadius: 14, backgroundColor: "#ecfeff", paddingHorizontal: 12, paddingVertical: 10 },
  searchInfoText: { color: "#155e75", fontWeight: "600" },
  botCommandBar: { marginHorizontal: 16, marginBottom: 8, borderRadius: 14, backgroundColor: "#eef2ff", padding: 12 },
  inlineResultsBar: { marginHorizontal: 16, marginBottom: 8, borderRadius: 14, backgroundColor: "#ecfccb", padding: 12 },
  inlineResultsTitle: { color: "#365314", fontWeight: "700" },
  inlineResultsMeta: { color: "#4d7c0f", marginTop: 6 },
  inlineResultsList: { gap: 8, marginTop: 10 },
  inlineResultCard: { borderRadius: 12, backgroundColor: "#ffffff", paddingHorizontal: 12, paddingVertical: 10 },
  inlineResultTitle: { color: "#0f172a", fontWeight: "700" },
  inlineResultDescription: { color: "#475569", marginTop: 4, fontSize: 12 },
  loadOlderButton: { marginHorizontal: 16, marginBottom: 8, borderRadius: 14, backgroundColor: "#e2e8f0", paddingVertical: 12, alignItems: "center" },
  loadOlderText: { color: "#0f172a", fontWeight: "700" },
  loader: { marginTop: 12 },
  errorText: { color: "#b91c1c", paddingHorizontal: 20, paddingTop: 8 },
  restrictionBar: {
    marginHorizontal: 16,
    marginBottom: 8,
    borderRadius: 14,
    backgroundColor: "#fff7ed",
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  restrictionBarTitle: {
    color: "#9a3412",
    fontWeight: "700"
  },
  restrictionBarText: {
    color: "#9a3412",
    marginTop: 4
  },
  messagesContent: { paddingHorizontal: 20, paddingVertical: 16, gap: 10 },
  messageBubble: { maxWidth: "80%", borderRadius: 18, paddingHorizontal: 14, paddingVertical: 10 },
  ownBubble: { alignSelf: "flex-end", backgroundColor: "#0f172a" },
  peerBubble: { alignSelf: "flex-start", backgroundColor: "#ffffff" },
  selectedBubble: { borderWidth: 2, borderColor: "#f59e0b" },
  pinnedBubble: { borderWidth: 2, borderColor: "#0284c7" },
  authorLabel: { color: "#0369a1", fontSize: 12, fontWeight: "700", marginBottom: 6 },
  replyPreview: { borderLeftWidth: 3, borderLeftColor: "#38bdf8", paddingLeft: 8, marginBottom: 8 },
  replyPreviewAuthor: { color: "#0f766e", fontSize: 12, fontWeight: "700", marginBottom: 4 },
  replyPreviewText: { color: "#475569", fontSize: 12 },
  attachmentsColumn: { gap: 8, marginTop: 10 },
  imageCard: { gap: 8, marginTop: 8 },
  imageAttachment: { width: 220, height: 220, borderRadius: 18, backgroundColor: "#dbeafe" },
  attachmentCard: { borderRadius: 12, backgroundColor: "#eff6ff", paddingHorizontal: 10, paddingVertical: 8 },
  attachmentName: { color: "#1e3a8a", fontWeight: "700" },
  attachmentMeta: { color: "#475569", fontSize: 12, marginTop: 2 },
  voiceCard: { borderRadius: 16, backgroundColor: "#dcfce7", paddingHorizontal: 12, paddingVertical: 10 },
  waveformRow: { flexDirection: "row", alignItems: "flex-end", gap: 2, marginTop: 8, minHeight: 24 },
  waveformBar: { width: 4, borderRadius: 999 },
  voiceTitle: { color: "#166534", fontWeight: "700" },
  voiceMeta: { color: "#166534", fontSize: 12, marginTop: 4 },
  structuredCard: { marginTop: 8, borderRadius: 16, backgroundColor: "#fef3c7", paddingHorizontal: 12, paddingVertical: 10 },
  structuredTitle: { color: "#92400e", fontWeight: "700" },
  structuredBody: { color: "#92400e", marginTop: 4 },
  structuredMeta: { color: "#b45309", fontSize: 12, marginTop: 4 },
  serviceCard: { marginTop: 8, borderRadius: 16, backgroundColor: "#e2e8f0", paddingHorizontal: 12, paddingVertical: 10 },
  serviceTitle: { color: "#334155", fontWeight: "700" },
  serviceText: { color: "#475569", marginTop: 4 },
  stickerBubble: {
    marginTop: 8,
    borderRadius: 24,
    borderWidth: 2,
    paddingHorizontal: 18,
    paddingVertical: 14,
    alignItems: "center"
  },
  stickerEmoji: {
    fontSize: 42
  },
  stickerLabel: {
    marginTop: 8,
    fontSize: 16,
    fontWeight: "700"
  },
  stickerPackLabel: {
    marginTop: 4,
    fontSize: 12,
    fontWeight: "600",
    opacity: 0.9
  },
  pollCard: { marginTop: 8, gap: 8 },
  pollQuestion: { color: "#0f172a", fontWeight: "700" },
  pollOption: { borderRadius: 12, backgroundColor: "#eff6ff", paddingHorizontal: 12, paddingVertical: 10 },
  pollOptionSelected: { borderWidth: 2, borderColor: "#2563eb" },
  pollOptionText: { color: "#0f172a", fontWeight: "600" },
  pollOptionMeta: { color: "#475569", fontSize: 12, marginTop: 4 },
  pollFooter: { color: "#64748b", fontSize: 12, fontWeight: "600" },
  badgeText: { marginBottom: 6, color: "#475569", fontSize: 12, fontWeight: "600" },
  messageText: { color: "#0f172a" },
  ownMessageText: { color: "#ffffff" },
  reactionChip: { borderRadius: 999, backgroundColor: "#dbeafe", paddingHorizontal: 10, paddingVertical: 6 },
  reactionChipText: { color: "#1e3a8a", fontWeight: "600" },
  messageTime: { marginTop: 6, fontSize: 11, color: "#94a3b8" },
  ownMessageTime: { color: "#cbd5e1" },
  inlineButton: { borderRadius: 10, backgroundColor: "#ffffff", paddingHorizontal: 12, paddingVertical: 8 },
  inlineButtonText: { color: "#0f172a", fontWeight: "600" },
  inlineDangerButton: { borderRadius: 10, backgroundColor: "#fee2e2", paddingHorizontal: 12, paddingVertical: 8 },
  inlineDangerText: { color: "#b91c1c", fontWeight: "600" },
  pendingBar: { borderTopWidth: 1, borderTopColor: "#e2e8f0", backgroundColor: "#ffffff", paddingHorizontal: 16, paddingTop: 12 },
  pendingTitle: { color: "#0f172a", fontWeight: "700", marginBottom: 8 },
  pendingList: { gap: 8, paddingBottom: 12 },
  pendingChip: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", borderRadius: 14, backgroundColor: "#f8fafc", paddingHorizontal: 12, paddingVertical: 10, gap: 12 },
  pendingImagePreview: { width: 48, height: 48, borderRadius: 12, backgroundColor: "#dbeafe" },
  pendingText: { flex: 1 },
  pendingName: { color: "#0f172a", fontWeight: "600" },
  pendingMeta: { marginTop: 2, color: "#64748b", fontSize: 12 },
  pollInput: { marginTop: 10 },
  pollOptionEditorRow: { flexDirection: "row", gap: 8, alignItems: "center", marginTop: 8 },
  pollOptionEditorInput: { flex: 1 },
  stickerPackList: {
    gap: 12,
    marginTop: 10
  },
  stickerPackSection: {
    gap: 8
  },
  stickerPackTitle: {
    color: "#92400e",
    fontWeight: "700"
  },
  stickerPickerGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8
  },
  stickerPickerCard: {
    width: 88,
    borderRadius: 18,
    borderWidth: 2,
    paddingHorizontal: 10,
    paddingVertical: 12,
    alignItems: "center"
  },
  stickerPickerEmoji: {
    fontSize: 28
  },
  stickerPickerLabel: {
    marginTop: 6,
    fontSize: 11,
    fontWeight: "700",
    textAlign: "center"
  },
  composerSection: {
    borderTopWidth: 1,
    borderTopColor: "#e2e8f0",
    backgroundColor: "#ffffff",
    paddingTop: 10
  },
  formatBar: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    paddingHorizontal: 16,
    paddingBottom: 10
  },
  formatButton: {
    borderRadius: 10,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 10,
    paddingVertical: 8
  },
  formatButtonActive: {
    backgroundColor: "#0f172a"
  },
  formatButtonText: {
    color: "#0f172a",
    fontWeight: "700"
  },
  formatButtonTextActive: {
    color: "#ffffff"
  },
  composer: { flexDirection: "row", alignItems: "flex-end", gap: 12, backgroundColor: "#ffffff", paddingHorizontal: 16, paddingBottom: 16 },
  composerInput: { flex: 1, minHeight: 48, maxHeight: 120 }
});
