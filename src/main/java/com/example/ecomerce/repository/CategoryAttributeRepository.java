package com.example.ecomerce.repository;

import com.example.ecomerce.models.CategoryAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryAttributeRepository extends JpaRepository<CategoryAttribute, Long> {

    boolean existsByCategoryId(Long categoryId);

    boolean existsByCategoryIdAndAttributeId(Long categoryId, Long attributeId);

    boolean existsByCategoryIdAndAttributeIdAndIdNot(Long categoryId, Long attributeId, Long id);

    List<CategoryAttribute> findByCategoryIdOrderBySortOrderAscIdAsc(Long categoryId);

    Optional<CategoryAttribute> findByCategoryIdAndId(Long categoryId, Long id);

    long countByAttributeId(Long attributeId);
}
