package com.alex.messenger.lawful;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "alex.lawful")
public class LawfulProperties {

    private final DirectExportProperties directExport = new DirectExportProperties();

    public DirectExportProperties getDirectExport() {
        return directExport;
    }

    public static class DirectExportProperties {

        private int inlineMessageLimit = 500;

        public int getInlineMessageLimit() {
            return inlineMessageLimit;
        }

        public void setInlineMessageLimit(int inlineMessageLimit) {
            this.inlineMessageLimit = inlineMessageLimit;
        }
    }
}
