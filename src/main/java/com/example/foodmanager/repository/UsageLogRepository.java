package com.example.foodmanager.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.foodmanager.domain.UsageLog;

public interface UsageLogRepository extends JpaRepository<UsageLog, Long> {

    List<UsageLog> findByCreatedAtAfter(LocalDateTime since);
}