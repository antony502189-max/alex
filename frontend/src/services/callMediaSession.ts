import { api } from "./api";
import type {
  CallAdaptationProfile,
  CallIceServer,
  CallMediaPolicy,
  CallMediaPeerState,
  CallMediaState,
  CallNetworkQuality,
  CallSession,
  CallSignalEvent
} from "../types";

type CallMediaListener = (state: CallMediaState) => void;
type SignalSender = (
  toUserId: string,
  signalType: string,
  payload: Record<string, unknown>
) => Promise<void>;

const INITIAL_STATE: CallMediaState = {
  callId: null,
  phase: "IDLE",
  adaptationProfile: "BALANCED",
  networkQuality: "UNKNOWN",
  localAudioEnabled: true,
  localVideoEnabled: false,
  localScreenSharing: false,
  localStreamReady: false,
  localStreamUrl: null,
  screenShareSupported: false,
  estimatedVideoSendBitrateKbps: null,
  targetVideoBitrateKbps: null,
  speakerOn: true,
  peers: [],
  error: null,
  requiresNativeBuild: false
};

const DEFAULT_MEDIA_POLICY: CallMediaPolicy = {
  videoBitrateHighKbps: 1400,
  videoBitrateMediumKbps: 800,
  videoBitrateLowKbps: 240,
  screenShareBitrateKbps: 1600,
  statsSampleIntervalSeconds: 4,
  degradedConnectionRttMs: 220,
  poorConnectionRttMs: 450,
  degradedConnectionPacketLossPercent: 5,
  poorConnectionPacketLossPercent: 12
};

type PeerStatsSnapshot = {
  timestampMs: number;
  videoBytesSent: number;
  packetsSent: number;
  packetsLost: number;
};

function parseSignalPayload(payload: string) {
  try {
    return JSON.parse(payload) as Record<string, unknown>;
  } catch {
    return null;
  }
}

export class CallMediaSessionService {
  private listeners = new Set<CallMediaListener>();
  private state: CallMediaState = INITIAL_STATE;
  private activeCall: CallSession | null = null;
  private currentUserId: string | null = null;
  private signalSender: SignalSender | null = null;
  private localStream: any | null = null;
  private screenShareStream: any | null = null;
  private rtcModule: any | null = null;
  private iceServers: CallIceServer[] = [];
  private mediaPolicy: CallMediaPolicy = DEFAULT_MEDIA_POLICY;
  private peerConnections = new Map<string, any>();
  private pendingCandidates = new Map<string, Array<Record<string, unknown>>>();
  private iceRestartTimeouts = new Map<string, ReturnType<typeof setTimeout>>();
  private statsSnapshots = new Map<string, PeerStatsSnapshot>();
  private statsIntervalId: ReturnType<typeof setInterval> | null = null;

  subscribe(listener: CallMediaListener) {
    this.listeners.add(listener);
    listener(this.state);
    return () => {
      this.listeners.delete(listener);
    };
  }

  getState() {
    return this.state;
  }

  async start(
    call: CallSession,
    currentUserId: string,
    token: string,
    signalSender: SignalSender
  ) {
    const sameCall = this.activeCall?.callId === call.callId;
    this.activeCall = call;
    this.currentUserId = currentUserId;
    this.signalSender = signalSender;

    if (!sameCall) {
      await this.stopInternal(false);
      this.activeCall = call;
      this.currentUserId = currentUserId;
      this.signalSender = signalSender;
    }

    if (this.state.phase !== "READY") {
      this.setState({
        callId: call.callId,
        phase: "STARTING",
        adaptationProfile: this.state.adaptationProfile,
        networkQuality: "UNKNOWN",
        localAudioEnabled: true,
        localVideoEnabled: call.kind === "VIDEO",
        localScreenSharing: false,
        localStreamReady: false,
        localStreamUrl: null,
        screenShareSupported: false,
        estimatedVideoSendBitrateKbps: null,
        targetVideoBitrateKbps: null,
        speakerOn: this.state.speakerOn,
        peers: [],
        error: null,
        requiresNativeBuild: false
      });
    }

    try {
      if (!this.rtcModule) {
        this.rtcModule = await import("react-native-webrtc");
      }

      if (!this.localStream) {
        const rtcConfig = await api.getCallRtcConfig(token);
        this.iceServers = rtcConfig.iceServers;
        this.mediaPolicy = rtcConfig.mediaPolicy ?? DEFAULT_MEDIA_POLICY;
        this.localStream = await this.rtcModule.mediaDevices.getUserMedia({
          audio: true,
          video: call.kind === "VIDEO"
            ? {
                facingMode: "user",
                width: 640,
                height: 480,
                frameRate: 24
              }
            : false
        });
      }

      this.applyLocalTrackState(this.state.localAudioEnabled, this.state.localVideoEnabled);
      await this.syncCall(call);
      this.startStatsPolling();
      await this.applyAdaptiveVideoPolicy();
      this.setState({
        ...this.state,
        callId: call.callId,
        phase: "READY",
        localStreamReady: true,
        localStreamUrl: this.getLocalPreviewStream()?.toURL?.() ?? null,
        screenShareSupported: this.isScreenShareSupported(),
        targetVideoBitrateKbps: this.resolveTargetVideoBitrateKbps(this.state.networkQuality),
        error: null,
        requiresNativeBuild: false
      });
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Unable to initialize call media session";
      const requiresNativeBuild =
        /native module|dev build|expo go|not installed|undefined/i.test(message);
      this.setState({
        ...this.state,
        callId: call.callId,
        phase: "ERROR",
        localStreamReady: false,
        localStreamUrl: null,
        screenShareSupported: this.isScreenShareSupported(),
        error: message,
        requiresNativeBuild
      });
    }
  }

