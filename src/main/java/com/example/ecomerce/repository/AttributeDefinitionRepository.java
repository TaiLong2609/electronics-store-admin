package com.example.ecomerce.repository;

import com.example.ecomerce.models.AttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttributeDefinitionRepository extends JpaRepository<AttributeDefinition, Long> {

    Optional<AttributeDefinition> findByCodeIgnoreCase(String code);
}
