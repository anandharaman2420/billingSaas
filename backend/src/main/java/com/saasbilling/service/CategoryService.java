package com.saasbilling.service;

import com.saasbilling.dto.common.CategoryDtos.CategoryRequest;
import com.saasbilling.dto.common.CategoryDtos.CategoryResponse;
import com.saasbilling.entity.Category;
import com.saasbilling.entity.CategoryType;
import com.saasbilling.exception.DuplicateResourceException;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.repository.CategoryRepository;
import com.saasbilling.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(CategoryType type) {
        return categoryRepository.findByBusinessIdAndType(TenantContext.currentBusinessId(), type)
                .stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        UUID businessId = TenantContext.currentBusinessId();

        if (categoryRepository.existsByBusinessIdAndNameIgnoreCaseAndType(businessId, request.name(), request.type())) {
            throw new DuplicateResourceException("A category named '" + request.name() + "' already exists");
        }

        Category category = new Category();
        category.setBusinessId(businessId);
        category.setName(request.name());
        category.setType(request.type());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID id) {
        UUID businessId = TenantContext.currentBusinessId();
        Category category = categoryRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        categoryRepository.delete(category);
    }
}
