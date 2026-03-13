import React, { useEffect, useMemo, useRef, useState } from "react";
import * as DocumentPicker from "expo-document-picker";
import { Audio, ResizeMode, Video } from "expo-av";
import * as ScreenCapture from "expo-screen-capture";
import {
  FlatList,
  Image,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  View
} from "react-native";
import { Avatar } from "../components/Avatar";
import { api } from "../services/api";
import {
  cleanupPreparedSecretAttachment,
  ensureDecryptedSecretAttachment,
  getCachedSecretAttachmentUri,
  prepareSecretAttachmentUpload,
  removePendingSecretAttachment,
  type PendingSecretAttachmentDraft,
  uploadPreparedSecretAttachment
} from "../services/secretChatAttachments";
import { localDatabase } from "../services/localDatabase";
import { secretChatCrypto } from "../services/secretChatCrypto";
import { secretChatLocalCleanup } from "../services/secretChatLocalCleanup";
import { wsService } from "../services/ws";
import type {
  SecretChatAttachment,
  SecretChatInboxEvent,
  SecretChatMessage,
  SecretChatPayload,
  SecretChatReadEvent,
  SecretChatScreenshotEvent,
  SecretChatSummary
} from "../types";

type SecretChatScreenProps = {
  token: string;
  currentUserId: string;
  secretChat: SecretChatSummary;
  onBack: () => void;
  onSummaryChange: (secretChat: SecretChatSummary) => void;
};

type ResolvedSecretChatMessage = {
  raw: SecretChatMessage;
  text: string | null;
  attachments: SecretChatAttachment[];
  failed: boolean;
};

const SECRET_SCREEN_CAPTURE_KEY = "alex-secret-chat";

function mergeSecretChatMessages(current: SecretChatMessage[], incoming: SecretChatMessage[]) {
  const map = new Map<string, SecretChatMessage>();
  for (const message of [...current, ...incoming]) {
    map.set(message.secretMessageId, message);
  }
  return [...map.values()].sort((left, right) => left.createdAt.localeCompare(right.createdAt));
}

function filterVisibleMessages(messages: SecretChatMessage[]) {
  const now = Date.now();
  return messages.filter((message) => !message.expiresAt || new Date(message.expiresAt).getTime() > now);
}

async function resolveMessages(
  secretChat: SecretChatSummary,
  messages: SecretChatMessage[]
): Promise<ResolvedSecretChatMessage[]> {
  return Promise.all(
    filterVisibleMessages(messages).map(async (message) => {
      try {
        const payload = await secretChatCrypto.decryptPayload(secretChat, message);
        return {
          raw: message,
          text: payload.text,
          attachments: payload.attachments,
          failed: false
        };
      } catch {
        return {
          raw: message,
          text: "[Unable to decrypt on this device]",
          attachments: [],
          failed: true
        };
      }
    })
  );
}

function formatFileSize(fileSizeBytes: number) {
  if (fileSizeBytes >= 1024 * 1024) {
    return `${(fileSizeBytes / (1024 * 1024)).toFixed(1)} MB`;
  }
  if (fileSizeBytes >= 1024) {
    return `${Math.round(fileSizeBytes / 1024)} KB`;
  }
  return `${fileSizeBytes} B`;
}

