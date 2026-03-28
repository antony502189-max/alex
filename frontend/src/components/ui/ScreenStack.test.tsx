import React from "react";
import { Text } from "react-native";
import { render } from "@testing-library/react-native";
import { ScreenStack } from "./ScreenStack";

describe("ScreenStack", () => {
  it("renders children inside the stack", () => {
    const screen = render(
      <ScreenStack direction="row" gap="md">
        <Text>First</Text>
        <Text>Second</Text>
      </ScreenStack>
    );

    expect(screen.getByText("First")).toBeTruthy();
    expect(screen.getByText("Second")).toBeTruthy();
  });
});
