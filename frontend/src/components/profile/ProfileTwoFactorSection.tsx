import React from "react";
import { Text } from "react-native";
import { AppButton } from "../ui/AppButton";
import { AppTextField } from "../ui/AppTextField";
import { SectionCard } from "../ui/SectionCard";
import { appColors } from "../../theme/tokens";

type ProfileTwoFactorSectionProps = {
  onChangeDisablePassword: (value: string) => void;
  onChangeHint: (value: string) => void;
  onChangePassword: (value: string) => void;
  onDisableTwoFactor: () => void;
  onEnableTwoFactor: () => void;
  twoFactorDisablePassword: string;
  twoFactorEnabled: boolean;
  twoFactorEnabledAt: string | null;
  twoFactorHint: string;
  twoFactorPassword: string;
  updatingTwoFactor: boolean;
};

export function ProfileTwoFactorSection({
  onChangeDisablePassword,
  onChangeHint,
  onChangePassword,
  onDisableTwoFactor,
  onEnableTwoFactor,
  twoFactorDisablePassword,
  twoFactorEnabled,
  twoFactorEnabledAt,
  twoFactorHint,
  twoFactorPassword,
  updatingTwoFactor
}: ProfileTwoFactorSectionProps) {
  return (
    <SectionCard
      description={
        twoFactorEnabled
          ? `Enabled${twoFactorEnabledAt ? ` since ${new Date(twoFactorEnabledAt).toLocaleString()}` : ""}. Trusted sessions can approve QR logins for new devices.`
          : "Require a password after OTP verification. Trusted sessions can approve QR logins for new devices."
      }
      title="Two-factor password"
    >
      {twoFactorEnabled ? (
        <>
          {twoFactorHint ? <Text style={{ color: appColors.textSecondary }}>Hint: {twoFactorHint}</Text> : null}
          <AppTextField
            onChangeText={onChangeDisablePassword}
            placeholder="Current two-factor password"
            secureTextEntry
            value={twoFactorDisablePassword}
          />
          <AppButton
            disabled={updatingTwoFactor || !twoFactorDisablePassword.trim()}
            fullWidth
            onPress={onDisableTwoFactor}
            variant="danger"
          >
            {updatingTwoFactor ? "Updating..." : "Disable two-factor"}
          </AppButton>
        </>
      ) : (
        <>
          <AppTextField
            onChangeText={onChangePassword}
            placeholder="New two-factor password"
            secureTextEntry
            value={twoFactorPassword}
          />
          <AppTextField
            onChangeText={onChangeHint}
            placeholder="Password hint (optional)"
            value={twoFactorHint}
          />
          <AppButton
            disabled={updatingTwoFactor || !twoFactorPassword.trim()}
            fullWidth
            onPress={onEnableTwoFactor}
            variant="secondary"
          >
            {updatingTwoFactor ? "Updating..." : "Enable two-factor"}
          </AppButton>
        </>
      )}
    </SectionCard>
  );
}
