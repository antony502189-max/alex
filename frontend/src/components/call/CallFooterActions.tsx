import React from "react";
import { StyleSheet, View } from "react-native";
import { appColors, appSpacing } from "../../theme/tokens";
import { AppButton } from "../ui/AppButton";

type CallFooterActionsProps = {
  incomingRinging: boolean;
  leaveLabel: string;
  onAccept: () => void;
  onDecline: () => void;
  onLeave: () => void;
};

export function CallFooterActions({
  incomingRinging,
  leaveLabel,
  onAccept,
  onDecline,
  onLeave
}: CallFooterActionsProps) {
  return (
    <View style={styles.footer}>
      {incomingRinging ? (
        <>
          <AppButton fullWidth onPress={onDecline} style={styles.actionButton} variant="danger">
            Decline
          </AppButton>
          <AppButton fullWidth onPress={onAccept} style={styles.actionButton} variant="primary">
            Accept
          </AppButton>
        </>
      ) : (
        <AppButton fullWidth onPress={onLeave} style={styles.singleButton} variant="danger">
          {leaveLabel}
        </AppButton>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  footer: {
    backgroundColor: appColors.background,
    flexDirection: "row",
    gap: appSpacing.md,
    paddingBottom: appSpacing.xl + 4,
    paddingHorizontal: appSpacing.xl,
    paddingTop: appSpacing.md
  },
  actionButton: {
    flex: 1
  },
  singleButton: {
    flex: 1
  }
});