  async syncCall(call: CallSession) {
    if (!this.activeCall || this.activeCall.callId !== call.callId) {
      return;
    }
    this.activeCall = call;

    const selfParticipant =
      call.participants.find((participant) => participant.userId === this.currentUserId) ?? null;
    if (selfParticipant) {
      let nextAudioEnabled = this.state.localAudioEnabled;
      let nextVideoEnabled = this.state.localVideoEnabled;

      if (!selfParticipant.audioPublishingAllowed && nextAudioEnabled) {
        nextAudioEnabled = false;
      }
      if (!selfParticipant.videoPublishingAllowed && nextVideoEnabled) {
        nextVideoEnabled = false;
      }

      if (this.state.localScreenSharing && !selfParticipant.screenShareAllowed) {
        await this.stopScreenShareInternal();
      }

      this.applyLocalTrackState(nextAudioEnabled, nextVideoEnabled);
      if (
        nextAudioEnabled !== this.state.localAudioEnabled ||
        nextVideoEnabled !== this.state.localVideoEnabled
      ) {
        this.setState({
          ...this.state,
          localAudioEnabled: nextAudioEnabled,
          localVideoEnabled: nextVideoEnabled,
          localStreamUrl: this.getLocalPreviewStream()?.toURL?.() ?? null
        });
      }
    }

    const activeParticipantIds = new Set(
      call.participants
        .filter((participant) => participant.userId !== this.currentUserId)
        .filter((participant) => !["LEFT", "DECLINED", "MISSED"].includes(participant.state))
        .map((participant) => participant.userId)
    );

    for (const [userId, peerConnection] of this.peerConnections.entries()) {
      if (activeParticipantIds.has(userId)) {
        continue;
      }
      peerConnection.close();
      this.peerConnections.delete(userId);
      this.pendingCandidates.delete(userId);
    }

    for (const participant of call.participants) {
      if (participant.userId === this.currentUserId) {
        continue;
      }
      if (!activeParticipantIds.has(participant.userId)) {
        continue;
      }
      this.ensurePeerConnection(participant.userId);
    }

    if (this.currentUserId === call.createdByUserId) {
      for (const participant of call.participants) {
        if (participant.userId === this.currentUserId) {
          continue;
        }
        if (!activeParticipantIds.has(participant.userId)) {
          continue;
        }
        await this.ensureOffer(participant.userId);
      }
    }

    this.refreshPeerStates();
    await this.applyAdaptiveVideoPolicy();
    this.setState({
      ...this.state,
      localStreamUrl: this.getLocalPreviewStream()?.toURL?.() ?? null,
      screenShareSupported: this.isScreenShareSupported(),
      targetVideoBitrateKbps: this.resolveTargetVideoBitrateKbps(this.state.networkQuality)
    });
  }

