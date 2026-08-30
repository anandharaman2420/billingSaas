package com.saasbilling.dto.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.saasbilling.entity.Category;
import com.saasbilling.entity.CategoryType;

import java.util.UUID;

public class CategoryDtos {

    public record CategoryRequest(
            @NotBlank(message = "Category name is required")
            @Size(max = 100)
            String name,

            @NotNull(message = "Category type is required")
            CategoryType type
    ) {
    }

    public record CategoryResponse(UUID id, String name, String type) {
        public static CategoryResponse from(Category category) {
            return new CategoryResponse(category.getId(), category.getName(), category.getType().name());
        }
    }
}
