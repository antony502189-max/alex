import React from "react";
import { Pressable, StyleSheet, Text, View, type StyleProp, type ViewStyle } from "react-native";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";

type AppToggleCardProps = {
  active: boolean;
  activeLabel?: string;
  description: string;
  disabled?: boolean;
  inactiveLabel?: string;
  onPress?: () => void;
  style?: StyleProp<ViewStyle>;
  title: string;
};

export function AppToggleCard({
  active,
  activeLabel = "ON",
  description,
  disabled = false,
  inactiveLabel = "OFF",
  onPress,
  style,
  title
}: AppToggleCardProps) {
  return (
    <Pressable
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.card,
        active && styles.cardActive,
        disabled && styles.disabled,
        pressed && !disabled && styles.pressed,
        style
      ]}
    >
      <View style={styles.body}>
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.description}>{description}</Text>
      </View>
      <View style={[styles.badge, active && styles.badgeActive]}>
        <Text style={[styles.badgeText, active && styles.badgeTextActive]}>
          {active ? activeLabel : inactiveLabel}
        </Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    alignItems: "center",
    backgroundColor: appColors.background,
    borderColor: appColors.border,
    borderRadius: appRadii.md,
    borderWidth: 1,
    flexDirection: "row",
    gap: appSpacing.md,
    paddingHorizontal: appSpacing.md,
    paddingVertical: appSpacing.md
  },
  cardActive: {
    backgroundColor: appColors.surfaceAccent,
    borderColor: appColors.textPrimary
  },
  body: {
    flex: 1,
    gap: appSpacing.xs
  },
  title: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  description: {
    color: appColors.textSecondary,
    lineHeight: 18
  },
  badge: {
    alignItems: "center",
    backgroundColor: appColors.surfaceAccent,
    borderRadius: appRadii.pill,
    minWidth: 48,
    paddingHorizontal: 10,
    paddingVertical: 6
  },
  badgeActive: {
    backgroundColor: appColors.textPrimary
  },
  badgeText: {
    color: appColors.textPrimary,
    fontSize: 12,
    fontWeight: "700"
  },
  badgeTextActive: {
    color: appColors.inverse
  },
  disabled: {
    opacity: 0.6
  },
  pressed: {
    opacity: 0.88
  }
});
