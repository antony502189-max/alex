import React from "react";
import {
  StyleSheet,
  Text,
  View,
  type StyleProp,
  type ViewStyle
} from "react-native";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";

type SectionCardProps = {
  children?: React.ReactNode;
  description?: string | null;
  style?: StyleProp<ViewStyle>;
  title?: string;
};

export function SectionCard({
  children,
  description,
  style,
  title
}: SectionCardProps) {
  return (
    <View style={[styles.card, style]}>
      {title || description ? (
        <View style={styles.header}>
          {title ? <Text style={styles.title}>{title}</Text> : null}
          {description ? <Text style={styles.description}>{description}</Text> : null}
        </View>
      ) : null}
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: appColors.surface,
    borderRadius: appRadii.lg,
    gap: appSpacing.md,
    padding: appSpacing.lg
  },
  header: {
    gap: appSpacing.xs
  },
  title: {
    color: appColors.textPrimary,
    fontSize: 18,
    fontWeight: "700"
  },
  description: {
    color: appColors.textSecondary,
    lineHeight: 20
  }
});
