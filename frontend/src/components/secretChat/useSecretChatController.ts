import { useEffect, useMemo, useRef, useState } from "react";
import * as DocumentPicker from "expo-document-picker";
import { Audio } from "expo-av";
import * as ScreenCapture from "expo-screen-capture";
import { Platform } from "react-native";
import { api } from "../../services/api";
import {
  cleanupPreparedSecretAttachment,
  ensureDecryptedSecretAttachment,
  getCachedSecretAttachmentUri,
  prepareSecretAttachmentUpload,
  removePendingSecretAttachment,
  type PendingSecretAttachmentDraft,
  uploadPreparedSecretAttachment
} from "../../services/secretChatAttachments";
import { localDatabase } from "../../services/localDatabase";
import { secretChatCrypto } from "../../services/secretChatCrypto";
import { secretChatLocalCleanup } from "../../services/secretChatLocalCleanup";
import { wsService } from "../../services/ws";
import type {
  SecretChatAttachment,
  SecretChatInboxEvent,
  SecretChatMessage,
  SecretChatPayload,
  SecretChatReadEvent,
  SecretChatScreenshotEvent,
  SecretChatSummary
} from "../../types";
import {
  buildSecretChatStatusText,
  filterVisibleSecretMessages,
  inferSecretAttachmentKind,
  mergeSecretChatMessages,
  type ResolvedSecretChatMessage
} from "./secretChatPresentation";

type FocusedSecretMedia = {
  name: string;
  uri: string;
} | null;

type UseSecretChatControllerParams = {
  currentUserId: string;
  onBack: () => void;
  onSummaryChange: (secretChat: SecretChatSummary) => void;
  secretChat: SecretChatSummary;
  token: string;
};

const SECRET_SCREEN_CAPTURE_KEY = "alex-secret-chat";

async function cleanupPendingUploads(
  token: string,
  attachments: PendingSecretAttachmentDraft[]
) {
  await Promise.allSettled(
    attachments.map((attachment) => removePendingSecretAttachment(token, attachment))
  );
}

async function resolveMessages(
  secretChat: SecretChatSummary,
  messages: SecretChatMessage[]
): Promise<ResolvedSecretChatMessage[]> {
  return Promise.all(
    filterVisibleSecretMessages(messages).map(async (message) => {
      try {
        const payload = await secretChatCrypto.decryptPayload(secretChat, message);
        return {
          attachments: payload.attachments,
          failed: false,
          raw: message,
          text: payload.text
        };
      } catch {
        return {
          attachments: [],
          failed: true,
          raw: message,
          text: "[Unable to decrypt on this device]"
        };
      }
    })
  );
}

