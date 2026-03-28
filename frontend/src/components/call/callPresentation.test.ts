import type {
  CallJoinLink,
  CallMediaState,
  CallParticipant,
  CallSession,
  CallSignalEvent
} from "../../types";
import {
  buildCallControlIssues,
  buildCallLinkMeta,
  buildCallSignalLines,
  buildCallStatusText,
  isCallLinkExpired,
  buildLeaveCallLabel,
  buildParticipantFlags,
  buildParticipantTransportLine,
  buildTransportLines,
  formatCallDuration
} from "./callPresentation";

function createParticipant(overrides: Partial<CallParticipant> = {}): CallParticipant {
  return {
    audioPublishingAllowed: true,
    displayName: "Alex",
    invitedAt: "2026-03-27T10:00:00.000Z",
    joinedAt: "2026-03-27T10:01:00.000Z",
    leftAt: null,
    moderatedAt: null,
    moderatedByUserId: null,
    phoneNumber: "+375291234567",
    photoAccessExpiresAt: null,
    photoUrl: null,
    screenShareAllowed: true,
    screenSharing: false,
    state: "JOINED",
    userId: "user-1",
    videoPublishingAllowed: true,
    ...overrides
  };
}

function createCall(overrides: Partial<CallSession> = {}): CallSession {
  return {
    answeredAt: "2026-03-27T10:00:00.000Z",
    callId: "call-1",
    chatId: "chat-1",
    createdByUserId: "user-1",
    endedAt: null,
    kind: "VIDEO",
    mode: "GROUP",
    participants: [createParticipant(), createParticipant({ userId: "user-2", displayName: "Nadia" })],
    startedAt: "2026-03-27T09:59:30.000Z",
    status: "ACTIVE",
    viewerCanManageLinks: true,
    viewerCanModerate: true,
    ...overrides
  };
}

function createMediaState(overrides: Partial<CallMediaState> = {}): CallMediaState {
  return {
    adaptationProfile: "BALANCED",
    callId: "call-1",
    error: null,
    estimatedVideoSendBitrateKbps: 480,
    localAudioEnabled: true,
    localScreenSharing: false,
    localStreamReady: true,
    localStreamUrl: "local-stream",
    localVideoEnabled: true,
    networkQuality: "GOOD",
    peers: [
      {
        connectionState: "connected",
        iceConnectionState: "completed",
        packetLossPercent: 1,
        quality: "GOOD",
        remoteStreamUrl: "remote-stream",
        remoteTrackCount: 2,
        restartingIce: false,
        roundTripTimeMs: 33,
        sendBitrateKbps: 512,
        signalingState: "stable",
        userId: "user-2"
      }
    ],
    phase: "READY",
    requiresNativeBuild: false,
    screenShareSupported: true,
    speakerOn: true,
    targetVideoBitrateKbps: 720,
    ...overrides
  };
}

describe("callPresentation", () => {
  it("formats duration and call status labels", () => {
    expect(formatCallDuration("2026-03-27T10:00:00.000Z", new Date("2026-03-27T10:02:05.000Z").getTime())).toBe("2:05");
    expect(
      buildCallStatusText({
        call: createCall(),
        currentUserId: "user-1",
        durationLabel: "2:05",
        myParticipant: createParticipant()
      })
    ).toBe("Video call - 2:05");
    expect(buildLeaveCallLabel(createCall())).toBe("Leave call");
  });

  it("builds signals, link meta, and participant transport text", () => {
    const signals: CallSignalEvent[] = [
      {
        callId: "call-1",
        emittedAt: "2026-03-27T10:02:00.000Z",
        fromUserId: "user-2",
        payload: "",
        signalType: "SCREEN_SHARE_ON",
        toUserId: "user-1"
      }
    ];
    const link: CallJoinLink = {
      chatId: "chat-1",
      createdAt: "2026-03-27T10:00:00.000Z",
      createdByUserId: "user-1",
      expiresAt: "2026-03-28T10:00:00.000Z",
      kind: "VIDEO",
      label: "Standup",
      lastUsedAt: null,
      linkId: "link-1",
      revoked: false,
      shareUrl: "https://alex.example/call/link-1",
      token: "token-1",
      usageCount: 4
    };

    expect(buildCallSignalLines(signals, createCall().participants)[0]).toContain("started screen sharing");
    expect(
      buildCallLinkMeta(link, new Date("2026-03-27T10:00:00.000Z").getTime())
    ).toContain("uses 4");
    expect(
      buildCallLinkMeta({
        ...link,
        revoked: true,
        lastUsedAt: "2026-03-27T10:05:00.000Z"
      }, new Date("2026-03-27T10:00:00.000Z").getTime())
    ).toContain("revoked");
    expect(
      buildCallLinkMeta(
        { ...link, lastUsedAt: "2026-03-27T10:05:00.000Z" },
        new Date("2026-03-27T10:00:00.000Z").getTime()
      )
    ).toContain("last used");
    expect(
      buildCallLinkMeta(
        {
          ...link,
          expiresAt: "2026-03-26T10:00:00.000Z"
        },
        new Date("2026-03-27T10:00:00.000Z").getTime()
      )
    ).toContain("expired");
    expect(
      isCallLinkExpired(
        {
          ...link,
          expiresAt: "2026-03-26T10:00:00.000Z"
        },
        new Date("2026-03-27T10:00:00.000Z").getTime()
      )
    ).toBe(true);
    expect(buildParticipantFlags(createParticipant({ screenSharing: true }))).toContain("sharing screen");
    expect(buildParticipantTransportLine("user-2", createMediaState().peers)).toContain("transport:");
  });

  it("builds transport lines", () => {
    expect(buildTransportLines(createMediaState())).toEqual(
      expect.arrayContaining([
        "Phase: ready",
        "Network quality: good"
      ])
    );
  });

  it("builds control issues for moderation, device limits, and transport errors", () => {
    expect(
      buildCallControlIssues({
        call: createCall({
          answeredAt: null,
          participants: [
            createParticipant({
              audioPublishingAllowed: false,
              screenShareAllowed: false,
              state: "RINGING",
              videoPublishingAllowed: false
            }),
            createParticipant({ displayName: "Nadia", userId: "user-2" })
          ],
          status: "RINGING"
        }),
        mediaState: createMediaState({
          error: "Microphone permission denied",
          localStreamReady: false,
          requiresNativeBuild: true,
          screenShareSupported: false
        }),
        myParticipant: createParticipant({
          audioPublishingAllowed: false,
          screenShareAllowed: false,
          state: "RINGING",
          videoPublishingAllowed: false
        }),
        screenShareEnabled: true
      })
    ).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ title: "Controls waiting for connection", tone: "info" }),
        expect.objectContaining({ title: "Microphone unavailable", tone: "warning" }),
        expect.objectContaining({ title: "Camera unavailable", tone: "warning" }),
        expect.objectContaining({ title: "Screen sharing needs a native build", tone: "warning" }),
        expect.objectContaining({ title: "Media transport issue", tone: "danger" })
      ])
    );
  });
});
