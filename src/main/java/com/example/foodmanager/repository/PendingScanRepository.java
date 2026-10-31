package com.example.foodmanager.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.foodmanager.domain.PendingScan;

public interface PendingScanRepository extends JpaRepository<PendingScan, Long> {

	List<PendingScan> findAllByOrderByCreatedAtAsc();

	Optional<PendingScan> findFirstByBarcode(String barcode);
}
