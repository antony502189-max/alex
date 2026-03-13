package com.alex.messenger.call;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "alex.calls.rtc")
public class CallRtcProperties {

    private List<IceServerProperties> iceServers = new ArrayList<>();
    private GeneratedTurnProperties generatedTurn = new GeneratedTurnProperties();
    private MediaPolicyProperties mediaPolicy = new MediaPolicyProperties();

    public List<IceServerProperties> getIceServers() {
        return iceServers;
    }

    public void setIceServers(List<IceServerProperties> iceServers) {
        this.iceServers = iceServers;
    }

    public GeneratedTurnProperties getGeneratedTurn() {
        return generatedTurn;
    }

    public void setGeneratedTurn(GeneratedTurnProperties generatedTurn) {
        this.generatedTurn = generatedTurn;
    }

    public MediaPolicyProperties getMediaPolicy() {
        return mediaPolicy;
    }

    public void setMediaPolicy(MediaPolicyProperties mediaPolicy) {
        this.mediaPolicy = mediaPolicy;
    }

    public static class IceServerProperties {

        private String url;
        private String username;
        private String credential;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getCredential() {
            return credential;
        }

        public void setCredential(String credential) {
            this.credential = credential;
        }
    }

    public static class GeneratedTurnProperties {

        private boolean enabled;
        private String url;
        private String sharedSecret;
        private String usernamePrefix = "alex";
        private Duration ttl = Duration.ofHours(12);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getSharedSecret() {
            return sharedSecret;
        }

        public void setSharedSecret(String sharedSecret) {
            this.sharedSecret = sharedSecret;
        }

        public String getUsernamePrefix() {
            return usernamePrefix;
        }

        public void setUsernamePrefix(String usernamePrefix) {
            this.usernamePrefix = usernamePrefix;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }
    }

    public static class MediaPolicyProperties {

        private int videoBitrateHighKbps = 1400;
        private int videoBitrateMediumKbps = 800;
        private int videoBitrateLowKbps = 240;
        private int screenShareBitrateKbps = 1600;
        private int statsSampleIntervalSeconds = 4;
        private int degradedConnectionRttMs = 220;
        private int poorConnectionRttMs = 450;
        private int degradedConnectionPacketLossPercent = 5;
        private int poorConnectionPacketLossPercent = 12;

        public int getVideoBitrateHighKbps() {
            return videoBitrateHighKbps;
        }

        public void setVideoBitrateHighKbps(int videoBitrateHighKbps) {
            this.videoBitrateHighKbps = videoBitrateHighKbps;
        }

        public int getVideoBitrateMediumKbps() {
            return videoBitrateMediumKbps;
        }

        public void setVideoBitrateMediumKbps(int videoBitrateMediumKbps) {
            this.videoBitrateMediumKbps = videoBitrateMediumKbps;
        }

        public int getVideoBitrateLowKbps() {
            return videoBitrateLowKbps;
        }

        public void setVideoBitrateLowKbps(int videoBitrateLowKbps) {
            this.videoBitrateLowKbps = videoBitrateLowKbps;
        }

        public int getScreenShareBitrateKbps() {
            return screenShareBitrateKbps;
        }

        public void setScreenShareBitrateKbps(int screenShareBitrateKbps) {
            this.screenShareBitrateKbps = screenShareBitrateKbps;
        }

        public int getStatsSampleIntervalSeconds() {
            return statsSampleIntervalSeconds;
        }

        public void setStatsSampleIntervalSeconds(int statsSampleIntervalSeconds) {
            this.statsSampleIntervalSeconds = statsSampleIntervalSeconds;
        }

        public int getDegradedConnectionRttMs() {
            return degradedConnectionRttMs;
        }

        public void setDegradedConnectionRttMs(int degradedConnectionRttMs) {
            this.degradedConnectionRttMs = degradedConnectionRttMs;
        }

        public int getPoorConnectionRttMs() {
            return poorConnectionRttMs;
        }

        public void setPoorConnectionRttMs(int poorConnectionRttMs) {
            this.poorConnectionRttMs = poorConnectionRttMs;
        }

        public int getDegradedConnectionPacketLossPercent() {
            return degradedConnectionPacketLossPercent;
        }

        public void setDegradedConnectionPacketLossPercent(int degradedConnectionPacketLossPercent) {
            this.degradedConnectionPacketLossPercent = degradedConnectionPacketLossPercent;
        }

        public int getPoorConnectionPacketLossPercent() {
            return poorConnectionPacketLossPercent;
        }

        public void setPoorConnectionPacketLossPercent(int poorConnectionPacketLossPercent) {
            this.poorConnectionPacketLossPercent = poorConnectionPacketLossPercent;
        }
    }
}
