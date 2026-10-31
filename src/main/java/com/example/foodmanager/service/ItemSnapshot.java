package com.example.foodmanager.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

import com.example.foodmanager.domain.Item;
import com.example.foodmanager.domain.QuantityUnit;

/** Alle Werte eines Items zu einem Zeitpunkt, ohne Bezug zur Datenbank. Grundlage für "Rückgängig". */
public record ItemSnapshot(
		String name,
		String brand,
		String barcode,
		LocalDate expiryDate,
		Integer quantity,
		QuantityUnit unit,
		String storageLocation,
		String category,
		String notes,
		String imageFilename,
		String externalImageUrl,
		LocalDate purchaseDate,
		String condition,
		Integer minQuantity,
		List<Item.BatchValue> batches
) implements Serializable {

	public static ItemSnapshot of(Item item) {
		return new ItemSnapshot(
				item.getName(), item.getBrand(), item.getBarcode(), item.getExpiryDate(), item.getQuantity(),
				item.getUnit(), item.getStorageLocation(), item.getCategory(), item.getNotes(),
				item.getImageFilename(), item.getExternalImageUrl(), item.getPurchaseDate(), item.getCondition(),
				item.getMinQuantity(), item.batchValues());
	}

	/** Neues Item mit diesen Werten (nach dem Löschen). */
	public Item toNewItem() {
		Item item = new Item();
		item.setName(name);
		item.setBrand(brand);
		item.setBarcode(barcode);
		item.setUnit(unit);
		item.setStorageLocation(storageLocation);
		item.setCategory(category);
		item.setNotes(notes);
		item.setImageFilename(imageFilename);
		item.setExternalImageUrl(externalImageUrl);
		item.setPurchaseDate(purchaseDate);
		item.setCondition(condition);
		item.setMinQuantity(minQuantity);
		item.restoreStock(quantity, expiryDate, batches);
		return item;
	}
}
