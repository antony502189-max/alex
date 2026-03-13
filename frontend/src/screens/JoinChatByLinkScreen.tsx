import React, { useEffect, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  View
} from "react-native";
import { Avatar } from "../components/Avatar";
import { api } from "../services/api";
import type { ChatSummary, PublicChatDiscovery } from "../types";

type JoinChatByLinkScreenProps = {
  token: string;
  onClose: () => void;
  onJoined: (chat: ChatSummary) => void;
};

function normalizeInviteToken(value: string) {
  const normalized = value.trim();
  if (normalized.startsWith("alex://join/")) {
    return normalized.slice("alex://join/".length);
  }
  return normalized;
}

export function JoinChatByLinkScreen({
  token,
  onClose,
  onJoined
}: JoinChatByLinkScreenProps) {
  const [inviteToken, setInviteToken] = useState("");
  const [joining, setJoining] = useState(false);
  const [discovering, setDiscovering] = useState(false);
  const [discoveries, setDiscoveries] = useState<PublicChatDiscovery[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  useEffect(() => {
    const normalized = normalizeInviteToken(inviteToken);
    if (
      normalized.length < 2 ||
      normalized.startsWith("alex://join/") ||
      normalized.includes("/")
    ) {
      setDiscoveries([]);
      setDiscovering(false);
      return;
    }

    let cancelled = false;
    const timeoutId = setTimeout(() => {
      setDiscovering(true);
      api.searchPublicChats(token, normalized.startsWith("@") ? normalized.slice(1) : normalized, 8)
        .then((results) => {
          if (!cancelled) {
            setDiscoveries(results);
          }
        })
        .catch(() => {
          if (!cancelled) {
            setDiscoveries([]);
          }
        })
        .finally(() => {
          if (!cancelled) {
            setDiscovering(false);
          }
        });
    }, 250);

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [inviteToken, token]);

  async function handleJoin() {
    const normalized = normalizeInviteToken(inviteToken);
    if (!normalized || joining) {
      return;
    }

    setJoining(true);
    setError(null);
    setStatusMessage(null);
    try {
      const result = normalized.startsWith("@")
        ? await api.joinChatByUsername(token, normalized)
        : await api.joinChatByLink(token, normalized);
      if (result.status === "JOINED" && result.chat) {
        onJoined(result.chat);
        return;
      }
      setStatusMessage(
        `Join request sent to ${result.title}${result.publicUsername ? ` (@${result.publicUsername})` : ""}.`
      );
    } catch (joinError) {
      setError(joinError instanceof Error ? joinError.message : "Unable to join chat");
    } finally {
      setJoining(false);
    }
  }

  async function handleJoinDiscoveredChat(chat: PublicChatDiscovery) {
    if (!chat.publicUsername || joining) {
      return;
    }

    setJoining(true);
    setError(null);
    setStatusMessage(null);
    try {
      const result = await api.joinChatByUsername(token, `@${chat.publicUsername}`);
      if (result.status === "JOINED" && result.chat) {
        onJoined(result.chat);
        return;
      }
      setStatusMessage(
        `Join request sent to ${result.title}${result.publicUsername ? ` (@${result.publicUsername})` : ""}.`
      );
    } catch (joinError) {
      setError(joinError instanceof Error ? joinError.message : "Unable to join chat");
    } finally {
      setJoining(false);
    }
  }

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable onPress={onClose} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>Back</Text>
        </Pressable>
        <Text style={styles.title}>Join by link</Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.body}>
          Paste an invite token, a full `alex://join/...` link, or a public `@username`.
        </Text>
        <TextInput
          autoCapitalize="none"
          autoCorrect={false}
          onChangeText={(value) => {
            setInviteToken(value);
            if (statusMessage) {
              setStatusMessage(null);
            }
          }}
          placeholder="alex://join/... or @channel"
          style={styles.input}
          value={inviteToken}
        />
        {error ? <Text style={styles.errorText}>{error}</Text> : null}
        {statusMessage ? <Text style={styles.successText}>{statusMessage}</Text> : null}
        <Pressable
          disabled={joining || !inviteToken.trim()}
          onPress={() => void handleJoin()}
          style={[styles.primaryButton, (joining || !inviteToken.trim()) && styles.disabled]}
        >
          <Text style={styles.primaryButtonText}>{joining ? "Joining..." : "Join chat"}</Text>
        </Pressable>

        {discovering ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}
        {discoveries.length > 0 ? (
          <View style={styles.discoverySection}>
            <Text style={styles.discoveryTitle}>Public chats</Text>
            <FlatList
              data={discoveries}
              keyExtractor={(item) => item.chatId}
              renderItem={({ item }) => (
                <Pressable
                  onPress={() => void handleJoinDiscoveredChat(item)}
                  style={styles.discoveryCard}
                >
                  <Avatar uri={item.photoUrl} title={item.title} size={44} />
                  <View style={styles.discoveryBody}>
                    <Text style={styles.discoveryName}>{item.title}</Text>
                    <Text style={styles.discoveryMeta}>
                      {item.publicUsername ? `@${item.publicUsername}` : item.chatType}
                    </Text>
                    <Text style={styles.discoveryMeta}>
                      {item.memberCount} member{item.memberCount === 1 ? "" : "s"}
                      {item.joined ? " • already joined" : ""}
                      {item.joinRequiresApproval ? " • approval" : ""}
                    </Text>
                  </View>
                </Pressable>
              )}
              scrollEnabled={false}
            />
          </View>
        ) : null}
      </View>
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
  title: {
    fontSize: 24,
    fontWeight: "700",
    color: "#0f172a"
  },
  card: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16,
    gap: 12
  },
  body: {
    color: "#475569",
    lineHeight: 20
  },
  input: {
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 12,
    backgroundColor: "#ffffff"
  },
  primaryButton: {
    borderRadius: 14,
    backgroundColor: "#0f172a",
    paddingVertical: 14,
    alignItems: "center"
  },
  primaryButtonText: {
    color: "#ffffff",
    fontWeight: "600"
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
  disabled: {
    opacity: 0.6
  },
  errorText: {
    color: "#b91c1c"
  },
  successText: {
    color: "#0f766e"
  },
  loader: {
    marginTop: 8
  },
  discoverySection: {
    gap: 10,
    marginTop: 4
  },
  discoveryTitle: {
    color: "#0f172a",
    fontWeight: "700"
  },
  discoveryCard: {
    flexDirection: "row",
    gap: 12,
    alignItems: "center",
    borderRadius: 14,
    backgroundColor: "#f8fafc",
    padding: 12
  },
  discoveryBody: {
    flex: 1
  },
  discoveryName: {
    color: "#0f172a",
    fontWeight: "700"
  },
  discoveryMeta: {
    color: "#475569",
    marginTop: 2,
    fontSize: 12
  }
});
