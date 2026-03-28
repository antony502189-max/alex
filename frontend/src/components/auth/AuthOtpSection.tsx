import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { appSpacing } from "../../theme/tokens";
import type { LoginCodeChallenge } from "../../types";
import { AppButton } from "../ui/AppButton";
import { AppPanel } from "../ui/AppPanel";
import { AppTextField } from "../ui/AppTextField";
import { AppToggleCard } from "../ui/AppToggleCard";
import { SectionCard } from "../ui/SectionCard";

type AuthOtpSectionProps = {
  challenge: LoginCodeChallenge | null;
  displayName: string;
  onDisplayNameChange: (value: string) => void;
  onPhoneNumberChange: (value: string) => void;
  onRequestCode: () => void;
  onResetOtpFlow: () => void;
  onToggleTrustSession: () => void;
  onTwoFactorPasswordChange: (value: string) => void;
  onVerificationCodeChange: (value: string) => void;
  onVerifyCode: () => void;
  onVerifyTwoFactor: () => void;
  phoneNumber: string;
  submitting: boolean;
  trustSession: boolean;
  twoFactorChallengeId: string | null;
  twoFactorHint: string | null;
  twoFactorPassword: string;
  verificationCode: string;
};

export function AuthOtpSection({
  challenge,
  displayName,
  onDisplayNameChange,
  onPhoneNumberChange,
  onRequestCode,
  onResetOtpFlow,
  onToggleTrustSession,
  onTwoFactorPasswordChange,
  onVerificationCodeChange,
  onVerifyCode,
  onVerifyTwoFactor,
  phoneNumber,
  submitting,
  trustSession,
  twoFactorChallengeId,
  twoFactorHint,
  twoFactorPassword,
  verificationCode
}: AuthOtpSectionProps) {
  if (twoFactorChallengeId) {
    return (
      <SectionCard
        description="Two-factor password is required to finish sign-in."
        title="Two-factor check"
      >
        {twoFactorHint ? (
          <AppPanel description={`Password hint: ${twoFactorHint}`} tone="warning" />
        ) : null}

        <AppTextField
          onChangeText={onTwoFactorPasswordChange}
          placeholder="Two-factor password"
          secureTextEntry
          value={twoFactorPassword}
        />

        <AppToggleCard
          active={trustSession}
          activeLabel="YES"
          description="Trusted sessions help reduce repeated security prompts on this device."
          inactiveLabel="NO"
          onPress={onToggleTrustSession}
          title={trustSession ? "Remember this device" : "Do not trust this device"}
        />

        <View style={styles.actionsRow}>
          <AppButton
            disabled={submitting}
            fullWidth
            onPress={onResetOtpFlow}
            style={styles.actionButton}
          >
            Restart
          </AppButton>
          <AppButton
            disabled={submitting || !twoFactorPassword.trim()}
            fullWidth
            onPress={onVerifyTwoFactor}
            style={styles.actionButton}
            variant="primary"
          >
            {submitting ? "Verifying..." : "Verify password"}
          </AppButton>
        </View>
      </SectionCard>
    );
  }

  if (challenge) {
    return (
      <SectionCard
        description={`Code requested for ${challenge.phoneNumber}. Expires at ${new Date(challenge.expiresAt).toLocaleTimeString()}.`}
        title="Code challenge"
      >
        <AppTextField
          autoCapitalize="none"
          keyboardType="number-pad"
          onChangeText={onVerificationCodeChange}
          placeholder="123456"
          value={verificationCode}
        />

        {challenge.debugCode ? (
          <Text style={styles.debugText}>Debug code: {challenge.debugCode}</Text>
        ) : null}

        <View style={styles.actionsRow}>
          <AppButton
            disabled={submitting}
            fullWidth
            onPress={onResetOtpFlow}
            style={styles.actionButton}
          >
            Back
          </AppButton>
          <AppButton
            disabled={submitting || !verificationCode.trim()}
            fullWidth
            onPress={onVerifyCode}
            style={styles.actionButton}
            variant="primary"
          >
            {submitting ? "Verifying..." : "Verify code"}
          </AppButton>
        </View>
      </SectionCard>
    );
  }

  return (
    <SectionCard
      description="Request a one-time code for the phone number you want to sign in with."
      title="Request login code"
    >
      <AppTextField
        autoCapitalize="none"
        keyboardType="phone-pad"
        onChangeText={onPhoneNumberChange}
        placeholder="+375291234567"
        value={phoneNumber}
      />
      <AppTextField
        onChangeText={onDisplayNameChange}
        placeholder="Display name"
        value={displayName}
      />
      <AppButton disabled={submitting} fullWidth onPress={onRequestCode} variant="primary">
        {submitting ? "Requesting..." : "Request code"}
      </AppButton>
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
  debugText: {
    color: "#7c2d12",
    fontSize: 13,
    fontWeight: "600"
  }
});
