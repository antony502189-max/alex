import React, { useEffect, useState } from "react";
import {
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  View
} from "react-native";
import { api } from "../services/api";
import type { GeneratedQrLogin, QrLoginChallenge, UserSession } from "../types";

type SessionsScreenProps = {
  currentSessionId: string;
  onClose: () => void;
  token: string;
};

export function SessionsScreen({
  currentSessionId,
  onClose,
  token
}: SessionsScreenProps) {
  const [sessions, setSessions] = useState<UserSession[]>([]);
  const [qrChallenge, setQrChallenge] = useState<GeneratedQrLogin | null>(null);
  const [qrChallenges, setQrChallenges] = useState<QrLoginChallenge[]>([]);
  const [loading, setLoading] = useState(false);
  const [revokingSessionId, setRevokingSessionId] = useState<string | null>(null);
  const [creatingQr, setCreatingQr] = useState(false);
  const [processingQrChallengeId, setProcessingQrChallengeId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function loadSessions(options?: { silent?: boolean }) {
    if (!options?.silent) {
      setLoading(true);
      setError(null);
    }
    try {
      const [nextSessions, nextQrChallenges] = await Promise.all([
        api.getSessions(token),
        api.getQrLoginChallenges(token)
      ]);
      setSessions(nextSessions);
      setQrChallenges(nextQrChallenges);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load sessions");
    } finally {
      if (!options?.silent) {
        setLoading(false);
      }
    }
  }

  useEffect(() => {
    void loadSessions();
  }, [token]);

  useEffect(() => {
    if (!qrChallenge && !qrChallenges.some((challenge) => challenge.status === "PENDING_APPROVAL")) {
      return;
    }

    const timer = setInterval(() => {
      void loadSessions({ silent: true });
    }, 4000);

    return () => {
      clearInterval(timer);
    };
  }, [qrChallenge, qrChallenges, token]);

  async function handleRevoke(sessionId: string) {
    setRevokingSessionId(sessionId);
    setError(null);
    setNotice(null);
    try {
      await api.revokeSession(token, sessionId);
      await loadSessions();
    } catch (revokeError) {
      setError(revokeError instanceof Error ? revokeError.message : "Unable to revoke session");
    } finally {
      setRevokingSessionId(null);
    }
  }

  async function handleRevokeOthers() {
    setRevokingSessionId("others");
    setError(null);
    setNotice(null);
    try {
      await api.revokeOtherSessions(token);
      await loadSessions();
    } catch (revokeError) {
      setError(revokeError instanceof Error ? revokeError.message : "Unable to revoke other sessions");
    } finally {
      setRevokingSessionId(null);
    }
  }

  async function handleCreateQr() {
    setCreatingQr(true);
    setError(null);
    setNotice(null);
    try {
      const nextChallenge = await api.generateQrLogin(token);
      setQrChallenge(nextChallenge);
      setNotice("QR login token generated. Open the QR tab on the new device and paste this token there.");
      await loadSessions();
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : "Unable to generate QR login token");
    } finally {
      setCreatingQr(false);
    }
  }

  async function handleApproveQr(challengeId: string) {
    setProcessingQrChallengeId(challengeId);
    setError(null);
    setNotice(null);
    try {
      await api.approveQrLogin(token, challengeId);
      setNotice("QR login approved. The new device can finish sign-in now.");
      await loadSessions();
    } catch (approveError) {
      setError(approveError instanceof Error ? approveError.message : "Unable to approve QR login");
    } finally {
      setProcessingQrChallengeId(null);
    }
  }

  async function handleDeclineQr(challengeId: string) {
    setProcessingQrChallengeId(challengeId);
    setError(null);
    setNotice(null);
    try {
      await api.declineQrLogin(token, challengeId);
      setNotice("QR login request declined.");
      await loadSessions();
    } catch (declineError) {
      setError(declineError instanceof Error ? declineError.message : "Unable to decline QR login");
    } finally {
      setProcessingQrChallengeId(null);
    }
  }

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable onPress={onClose} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>Back</Text>
        </Pressable>
        <Text style={styles.title}>Devices</Text>
      </View>

      <View style={styles.actionsRow}>
        <Pressable
          disabled={loading || revokingSessionId === "others"}
          onPress={() => void loadSessions()}
          style={[styles.secondaryButton, (loading || revokingSessionId === "others") && styles.disabled]}
        >
          <Text style={styles.secondaryButtonText}>{loading ? "Loading..." : "Refresh"}</Text>
        </Pressable>
        <Pressable
          disabled={loading || revokingSessionId === "others"}
          onPress={() => void handleRevokeOthers()}
          style={[styles.dangerButton, (loading || revokingSessionId === "others") && styles.disabled]}
        >
          <Text style={styles.dangerButtonText}>
            {revokingSessionId === "others" ? "..." : "Terminate others"}
          </Text>
        </Pressable>
      </View>

      {error ? <Text style={styles.errorText}>{error}</Text> : null}
      {notice ? <Text style={styles.noticeText}>{notice}</Text> : null}

      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.card}>
          <Text style={styles.deviceName}>QR login</Text>
          <Text style={styles.metaText}>
            Generate a one-time token, paste it into another client, then approve the pending request here.
          </Text>
          <View style={styles.helpList}>
            <Text style={styles.helpItem}>1. Generate a QR token on this trusted device.</Text>
            <Text style={styles.helpItem}>2. Open the QR tab on the new device and paste the token.</Text>
            <Text style={styles.helpItem}>3. Approve the pending device request here.</Text>
          </View>
          {qrChallenge ? (
            <View style={styles.qrTokenCard}>
              <Text style={styles.qrTokenLabel}>Active token</Text>
              <Text selectable style={styles.qrTokenValue}>
                {qrChallenge.qrToken}
              </Text>
              <Text style={styles.metaText}>
                Expires: {new Date(qrChallenge.expiresAt).toLocaleString()}
              </Text>
              <Text style={styles.metaText}>Long press the token to select and copy it.</Text>
            </View>
          ) : null}
          <View style={styles.actionsRow}>
            <Pressable
              disabled={creatingQr}
              onPress={() => void handleCreateQr()}
              style={[styles.secondaryButton, creatingQr && styles.disabled]}
            >
              <Text style={styles.secondaryButtonText}>
                {creatingQr ? "Generating..." : "Generate QR token"}
              </Text>
            </Pressable>
            <Pressable
              disabled={loading}
              onPress={() => void loadSessions()}
              style={[styles.secondaryButton, loading && styles.disabled]}
            >
              <Text style={styles.secondaryButtonText}>Refresh requests</Text>
            </Pressable>
          </View>
        </View>

        {qrChallenges.length > 0 ? (
          <View style={styles.card}>
            <Text style={styles.deviceName}>QR login requests</Text>
            <Text style={styles.metaText}>
              Pending approvals: {qrChallenges.filter((challenge) => challenge.status === "PENDING_APPROVAL").length}
            </Text>
            <View style={styles.qrChallengesList}>
              {qrChallenges.map((challenge) => (
                <View key={challenge.challengeId} style={styles.qrChallengeCard}>
                  <Text style={styles.metaText}>Status: {challenge.status}</Text>
                  <Text style={styles.metaText}>
                    Device: {challenge.deviceName ?? "Unknown device"}
                  </Text>
                  <Text style={styles.metaText}>
                    {[challenge.platform, challenge.appVersion].filter(Boolean).join(" | ") || "Unknown platform"}
                  </Text>
                  <Text style={styles.metaText}>
                    Created: {new Date(challenge.createdAt).toLocaleString()}
                  </Text>
                  {challenge.boundAt ? (
                    <Text style={styles.metaText}>
                      Bound: {new Date(challenge.boundAt).toLocaleString()}
                    </Text>
                  ) : null}
                  {challenge.approvedAt ? (
                    <Text style={styles.metaText}>
                      Approved: {new Date(challenge.approvedAt).toLocaleString()}
                    </Text>
                  ) : null}
                  {challenge.ipAddress ? (
                    <Text style={styles.metaText}>IP {challenge.ipAddress}</Text>
                  ) : null}
                  {challenge.userAgent ? (
                    <Text style={styles.metaText}>{challenge.userAgent}</Text>
                  ) : null}
                  <Text style={styles.metaText}>
                    Expires: {new Date(challenge.expiresAt).toLocaleString()}
                  </Text>
                  {challenge.status === "PENDING_APPROVAL" ? (
                    <View style={styles.actionsRow}>
                      <Pressable
                        disabled={processingQrChallengeId === challenge.challengeId}
                        onPress={() => void handleApproveQr(challenge.challengeId)}
                        style={[
                          styles.secondaryButton,
                          processingQrChallengeId === challenge.challengeId && styles.disabled
                        ]}
                      >
                        <Text style={styles.secondaryButtonText}>Approve</Text>
                      </Pressable>
                      <Pressable
                        disabled={processingQrChallengeId === challenge.challengeId}
                        onPress={() => void handleDeclineQr(challenge.challengeId)}
                        style={[
                          styles.inlineDangerButton,
                          processingQrChallengeId === challenge.challengeId && styles.disabled
                        ]}
                      >
                        <Text style={styles.inlineDangerText}>Decline</Text>
                      </Pressable>
                    </View>
                  ) : null}
                </View>
              ))}
            </View>
          </View>
        ) : null}

        {sessions.map((session) => (
          <View key={session.sessionId} style={styles.card}>
            <View style={styles.cardTopRow}>
              <View style={styles.cardText}>
                <Text style={styles.deviceName}>{session.deviceName}</Text>
                <Text style={styles.metaText}>
                  {[session.platform, session.appVersion].filter(Boolean).join(" | ") || "Unknown platform"}
                </Text>
                <Text style={styles.metaText}>
                  Active: {new Date(session.lastActiveAt).toLocaleString()}
                </Text>
                <Text style={styles.metaText}>
                  Auth: {session.authMethod ?? "UNKNOWN"}
                </Text>
                <Text style={styles.metaText}>
                  Trust: {session.trustedSession ? "trusted" : "standard"}
                  {session.trustedAt ? ` since ${new Date(session.trustedAt).toLocaleString()}` : ""}
                </Text>
                <Text style={styles.metaText}>
                  Push notifications: {session.notificationsEnabled ? "enabled" : "disabled"}
                </Text>
                <Text style={styles.metaText}>
                  Started: {new Date(session.createdAt).toLocaleString()}
                </Text>
                {session.userAgent ? <Text style={styles.metaText}>{session.userAgent}</Text> : null}
                {session.ipAddress ? <Text style={styles.metaText}>IP {session.ipAddress}</Text> : null}
              </View>
              {session.current || session.sessionId === currentSessionId ? (
                <View style={styles.currentBadge}>
                  <Text style={styles.currentBadgeText}>Current</Text>
                </View>
              ) : (
                <Pressable
                  disabled={revokingSessionId === session.sessionId}
                  onPress={() => void handleRevoke(session.sessionId)}
                  style={[styles.inlineDangerButton, revokingSessionId === session.sessionId && styles.disabled]}
                >
                  <Text style={styles.inlineDangerText}>
                    {revokingSessionId === session.sessionId ? "..." : "Terminate"}
                  </Text>
                </Pressable>
              )}
            </View>
          </View>
        ))}
      </ScrollView>
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
  actionsRow: {
    flexDirection: "row",
    gap: 12,
    marginBottom: 12
  },
  content: {
    gap: 12,
    paddingBottom: 24
  },
  card: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16
  },
  qrTokenCard: {
    borderRadius: 14,
    backgroundColor: "#eff6ff",
    padding: 12,
    gap: 6,
    marginTop: 10,
    marginBottom: 10
  },
  qrTokenLabel: {
    color: "#1d4ed8",
    fontWeight: "700"
  },
  qrTokenValue: {
    color: "#0f172a",
    fontFamily: "monospace",
    fontWeight: "700"
  },
  qrChallengesList: {
    gap: 10,
    marginTop: 10
  },
  qrChallengeCard: {
    borderRadius: 14,
    backgroundColor: "#f8fafc",
    padding: 12,
    gap: 4
  },
  cardTopRow: {
    flexDirection: "row",
    gap: 12,
    alignItems: "flex-start"
  },
  cardText: {
    flex: 1,
    gap: 4
  },
  deviceName: {
    fontSize: 18,
    fontWeight: "700",
    color: "#0f172a"
  },
  metaText: {
    color: "#64748b"
  },
  noticeText: {
    color: "#166534",
    marginBottom: 8
  },
  helpList: {
    gap: 6,
    marginTop: 10,
    marginBottom: 10
  },
  helpItem: {
    color: "#475569",
    fontSize: 13
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
  inlineDangerButton: {
    borderRadius: 12,
    backgroundColor: "#fee2e2",
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  inlineDangerText: {
    color: "#b91c1c",
    fontWeight: "700"
  },
  currentBadge: {
    borderRadius: 999,
    backgroundColor: "#dcfce7",
    paddingHorizontal: 12,
    paddingVertical: 8
  },
  currentBadgeText: {
    color: "#166534",
    fontWeight: "700"
  },
  errorText: {
    color: "#b91c1c",
    marginBottom: 8
  },
  disabled: {
    opacity: 0.6
  }
});
