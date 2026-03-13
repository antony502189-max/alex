package com.alex.messenger.feature;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "alex.features")
public class FeatureProperties {

    private boolean stories = true;
    private boolean bots = true;
    private boolean calls = true;
    private boolean secretChats = true;
    private boolean adminCompliance = true;
    private boolean lawfulDirectExport = false;
    private boolean groupCalls = false;
    private boolean storyInteractions = false;
    private boolean botApiFull = false;
    private boolean business = false;
    private boolean payments = false;
    private boolean premium = false;
    private boolean monetization = false;
    private boolean translations = false;
}
