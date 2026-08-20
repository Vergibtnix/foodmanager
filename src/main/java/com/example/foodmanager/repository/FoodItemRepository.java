package com.example.foodmanager.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.foodmanager.domain.FoodItem;

public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {

	List<FoodItem> findAllByOrderByExpiryDateAscCreatedAtDesc();

	List<FoodItem> findTop5ByOrderByExpiryDateAscCreatedAtDesc();

	long countByExpiryDateBefore(LocalDate date);

	long countByExpiryDateBetween(LocalDate start, LocalDate end);

	@Query("""
		SELECT f
		FROM FoodItem f
		WHERE LOWER(f.name) LIKE LOWER(CONCAT('%', :query, '%'))
			OR LOWER(COALESCE(f.brand, '')) LIKE LOWER(CONCAT('%', :query, '%'))
			OR LOWER(COALESCE(f.barcode, '')) LIKE LOWER(CONCAT('%', :query, '%'))
			OR LOWER(COALESCE(f.storageLocation, '')) LIKE LOWER(CONCAT('%', :query, '%'))
		ORDER BY f.expiryDate ASC, f.createdAt DESC
		""")
	List<FoodItem> search(@Param("query") String query);
}

