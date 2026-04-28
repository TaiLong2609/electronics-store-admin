package com.example.ecomerce.repository;

import com.example.ecomerce.models.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

	List<Product> findAllByOrderByIdAsc();

	List<Product> findByNameContainingIgnoreCaseOrSkuContainingIgnoreCaseOrderByIdAsc(String name, String sku);

	Optional<Product> findFirstByMpnIgnoreCase(String mpn);

	Optional<Product> findFirstBySkuIgnoreCase(String sku);

	boolean existsByCategoryId(Long categoryId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Product p where p.id = :id")
	Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