export function useSecretChatController({
  currentUserId,
  onBack,
  onSummaryChange,
  secretChat,
  token
}: UseSecretChatControllerParams) {
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
  const [focusedImage, setFocusedImage] = useState<FocusedSecretMedia>(null);
  const [focusedVideo, setFocusedVideo] = useState<FocusedSecretMedia>(null);
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

  const statusText = useMemo(() => buildSecretChatStatusText(secretChat), [secretChat]);
  const active = secretChat.status === "ACTIVE";
  const sendDisabled =
    sending ||
    uploadingAttachments ||
    recordingVoice ||
    (!draft.trim() && pendingAttachments.length === 0);

  function applyReadEvent(event: SecretChatReadEvent) {
    if (!event.messageIds.length) {
      return;
    }

    setMessages((current) => {
      const next = current.map((message) =>
        event.messageIds.includes(message.secretMessageId)
          ? {
              ...message,
              expiresAt: event.expiresAt,
              readAt: event.readAt
            }
          : message
      );
      void localDatabase
        .upsertSecretChatMessages(
          currentUserId,
          next.filter((message) => event.messageIds.includes(message.secretMessageId))
        )
        .catch(() => undefined);
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

  function closeFocusedImage() {
    setFocusedImage(null);
  }

  function closeFocusedVideo() {
    setFocusedVideoPlaying(false);
    setFocusedVideo(null);
  }

  function toggleFocusedVideoPlayback() {
    setFocusedVideoPlaying((current) => !current);
  }

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

  useEffect(() => {
    if (!["DECLINED", "CLOSED"].includes(secretChat.status)) {
      return;
    }

    let activeEffect = true;
    void secretChatLocalCleanup
      .purgeSecretChat(currentUserId, secretChat.secretChatId)
      .finally(() => {
        if (activeEffect) {
          onBack();
        }
      });

    return () => {
      activeEffect = false;
    };
  }, [currentUserId, onBack, secretChat.secretChatId, secretChat.status]);

  useEffect(() => {
    let cancelled = false;

    async function loadSecretChat() {
      setError(null);
      try {
        const cachedMessages = await localDatabase.getSecretChatMessages(
          currentUserId,
          secretChat.secretChatId
        );
        if (!cancelled && cachedMessages.length > 0) {
          setMessages(cachedMessages);
        }

        const nextMessages = await api.getSecretChatMessages(token, secretChat.secretChatId, 100);
        if (!cancelled) {
          setMessages(nextMessages);
          await localDatabase.replaceSecretChatMessages(
            currentUserId,
            secretChat.secretChatId,
            nextMessages
          );
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
  }, [currentUserId, onBack, onSummaryChange, secretChat.secretChatId, token]);

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
      void recordingRef.current
        ?.getStatusAsync()
        .then((status) => {
          if ("isRecording" in status && status.isRecording && typeof status.durationMillis === "number") {
            setRecordingDurationMs(status.durationMillis);
          }
        })
        .catch(() => undefined);
    }, 250);

    return () => {
      clearInterval(intervalId);
    };
  }, [recordingVoice]);

  useEffect(() => {
    const visibleMessages = filterVisibleSecretMessages(messages);
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

    void secretChatCrypto
      .deriveStoredFingerprint(secretChat)
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
    void api
      .markSecretChatRead(token, secretChat.secretChatId)
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

    let activeEffect = true;
    let subscription: ScreenCapture.Subscription | null = null;

    void (async () => {
      try {
        await ScreenCapture.preventScreenCaptureAsync(SECRET_SCREEN_CAPTURE_KEY);
        if (!activeEffect) {
          await ScreenCapture.allowScreenCaptureAsync(SECRET_SCREEN_CAPTURE_KEY).catch(() => undefined);
          return;
        }

        const currentPermission = await ScreenCapture.getPermissionsAsync();
        if (!activeEffect) {
          return;
        }
        if (!currentPermission.granted) {
          const requestedPermission = await ScreenCapture.requestPermissionsAsync();
          if (!activeEffect || !requestedPermission.granted) {
            return;
          }
        }

        if (Platform.OS === "ios") {
          await ScreenCapture.enableAppSwitcherProtectionAsync();
        }
        if (!activeEffect) {
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
      activeEffect = false;
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
        const uri =
          cachedUri ?? (await ensureDecryptedSecretAttachment(token, secretChat, attachment));
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
        attachments: pendingAttachments.map((attachment) => ({
          attachmentId: attachment.attachmentId,
          contentType: attachment.contentType,
          durationMs: attachment.durationMs ?? null,
          fileNonce: attachment.fileNonce,
          fileSizeBytes: attachment.fileSizeBytes,
          kind: attachment.kind,
          originalFileName: attachment.originalFileName
        })),
        text: normalizedDraft || null,
        version: 1
      };
      const encrypted = await secretChatCrypto.encryptPayload(secretChat, payload);
      const message = await api.sendSecretChatMessage(token, secretChat.secretChatId, {
        attachmentIds: pendingAttachments.map((attachment) => attachment.attachmentId),
        ciphertext: encrypted.ciphertextBase64,
        nonce: encrypted.nonce
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
          contentType: "audio/mp4",
          durationMs: Math.max(durationMs, 1),
          kind: "VOICE",
          name: `secret-voice-${Date.now()}.m4a`,
          uri
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
        copyToCacheDirectory: true,
        multiple: true,
        type: mimeType
      });

      if (selection.canceled || selection.assets.length === 0) {
        return;
      }

      const nextAttachments: PendingSecretAttachmentDraft[] = [];
      for (const asset of selection.assets) {
        const attachmentKind = inferSecretAttachmentKind(kind, asset.name, asset.mimeType);
        const preparedAttachment = await prepareSecretAttachmentUpload(secretChat, {
          contentType:
            asset.mimeType ??
            (attachmentKind === "IMAGE"
              ? "image/jpeg"
              : attachmentKind === "VIDEO"
                ? "video/mp4"
                : "application/octet-stream"),
          kind: attachmentKind,
          name:
            asset.name ??
            (attachmentKind === "IMAGE"
              ? "photo.jpg"
              : attachmentKind === "VIDEO"
                ? "video.mp4"
                : "file"),
          uri: asset.uri
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
      setError(
        attachmentError instanceof Error
          ? attachmentError.message
          : "Unable to upload secret attachment"
      );
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
          name: attachment.originalFileName,
          uri
        });
      } else if (attachment.kind === "VIDEO") {
        setFocusedImage(null);
        setFocusedVideo({
          name: attachment.originalFileName,
          uri
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
      const { sound } = await Audio.Sound.createAsync({ uri }, { shouldPlay: true });
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
      setError(
        playbackError instanceof Error ? playbackError.message : "Unable to play secret voice note"
      );
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

  return {
    active,
    activityNotice,
    closing,
    closeFocusedImage,
    closeFocusedVideo,
    decryptedAttachmentUris,
    draft,
    error,
    fingerprintMismatch,
    focusedImage,
    focusedVideo,
    focusedVideoPlaying,
    handleCloseChat,
    handleOpenAttachment,
    handlePickAttachments,
    handleRemovePendingAttachment,
    handleRestrictedActionNotice,
    handleSend,
    handleStartVoiceRecording,
    handleStopVoiceRecording,
    handleToggleVoicePlayback,
    handleUpdateTimer,
    localFingerprint,
    openingAttachmentId,
    pendingAttachments,
    playingVoiceAttachmentId,
    recordingDurationMs,
    recordingVoice,
    resolvedMessages,
    secretChat,
    sendDisabled,
    sending,
    setDraft,
    statusText,
    toggleFocusedVideoPlayback,
    updatingTimer,
    uploadingAttachments
  };
}

export type SecretChatScreenController = ReturnType<typeof useSecretChatController>;
