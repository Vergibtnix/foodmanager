package com.example.foodmanager.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.foodmanager.domain.Item;

import jakarta.persistence.LockModeType;

public interface ItemRepository extends JpaRepository<Item, Long> {

	@Query("""
        SELECT i
        FROM Item i
        WHERE LOWER(i.name) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(COALESCE(i.brand, '')) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(COALESCE(i.barcode, '')) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(COALESCE(i.storageLocation, '')) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(COALESCE(i.category, '')) LIKE LOWER(CONCAT('%', :query, '%'))
        ORDER BY i.createdAt DESC
        """)
	List<Item> search(@Param("query") String query);

	List<Item> findAllByOrderByCreatedAtDesc();

	/** Sperrt die Zeile bis zum Ende der Transaktion, damit gleichzeitige Mengenänderungen nacheinander laufen. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT i FROM Item i WHERE i.id = :id")
	Optional<Item> findByIdForUpdate(@Param("id") Long id);
}
