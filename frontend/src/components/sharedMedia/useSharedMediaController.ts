import { useEffect, useMemo, useRef, useState } from "react";
import { Audio } from "expo-av";
import * as Sharing from "expo-sharing";
import { Linking } from "react-native";
import { parseAlexDeepLink, type ParsedDeepLink } from "../../navigation/deepLinks";
import { attachmentTransfers } from "../../services/attachmentTransfers";
import { api } from "../../services/api";
import { useMediaStore } from "../../store/useMediaStore";
import type {
  ChatSummary,
  MessageAttachment,
  SharedMediaBuckets
} from "../../types";
import {
  buildSharedMediaBuckets,
  isAudioAttachment,
  normalizeSharedMediaLinkUrl
} from "./sharedMediaPresentation";

type UseSharedMediaControllerParams = {
  chat: ChatSummary;
  onOpenParsedLink?: (parsedLink: ParsedDeepLink) => void;
  token: string;
};

export function useSharedMediaController({
  chat,
  onOpenParsedLink,
  token
}: UseSharedMediaControllerParams) {
  const cachedBuckets = useMediaStore((state) => state.bucketsByChatId[chat.chatId] ?? null);
  const setBuckets = useMediaStore((state) => state.setBuckets);

  const [buckets, setLocalBuckets] = useState<SharedMediaBuckets | null>(cachedBuckets);
  const [loading, setLoading] = useState(!cachedBuckets);
  const [refreshing, setRefreshing] = useState(false);
  const [openingAttachmentId, setOpeningAttachmentId] = useState<string | null>(null);
  const [loadingAudioAttachmentId, setLoadingAudioAttachmentId] = useState<string | null>(null);
  const [playingAudioAttachmentId, setPlayingAudioAttachmentId] = useState<string | null>(null);
  const [openingLinkId, setOpeningLinkId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const soundRef = useRef<Audio.Sound | null>(null);

  useEffect(() => {
    setLocalBuckets(cachedBuckets);
    setLoading(!cachedBuckets);
  }, [cachedBuckets]);

  useEffect(() => {
    return () => {
      const cleanup = async () => {
        if (!soundRef.current) {
          return;
        }

        try {
          await soundRef.current.unloadAsync();
        } catch {
        } finally {
          soundRef.current = null;
          setPlayingAudioAttachmentId(null);
        }
      };

      void cleanup();
    };
  }, []);

  async function loadBuckets(showLoader = false) {
    if (showLoader) {
      setLoading(true);
    } else {
      setRefreshing(true);
    }
    setError(null);

    try {
      const messages = await api.getMessages(token, chat.chatId, 120);
      const nextBuckets = buildSharedMediaBuckets(chat.chatId, messages);
      setBuckets(chat.chatId, nextBuckets);
      setLocalBuckets(nextBuckets);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load shared media");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  useEffect(() => {
    void loadBuckets(!cachedBuckets);
  }, [chat.chatId, token]);

  const mediaAttachments = useMemo(
    () => buckets?.media.map((entry) => entry.attachment) ?? [],
    [buckets]
  );

  async function handleRefresh() {
    await loadBuckets(false);
  }

  async function handleOpenFileAttachment(attachment: MessageAttachment) {
    setOpeningAttachmentId(attachment.attachmentId);
    setError(null);

    try {
      const uri = await attachmentTransfers.downloadAttachment(token, attachment);
      if (!uri) {
        return;
      }

      if (await Sharing.isAvailableAsync()) {
        await Sharing.shareAsync(uri);
        return;
      }

      setError("Sharing is not available on this platform.");
    } catch (openError) {
      setError(openError instanceof Error ? openError.message : "Unable to open attachment");
    } finally {
      setOpeningAttachmentId(null);
    }
  }

  async function handleToggleAudioAttachment(attachment: MessageAttachment) {
    if (!isAudioAttachment(attachment)) {
      return;
    }

    setError(null);
    try {
      if (playingAudioAttachmentId === attachment.attachmentId && soundRef.current) {
        await soundRef.current.unloadAsync();
        soundRef.current = null;
        setPlayingAudioAttachmentId(null);
        return;
      }

      setLoadingAudioAttachmentId(attachment.attachmentId);

      if (soundRef.current) {
        await soundRef.current.unloadAsync();
        soundRef.current = null;
        setPlayingAudioAttachmentId(null);
      }

      const uri = attachment.localUri ?? await attachmentTransfers.downloadAttachment(token, attachment);
      if (!uri) {
        return;
      }

      const { sound } = await Audio.Sound.createAsync(
        { uri },
        { shouldPlay: true }
      );
      soundRef.current = sound;
      setPlayingAudioAttachmentId(attachment.attachmentId);
      sound.setOnPlaybackStatusUpdate((status) => {
        if ("isLoaded" in status && status.isLoaded && status.didJustFinish) {
          void sound.unloadAsync();
          if (soundRef.current === sound) {
            soundRef.current = null;
          }
          setPlayingAudioAttachmentId(null);
        }
      });
    } catch (playbackError) {
      setError(playbackError instanceof Error ? playbackError.message : "Unable to play audio");
      setPlayingAudioAttachmentId(null);
      if (soundRef.current) {
        try {
          await soundRef.current.unloadAsync();
        } catch {
        }
        soundRef.current = null;
      }
    } finally {
      setLoadingAudioAttachmentId(null);
    }
  }

  async function handleOpenLink(linkId: string, url: string) {
    setOpeningLinkId(linkId);
    setError(null);

    try {
      const parsedLink = parseAlexDeepLink(url);
      if (parsedLink && onOpenParsedLink) {
        onOpenParsedLink(parsedLink);
        return;
      }

      await Linking.openURL(normalizeSharedMediaLinkUrl(url));
    } catch (linkError) {
      setError(linkError instanceof Error ? linkError.message : "Unable to open link");
    } finally {
      setOpeningLinkId(null);
    }
  }

  return {
    buckets,
    error,
    handleOpenFileAttachment,
    handleOpenLink,
    handleRefresh,
    handleToggleAudioAttachment,
    loading,
    loadingAudioAttachmentId,
    mediaAttachments,
    openingAttachmentId,
    openingLinkId,
    playingAudioAttachmentId,
    refreshing
  };
}

export type SharedMediaScreenController = ReturnType<typeof useSharedMediaController>;
