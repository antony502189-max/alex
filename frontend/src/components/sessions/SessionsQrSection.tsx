import React from "react";
import { StyleSheet, Text, View } from "react-native";
import type { GeneratedQrLogin, QrLoginChallenge } from "../../types";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import { AppButton } from "../ui/AppButton";
import { AppChip } from "../ui/AppChip";
import { SectionCard } from "../ui/SectionCard";
import { buildQrChallengeMetaLines } from "./sessionsPresentation";

type SessionsQrSectionProps = {
  creatingQr: boolean;
  loading: boolean;
  onApproveQr: (challengeId: string) => void;
  onCreateQr: () => void;
  onDeclineQr: (challengeId: string) => void;
  onRefresh: () => void;
  pendingQrApprovals: number;
  processingQrChallengeId: string | null;
  qrChallenge: GeneratedQrLogin | null;
  qrChallenges: QrLoginChallenge[];
};

export function SessionsQrSection({
  creatingQr,
  loading,
  onApproveQr,
  onCreateQr,
  onDeclineQr,
  onRefresh,
  pendingQrApprovals,
  processingQrChallengeId,
  qrChallenge,
  qrChallenges
}: SessionsQrSectionProps) {
  return (
    <>
      <SectionCard
        description="Generate a one-time token, paste it into another client, then approve the pending request here."
        title="QR login"
      >
        <View style={styles.helpList}>
          <Text style={styles.helpItem}>1. Generate a QR token on this trusted device.</Text>
          <Text style={styles.helpItem}>2. Open the QR tab on the new device and paste the token.</Text>
          <Text style={styles.helpItem}>3. Approve the pending device request here.</Text>
        </View>
        {qrChallenge ? (
          <View style={styles.qrTokenCard}>
            <AppChip tone="brand">Active token</AppChip>
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
          <AppButton disabled={creatingQr} onPress={onCreateQr}>
            {creatingQr ? "Generating..." : "Generate QR token"}
          </AppButton>
          <AppButton disabled={loading} onPress={onRefresh}>
            {loading ? "Loading..." : "Refresh requests"}
          </AppButton>
        </View>
      </SectionCard>

      {qrChallenges.length > 0 ? (
        <SectionCard
          description="Approve or decline device sign-in requests from here."
          title="QR login requests"
        >
          <View style={styles.pendingHeader}>
            <AppChip tone={pendingQrApprovals > 0 ? "warning" : "muted"}>
              Pending approvals: {pendingQrApprovals}
            </AppChip>
          </View>
          <View style={styles.challengeList}>
            {qrChallenges.map((challenge) => {
              const busy = processingQrChallengeId === challenge.challengeId;

              return (
                <View key={challenge.challengeId} style={styles.challengeCard}>
                  {buildQrChallengeMetaLines(challenge).map((line) => (
                    <Text key={`${challenge.challengeId}:${line}`} style={styles.metaText}>
                      {line}
                    </Text>
                  ))}
                  {challenge.status === "PENDING_APPROVAL" ? (
                    <View style={styles.actionsRow}>
                      <AppButton
                        disabled={busy}
                        onPress={() => onApproveQr(challenge.challengeId)}
                        size="sm"
                      >
                        Approve
                      </AppButton>
                      <AppButton
                        disabled={busy}
                        onPress={() => onDeclineQr(challenge.challengeId)}
                        size="sm"
                        variant="danger"
                      >
                        Decline
                      </AppButton>
                    </View>
                  ) : null}
                </View>
              );
            })}
          </View>
        </SectionCard>
      ) : null}
    </>
  );
}

const styles = StyleSheet.create({
  helpList: {
    gap: appSpacing.sm
  },
  helpItem: {
    color: appColors.textSecondary,
    fontSize: 13
  },
  qrTokenCard: {
    backgroundColor: appColors.surfaceMuted,
    borderRadius: appRadii.md,
    gap: appSpacing.sm,
    padding: appSpacing.md
  },
  qrTokenValue: {
    color: appColors.textPrimary,
    fontFamily: "monospace",
    fontWeight: "700"
  },
  metaText: {
    color: appColors.textSecondary
  },
  actionsRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  pendingHeader: {
    alignItems: "flex-start"
  },
  challengeList: {
    gap: appSpacing.sm + 2
  },
  challengeCard: {
    backgroundColor: "#f8fbff",
    borderColor: appColors.border,
    borderRadius: appRadii.md,
    borderWidth: 1,
    gap: appSpacing.xs,
    padding: appSpacing.md
  }
});
