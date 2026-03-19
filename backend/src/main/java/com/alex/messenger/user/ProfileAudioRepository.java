package com.alex.messenger.user;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileAudioRepository extends JpaRepository<ProfileAudioEntity, UUID> {
}
