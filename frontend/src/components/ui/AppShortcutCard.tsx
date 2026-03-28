import React from "react";
import { Pressable, StyleSheet, Text, type StyleProp, type ViewStyle } from "react-native";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";

type AppShortcutCardProps = {
  caption: string;
  onPress?: () => void;
  style?: StyleProp<ViewStyle>;
  title: string;
  tone?: "brand" | "dark" | "default" | "warm";
};

export function AppShortcutCard({
  caption,
  onPress,
  style,
  title,
  tone = "default"
}: AppShortcutCardProps) {
  const dark = tone === "dark";

  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.base,
        tone === "brand"
          ? styles.brand
          : tone === "dark"
            ? styles.dark
            : tone === "warm"
              ? styles.warm
              : styles.default,
        pressed && styles.pressed,
        style
      ]}
    >
      <Text style={[styles.title, dark && styles.darkTitle]}>{title}</Text>
      <Text style={[styles.caption, dark && styles.darkCaption]}>{caption}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    backgroundColor: appColors.surface,
    borderColor: appColors.border,
    borderRadius: appRadii.xl,
    borderWidth: 1,
    minHeight: 82,
    padding: appSpacing.md,
    width: "48.5%"
  },
  default: {
    backgroundColor: appColors.surface
  },
  brand: {
    backgroundColor: "#e8f0ff"
  },
  dark: {
    backgroundColor: "#162033",
    borderColor: "#162033"
  },
  warm: {
    backgroundColor: "#fff3df"
  },
  title: {
    color: appColors.textPrimary,
    fontSize: 15,
    fontWeight: "800"
  },
  darkTitle: {
    color: appColors.inverse
  },
  caption: {
    color: appColors.textSecondary,
    fontSize: 12,
    lineHeight: 18,
    marginTop: 6
  },
  darkCaption: {
    color: "#cbd5e1"
  },
  pressed: {
    opacity: 0.88
  }
});
