import React from "react";
import { StyleSheet, Text } from "react-native";
import { appColors } from "../../theme/tokens";
import { AppButton } from "../ui/AppButton";
import { AppTextField } from "../ui/AppTextField";
import { SectionCard } from "../ui/SectionCard";

type CallsJoinSectionProps = {
  callLinkToken: string;
  canJoinCallLink: boolean;
  onCallLinkTokenChange: (value: string) => void;
  onJoinCallLink: () => void;
};

export function CallsJoinSection({
  callLinkToken,
  canJoinCallLink,
  onCallLinkTokenChange,
  onJoinCallLink
}: CallsJoinSectionProps) {
  return (
    <SectionCard
      title="Join by call link"
      description="Paste a full call link, a `t.me/call/...` or `tg://call/...` link, or a raw call token to jump into an active room. Invite links and chat links will open their matching flow instead."
    >
      <AppTextField
        autoCapitalize="none"
        autoCorrect={false}
        onChangeText={onCallLinkTokenChange}
        placeholder="t.me/call/... , tg://call/... , token, or chat link"
        value={callLinkToken}
      />
      <Text style={styles.meta}>
        Valid call links can be used for direct or group calls. Invite links and app chat links are rerouted into the same internal join or chat flow used elsewhere in the app.
      </Text>
      <AppButton disabled={!canJoinCallLink} fullWidth onPress={onJoinCallLink} variant="primary">
        Join call
      </AppButton>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  meta: {
    color: appColors.textSecondary,
    lineHeight: 18
  }
});
