import React from "react";
import { Text } from "react-native";
import { render } from "@testing-library/react-native";
import { ScreenScrollView } from "./ScreenScrollView";

describe("ScreenScrollView", () => {
  it("renders children inside the scroll container", () => {
    const screen = render(
      <ScreenScrollView gap="md" paddingBottom="xl">
        <Text>Scrollable content</Text>
      </ScreenScrollView>
    );

    expect(screen.getByText("Scrollable content")).toBeTruthy();
  });
});
