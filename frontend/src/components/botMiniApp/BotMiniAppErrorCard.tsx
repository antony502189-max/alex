import React from "react";
import { StyleSheet, View } from "react-native";
import { AppBanner } from "../ui/AppBanner";
import { AppButton } from "../ui/AppButton";
import { SectionCard } from "../ui/SectionCard";
import { appSpacing } from "../../theme/tokens";

type BotMiniAppErrorCardProps = {
  error: string;
  onClose: () => void;
  onRetry: () => void;
};

export function BotMiniAppErrorCard({
  error,
  onClose,
  onRetry
}: BotMiniAppErrorCardProps) {
  return (
    <SectionCard
      description="The signed launch session could not be prepared right now."
      title="Mini app unavailable"
    >
      <AppBanner message={error} tone="danger" />
      <View style={styles.row}>
        <AppButton onPress={onRetry} variant="primary">
          Retry
        </AppButton>
        <AppButton onPress={onClose}>Close</AppButton>
      </View>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    gap: appSpacing.sm
  }
});
