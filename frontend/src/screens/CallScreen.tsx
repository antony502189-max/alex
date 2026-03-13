import React, { useEffect, useMemo, useState } from "react";
import {
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  View
} from "react-native";
import { Avatar } from "../components/Avatar";
import type {
  CallAdaptationProfile,
  CallJoinLink,
  CallMediaState,
  CallSession,
  CallSignalEvent
} from "../types";

let RTCViewComponent: null | React.ComponentType<{
  streamURL?: string;
  mirror?: boolean;
  objectFit?: "contain" | "cover";
  zOrder?: number;
  style?: object;
}> = null;

try {
  RTCViewComponent = require("react-native-webrtc").RTCView as typeof RTCViewComponent;
} catch {
  RTCViewComponent = null;
}

type CallScreenProps = {
  call: CallSession;
  callLinks: CallJoinLink[];
  chatTitle: string;
  chatPhotoUrl?: string | null;
  currentUserId: string;
  mediaState: CallMediaState;
  recentSignals: CallSignalEvent[];
  onAccept: () => void;
  onDecline: () => void;
  onLeave: () => void;
  onToggleMute: () => void;
  onToggleSpeaker: () => void;
  onToggleVideo: () => void;
  onToggleScreenShare: () => void;
  onSetAdaptationProfile: (profile: CallAdaptationProfile) => void;
  onCreateCallLink: (kind: "VOICE" | "VIDEO") => void;
  onModerateParticipant: (
    userId: string,
    payload: {
      audioPublishingAllowed?: boolean;
      videoPublishingAllowed?: boolean;
      screenShareAllowed?: boolean;
      removeParticipant?: boolean;
    }
  ) => void;
};

