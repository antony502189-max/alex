package com.alex.messenger.call.dto;

public record CallMediaPolicyResponse(
        int videoBitrateHighKbps,
        int videoBitrateMediumKbps,
        int videoBitrateLowKbps,
        int screenShareBitrateKbps,
        int statsSampleIntervalSeconds,
        int degradedConnectionRttMs,
        int poorConnectionRttMs,
        int degradedConnectionPacketLossPercent,
        int poorConnectionPacketLossPercent
) {
}
