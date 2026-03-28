import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { appColors, appSpacing } from "../../theme/tokens";
import { AppButton } from "./AppButton";

type AppHeaderProps = {
  backLabel?: string;
  onBack?: () => void;
  rightSlot?: React.ReactNode;
  subtitle?: string | null;
  title: string;
};

export function AppHeader({
  backLabel = "Back",
  onBack,
  rightSlot,
  subtitle,
  title
}: AppHeaderProps) {
  return (
    <View style={styles.container}>
      <View style={styles.leading}>
        {onBack ? (
          <AppButton onPress={onBack} size="sm" variant="secondary">
            {backLabel}
          </AppButton>
        ) : null}
        <View style={styles.titleBlock}>
          <Text style={styles.title}>{title}</Text>
          {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
        </View>
      </View>
      {rightSlot ? <View>{rightSlot}</View> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
    gap: appSpacing.md,
    marginBottom: appSpacing.lg
  },
  leading: {
    alignItems: "center",
    flexDirection: "row",
    flex: 1,
    gap: appSpacing.md
  },
  titleBlock: {
    flex: 1,
    gap: appSpacing.xs
  },
  title: {
    color: appColors.textPrimary,
    fontSize: 24,
    fontWeight: "700"
  },
  subtitle: {
    color: appColors.textSecondary,
    fontSize: 13
  }
});
