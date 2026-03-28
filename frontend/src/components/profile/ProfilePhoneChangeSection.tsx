import React from "react";
import { Text, View } from "react-native";
import { AppButton } from "../ui/AppButton";
import { AppTextField } from "../ui/AppTextField";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appSpacing } from "../../theme/tokens";
import type { PhoneChangeChallenge } from "../../types";

type ProfilePhoneChangeSectionProps = {
  changingPhone: boolean;
  newPhoneNumber: string;
  onChangeCode: (value: string) => void;
  onChangePhoneNumber: (value: string) => void;
  onRequestCode: () => void;
  onVerifyCode: () => void;
  phoneChangeChallenge: PhoneChangeChallenge | null;
  phoneChangeCode: string;
};

export function ProfilePhoneChangeSection({
  changingPhone,
  newPhoneNumber,
  onChangeCode,
  onChangePhoneNumber,
  onRequestCode,
  onVerifyCode,
  phoneChangeChallenge,
  phoneChangeCode
}: ProfilePhoneChangeSectionProps) {
  return (
    <SectionCard
      description="Request a verification code for a new phone number, then confirm it here."
      title="Change phone number"
    >
      <AppTextField
        keyboardType="phone-pad"
        onChangeText={onChangePhoneNumber}
        placeholder="New phone number"
        value={newPhoneNumber}
      />
      {phoneChangeChallenge ? (
        <>
          <Text style={{ color: appColors.textSecondary }}>
            Code requested for {phoneChangeChallenge.newPhoneNumber}. Expires at{" "}
            {new Date(phoneChangeChallenge.expiresAt).toLocaleString()}.
          </Text>
          {phoneChangeChallenge.debugCode ? (
            <Text style={{ color: appColors.textSecondary }}>
              Debug code: {phoneChangeChallenge.debugCode}
            </Text>
          ) : null}
          <AppTextField
            keyboardType="number-pad"
            onChangeText={onChangeCode}
            placeholder="Verification code"
            value={phoneChangeCode}
          />
        </>
      ) : null}
      <View style={{ gap: appSpacing.sm + 2, marginTop: appSpacing.sm + 2 }}>
        <AppButton
          disabled={changingPhone || !newPhoneNumber.trim()}
          fullWidth
          onPress={onRequestCode}
          variant="secondary"
        >
          {changingPhone && !phoneChangeChallenge ? "Requesting..." : "Request code"}
        </AppButton>
        {phoneChangeChallenge ? (
          <AppButton
            disabled={changingPhone || !phoneChangeCode.trim()}
            fullWidth
            onPress={onVerifyCode}
            variant="primary"
          >
            {changingPhone ? "Verifying..." : "Verify new number"}
          </AppButton>
        ) : null}
      </View>
    </SectionCard>
  );
}
