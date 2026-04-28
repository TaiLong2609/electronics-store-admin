package com.example.ecomerce.repository;

import com.example.ecomerce.models.StoreSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreSettingRepository extends JpaRepository<StoreSetting, Long> {
}