  async handleSignal(signal: CallSignalEvent) {
    if (!this.activeCall || signal.callId !== this.activeCall.callId) {
      return;
    }

    const signalType = signal.signalType.trim().toUpperCase();
    if (!["OFFER", "ANSWER", "ICE_CANDIDATE"].includes(signalType)) {
      return;
    }

    const payload = parseSignalPayload(signal.payload);
    if (!payload) {
      return;
    }

    if (!this.rtcModule) {
      return;
    }

    switch (signalType) {
      case "OFFER":
        await this.handleOffer(signal.fromUserId, payload);
        break;
      case "ANSWER":
        await this.handleAnswer(signal.fromUserId, payload);
        break;
      case "ICE_CANDIDATE":
        await this.handleCandidate(signal.fromUserId, payload);
        break;
      default:
        break;
    }
  }

  async setAudioEnabled(enabled: boolean) {
    const selfParticipant =
      this.activeCall?.participants.find((participant) => participant.userId === this.currentUserId) ?? null;
    const effectiveEnabled = selfParticipant?.audioPublishingAllowed === false ? false : enabled;
    this.applyLocalTrackState(effectiveEnabled, this.state.localVideoEnabled);
    this.setState({
      ...this.state,
      localAudioEnabled: effectiveEnabled
    });
  }

  async setVideoEnabled(enabled: boolean) {
    const selfParticipant =
      this.activeCall?.participants.find((participant) => participant.userId === this.currentUserId) ?? null;
    const effectiveEnabled = selfParticipant?.videoPublishingAllowed === false ? false : enabled;
    this.applyLocalTrackState(this.state.localAudioEnabled, effectiveEnabled);
    this.setState({
      ...this.state,
      localVideoEnabled: effectiveEnabled,
      localStreamUrl: this.getLocalPreviewStream()?.toURL?.() ?? null
    });
    await this.applyAdaptiveVideoPolicy();
  }

  async startScreenShare() {
    if (!this.rtcModule || !this.localStream || !this.isScreenShareSupported()) {
      throw new Error("Screen sharing is not available in this build");
    }
    const selfParticipant =
      this.activeCall?.participants.find((participant) => participant.userId === this.currentUserId) ?? null;
    if (selfParticipant?.screenShareAllowed === false) {
      throw new Error("Screen sharing is disabled for this participant");
    }
    if (this.screenShareStream) {
      return;
    }

    this.screenShareStream = await this.rtcModule.mediaDevices.getDisplayMedia({
      video: true,
      audio: false
    });
    const screenTrack = this.screenShareStream?.getVideoTracks?.()?.[0] ?? null;
    if (!screenTrack) {
      throw new Error("Unable to capture screen");
    }

    const stopShare = () => {
      void this.stopScreenShareInternal().catch(() => undefined);
    };
    screenTrack.onended = stopShare;
    await this.setActiveVideoTrack(screenTrack);
    this.setState({
      ...this.state,
      localScreenSharing: true,
      localStreamUrl: this.getLocalPreviewStream()?.toURL?.() ?? null,
      localStreamReady: true
    });
    await this.applyAdaptiveVideoPolicy();
  }

  async stopScreenShare() {
    await this.stopScreenShareInternal();
  }

  async setAdaptationProfile(profile: CallAdaptationProfile) {
    this.setState({
      ...this.state,
      adaptationProfile: profile,
      targetVideoBitrateKbps: this.resolveTargetVideoBitrateKbps(this.state.networkQuality)
    });
    await this.applyAdaptiveVideoPolicy();
  }

  setSpeakerEnabled(enabled: boolean) {
    this.setState({
      ...this.state,
      speakerOn: enabled
    });
  }

  async stop() {
    await this.stopInternal(true);
  }

  private async stopInternal(resetState: boolean) {
    if (this.statsIntervalId) {
      clearInterval(this.statsIntervalId);
      this.statsIntervalId = null;
    }
    this.statsSnapshots.clear();

    for (const timeoutId of this.iceRestartTimeouts.values()) {
      clearTimeout(timeoutId);
    }
    this.iceRestartTimeouts.clear();

    for (const peerConnection of this.peerConnections.values()) {
      try {
        peerConnection.close();
      } catch {
      }
    }
    this.peerConnections.clear();
    this.pendingCandidates.clear();

    if (this.screenShareStream) {
      try {
        for (const track of this.screenShareStream.getTracks()) {
          track.stop?.();
        }
      } catch {
      }
    }
    this.screenShareStream = null;

    if (this.localStream) {
      try {
        for (const track of this.localStream.getTracks()) {
          track.stop?.();
        }
      } catch {
      }
    }
    this.localStream = null;
    this.iceServers = [];
    this.mediaPolicy = DEFAULT_MEDIA_POLICY;

    if (resetState) {
      this.activeCall = null;
      this.currentUserId = null;
      this.signalSender = null;
      this.setState(INITIAL_STATE);
    }
  }

