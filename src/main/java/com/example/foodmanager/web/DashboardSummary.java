package com.example.foodmanager.web;

import java.util.List;

import com.example.foodmanager.domain.FoodItem;

public record DashboardSummary(
	long totalItems,
	long expiredItems,
	long expiringSoonItems,
	List<FoodItem> nextExpiringItems
) {
}

