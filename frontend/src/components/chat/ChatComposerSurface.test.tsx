import React from "react";
import { Image } from "react-native";
import { render } from "@testing-library/react-native";
import { ChatComposerSurface } from "./ChatComposerSurface";
import type { MessageAttachment } from "../../types";

function createGifAttachment(overrides: Partial<MessageAttachment> = {}): MessageAttachment {
  return {
    accessExpiresAt: null,
    attachmentId: "gif-1",
    contentType: "image/gif",
    downloadUrl: "",
    durationMs: null,
    fileSizeBytes: 1024,
    height: 360,
    kind: "GIF",
    localUri: null,
    originalFileName: "loop.gif",
    previewUrl: null,
    requiresAuthorization: false,
    streamingSupported: false,
    thumbnailUrl: null,
    uploadState: "UPLOADED",
    waveform: null,
    width: 360,
    ...overrides
  };
}

describe("ChatComposerSurface", () => {
  it("renders local recent GIF previews when remote preview assets are missing", () => {
    const screen = render(
      <ChatComposerSurface
        composer={{
          activeStructuredMessageType: null,
          canFormatSelection: false,
          canPost: true,
          chatType: "GROUP",
          draft: "",
          editingMessageId: null,
          formatActions: [],
          hasComposerContent: false,
          isFormattingActive: () => false,
          onCapturePhoto: jest.fn(),
          onCaptureVideo: jest.fn(),
          onCaptureVideoNote: jest.fn(),
          onChangeDraft: jest.fn(),
          onComposerSelectionChange: jest.fn(),
          onPickAttachments: jest.fn(),
          onPickAudioFiles: jest.fn(),
          onPickPhotos: jest.fn(),
          onPickVideos: jest.fn(),
          onScheduleMessage: jest.fn(),
          onSend: jest.fn(),
          onSendWhenOnline: jest.fn(),
          onStartVoiceRecording: jest.fn(),
          onToggleContactComposer: jest.fn(),
          onToggleFormatting: jest.fn(),
          onToggleGifPicker: jest.fn(),
          onToggleLocationComposer: jest.fn(),
          onTogglePollComposer: jest.fn(),
          onToggleSendSilently: jest.fn(),
          onToggleStickerPicker: jest.fn(),
          pendingAttachmentsCount: 0,
          placeholder: "Message",
          recordingVoice: false,
          scheduling: false,
          sendSilently: false,
          sending: false,
          showContactComposer: false,
          showLocationComposer: false,
          showPollComposer: false,
          status: null,
          uploadingAttachments: false
        }}
        contactComposer={{
          firstName: "",
          lastName: "",
          onCancel: jest.fn(),
          onChangeFirstName: jest.fn(),
          onChangeLastName: jest.fn(),
          onChangePhoneNumber: jest.fn(),
          onChangeUserId: jest.fn(),
          phoneNumber: "",
          userId: "",
          visible: false
        }}
        gifPicker={{
          formatFileSize: (value) => `${value} B`,
          loading: false,
          onClose: jest.fn(),
          onInsert: jest.fn(),
          onUpload: jest.fn(),
          recentGifs: [createGifAttachment({ localUri: "file:///tmp/recent.gif" })],
          visible: true
        }}
        locationComposer={{
          address: "",
          liveEnabled: false,
          livePeriodMinutes: "15",
          latitude: "",
          longitude: "",
          onCancel: jest.fn(),
          onChangeAddress: jest.fn(),
          onChangeLivePeriodMinutes: jest.fn(),
          onChangeLatitude: jest.fn(),
          onChangeLongitude: jest.fn(),
          onChangeTitle: jest.fn(),
          onToggleLiveMode: jest.fn(),
          onUseCurrentLocation: jest.fn(),
          resolvingDeviceLocation: false,
          title: "",
          visible: false
        }}
        pollComposer={{
          creating: false,
          multipleChoice: false,
          onAddOption: jest.fn(),
          onCancel: jest.fn(),
          onChangeOption: jest.fn(),
          onChangeQuestion: jest.fn(),
          onCreate: jest.fn(),
          onRemoveOption: jest.fn(),
          onToggleMultipleChoice: jest.fn(),
          options: ["", ""],
          question: "",
          visible: false
        }}
        recording={{
          active: false,
          durationLabel: "0:00",
          onDiscard: jest.fn(),
          onSend: jest.fn()
        }}
        stickerPicker={{
          loading: false,
          onSendSticker: jest.fn(),
          packs: [],
          visible: false
        }}
      />
    );

    expect(screen.UNSAFE_getByType(Image).props.source).toEqual({
      uri: "file:///tmp/recent.gif"
    });
    expect(screen.getByText("1024 B")).toBeTruthy();
  });

  it("renders composer status guidance when the next send has a special mode", () => {
    const screen = render(
      <ChatComposerSurface
        composer={{
          activeStructuredMessageType: null,
          canFormatSelection: false,
          canPost: true,
          chatType: "GROUP",
          draft: "",
          editingMessageId: null,
          formatActions: [],
          hasComposerContent: true,
          isFormattingActive: () => false,
          onCapturePhoto: jest.fn(),
          onCaptureVideo: jest.fn(),
          onCaptureVideoNote: jest.fn(),
          onChangeDraft: jest.fn(),
          onComposerSelectionChange: jest.fn(),
          onPickAttachments: jest.fn(),
          onPickAudioFiles: jest.fn(),
          onPickPhotos: jest.fn(),
          onPickVideos: jest.fn(),
          onScheduleMessage: jest.fn(),
          onSend: jest.fn(),
          onSendWhenOnline: jest.fn(),
          onStartVoiceRecording: jest.fn(),
          onToggleContactComposer: jest.fn(),
          onToggleFormatting: jest.fn(),
          onToggleGifPicker: jest.fn(),
          onToggleLocationComposer: jest.fn(),
          onTogglePollComposer: jest.fn(),
          onToggleSendSilently: jest.fn(),
          onToggleStickerPicker: jest.fn(),
          pendingAttachmentsCount: 2,
          placeholder: "Message",
          recordingVoice: false,
          scheduling: false,
          sendSilently: true,
          sending: false,
          showContactComposer: false,
          showLocationComposer: false,
          showPollComposer: false,
          status: {
            description: "2 visual items will send together as one media batch.",
            title: "2 attachments ready",
            tone: "success"
          },
          uploadingAttachments: false
        }}
        contactComposer={{
          firstName: "",
          lastName: "",
          onCancel: jest.fn(),
          onChangeFirstName: jest.fn(),
          onChangeLastName: jest.fn(),
          onChangePhoneNumber: jest.fn(),
          onChangeUserId: jest.fn(),
          phoneNumber: "",
          userId: "",
          visible: false
        }}
        gifPicker={{
          formatFileSize: (value) => `${value} B`,
          loading: false,
          onClose: jest.fn(),
          onInsert: jest.fn(),
          onUpload: jest.fn(),
          recentGifs: [],
          visible: false
        }}
        locationComposer={{
          address: "",
          liveEnabled: false,
          livePeriodMinutes: "15",
          latitude: "",
          longitude: "",
          onCancel: jest.fn(),
          onChangeAddress: jest.fn(),
          onChangeLivePeriodMinutes: jest.fn(),
          onChangeLatitude: jest.fn(),
          onChangeLongitude: jest.fn(),
          onChangeTitle: jest.fn(),
          onToggleLiveMode: jest.fn(),
          onUseCurrentLocation: jest.fn(),
          resolvingDeviceLocation: false,
          title: "",
          visible: false
        }}
        pollComposer={{
          creating: false,
          multipleChoice: false,
          onAddOption: jest.fn(),
          onCancel: jest.fn(),
          onChangeOption: jest.fn(),
          onChangeQuestion: jest.fn(),
          onCreate: jest.fn(),
          onRemoveOption: jest.fn(),
          onToggleMultipleChoice: jest.fn(),
          options: ["", ""],
          question: "",
          visible: false
        }}
        recording={{
          active: false,
          durationLabel: "0:00",
          onDiscard: jest.fn(),
          onSend: jest.fn()
        }}
        stickerPicker={{
          loading: false,
          onSendSticker: jest.fn(),
          packs: [],
          visible: false
        }}
      />
    );

    expect(screen.getByText("2 attachments ready")).toBeTruthy();
    expect(screen.getByText("2 visual items will send together as one media batch.")).toBeTruthy();
  });
});
