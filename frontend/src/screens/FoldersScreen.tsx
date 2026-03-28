import React from "react";
import { FoldersScreenContent } from "../components/folders/FoldersScreenContent";
import { AppScreen } from "../components/ui/AppScreen";
import { useFoldersController } from "../components/folders/useFoldersController";

type FoldersScreenProps = {
  token: string;
  onClose: () => void;
};

export function FoldersScreen({ token, onClose }: FoldersScreenProps) {
  const controller = useFoldersController({
    token
  });

  return (
    <AppScreen padding="xl">
      <FoldersScreenContent controller={controller} onClose={onClose} />
    </AppScreen>
  );
}
