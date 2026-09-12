package com.saasbilling.repository;

import com.saasbilling.entity.BusinessSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessSettingsRepository extends JpaRepository<BusinessSettings, UUID> {

    Optional<BusinessSettings> findByBusinessId(UUID businessId);
}
