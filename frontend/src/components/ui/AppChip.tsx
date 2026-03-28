import React from "react";
import {
  Pressable,
  StyleSheet,
  Text,
  View,
  type StyleProp,
  type TextStyle,
  type ViewStyle
} from "react-native";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";

type AppChipProps = {
  active?: boolean;
  children: React.ReactNode;
  onPress?: () => void;
  style?: StyleProp<ViewStyle>;
  textStyle?: StyleProp<TextStyle>;
  tone?: "brand" | "danger" | "default" | "muted" | "success" | "warning";
};

function resolveStyles(active: boolean, tone: NonNullable<AppChipProps["tone"]>) {
  if (tone === "danger") {
    return active
      ? { container: styles.dangerActive, label: styles.activeLabel }
      : { container: styles.danger, label: styles.dangerLabel };
  }

  if (tone === "brand") {
    return active
      ? { container: styles.brandActive, label: styles.activeLabel }
      : { container: styles.brand, label: styles.brandLabel };
  }

  if (tone === "success") {
    return active
      ? { container: styles.successActive, label: styles.activeLabel }
      : { container: styles.success, label: styles.successLabel };
  }

  if (tone === "warning") {
    return active
      ? { container: styles.warningActive, label: styles.activeLabel }
      : { container: styles.warning, label: styles.warningLabel };
  }

  if (tone === "muted") {
    return active
      ? { container: styles.mutedActive, label: styles.activeLabel }
      : { container: styles.muted, label: styles.mutedLabel };
  }

  return active
    ? { container: styles.defaultActive, label: styles.activeLabel }
    : { container: styles.default, label: styles.defaultLabel };
}

export function AppChip({
  active = false,
  children,
  onPress,
  style,
  textStyle,
  tone = "default"
}: AppChipProps) {
  const resolved = resolveStyles(active, tone);

  if (onPress) {
    return (
      <Pressable
        onPress={onPress}
        style={({ pressed }) => [
          styles.base,
          resolved.container,
          pressed && styles.pressed,
          style
        ]}
      >
        <Text style={[styles.label, resolved.label, textStyle]}>{children}</Text>
      </Pressable>
    );
  }

  return (
    <View style={[styles.base, resolved.container, style]}>
      <Text style={[styles.label, resolved.label, textStyle]}>{children}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  base: {
    borderRadius: appRadii.pill,
    paddingHorizontal: appSpacing.md,
    paddingVertical: appSpacing.sm
  },
  default: {
    backgroundColor: appColors.surfaceAccent
  },
  defaultActive: {
    backgroundColor: appColors.textPrimary
  },
  brand: {
    backgroundColor: appColors.surfaceAccent
  },
  brandActive: {
    backgroundColor: appColors.brand
  },
  danger: {
    backgroundColor: "#fee2e2"
  },
  dangerActive: {
    backgroundColor: appColors.danger
  },
  success: {
    backgroundColor: "#dcfce7"
  },
  successActive: {
    backgroundColor: appColors.success
  },
  warning: {
    backgroundColor: "#fef3c7"
  },
  warningActive: {
    backgroundColor: "#b45309"
  },
  muted: {
    backgroundColor: "#f1f5f9"
  },
  mutedActive: {
    backgroundColor: "#64748b"
  },
  label: {
    fontWeight: "600"
  },
  defaultLabel: {
    color: appColors.textPrimary
  },
  brandLabel: {
    color: appColors.brandText
  },
  dangerLabel: {
    color: appColors.danger
  },
  successLabel: {
    color: appColors.success
  },
  warningLabel: {
    color: "#92400e"
  },
  mutedLabel: {
    color: appColors.textSecondary
  },
  activeLabel: {
    color: appColors.inverse
  },
  pressed: {
    opacity: 0.88
  }
});
