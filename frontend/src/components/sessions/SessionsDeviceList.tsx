import React from "react";
import { StyleSheet, Text, View } from "react-native";
import type { UserSession } from "../../types";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import { AppButton } from "../ui/AppButton";
import { AppChip } from "../ui/AppChip";
import { SectionCard } from "../ui/SectionCard";
import {
  buildSessionMetaLines,
  isCurrentSession
} from "./sessionsPresentation";

type SessionsDeviceListProps = {
  currentSessionId: string;
  loading: boolean;
  onRefresh: () => void;
  onRevoke: (sessionId: string) => void;
  onRevokeOthers: () => void;
  revokingSessionId: string | null;
  sessions: UserSession[];
};

export function SessionsDeviceList({
  currentSessionId,
  loading,
  onRefresh,
  onRevoke,
  onRevokeOthers,
  revokingSessionId,
  sessions
}: SessionsDeviceListProps) {
  return (
    <SectionCard
      description="Review active devices, revoke sessions you no longer trust, and quickly terminate all other logins."
      title="Devices"
    >
      <View style={styles.actionsRow}>
        <AppButton disabled={loading || revokingSessionId === "others"} onPress={onRefresh}>
          {loading ? "Loading..." : "Refresh"}
        </AppButton>
        <AppButton
          disabled={loading || revokingSessionId === "others"}
          onPress={onRevokeOthers}
          variant="danger"
        >
          {revokingSessionId === "others" ? "Terminating..." : "Terminate others"}
        </AppButton>
      </View>

      <View style={styles.sessionList}>
        {sessions.map((session) => {
          const current = isCurrentSession(session, currentSessionId);
          const busy = revokingSessionId === session.sessionId;

          return (
            <View key={session.sessionId} style={[styles.sessionCard, current && styles.currentCard]}>
              <View style={styles.cardTopRow}>
                <View style={styles.cardBody}>
                  <Text style={styles.deviceName}>{session.deviceName}</Text>
                  {buildSessionMetaLines(session).map((line) => (
                    <Text key={`${session.sessionId}:${line}`} style={styles.metaText}>
                      {line}
                    </Text>
                  ))}
                </View>
                {current ? (
                  <AppChip tone="success">Current</AppChip>
                ) : (
                  <AppButton
                    disabled={busy}
                    onPress={() => onRevoke(session.sessionId)}
                    size="sm"
                    variant="danger"
                  >
                    {busy ? "Terminating..." : "Terminate"}
                  </AppButton>
                )}
              </View>
            </View>
          );
        })}
      </View>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  actionsRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  sessionList: {
    gap: appSpacing.sm + 2
  },
  sessionCard: {
    backgroundColor: "#f8fbff",
    borderColor: appColors.border,
    borderRadius: appRadii.md,
    borderWidth: 1,
    padding: appSpacing.md
  },
  currentCard: {
    backgroundColor: "#f0fdf4",
    borderColor: "#86efac"
  },
  cardTopRow: {
    alignItems: "flex-start",
    flexDirection: "row",
    gap: appSpacing.md
  },
  cardBody: {
    flex: 1,
    gap: appSpacing.xs
  },
  deviceName: {
    color: appColors.textPrimary,
    fontSize: 18,
    fontWeight: "700"
  },
  metaText: {
    color: appColors.textSecondary
  }
});