function formatDuration(startedAt: string) {
  const elapsedSeconds = Math.max(0, Math.floor((Date.now() - new Date(startedAt).getTime()) / 1000));
  const minutes = Math.floor(elapsedSeconds / 60);
  const seconds = elapsedSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

export function CallScreen({
  call,
  callLinks,
  chatTitle,
  chatPhotoUrl,
  currentUserId,
  mediaState,
  recentSignals,
  onAccept,
  onDecline,
  onLeave,
  onToggleMute,
  onToggleSpeaker,
  onToggleVideo,
  onToggleScreenShare,
  onSetAdaptationProfile,
  onCreateCallLink,
  onModerateParticipant
}: CallScreenProps) {
  const [durationLabel, setDurationLabel] = useState(
    call.answeredAt ? formatDuration(call.answeredAt) : "0:00"
  );

  useEffect(() => {
    if (!call.answeredAt) {
      setDurationLabel("0:00");
      return;
    }

    setDurationLabel(formatDuration(call.answeredAt));
    const intervalId = setInterval(() => {
      setDurationLabel(formatDuration(call.answeredAt ?? call.startedAt));
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
  const recentSignalLines = recentSignals.slice(-4).reverse().map((signal) => {
    const actor =
      call.participants.find((participant) => participant.userId === signal.fromUserId)?.displayName
      ?? "Participant";
    const normalizedType = signal.signalType.toUpperCase();
    const readableType =
      normalizedType === "MUTE" ? "muted the microphone"
      : normalizedType === "UNMUTE" ? "unmuted the microphone"
      : normalizedType === "CAMERA_ON" ? "turned the camera on"
      : normalizedType === "CAMERA_OFF" ? "turned the camera off"
      : normalizedType === "SPEAKER_ON" ? "enabled speaker"
      : normalizedType === "SPEAKER_OFF" ? "disabled speaker"
      : normalizedType === "SCREEN_SHARE_ON" ? "started screen sharing"
      : normalizedType === "SCREEN_SHARE_OFF" ? "stopped screen sharing"
      : normalizedType.toLowerCase();
    return `${actor} ${readableType}`;
  });

  const statusText =
    call.status === "ACTIVE"
      ? `${call.kind === "VIDEO" ? "Video" : "Voice"} call - ${durationLabel}`
      : myParticipant?.state === "RINGING" && call.createdByUserId !== currentUserId
        ? `Incoming ${call.kind === "VIDEO" ? "video" : "voice"} call`
        : call.createdByUserId === currentUserId
          ? "Calling..."
          : "Waiting for answer...";

  return (
    <SafeAreaView style={styles.screen}>
      <ScrollView contentContainerStyle={styles.content}>
        {call.kind === "VIDEO" && RTCViewComponent && primaryRemotePeer?.remoteStreamUrl ? (
          <View style={styles.videoStage}>
            <RTCViewComponent
              objectFit="cover"
              streamURL={primaryRemotePeer.remoteStreamUrl}
              style={styles.remoteVideo}
            />
            {mediaState.localStreamUrl && (mediaState.localVideoEnabled || mediaState.localScreenSharing) ? (
              <RTCViewComponent
                mirror
                objectFit="cover"
                streamURL={mediaState.localStreamUrl}
                style={styles.localVideo}
                zOrder={2}
              />
            ) : null}
          </View>
        ) : null}

        <View style={styles.heroCard}>
          <Avatar
            uri={chatPhotoUrl ?? headlineParticipant?.photoUrl}
            title={chatTitle || headlineParticipant?.displayName || "Call"}
            size={112}
          />
          <Text style={styles.title}>{chatTitle || headlineParticipant?.displayName || "Call"}</Text>
          <Text style={styles.subtitle}>{statusText}</Text>
          <Text style={styles.meta}>
            {call.mode === "GROUP" ? `${call.participants.length} participants` : "Direct call"}
          </Text>
        </View>

        <View style={styles.controlsRow}>
          <Pressable
            onPress={onToggleMute}
            disabled={!canToggleMicrophone}
            style={[
              styles.controlChip,
              !mediaState.localAudioEnabled && styles.controlChipActive,
              !canToggleMicrophone && styles.controlChipDisabled
            ]}
          >
            <Text
              style={[
                styles.controlChipText,
                !mediaState.localAudioEnabled && styles.controlChipTextActive,
                !canToggleMicrophone && styles.controlChipTextDisabled
              ]}
            >
              {mediaState.localAudioEnabled ? "Mic on" : "Mic off"}
            </Text>
          </Pressable>
          <Pressable
            onPress={onToggleSpeaker}
            style={[styles.controlChip, mediaState.speakerOn && styles.controlChipActive]}
          >
            <Text style={[styles.controlChipText, mediaState.speakerOn && styles.controlChipTextActive]}>
              {mediaState.speakerOn ? "Speaker" : "Earpiece"}
            </Text>
          </Pressable>
          {call.kind === "VIDEO" ? (
            <Pressable
              onPress={onToggleVideo}
              disabled={!canToggleCamera}
              style={[
                styles.controlChip,
                mediaState.localVideoEnabled && styles.controlChipActive,
                !canToggleCamera && styles.controlChipDisabled
              ]}
            >
              <Text
                style={[
                  styles.controlChipText,
                  mediaState.localVideoEnabled && styles.controlChipTextActive,
                  !canToggleCamera && styles.controlChipTextDisabled
                ]}
              >
                {mediaState.localVideoEnabled ? "Camera on" : "Camera off"}
              </Text>
            </Pressable>
          ) : null}
          {mediaState.screenShareSupported ? (
            <Pressable
              onPress={onToggleScreenShare}
              disabled={!canToggleScreenShare}
              style={[
                styles.controlChip,
                mediaState.localScreenSharing && styles.controlChipActive,
                !canToggleScreenShare && styles.controlChipDisabled
              ]}
            >
              <Text
                style={[
                  styles.controlChipText,
                  mediaState.localScreenSharing && styles.controlChipTextActive,
                  !canToggleScreenShare && styles.controlChipTextDisabled
                ]}
              >
                {mediaState.localScreenSharing ? "Stop share" : "Share screen"}
              </Text>
            </Pressable>
          ) : null}
        </View>

        <View style={styles.transportCard}>
          <Text style={styles.sectionTitle}>Media transport</Text>
          <Text style={styles.transportLine}>Phase: {mediaState.phase.toLowerCase()}</Text>
          <Text style={styles.transportLine}>
            Local stream: {mediaState.localStreamReady ? "ready" : "not ready"}
          </Text>
          <Text style={styles.transportLine}>
            Network quality: {mediaState.networkQuality.toLowerCase()}
          </Text>
          <Text style={styles.transportLine}>
            Media profile: {mediaState.adaptationProfile.toLowerCase().replace("_", " ")}
          </Text>
          <Text style={styles.transportLine}>
            Target video bitrate: {mediaState.targetVideoBitrateKbps ?? 0} kbps
          </Text>
          <Text style={styles.transportLine}>
            Estimated video uplink: {mediaState.estimatedVideoSendBitrateKbps ?? 0} kbps
          </Text>
          <View style={styles.profileRow}>
            {(["BALANCED", "AUDIO_PRIORITY", "VIDEO_PRIORITY"] as CallAdaptationProfile[]).map((profile) => (
              <Pressable
                key={profile}
                onPress={() => onSetAdaptationProfile(profile)}
                style={[
                  styles.profileChip,
                  mediaState.adaptationProfile === profile && styles.profileChipActive
                ]}
              >
                <Text
                  style={[
                    styles.profileChipText,
                    mediaState.adaptationProfile === profile && styles.profileChipTextActive
                  ]}
                >
                  {profile === "BALANCED"
                    ? "Balanced"
                    : profile === "AUDIO_PRIORITY"
                      ? "Audio first"
                      : "Video first"}
                </Text>
              </Pressable>
            ))}
          </View>
          {mediaState.requiresNativeBuild ? (
            <Text style={styles.transportWarning}>
              WebRTC native transport requires a development/native build. Expo Go will not expose the native module.
            </Text>
          ) : null}
          {mediaState.error ? (
            <Text style={styles.transportWarning}>{mediaState.error}</Text>
          ) : null}
        </View>

        {call.mode === "GROUP" && call.viewerCanManageLinks ? (
          <View style={styles.linksCard}>
            <View style={styles.linksHeader}>
              <Text style={styles.sectionTitle}>Call links</Text>
              <View style={styles.linksActions}>
                <Pressable onPress={() => onCreateCallLink(call.kind)} style={styles.linkActionButton}>
                  <Text style={styles.linkActionButtonText}>
                    New {call.kind === "VIDEO" ? "video" : "voice"}
                  </Text>
                </Pressable>
              </View>
            </View>
            {callLinks.length === 0 ? (
              <Text style={styles.transportLine}>No call links yet.</Text>
            ) : (
              callLinks.slice(0, 3).map((link) => (
                <View key={link.linkId} style={styles.linkRow}>
                  <View style={styles.linkText}>
                    <Text style={styles.linkTitle}>{link.label ?? link.shareUrl}</Text>
                    <Text style={styles.linkMeta}>
                      {link.kind.toLowerCase()} - uses {link.usageCount}
                      {link.expiresAt ? ` - expires ${new Date(link.expiresAt).toLocaleString()}` : ""}
                    </Text>
                  </View>
                </View>
              ))
            )}
          </View>
        ) : null}

        <View style={styles.participantsCard}>
          <Text style={styles.sectionTitle}>Participants</Text>
          {call.participants.map((participant) => (
            <View key={participant.userId} style={styles.participantRow}>
              <Avatar uri={participant.photoUrl} title={participant.displayName} size={44} />
              <View style={styles.participantText}>
                <Text style={styles.participantName}>
                  {participant.displayName}
                  {participant.userId === currentUserId ? " - You" : ""}
                </Text>
                <Text style={styles.participantMeta}>
                  {participant.phoneNumber ?? "phone-hidden"} - {participant.state.toLowerCase()}
                </Text>
                <Text style={styles.participantFlags}>
                  {participant.screenSharing ? "sharing screen - " : ""}
                  {participant.audioPublishingAllowed ? "mic allowed" : "mic restricted"}
                  {" - "}
                  {participant.videoPublishingAllowed ? "camera allowed" : "camera restricted"}
                  {" - "}
                  {participant.screenShareAllowed ? "screen allowed" : "screen restricted"}
                </Text>
                {participant.userId !== currentUserId ? (
                  <Text style={styles.participantTransport}>
                    {(() => {
                      const peer = mediaState.peers.find((item) => item.userId === participant.userId);
                      if (!peer) {
                        return "transport: pending";
                      }
                      const quality = `quality ${peer.quality.toLowerCase()}`;
                      const rtt = peer.roundTripTimeMs != null ? ` / rtt ${peer.roundTripTimeMs}ms` : "";
                      const bitrate = peer.sendBitrateKbps != null ? ` / ${peer.sendBitrateKbps}kbps` : "";
                      const loss = peer.packetLossPercent != null ? ` / loss ${peer.packetLossPercent}%` : "";
                      const restart = peer.restartingIce ? " / restarting" : "";
                      return `transport: ${peer.connectionState} / ice ${peer.iceConnectionState} / tracks ${peer.remoteTrackCount} / ${quality}${rtt}${bitrate}${loss}${restart}`;
                    })()}
                  </Text>
                ) : null}
                {call.viewerCanModerate && participant.userId !== currentUserId ? (
                  <View style={styles.moderationRow}>
                    <Pressable
                      onPress={() =>
                        onModerateParticipant(participant.userId, {
                          audioPublishingAllowed: !participant.audioPublishingAllowed
                        })
                      }
                      style={styles.moderationButton}
                    >
                      <Text style={styles.moderationButtonText}>
                        {participant.audioPublishingAllowed ? "Mute mic" : "Allow mic"}
                      </Text>
                    </Pressable>
                    <Pressable
                      onPress={() =>
                        onModerateParticipant(participant.userId, {
                          videoPublishingAllowed: !participant.videoPublishingAllowed
                        })
                      }
                      style={styles.moderationButton}
                    >
                      <Text style={styles.moderationButtonText}>
                        {participant.videoPublishingAllowed ? "Block camera" : "Allow camera"}
                      </Text>
                    </Pressable>
                    <Pressable
                      onPress={() =>
                        onModerateParticipant(participant.userId, {
                          screenShareAllowed: !participant.screenShareAllowed
                        })
                      }
                      style={styles.moderationButton}
                    >
                      <Text style={styles.moderationButtonText}>
                        {participant.screenShareAllowed ? "Block screen" : "Allow screen"}
                      </Text>
                    </Pressable>
                    <Pressable
                      onPress={() =>
                        onModerateParticipant(participant.userId, {
                          removeParticipant: true
                        })
                      }
                      style={[styles.moderationButton, styles.moderationDangerButton]}
                    >
                      <Text style={[styles.moderationButtonText, styles.moderationDangerButtonText]}>
                        Remove
                      </Text>
                    </Pressable>
                  </View>
                ) : null}
              </View>
            </View>
          ))}
        </View>

        {recentSignalLines.length > 0 ? (
          <View style={styles.signalsCard}>
            <Text style={styles.sectionTitle}>Call activity</Text>
            {recentSignalLines.map((line, index) => (
              <Text key={`${index}-${line}`} style={styles.signalLine}>
                {line}
              </Text>
            ))}
          </View>
        ) : null}
      </ScrollView>

      <View style={styles.footer}>
        {myParticipant?.state === "RINGING" && call.createdByUserId !== currentUserId ? (
          <>
            <Pressable onPress={onDecline} style={styles.dangerButton}>
              <Text style={styles.dangerButtonText}>Decline</Text>
            </Pressable>
            <Pressable onPress={onAccept} style={styles.primaryButton}>
              <Text style={styles.primaryButtonText}>Accept</Text>
            </Pressable>
          </>
        ) : (
          <Pressable onPress={onLeave} style={styles.hangupButton}>
            <Text style={styles.hangupButtonText}>
              {call.status === "ACTIVE" ? "Leave call" : "Cancel call"}
            </Text>
          </Pressable>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: "#ecfeff"
  },
  content: {
    padding: 20,
    gap: 16,
    paddingBottom: 120
  },
  heroCard: {
    borderRadius: 28,
    backgroundColor: "#ffffff",
    padding: 24,
    alignItems: "center",
    gap: 8
  },
  videoStage: {
    position: "relative",
    height: 320,
    borderRadius: 28,
    overflow: "hidden",
    backgroundColor: "#020617"
  },
  remoteVideo: {
    width: "100%",
    height: "100%"
  },
  localVideo: {
    position: "absolute",
    right: 16,
    bottom: 16,
    width: 110,
    height: 168,
    borderRadius: 18,
    backgroundColor: "#0f172a"
  },
  title: {
    fontSize: 28,
    fontWeight: "700",
    color: "#0f172a"
  },
  subtitle: {
    color: "#0f766e",
    fontSize: 16,
    fontWeight: "600"
  },
  meta: {
    color: "#64748b"
  },
  controlsRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10
  },
  controlChip: {
    flex: 1,
    minWidth: 100,
    borderRadius: 999,
    backgroundColor: "#cffafe",
    paddingHorizontal: 14,
    paddingVertical: 12,
    alignItems: "center"
  },
  controlChipActive: {
    backgroundColor: "#0f172a"
  },
  controlChipDisabled: {
    opacity: 0.45
  },
  controlChipText: {
    color: "#0f172a",
    fontWeight: "700"
  },
  controlChipTextActive: {
    color: "#ffffff"
  },
  controlChipTextDisabled: {
    color: "#475569"
  },
  participantsCard: {
    borderRadius: 22,
    backgroundColor: "#ffffff",
    padding: 18,
    gap: 14
  },
  linksCard: {
    borderRadius: 22,
    backgroundColor: "#ffffff",
    padding: 18,
    gap: 12
  },
  linksHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 10
  },
  linksActions: {
    flexDirection: "row",
    gap: 8
  },
  linkActionButton: {
    borderRadius: 12,
    backgroundColor: "#dbeafe",
    paddingHorizontal: 12,
    paddingVertical: 9
  },
  linkActionButtonText: {
    color: "#1d4ed8",
    fontWeight: "700"
  },
  linkRow: {
    borderRadius: 16,
    backgroundColor: "#f8fafc",
    padding: 12
  },
  linkText: {
    gap: 4
  },
  linkTitle: {
    color: "#0f172a",
    fontWeight: "600"
  },
  linkMeta: {
    color: "#64748b",
    fontSize: 12
  },
  signalsCard: {
    borderRadius: 22,
    backgroundColor: "#ffffff",
    padding: 18,
    gap: 10
  },
  transportCard: {
    borderRadius: 22,
    backgroundColor: "#ffffff",
    padding: 18,
    gap: 8
  },
  sectionTitle: {
    color: "#0f172a",
    fontWeight: "700",
    fontSize: 16
  },
  transportLine: {
    color: "#334155"
  },
  transportWarning: {
    color: "#b91c1c",
    lineHeight: 20
  },
  profileRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginTop: 4
  },
  profileChip: {
    borderRadius: 999,
    backgroundColor: "#dbeafe",
    paddingHorizontal: 12,
    paddingVertical: 8
  },
  profileChipActive: {
    backgroundColor: "#1d4ed8"
  },
  profileChipText: {
    color: "#1d4ed8",
    fontWeight: "700",
    fontSize: 12
  },
  profileChipTextActive: {
    color: "#ffffff"
  },
  participantRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12
  },
  participantText: {
    flex: 1
  },
  participantName: {
    color: "#0f172a",
    fontWeight: "600",
    fontSize: 16
  },
  participantMeta: {
    color: "#64748b",
    marginTop: 2
  },
  participantFlags: {
    color: "#475569",
    marginTop: 4,
    fontSize: 12
  },
  participantTransport: {
    color: "#0f766e",
    marginTop: 4,
    fontSize: 12
  },
  moderationRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginTop: 8
  },
  moderationButton: {
    borderRadius: 999,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 10,
    paddingVertical: 8
  },
  moderationButtonText: {
    color: "#0f172a",
    fontSize: 12,
    fontWeight: "700"
  },
  moderationDangerButton: {
    backgroundColor: "#fee2e2"
  },
  moderationDangerButtonText: {
    color: "#b91c1c"
  },
  signalLine: {
    color: "#334155",
    lineHeight: 20
  },
  footer: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: "row",
    gap: 12,
    paddingHorizontal: 20,
    paddingTop: 12,
    paddingBottom: 24,
    backgroundColor: "#ecfeff"
  },
  primaryButton: {
    flex: 1,
    borderRadius: 18,
    backgroundColor: "#0f172a",
    paddingVertical: 18,
    alignItems: "center"
  },
  primaryButtonText: {
    color: "#ffffff",
    fontWeight: "700",
    fontSize: 16
  },
  dangerButton: {
    flex: 1,
    borderRadius: 18,
    backgroundColor: "#fee2e2",
    paddingVertical: 18,
    alignItems: "center"
  },
  dangerButtonText: {
    color: "#b91c1c",
    fontWeight: "700",
    fontSize: 16
  },
  hangupButton: {
    flex: 1,
    borderRadius: 18,
    backgroundColor: "#dc2626",
    paddingVertical: 18,
    alignItems: "center"
  },
  hangupButtonText: {
    color: "#ffffff",
    fontWeight: "700",
    fontSize: 16
  }
});
