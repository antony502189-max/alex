import React from "react";
import { Pressable, StyleSheet, Text, View, type StyleProp, type ViewStyle } from "react-native";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";

type AppActionTileProps = {
  body: string;
  disabled?: boolean;
  onPress?: () => void;
  style?: StyleProp<ViewStyle>;
  title: string;
  tone?: "danger" | "default";
};

export function AppActionTile({
  body,
  disabled = false,
  onPress,
  style,
  title,
  tone = "default"
}: AppActionTileProps) {
  return (
    <Pressable
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.base,
        tone === "danger" ? styles.danger : styles.default,
        disabled && styles.disabled,
        pressed && !disabled && styles.pressed,
        style
      ]}
    >
      <View style={styles.content}>
        <Text style={[styles.title, tone === "danger" && styles.dangerTitle]}>{title}</Text>
        <Text style={[styles.body, tone === "danger" && styles.dangerBody]}>{body}</Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    borderRadius: appRadii.md,
    flexGrow: 1,
    minWidth: 150,
    padding: appSpacing.md
  },
  content: {
    gap: appSpacing.xs
  },
  default: {
    backgroundColor: appColors.background
  },
  danger: {
    backgroundColor: "#fee2e2"
  },
  title: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  dangerTitle: {
    color: appColors.danger
  },
  body: {
    color: appColors.textSecondary,
    fontSize: 12,
    lineHeight: 18
  },
  dangerBody: {
    color: "#991b1b"
  },
  disabled: {
    opacity: 0.6
  },
  pressed: {
    opacity: 0.88
  }
});
