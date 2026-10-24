package com.example.foodmanager.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.foodmanager.domain.ShoppingEntry;

public interface ShoppingEntryRepository extends JpaRepository<ShoppingEntry, Long> {

    List<ShoppingEntry> findAllByOrderByDoneAscCreatedAtAsc();

    List<ShoppingEntry> findByDoneTrue();
}