import React from "react";
import { fireEvent, render, waitFor } from "@testing-library/react-native";
import type { MessageAttachment } from "../../types";
import { PendingAttachmentBar } from "./PendingAttachmentBar";

const videoAttachment: MessageAttachment = {
  accessExpiresAt: null,
  attachmentId: "video-1",
  contentType: "video/mp4",
  downloadUrl: "",
  durationMs: 12000,
  fileSizeBytes: 8192,
  height: 720,
  kind: "VIDEO",
  localUri: null,
  originalFileName: "clip.mp4",
  previewUrl: null,
  requiresAuthorization: false,
  streamingSupported: true,
  thumbnailUrl: null,
  uploadState: "UPLOADED",
  waveform: null,
  width: 1280
};

describe("PendingAttachmentBar", () => {
  it("submits trim ranges in milliseconds for supported clips", async () => {
    const onTrimAttachment = jest.fn().mockResolvedValue(true);
    const screen = render(
      <PendingAttachmentBar
        items={[
          {
            attachment: videoAttachment,
            canMoveEarlier: false,
            canMoveLater: false,
            canRetryUpload: false,
            canTrim: true,
            metaLabel: "8192 B",
            title: "Video"
          }
        ]}
        onMoveAttachment={jest.fn()}
        onRemoveAttachment={jest.fn()}
        onRetryAttachment={jest.fn()}
        onTrimAttachment={onTrimAttachment}
        trimmingAttachmentId={null}
        uploadingAttachments={false}
      />
    );

    fireEvent.press(screen.getByText("Trim clip"));
    fireEvent.changeText(screen.getByPlaceholderText("Start (s)"), "1.5");
    fireEvent.changeText(screen.getByPlaceholderText("End (s)"), "6");
    fireEvent.press(screen.getByText("Apply trim"));

    await waitFor(() => {
      expect(onTrimAttachment).toHaveBeenCalledWith(videoAttachment, 1500, 6000);
    });
  });

  it("renders summary and upload progress for pending attachments", () => {
    const screen = render(
      <PendingAttachmentBar
        items={[
          {
            attachment: videoAttachment,
            canMoveEarlier: false,
            canMoveLater: false,
            canRetryUpload: false,
            canTrim: true,
            metaLabel: "8192 B",
            progress: 0.5,
            progressLabel: "4.0 KB / 8.0 KB",
            statusLabel: "Uploading now",
            statusTone: "brand",
            title: "Video"
          }
        ]}
        onMoveAttachment={jest.fn()}
        onRemoveAttachment={jest.fn()}
        onRetryAttachment={jest.fn()}
        onTrimAttachment={jest.fn()}
        summary={{
          description: "1 attachment is still uploading.",
          title: "1 attachment ready",
          tone: "brand"
        }}
        trimmingAttachmentId={null}
        uploadingAttachments={false}
      />
    );

    expect(screen.getByText("1 attachment ready")).toBeTruthy();
    expect(screen.getByText("1 attachment is still uploading.")).toBeTruthy();
    expect(screen.getByText("Uploading now")).toBeTruthy();
    expect(screen.getByText("4.0 KB / 8.0 KB")).toBeTruthy();
  });
});
