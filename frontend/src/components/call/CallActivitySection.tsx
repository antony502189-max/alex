import React from "react";
import { StyleSheet, Text } from "react-native";
import { appColors } from "../../theme/tokens";
import { SectionCard } from "../ui/SectionCard";

type CallActivitySectionProps = {
  lines: string[];
};

export function CallActivitySection({ lines }: CallActivitySectionProps) {
  if (lines.length === 0) {
    return null;
  }

  return (
    <SectionCard
      description="Recent signaling events help explain why a call suddenly changed state."
      title="Call activity"
    >
      {lines.map((line, index) => (
        <Text key={`${index}-${line}`} style={styles.line}>
          {line}
        </Text>
      ))}
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  line: {
    color: appColors.textSecondary,
    lineHeight: 20
  }
});
