package com.example.foodmanager.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ItemStockTests {

	private static final LocalDate NOV_3 = LocalDate.of(2026, 11, 3);
	private static final LocalDate NOV_10 = LocalDate.of(2026, 11, 10);

	private Item milk(int quantity, LocalDate expiry) {
		Item item = new Item();
		item.setName("Milch");
		item.replaceStock(quantity, expiry);
		return item;
	}

	@Test
	void sameOrNoDateJustAddsUp() {
		Item item = milk(2, NOV_3);
		item.addStock(1, NOV_3);
		item.addStock(1, null);
		assertEquals(4, item.getQuantity());
		assertEquals(NOV_3, item.getExpiryDate());
		assertFalse(item.hasBatches());
	}

	@Test
	void differentDateCreatesBatchesAndEarliestCounts() {
		Item item = milk(2, NOV_10);
		item.addStock(1, NOV_3);

		assertTrue(item.hasBatches());
		assertEquals(3, item.getQuantity());
		assertEquals(NOV_3, item.getExpiryDate());
		assertEquals(NOV_3, item.getBatches().get(0).getExpiryDate());
	}

	@Test
	void consumeTakesEarliestFirstAndCollapsesToSingleDate() {
		Item item = milk(2, NOV_10);
		item.addStock(1, NOV_3);

		assertEquals(1, item.consume(1));
		assertFalse(item.hasBatches());
		assertEquals(2, item.getQuantity());
		assertEquals(NOV_10, item.getExpiryDate());
	}

	@Test
	void consumeAcrossBatchesNeverBelowZero() {
		Item item = milk(1, NOV_10);
		item.addStock(1, NOV_3);

		assertEquals(2, item.consume(5));
		assertEquals(0, item.getQuantity());
	}

	@Test
	void emptyItemTakesDateOfNewStock() {
		Item item = milk(0, null);
		item.addStock(2, NOV_3);
		assertEquals(2, item.getQuantity());
		assertEquals(NOV_3, item.getExpiryDate());
		assertFalse(item.hasBatches());
	}

	@Test
	void restoreBringsBackBatches() {
		Item item = milk(2, NOV_10);
		item.addStock(1, NOV_3);
		var saved = item.batchValues();

		item.consume(3);
		item.restoreStock(3, NOV_3, saved);

		assertEquals(3, item.getQuantity());
		assertEquals(2, item.getBatches().size());
	}
}
