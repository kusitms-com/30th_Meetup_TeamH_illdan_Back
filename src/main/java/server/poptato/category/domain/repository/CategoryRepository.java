package server.poptato.category.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import server.poptato.category.domain.entity.Category;

import java.util.Optional;

public interface CategoryRepository {
    Optional<Integer> findMaxCategoryOrderByUserId(Long userId);

    Category save(Category category);

    Page<Category> findByUserIdOrderByCategoryOrder(Long userId, Pageable pageable);

    Optional<Category> findById(Long categoryId);

    void delete(Category category);
}