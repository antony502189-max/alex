import React from "react";
import { AuthScreenContent } from "../components/auth/AuthScreenContent";
import {
  AUTH_SCREEN_SUBTITLE,
  AUTH_SCREEN_TITLE
} from "../components/auth/authPresentation";
import { useAuthScreenController } from "../components/auth/useAuthScreenController";
import { AppScreen } from "../components/ui/AppScreen";
import type { AuthSession } from "../types";

type AuthScreenProps = {
  mode?: "LOGIN" | "ADD_ACCOUNT";
  onCancel?: () => void;
  onAuthenticated?: (session: AuthSession) => void;
};

export function AuthScreen({
  mode = "LOGIN",
  onCancel,
  onAuthenticated
}: AuthScreenProps) {
  const controller = useAuthScreenController({
    onAuthenticated
  });

  return (
    <AppScreen>
      <AuthScreenContent
        controller={controller}
        mode={mode}
        onCancel={onCancel}
        subtitle={AUTH_SCREEN_SUBTITLE}
        title={AUTH_SCREEN_TITLE}
      />
    </AppScreen>
  );
}
