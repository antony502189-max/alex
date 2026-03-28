import { useCallback } from "react";
import { api } from "../../services/api";
import { fromQueuedScheduledMessageId } from "../../services/clientMessageIds";
import { localDatabase } from "../../services/localDatabase";
import { scheduledMessageOutbox } from "../../services/scheduledMessageOutbox";
import type { ChatMessage, ScheduledMessage } from "../../types";

type UseChatModerationActionsParams = {
  cancelingScheduledMessageId: string | null;
  chatId: string;
  closingPollMessageId: string | null;
  currentUserId: string;
  persistMessage: (message: ChatMessage) => void;
  setCancelingScheduledMessageId: React.Dispatch<React.SetStateAction<string | null>>;
  setClosingPollMessageId: React.Dispatch<React.SetStateAction<string | null>>;
  setError: (value: string | null) => void;
  setScheduledMessages: React.Dispatch<React.SetStateAction<ScheduledMessage[]>>;
  setSelectedMessageId: React.Dispatch<React.SetStateAction<string | null>>;
  setVotingMessageId: React.Dispatch<React.SetStateAction<string | null>>;
  token: string;
  votingMessageId: string | null;
};

export function useChatModerationActions({
  cancelingScheduledMessageId,
  chatId,
  closingPollMessageId,
  currentUserId,
  persistMessage,
  setCancelingScheduledMessageId,
  setClosingPollMessageId,
  setError,
  setScheduledMessages,
  setSelectedMessageId,
  setVotingMessageId,
  token,
  votingMessageId
}: UseChatModerationActionsParams) {
  const handleCancelScheduledMessage = useCallback(async (scheduledMessageId: string) => {
    if (cancelingScheduledMessageId) {
      return;
    }

    setCancelingScheduledMessageId(scheduledMessageId);
    setError(null);
    try {
      const queuedClientMessageId = fromQueuedScheduledMessageId(scheduledMessageId);
      if (queuedClientMessageId) {
        await scheduledMessageOutbox.removeQueuedMessage(currentUserId, chatId, queuedClientMessageId);
      } else {
        await api.cancelScheduledMessage(token, scheduledMessageId);
      }
      setScheduledMessages((current) =>
        current.filter((message) => message.scheduledMessageId !== scheduledMessageId)
      );
      void localDatabase.removeScheduledMessage(currentUserId, chatId, scheduledMessageId).catch(() => undefined);
    } catch (cancelError) {
      setError(cancelError instanceof Error ? cancelError.message : "Unable to cancel scheduled message");
    } finally {
      setCancelingScheduledMessageId(null);
    }
  }, [
    cancelingScheduledMessageId,
    chatId,
    currentUserId,
    setCancelingScheduledMessageId,
    setError,
    setScheduledMessages,
    token
  ]);

  const handleVotePoll = useCallback(async (message: ChatMessage, optionId: string) => {
    if (!message.poll || message.poll.closed || message.deliveryStatus === "QUEUED" || votingMessageId) {
      return;
    }

    const nextOptionIds = message.poll.multipleChoice
      ? message.poll.options
          .filter((option) =>
            option.optionId === optionId ? !option.selectedByMe : option.selectedByMe
          )
          .map((option) => option.optionId)
      : [optionId];

    setVotingMessageId(message.messageId);
    setError(null);
    try {
      const updated = await api.votePoll(token, message.messageId, nextOptionIds);
      persistMessage(updated);
    } catch (voteError) {
      setError(voteError instanceof Error ? voteError.message : "Unable to vote in poll");
    } finally {
      setVotingMessageId(null);
    }
  }, [persistMessage, setError, setVotingMessageId, token, votingMessageId]);

  const handleClosePoll = useCallback(async (message: ChatMessage) => {
    if (!message.poll || message.poll.closed || message.deliveryStatus === "QUEUED" || closingPollMessageId) {
      return;
    }

    setClosingPollMessageId(message.messageId);
    setError(null);
    try {
      const updated = await api.closePoll(token, message.messageId);
      persistMessage(updated);
      setSelectedMessageId(null);
    } catch (closeError) {
      setError(closeError instanceof Error ? closeError.message : "Unable to close poll");
    } finally {
      setClosingPollMessageId(null);
    }
  }, [
    closingPollMessageId,
    persistMessage,
    setClosingPollMessageId,
    setError,
    setSelectedMessageId,
    token
  ]);

  return {
    handleCancelScheduledMessage,
    handleClosePoll,
    handleVotePoll
  };
}
