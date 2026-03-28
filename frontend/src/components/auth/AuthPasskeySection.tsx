import React from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { DevicePasskey } from "../../types";
import { AppButton } from "../ui/AppButton";
import { AppChip } from "../ui/AppChip";
import { AppTextField } from "../ui/AppTextField";
import { SectionCard } from "../ui/SectionCard";
import { formatPasskeyCreatedAt, formatPasskeyLastUsedAt } from "./authPresentation";

type AuthPasskeySectionProps = {
  availablePasskeys: DevicePasskey[];
  loadingPasskeys: boolean;
  onPhoneNumberChange: (value: string) => void;
  onRefreshPasskeys: () => void;
  onSelectPasskey: (credentialId: string) => void;
  onUsePasskey: () => void;
  phoneNumber: string;
  primaryDisabled: boolean;
  selectedPasskeyId: string | null;
  submitting: boolean;
};

export function AuthPasskeySection({
  availablePasskeys,
  loadingPasskeys,
  onPhoneNumberChange,
  onRefreshPasskeys,
  onSelectPasskey,
  onUsePasskey,
  phoneNumber,
  primaryDisabled,
  selectedPasskeyId,
  submitting
}: AuthPasskeySectionProps) {
  return (
    <SectionCard
      description="Sign in with a device passkey already registered on this phone from the profile security screen."
      title="Passkey sign in"
    >
      <AppTextField
        autoCapitalize="none"
        keyboardType="phone-pad"
        onChangeText={onPhoneNumberChange}
        placeholder="+375291234567"
        value={phoneNumber}
      />

      {loadingPasskeys ? <Text style={styles.infoText}>Checking device passkeys...</Text> : null}

      {!loadingPasskeys && availablePasskeys.length > 0 ? (
        <View style={styles.passkeyList}>
          {availablePasskeys.map((passkey) => {
            const selected = selectedPasskeyId === passkey.credentialId;
            return (
              <Pressable
                key={passkey.credentialId}
                onPress={() => onSelectPasskey(passkey.credentialId)}
                style={({ pressed }) => [
                  styles.passkeyCard,
                  selected && styles.passkeyCardActive,
                  pressed && styles.pressed
                ]}
              >
                <View style={styles.passkeyHeader}>
                  <Text style={styles.passkeyTitle}>{passkey.label ?? "Unnamed device passkey"}</Text>
                  {selected ? (
                    <AppChip active tone="brand">
                      Selected for sign-in
                    </AppChip>
                  ) : null}
                </View>
                <Text style={styles.passkeyMeta}>{formatPasskeyCreatedAt(passkey)}</Text>
                <Text style={styles.passkeyMeta}>{formatPasskeyLastUsedAt(passkey)}</Text>
              </Pressable>
            );
          })}
        </View>
      ) : null}

      {!loadingPasskeys && availablePasskeys.length === 0 ? (
        <Text style={styles.emptyText}>No local device passkeys found for this phone number.</Text>
      ) : null}

      <View style={styles.actionsRow}>
        <AppButton
          disabled={loadingPasskeys}
          fullWidth
          onPress={onRefreshPasskeys}
          style={styles.actionButton}
        >
          {loadingPasskeys ? "Refreshing..." : "Refresh passkeys"}
        </AppButton>
        <AppButton
          disabled={primaryDisabled}
          fullWidth
          onPress={onUsePasskey}
          style={styles.actionButton}
          variant="primary"
        >
          {submitting ? "Signing in..." : "Use device passkey"}
        </AppButton>
      </View>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  infoText: {
    color: "#0f766e",
    fontSize: 13
  },
  passkeyList: {
    gap: appSpacing.sm
  },
  passkeyCard: {
    backgroundColor: appColors.background,
    borderColor: appColors.border,
    borderRadius: appRadii.md,
    borderWidth: 1,
    gap: appSpacing.xs,
    padding: appSpacing.md
  },
  passkeyCardActive: {
    backgroundColor: appColors.surfaceAccent,
    borderColor: appColors.textPrimary
  },
  passkeyHeader: {
    alignItems: "center",
    flexDirection: "row",
    gap: appSpacing.sm,
    justifyContent: "space-between"
  },
  passkeyTitle: {
    color: appColors.textPrimary,
    flex: 1,
    fontWeight: "700"
  },
  passkeyMeta: {
    color: appColors.textSecondary,
    fontSize: 12
  },
  emptyText: {
    color: "#7c2d12",
    fontSize: 13,
    fontWeight: "600"
  },
  actionsRow: {
    flexDirection: "row",
    gap: appSpacing.sm
  },
  actionButton: {
    flex: 1
  },
  pressed: {
    opacity: 0.88
  }
});
