import { act, renderHook } from "@testing-library/react-native";
import { useState } from "react";
import type { MessageComposerSelection } from "../../services/messageFormatting";
import type {
  InlineBotResult,
  MessageAttachment,
  MessageTextEntity
} from "../../types";
import { useChatComposerControls } from "./useChatComposerControls";

const baseAttachment: MessageAttachment = {
  accessExpiresAt: null,
  attachmentId: "attachment-1",
  contentType: "image/jpeg",
  downloadUrl: "https://example.test/image.jpg",
  durationMs: null,
  fileSizeBytes: 1024,
  height: 720,
  kind: "IMAGE",
  localUri: null,
  originalFileName: "image.jpg",
  previewUrl: "https://example.test/preview.jpg",
  requiresAuthorization: false,
  streamingSupported: false,
  thumbnailUrl: null,
  uploadState: "PENDING_UPLOAD",
  waveform: null,
  width: 1280
};

const baseInlineResult: InlineBotResult = {
  botUserId: "bot-1",
  botUsername: "helper_bot",
  description: "Helpful inline result",
  resultId: "inline-1",
  text: "inline result",
  title: "Result"
};

type HarnessConfig = {
  canPost?: boolean;
  draft?: string;
  draftEntities?: MessageTextEntity[];
  editingMessageId?: string | null;
  peerDisplayName?: string | null;
  peerUserId?: string | null;
  pendingAttachments?: MessageAttachment[];
  replyToMessageId?: string | null;
  selectedMessageId?: string | null;
  sendSilently?: boolean;
};

describe("useChatComposerControls", () => {
  const closeRichMediaPickers = jest.fn();
  const hideStructuredComposerPanels = jest.fn();
  const onOpenBotMiniApp = jest.fn();
  const resetStructuredComposerState = jest.fn();

  beforeEach(() => {
    closeRichMediaPickers.mockReset();
    hideStructuredComposerPanels.mockReset();
    onOpenBotMiniApp.mockReset();
    resetStructuredComposerState.mockReset();
  });

  function createHarness(config: HarnessConfig = {}) {
    return function useHarness() {
      const [draft, setDraft] = useState(config.draft ?? "Hello");
      const [draftEntities, setDraftEntities] = useState<MessageTextEntity[]>(
        config.draftEntities ?? [{ type: "BOLD", offset: 0, length: 5 }]
      );
      const [composerSelection, setComposerSelection] = useState<MessageComposerSelection>({
        start: 0,
        end: 5
      });
      const [editingMessageId, setEditingMessageId] = useState<string | null>(
        config.editingMessageId === undefined ? "message-1" : config.editingMessageId
      );
      const [inlineBotResults, setInlineBotResults] = useState<InlineBotResult[]>([
        baseInlineResult
      ]);
      const [pendingAttachments, setPendingAttachments] = useState<MessageAttachment[]>(
        config.pendingAttachments ?? [baseAttachment]
      );
      const [replyToMessageId, setReplyToMessageId] = useState<string | null>(
        config.replyToMessageId === undefined ? "reply-1" : config.replyToMessageId
      );
      const [selectedMessageId, setSelectedMessageId] = useState<string | null>(
        config.selectedMessageId === undefined ? "selected-1" : config.selectedMessageId
      );
      const [sendSilently, setSendSilently] = useState(config.sendSilently ?? true);

      const controls = useChatComposerControls({
        canPost: config.canPost ?? true,
        canSendContact: false,
        canSendLiveLocation: false,
        canSendLocation: false,
        chatId: "chat-1",
        chatTitle: "Team chat",
        closeRichMediaPickers,
        composerSelection,
        draft,
        draftEntities,
        editingMessageId,
        hideStructuredComposerPanels,
        onOpenBotMiniApp,
        peerDisplayName: config.peerDisplayName ?? "Helper Bot",
        peerUserId: config.peerUserId ?? "bot-1",
        pendingAttachmentCount: pendingAttachments.length,
        recordingVoice: false,
        resetStructuredComposerState,
        sending: false,
        setComposerSelection,
        setDraft,
        setDraftEntities,
        setEditingMessageId,
        setInlineBotResults,
        setPendingAttachments,
        setReplyToMessageId,
        setSelectedMessageId,
        setSendSilently,
        uploadingAttachments: false
      });

      return {
        composerSelection,
        draft,
        draftEntities,
        editingMessageId,
        inlineBotResults,
        pendingAttachments,
        replyToMessageId,
        selectedMessageId,
        sendSilently,
        ...controls
      };
    };
  }

  it("clears formatting entities when the draft changes", () => {
    const { result } = renderHook(createHarness());

    expect(result.current.canFormatSelection).toBe(true);
    expect(result.current.isFormattingActive("BOLD")).toBe(true);

    act(() => {
      result.current.handleDraftChange("Hello world");
    });

    expect(result.current.draft).toBe("Hello world");
    expect(result.current.draftEntities).toEqual([]);
    expect(result.current.normalizedComposerDraft.text).toBe("Hello world");
  });

  it("inserts a bot command and closes rich composer surfaces", () => {
    const { result } = renderHook(createHarness({ editingMessageId: null, pendingAttachments: [] }));

    act(() => {
      result.current.handleInsertBotCommand("/start");
    });

    expect(result.current.draft).toBe("/start");
    expect(result.current.draftEntities).toEqual([]);
    expect(result.current.composerSelection).toEqual({ start: 6, end: 6 });
    expect(closeRichMediaPickers).toHaveBeenCalledTimes(1);
    expect(hideStructuredComposerPanels).toHaveBeenCalledTimes(1);
  });

  it("resets composer state and opens the peer mini app", () => {
    const { result } = renderHook(createHarness());

    act(() => {
      result.current.resetComposerState();
    });

    expect(result.current.draft).toBe("");
    expect(result.current.draftEntities).toEqual([]);
    expect(result.current.pendingAttachments).toEqual([]);
    expect(result.current.sendSilently).toBe(false);
    expect(result.current.editingMessageId).toBeNull();
    expect(result.current.replyToMessageId).toBeNull();
    expect(result.current.selectedMessageId).toBeNull();
    expect(result.current.inlineBotResults).toEqual([]);
    expect(resetStructuredComposerState).toHaveBeenCalledTimes(1);

    act(() => {
      result.current.handleOpenBotMiniApp();
    });

    expect(onOpenBotMiniApp).toHaveBeenCalledWith("bot-1", "Helper Bot", "chat-1", null);
  });
});
