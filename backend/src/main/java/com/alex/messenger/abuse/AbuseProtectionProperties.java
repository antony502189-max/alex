package com.alex.messenger.abuse;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "alex.abuse")
public class AbuseProtectionProperties {

    private SimpleRate chatCreation = new SimpleRate();
    private ChatScopedRate inviteLinkCreation = new ChatScopedRate();
    private ChatScopedRate joinRequestCreation = new ChatScopedRate();
    private ChatScopedRate messageSend = new ChatScopedRate();
    private SimpleRate chatReport = new SimpleRate();
    private SimpleRate messageReport = new SimpleRate();

    @Getter
    @Setter
    public static class SimpleRate {

        private int max = 0;
        private Duration window = Duration.ofMinutes(1);
    }

    @Getter
    @Setter
    public static class ChatScopedRate {

        private int globalMax = 0;
        private Duration globalWindow = Duration.ofMinutes(1);
        private int chatMax = 0;
        private Duration chatWindow = Duration.ofMinutes(1);
    }
}
