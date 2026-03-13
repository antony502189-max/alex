package com.alex.messenger.call.dto;

import java.util.List;

public record CallRtcConfigResponse(
        List<CallIceServerResponse> iceServers,
        CallMediaPolicyResponse mediaPolicy
) {
}
