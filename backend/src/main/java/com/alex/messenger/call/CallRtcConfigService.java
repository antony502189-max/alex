package com.alex.messenger.call;

import com.alex.messenger.call.dto.CallIceServerResponse;
import com.alex.messenger.call.dto.CallMediaPolicyResponse;
import com.alex.messenger.call.dto.CallRtcConfigResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CallRtcConfigService {

    private final CallRtcProperties callRtcProperties;

    public CallRtcConfigService(CallRtcProperties callRtcProperties) {
        this.callRtcProperties = callRtcProperties;
    }

    public CallRtcConfigResponse getRtcConfig() {
        return getRtcConfig(null);
    }

    public CallRtcConfigResponse getRtcConfig(UUID userId) {
        List<CallIceServerResponse> configuredIceServers = callRtcProperties.getIceServers().stream()
                .filter(server -> server.getUrl() != null && !server.getUrl().isBlank())
                .map(server -> new CallIceServerResponse(
                        server.getUrl().trim(),
                        normalize(server.getUsername()),
                        normalize(server.getCredential())
                ))
                .toList();

        CallIceServerResponse generatedTurn = buildGeneratedTurnCredentials(userId);
        List<CallIceServerResponse> iceServers = generatedTurn == null
                ? configuredIceServers
                : java.util.stream.Stream.concat(configuredIceServers.stream(), java.util.stream.Stream.of(generatedTurn))
                        .toList();
        CallRtcProperties.MediaPolicyProperties mediaPolicy = callRtcProperties.getMediaPolicy();
        return new CallRtcConfigResponse(
                iceServers,
                new CallMediaPolicyResponse(
                        mediaPolicy.getVideoBitrateHighKbps(),
                        mediaPolicy.getVideoBitrateMediumKbps(),
                        mediaPolicy.getVideoBitrateLowKbps(),
                        mediaPolicy.getScreenShareBitrateKbps(),
                        mediaPolicy.getStatsSampleIntervalSeconds(),
                        mediaPolicy.getDegradedConnectionRttMs(),
                        mediaPolicy.getPoorConnectionRttMs(),
                        mediaPolicy.getDegradedConnectionPacketLossPercent(),
                        mediaPolicy.getPoorConnectionPacketLossPercent()
                )
        );
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private CallIceServerResponse buildGeneratedTurnCredentials(UUID userId) {
        CallRtcProperties.GeneratedTurnProperties generatedTurn = callRtcProperties.getGeneratedTurn();
        if (generatedTurn == null || !generatedTurn.isEnabled()) {
            return null;
        }

        String url = normalize(generatedTurn.getUrl());
        String sharedSecret = normalize(generatedTurn.getSharedSecret());
        if (url == null || sharedSecret == null) {
            return null;
        }

        long expiresAt = Instant.now().plus(generatedTurn.getTtl()).getEpochSecond();
        String usernamePrefix = normalize(generatedTurn.getUsernamePrefix());
        String username = "%d:%s:%s".formatted(
                expiresAt,
                usernamePrefix != null ? usernamePrefix : "alex",
                userId != null ? userId : "system"
        );

        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String credential = java.util.Base64.getEncoder()
                    .encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
            return new CallIceServerResponse(url, username, credential);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to generate TURN credentials", exception);
        }
    }
}