  private ensurePeerConnection(userId: string) {
    const existing = this.peerConnections.get(userId);
    if (existing) {
      return existing;
    }

    const peerConnection = new this.rtcModule.RTCPeerConnection({
      iceServers: this.iceServers.map((server) => ({
        urls: server.url,
        username: server.username ?? undefined,
        credential: server.credential ?? undefined
      }))
    });

    if (this.localStream) {
      for (const track of this.localStream.getAudioTracks()) {
        peerConnection.addTrack(track, this.localStream);
      }
      const primaryVideoTrack = this.getPrimaryVideoTrack();
      if (primaryVideoTrack) {
        peerConnection.addTrack(primaryVideoTrack, this.getLocalPreviewStream() ?? this.localStream);
      }
    }

    peerConnection.onicecandidate = (event: { candidate?: Record<string, unknown> | null }) => {
      if (!event.candidate || !this.signalSender) {
        return;
      }
      void this.signalSender(userId, "ICE_CANDIDATE", {
        candidate: event.candidate
      }).catch(() => undefined);
    };

    peerConnection.ontrack = (event: { streams?: Array<{ getTracks: () => unknown[]; toURL?: () => string }> }) => {
      const stream = event.streams?.[0];
      const remoteTrackCount = stream?.getTracks().length ?? 0;
      this.updatePeerState(userId, {
        remoteTrackCount,
        remoteStreamUrl: stream?.toURL?.() ?? null
      });
    };

    peerConnection.onconnectionstatechange = () => {
      this.maybeScheduleIceRestart(userId, peerConnection);
      this.updatePeerState(userId, {
        connectionState: peerConnection.connectionState ?? "unknown",
        restartingIce: this.iceRestartTimeouts.has(userId)
      });
    };

    peerConnection.oniceconnectionstatechange = () => {
      if (!["failed", "disconnected"].includes(peerConnection.iceConnectionState ?? "")) {
        this.clearIceRestart(userId);
      }
      this.maybeScheduleIceRestart(userId, peerConnection);
      this.updatePeerState(userId, {
        iceConnectionState: peerConnection.iceConnectionState ?? "unknown",
        restartingIce: this.iceRestartTimeouts.has(userId)
      });
    };

    peerConnection.onsignalingstatechange = () => {
      if (peerConnection.signalingState === "stable") {
        this.clearIceRestart(userId);
      }
      this.updatePeerState(userId, {
        signalingState: peerConnection.signalingState ?? "stable",
        restartingIce: this.iceRestartTimeouts.has(userId)
      });
    };

    this.peerConnections.set(userId, peerConnection);
    this.refreshPeerStates();
    return peerConnection;
  }

  private async ensureOffer(userId: string, iceRestart = false) {
    const peerConnection = this.ensurePeerConnection(userId);
    if (!this.signalSender) {
      return;
    }
    if (peerConnection.signalingState !== "stable") {
      return;
    }
    if (peerConnection.localDescription?.type === "offer" && !peerConnection.currentRemoteDescription) {
      return;
    }

    const offer = await peerConnection.createOffer({
      offerToReceiveAudio: true,
      offerToReceiveVideo: this.activeCall?.kind === "VIDEO" || this.state.localScreenSharing,
      iceRestart
    });
    await peerConnection.setLocalDescription(offer);
    await this.signalSender(userId, "OFFER", {
      description: peerConnection.localDescription ?? offer
    });
  }

  private async handleOffer(fromUserId: string, payload: Record<string, unknown>) {
    if (!this.signalSender) {
      return;
    }
    const description = payload.description as Record<string, unknown> | undefined;
    if (!description) {
      return;
    }
    const peerConnection = this.ensurePeerConnection(fromUserId);
    this.clearIceRestart(fromUserId);
    const remoteDescription = new this.rtcModule.RTCSessionDescription(description);
    await peerConnection.setRemoteDescription(remoteDescription);
    await this.flushPendingCandidates(fromUserId);
    const answer = await peerConnection.createAnswer();
    await peerConnection.setLocalDescription(answer);
    await this.signalSender(fromUserId, "ANSWER", {
      description: peerConnection.localDescription ?? answer
    });
  }

