import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { appColors, appSpacing } from "../../theme/tokens";
import { AppButton } from "../ui/AppButton";
import { AppPanel } from "../ui/AppPanel";
import { AppTextField } from "../ui/AppTextField";
import { SectionCard } from "../ui/SectionCard";

type AuthQrSectionProps = {
  autoPollingQr: boolean;
  onBindDevice: () => void;
  onCheckApproval: () => void;
  onQrTokenChange: (value: string) => void;
  qrDeviceSummary: string | null;
  qrExpiresAt: string | null;
  qrStatusDescription: string | null;
  qrStatusVisible: boolean;
  qrToken: string;
  submitting: boolean;
};

export function AuthQrSection({
  autoPollingQr,
  onBindDevice,
  onCheckApproval,
  onQrTokenChange,
  qrDeviceSummary,
  qrExpiresAt,
  qrStatusDescription,
  qrStatusVisible,
  qrToken,
  submitting
}: AuthQrSectionProps) {
  const disabled = submitting || !qrToken.trim();

  return (
    <SectionCard
      description="Paste a QR login token generated from an active session, then bind and poll until approval is granted."
      title="QR sign in"
    >
      <AppTextField
        autoCapitalize="none"
        autoCorrect={false}
        onChangeText={onQrTokenChange}
        placeholder="QR login token"
        value={qrToken}
      />

      {qrStatusVisible ? (
        <AppPanel
          description={qrStatusDescription ?? undefined}
          title="QR request state"
          tone={autoPollingQr ? "brand" : "info"}
        >
          {qrDeviceSummary ? <Text style={styles.metaText}>Device: {qrDeviceSummary}</Text> : null}
          {qrExpiresAt ? (
            <Text style={styles.metaText}>Expires: {new Date(qrExpiresAt).toLocaleString()}</Text>
          ) : null}
          {autoPollingQr ? (
            <Text style={styles.metaText}>This screen is checking approval automatically.</Text>
          ) : null}
        </AppPanel>
      ) : null}

      <View style={styles.actionsRow}>
        <AppButton
          disabled={disabled}
          fullWidth
          onPress={onBindDevice}
          style={styles.actionButton}
        >
          {submitting ? "Working..." : "Bind device"}
        </AppButton>
        <AppButton
          disabled={disabled}
          fullWidth
          onPress={onCheckApproval}
          style={styles.actionButton}
          variant="primary"
        >
          {submitting ? "Checking..." : "Check approval"}
        </AppButton>
      </View>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  actionsRow: {
    flexDirection: "row",
    gap: appSpacing.sm
  },
  actionButton: {
    flex: 1
  },
  metaText: {
    color: appColors.textSecondary,
    fontSize: 13
  }
});
