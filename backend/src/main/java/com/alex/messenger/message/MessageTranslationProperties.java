package com.alex.messenger.message;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "alex.translations")
public class MessageTranslationProperties {

    private String providerUrl;
    private String apiKey;
    private Duration timeout = Duration.ofSeconds(5);
}
