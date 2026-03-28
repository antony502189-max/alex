import React from "react";
import { fireEvent, render } from "@testing-library/react-native";
import { CallControlsPanel } from "./CallControlsPanel";
import type { CallControlIssue } from "./callPresentation";
import type { CallSession } from "../../types";

function createCall(overrides: Partial<CallSession> = {}): CallSession {
  return {
    answeredAt: "2026-03-27T10:00:00.000Z",
    callId: "call-1",
    chatId: "chat-1",
    createdByUserId: "user-1",
    endedAt: null,
    kind: "VIDEO",
    mode: "GROUP",
    participants: [],
    startedAt: "2026-03-27T09:59:30.000Z",
    status: "ACTIVE",
    viewerCanManageLinks: true,
    viewerCanModerate: true,
    ...overrides
  };
}

describe("CallControlsPanel", () => {
  it("shows disabled share control and issue panels for unsupported states", () => {
    const onToggleScreenShare = jest.fn();
    const issues: CallControlIssue[] = [
      {
        description: "Use a development or native build to expose the screen-share transport.",
        title: "Screen sharing needs a native build",
        tone: "warning"
      },
      {
        description: "The host has disabled your microphone.",
        title: "Microphone unavailable",
        tone: "warning"
      }
    ];

    const screen = render(
      <CallControlsPanel
        call={createCall()}
        canToggleCamera={true}
        canToggleMicrophone={false}
        canToggleScreenShare={false}
        controlIssues={issues}
        localAudioEnabled={true}
        localScreenSharing={false}
        localVideoEnabled={true}
        onToggleMute={jest.fn()}
        onToggleScreenShare={onToggleScreenShare}
        onToggleSpeaker={jest.fn()}
        onToggleVideo={jest.fn()}
        screenShareEnabled={true}
        screenShareSupported={false}
        speakerOn={true}
      />
    );

    expect(screen.getByText("Share unavailable")).toBeTruthy();
    expect(screen.getByText("Screen sharing needs a native build")).toBeTruthy();
    expect(screen.getByText("Microphone unavailable")).toBeTruthy();

    fireEvent.press(screen.getByText("Share unavailable"));

    expect(onToggleScreenShare).not.toHaveBeenCalled();
  });

  it("renders active controls with live labels when features are available", () => {
    const onToggleMute = jest.fn();
    const onToggleVideo = jest.fn();
    const onToggleScreenShare = jest.fn();

    const screen = render(
      <CallControlsPanel
        call={createCall()}
        canToggleCamera={true}
        canToggleMicrophone={true}
        canToggleScreenShare={true}
        controlIssues={[]}
        localAudioEnabled={false}
        localScreenSharing={false}
        localVideoEnabled={false}
        onToggleMute={onToggleMute}
        onToggleScreenShare={onToggleScreenShare}
        onToggleSpeaker={jest.fn()}
        onToggleVideo={onToggleVideo}
        screenShareEnabled={true}
        screenShareSupported={true}
        speakerOn={false}
      />
    );

    fireEvent.press(screen.getByText("Mic off"));
    fireEvent.press(screen.getByText("Camera off"));
    fireEvent.press(screen.getByText("Share screen"));

    expect(onToggleMute).toHaveBeenCalledTimes(1);
    expect(onToggleVideo).toHaveBeenCalledTimes(1);
    expect(onToggleScreenShare).toHaveBeenCalledTimes(1);
  });
});
