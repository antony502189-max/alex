import React from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import { SectionCard } from "../ui/SectionCard";
import {
  getCreateStoryAudienceHint,
  STORY_AUDIENCE_OPTIONS,
  STORY_PRESETS,
  type StoryAudience,
  type StoryPreset
} from "./createStoryPresentation";

type CreateStoryAudienceSectionProps = {
  audience: StoryAudience;
  onSelectAudience: (audience: StoryAudience) => void;
  onSelectPreset: (preset: StoryPreset) => void;
  preset: StoryPreset;
};

export function CreateStoryAudienceSection({
  audience,
  onSelectAudience,
  onSelectPreset,
  preset
}: CreateStoryAudienceSectionProps) {
  return (
    <>
      <SectionCard title="Story look">
        <View style={styles.presetsRow}>
          {STORY_PRESETS.map((item, index) => (
            <Pressable
              key={`story-preset-${index}`}
              onPress={() => onSelectPreset(item)}
              style={({ pressed }) => [
                styles.presetChip,
                {
                  backgroundColor: item.backgroundFrom,
                  borderColor: item.backgroundTo
                },
                preset === item && styles.presetChipActive,
                pressed && styles.pressed
              ]}
            >
              <Text style={[styles.presetChipText, { color: item.textColor }]}>Aa</Text>
            </Pressable>
          ))}
        </View>
      </SectionCard>

      <SectionCard title="Audience" description={getCreateStoryAudienceHint(audience)}>
        <View style={styles.audienceRow}>
          {STORY_AUDIENCE_OPTIONS.map((option) => (
            <Pressable
              key={option.value}
              onPress={() => onSelectAudience(option.value)}
              style={({ pressed }) => [
                styles.audienceChip,
                audience === option.value && styles.audienceChipActive,
                pressed && styles.pressed
              ]}
            >
              <Text
                style={[
                  styles.audienceChipText,
                  audience === option.value && styles.audienceChipTextActive
                ]}
              >
                {option.label}
              </Text>
            </Pressable>
          ))}
        </View>
      </SectionCard>
    </>
  );
}

const styles = StyleSheet.create({
  presetsRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.md
  },
  presetChip: {
    alignItems: "center",
    borderRadius: appRadii.pill,
    borderWidth: 3,
    height: 56,
    justifyContent: "center",
    width: 56
  },
  presetChipActive: {
    transform: [{ scale: 1.08 }]
  },
  presetChipText: {
    fontWeight: "700"
  },
  audienceRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  audienceChip: {
    backgroundColor: appColors.surfaceAccent,
    borderRadius: appRadii.pill,
    paddingHorizontal: appSpacing.md,
    paddingVertical: appSpacing.sm
  },
  audienceChipActive: {
    backgroundColor: appColors.textPrimary
  },
  audienceChipText: {
    color: appColors.textPrimary,
    fontWeight: "600"
  },
  audienceChipTextActive: {
    color: appColors.inverse
  },
  pressed: {
    opacity: 0.9
  }
});
