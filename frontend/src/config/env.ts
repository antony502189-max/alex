import { Platform } from "react-native";

const defaultHost = Platform.OS === "android" ? "10.0.2.2" : "localhost";
const env =
  (globalThis as { process?: { env?: Record<string, string | undefined> } })
    .process?.env ?? {};

export const API_BASE_URL =
  env.EXPO_PUBLIC_API_BASE_URL ?? `http://${defaultHost}:8080/api`;

export const WS_URL = env.EXPO_PUBLIC_WS_URL ?? `ws://${defaultHost}:8080/ws`;
