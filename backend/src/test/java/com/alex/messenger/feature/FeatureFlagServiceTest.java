package com.alex.messenger.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class FeatureFlagServiceTest {

    @Test
    void requireBotsEnabledThrowsWhenFeatureIsDisabled() {
        FeatureProperties properties = new FeatureProperties();
        properties.setBots(false);
        FeatureFlagService service = new FeatureFlagService(properties);

        ResponseStatusException exception =
                catchThrowableOfType(service::requireBotsEnabled, ResponseStatusException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getReason()).contains("Bots");
    }

    @Test
    void requireAdminComplianceEnabledPassesWhenFeatureIsEnabled() {
        FeatureProperties properties = new FeatureProperties();
        properties.setAdminCompliance(true);
        FeatureFlagService service = new FeatureFlagService(properties);

        service.requireAdminComplianceEnabled();

        assertThat(properties.isAdminCompliance()).isTrue();
    }
}
