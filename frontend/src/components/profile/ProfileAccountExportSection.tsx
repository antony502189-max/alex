import React from "react";
import { Text, View } from "react-native";
import { AppButton } from "../ui/AppButton";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appSpacing } from "../../theme/tokens";
import type { AccountExportJob } from "../../types";

type ProfileAccountExportSectionProps = {
  accountExportJob: AccountExportJob | null;
  exportingAccount: boolean;
  onExportAccount: () => void;
};

export function ProfileAccountExportSection({
  accountExportJob,
  exportingAccount,
  onExportAccount
}: ProfileAccountExportSectionProps) {
  return (
    <SectionCard
      description="Request a JSON export of your account data and attachment metadata."
      title="Account export"
    >
      {accountExportJob ? (
        <View
          style={{
            backgroundColor: "#eff6ff",
            borderRadius: 14,
            gap: 4,
            padding: appSpacing.md
          }}
        >
          <Text style={{ color: appColors.textSecondary }}>Status: {accountExportJob.status}</Text>
          <Text style={{ color: appColors.textSecondary }}>
            Messages: {accountExportJob.messageCount}
          </Text>
          {accountExportJob.artifactLocation ? (
            <Text style={{ color: appColors.textSecondary }}>
              Artifact: {accountExportJob.artifactLocation}
            </Text>
          ) : null}
        </View>
      ) : null}
      <AppButton
        disabled={exportingAccount}
        fullWidth
        onPress={onExportAccount}
        variant="secondary"
      >
        {exportingAccount ? "Requesting..." : "Request account export"}
      </AppButton>
    </SectionCard>
  );
}
