import React from "react";
import { BotDeveloperScreenContent } from "../components/botDeveloper/BotDeveloperScreenContent";
import { AppScreen } from "../components/ui/AppScreen";
import { useBotDeveloperController } from "../components/botDeveloper/useBotDeveloperController";

type BotDeveloperScreenProps = {
  onClose: () => void;
  token: string;
};

export function BotDeveloperScreen({
  onClose,
  token
}: BotDeveloperScreenProps) {
  const controller = useBotDeveloperController({ token });

  return (
    <AppScreen padding="xl">
      <BotDeveloperScreenContent controller={controller} onClose={onClose} />
    </AppScreen>
  );
}
