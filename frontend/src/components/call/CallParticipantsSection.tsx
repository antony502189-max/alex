import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { Avatar } from "../Avatar";
import { AppButton } from "../ui/AppButton";
import { AppChip } from "../ui/AppChip";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { CallMediaState, CallParticipant, CallSession } from "../../types";
import {
  buildParticipantFlags,
  buildParticipantTransportLine
} from "./callPresentation";

type CallParticipantsSectionProps = {
  call: CallSession;
  callModerationEnabled: boolean;
  currentUserId: string;
  mediaState: CallMediaState;
  onModerateParticipant: (
    userId: string,
    payload: {
      audioPublishingAllowed?: boolean;
      removeParticipant?: boolean;
      screenShareAllowed?: boolean;
      videoPublishingAllowed?: boolean;
    }
  ) => void;
};

function ParticipantModerationActions({
  onModerateParticipant,
  participant
}: {
  onModerateParticipant: CallParticipantsSectionProps["onModerateParticipant"];
  participant: CallParticipant;
}) {
  return (
    <View style={styles.moderationRow}>
      <AppButton
        onPress={() =>
          onModerateParticipant(participant.userId, {
            audioPublishingAllowed: !participant.audioPublishingAllowed
          })
        }
        size="sm"
      >
        {participant.audioPublishingAllowed ? "Mute mic" : "Allow mic"}
      </AppButton>
      <AppButton
        onPress={() =>
          onModerateParticipant(participant.userId, {
            videoPublishingAllowed: !participant.videoPublishingAllowed
          })
        }
        size="sm"
      >
        {participant.videoPublishingAllowed ? "Block camera" : "Allow camera"}
      </AppButton>
      <AppButton
        onPress={() =>
          onModerateParticipant(participant.userId, {
            screenShareAllowed: !participant.screenShareAllowed
          })
        }
        size="sm"
      >
        {participant.screenShareAllowed ? "Block screen" : "Allow screen"}
      </AppButton>
      <AppButton
        onPress={() =>
          onModerateParticipant(participant.userId, {
            removeParticipant: true
          })
        }
        size="sm"
        variant="danger"
      >
        Remove
      </AppButton>
    </View>
  );
}

export function CallParticipantsSection({
  call,
  callModerationEnabled,
  currentUserId,
  mediaState,
  onModerateParticipant
}: CallParticipantsSectionProps) {
  return (
    <SectionCard
      description="Participant state, transport telemetry, and moderation controls are grouped here."
      title="Participants"
    >
      {call.participants.map((participant) => {
        const isCurrentUser = participant.userId === currentUserId;

        return (
          <View key={participant.userId} style={styles.participantCard}>
            <View style={styles.participantRow}>
              <Avatar size={44} title={participant.displayName} uri={participant.photoUrl} />
              <View style={styles.participantBody}>
                <View style={styles.participantHeader}>
                  <Text style={styles.participantName}>{participant.displayName}</Text>
                  {isCurrentUser ? <AppChip tone="success">You</AppChip> : null}
                </View>
                <Text style={styles.participantMeta}>
                  {participant.phoneNumber ?? "phone-hidden"} - {participant.state.toLowerCase()}
                </Text>
                <Text style={styles.participantFlags}>{buildParticipantFlags(participant)}</Text>
                {!isCurrentUser ? (
                  <Text style={styles.participantTransport}>
                    {buildParticipantTransportLine(participant.userId, mediaState.peers)}
                  </Text>
                ) : null}
                {call.viewerCanModerate && callModerationEnabled && !isCurrentUser ? (
                  <ParticipantModerationActions
                    onModerateParticipant={onModerateParticipant}
                    participant={participant}
                  />
                ) : null}
              </View>
            </View>
          </View>
        );
      })}
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  participantCard: {
    backgroundColor: "#f8fbff",
    borderColor: appColors.border,
    borderRadius: appRadii.md,
    borderWidth: 1,
    padding: appSpacing.md
  },
  participantRow: {
    alignItems: "flex-start",
    flexDirection: "row",
    gap: appSpacing.md
  },
  participantBody: {
    flex: 1,
    gap: appSpacing.xs
  },
  participantHeader: {
    alignItems: "center",
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  participantName: {
    color: appColors.textPrimary,
    fontSize: 16,
    fontWeight: "700"
  },
  participantMeta: {
    color: appColors.textSecondary
  },
  participantFlags: {
    color: "#475569",
    fontSize: 12
  },
  participantTransport: {
    color: "#0f766e",
    fontSize: 12
  },
  moderationRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm,
    marginTop: appSpacing.xs
  }
});
