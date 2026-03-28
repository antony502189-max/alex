import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { AppButton } from "../ui/AppButton";
import { AppTextField } from "../ui/AppTextField";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { DevicePasskey } from "../../types";
import { formatPasskeyUsage } from "./profilePresentation";

type ProfilePasskeysSectionProps = {
  loadingPasskeys: boolean;
  localPasskeys: DevicePasskey[];
  onChangePasskeyLabel: (value: string) => void;
  onRefreshPasskeys: () => void;
  onRegisterPasskey: () => void;
  onRemovePasskey: (credentialId: string) => void;
  passkeyLabel: string;
  registeringPasskey: boolean;
  removingPasskeyId: string | null;
};

export function ProfilePasskeysSection({
  loadingPasskeys,
  localPasskeys,
  onChangePasskeyLabel,
  onRefreshPasskeys,
  onRegisterPasskey,
  onRemovePasskey,
  passkeyLabel,
  registeringPasskey,
  removingPasskeyId
}: ProfilePasskeysSectionProps) {
  return (
    <SectionCard
      description="Register a passkey on this device for faster sign-in later. This MVP stores the device credential locally and links it to the current account."
      title="Device passkeys"
    >
      <AppTextField
        onChangeText={onChangePasskeyLabel}
        placeholder="Passkey label (optional)"
        style={styles.input}
        value={passkeyLabel}
      />
      <View style={styles.buttonGroup}>
        <AppButton
          disabled={registeringPasskey}
          fullWidth
          onPress={onRegisterPasskey}
          variant="secondary"
        >
          {registeringPasskey ? "Registering..." : "Register passkey"}
        </AppButton>
        <AppButton
          disabled={loadingPasskeys}
          fullWidth
          onPress={onRefreshPasskeys}
          variant="secondary"
        >
          {loadingPasskeys ? "Refreshing..." : "Refresh passkeys"}
        </AppButton>
      </View>
      <View style={styles.list}>
        {localPasskeys.length === 0 ? (
          <Text style={styles.metaText}>No device passkeys registered on this phone yet.</Text>
        ) : (
          localPasskeys.map((passkey) => (
            <View key={passkey.credentialId} style={styles.card}>
              <Text style={styles.title}>{passkey.label ?? "Unnamed device passkey"}</Text>
              <Text style={styles.metaText}>
                Added {new Date(passkey.createdAt).toLocaleString()}
              </Text>
              <Text style={styles.metaText}>{formatPasskeyUsage(passkey)}</Text>
              <Text style={styles.metaText}>
                Removing it here clears only the local device copy.
              </Text>
              <AppButton
                disabled={removingPasskeyId === passkey.credentialId}
                fullWidth
                onPress={() => onRemovePasskey(passkey.credentialId)}
                variant="danger"
              >
                {removingPasskeyId === passkey.credentialId
                  ? "Removing..."
                  : "Remove local passkey"}
              </AppButton>
            </View>
          ))
        )}
      </View>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  input: {
    marginTop: 0
  },
  buttonGroup: {
    gap: appSpacing.sm + 2,
    marginTop: appSpacing.sm + 2
  },
  list: {
    gap: appSpacing.sm + 2,
    marginTop: appSpacing.sm + 2
  },
  card: {
    backgroundColor: "#f8fafc",
    borderRadius: appRadii.md,
    gap: 4,
    padding: appSpacing.md
  },
  title: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  metaText: {
    color: appColors.textSecondary,
    fontSize: 12,
    lineHeight: 18
  }
});
