import React from "react";
import type { ReactNode } from "react";
import { StyleSheet } from "react-native";
import type { StyleProp, ViewStyle } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import type { Edge } from "react-native-safe-area-context";
import { appColors, appSpacing } from "../../theme/tokens";

type AppScreenSpacing = keyof typeof appSpacing;

type AppScreenProps = {
  backgroundColor?: string;
  children: ReactNode;
  edges?: Edge[];
  padding?: AppScreenSpacing;
  paddingBottom?: AppScreenSpacing;
  paddingHorizontal?: AppScreenSpacing;
  paddingLeft?: AppScreenSpacing;
  paddingRight?: AppScreenSpacing;
  paddingTop?: AppScreenSpacing;
  paddingVertical?: AppScreenSpacing;
  style?: StyleProp<ViewStyle>;
};

function resolveSpacing(value?: AppScreenSpacing) {
  return value ? appSpacing[value] : undefined;
}

export function AppScreen({
  backgroundColor = appColors.background,
  children,
  edges,
  padding,
  paddingBottom,
  paddingHorizontal,
  paddingLeft,
  paddingRight,
  paddingTop,
  paddingVertical,
  style
}: AppScreenProps) {
  return (
    <SafeAreaView
      edges={edges}
      style={[
        styles.base,
        {
          backgroundColor,
          padding: resolveSpacing(padding),
          paddingBottom: resolveSpacing(paddingBottom),
          paddingHorizontal: resolveSpacing(paddingHorizontal),
          paddingLeft: resolveSpacing(paddingLeft),
          paddingRight: resolveSpacing(paddingRight),
          paddingTop: resolveSpacing(paddingTop),
          paddingVertical: resolveSpacing(paddingVertical)
        },
        style
      ]}
    >
      {children}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  base: {
    flex: 1
  }
});
