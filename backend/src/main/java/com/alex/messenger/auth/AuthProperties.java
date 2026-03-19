package com.alex.messenger.auth;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "alex.auth")
public class AuthProperties {

    private Code code = new Code();
    private Refresh refresh = new Refresh();
    private Cleanup cleanup = new Cleanup();
    private TwoFactor twoFactor = new TwoFactor();
    private Qr qr = new Qr();
    private Passkeys passkeys = new Passkeys();
    private PhoneChange phoneChange = new PhoneChange();
    private Identity identity = new Identity();

    @Getter
    @Setter
    public static class Code {

        private int length = 6;
        private Duration ttl = Duration.ofMinutes(10);
        private int maxAttempts = 5;
        private int maxRequestsPerWindow = 3;
        private Duration requestWindow = Duration.ofMinutes(10);
        private boolean exposeDebugCode = true;
    }

    @Getter
    @Setter
    public static class Refresh {

        private Duration ttl = Duration.ofDays(30);
    }

    @Getter
    @Setter
    public static class Cleanup {

        private int batchSize = 200;
    }

    @Getter
    @Setter
    public static class TwoFactor {

        private Duration challengeTtl = Duration.ofMinutes(10);
        private int maxAttempts = 5;
        private int saltLengthBytes = 16;
        private int pbkdf2Iterations = 210_000;
        private int minPasswordLength = 8;
    }

    @Getter
    @Setter
    public static class Qr {

        private Duration challengeTtl = Duration.ofMinutes(10);
        private int tokenBytes = 32;
    }

    @Getter
    @Setter
    public static class Passkeys {

        private Duration challengeTtl = Duration.ofMinutes(10);
        private int maxCredentialsPerUser = 10;
    }

    @Getter
    @Setter
    public static class PhoneChange {

        private Duration ttl = Duration.ofMinutes(10);
    }

    @Getter
    @Setter
    public static class Identity {

        private Duration ttl = Duration.ofMinutes(5);
    }
}
