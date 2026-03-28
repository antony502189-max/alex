import React from "react";
import { StyleSheet, Text, View } from "react-native";
import type { CallAdaptationProfile, CallMediaState } from "../../types";
import { appColors, appSpacing } from "../../theme/tokens";
import { AppPanel } from "../ui/AppPanel";
import { AppChip } from "../ui/AppChip";
import { SectionCard } from "../ui/SectionCard";
import { CALL_PROFILE_OPTIONS, buildTransportLines } from "./callPresentation";

type CallTransportPanelProps = {
  mediaState: CallMediaState;
  onSetAdaptationProfile: (profile: CallAdaptationProfile) => void;
};

export function CallTransportPanel({
  mediaState,
  onSetAdaptationProfile
}: CallTransportPanelProps) {
  return (
    <SectionCard
      description="Transport details help you understand what the call stack is doing right now."
      title="Media transport"
    >
      {buildTransportLines(mediaState).map((line) => (
        <Text key={line} style={styles.line}>
          {line}
        </Text>
      ))}

      <View style={styles.profileRow}>
        {CALL_PROFILE_OPTIONS.map((profile) => (
          <AppChip
            key={profile.value}
            active={mediaState.adaptationProfile === profile.value}
            onPress={() => onSetAdaptationProfile(profile.value)}
            tone="brand"
          >
            {profile.label}
          </AppChip>
        ))}
      </View>

      {mediaState.requiresNativeBuild ? (
        <AppPanel
          description="WebRTC native transport requires a development or native build. Expo Go will not expose the native module."
          tone="warning"
        />
      ) : null}

      {mediaState.error ? <AppPanel description={mediaState.error} tone="danger" /> : null}
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  line: {
    color: appColors.textSecondary
  },
  profileRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm,
    marginTop: appSpacing.xs
  }
});
