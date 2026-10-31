package com.example.foodmanager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class Gs1ParserTests {

	@Test
	void normalEanIsNoGs1Code() {
		assertTrue(Gs1Parser.parse("4012345678901").isEmpty());
		assertTrue(Gs1Parser.parse("").isEmpty());
		assertTrue(Gs1Parser.parse(null).isEmpty());
	}

	@Test
	void dataMatrixWithExpiry() {
		var data = Gs1Parser.parse("]d20104012345678901172612311042LOT\u001D21ABC").orElseThrow();
		assertEquals("4012345678901", data.gtin());
		assertEquals(LocalDate.of(2026, 12, 31), data.expiryDate());
	}

	@Test
	void variableFieldBeforeExpiry() {
		var data = Gs1Parser.parse("\u001D010401234567890110LOT42\u001D15270300").orElseThrow();
		assertEquals("4012345678901", data.gtin());
		assertEquals(LocalDate.of(2027, 3, 31), data.expiryDate()); // Tag 00 = Monatsende
	}

	@Test
	void gs1CodeWithoutExpiryStillReturnsProduct() {
		var data = Gs1Parser.parse("010401234567890110LOT42").orElseThrow();
		assertEquals("4012345678901", data.gtin());
		assertNull(data.expiryDate());
	}

	@Test
	void bracketedForm() {
		var data = Gs1Parser.parse("(01)04012345678901(17)261115").orElseThrow();
		assertEquals(LocalDate.of(2026, 11, 15), data.expiryDate());
	}

	@Test
	void digitalLink() {
		var data = Gs1Parser.parse("https://id.gs1.org/01/04012345678901/10/LOT42?17=261231").orElseThrow();
		assertEquals("4012345678901", data.gtin());
		assertEquals(LocalDate.of(2026, 12, 31), data.expiryDate());

		var path = Gs1Parser.parse("https://example.com/01/04012345678901/17/261231").orElseThrow();
		assertEquals(LocalDate.of(2026, 12, 31), path.expiryDate());
	}

	@Test
	void invalidDateIsIgnored() {
		var data = Gs1Parser.parse("010401234567890117261301").orElseThrow();
		assertEquals("4012345678901", data.gtin());
		assertNull(data.expiryDate());
	}
}
