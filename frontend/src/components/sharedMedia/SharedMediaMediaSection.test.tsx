import React from "react";
import { Image } from "react-native";
import { fireEvent, render } from "@testing-library/react-native";
import { SharedMediaMediaSection } from "./SharedMediaMediaSection";
import type { MessageAttachment, SharedMediaEntry } from "../../types";

function createAttachment(overrides: Partial<MessageAttachment> = {}): MessageAttachment {
  return {
    accessExpiresAt: null,
    attachmentId: "attachment-1",
    contentType: "image/jpeg",
    downloadUrl: "",
    durationMs: null,
    fileSizeBytes: 2048,
    height: 720,
    kind: "IMAGE",
    localUri: null,
    originalFileName: "photo.jpg",
    previewUrl: null,
    requiresAuthorization: false,
    streamingSupported: false,
    thumbnailUrl: null,
    uploadState: "UPLOADED",
    waveform: null,
    width: 1280,
    ...overrides
  };
}

describe("SharedMediaMediaSection", () => {
  it("renders a local image preview when no remote preview asset is available", () => {
    const attachment = createAttachment({
      localUri: "file:///tmp/photo.jpg"
    });
    const entry: SharedMediaEntry = {
      attachment,
      caption: null,
      chatId: "chat-1",
      createdAt: "2026-03-28T10:00:00.000Z",
      messageId: "message-1",
      senderDisplayName: "Alex"
    };
    const onOpenMessage = jest.fn();
    const onOpenMediaViewer = jest.fn();

    const screen = render(
      <SharedMediaMediaSection
        chatTitle="Team chat"
        entries={[entry]}
        mediaAttachments={[attachment]}
        onOpenMediaViewer={onOpenMediaViewer}
        onOpenMessage={onOpenMessage}
      />
    );

    expect(screen.UNSAFE_getByType(Image).props.source).toEqual({
      uri: "file:///tmp/photo.jpg"
    });

    fireEvent.press(screen.getByText("View in chat"));
    expect(onOpenMessage).toHaveBeenCalledWith("message-1", "2026-03-28T10:00:00.000Z");
  });
});
