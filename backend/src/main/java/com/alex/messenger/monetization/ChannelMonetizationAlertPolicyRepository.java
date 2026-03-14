package com.alex.messenger.monetization;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMonetizationAlertPolicyRepository
        extends JpaRepository<ChannelMonetizationAlertPolicyEntity, UUID> {
}
