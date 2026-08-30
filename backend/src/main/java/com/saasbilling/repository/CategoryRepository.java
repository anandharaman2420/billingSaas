package com.saasbilling.repository;

import com.saasbilling.entity.Category;
import com.saasbilling.entity.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByBusinessIdAndType(UUID businessId, CategoryType type);

    Optional<Category> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByBusinessIdAndNameIgnoreCaseAndType(UUID businessId, String name, CategoryType type);
}
