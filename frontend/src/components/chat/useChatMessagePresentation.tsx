import React, { useCallback } from "react";
import { getAttachmentTransferMeta as getTransferMeta } from "./chatAttachmentHelpers";
import { ChatWaveform } from "./ChatWaveform";
import {
  describeMessage as describeTimelineMessage,
  renderMessageMeta as renderTimelineMessageMeta,
  resolveDisplaySenderName as resolveTimelineDisplaySenderName
} from "./chatMessageHelpers";
import { attachmentTitle } from "./chatAttachmentHelpers";
import type { AttachmentTransferState } from "../../store/useAttachmentTransferStore";
import type {
  ChatMember,
  ChatMessage,
  ChatSummary,
  MessageAttachment,
  ScheduledMessage
} from "../../types";

type UseChatMessagePresentationParams = {
  attachmentTransferStates: Record<string, AttachmentTransferState>;
  canManageMessages: boolean;
  chatType: ChatSummary["chatType"];
  currentUserId: string;
  members: ChatMember[];
};

export function useChatMessagePresentation({
  attachmentTransferStates,
  canManageMessages,
  chatType,
  currentUserId,
  members
}: UseChatMessagePresentationParams) {
  const canClosePoll = useCallback((message: ChatMessage) => {
    if (!message.poll || message.poll.closed || message.deliveryStatus === "QUEUED") {
      return false;
    }
    if (message.senderId === currentUserId) {
      return true;
    }
    return chatType !== "DIRECT" && chatType !== "SAVED" && canManageMessages;
  }, [canManageMessages, chatType, currentUserId]);

  const describeMessage = useCallback((message: ChatMessage | ScheduledMessage) => {
    return describeTimelineMessage(message, attachmentTitle);
  }, []);

  const getAttachmentTransferMeta = useCallback((attachment: MessageAttachment) => {
    return getTransferMeta(attachmentTransferStates[attachment.attachmentId]);
  }, [attachmentTransferStates]);

  const renderMessageMeta = useCallback((message: ChatMessage) => {
    return renderTimelineMessageMeta(message, currentUserId, members);
  }, [currentUserId, members]);

  const renderWaveform = useCallback((attachment: MessageAttachment, color: string) => {
    return <ChatWaveform attachment={attachment} color={color} />;
  }, []);

  const resolveDisplaySenderName = useCallback((message: ChatMessage | null | undefined) => {
    return resolveTimelineDisplaySenderName(message, currentUserId, members);
  }, [currentUserId, members]);

  return {
    canClosePoll,
    describeMessage,
    getAttachmentTransferMeta,
    renderMessageMeta,
    renderWaveform,
    resolveDisplaySenderName
  };
}