  private async handleAnswer(fromUserId: string, payload: Record<string, unknown>) {
    const description = payload.description as Record<string, unknown> | undefined;
    if (!description) {
      return;
    }
    const peerConnection = this.ensurePeerConnection(fromUserId);
    this.clearIceRestart(fromUserId);
    const remoteDescription = new this.rtcModule.RTCSessionDescription(description);
    await peerConnection.setRemoteDescription(remoteDescription);
    await this.flushPendingCandidates(fromUserId);
  }

  private async handleCandidate(fromUserId: string, payload: Record<string, unknown>) {
    const candidate = payload.candidate as Record<string, unknown> | undefined;
    if (!candidate) {
      return;
    }
    const peerConnection = this.ensurePeerConnection(fromUserId);
    if (!peerConnection.remoteDescription) {
      const current = this.pendingCandidates.get(fromUserId) ?? [];
      current.push(candidate);
      this.pendingCandidates.set(fromUserId, current);
      return;
    }
    await peerConnection.addIceCandidate(new this.rtcModule.RTCIceCandidate(candidate));
  }

  private async flushPendingCandidates(userId: string) {
    const pending = this.pendingCandidates.get(userId);
    if (!pending || pending.length === 0) {
      return;
    }
    const peerConnection = this.peerConnections.get(userId);
    if (!peerConnection) {
      return;
    }
    this.pendingCandidates.delete(userId);
    for (const candidate of pending) {
      await peerConnection.addIceCandidate(new this.rtcModule.RTCIceCandidate(candidate));
    }
  }

  private applyLocalTrackState(audioEnabled: boolean, videoEnabled: boolean) {
    if (!this.localStream) {
      return;
    }
    for (const audioTrack of this.localStream.getAudioTracks()) {
      audioTrack.enabled = audioEnabled;
    }
    for (const videoTrack of this.localStream.getVideoTracks()) {
      videoTrack.enabled = videoEnabled;
    }
    if (this.screenShareStream) {
      for (const videoTrack of this.screenShareStream.getVideoTracks()) {
        videoTrack.enabled = true;
      }
    }
  }

  private isScreenShareSupported() {
    return !!this.rtcModule?.mediaDevices?.getDisplayMedia;
  }

  private getPrimaryVideoTrack() {
    return this.screenShareStream?.getVideoTracks?.()?.[0]
      ?? this.localStream?.getVideoTracks?.()?.[0]
      ?? null;
  }

  private getLocalPreviewStream() {
    return this.screenShareStream ?? this.localStream;
  }

  private async setActiveVideoTrack(track: any | null) {
    for (const [userId, peerConnection] of this.peerConnections.entries()) {
      const senders = peerConnection.getSenders?.() ?? [];
      const sender =
        senders.find((candidate: any) => candidate.track?.kind === "video") ?? null;
      if (sender?.replaceTrack) {
        await sender.replaceTrack(track);
      } else if (track && this.getLocalPreviewStream()) {
        peerConnection.addTrack(track, this.getLocalPreviewStream());
      }
      this.clearIceRestart(userId);
    }
  }

  private async stopScreenShareInternal() {
    if (!this.screenShareStream) {
      if (this.state.localScreenSharing) {
        this.setState({
          ...this.state,
          localScreenSharing: false,
          localStreamUrl: this.getLocalPreviewStream()?.toURL?.() ?? null
        });
      }
      return;
    }

    const cameraTrack = this.localStream?.getVideoTracks?.()?.[0] ?? null;
    await this.setActiveVideoTrack(cameraTrack);
    try {
      for (const track of this.screenShareStream.getTracks()) {
        track.stop?.();
      }
    } catch {
    }
    this.screenShareStream = null;
    this.setState({
      ...this.state,
      localScreenSharing: false,
      localStreamUrl: this.getLocalPreviewStream()?.toURL?.() ?? null,
      localStreamReady: !!this.localStream
    });
    await this.applyAdaptiveVideoPolicy();
  }

  private startStatsPolling() {
    if (this.statsIntervalId) {
      clearInterval(this.statsIntervalId);
    }
    const intervalMs = Math.max(2, this.mediaPolicy.statsSampleIntervalSeconds) * 1000;
    this.statsIntervalId = setInterval(() => {
      void this.collectPeerQualityStats().catch(() => undefined);
    }, intervalMs);
    void this.collectPeerQualityStats().catch(() => undefined);
  }

