import { act, renderHook } from "@testing-library/react-native";
import type { CallMediaState, CallParticipant, CallSession, CallSignalEvent } from "../../types";
import { useCallScreenController } from "./useCallScreenController";

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
    createdByUserId: "user-2",
    endedAt: null,
    kind: "VIDEO",
    mode: "GROUP",
    participants: [
      createParticipant(),
      createParticipant({ displayName: "Nadia", userId: "user-2" })
    ],
    startedAt: "2026-03-27T09:59:50.000Z",
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
    estimatedVideoSendBitrateKbps: 400,
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
        packetLossPercent: 0,
        quality: "GOOD",
        remoteStreamUrl: "remote-stream",
        remoteTrackCount: 2,
        restartingIce: false,
        roundTripTimeMs: 25,
        sendBitrateKbps: 512,
        signalingState: "stable",
        userId: "user-2"
      }
    ],
    phase: "READY",
    requiresNativeBuild: false,
    screenShareSupported: true,
    speakerOn: true,
    targetVideoBitrateKbps: 600,
    ...overrides
  };
}

describe("useCallScreenController", () => {
  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  it("tracks duration and derives remote participant state", () => {
    jest.useFakeTimers();
    let now = new Date("2026-03-27T10:02:05.000Z").getTime();
    jest.spyOn(Date, "now").mockImplementation(() => now);

    const { result } = renderHook(() =>
      useCallScreenController({
        call: createCall(),
        callScreenSharingEnabled: true,
        currentUserId: "user-1",
        mediaState: createMediaState(),
        recentSignals: [] as CallSignalEvent[]
      })
    );

    expect(result.current.durationLabel).toBe("2:05");
    expect(result.current.headlineParticipant?.userId).toBe("user-2");
    expect(result.current.primaryRemotePeer?.remoteStreamUrl).toBe("remote-stream");

    act(() => {
      now += 1000;
      jest.advanceTimersByTime(1000);
    });

    expect(result.current.durationLabel).toBe("2:06");
  });

  it("detects incoming ringing state and capability restrictions", () => {
    const { result } = renderHook(() =>
      useCallScreenController({
        call: createCall({
          answeredAt: null,
          participants: [
            createParticipant({
              audioPublishingAllowed: false,
              state: "RINGING",
              videoPublishingAllowed: false
            }),
            createParticipant({ displayName: "Nadia", userId: "user-2" })
          ],
          status: "RINGING"
        }),
        callScreenSharingEnabled: true,
        currentUserId: "user-1",
        mediaState: createMediaState({ screenShareSupported: false }),
        recentSignals: []
      })
    );

    expect(result.current.incomingRinging).toBe(true);
    expect(result.current.canToggleMicrophone).toBe(false);
    expect(result.current.canToggleCamera).toBe(false);
    expect(result.current.canToggleScreenShare).toBe(false);
    expect(result.current.controlIssues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ title: "Controls waiting for connection" }),
        expect.objectContaining({ title: "Microphone unavailable" }),
        expect.objectContaining({ title: "Camera unavailable" }),
        expect.objectContaining({ title: "Screen sharing unavailable on this device" })
      ])
    );
    expect(result.current.leaveLabel).toBe("Cancel call");
  });
});
