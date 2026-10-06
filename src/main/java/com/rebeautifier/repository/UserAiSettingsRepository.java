package com.rebeautifier.repository;

import com.rebeautifier.domain.UserAiSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserAiSettingsRepository extends JpaRepository<UserAiSettings, UUID> {
}
