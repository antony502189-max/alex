import React, { useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  View
} from "react-native";
import { Avatar } from "../components/Avatar";
import { api } from "../services/api";
import { localDatabase } from "../services/localDatabase";
import { secretChatCrypto } from "../services/secretChatCrypto";
import { secretChatLocalCleanup } from "../services/secretChatLocalCleanup";
import { wsService } from "../services/ws";
import type { SecretChatInboxEvent, SecretChatSummary } from "../types";

type SecretChatsScreenProps = {
  token: string;
  currentUserId: string;
  currentSessionId: string;
  seedPeerUserId?: string | null;
  seedPeerDisplayName?: string | null;
  onClose: () => void;
  onOpenSecretChat: (chat: SecretChatSummary) => void;
};

function sortSecretChats(secretChats: SecretChatSummary[]) {
  return [...secretChats].sort((left, right) =>
    (right.lastMessageAt ?? right.acceptedAt ?? right.createdAt).localeCompare(
      left.lastMessageAt ?? left.acceptedAt ?? left.createdAt
    )
  );
}

function upsertSecretChat(current: SecretChatSummary[], next: SecretChatSummary) {
  return sortSecretChats([
    ...current.filter((item) => item.secretChatId !== next.secretChatId),
    next
  ]);
}

function removeSecretChat(current: SecretChatSummary[], secretChatId: string) {
  return current.filter((item) => item.secretChatId !== secretChatId);
}

function formatSecretChatState(secretChat: SecretChatSummary) {
  if (secretChat.status === "PENDING") {
    return secretChat.direction === "OUTGOING"
      ? "Waiting for peer device to accept"
      : "Incoming request";
  }
  if (secretChat.status === "ACTIVE") {
    return secretChat.autoDeleteSeconds
      ? `Active - TTL ${secretChat.autoDeleteSeconds}s`
      : "Active";
  }
  if (secretChat.status === "DECLINED") {
    return "Declined";
  }
  return "Closed";
}

