import type {
  CallAdaptationProfile,
  CallJoinLink,
  CallMediaState,
  CallParticipant,
  CallSession,
  CallSignalEvent
} from "../../types";

export const CALL_PROFILE_OPTIONS: Array<{
  label: string;
  value: CallAdaptationProfile;
}> = [
  { label: "Balanced", value: "BALANCED" },
  { label: "Audio first", value: "AUDIO_PRIORITY" },
  { label: "Video first", value: "VIDEO_PRIORITY" }
];

export type CallControlIssue = {
  description: string;
  title: string;
  tone: "danger" | "info" | "warning";
};

export function formatCallDuration(startedAt: string, nowMs = Date.now()) {
  const elapsedSeconds = Math.max(0, Math.floor((nowMs - new Date(startedAt).getTime()) / 1000));
  const minutes = Math.floor(elapsedSeconds / 60);
  const seconds = elapsedSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

export function buildCallStatusText(params: {
  call: CallSession;
  currentUserId: string;
  durationLabel: string;
  myParticipant: CallParticipant | null;
}) {
  const { call, currentUserId, durationLabel, myParticipant } = params;

  if (call.status === "ACTIVE") {
    return `${call.kind === "VIDEO" ? "Video" : "Voice"} call - ${durationLabel}`;
  }

  if (myParticipant?.state === "RINGING" && call.createdByUserId !== currentUserId) {
    return `Incoming ${call.kind === "VIDEO" ? "video" : "voice"} call`;
  }

  if (call.createdByUserId === currentUserId) {
    return "Calling...";
  }

  return "Waiting for answer...";
}

export function buildCallMetaLabel(call: CallSession) {
  return call.mode === "GROUP" ? `${call.participants.length} participants` : "Direct call";
}

export function isCallLinkExpired(link: CallJoinLink, nowMs = Date.now()) {
  if (!link.expiresAt) {
    return false;
  }

  return new Date(link.expiresAt).getTime() <= nowMs;
}

export function buildCallLinkMeta(link: CallJoinLink, nowMs = Date.now()) {
  const expired = isCallLinkExpired(link, nowMs);
  const segments = [
    link.kind.toLowerCase(),
    link.revoked ? "revoked" : expired ? "expired" : `uses ${link.usageCount}`
  ];

  if (link.lastUsedAt) {
    segments.push(`last used ${new Date(link.lastUsedAt).toLocaleString()}`);
  }

  if (link.expiresAt) {
    segments.push(`${expired ? "expired" : "expires"} ${new Date(link.expiresAt).toLocaleString()}`);
  }

  return segments.join(" - ");
}

export function buildCallSignalLines(
  recentSignals: CallSignalEvent[],
  participants: CallParticipant[]
) {
  return recentSignals
    .slice(-4)
    .reverse()
    .map((signal) => {
      const actor =
        participants.find((participant) => participant.userId === signal.fromUserId)?.displayName ??
        "Participant";
      const normalizedType = signal.signalType.toUpperCase();
      const readableType =
        normalizedType === "MUTE"
          ? "muted the microphone"
          : normalizedType === "UNMUTE"
            ? "unmuted the microphone"
            : normalizedType === "CAMERA_ON"
              ? "turned the camera on"
              : normalizedType === "CAMERA_OFF"
                ? "turned the camera off"
                : normalizedType === "SPEAKER_ON"
                  ? "enabled speaker"
                  : normalizedType === "SPEAKER_OFF"
                    ? "disabled speaker"
                    : normalizedType === "SCREEN_SHARE_ON"
                      ? "started screen sharing"
                      : normalizedType === "SCREEN_SHARE_OFF"
                        ? "stopped screen sharing"
                        : normalizedType.toLowerCase();

      return `${actor} ${readableType}`;
    });
}

export function buildParticipantFlags(participant: CallParticipant) {
  const screenPrefix = participant.screenSharing ? "sharing screen - " : "";
  return `${screenPrefix}${
    participant.audioPublishingAllowed ? "mic allowed" : "mic restricted"
  } - ${participant.videoPublishingAllowed ? "camera allowed" : "camera restricted"} - ${
    participant.screenShareAllowed ? "screen allowed" : "screen restricted"
  }`;
}

export function buildParticipantTransportLine(
  participantUserId: string,
  peers: CallMediaState["peers"]
) {
  const peer = peers.find((item) => item.userId === participantUserId);
  if (!peer) {
    return "transport: pending";
  }

  const quality = `quality ${peer.quality.toLowerCase()}`;
  const rtt = peer.roundTripTimeMs != null ? ` / rtt ${peer.roundTripTimeMs}ms` : "";
  const bitrate = peer.sendBitrateKbps != null ? ` / ${peer.sendBitrateKbps}kbps` : "";
  const loss = peer.packetLossPercent != null ? ` / loss ${peer.packetLossPercent}%` : "";
  const restart = peer.restartingIce ? " / restarting" : "";
  return `transport: ${peer.connectionState} / ice ${peer.iceConnectionState} / tracks ${peer.remoteTrackCount} / ${quality}${rtt}${bitrate}${loss}${restart}`;
}

export function buildTransportLines(mediaState: CallMediaState) {
  return [
    `Phase: ${mediaState.phase.toLowerCase()}`,
    `Local stream: ${mediaState.localStreamReady ? "ready" : "not ready"}`,
    `Network quality: ${mediaState.networkQuality.toLowerCase()}`,
    `Media profile: ${mediaState.adaptationProfile.toLowerCase().replace("_", " ")}`,
    `Target video bitrate: ${mediaState.targetVideoBitrateKbps ?? 0} kbps`,
    `Estimated video uplink: ${mediaState.estimatedVideoSendBitrateKbps ?? 0} kbps`
  ];
}

export function isIncomingRingingCall(
  call: CallSession,
  currentUserId: string,
  myParticipant: CallParticipant | null
) {
  return myParticipant?.state === "RINGING" && call.createdByUserId !== currentUserId;
}

export function buildLeaveCallLabel(call: CallSession) {
  return call.status === "ACTIVE" ? "Leave call" : "Cancel call";
}

export function buildCallControlIssues(params: {
  call: CallSession;
  mediaState: CallMediaState;
  myParticipant: CallParticipant | null;
  screenShareEnabled: boolean;
}) {
  const {
    call,
    mediaState,
    myParticipant,
    screenShareEnabled
  } = params;
  const issues: CallControlIssue[] = [];

  if (call.status !== "ACTIVE") {
    issues.push({
      description:
        call.status === "RINGING"
          ? "Mute, camera, and screen-share controls unlock after the call connects."
          : "Live controls are unavailable until the call becomes active again.",
      title: "Controls waiting for connection",
      tone: "info"
    });
  }

  if (myParticipant?.audioPublishingAllowed === false) {
    issues.push({
      description: "The host or current moderation settings have disabled your microphone.",
      title: "Microphone unavailable",
      tone: "warning"
    });
  }

  if (call.kind === "VIDEO" && myParticipant?.videoPublishingAllowed === false) {
    issues.push({
      description: "The host or current moderation settings have disabled your camera.",
      title: "Camera unavailable",
      tone: "warning"
    });
  }

  if (screenShareEnabled) {
    if (mediaState.requiresNativeBuild) {
      issues.push({
        description:
          "Use a development or native build to expose the screen-share transport. Expo Go cannot start it.",
        title: "Screen sharing needs a native build",
        tone: "warning"
      });
    } else if (!mediaState.screenShareSupported) {
      issues.push({
        description: "This device or platform does not currently expose screen sharing for calls.",
        title: "Screen sharing unavailable on this device",
        tone: "info"
      });
    } else if (myParticipant?.screenShareAllowed === false) {
      issues.push({
        description: "The host or current moderation settings have disabled screen sharing.",
        title: "Screen sharing unavailable",
        tone: "warning"
      });
    }
  }

  if (mediaState.error) {
    issues.push({
      description: mediaState.error,
      title: "Media transport issue",
      tone: "danger"
    });
  } else if (call.status === "ACTIVE" && !mediaState.localStreamReady) {
    issues.push({
      description: "Your local microphone and camera stream are still preparing.",
      title: "Preparing local media",
      tone: "info"
    });
  }

  return issues;
}
