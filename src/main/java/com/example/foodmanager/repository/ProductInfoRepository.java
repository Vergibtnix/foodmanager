package com.example.foodmanager.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.foodmanager.domain.ProductInfo;

public interface ProductInfoRepository extends JpaRepository<ProductInfo, String> {
}