export function SecretChatsScreen({
  token,
  currentUserId,
  currentSessionId,
  seedPeerUserId,
  seedPeerDisplayName,
  onClose,
  onOpenSecretChat
}: SecretChatsScreenProps) {
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
    void localDatabase.getSecretChats(currentUserId)
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
          : event.chat.recipientSessionId === currentSessionId ||
            !event.chat.recipientSessionId;
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
        recipientUserId: seedPeerUserId,
        initiatorPublicKey: keyPair.publicKey
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
    () => seedPeerUserId
      ? secretChats.find(
          (secretChat) =>
            secretChat.peerUserId === seedPeerUserId &&
            ["PENDING", "ACTIVE"].includes(secretChat.status)
        ) ?? null
      : null,
    [secretChats, seedPeerUserId]
  );

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable onPress={onClose} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>Back</Text>
        </Pressable>
        <View style={styles.headerText}>
          <Text style={styles.title}>Secret Chats</Text>
          <Text style={styles.subtitle}>Device-bound end-to-end sessions</Text>
        </View>
      </View>

      {seedPeerUserId ? (
        <View style={styles.seedCard}>
          <Text style={styles.seedTitle}>
            {seedPeerDisplayName ? `Start with ${seedPeerDisplayName}` : "Start secret chat"}
          </Text>
          <Text style={styles.seedText}>
            Secret chats are bound to one device and use end-to-end encryption.
          </Text>
          {activeForSeedPeer ? (
            <Pressable onPress={() => onOpenSecretChat(activeForSeedPeer)} style={styles.primaryButton}>
              <Text style={styles.primaryButtonText}>
                {activeForSeedPeer.status === "ACTIVE" ? "Open secret chat" : "Open request"}
              </Text>
            </Pressable>
          ) : (
            <Pressable
              disabled={creating}
              onPress={() => void handleCreateSecretChat()}
              style={[styles.primaryButton, creating && styles.disabled]}
            >
              <Text style={styles.primaryButtonText}>{creating ? "Creating..." : "Create secret chat"}</Text>
            </Pressable>
          )}
        </View>
      ) : null}

      {loading ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}
      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      <FlatList
        contentContainerStyle={styles.listContent}
        data={secretChats}
        keyExtractor={(item) => item.secretChatId}
        ListEmptyComponent={
          <Text style={styles.emptyState}>
            No secret chats yet. Open a direct chat and start one from the header.
          </Text>
        }
        renderItem={({ item }) => (
          <Pressable
            onPress={() => {
              if (item.status === "ACTIVE" || item.status === "PENDING") {
                onOpenSecretChat(item);
              }
            }}
            style={styles.secretChatCard}
          >
            <Avatar uri={item.peerPhotoUrl} title={item.peerDisplayName} size={56} />
            <View style={styles.secretChatBody}>
              <Text style={styles.secretChatTitle}>{item.peerDisplayName}</Text>
              <Text style={styles.secretChatMeta}>{formatSecretChatState(item)}</Text>
              <Text style={styles.secretChatMeta}>
                {`${item.peerDeviceName ?? "Device not bound yet"} - ${item.direction.toLowerCase()}`}
              </Text>
            </View>
            {item.status === "PENDING" && item.direction === "INCOMING" ? (
              <View style={styles.inlineActions}>
                <Pressable
                  disabled={handlingChatId === item.secretChatId}
                  onPress={() => void handleAccept(item)}
                  style={[styles.inlinePrimaryButton, handlingChatId === item.secretChatId && styles.disabled]}
                >
                  <Text style={styles.inlinePrimaryButtonText}>Accept</Text>
                </Pressable>
                <Pressable
                  disabled={handlingChatId === item.secretChatId}
                  onPress={() => void handleDecline(item)}
                  style={[styles.inlineDangerButton, handlingChatId === item.secretChatId && styles.disabled]}
                >
                  <Text style={styles.inlineDangerButtonText}>Decline</Text>
                </Pressable>
              </View>
            ) : null}
          </Pressable>
        )}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: "#f8fafc",
    padding: 20
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    marginBottom: 16
  },
  headerText: {
    flex: 1
  },
  title: {
    fontSize: 28,
    fontWeight: "700",
    color: "#0f172a"
  },
  subtitle: {
    marginTop: 4,
    color: "#64748b"
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
  seedCard: {
    borderRadius: 18,
    backgroundColor: "#ecfccb",
    padding: 16,
    gap: 8,
    marginBottom: 16
  },
  seedTitle: {
    color: "#365314",
    fontWeight: "700",
    fontSize: 16
  },
  seedText: {
    color: "#4d7c0f",
    lineHeight: 20
  },
  primaryButton: {
    borderRadius: 14,
    backgroundColor: "#0f172a",
    paddingVertical: 14,
    alignItems: "center"
  },
  primaryButtonText: {
    color: "#ffffff",
    fontWeight: "700"
  },
  disabled: {
    opacity: 0.6
  },
  loader: {
    marginBottom: 12
  },
  errorText: {
    color: "#b91c1c",
    marginBottom: 12
  },
  listContent: {
    gap: 12,
    paddingBottom: 24
  },
  secretChatCard: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16,
    flexDirection: "row",
    alignItems: "center",
    gap: 14
  },
  secretChatBody: {
    flex: 1,
    gap: 4
  },
  secretChatTitle: {
    color: "#0f172a",
    fontWeight: "700",
    fontSize: 18
  },
  secretChatMeta: {
    color: "#64748b",
    fontSize: 13
  },
  inlineActions: {
    gap: 8
  },
  inlinePrimaryButton: {
    borderRadius: 12,
    backgroundColor: "#0f172a",
    paddingHorizontal: 14,
    paddingVertical: 10,
    alignItems: "center"
  },
  inlinePrimaryButtonText: {
    color: "#ffffff",
    fontWeight: "700"
  },
  inlineDangerButton: {
    borderRadius: 12,
    backgroundColor: "#fee2e2",
    paddingHorizontal: 14,
    paddingVertical: 10,
    alignItems: "center"
  },
  inlineDangerButtonText: {
    color: "#b91c1c",
    fontWeight: "700"
  },
  emptyState: {
    color: "#64748b",
    lineHeight: 22,
    paddingTop: 24
  }
});
