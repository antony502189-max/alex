import React from "react";
import { SessionsScreenContent } from "../components/sessions/SessionsScreenContent";
import { AppScreen } from "../components/ui/AppScreen";
import { useSessionsScreenController } from "../components/sessions/useSessionsScreenController";

type SessionsScreenProps = {
  currentSessionId: string;
  onClose: () => void;
  token: string;
};

export function SessionsScreen({
  currentSessionId,
  onClose,
  token
}: SessionsScreenProps) {
  const controller = useSessionsScreenController({
    token
  });

  return (
    <AppScreen padding="xl">
      <SessionsScreenContent
        controller={controller}
        currentSessionId={currentSessionId}
        onClose={onClose}
      />
    </AppScreen>
  );
}
