import React from "react";
import type { ReactNode } from "react";
import { StyleSheet, View, type StyleProp, type ViewStyle } from "react-native";
import { appSpacing } from "../../theme/tokens";

type ScreenStackSpacing = keyof typeof appSpacing;

type ScreenStackProps = {
  alignItems?: ViewStyle["alignItems"];
  children: ReactNode;
  direction?: ViewStyle["flexDirection"];
  flex?: number;
  flexWrap?: ViewStyle["flexWrap"];
  gap?: ScreenStackSpacing;
  justifyContent?: ViewStyle["justifyContent"];
  marginTop?: ScreenStackSpacing;
  paddingBottom?: ScreenStackSpacing;
  style?: StyleProp<ViewStyle>;
};

function resolveSpacing(value?: ScreenStackSpacing) {
  return value ? appSpacing[value] : undefined;
}

export function ScreenStack({
  alignItems,
  children,
  direction = "column",
  flex,
  flexWrap,
  gap,
  justifyContent,
  marginTop,
  paddingBottom,
  style
}: ScreenStackProps) {
  return (
    <View
      style={[
        styles.base,
        {
          alignItems,
          flex,
          flexDirection: direction,
          flexWrap,
          gap: resolveSpacing(gap),
          justifyContent,
          marginTop: resolveSpacing(marginTop),
          paddingBottom: resolveSpacing(paddingBottom)
        },
        style
      ]}
    >
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  base: {}
});
