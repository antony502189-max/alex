import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { AppButton } from "../ui/AppButton";
import { AppPanel } from "../ui/AppPanel";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appSpacing } from "../../theme/tokens";
import type { SecretChatSummary } from "../../types";

type SecretChatSecuritySectionProps = {
  fingerprintMismatch: boolean;
  localFingerprint: string | null;
  onUpdateTimer: (autoDeleteSeconds: number | null) => void;
  secretChat: SecretChatSummary;
  updatingTimer: boolean;
};

const TIMER_OPTIONS = [null, 10, 30, 60] as const;

export function SecretChatSecuritySection({
  fingerprintMismatch,
  localFingerprint,
  onUpdateTimer,
  secretChat,
  updatingTimer
}: SecretChatSecuritySectionProps) {
  return (
    <SectionCard
      description="Copying, forwarding, and external saving are disabled in this secret chat."
      style={styles.card}
      title="Key fingerprint"
    >
      <Text style={styles.fingerprintText}>
        {localFingerprint ?? secretChat.sharedKeyFingerprint ?? "Pending handshake"}
      </Text>

      {fingerprintMismatch ? (
        <AppPanel
          description="Local fingerprint does not match the server-advertised fingerprint for this secret chat."
          tone="warning"
        />
      ) : null}

      <View style={styles.timerRow}>
        {TIMER_OPTIONS.map((timerValue) => {
          const active = secretChat.autoDeleteSeconds === timerValue;
          return (
            <AppButton
              key={`ttl-${timerValue ?? "off"}`}
              disabled={updatingTimer || secretChat.status !== "ACTIVE"}
              onPress={() => onUpdateTimer(timerValue)}
              size="sm"
              style={styles.timerButton}
              variant={active ? "primary" : "secondary"}
            >
              {timerValue ? `${timerValue}s` : "Off"}
            </AppButton>
          );
        })}
      </View>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  card: {
    marginHorizontal: appSpacing.xl
  },
  fingerprintText: {
    color: appColors.textPrimary,
    fontFamily: "monospace",
    lineHeight: 22
  },
  timerRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  timerButton: {
    minWidth: 64
  }
});
