package com.example.foodmanager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.foodmanager.domain.QuantityUnit;
import com.example.foodmanager.service.RecipeService.Amount;

class RecipeServiceAmountTests {

	@Test
	void gramsAndKilograms() {
		assertEquals(new Amount(200, QuantityUnit.GRAMM), RecipeService.parseAmount("200 g Mehl").orElseThrow());
		assertEquals(new Amount(1500, QuantityUnit.GRAMM), RecipeService.parseAmount("1,5 kg Kartoffeln").orElseThrow());
		assertEquals(new Amount(250, QuantityUnit.GRAMM), RecipeService.parseAmount("250g Butter").orElseThrow());
	}

	@Test
	void litersBecomeMilliliters() {
		assertEquals(new Amount(500, QuantityUnit.MILLILITER), RecipeService.parseAmount("½ l Milch").orElseThrow());
		assertEquals(new Amount(200, QuantityUnit.MILLILITER), RecipeService.parseAmount("2 dl Sahne").orElseThrow());
	}

	@Test
	void numberWithoutUnitIsPieces() {
		assertEquals(new Amount(3, QuantityUnit.STUECK), RecipeService.parseAmount("3 Eier").orElseThrow());
		assertEquals(new Amount(2, QuantityUnit.STUECK), RecipeService.parseAmount("2 Stk. Zwiebeln").orElseThrow());
	}

	@Test
	void otherUnitsAndTextAreNotCompared() {
		assertTrue(RecipeService.parseAmount("2 EL Öl").isEmpty());
		assertTrue(RecipeService.parseAmount("1 Prise Salz").isEmpty());
		assertTrue(RecipeService.parseAmount("Salz und Pfeffer").isEmpty());
	}

	@Test
	void fractions() {
		assertEquals(0.5, RecipeService.parseNumber("1/2"));
		assertEquals(1.5, RecipeService.parseNumber("1½"));
		assertEquals(0.25, RecipeService.parseNumber("¼"));
	}

	@Test
	void jsonQuoteEscapes() {
		assertEquals("\"a\\\"b\\nc\"", ExpiryNotificationService.quote("a\"b\nc"));
	}
}