  private async collectPeerQualityStats() {
    if (this.peerConnections.size === 0) {
      const nextQuality = "UNKNOWN" as CallNetworkQuality;
      if (
        this.state.networkQuality !== nextQuality
        || this.state.estimatedVideoSendBitrateKbps !== null
      ) {
        this.setState({
          ...this.state,
          networkQuality: nextQuality,
          estimatedVideoSendBitrateKbps: null,
          targetVideoBitrateKbps: this.resolveTargetVideoBitrateKbps(nextQuality)
        });
        await this.applyAdaptiveVideoPolicy();
      }
      return;
    }

    let aggregateBitrate: number | null = null;
    let aggregateQuality: CallNetworkQuality = "EXCELLENT";

    for (const [userId, peerConnection] of this.peerConnections.entries()) {
      const metrics = await this.extractPeerMetrics(userId, peerConnection);
      aggregateBitrate = this.maxNullable(aggregateBitrate, metrics.sendBitrateKbps);
      aggregateQuality = this.worstQuality(aggregateQuality, metrics.quality);
      this.updatePeerState(userId, metrics);
    }

    const nextNetworkQuality = aggregateQuality;
    if (
      nextNetworkQuality !== this.state.networkQuality
      || aggregateBitrate !== this.state.estimatedVideoSendBitrateKbps
    ) {
      this.setState({
        ...this.state,
        networkQuality: nextNetworkQuality,
        estimatedVideoSendBitrateKbps: aggregateBitrate,
        targetVideoBitrateKbps: this.resolveTargetVideoBitrateKbps(nextNetworkQuality)
      });
      await this.applyAdaptiveVideoPolicy();
    }
  }

  private async extractPeerMetrics(userId: string, peerConnection: any) {
    const reports = this.normalizeStatsReports(await peerConnection.getStats?.());
    let roundTripTimeMs: number | null = null;
    let videoBytesSent = 0;
    let packetsSent = 0;
    let packetsLost = 0;

    for (const report of reports) {
      if (!report || typeof report !== "object") {
        continue;
      }

      const reportType = String((report as Record<string, unknown>).type ?? "");
      if (reportType === "candidate-pair" && roundTripTimeMs == null) {
        const currentRoundTripTime = Number((report as Record<string, unknown>).currentRoundTripTime ?? NaN);
        if (Number.isFinite(currentRoundTripTime) && currentRoundTripTime >= 0) {
          roundTripTimeMs = Math.round(currentRoundTripTime * 1000);
        }
      }

      const kind =
        String((report as Record<string, unknown>).kind ?? (report as Record<string, unknown>).mediaType ?? "");
      if (reportType === "outbound-rtp" && kind === "video") {
        videoBytesSent = Math.max(videoBytesSent, Number((report as Record<string, unknown>).bytesSent ?? 0));
        packetsSent = Math.max(packetsSent, Number((report as Record<string, unknown>).packetsSent ?? 0));
      }
      if (reportType === "remote-inbound-rtp" && kind === "video") {
        packetsLost = Math.max(packetsLost, Number((report as Record<string, unknown>).packetsLost ?? 0));
        if (roundTripTimeMs == null) {
          const rtt = Number((report as Record<string, unknown>).roundTripTime ?? NaN);
          if (Number.isFinite(rtt) && rtt >= 0) {
            roundTripTimeMs = Math.round(rtt * 1000);
          }
        }
      }
    }

    const nowMs = Date.now();
    const previous = this.statsSnapshots.get(userId);
    this.statsSnapshots.set(userId, {
      timestampMs: nowMs,
      videoBytesSent,
      packetsSent,
      packetsLost
    });

    let sendBitrateKbps: number | null = null;
    let packetLossPercent: number | null = null;
    if (previous && nowMs > previous.timestampMs) {
      const elapsedMs = nowMs - previous.timestampMs;
      const sentDelta = Math.max(0, videoBytesSent - previous.videoBytesSent);
      sendBitrateKbps = Math.round((sentDelta * 8) / elapsedMs);

      const packetsSentDelta = Math.max(0, packetsSent - previous.packetsSent);
      const packetsLostDelta = Math.max(0, packetsLost - previous.packetsLost);
      const totalPackets = packetsSentDelta + packetsLostDelta;
      if (totalPackets > 0) {
        packetLossPercent = Math.round((packetsLostDelta / totalPackets) * 100);
      }
    }

    return {
      roundTripTimeMs,
      sendBitrateKbps,
      packetLossPercent,
      quality: this.classifyPeerQuality(peerConnection, sendBitrateKbps, packetLossPercent, roundTripTimeMs)
    };
  }

