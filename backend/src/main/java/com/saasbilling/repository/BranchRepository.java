package com.saasbilling.repository;

import com.saasbilling.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    Optional<Branch> findByIdAndBusinessIdAndStatus(UUID id, UUID businessId, String status);

    Optional<Branch> findByIdAndBusinessId(UUID id, UUID businessId);

    Optional<Branch> findFirstByBusinessIdAndDefaultBranchTrue(UUID businessId);

    List<Branch> findByBusinessIdAndStatusOrderByDefaultBranchDescBranchNameAsc(UUID businessId, String status);

    List<Branch> findByBusinessIdOrderByDefaultBranchDescBranchNameAsc(UUID businessId);

    boolean existsByIdAndBusinessIdAndStatus(UUID id, UUID businessId, String status);
}
