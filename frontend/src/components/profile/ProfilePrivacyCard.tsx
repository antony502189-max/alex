import React from "react";
import { StyleSheet, View } from "react-native";
import { AppChip } from "../ui/AppChip";
import { SectionCard } from "../ui/SectionCard";
import { appSpacing } from "../../theme/tokens";
import {
  PROFILE_PRIVACY_OPTIONS,
  type ProfilePrivacyValue
} from "./profilePresentation";

type ProfilePrivacyCardProps = {
  label: string;
  onChange: (next: ProfilePrivacyValue) => void;
  value: ProfilePrivacyValue;
};

export function ProfilePrivacyCard({
  label,
  onChange,
  value
}: ProfilePrivacyCardProps) {
  return (
    <SectionCard title={label}>
      <View style={styles.options}>
        {PROFILE_PRIVACY_OPTIONS.map((option) => (
          <AppChip active={value === option} key={option} onPress={() => onChange(option)}>
            {option}
          </AppChip>
        ))}
      </View>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  options: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  }
});
