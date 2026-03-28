import React from "react";
import { CreateChatScreenContent } from "../components/createChat/CreateChatScreenContent";
import { type CreateChatMode } from "../components/createChat/createChatPresentation";
import { AppScreen } from "../components/ui/AppScreen";
import { useCreateChatController } from "../components/createChat/useCreateChatController";
import type { ChatSummary } from "../types";

type CreateChatScreenProps = {
  mode: CreateChatMode;
  token: string;
  onClose: () => void;
  onCreated: (chat: ChatSummary) => void;
};

export function CreateChatScreen({
  mode,
  token,
  onClose,
  onCreated
}: CreateChatScreenProps) {
  const controller = useCreateChatController({
    mode,
    onCreated,
    token
  });

  return (
    <AppScreen padding="xl">
      <CreateChatScreenContent
        controller={controller}
        mode={mode}
        onClose={onClose}
      />
    </AppScreen>
  );
}
