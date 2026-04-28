package com.example.ecomerce.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoreSetting {

    @Id
    private Long id;

    @Column(name = "store_name", length = 255)
    private String storeName;

    @Column(length = 64)
    private String phone;

    @Column(length = 500)
    private String address;

    @Column(name = "mouser_api_key", length = 255)
    private String mouserApiKey;
}

