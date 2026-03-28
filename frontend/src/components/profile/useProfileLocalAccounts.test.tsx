import { act, renderHook } from "@testing-library/react-native";
import { useAppStore } from "../../store/useAppStore";
import { useProfileLocalAccounts } from "./useProfileLocalAccounts";

describe("useProfileLocalAccounts", () => {
  beforeEach(() => {
    const now = new Date().toISOString();
    useAppStore.setState({
      hydrated: true,
      hydrating: false,
      activeAccountId: "user-1",
      accountsById: {
        "user-1": {
          session: {
            token: "token-1",
            refreshToken: "refresh-1",
            sessionId: "session-1",
            userId: "user-1",
            phoneNumber: "+375291111111",
            displayName: "Alex",
            username: "alex",
            accessTokenExpiresAt: null,
            refreshTokenExpiresAt: null,
            authMethod: "OTP",
            trustedSession: true
          },
          featureProfile: null,
          chats: [],
          folders: [],
          messagesByChat: {},
          lastActivatedAt: now
        },
        "user-2": {
          session: {
            token: "token-2",
            refreshToken: "refresh-2",
            sessionId: "session-2",
            userId: "user-2",
            phoneNumber: "+375292222222",
            displayName: "Nadia",
            username: "nadia",
            accessTokenExpiresAt: null,
            refreshTokenExpiresAt: null,
            authMethod: "OTP",
            trustedSession: true
          },
          featureProfile: null,
          chats: [],
          folders: [],
          messagesByChat: {},
          lastActivatedAt: new Date(Date.now() - 1000).toISOString()
        }
      },
      session: {
        token: "token-1",
        refreshToken: "refresh-1",
        sessionId: "session-1",
        userId: "user-1",
        phoneNumber: "+375291111111",
        displayName: "Alex",
        username: "alex",
        accessTokenExpiresAt: null,
        refreshTokenExpiresAt: null,
        authMethod: "OTP",
        trustedSession: true
      },
      featureProfile: null,
      chats: [],
      folders: [],
      messagesByChat: {}
    });
  });

  it("switches and removes local accounts with notices", () => {
    const notices: Array<string | null> = [];
    const errors: Array<string | null> = [];
    const { result } = renderHook(() => useProfileLocalAccounts());

    act(() => {
      result.current.handleSwitchAccount("user-2", (value) => notices.push(value), (value) =>
        errors.push(value)
      );
    });

    expect(useAppStore.getState().activeAccountId).toBe("user-2");
    expect(notices.at(-1)).toBe("Switched to Nadia.");
    expect(errors.at(-1)).toBeNull();

    act(() => {
      result.current.handleRemoveLocalAccount("user-1", (value) => notices.push(value), (value) =>
        errors.push(value)
      );
    });

    expect(useAppStore.getState().accountsById["user-1"]).toBeUndefined();
    expect(notices.at(-1)).toBe("Local account removed from this device.");
  });
});
