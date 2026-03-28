import React from "react";
import { StyleSheet, View } from "react-native";
import { appSpacing } from "../../theme/tokens";
import { AppChip } from "../ui/AppChip";
import type { AuthMode } from "./authPresentation";

type AuthModeTabsProps = {
  authMode: AuthMode;
  onSelectMode: (mode: AuthMode) => void;
};

const MODE_OPTIONS: Array<{ label: string; value: AuthMode }> = [
  { label: "OTP", value: "otp" },
  { label: "Passkey", value: "passkey" },
  { label: "QR", value: "qr" }
];

export function AuthModeTabs({ authMode, onSelectMode }: AuthModeTabsProps) {
  return (
    <View style={styles.row}>
      {MODE_OPTIONS.map((option) => (
        <AppChip
          key={option.value}
          active={authMode === option.value}
          onPress={() => onSelectMode(option.value)}
          style={styles.chip}
          tone="brand"
        >
          {option.label}
        </AppChip>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    gap: appSpacing.sm
  },
  chip: {
    flex: 1
  }
});
