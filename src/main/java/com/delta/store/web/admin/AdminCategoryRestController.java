package com.delta.store.web.admin;



import com.delta.store.product.repository.ProductCategoryRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryRestController {

    private final ProductCategoryRepository categoryRepository;

    public AdminCategoryRestController(ProductCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // DTO minimale per la select
    public record CategoryDTO(Long id, String name) {}

    @GetMapping
    public List<CategoryDTO> listCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(c -> new CategoryDTO(c.getId(), c.getName()))
                .toList();
    }
}

