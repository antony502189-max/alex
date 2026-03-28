import React from "react";
import type { ReactNode } from "react";
import { ScrollView, StyleSheet, type ScrollViewProps, type StyleProp, type ViewStyle } from "react-native";
import { appSpacing } from "../../theme/tokens";

type ScreenScrollSpacing = keyof typeof appSpacing;

type ScreenScrollViewProps = {
  children: ReactNode;
  contentContainerStyle?: StyleProp<ViewStyle>;
  gap?: ScreenScrollSpacing;
  padding?: ScreenScrollSpacing;
  paddingBottom?: ScreenScrollSpacing;
  paddingHorizontal?: ScreenScrollSpacing;
  paddingTop?: ScreenScrollSpacing;
} & Pick<
  ScrollViewProps,
  "keyboardShouldPersistTaps" | "showsVerticalScrollIndicator" | "style"
>;

function resolveSpacing(value?: ScreenScrollSpacing) {
  return value ? appSpacing[value] : undefined;
}

export function ScreenScrollView({
  children,
  contentContainerStyle,
  gap,
  keyboardShouldPersistTaps,
  padding,
  paddingBottom,
  paddingHorizontal,
  paddingTop,
  showsVerticalScrollIndicator,
  style
}: ScreenScrollViewProps) {
  return (
    <ScrollView
      contentContainerStyle={[
        styles.content,
        {
          gap: resolveSpacing(gap),
          padding: resolveSpacing(padding),
          paddingBottom: resolveSpacing(paddingBottom),
          paddingHorizontal: resolveSpacing(paddingHorizontal),
          paddingTop: resolveSpacing(paddingTop)
        },
        contentContainerStyle
      ]}
      keyboardShouldPersistTaps={keyboardShouldPersistTaps}
      showsVerticalScrollIndicator={showsVerticalScrollIndicator}
      style={style}
    >
      {children}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: {}
});
