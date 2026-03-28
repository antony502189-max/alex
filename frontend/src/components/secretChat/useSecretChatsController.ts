import { useEffect, useMemo, useState } from "react";
import { api } from "../../services/api";
import { localDatabase } from "../../services/localDatabase";
import { secretChatCrypto } from "../../services/secretChatCrypto";
import { secretChatLocalCleanup } from "../../services/secretChatLocalCleanup";
import { wsService } from "../../services/ws";
import type { SecretChatInboxEvent, SecretChatSummary } from "../../types";
import {
  removeSecretChat,
  sortSecretChats,
  upsertSecretChat
} from "./secretChatsPresentation";

type UseSecretChatsControllerParams = {
  currentSessionId: string;
  currentUserId: string;
  onOpenSecretChat: (chat: SecretChatSummary) => void;
  seedPeerUserId?: string | null;
  token: string;
};

export function useSecretChatsController({
  currentSessionId,
  currentUserId,
  onOpenSecretChat,
  seedPeerUserId,
  token
}: UseSecretChatsControllerParams) {
  const [secretChats, setSecretChats] = useState<SecretChatSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [handlingChatId, setHandlingChatId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function loadSecretChats() {
    setLoading(true);
    setError(null);
    try {
      const retainedSecretChats = sortSecretChats(
        await secretChatLocalCleanup.pruneSecretChats(currentUserId, await api.getSecretChats(token))
      );
      setSecretChats(retainedSecretChats);
      await localDatabase.replaceSecretChats(currentUserId, retainedSecretChats);
    } catch (loadError) {
      setError(
        secretChats.length > 0
          ? "Offline mode. Showing cached secret chats."
          : loadError instanceof Error
            ? loadError.message
            : "Unable to load secret chats"
      );
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void localDatabase
      .getSecretChats(currentUserId)
      .then((cachedSecretChats) => {
        if (cachedSecretChats.length > 0) {
          setSecretChats(sortSecretChats(cachedSecretChats));
        }
      })
      .catch(() => undefined);

    void loadSecretChats();

    const unsubscribe = wsService.subscribe("/user/queue/secret-chats", (payload) => {
      const event = JSON.parse(payload) as SecretChatInboxEvent;
      if (!event.chat) {
        return;
      }
      const visibleOnCurrentSession =
        event.chat.direction === "OUTGOING"
          ? event.chat.initiatorSessionId === currentSessionId
          : event.chat.recipientSessionId === currentSessionId || !event.chat.recipientSessionId;
      if (!visibleOnCurrentSession) {
        return;
      }
      if (["DECLINED", "CLOSED"].includes(event.chat.status)) {
        setSecretChats((current) => removeSecretChat(current, event.chat!.secretChatId));
        void secretChatLocalCleanup
          .purgeSecretChat(currentUserId, event.chat.secretChatId)
          .catch(() => undefined);
        return;
      }
      setSecretChats((current) => upsertSecretChat(current, event.chat as SecretChatSummary));
      void localDatabase.upsertSecretChat(currentUserId, event.chat).catch(() => undefined);
    });

    return () => {
      unsubscribe();
    };
  }, [currentSessionId, currentUserId, token]);

  async function handleCreateSecretChat() {
    if (!seedPeerUserId || creating) {
      return;
    }

    setCreating(true);
    setError(null);
    const keyPair = secretChatCrypto.generateKeyPair();
    try {
      const secretChat = await api.createSecretChat(token, {
        initiatorPublicKey: keyPair.publicKey,
        recipientUserId: seedPeerUserId
      });
      await secretChatCrypto.storePrivateKey(secretChat.secretChatId, keyPair.privateKey);
      setSecretChats((current) => upsertSecretChat(current, secretChat));
      await localDatabase.upsertSecretChat(currentUserId, secretChat);
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : "Unable to create secret chat");
    } finally {
      setCreating(false);
    }
  }

  async function handleAccept(secretChat: SecretChatSummary) {
    if (handlingChatId) {
      return;
    }

    setHandlingChatId(secretChat.secretChatId);
    setError(null);
    const keyPair = secretChatCrypto.generateKeyPair();
    try {
      const fingerprint = secretChatCrypto.deriveFingerprint(secretChat, keyPair.privateKey);
      const accepted = await api.acceptSecretChat(token, secretChat.secretChatId, {
        recipientPublicKey: keyPair.publicKey,
        sharedKeyFingerprint: fingerprint
      });
      await secretChatCrypto.storePrivateKey(accepted.secretChatId, keyPair.privateKey);
      setSecretChats((current) => upsertSecretChat(current, accepted));
      await localDatabase.upsertSecretChat(currentUserId, accepted);
      onOpenSecretChat(accepted);
    } catch (acceptError) {
      setError(acceptError instanceof Error ? acceptError.message : "Unable to accept secret chat");
    } finally {
      setHandlingChatId(null);
    }
  }

  async function handleDecline(secretChat: SecretChatSummary) {
    if (handlingChatId) {
      return;
    }

    setHandlingChatId(secretChat.secretChatId);
    setError(null);
    try {
      const declined = await api.declineSecretChat(token, secretChat.secretChatId);
      setSecretChats((current) => removeSecretChat(current, declined.secretChatId));
      await secretChatLocalCleanup.purgeSecretChat(currentUserId, declined.secretChatId);
    } catch (declineError) {
      setError(declineError instanceof Error ? declineError.message : "Unable to decline secret chat");
    } finally {
      setHandlingChatId(null);
    }
  }

  const activeForSeedPeer = useMemo(
    () =>
      seedPeerUserId
        ? secretChats.find(
            (secretChat) =>
              secretChat.peerUserId === seedPeerUserId &&
              ["PENDING", "ACTIVE"].includes(secretChat.status)
          ) ?? null
        : null,
    [secretChats, seedPeerUserId]
  );

  return {
    activeForSeedPeer,
    creating,
    error,
    handleAccept,
    handleCreateSecretChat,
    handleDecline,
    handlingChatId,
    loading,
    secretChats
  };
}

export type SecretChatsScreenController = ReturnType<typeof useSecretChatsController>;
