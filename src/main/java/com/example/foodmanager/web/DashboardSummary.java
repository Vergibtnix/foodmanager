package com.example.foodmanager.web;

import java.util.List;

import com.example.foodmanager.domain.Item;

public class DashboardSummary {

	private long totalItems;

	private long itemsBelowMinQuantity;

	private long itemsWarrantyExpiringSoon;

	private long itemsDefective;

	private List<Item> latestItems;

	public DashboardSummary(
			long totalItems,
			long itemsBelowMinQuantity,
			long itemsWarrantyExpiringSoon,
			long itemsDefective,
			List<Item> latestItems
	) {
		this.totalItems = totalItems;
		this.itemsBelowMinQuantity = itemsBelowMinQuantity;
		this.itemsWarrantyExpiringSoon = itemsWarrantyExpiringSoon;
		this.itemsDefective = itemsDefective;
		this.latestItems = latestItems;
	}

	public long getTotalItems() {
		return totalItems;
	}

	public long getItemsBelowMinQuantity() {
		return itemsBelowMinQuantity;
	}

	public long getItemsWarrantyExpiringSoon() {
		return itemsWarrantyExpiringSoon;
	}

	public long getItemsDefective() {
		return itemsDefective;
	}

	public List<Item> getLatestItems() {
		return latestItems;
	}
}
