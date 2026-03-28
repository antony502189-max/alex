import { useCallback, useEffect } from "react";
import { Audio } from "expo-av";
import { attachmentTransfers } from "../../services/attachmentTransfers";
import type { MessageAttachment } from "../../types";
import { isAudioAttachment, isQueuedUploadAttachment } from "./chatAttachmentHelpers";

type UploadVoiceAttachment = (params: {
  uri: string;
  name: string;
  contentType?: string;
  kind: "VOICE";
  durationMs?: number;
  waveform?: number[] | null;
}) => Promise<MessageAttachment>;

type UseChatVoiceControlsParams = {
  canPost: boolean;
  editingMessageId: string | null;
  playingVoiceAttachmentId: string | null;
  recordingDurationMs: number;
  recordingRef: React.MutableRefObject<Audio.Recording | null>;
  recordingVoice: boolean;
  recordingWaveformSamplesRef: React.MutableRefObject<number[]>;
  setError: (value: string | null) => void;
  setPendingAttachments: React.Dispatch<React.SetStateAction<MessageAttachment[]>>;
  setPlayingVoiceAttachmentId: React.Dispatch<React.SetStateAction<string | null>>;
  setRecordingDurationMs: React.Dispatch<React.SetStateAction<number>>;
  setRecordingVoice: React.Dispatch<React.SetStateAction<boolean>>;
  setUploadingAttachments: React.Dispatch<React.SetStateAction<boolean>>;
  soundRef: React.MutableRefObject<Audio.Sound | null>;
  token: string;
  uploadingAttachments: boolean;
  uploadOrStageAttachment: UploadVoiceAttachment;
};

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
  const normalized = (metering + 60) / 60;
  return Math.max(0, Math.min(100, Math.round(normalized * 100)));
}

export function useChatVoiceControls({
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
}: UseChatVoiceControlsParams) {
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
  }, [
    recordingRef,
    setPlayingVoiceAttachmentId,
    setRecordingDurationMs,
    setRecordingVoice,
    soundRef
  ]);

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
  }, [recordingRef, recordingVoice, recordingWaveformSamplesRef, setRecordingDurationMs]);

  const handleStartVoiceRecording = useCallback(async () => {
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
  }, [
    canPost,
    editingMessageId,
    recordingRef,
    recordingVoice,
    recordingWaveformSamplesRef,
    setError,
    setRecordingDurationMs,
    setRecordingVoice,
    uploadingAttachments
  ]);

  const handleStopVoiceRecording = useCallback(async (cancel = false) => {
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
  }, [
    recordingDurationMs,
    recordingRef,
    recordingWaveformSamplesRef,
    setError,
    setPendingAttachments,
    setRecordingDurationMs,
    setRecordingVoice,
    setUploadingAttachments,
    uploadOrStageAttachment
  ]);

  const handleToggleVoicePlayback = useCallback(async (attachment: MessageAttachment) => {
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
  }, [
    playingVoiceAttachmentId,
    setError,
    setPlayingVoiceAttachmentId,
    soundRef,
    token
  ]);

  return {
    handleStartVoiceRecording,
    handleStopVoiceRecording,
    handleToggleVoicePlayback
  };
}
