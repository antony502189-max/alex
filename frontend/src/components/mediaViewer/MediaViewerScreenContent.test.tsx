import React from "react";
import { fireEvent, render } from "@testing-library/react-native";
import type { MessageAttachment } from "../../types";
import { MediaViewerScreenContent } from "./MediaViewerScreenContent";

jest.mock("./MediaViewerStage", () => ({
  MediaViewerStage: () => null
}));

const attachment: MessageAttachment = {
  accessExpiresAt: null,
  attachmentId: "attachment-1",
  contentType: "image/jpeg",
  downloadUrl: "/attachments/1",
  durationMs: null,
  fileSizeBytes: 2048,
  height: 600,
  kind: "IMAGE",
  originalFileName: "photo.jpg",
  previewUrl: "https://cdn.example/preview.jpg",
  requiresAuthorization: true,
  streamingSupported: false,
  thumbnailUrl: "https://cdn.example/thumb.jpg",
  waveform: null,
  width: 800
};

describe("MediaViewerScreenContent", () => {
  it("opens the source chat message when viewer source metadata is available", () => {
    const onOpenMessage = jest.fn();
    const screen = render(
      <MediaViewerScreenContent
        attachmentCount={1}
        chatTitle="Team chat"
        controller={{
          currentAttachment: attachment,
          currentIndex: 0,
          currentUri: attachment.previewUrl,
          error: null,
          handleNext: jest.fn(),
          handlePrevious: jest.fn(),
          handleShareCurrent: jest.fn(),
          handleToggleVideoPlayback: jest.fn(),
          hasNext: false,
          hasPrevious: false,
          loadingLocalAttachmentId: null,
          sharingAttachmentId: null,
          videoPlaying: false
        }}
        onClose={jest.fn()}
        onOpenMessage={onOpenMessage}
        sourceMessage={{
          createdAt: "2026-03-27T12:00:00.000Z",
          messageId: "message-1"
        }}
      />
    );

    fireEvent.press(screen.getByText("View in chat"));

    expect(onOpenMessage).toHaveBeenCalledWith(
      "message-1",
      "2026-03-27T12:00:00.000Z"
    );
  });
});
