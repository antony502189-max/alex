package com.alex.messenger.user;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfilePreferenceRepository extends JpaRepository<UserProfilePreferenceEntity, UUID> {
}
