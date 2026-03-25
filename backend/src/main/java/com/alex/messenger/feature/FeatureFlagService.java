package com.alex.messenger.feature;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private final FeatureProperties featureProperties;

    public void requireStoriesEnabled() {
        require(featureProperties.isStories(), "Stories");
    }

    public void requireBotsEnabled() {
        require(featureProperties.isBots(), "Bots");
    }

    public void requireCallsEnabled() {
        require(featureProperties.isCalls(), "Calls");
    }

    public void requireSecretChatsEnabled() {
        require(featureProperties.isSecretChats(), "Secret chats");
    }

    public void requireAdminComplianceEnabled() {
        require(featureProperties.isAdminCompliance(), "Admin compliance");
    }

    public void requireLawfulDirectExportEnabled() {
        require(featureProperties.isLawfulDirectExport(), "Lawful direct export");
    }

    public void requireGroupCallsEnabled() {
        require(featureProperties.isGroupCalls(), "Group calls");
    }

    public void requireStoryInteractionsEnabled() {
        require(featureProperties.isStoryInteractions(), "Story interactions");
    }

    public void requireBotApiFullEnabled() {
        require(featureProperties.isBotApiFull(), "Full bot API");
    }

    public void requireBusinessEnabled() {
        require(featureProperties.isBusiness(), "Business");
    }

    public void requirePaymentsEnabled() {
        require(featureProperties.isPayments(), "Payments");
    }

    public void requirePremiumEnabled() {
        require(featureProperties.isPremium(), "Premium");
    }

    public void requireMonetizationEnabled() {
        require(featureProperties.isMonetization(), "Monetization");
    }

    public void requireTranslationsEnabled() {
        require(featureProperties.isTranslations(), "Translations");
    }

    public boolean isCallsEnabled() {
        return featureProperties.isCalls();
    }

    public boolean isGroupCallsEnabled() {
        return featureProperties.isGroupCalls();
    }

    private void require(boolean enabled, String featureName) {
        if (enabled) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "%s is disabled".formatted(featureName));
    }
}
