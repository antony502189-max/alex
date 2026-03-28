import { useCallback, useMemo } from "react";
import type { Dispatch, SetStateAction } from "react";
import type {
  NativeSyntheticEvent,
  TextInputSelectionChangeEventData
} from "react-native";
import {
  toggleMessageEntity,
  trimFormattedMessage,
  type MessageComposerSelection
} from "../../services/messageFormatting";
import type {
  InlineBotResult,
  MessageAttachment,
  MessageTextEntity
} from "../../types";

type UseChatComposerControlsParams = {
  canPost: boolean;
  canSendContact: boolean;
  canSendLiveLocation: boolean;
  canSendLocation: boolean;
  chatId: string;
  chatTitle: string;
  closeRichMediaPickers: () => void;
  composerSelection: MessageComposerSelection;
  draft: string;
  draftEntities: MessageTextEntity[];
  editingMessageId: string | null;
  hideStructuredComposerPanels: () => void;
  onOpenBotMiniApp?: (
    botUserId: string,
    title: string,
    chatId?: string | null,
    startParameter?: string | null
  ) => void;
  pendingAttachmentCount: number;
  peerDisplayName: string | null;
  peerUserId: string | null;
  recordingVoice: boolean;
  resetStructuredComposerState: () => void;
  sending: boolean;
  setComposerSelection: Dispatch<SetStateAction<MessageComposerSelection>>;
  setDraft: Dispatch<SetStateAction<string>>;
  setDraftEntities: Dispatch<SetStateAction<MessageTextEntity[]>>;
  setEditingMessageId: Dispatch<SetStateAction<string | null>>;
  setInlineBotResults: Dispatch<SetStateAction<InlineBotResult[]>>;
  setPendingAttachments: Dispatch<SetStateAction<MessageAttachment[]>>;
  setReplyToMessageId: Dispatch<SetStateAction<string | null>>;
  setSelectedMessageId: Dispatch<SetStateAction<string | null>>;
  setSendSilently: Dispatch<SetStateAction<boolean>>;
  uploadingAttachments: boolean;
};

export function useChatComposerControls({
  canPost,
  canSendContact,
  canSendLiveLocation,
  canSendLocation,
  chatId,
  chatTitle,
  closeRichMediaPickers,
  composerSelection,
  draft,
  draftEntities,
  editingMessageId,
  hideStructuredComposerPanels,
  onOpenBotMiniApp,
  pendingAttachmentCount,
  peerDisplayName,
  peerUserId,
  recordingVoice,
  resetStructuredComposerState,
  sending,
  setComposerSelection,
  setDraft,
  setDraftEntities,
  setEditingMessageId,
  setInlineBotResults,
  setPendingAttachments,
  setReplyToMessageId,
  setSelectedMessageId,
  setSendSilently,
  uploadingAttachments
}: UseChatComposerControlsParams) {
  const normalizedComposerSelection = useMemo(() => {
    const start = Math.max(0, Math.min(composerSelection.start, composerSelection.end));
    const end = Math.min(draft.length, Math.max(composerSelection.start, composerSelection.end));
    return { start, end };
  }, [composerSelection, draft.length]);

  const canFormatSelection =
    normalizedComposerSelection.end > normalizedComposerSelection.start;

  const normalizedComposerDraft = useMemo(
    () => trimFormattedMessage(draft, draftEntities),
    [draft, draftEntities]
  );

  const hasComposerContent =
    normalizedComposerDraft.text.length > 0 ||
    pendingAttachmentCount > 0 ||
    canSendLiveLocation ||
    canSendLocation ||
    canSendContact;

  const handleDraftChange = useCallback((nextDraft: string) => {
    setDraft(nextDraft);
    if (nextDraft !== draft && draftEntities.length > 0) {
      setDraftEntities([]);
    }
  }, [draft, draftEntities.length, setDraft, setDraftEntities]);

  const handleInsertBotCommand = useCallback((command: string) => {
    if (!canPost || sending || uploadingAttachments || recordingVoice || editingMessageId) {
      return;
    }
    setDraft(command);
    setDraftEntities([]);
    setComposerSelection({ start: command.length, end: command.length });
    closeRichMediaPickers();
    hideStructuredComposerPanels();
  }, [
    canPost,
    closeRichMediaPickers,
    editingMessageId,
    hideStructuredComposerPanels,
    recordingVoice,
    sending,
    setComposerSelection,
    setDraft,
    setDraftEntities,
    uploadingAttachments
  ]);

  const handleComposerSelectionChange = useCallback(
    (event: NativeSyntheticEvent<TextInputSelectionChangeEventData>) => {
      setComposerSelection(event.nativeEvent.selection);
    },
    [setComposerSelection]
  );

  const isFormattingActive = useCallback((type: MessageTextEntity["type"]) => {
    if (!canFormatSelection) {
      return false;
    }

    return draftEntities.some(
      (entity) =>
        entity.type === type &&
        entity.offset <= normalizedComposerSelection.start &&
        entity.offset + entity.length >= normalizedComposerSelection.end
    );
  }, [canFormatSelection, draftEntities, normalizedComposerSelection]);

  const handleToggleFormatting = useCallback((type: MessageTextEntity["type"]) => {
    if (!canFormatSelection) {
      return;
    }
    setDraftEntities((current) =>
      toggleMessageEntity(draft, current, type, normalizedComposerSelection)
    );
  }, [canFormatSelection, draft, normalizedComposerSelection, setDraftEntities]);

  const resetComposerState = useCallback(() => {
    setDraft("");
    setDraftEntities([]);
    setComposerSelection({ start: 0, end: 0 });
    setPendingAttachments([]);
    setSendSilently(false);
    resetStructuredComposerState();
    setEditingMessageId(null);
    setReplyToMessageId(null);
    setSelectedMessageId(null);
    setInlineBotResults([]);
  }, [
    resetStructuredComposerState,
    setComposerSelection,
    setDraft,
    setDraftEntities,
    setEditingMessageId,
    setInlineBotResults,
    setPendingAttachments,
    setReplyToMessageId,
    setSelectedMessageId,
    setSendSilently
  ]);

  const handleOpenBotMiniApp = useCallback(() => {
    if (!peerUserId) {
      return;
    }
    onOpenBotMiniApp?.(peerUserId, peerDisplayName ?? chatTitle, chatId, null);
  }, [chatId, chatTitle, onOpenBotMiniApp, peerDisplayName, peerUserId]);

  return {
    canFormatSelection,
    handleComposerSelectionChange,
    handleDraftChange,
    handleInsertBotCommand,
    handleOpenBotMiniApp,
    handleToggleFormatting,
    hasComposerContent,
    isFormattingActive,
    normalizedComposerDraft,
    resetComposerState
  };
}
