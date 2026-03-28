import React from "react";
import { StyleSheet, Text, View, type StyleProp, type ViewStyle } from "react-native";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";

type AppBannerProps = {
  message: string;
  style?: StyleProp<ViewStyle>;
  tone?: "danger" | "info" | "success";
};

export function AppBanner({ message, style, tone = "info" }: AppBannerProps) {
  return (
    <View
      style={[
        styles.base,
        tone === "danger"
          ? styles.danger
          : tone === "success"
            ? styles.success
            : styles.info,
        style
      ]}
    >
      <Text
        style={[
          styles.text,
          tone === "danger"
            ? styles.dangerText
            : tone === "success"
              ? styles.successText
              : styles.infoText
        ]}
      >
        {message}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  base: {
    borderRadius: appRadii.md,
    marginBottom: appSpacing.md,
    paddingHorizontal: appSpacing.md,
    paddingVertical: appSpacing.sm
  },
  info: {
    backgroundColor: appColors.surfaceMuted
  },
  success: {
    backgroundColor: "#ecfdf5"
  },
  danger: {
    backgroundColor: "#fef2f2"
  },
  text: {
    fontWeight: "600"
  },
  infoText: {
    color: appColors.brandText
  },
  successText: {
    color: appColors.success
  },
  dangerText: {
    color: appColors.danger
  }
});
