package com.alex.messenger.message;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "alex.message.delivery")
public class MessageDeliveryProperties {

    private Reconciliation reconciliation = new Reconciliation();

    @Getter
    @Setter
    public static class Reconciliation {

        private boolean enabled = true;
        private int chatBatchSize = 100;
        private int messageBatchSize = 200;
        private Duration lookback = Duration.ofMinutes(10);
        private Duration deliveryGracePeriod = Duration.ofSeconds(5);
    }
}
