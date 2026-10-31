package com.example.foodmanager.web;

import java.util.List;

import com.example.foodmanager.domain.Item;

public class DashboardSummary {

	private long totalItems;

	private long itemsBelowMinQuantity;

	private long itemsDefective;

	private List<Item> latestItems;

	public DashboardSummary(
			long totalItems,
			long itemsBelowMinQuantity,
			long itemsDefective,
			List<Item> latestItems
	) {
		this.totalItems = totalItems;
		this.itemsBelowMinQuantity = itemsBelowMinQuantity;
		this.itemsDefective = itemsDefective;
		this.latestItems = latestItems;
	}

	public long getTotalItems() {
		return totalItems;
	}

	public long getItemsBelowMinQuantity() {
		return itemsBelowMinQuantity;
	}

	public long getItemsDefective() {
		return itemsDefective;
	}

	public List<Item> getLatestItems() {
		return latestItems;
	}
}
