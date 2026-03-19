package com.alex.messenger.account;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "alex.account")
public class AccountProperties {

    private Export export = new Export();
    private Deletion deletion = new Deletion();

    @Getter
    @Setter
    public static class Export {

        private String defaultFormat = "JSON";
    }

    @Getter
    @Setter
    public static class Deletion {

        private Duration defaultDelay = Duration.ofDays(7);
        private int defaultSelfDestructDays = 365;
        private int executionBatchSize = 50;
    }
}
