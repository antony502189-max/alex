jest.mock("../services/api", () => ({
  api: {
    joinCallLink: jest.fn(),
    getCallLinks: jest.fn()
  }
}));

jest.mock("../services/callMediaSession", () => ({
  callMediaSession: {
    startScreenShare: jest.fn(),
    stopScreenShare: jest.fn()
  }
}));

import { api } from "../services/api";
import { useRootCallActions } from "./useRootCallActions";
import type {
  AuthSession,
  CallMediaState,
  CallSession
} from "../types";

function createSession(overrides: Partial<AuthSession> = {}): AuthSession {
  return {
    token: "token-1",
    refreshToken: "refresh-1",
    sessionId: "session-1",
    userId: "user-1",
    phoneNumber: "+375291234567",
    displayName: "Alex",
    username: "alex",
    accessTokenExpiresAt: null,
    refreshTokenExpiresAt: null,
    authMethod: "OTP",
    trustedSession: true,
    ...overrides
  };
}

function createCall(overrides: Partial<CallSession> = {}): CallSession {
  return {
    callId: "call-1",
    chatId: "chat-1",
    createdByUserId: "user-1",
    kind: "VOICE",
    mode: "DIRECT",
    status: "ACTIVE",
    startedAt: "2026-03-27T10:00:00.000Z",
    answeredAt: "2026-03-27T10:00:05.000Z",
    endedAt: null,
    viewerCanModerate: false,
    viewerCanManageLinks: false,
    participants: [
      {
        userId: "user-1",
        displayName: "Alex",
        phoneNumber: null,
        photoUrl: null,
        photoAccessExpiresAt: null,
        state: "JOINED",
        invitedAt: "2026-03-27T10:00:00.000Z",
        joinedAt: "2026-03-27T10:00:05.000Z",
        leftAt: null,
        audioPublishingAllowed: true,
        videoPublishingAllowed: true,
        screenShareAllowed: true,
        screenSharing: false,
        moderatedByUserId: null,
        moderatedAt: null
      }
    ],
    ...overrides
  };
}

const idleMediaState: CallMediaState = {
  adaptationProfile: "BALANCED",
  callId: null,
  error: null,
  estimatedVideoSendBitrateKbps: null,
  localAudioEnabled: true,
  localScreenSharing: false,
  localStreamReady: false,
  localStreamUrl: null,
  localVideoEnabled: false,
  networkQuality: "UNKNOWN",
  peers: [],
  phase: "IDLE",
  requiresNativeBuild: false,
  screenShareSupported: false,
  speakerOn: false,
  targetVideoBitrateKbps: null
};

describe("useRootCallActions", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("normalizes a full call link before joining", async () => {
    const setCurrentCall = jest.fn();
    const setCurrentCallLinks = jest.fn();
    const setRecentCallSignals = jest.fn();

    (api.joinCallLink as jest.Mock).mockResolvedValue(createCall());

    const actions = useRootCallActions({
      callMediaState: idleMediaState,
      currentCallRef: { current: null },
      session: createSession(),
      setCurrentCall,
      setCurrentCallLinks,
      setRecentCallSignals
    });

    await actions.joinCallByLink(" https://t.me/call?token=room-77 ");

    expect(api.joinCallLink).toHaveBeenCalledWith("token-1", "room-77");
    expect(setRecentCallSignals).toHaveBeenCalledWith([]);
    expect(setCurrentCall).toHaveBeenCalledWith(expect.objectContaining({ callId: "call-1" }));
    expect(setCurrentCallLinks).toHaveBeenCalledWith([]);
  });

  it("ignores empty call-link input after normalization", async () => {
    const actions = useRootCallActions({
      callMediaState: idleMediaState,
      currentCallRef: { current: null },
      session: createSession(),
      setCurrentCall: jest.fn(),
      setCurrentCallLinks: jest.fn(),
      setRecentCallSignals: jest.fn()
    });

    await actions.joinCallByLink("   ");

    expect(api.joinCallLink).not.toHaveBeenCalled();
  });
});
