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

    // Same tenant-scoped guarantee, but eagerly fetches the lazy Business
    // association in the same query. Use this (from inside a
    // @Transactional service method) whenever the caller needs to read
    // fields off user.getBusiness() - otherwise you'll hit
    // LazyInitializationException once the Hibernate session closes
    // (open-in-view is disabled on purpose - see application.yml).
    @Query("select u from User u join fetch u.business where u.id = :id and u.business.id = :businessId")
    Optional<User> findWithBusinessByIdAndBusinessId(@Param("id") UUID id, @Param("businessId") UUID businessId);
}
