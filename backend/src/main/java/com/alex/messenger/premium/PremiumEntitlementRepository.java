package com.alex.messenger.premium;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PremiumEntitlementRepository extends JpaRepository<PremiumEntitlementEntity, UUID> {
}
