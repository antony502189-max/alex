import React from "react";
import { View } from "react-native";
import { AppButton } from "../ui/AppButton";
import { SectionCard } from "../ui/SectionCard";
import { appSpacing } from "../../theme/tokens";

type ProfileDeviceSectionProps = {
  clearingPush: boolean;
  onClearPushToken: () => void;
  onRefreshPushToken: () => void;
  refreshingPush: boolean;
};

export function ProfileDeviceSection({
  clearingPush,
  onClearPushToken,
  onRefreshPushToken,
  refreshingPush
}: ProfileDeviceSectionProps) {
  return (
    <SectionCard
      description="Refresh or disable push notifications for the current mobile session."
      title="This device"
    >
      <View style={{ gap: appSpacing.sm + 2, marginTop: appSpacing.sm + 2 }}>
        <AppButton
          disabled={refreshingPush}
          fullWidth
          onPress={onRefreshPushToken}
          variant="secondary"
        >
          {refreshingPush ? "Refreshing..." : "Refresh push token"}
        </AppButton>
        <AppButton
          disabled={clearingPush}
          fullWidth
          onPress={onClearPushToken}
          variant="danger"
        >
          {clearingPush ? "Disabling..." : "Disable push on this device"}
        </AppButton>
      </View>
    </SectionCard>
  );
}
