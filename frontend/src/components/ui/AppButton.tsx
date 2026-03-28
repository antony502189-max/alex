import React from "react";
import {
  Pressable,
  StyleSheet,
  Text,
  type StyleProp,
  type TextStyle,
  type ViewStyle
} from "react-native";
import { appColors, appRadii } from "../../theme/tokens";

type AppButtonProps = {
  children: React.ReactNode;
  disabled?: boolean;
  fullWidth?: boolean;
  onPress?: () => void;
  size?: "sm" | "md";
  style?: StyleProp<ViewStyle>;
  textStyle?: StyleProp<TextStyle>;
  variant?: "primary" | "secondary" | "danger";
};

export function AppButton({
  children,
  disabled = false,
  fullWidth = false,
  onPress,
  size = "md",
  style,
  textStyle,
  variant = "secondary"
}: AppButtonProps) {
  return (
    <Pressable
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.base,
        size === "sm" ? styles.small : styles.medium,
        variant === "primary"
          ? styles.primary
          : variant === "danger"
            ? styles.danger
            : styles.secondary,
        fullWidth && styles.fullWidth,
        disabled && styles.disabled,
        pressed && !disabled && styles.pressed,
        style
      ]}
    >
      <Text
        style={[
          styles.label,
          size === "sm" ? styles.smallLabel : styles.mediumLabel,
          variant === "primary"
            ? styles.primaryLabel
            : variant === "danger"
              ? styles.dangerLabel
              : styles.secondaryLabel,
          textStyle
        ]}
      >
        {children}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    alignItems: "center",
    borderRadius: appRadii.md,
    justifyContent: "center"
  },
  secondary: {
    backgroundColor: appColors.surfaceAccent
  },
  primary: {
    backgroundColor: appColors.textPrimary
  },
  danger: {
    backgroundColor: "#fee2e2"
  },
  secondaryLabel: {
    color: appColors.brandText
  },
  primaryLabel: {
    color: appColors.inverse
  },
  dangerLabel: {
    color: appColors.danger
  },
  label: {
    fontWeight: "700"
  },
  small: {
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  medium: {
    paddingHorizontal: 16,
    paddingVertical: 14
  },
  smallLabel: {
    fontSize: 14
  },
  mediumLabel: {
    fontSize: 15
  },
  fullWidth: {
    width: "100%"
  },
  disabled: {
    opacity: 0.6
  },
  pressed: {
    opacity: 0.88
  }
});
