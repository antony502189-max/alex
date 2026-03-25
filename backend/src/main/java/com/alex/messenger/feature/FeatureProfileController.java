package com.alex.messenger.feature;

import com.alex.messenger.feature.dto.FeatureProfileResponse;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/features")
@RequiredArgsConstructor
public class FeatureProfileController {

    private final FeatureProperties featureProperties;
    private final Environment environment;

    @GetMapping("/profile")
    public ResponseEntity<FeatureProfileResponse> getProfile() {
        boolean callsEnabled = featureProperties.isCalls();
        boolean groupCallsEnabled = callsEnabled && featureProperties.isGroupCalls();
        return ResponseEntity.ok(new FeatureProfileResponse(
                resolveProductProfile(),
                featureProperties.isStories(),
                featureProperties.isBots(),
                callsEnabled,
                callsEnabled,
                groupCallsEnabled,
                groupCallsEnabled,
                groupCallsEnabled,
                groupCallsEnabled,
                groupCallsEnabled,
                groupCallsEnabled,
                groupCallsEnabled,
                groupCallsEnabled,
                featureProperties.isSecretChats(),
                featureProperties.isAdminCompliance(),
                featureProperties.isLawfulDirectExport(),
                featureProperties.isBotApiFull(),
                featureProperties.isBusiness(),
                featureProperties.isPayments(),
                featureProperties.isPremium(),
                featureProperties.isMonetization(),
                featureProperties.isTranslations()
        ));
    }

    private String resolveProductProfile() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        if (activeProfiles.contains("mvp")) {
            return "mvp";
        }
        return activeProfiles.isEmpty() ? "default" : activeProfiles.get(0);
    }
}
