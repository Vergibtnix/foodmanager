package com.example.foodmanager.service;

import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest GS1-Codes (DataMatrix, GS1-128, GS1-QR, Digital Link) aus.
 * Liefert die GTIN und – falls im Code enthalten – das Ablaufdatum.
 * Normale EAN-Barcodes sind kein GS1-Code, dann ist das Ergebnis leer.
 */
public final class Gs1Parser {

	public record Gs1Data(String gtin, LocalDate expiryDate) {
	}

	private static final char GS = '\u001D';

	// Feste Gesamtlänge (AI + Daten) je nach den ersten zwei Ziffern laut GS1-Spezifikation.
	// Alle anderen AIs haben variable Länge und enden mit GS.
	private static final Map<String, Integer> FIXED_LENGTH = new HashMap<>();

	static {
		FIXED_LENGTH.put("00", 20);
		FIXED_LENGTH.put("01", 16);
		FIXED_LENGTH.put("02", 16);
		FIXED_LENGTH.put("03", 16);
		FIXED_LENGTH.put("04", 18);
		for (int i = 11; i <= 19; i++) FIXED_LENGTH.put(String.valueOf(i), 8);
		FIXED_LENGTH.put("20", 4);
		for (int i = 31; i <= 36; i++) FIXED_LENGTH.put(String.valueOf(i), 10);
		FIXED_LENGTH.put("41", 16);
	}

	private static final Pattern SYMBOLOGY_ID = Pattern.compile("^\\][A-Za-z][0-9]");
	private static final Pattern BRACKETED = Pattern.compile("\\((\\d{2,4})\\)([^()]*)");

	private Gs1Parser() {
	}

	public static Optional<Gs1Data> parse(String raw) {
		if (raw == null || raw.isBlank()) return Optional.empty();

		String text = SYMBOLOGY_ID.matcher(raw.trim()).replaceFirst("");

		Map<String, String> ais;
		if (text.startsWith("http://") || text.startsWith("https://")) {
			ais = parseDigitalLink(text);
		} else if (text.startsWith("(")) {
			ais = parseBracketed(text);
		} else {
			ais = parseElementString(text);
		}

		String gtin = ais.get("01");
		if (gtin == null || !gtin.matches("\\d{14}")) return Optional.empty();

		LocalDate expiry = toDate(ais.get("17")); // Verbrauchsdatum
		if (expiry == null) expiry = toDate(ais.get("15")); // Mindesthaltbarkeitsdatum

		return Optional.of(new Gs1Data(normalizeGtin(gtin), expiry));
	}

	// z. B. https://id.gs1.org/01/04012345678901/10/LOT42/17/261231 oder ...?17=261231
	private static Map<String, String> parseDigitalLink(String url) {
		Map<String, String> ais = new HashMap<>();
		try {
			URI uri = URI.create(url);
			String[] parts = uri.getPath() == null ? new String[0] : uri.getPath().split("/");
			// ab "/01/" folgen immer Paare aus AI und Wert
			for (int i = 0; i + 1 < parts.length; i++) {
				if (parts[i].equals("01")) {
					for (int j = i; j + 1 < parts.length; j += 2) {
						ais.put(parts[j], parts[j + 1]);
					}
					break;
				}
			}
			if (uri.getQuery() != null) {
				for (String pair : uri.getQuery().split("&")) {
					String[] kv = pair.split("=", 2);
					if (kv.length == 2) ais.putIfAbsent(kv[0], kv[1]);
				}
			}
		} catch (IllegalArgumentException e) {
			// keine gültige URL
		}
		return ais;
	}

	// z. B. (01)04012345678901(17)261231(10)LOT42
	private static Map<String, String> parseBracketed(String text) {
		Map<String, String> ais = new HashMap<>();
		Matcher m = BRACKETED.matcher(text);
		while (m.find()) {
			ais.put(m.group(1), m.group(2).trim());
		}
		return ais;
	}

	// z. B. 010401234567890117261231 10LOT42 (GS als Trennzeichen nach variablen Feldern)
	private static Map<String, String> parseElementString(String text) {
		Map<String, String> ais = new HashMap<>();
		int pos = 0;
		while (pos < text.length()) {
			if (text.charAt(pos) == GS) {
				pos++;
				continue;
			}
			if (pos + 2 > text.length()) break;

			String prefix = text.substring(pos, pos + 2);
			Integer total = FIXED_LENGTH.get(prefix);

			if (total != null) {
				if (pos + total > text.length()) break;
				int aiLength = prefix.compareTo("31") >= 0 && prefix.compareTo("36") <= 0 ? 4 : prefix.equals("41") ? 3 : 2;
				ais.put(text.substring(pos, pos + aiLength), text.substring(pos + aiLength, pos + total));
				pos += total;
			} else {
				// variables Feld: Inhalt interessiert uns nicht, nur bis zum nächsten GS überspringen
				int next = text.indexOf(GS, pos);
				pos = next < 0 ? text.length() : next + 1;
			}
		}
		return ais;
	}

	// GS1-Datum JJMMTT; Tag 00 bedeutet "letzter Tag des Monats".
	static LocalDate toDate(String value) {
		if (value == null || !value.matches("\\d{6}")) return null;
		try {
			int year = 2000 + Integer.parseInt(value.substring(0, 2));
			int month = Integer.parseInt(value.substring(2, 4));
			int day = Integer.parseInt(value.substring(4, 6));
			if (day == 0) return YearMonth.of(year, month).atEndOfMonth();
			return LocalDate.of(year, month, day);
		} catch (RuntimeException e) {
			return null;
		}
	}

	// GTIN-14 mit führender 0 entspricht der normalen EAN-13 auf der Packung.
	private static String normalizeGtin(String gtin) {
		return gtin.startsWith("0") ? gtin.substring(1) : gtin;
	}
}
