package com.alex.messenger.sync;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "alex.sync")
public class SyncProperties {

    private Reconciliation reconciliation = new Reconciliation();
    private Retention retention = new Retention();

    @Getter
    @Setter
    public static class Reconciliation {

        private boolean enabled = true;
        private int batchSize = 100;
        private Duration lookback = Duration.ofMinutes(5);
    }

    @Getter
    @Setter
    public static class Retention {

        private boolean enabled = true;
        private Duration ttl = Duration.ofDays(30);
        private int cleanupBatchSize = 500;
    }
}
