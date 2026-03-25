package com.alex.messenger.compliance;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "alex.compliance")
public class ComplianceProperties {

    private Export export = new Export();

    @Getter
    @Setter
    public static class Export {

        private Duration artifactTtl = Duration.ofHours(24);
        private int cleanupBatchSize = 50;
        private int maxInlineMessages = 100;
        private String encryptionSecret = "dev-compliance-export-key";
    }
}
