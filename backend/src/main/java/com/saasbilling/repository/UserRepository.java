package com.saasbilling.repository;

import com.saasbilling.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByActivationToken(String activationToken);

    Optional<User> findByPasswordResetToken(String passwordResetToken);

    // Tenant-scoped lookup - always prefer this over findById(id) alone
    // when returning a user to a caller, so a stray id from another
    // business can never be fetched.
    Optional<User> findByIdAndBusinessId(UUID id, UUID businessId);

    @Query("""
            select u from User u
            join fetch u.business b
            where u.id = :userId and b.id = :businessId
            """)
    Optional<User> findWithBusinessByIdAndBusinessId(@Param("userId") UUID userId,
                                                   @Param("businessId") UUID businessId);
}