  private normalizeStatsReports(rawStats: unknown) {
    if (!rawStats) {
      return [] as Array<Record<string, unknown>>;
    }
    if (Array.isArray(rawStats)) {
      return rawStats as Array<Record<string, unknown>>;
    }
    if (typeof (rawStats as { forEach?: unknown }).forEach === "function") {
      const reports: Array<Record<string, unknown>> = [];
      (rawStats as { forEach: (callback: (value: Record<string, unknown>) => void) => void })
        .forEach((value) => {
          reports.push(value);
        });
      return reports;
    }
    if (typeof rawStats === "object") {
      return Object.values(rawStats as Record<string, Record<string, unknown>>);
    }
    return [] as Array<Record<string, unknown>>;
  }

  private classifyPeerQuality(
    peerConnection: any,
    sendBitrateKbps: number | null,
    packetLossPercent: number | null,
    roundTripTimeMs: number | null
  ): CallNetworkQuality {
    if (["failed", "disconnected"].includes(peerConnection.iceConnectionState ?? "")) {
      return "POOR";
    }
    if (
      (roundTripTimeMs != null && roundTripTimeMs >= this.mediaPolicy.poorConnectionRttMs)
      || (packetLossPercent != null && packetLossPercent >= this.mediaPolicy.poorConnectionPacketLossPercent)
      || (sendBitrateKbps != null && sendBitrateKbps <= Math.round(this.mediaPolicy.videoBitrateLowKbps * 0.7))
    ) {
      return "POOR";
    }
    if (
      (roundTripTimeMs != null && roundTripTimeMs >= this.mediaPolicy.degradedConnectionRttMs)
      || (packetLossPercent != null && packetLossPercent >= this.mediaPolicy.degradedConnectionPacketLossPercent)
      || (sendBitrateKbps != null && sendBitrateKbps <= Math.round(this.mediaPolicy.videoBitrateMediumKbps * 0.7))
    ) {
      return "FAIR";
    }
    if (
      (roundTripTimeMs != null && roundTripTimeMs <= 120)
      && (packetLossPercent == null || packetLossPercent <= 1)
      && (sendBitrateKbps == null || sendBitrateKbps >= Math.round(this.mediaPolicy.videoBitrateHighKbps * 0.8))
    ) {
      return "EXCELLENT";
    }
    if (
      (roundTripTimeMs == null || roundTripTimeMs <= this.mediaPolicy.degradedConnectionRttMs)
      && (packetLossPercent == null || packetLossPercent <= this.mediaPolicy.degradedConnectionPacketLossPercent)
    ) {
      return "GOOD";
    }
    return "FAIR";
  }

  private resolveTargetVideoBitrateKbps(quality: CallNetworkQuality) {
    if (this.state.localScreenSharing) {
      return this.mediaPolicy.screenShareBitrateKbps;
    }
    if (!this.state.localVideoEnabled) {
      return null;
    }

    switch (this.state.adaptationProfile) {
      case "AUDIO_PRIORITY":
        if (quality === "POOR") {
          return this.mediaPolicy.videoBitrateLowKbps;
        }
        if (quality === "FAIR") {
          return this.mediaPolicy.videoBitrateLowKbps;
        }
        return this.mediaPolicy.videoBitrateMediumKbps;
      case "VIDEO_PRIORITY":
        if (quality === "POOR") {
          return this.mediaPolicy.videoBitrateMediumKbps;
        }
        return this.mediaPolicy.videoBitrateHighKbps;
      case "BALANCED":
      default:
        if (quality === "POOR") {
          return this.mediaPolicy.videoBitrateLowKbps;
        }
        if (quality === "FAIR") {
          return this.mediaPolicy.videoBitrateMediumKbps;
        }
        if (quality === "UNKNOWN") {
          return this.mediaPolicy.videoBitrateMediumKbps;
        }
        return this.mediaPolicy.videoBitrateHighKbps;
    }
  }

