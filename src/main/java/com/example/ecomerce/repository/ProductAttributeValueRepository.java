package com.example.ecomerce.repository;

import com.example.ecomerce.models.ProductAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, Long> {

    void deleteByProductId(Long productId);

    List<ProductAttributeValue> findByProductIdOrderByIdAsc(Long productId);

    List<ProductAttributeValue> findByProductIdInOrderByProductIdAscIdAsc(List<Long> productIds);

    Optional<ProductAttributeValue> findByProductIdAndAttributeId(Long productId, Long attributeId);

    void deleteByProductIdAndAttributeId(Long productId, Long attributeId);

    void deleteByProductIdAndAttributeIdNotIn(Long productId, List<Long> attributeIds);

    @Modifying
    @Query("delete from ProductAttributeValue pav where pav.product.category.id = :categoryId and pav.attribute.id = :attributeId")
    void deleteByProductCategoryAndAttribute(
            @Param("categoryId") Long categoryId,
            @Param("attributeId") Long attributeId
    );
}
