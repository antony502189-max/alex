import React from "react";
import { Text, View } from "react-native";
import { AppButton } from "../ui/AppButton";
import { AppTextField } from "../ui/AppTextField";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appSpacing } from "../../theme/tokens";
import type { AccountDeletionJob } from "../../types";

type ProfileAccountDeletionSectionProps = {
  accountDeletionJob: AccountDeletionJob | null;
  deletionDelayDays: string;
  deletionReason: string;
  onChangeDelayDays: (value: string) => void;
  onChangeReason: (value: string) => void;
  onScheduleDeletion: () => void;
  schedulingDeletion: boolean;
};

export function ProfileAccountDeletionSection({
  accountDeletionJob,
  deletionDelayDays,
  deletionReason,
  onChangeDelayDays,
  onChangeReason,
  onScheduleDeletion,
  schedulingDeletion
}: ProfileAccountDeletionSectionProps) {
  return (
    <SectionCard
      description="Schedule account deletion with an optional reason and delay window."
      title="Account deletion"
    >
      <AppTextField
        onChangeText={onChangeReason}
        placeholder="Reason (optional)"
        value={deletionReason}
      />
      <AppTextField
        keyboardType="number-pad"
        onChangeText={onChangeDelayDays}
        placeholder="Delay in days"
        value={deletionDelayDays}
      />
      {accountDeletionJob ? (
        <View
          style={{
            backgroundColor: "#eff6ff",
            borderRadius: 14,
            gap: 4,
            padding: appSpacing.md
          }}
        >
          <Text style={{ color: appColors.textSecondary }}>
            Status: {accountDeletionJob.status}
          </Text>
          {accountDeletionJob.scheduledFor ? (
            <Text style={{ color: appColors.textSecondary }}>
              Scheduled for: {new Date(accountDeletionJob.scheduledFor).toLocaleString()}
            </Text>
          ) : null}
          {accountDeletionJob.reason ? (
            <Text style={{ color: appColors.textSecondary }}>
              Reason: {accountDeletionJob.reason}
            </Text>
          ) : null}
        </View>
      ) : null}
      <AppButton
        disabled={schedulingDeletion}
        fullWidth
        onPress={onScheduleDeletion}
        variant="danger"
      >
        {schedulingDeletion ? "Scheduling..." : "Schedule account deletion"}
      </AppButton>
    </SectionCard>
  );
}
