import React from "react";
import { AuthModeTabs } from "./AuthModeTabs";
import { AuthOtpSection } from "./AuthOtpSection";
import { AuthPasskeySection } from "./AuthPasskeySection";
import { AuthQrSection } from "./AuthQrSection";
import type { AuthScreenController } from "./useAuthScreenController";
import { AppHeader } from "../ui/AppHeader";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import { ScreenScrollView } from "../ui/ScreenScrollView";

type AuthScreenContentProps = {
  controller: AuthScreenController;
  mode: "LOGIN" | "ADD_ACCOUNT";
  onCancel?: () => void;
  subtitle: string;
  title: string;
};

export function AuthScreenContent({
  controller,
  mode,
  onCancel,
  subtitle,
  title
}: AuthScreenContentProps) {
  return (
    <ScreenScrollView gap="lg" keyboardShouldPersistTaps="handled" padding="xl">
      <AppHeader
        backLabel="Back"
        onBack={mode === "ADD_ACCOUNT" ? onCancel : undefined}
        subtitle={subtitle}
        title={title}
      />

      <AuthModeTabs authMode={controller.authMode} onSelectMode={controller.handleSelectAuthMode} />
      <ScreenFeedback error={controller.error} />

      {controller.authMode === "otp" ? (
        <AuthOtpSection
          challenge={controller.challenge}
          displayName={controller.displayName}
          onDisplayNameChange={controller.setDisplayName}
          onPhoneNumberChange={controller.setPhoneNumber}
          onRequestCode={() => void controller.handleRequestCode()}
          onResetOtpFlow={controller.resetOtpFlow}
          onToggleTrustSession={controller.toggleTrustSession}
          onTwoFactorPasswordChange={controller.setTwoFactorPassword}
          onVerificationCodeChange={controller.setVerificationCode}
          onVerifyCode={() => void controller.handleVerifyCode()}
          onVerifyTwoFactor={() => void controller.handleVerifyTwoFactor()}
          phoneNumber={controller.phoneNumber}
          submitting={controller.submitting}
          trustSession={controller.trustSession}
          twoFactorChallengeId={controller.twoFactorChallengeId}
          twoFactorHint={controller.twoFactorHint}
          twoFactorPassword={controller.twoFactorPassword}
          verificationCode={controller.verificationCode}
        />
      ) : null}

      {controller.authMode === "passkey" ? (
        <AuthPasskeySection
          availablePasskeys={controller.availablePasskeys}
          loadingPasskeys={controller.loadingPasskeys}
          onPhoneNumberChange={controller.setPhoneNumber}
          onRefreshPasskeys={controller.handleRefreshPasskeys}
          onSelectPasskey={controller.handleSelectPasskey}
          onUsePasskey={() => void controller.handlePasskeyLogin()}
          phoneNumber={controller.phoneNumber}
          primaryDisabled={controller.passkeyPrimaryDisabled}
          selectedPasskeyId={controller.selectedPasskeyId}
          submitting={controller.submitting}
        />
      ) : null}

      {controller.authMode === "qr" ? (
        <AuthQrSection
          autoPollingQr={controller.autoPollingQr}
          onBindDevice={() => void controller.handleBindQrLogin()}
          onCheckApproval={() => {
            void controller.handlePollQrLogin();
          }}
          onQrTokenChange={controller.handleQrTokenChange}
          qrDeviceSummary={controller.qrDeviceSummary}
          qrExpiresAt={controller.qrExpiresAt}
          qrStatusDescription={controller.qrStatusDescription}
          qrStatusVisible={controller.qrStatusVisible}
          qrToken={controller.qrToken}
          submitting={controller.submitting}
        />
      ) : null}
    </ScreenScrollView>
  );
}
