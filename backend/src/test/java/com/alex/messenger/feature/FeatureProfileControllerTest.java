package com.alex.messenger.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.alex.messenger.feature.dto.FeatureProfileResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class FeatureProfileControllerTest {

    @Mock
    private Environment environment;

    @Test
    void returnsMvpFeatureContractForFrontendClients() {
        FeatureProperties featureProperties = new FeatureProperties();
        featureProperties.setStories(false);
        featureProperties.setBots(false);
        featureProperties.setCalls(true);
        featureProperties.setSecretChats(false);
        featureProperties.setAdminCompliance(true);
        featureProperties.setLawfulDirectExport(false);
        featureProperties.setGroupCalls(false);
        featureProperties.setBotApiFull(false);
        featureProperties.setBusiness(false);
        featureProperties.setPayments(false);
        featureProperties.setPremium(false);
        featureProperties.setMonetization(false);
        featureProperties.setTranslations(false);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"mvp"});

        FeatureProfileController controller = new FeatureProfileController(featureProperties, environment);

        ResponseEntity<FeatureProfileResponse> response = controller.getProfile();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().productProfile()).isEqualTo("mvp");
        assertThat(response.getBody().calls()).isTrue();
        assertThat(response.getBody().directCalls()).isTrue();
        assertThat(response.getBody().groupCalls()).isFalse();
        assertThat(response.getBody().callJoinLinks()).isFalse();
        assertThat(response.getBody().callComments()).isFalse();
        assertThat(response.getBody().callRecording()).isFalse();
        assertThat(response.getBody().adminCompliance()).isTrue();
        assertThat(response.getBody().secretChats()).isFalse();
    }
}
