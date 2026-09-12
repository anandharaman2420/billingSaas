package com.saasbilling.repository;

import com.saasbilling.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessRepository extends JpaRepository<Business, UUID> {

    Optional<Business> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