function formatDuration(durationMs: number | null | undefined) {
  const totalSeconds = Math.max(0, Math.round((durationMs ?? 0) / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

async function cleanupPendingUploads(
  token: string,
  attachments: PendingSecretAttachmentDraft[]
) {
  await Promise.allSettled(
    attachments.map((attachment) => removePendingSecretAttachment(token, attachment))
  );
}

function inferSecretAttachmentKind(
  requestedKind: "FILE" | "IMAGE",
  assetName: string | null | undefined,
  mimeType: string | null | undefined
): "FILE" | "IMAGE" | "VIDEO" {
  if (requestedKind === "IMAGE") {
    return "IMAGE";
  }

  const normalizedMimeType = mimeType?.trim().toLowerCase() ?? "";
  if (normalizedMimeType.startsWith("video/")) {
    return "VIDEO";
  }

  const normalizedName = assetName?.trim().toLowerCase() ?? "";
  if (/\.(mp4|mov|m4v|webm|mkv|avi)$/i.test(normalizedName)) {
    return "VIDEO";
  }

  return "FILE";
}

export function SecretChatScreen({
  token,
  currentUserId,
  secretChat,
  onBack,
  onSummaryChange
}: SecretChatScreenProps) {
  const [messages, setMessages] = useState<SecretChatMessage[]>([]);
  const [resolvedMessages, setResolvedMessages] = useState<ResolvedSecretChatMessage[]>([]);
  const [pendingAttachments, setPendingAttachments] = useState<PendingSecretAttachmentDraft[]>([]);
  const [decryptedAttachmentUris, setDecryptedAttachmentUris] = useState<Record<string, string>>({});
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [uploadingAttachments, setUploadingAttachments] = useState(false);
  const [recordingVoice, setRecordingVoice] = useState(false);
  const [recordingDurationMs, setRecordingDurationMs] = useState(0);
  const [playingVoiceAttachmentId, setPlayingVoiceAttachmentId] = useState<string | null>(null);
  const [openingAttachmentId, setOpeningAttachmentId] = useState<string | null>(null);
  const [focusedImage, setFocusedImage] = useState<{ uri: string; name: string } | null>(null);
  const [focusedVideo, setFocusedVideo] = useState<{ uri: string; name: string } | null>(null);
  const [focusedVideoPlaying, setFocusedVideoPlaying] = useState(true);
  const [updatingTimer, setUpdatingTimer] = useState(false);
  const [markingRead, setMarkingRead] = useState(false);
  const [closing, setClosing] = useState(false);
  const [localFingerprint, setLocalFingerprint] = useState<string | null>(null);
  const [fingerprintMismatch, setFingerprintMismatch] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activityNotice, setActivityNotice] = useState<string | null>(null);
  const [visibilityTick, setVisibilityTick] = useState(0);
  const pendingAttachmentsRef = useRef<PendingSecretAttachmentDraft[]>([]);
  const recordingRef = useRef<Audio.Recording | null>(null);
  const soundRef = useRef<Audio.Sound | null>(null);

  useEffect(() => {
    pendingAttachmentsRef.current = pendingAttachments;
  }, [pendingAttachments]);

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

  function applyReadEvent(event: SecretChatReadEvent) {
    if (!event.messageIds.length) {
      return;
    }

    setMessages((current) => {
      const next = current.map((message) =>
        event.messageIds.includes(message.secretMessageId)
          ? {
              ...message,
              readAt: event.readAt,
              expiresAt: event.expiresAt
            }
          : message
      );
      void localDatabase.upsertSecretChatMessages(
        currentUserId,
        next.filter((message) => event.messageIds.includes(message.secretMessageId))
      ).catch(() => undefined);
      return next;
    });
  }

  function applyScreenshotEvent(event: SecretChatScreenshotEvent) {
    setActivityNotice(
      event.capturedByUserId === currentUserId
        ? "Screenshot captured on this device."
        : `${secretChat.peerDisplayName} took a screenshot.`
    );
  }

  function handleRestrictedActionNotice() {
    setActivityNotice("Copying, forwarding, and saving are disabled in secret chats.");
  }

  useEffect(() => {
    if (!["DECLINED", "CLOSED"].includes(secretChat.status)) {
      return;
    }

    let active = true;
    void secretChatLocalCleanup
      .purgeSecretChat(currentUserId, secretChat.secretChatId)
      .finally(() => {
        if (active) {
          onBack();
        }
      });

    return () => {
      active = false;
    };
  }, [currentUserId, onBack, secretChat.secretChatId, secretChat.status]);

  useEffect(() => {
    let cancelled = false;

    async function loadSecretChat() {
      setError(null);
      try {
        const cachedMessages = await localDatabase.getSecretChatMessages(currentUserId, secretChat.secretChatId);
        if (!cancelled && cachedMessages.length > 0) {
          setMessages(cachedMessages);
        }

        const nextMessages = await api.getSecretChatMessages(token, secretChat.secretChatId, 100);
        if (!cancelled) {
          setMessages(nextMessages);
          await localDatabase.replaceSecretChatMessages(currentUserId, secretChat.secretChatId, nextMessages);
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : "Unable to load secret chat");
        }
      }
    }

    void loadSecretChat();

    const unsubscribe = wsService.subscribe("/user/queue/secret-chats", (payload) => {
      const event = JSON.parse(payload) as SecretChatInboxEvent;
      if (event.chat?.secretChatId === secretChat.secretChatId) {
        if (["DECLINED", "CLOSED"].includes(event.chat.status)) {
          void secretChatLocalCleanup
            .purgeSecretChat(currentUserId, event.chat.secretChatId)
            .finally(onBack);
          return;
        }
        onSummaryChange(event.chat);
        void localDatabase.upsertSecretChat(currentUserId, event.chat).catch(() => undefined);
      }
      if (event.message?.secretChatId === secretChat.secretChatId) {
        setMessages((current) => mergeSecretChatMessages(current, [event.message as SecretChatMessage]));
        void localDatabase.upsertSecretChatMessages(currentUserId, [event.message]).catch(() => undefined);
      }
      if (event.read?.secretChatId === secretChat.secretChatId) {
        applyReadEvent(event.read);
      }
      if (event.screenshot?.secretChatId === secretChat.secretChatId) {
        applyScreenshotEvent(event.screenshot);
      }
    });

    return () => {
      cancelled = true;
      unsubscribe();
      void cleanupPendingUploads(token, pendingAttachmentsRef.current).catch(() => undefined);
    };
  }, [currentUserId, onSummaryChange, secretChat.secretChatId, token]);

  useEffect(() => {
    if (!activityNotice) {
      return;
    }

    const timeoutId = setTimeout(() => {
      setActivityNotice(null);
    }, 5000);

    return () => {
      clearTimeout(timeoutId);
    };
  }, [activityNotice]);

  useEffect(() => {
    void resolveMessages(secretChat, messages)
      .then(setResolvedMessages)
      .catch(() => setResolvedMessages([]));
  }, [messages, secretChat, visibilityTick]);

  useEffect(() => {
    const intervalId = setInterval(() => {
      setVisibilityTick((current) => current + 1);
    }, 1000);

    return () => {
      clearInterval(intervalId);
    };
  }, []);

  useEffect(() => {
    if (!recordingVoice || !recordingRef.current) {
      return;
    }

    const intervalId = setInterval(() => {
      void recordingRef.current?.getStatusAsync().then((status) => {
        if ("isRecording" in status && status.isRecording && typeof status.durationMillis === "number") {
          setRecordingDurationMs(status.durationMillis);
        }
      }).catch(() => undefined);
    }, 250);

    return () => {
      clearInterval(intervalId);
    };
  }, [recordingVoice]);

  useEffect(() => {
    const visibleMessages = filterVisibleMessages(messages);
    if (visibleMessages.length === messages.length) {
      return;
    }

    setMessages(visibleMessages);
    void localDatabase
      .replaceSecretChatMessages(currentUserId, secretChat.secretChatId, visibleMessages)
      .catch(() => undefined);
  }, [currentUserId, messages, secretChat.secretChatId, visibilityTick]);

  useEffect(() => {
    if (secretChat.status !== "ACTIVE") {
      setLocalFingerprint(null);
      setFingerprintMismatch(false);
      return;
    }

    void secretChatCrypto.deriveStoredFingerprint(secretChat)
      .then((fingerprint) => {
        setLocalFingerprint(fingerprint);
        setFingerprintMismatch(
          !!secretChat.sharedKeyFingerprint && secretChat.sharedKeyFingerprint !== fingerprint
        );
      })
      .catch(() => {
        setLocalFingerprint(null);
        setFingerprintMismatch(false);
      });
  }, [secretChat]);

  useEffect(() => {
    if (secretChat.status !== "ACTIVE") {
      return;
    }

    const hasUnreadIncoming = messages.some(
      (message) => message.senderUserId !== currentUserId && !message.readAt
    );
    if (!hasUnreadIncoming || markingRead) {
      return;
    }

    let cancelled = false;
    setMarkingRead(true);
    void api.markSecretChatRead(token, secretChat.secretChatId)
      .then((event) => {
        if (cancelled) {
          return;
        }
        applyReadEvent(event);
      })
      .catch(() => undefined)
      .finally(() => {
        if (!cancelled) {
          setMarkingRead(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [currentUserId, markingRead, messages, secretChat.secretChatId, secretChat.status, token]);

  useEffect(() => {
    if (secretChat.status !== "ACTIVE") {
      return;
    }

    let active = true;
    let subscription: ScreenCapture.Subscription | null = null;

    void (async () => {
      try {
        await ScreenCapture.preventScreenCaptureAsync(SECRET_SCREEN_CAPTURE_KEY);
        if (!active) {
          await ScreenCapture.allowScreenCaptureAsync(SECRET_SCREEN_CAPTURE_KEY).catch(() => undefined);
          return;
        }

        const currentPermission = await ScreenCapture.getPermissionsAsync();
        if (!active) {
          return;
        }
        if (!currentPermission.granted) {
          const requestedPermission = await ScreenCapture.requestPermissionsAsync();
          if (!active || !requestedPermission.granted) {
            return;
          }
        }

        if (Platform.OS === "ios") {
          await ScreenCapture.enableAppSwitcherProtectionAsync();
        }
        if (!active) {
          if (Platform.OS === "ios") {
            await ScreenCapture.disableAppSwitcherProtectionAsync().catch(() => undefined);
          }
          return;
        }

        subscription = ScreenCapture.addScreenshotListener(() => {
          void api.reportSecretChatScreenshot(token, secretChat.secretChatId).catch(() => undefined);
        });
      } catch {
        return;
      }
    })();

    return () => {
      active = false;
      subscription?.remove();
      void ScreenCapture.allowScreenCaptureAsync(SECRET_SCREEN_CAPTURE_KEY).catch(() => undefined);
      if (Platform.OS === "ios") {
        void ScreenCapture.disableAppSwitcherProtectionAsync().catch(() => undefined);
      }
    };
  }, [secretChat.secretChatId, secretChat.status, token]);

  useEffect(() => {
    const imageAttachments = new Map<string, SecretChatAttachment>();
    for (const message of resolvedMessages) {
      for (const attachment of message.attachments) {
        if (
          attachment.kind === "IMAGE" &&
          !decryptedAttachmentUris[attachment.attachmentId] &&
          attachment.fileSizeBytes <= 8 * 1024 * 1024
        ) {
          imageAttachments.set(attachment.attachmentId, attachment);
        }
      }
    }

    if (imageAttachments.size === 0) {
      return;
    }

    let cancelled = false;
    void Promise.allSettled(
      [...imageAttachments.values()].map(async (attachment) => {
        const cachedUri = await getCachedSecretAttachmentUri(attachment);
        const uri = cachedUri ?? await ensureDecryptedSecretAttachment(token, secretChat, attachment);
        if (!cancelled) {
          setDecryptedAttachmentUris((current) => ({
            ...current,
            [attachment.attachmentId]: uri
          }));
        }
      })
    );

    return () => {
      cancelled = true;
    };
  }, [decryptedAttachmentUris, resolvedMessages, secretChat, token]);

  async function handleSend() {
    const normalizedDraft = draft.trim();
    if (
      (!normalizedDraft && pendingAttachments.length === 0) ||
      sending ||
      uploadingAttachments ||
      recordingVoice ||
      secretChat.status !== "ACTIVE"
    ) {
      return;
    }

    setSending(true);
    setError(null);
    try {
      const payload: SecretChatPayload = {
        version: 1,
        text: normalizedDraft || null,
        attachments: pendingAttachments.map((attachment) => ({
          attachmentId: attachment.attachmentId,
          kind: attachment.kind,
          originalFileName: attachment.originalFileName,
          contentType: attachment.contentType,
          fileSizeBytes: attachment.fileSizeBytes,
          fileNonce: attachment.fileNonce,
          durationMs: attachment.durationMs ?? null
        }))
      };
      const encrypted = await secretChatCrypto.encryptPayload(secretChat, payload);
      const message = await api.sendSecretChatMessage(token, secretChat.secretChatId, {
        ciphertext: encrypted.ciphertextBase64,
        nonce: encrypted.nonce,
        attachmentIds: pendingAttachments.map((attachment) => attachment.attachmentId)
      });
      setDraft("");
      setPendingAttachments([]);
      setMessages((current) => mergeSecretChatMessages(current, [message]));
      await localDatabase.upsertSecretChatMessages(currentUserId, [message]);
    } catch (sendError) {
      setError(sendError instanceof Error ? sendError.message : "Unable to send secret message");
    } finally {
      setSending(false);
    }
  }

  async function handleStartVoiceRecording() {
    if (secretChat.status !== "ACTIVE" || uploadingAttachments || sending || recordingVoice) {
      return;
    }

    setError(null);
    try {
      const permission = await Audio.requestPermissionsAsync();
      if (!permission.granted) {
        setError("Microphone permission is required for secret voice notes");
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
        const preparedAttachment = await prepareSecretAttachmentUpload(secretChat, {
          kind: "VOICE",
          uri,
          name: `secret-voice-${Date.now()}.m4a`,
          contentType: "audio/mp4",
          durationMs: Math.max(durationMs, 1)
        });

        try {
          const uploadedAttachment = await uploadPreparedSecretAttachment(
            token,
            secretChat.secretChatId,
            preparedAttachment
          );
          setPendingAttachments((current) => [...current, uploadedAttachment]);
        } finally {
          await cleanupPreparedSecretAttachment(preparedAttachment).catch(() => undefined);
        }
      }
    } catch (recordError) {
      if (!cancel) {
        setError(recordError instanceof Error ? recordError.message : "Unable to finalize voice note");
      }
    } finally {
      recordingRef.current = null;
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

  async function handlePickAttachments(kind: "FILE" | "IMAGE", mimeType: string) {
    if (secretChat.status !== "ACTIVE" || uploadingAttachments || sending || recordingVoice) {
      return;
    }

    setUploadingAttachments(true);
    setError(null);
    try {
      const selection = await DocumentPicker.getDocumentAsync({
        type: mimeType,
        multiple: true,
        copyToCacheDirectory: true
      });

      if (selection.canceled || selection.assets.length === 0) {
        return;
      }

      const nextAttachments: PendingSecretAttachmentDraft[] = [];
      for (const asset of selection.assets) {
        const attachmentKind = inferSecretAttachmentKind(kind, asset.name, asset.mimeType);
        const preparedAttachment = await prepareSecretAttachmentUpload(secretChat, {
          kind: attachmentKind,
          uri: asset.uri,
          name: asset.name ?? (attachmentKind === "IMAGE" ? "photo.jpg" : attachmentKind === "VIDEO" ? "video.mp4" : "file"),
          contentType:
            asset.mimeType ??
            (attachmentKind === "IMAGE"
              ? "image/jpeg"
              : attachmentKind === "VIDEO"
                ? "video/mp4"
                : "application/octet-stream")
        });

        try {
          const uploadedAttachment = await uploadPreparedSecretAttachment(
            token,
            secretChat.secretChatId,
            preparedAttachment
          );
          nextAttachments.push(uploadedAttachment);
        } finally {
          await cleanupPreparedSecretAttachment(preparedAttachment).catch(() => undefined);
        }
      }

      setPendingAttachments((current) => [...current, ...nextAttachments]);
    } catch (attachmentError) {
      setError(attachmentError instanceof Error ? attachmentError.message : "Unable to upload secret attachment");
    } finally {
      setUploadingAttachments(false);
    }
  }

  async function handleRemovePendingAttachment(attachment: PendingSecretAttachmentDraft) {
    setError(null);
    try {
      await removePendingSecretAttachment(token, attachment);
      setPendingAttachments((current) =>
        current.filter((item) => item.attachmentId !== attachment.attachmentId)
      );
    } catch (removeError) {
      setError(removeError instanceof Error ? removeError.message : "Unable to remove secret attachment");
    }
  }

  async function handleOpenAttachment(attachment: SecretChatAttachment) {
    if (attachment.kind === "VOICE") {
      await handleToggleVoicePlayback(attachment);
      return;
    }

    setOpeningAttachmentId(attachment.attachmentId);
    setError(null);
    try {
      const uri = await ensureDecryptedSecretAttachment(token, secretChat, attachment);
      setDecryptedAttachmentUris((current) => ({
        ...current,
        [attachment.attachmentId]: uri
      }));
      if (attachment.kind === "IMAGE") {
        setFocusedVideo(null);
        setFocusedImage({
          uri,
          name: attachment.originalFileName
        });
      } else if (attachment.kind === "VIDEO") {
        setFocusedImage(null);
        setFocusedVideo({
          uri,
          name: attachment.originalFileName
        });
        setFocusedVideoPlaying(true);
      } else {
        setActivityNotice("Secret files stay on this device. External export is disabled.");
      }
    } catch (openError) {
      setError(openError instanceof Error ? openError.message : "Unable to open secret attachment");
    } finally {
      setOpeningAttachmentId(null);
    }
  }

  async function handleToggleVoicePlayback(attachment: SecretChatAttachment) {
    if (attachment.kind !== "VOICE") {
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

      await Audio.setAudioModeAsync({
        allowsRecordingIOS: false,
        playsInSilentModeIOS: true
      });
      const uri = await ensureDecryptedSecretAttachment(token, secretChat, attachment);
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
      setError(playbackError instanceof Error ? playbackError.message : "Unable to play secret voice note");
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

  async function handleUpdateTimer(autoDeleteSeconds: number | null) {
    if (updatingTimer || secretChat.status !== "ACTIVE") {
      return;
    }

    setUpdatingTimer(true);
    setError(null);
    try {
      const updated = await api.updateSecretChatTimer(token, secretChat.secretChatId, autoDeleteSeconds);
      onSummaryChange(updated);
      await localDatabase.upsertSecretChat(currentUserId, updated);
    } catch (timerError) {
      setError(timerError instanceof Error ? timerError.message : "Unable to update timer");
    } finally {
      setUpdatingTimer(false);
    }
  }

  async function handleCloseChat() {
    if (closing) {
      return;
    }

    setClosing(true);
    setError(null);
    try {
      await cleanupPendingUploads(token, pendingAttachmentsRef.current);
      const closed = await api.closeSecretChat(token, secretChat.secretChatId);
      await secretChatLocalCleanup.purgeSecretChat(currentUserId, closed.secretChatId);
      onBack();
    } catch (closeError) {
      setError(closeError instanceof Error ? closeError.message : "Unable to close secret chat");
    } finally {
      setClosing(false);
    }
  }

  const statusText = useMemo(() => {
    if (secretChat.status === "PENDING") {
      return secretChat.direction === "OUTGOING"
        ? "Waiting for peer device to accept"
        : "Incoming request";
    }
    if (secretChat.status === "ACTIVE") {
      return secretChat.autoDeleteSeconds
        ? `Active - auto-delete ${secretChat.autoDeleteSeconds}s`
        : "Active";
    }
    if (secretChat.status === "DECLINED") {
      return "Declined";
    }
    return "Closed";
  }, [secretChat]);

  return (
    <SafeAreaView style={styles.screen}>
      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        keyboardVerticalOffset={Platform.OS === "ios" ? 18 : 0}
        style={styles.screen}
      >
        <View style={styles.header}>
          <Pressable onPress={onBack} style={styles.secondaryButton}>
            <Text style={styles.secondaryButtonText}>Back</Text>
          </Pressable>
          <Avatar uri={secretChat.peerPhotoUrl} title={secretChat.peerDisplayName} size={48} />
          <View style={styles.headerText}>
            <Text style={styles.title}>{secretChat.peerDisplayName}</Text>
            <Text style={styles.subtitle}>{statusText}</Text>
            <Text style={styles.subtitle}>
              {secretChat.peerDeviceName ?? "Device not bound yet"}
            </Text>
          </View>
          <Pressable
            disabled={closing}
            onPress={() => void handleCloseChat()}
            style={[styles.dangerButton, closing && styles.disabled]}
          >
            <Text style={styles.dangerButtonText}>{closing ? "..." : "Close"}</Text>
          </Pressable>
        </View>

        <View style={styles.infoCard}>
          <Text style={styles.infoTitle}>Key fingerprint</Text>
          <Text style={styles.infoText}>
            {localFingerprint ?? secretChat.sharedKeyFingerprint ?? "Pending handshake"}
          </Text>
          <Text style={styles.infoText}>
            Copying, forwarding, and external saving are disabled in this secret chat.
          </Text>
          {fingerprintMismatch ? (
            <Text style={styles.warningText}>
              Local fingerprint does not match the server-advertised fingerprint for this secret chat.
            </Text>
          ) : null}
          <View style={styles.timerRow}>
            {[null, 10, 30, 60].map((timerValue) => (
              <Pressable
                key={`ttl-${timerValue ?? "off"}`}
                disabled={updatingTimer || secretChat.status !== "ACTIVE"}
                onPress={() => void handleUpdateTimer(timerValue)}
                style={[
                  styles.timerChip,
                  secretChat.autoDeleteSeconds === timerValue && styles.timerChipActive,
                  (updatingTimer || secretChat.status !== "ACTIVE") && styles.disabled
                ]}
              >
                <Text
                  style={[
                    styles.timerChipText,
                    secretChat.autoDeleteSeconds === timerValue && styles.timerChipTextActive
                  ]}
                >
                  {timerValue ? `${timerValue}s` : "Off"}
                </Text>
              </Pressable>
            ))}
          </View>
        </View>

        {error ? <Text style={styles.errorText}>{error}</Text> : null}
        {activityNotice ? <Text style={styles.noticeText}>{activityNotice}</Text> : null}

        <FlatList
          contentContainerStyle={styles.messagesContent}
          data={resolvedMessages}
          keyExtractor={(item) => item.raw.secretMessageId}
          renderItem={({ item }) => {
            const isMine = item.raw.senderUserId === currentUserId;
            const metaParts = [new Date(item.raw.createdAt).toLocaleString()];
            if (isMine && item.raw.readAt) {
              metaParts.push(`read ${new Date(item.raw.readAt).toLocaleTimeString()}`);
            }
            if (item.raw.expiresAt) {
              metaParts.push(`expires ${new Date(item.raw.expiresAt).toLocaleTimeString()}`);
            }
            if (item.failed) {
              metaParts.push("undecryptable");
            }
            return (
              <Pressable
                delayLongPress={250}
                onLongPress={handleRestrictedActionNotice}
                style={[styles.messageBubble, isMine ? styles.ownBubble : styles.peerBubble]}
              >
                {item.text ? (
                  <Text selectable={false} style={[styles.messageText, isMine && styles.ownMessageText]}>
                    {item.text}
                  </Text>
                ) : null}
                {item.attachments.length > 0 ? (
                  <View style={styles.attachmentsColumn}>
                    {item.attachments.map((attachment) => {
                      const localImageUri = decryptedAttachmentUris[attachment.attachmentId] ?? null;
                      const imageVisible = attachment.kind === "IMAGE" && !!localImageUri;
                      if (attachment.kind === "VOICE") {
                        return (
                          <Pressable
                            key={attachment.attachmentId}
                            delayLongPress={250}
                            onLongPress={handleRestrictedActionNotice}
                            onPress={() => void handleToggleVoicePlayback(attachment)}
                            style={styles.voiceCard}
                          >
                            <Text style={styles.voiceTitle}>Secret voice note</Text>
                            <Text style={styles.voiceMeta}>
                              {formatDuration(attachment.durationMs)} - {formatFileSize(attachment.fileSizeBytes)}
                            </Text>
                            <Text style={styles.voiceMeta}>
                              {playingVoiceAttachmentId === attachment.attachmentId ? "Stop" : "Play"}
                            </Text>
                          </Pressable>
                        );
                      }
                      if (attachment.kind === "VIDEO") {
                        return (
                          <Pressable
                            key={attachment.attachmentId}
                            onPress={() => void handleOpenAttachment(attachment)}
                            style={styles.videoNoteCard}
                          >
                            <View style={styles.videoNoteCircle}>
                              <Text style={styles.videoNotePlay}>Play</Text>
                            </View>
                            <Text style={styles.videoNoteTitle}>Secret video note</Text>
                            <Text style={styles.videoNoteMeta}>
                              {attachment.durationMs
                                ? `${formatDuration(attachment.durationMs)} - ${formatFileSize(attachment.fileSizeBytes)}`
                                : formatFileSize(attachment.fileSizeBytes)}
                            </Text>
                            <Text style={styles.videoNoteMeta}>
                              {openingAttachmentId === attachment.attachmentId ? "Opening..." : "Open video"}
                            </Text>
                          </Pressable>
                        );
                      }
                      return (
                        <Pressable
                          key={attachment.attachmentId}
                          delayLongPress={250}
                          onLongPress={handleRestrictedActionNotice}
                          onPress={() => void handleOpenAttachment(attachment)}
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
                          <Text style={styles.attachmentMeta}>
                            {attachment.contentType} - {formatFileSize(attachment.fileSizeBytes)}
                          </Text>
                          <Text style={styles.attachmentMeta}>
                            {openingAttachmentId === attachment.attachmentId
                              ? "Opening..."
                              : attachment.kind === "IMAGE"
                                ? imageVisible ? "View photo" : "Decrypt photo"
                                : "Decrypt only"}
                          </Text>
                        </Pressable>
                      );
                    })}
                  </View>
                ) : null}
                <Text style={[styles.messageMeta, isMine && styles.ownMessageMeta]}>
                  {metaParts.join(" - ")}
                </Text>
              </Pressable>
            );
          }}
        />

        <Modal
          animationType="fade"
          onRequestClose={() => setFocusedImage(null)}
          transparent
          visible={!!focusedImage}
        >
          <Pressable onLongPress={handleRestrictedActionNotice} style={styles.imageModalBackdrop}>
            <View style={styles.imageModalCard}>
              {focusedImage ? (
                <>
                  <Image
                    resizeMode="contain"
                    source={{ uri: focusedImage.uri }}
                    style={styles.focusedImage}
                  />
                  <Text style={styles.focusedImageName}>{focusedImage.name}</Text>
                </>
              ) : null}
              <Pressable onPress={() => setFocusedImage(null)} style={styles.modalCloseButton}>
                <Text style={styles.modalCloseButtonText}>Close</Text>
              </Pressable>
            </View>
          </Pressable>
        </Modal>

        <Modal
          animationType="fade"
          onRequestClose={() => {
            setFocusedVideoPlaying(false);
            setFocusedVideo(null);
          }}
          transparent
          visible={!!focusedVideo}
        >
          <Pressable onLongPress={handleRestrictedActionNotice} style={styles.imageModalBackdrop}>
            <View style={styles.imageModalCard}>
              {focusedVideo ? (
                <>
                  <Pressable
                    onPress={() => setFocusedVideoPlaying((current) => !current)}
                    style={styles.focusedVideoFrame}
                  >
                    <Video
                      isLooping
                      resizeMode={ResizeMode.COVER}
                      shouldPlay={focusedVideoPlaying}
                      source={{ uri: focusedVideo.uri }}
                      style={styles.focusedVideo}
                    />
                    <View style={styles.videoOverlayBadge}>
                      <Text style={styles.videoOverlayBadgeText}>
                        {focusedVideoPlaying ? "Pause" : "Play"}
                      </Text>
                    </View>
                  </Pressable>
                  <Text style={styles.focusedImageName}>{focusedVideo.name}</Text>
                  <Text style={styles.focusedVideoHint}>
                    Local-only playback. Saving and forwarding are disabled.
                  </Text>
                </>
              ) : null}
              <Pressable
                onPress={() => {
                  setFocusedVideoPlaying(false);
                  setFocusedVideo(null);
                }}
                style={styles.modalCloseButton}
              >
                <Text style={styles.modalCloseButtonText}>Close</Text>
              </Pressable>
            </View>
          </Pressable>
        </Modal>

        {pendingAttachments.length > 0 ? (
          <View style={styles.pendingBar}>
            <Text style={styles.pendingTitle}>Pending encrypted attachments</Text>
            <View style={styles.pendingList}>
              {pendingAttachments.map((attachment) => (
                <Pressable
                  key={attachment.attachmentId}
                  delayLongPress={250}
                  onLongPress={handleRestrictedActionNotice}
                  style={styles.pendingChip}
                >
                  {attachment.kind === "IMAGE" && attachment.previewUri ? (
                    <Image source={{ uri: attachment.previewUri }} style={styles.pendingImagePreview} />
                  ) : null}
                  <View style={styles.pendingTextBlock}>
                    <Text style={styles.pendingName}>
                      {attachment.kind === "VOICE"
                        ? "Secret voice note"
                        : attachment.kind === "VIDEO"
                          ? "Secret video note"
                        : attachment.kind === "IMAGE"
                          ? "Photo"
                          : attachment.originalFileName}
                    </Text>
                    <Text style={styles.pendingMeta}>
                      {attachment.kind === "VOICE"
                        ? `${formatDuration(attachment.durationMs)} - ${formatFileSize(attachment.fileSizeBytes)}`
                        : attachment.kind === "VIDEO"
                          ? attachment.durationMs
                            ? `${formatDuration(attachment.durationMs)} - ${formatFileSize(attachment.fileSizeBytes)}`
                            : formatFileSize(attachment.fileSizeBytes)
                        : formatFileSize(attachment.fileSizeBytes)}
                    </Text>
                  </View>
                  <Pressable
                    onPress={() => void handleRemovePendingAttachment(attachment)}
                    style={styles.inlineDangerButton}
                  >
                    <Text style={styles.inlineDangerText}>Remove</Text>
                  </Pressable>
                </Pressable>
              ))}
            </View>
          </View>
        ) : null}

        {secretChat.status !== "ACTIVE" ? (
          <View style={styles.pendingBar}>
            <Text style={styles.pendingText}>
              {secretChat.direction === "OUTGOING"
                ? "Messages are disabled until the peer accepts this device-bound secret chat."
                : "Accept this secret chat from the previous screen to bind it to this device."}
            </Text>
          </View>
        ) : (
          <View style={styles.composer}>
            <Pressable
              disabled={uploadingAttachments || sending || recordingVoice}
              onPress={() => void handlePickAttachments("FILE", "*/*")}
              style={[styles.secondaryButton, (uploadingAttachments || sending || recordingVoice) && styles.disabled]}
            >
              <Text style={styles.secondaryButtonText}>{uploadingAttachments ? "..." : "File/Video"}</Text>
            </Pressable>
            <Pressable
              disabled={uploadingAttachments || sending || recordingVoice}
              onPress={() => void handlePickAttachments("IMAGE", "image/*")}
              style={[styles.secondaryButton, (uploadingAttachments || sending || recordingVoice) && styles.disabled]}
            >
              <Text style={styles.secondaryButtonText}>Photo</Text>
            </Pressable>
            <Pressable
              disabled={uploadingAttachments || sending}
              onPress={() => void (recordingVoice ? handleStopVoiceRecording(false) : handleStartVoiceRecording())}
              style={[styles.secondaryButton, (uploadingAttachments || sending) && styles.disabled]}
            >
              <Text style={styles.secondaryButtonText}>{recordingVoice ? "Send voice" : "Mic"}</Text>
            </Pressable>
            <TextInput
              editable={!sending && !uploadingAttachments && !recordingVoice}
              multiline
              onChangeText={setDraft}
              placeholder="Type an end-to-end encrypted message"
              style={[styles.input, styles.composerInput]}
              value={draft}
            />
            <Pressable
              disabled={sending || uploadingAttachments || recordingVoice || (!draft.trim() && pendingAttachments.length === 0)}
              onPress={() => void handleSend()}
              style={[
                styles.primaryButton,
                (sending || uploadingAttachments || recordingVoice || (!draft.trim() && pendingAttachments.length === 0)) && styles.disabled
              ]}
            >
              <Text style={styles.primaryButtonText}>{sending ? "..." : "Send"}</Text>
            </Pressable>
          </View>
        )}
        {recordingVoice ? (
          <View style={styles.recordingBar}>
            <Text style={styles.infoTitle}>Recording secret voice note</Text>
            <Text style={styles.infoText}>{formatDuration(recordingDurationMs)}</Text>
            <View style={styles.timerRow}>
              <Pressable onPress={() => void handleStopVoiceRecording(false)} style={styles.secondaryButton}>
                <Text style={styles.secondaryButtonText}>Send</Text>
              </Pressable>
              <Pressable onPress={() => void handleStopVoiceRecording(true)} style={styles.inlineDangerButton}>
                <Text style={styles.inlineDangerText}>Discard</Text>
              </Pressable>
            </View>
          </View>
        ) : null}
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: "#f8fafc"
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 12
  },
  headerText: {
    flex: 1
  },
  title: {
    color: "#0f172a",
    fontSize: 20,
    fontWeight: "700"
  },
  subtitle: {
    color: "#64748b",
    marginTop: 2
  },
  secondaryButton: {
    borderRadius: 12,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 14,
    paddingVertical: 10
  },
  secondaryButtonText: {
    color: "#0f172a",
    fontWeight: "600"
  },
  dangerButton: {
    borderRadius: 12,
    backgroundColor: "#fee2e2",
    paddingHorizontal: 14,
    paddingVertical: 10
  },
  dangerButtonText: {
    color: "#b91c1c",
    fontWeight: "700"
  },
  disabled: {
    opacity: 0.6
  },
  infoCard: {
    marginHorizontal: 16,
    marginBottom: 8,
    borderRadius: 16,
    backgroundColor: "#ecfccb",
    padding: 14,
    gap: 6
  },
  infoTitle: {
    color: "#365314",
    fontWeight: "700"
  },
  infoText: {
    color: "#3f6212"
  },
  warningText: {
    color: "#b91c1c",
    lineHeight: 20
  },
  timerRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginTop: 4
  },
  timerChip: {
    borderRadius: 999,
    backgroundColor: "#d9f99d",
    paddingHorizontal: 12,
    paddingVertical: 8
  },
  timerChipActive: {
    backgroundColor: "#0f172a"
  },
  timerChipText: {
    color: "#365314",
    fontWeight: "700"
  },
  timerChipTextActive: {
    color: "#ffffff"
  },
  errorText: {
    color: "#b91c1c",
    paddingHorizontal: 20,
    paddingBottom: 8
  },
  noticeText: {
    color: "#92400e",
    backgroundColor: "#fef3c7",
    marginHorizontal: 16,
    marginBottom: 8,
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 10
  },
  messagesContent: {
    paddingHorizontal: 20,
    paddingVertical: 16,
    gap: 10
  },
  messageBubble: {
    maxWidth: "84%",
    borderRadius: 18,
    paddingHorizontal: 14,
    paddingVertical: 10
  },
  ownBubble: {
    alignSelf: "flex-end",
    backgroundColor: "#0f172a"
  },
  peerBubble: {
    alignSelf: "flex-start",
    backgroundColor: "#ffffff"
  },
  messageText: {
    color: "#0f172a"
  },
  ownMessageText: {
    color: "#ffffff"
  },
  messageMeta: {
    marginTop: 6,
    color: "#64748b",
    fontSize: 11
  },
  ownMessageMeta: {
    color: "#cbd5e1"
  },
  attachmentsColumn: {
    gap: 8,
    marginTop: 8
  },
  imageCard: {
    gap: 8,
    marginTop: 4
  },
  imageAttachment: {
    width: 220,
    height: 220,
    borderRadius: 18,
    backgroundColor: "#dbeafe"
  },
  attachmentCard: {
    borderRadius: 12,
    backgroundColor: "#eff6ff",
    paddingHorizontal: 10,
    paddingVertical: 8
  },
  attachmentPlaceholder: {
    width: 220,
    height: 140,
    borderRadius: 16,
    backgroundColor: "#dbeafe",
    alignItems: "center",
    justifyContent: "center"
  },
  attachmentPlaceholderText: {
    color: "#1d4ed8",
    fontWeight: "700"
  },
  attachmentName: {
    color: "#1e3a8a",
    fontWeight: "700"
  },
  attachmentMeta: {
    color: "#475569",
    fontSize: 12,
    marginTop: 2
  },
  voiceCard: {
    borderRadius: 14,
    backgroundColor: "#dcfce7",
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  voiceTitle: {
    color: "#166534",
    fontWeight: "700"
  },
  voiceMeta: {
    color: "#166534",
    fontSize: 12,
    marginTop: 4
  },
  videoNoteCard: {
    alignItems: "center",
    gap: 8,
    marginTop: 4
  },
  videoNoteCircle: {
    width: 148,
    height: 148,
    borderRadius: 74,
    backgroundColor: "#dbeafe",
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 3,
    borderColor: "#38bdf8"
  },
  videoNotePlay: {
    color: "#0369a1",
    fontWeight: "800"
  },
  videoNoteTitle: {
    color: "#0f172a",
    fontWeight: "700"
  },
  videoNoteMeta: {
    color: "#475569",
    fontSize: 12
  },
  pendingBar: {
    borderTopWidth: 1,
    borderTopColor: "#e2e8f0",
    backgroundColor: "#ffffff",
    paddingHorizontal: 16,
    paddingVertical: 14
  },
  pendingText: {
    color: "#64748b",
    lineHeight: 20
  },
  pendingTitle: {
    color: "#0f172a",
    fontWeight: "700",
    marginBottom: 10
  },
  pendingList: {
    gap: 8
  },
  pendingChip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    borderRadius: 14,
    backgroundColor: "#eff6ff",
    paddingHorizontal: 10,
    paddingVertical: 8
  },
  pendingImagePreview: {
    width: 44,
    height: 44,
    borderRadius: 10,
    backgroundColor: "#dbeafe"
  },
  pendingTextBlock: {
    flex: 1
  },
  pendingName: {
    color: "#1e3a8a",
    fontWeight: "700"
  },
  pendingMeta: {
    color: "#475569",
    fontSize: 12,
    marginTop: 2
  },
  inlineDangerButton: {
    borderRadius: 10,
    backgroundColor: "#fee2e2",
    paddingHorizontal: 10,
    paddingVertical: 8
  },
  inlineDangerText: {
    color: "#b91c1c",
    fontWeight: "700"
  },
  recordingBar: {
    borderTopWidth: 1,
    borderTopColor: "#e2e8f0",
    backgroundColor: "#eff6ff",
    paddingHorizontal: 16,
    paddingBottom: 16
  },
  composer: {
    flexDirection: "row",
    alignItems: "flex-end",
    gap: 12,
    borderTopWidth: 1,
    borderTopColor: "#e2e8f0",
    backgroundColor: "#ffffff",
    padding: 16
  },
  input: {
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 14,
    backgroundColor: "#ffffff",
    paddingHorizontal: 14,
    paddingVertical: 12
  },
  composerInput: {
    flex: 1,
    minHeight: 48,
    maxHeight: 120
  },
  imageModalBackdrop: {
    flex: 1,
    backgroundColor: "rgba(15, 23, 42, 0.92)",
    alignItems: "center",
    justifyContent: "center",
    padding: 24
  },
  imageModalCard: {
    width: "100%",
    alignItems: "center",
    gap: 12
  },
  focusedImage: {
    width: "92%",
    maxWidth: 420,
    height: "72%",
    borderRadius: 20,
    backgroundColor: "#020617"
  },
  focusedVideoFrame: {
    width: "92%",
    maxWidth: 360,
    aspectRatio: 1,
    borderRadius: 999,
    overflow: "hidden",
    backgroundColor: "#020617"
  },
  focusedVideo: {
    width: "100%",
    height: "100%"
  },
  videoOverlayBadge: {
    position: "absolute",
    bottom: 14,
    alignSelf: "center",
    borderRadius: 999,
    backgroundColor: "rgba(15, 23, 42, 0.72)",
    paddingHorizontal: 14,
    paddingVertical: 8
  },
  videoOverlayBadgeText: {
    color: "#f8fafc",
    fontWeight: "700"
  },
  focusedImageName: {
    color: "#e2e8f0",
    fontWeight: "700"
  },
  focusedVideoHint: {
    color: "#cbd5e1",
    textAlign: "center"
  },
  modalCloseButton: {
    borderRadius: 14,
    backgroundColor: "#ffffff",
    paddingHorizontal: 16,
    paddingVertical: 12
  },
  modalCloseButtonText: {
    color: "#0f172a",
    fontWeight: "700"
  },
  primaryButton: {
    borderRadius: 14,
    backgroundColor: "#0f172a",
    paddingHorizontal: 16,
    paddingVertical: 14,
    alignItems: "center",
    justifyContent: "center"
  },
  primaryButtonText: {
    color: "#ffffff",
    fontWeight: "700"
  }
});