  private async applyAdaptiveVideoPolicy() {
    const targetBitrateKbps = this.resolveTargetVideoBitrateKbps(this.state.networkQuality);
    const degradationPreference =
      this.state.adaptationProfile === "AUDIO_PRIORITY"
        ? "maintain-framerate"
        : this.state.adaptationProfile === "VIDEO_PRIORITY"
          ? "maintain-resolution"
          : "balanced";

    for (const peerConnection of this.peerConnections.values()) {
      const sender =
        (peerConnection.getSenders?.() ?? []).find((candidate: any) => candidate.track?.kind === "video") ?? null;
      if (!sender?.getParameters || !sender?.setParameters) {
        continue;
      }

      try {
        const parameters = sender.getParameters?.() ?? {};
        const encodings = Array.isArray(parameters.encodings) && parameters.encodings.length > 0
          ? [...parameters.encodings]
          : [{}];
        encodings[0] = {
          ...encodings[0],
          maxBitrate: targetBitrateKbps != null ? targetBitrateKbps * 1000 : undefined
        };
        parameters.encodings = encodings;
        parameters.degradationPreference = degradationPreference;
        await sender.setParameters(parameters);
      } catch {
      }
    }
  }

  private worstQuality(left: CallNetworkQuality, right: CallNetworkQuality): CallNetworkQuality {
    const order: CallNetworkQuality[] = ["UNKNOWN", "EXCELLENT", "GOOD", "FAIR", "POOR"];
    return order.indexOf(left) >= order.indexOf(right) ? left : right;
  }

  private maxNullable(left: number | null, right: number | null) {
    if (left == null) {
      return right;
    }
    if (right == null) {
      return left;
    }
    return Math.max(left, right);
  }

  private maybeScheduleIceRestart(userId: string, peerConnection: any) {
    const shouldRestart =
      this.currentUserId === this.activeCall?.createdByUserId
      && ["failed", "disconnected"].includes(peerConnection.iceConnectionState ?? "")
      && peerConnection.signalingState === "stable";
    if (!shouldRestart || this.iceRestartTimeouts.has(userId)) {
      return;
    }
    const timeoutId = setTimeout(() => {
      this.iceRestartTimeouts.delete(userId);
      void this.ensureOffer(userId, true)
        .catch(() => undefined)
        .finally(() => {
          this.updatePeerState(userId, { restartingIce: false });
        });
    }, 800);
    this.iceRestartTimeouts.set(userId, timeoutId);
  }

  private clearIceRestart(userId: string) {
    const timeoutId = this.iceRestartTimeouts.get(userId);
    if (timeoutId) {
      clearTimeout(timeoutId);
    }
    this.iceRestartTimeouts.delete(userId);
  }

  private updatePeerState(
    userId: string,
    overrides: Partial<CallMediaPeerState>
  ) {
    const current = this.state.peers.find((peer) => peer.userId === userId) ?? {
      userId,
      connectionState: "new",
      iceConnectionState: "new",
      signalingState: "stable",
      remoteTrackCount: 0,
      remoteStreamUrl: null,
      restartingIce: false,
      quality: "UNKNOWN" as CallNetworkQuality,
      roundTripTimeMs: null,
      sendBitrateKbps: null,
      packetLossPercent: null
    };
    const next = {
      ...current,
      ...overrides
    };
    this.setState({
      ...this.state,
      peers: [
        ...this.state.peers.filter((peer) => peer.userId !== userId),
        next
      ].sort((left, right) => left.userId.localeCompare(right.userId))
    });
  }

  private refreshPeerStates() {
    const peers = [...this.peerConnections.entries()].map(([userId, peerConnection]) => ({
      userId,
      connectionState: peerConnection.connectionState ?? "new",
      iceConnectionState: peerConnection.iceConnectionState ?? "new",
      signalingState: peerConnection.signalingState ?? "stable",
      remoteTrackCount:
        this.state.peers.find((peer) => peer.userId === userId)?.remoteTrackCount ?? 0,
      remoteStreamUrl:
        this.state.peers.find((peer) => peer.userId === userId)?.remoteStreamUrl ?? null,
      restartingIce: this.iceRestartTimeouts.has(userId),
      quality: this.state.peers.find((peer) => peer.userId === userId)?.quality ?? "UNKNOWN",
      roundTripTimeMs: this.state.peers.find((peer) => peer.userId === userId)?.roundTripTimeMs ?? null,
      sendBitrateKbps: this.state.peers.find((peer) => peer.userId === userId)?.sendBitrateKbps ?? null,
      packetLossPercent: this.state.peers.find((peer) => peer.userId === userId)?.packetLossPercent ?? null
    }));
    this.setState({
      ...this.state,
      peers
    });
  }

  private setState(state: CallMediaState) {
    this.state = state;
    for (const listener of this.listeners) {
      listener(this.state);
    }
  }
}

export const callMediaSession = new CallMediaSessionService();
