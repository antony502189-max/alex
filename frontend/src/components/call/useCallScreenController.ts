import { useEffect, useMemo, useState } from "react";
import type { CallMediaState, CallSession, CallSignalEvent } from "../../types";
import {
  buildCallControlIssues,
  buildCallSignalLines,
  buildCallStatusText,
  buildLeaveCallLabel,
  formatCallDuration,
  isIncomingRingingCall
} from "./callPresentation";

type UseCallScreenControllerParams = {
  call: CallSession;
  callScreenSharingEnabled: boolean;
  currentUserId: string;
  mediaState: CallMediaState;
  recentSignals: CallSignalEvent[];
};

export function useCallScreenController({
  call,
  callScreenSharingEnabled,
  currentUserId,
  mediaState,
  recentSignals
}: UseCallScreenControllerParams) {
  const [durationLabel, setDurationLabel] = useState(
    call.answeredAt ? formatCallDuration(call.answeredAt) : "0:00"
  );

  useEffect(() => {
    if (!call.answeredAt) {
      setDurationLabel("0:00");
      return;
    }

    setDurationLabel(formatCallDuration(call.answeredAt));
    const intervalId = setInterval(() => {
      setDurationLabel(formatCallDuration(call.answeredAt ?? call.startedAt));
    }, 1000);

    return () => {
      clearInterval(intervalId);
    };
  }, [call.answeredAt, call.startedAt]);

  const myParticipant = useMemo(
    () => call.participants.find((participant) => participant.userId === currentUserId) ?? null,
    [call.participants, currentUserId]
  );

  const otherParticipants = useMemo(
    () => call.participants.filter((participant) => participant.userId !== currentUserId),
    [call.participants, currentUserId]
  );

  const canToggleMicrophone = myParticipant?.audioPublishingAllowed !== false;
  const canToggleCamera = myParticipant?.videoPublishingAllowed !== false;
  const canToggleScreenShare =
    myParticipant?.screenShareAllowed !== false && mediaState.screenShareSupported;

  const primaryRemotePeer = useMemo(
    () => mediaState.peers.find((peer) => peer.remoteStreamUrl) ?? null,
    [mediaState.peers]
  );

  const headlineParticipant = otherParticipants[0] ?? myParticipant;
  const recentSignalLines = useMemo(
    () => buildCallSignalLines(recentSignals, call.participants),
    [call.participants, recentSignals]
  );

  const statusText = useMemo(
    () =>
      buildCallStatusText({
        call,
        currentUserId,
        durationLabel,
        myParticipant
      }),
    [call, currentUserId, durationLabel, myParticipant]
  );

  const incomingRinging = isIncomingRingingCall(call, currentUserId, myParticipant);
  const leaveLabel = buildLeaveCallLabel(call);
  const controlIssues = useMemo(
    () =>
      buildCallControlIssues({
        call,
        mediaState,
        myParticipant,
        screenShareEnabled: callScreenSharingEnabled
      }),
    [call, callScreenSharingEnabled, mediaState, myParticipant]
  );

  return {
    canToggleCamera,
    canToggleMicrophone,
    canToggleScreenShare,
    controlIssues,
    durationLabel,
    headlineParticipant,
    incomingRinging,
    leaveLabel,
    myParticipant,
    otherParticipants,
    primaryRemotePeer,
    recentSignalLines,
    statusText
  };
}

export type CallScreenController = ReturnType<typeof useCallScreenController>;
