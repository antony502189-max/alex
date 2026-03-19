package com.alex.messenger.call;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "alex.calls.lifecycle")
public class CallLifecycleProperties {

    private Duration ringTimeout = Duration.ofSeconds(45);
    private int ringTimeoutBatchSize = 100;

    public Duration getRingTimeout() {
        return ringTimeout;
    }

    public void setRingTimeout(Duration ringTimeout) {
        this.ringTimeout = ringTimeout;
    }

    public int getRingTimeoutBatchSize() {
        return ringTimeoutBatchSize;
    }

    public void setRingTimeoutBatchSize(int ringTimeoutBatchSize) {
        this.ringTimeoutBatchSize = ringTimeoutBatchSize;
    }
}
