jest.mock("../../services/api", () => ({
  api: {
    getRecentGifs: jest.fn(),
    getStickerPacks: jest.fn()
  }
}));

jest.mock("../../services/deviceLocation", () => ({
  deviceLocation: {
    getCurrentPosition: jest.fn()
  }
}));

import { act, renderHook } from "@testing-library/react-native";
import { deviceLocation } from "../../services/deviceLocation";
import { useChatStructuredComposer } from "./useChatStructuredComposer";

describe("useChatStructuredComposer", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (deviceLocation.getCurrentPosition as jest.Mock).mockResolvedValue({
      address: "Minsk, Belarus",
      latitude: 53.90454,
      longitude: 27.56152
    });
  });

  it("hydrates location fields from the current device position", async () => {
    const setError = jest.fn();
    const { result } = renderHook(() =>
      useChatStructuredComposer({
        setError,
        token: "token-1"
      })
    );

    await act(async () => {
      await result.current.handleUseCurrentLocation();
    });

    expect(deviceLocation.getCurrentPosition).toHaveBeenCalledTimes(1);
    expect(result.current.locationLatitude).toBe("53.904540");
    expect(result.current.locationLongitude).toBe("27.561520");
    expect(result.current.locationAddress).toBe("Minsk, Belarus");
    expect(result.current.locationTitle).toBe("Current location");
    expect(result.current.canSendLocation).toBe(true);
  });
});
