import React from "react";
import {
  StyleSheet,
  Text,
  View,
  type StyleProp,
  type TextStyle,
  type ViewStyle
} from "react-native";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";

type AppPanelProps = {
  children?: React.ReactNode;
  description?: React.ReactNode;
  descriptionStyle?: StyleProp<TextStyle>;
  style?: StyleProp<ViewStyle>;
  title?: React.ReactNode;
  titleStyle?: StyleProp<TextStyle>;
  tone?: "brand" | "danger" | "info" | "success" | "warning";
};

export function AppPanel({
  children,
  description,
  descriptionStyle,
  style,
  title,
  titleStyle,
  tone = "info"
}: AppPanelProps) {
  return (
    <View
      style={[
        styles.base,
        tone === "danger"
          ? styles.danger
          : tone === "success"
            ? styles.success
            : tone === "warning"
              ? styles.warning
              : tone === "brand"
                ? styles.brand
                : styles.info,
        style
      ]}
    >
      {title ? <Text style={[styles.title, titleStyle]}>{title}</Text> : null}
      {description ? <Text style={[styles.description, descriptionStyle]}>{description}</Text> : null}
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  base: {
    borderRadius: appRadii.md,
    gap: appSpacing.xs,
    padding: appSpacing.md
  },
  info: {
    backgroundColor: "#e0f2fe"
  },
  brand: {
    backgroundColor: "#eef2ff"
  },
  success: {
    backgroundColor: "#ecfccb"
  },
  warning: {
    backgroundColor: "#fef3c7"
  },
  danger: {
    backgroundColor: "#fff7ed"
  },
  title: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  description: {
    color: appColors.textSecondary,
    lineHeight: 20
  }
});
