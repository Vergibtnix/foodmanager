package com.example.foodmanager.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Einheit der Menge eines Items. Gerechnet wird immer in der kleinsten Einheit (g, ml, Stück),
 * damit die Menge eine ganze Zahl bleibt. "1 kg" wird also als 1000 g gespeichert.
 */
public enum QuantityUnit {

	STUECK("Stück", "Stk.", 1),
	GRAMM("Gramm", "g", 100),
	MILLILITER("Milliliter", "ml", 100);

	private final String label;
	private final String symbol;
	private final int step;

	QuantityUnit(String label, String symbol, int step) {
		this.label = label;
		this.symbol = symbol;
		this.step = step;
	}

	public String getLabel() {
		return label;
	}

	public String getSymbol() {
		return symbol;
	}

	/** Schrittweite für "Verbraucht" und "+": 1 Stück, 100 g oder 100 ml. */
	public int getStep() {
		return step;
	}

	public String format(int amount) {
		return amount + " " + symbol;
	}

	/** Einheit in einer Rezeptzeile und der Faktor zur Basiseinheit, zum Beispiel "kg" = 1000 g. */
	public record Parsed(QuantityUnit unit, double factor) {
	}

	public static Optional<Parsed> parse(String text) {
		String u = text == null ? "" : text.trim().toLowerCase(Locale.GERMAN).replace(".", "");
		return Optional.ofNullable(switch (u) {
			case "", "stk", "stück", "stücke", "st" -> new Parsed(STUECK, 1);
			case "g", "gr", "gramm" -> new Parsed(GRAMM, 1);
			case "kg" -> new Parsed(GRAMM, 1000);
			case "mg" -> new Parsed(GRAMM, 0.001);
			case "ml" -> new Parsed(MILLILITER, 1);
			case "cl" -> new Parsed(MILLILITER, 10);
			case "dl" -> new Parsed(MILLILITER, 100);
			case "l", "liter" -> new Parsed(MILLILITER, 1000);
			default -> null;
		});
	}
}
