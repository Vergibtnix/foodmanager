package com.example.foodmanager.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.foodmanager.domain.ProductInfo;
import com.example.foodmanager.repository.ProductInfoRepository;
import com.example.foodmanager.web.BarcodeLookupResult;

/**
 * Produktdaten zu einem Barcode: zuerst aus dem eigenen Speicher, sonst von OpenFoodFacts bzw. dem
 * Universal-Lookup. Gefundene Daten werden gemerkt, damit der nächste Scan sofort und auch offline klappt.
 */
@Service
public class ProductLookupService {

	private final ProductInfoRepository repository;
	private final BarcodeLookupService foodLookup;
	private final UniversalBarcodeLookupService universalLookup;

	public ProductLookupService(
			ProductInfoRepository repository,
			BarcodeLookupService foodLookup,
			UniversalBarcodeLookupService universalLookup
	) {
		this.repository = repository;
		this.foodLookup = foodLookup;
		this.universalLookup = universalLookup;
	}

	public BarcodeLookupResult lookup(String barcode) {
		if (!StringUtils.hasText(barcode)) {
			return BarcodeLookupResult.notFound("", "Bitte gib einen Barcode ein.");
		}

		var cached = repository.findById(barcode);
		if (cached.isPresent() && StringUtils.hasText(cached.get().getName())) {
			ProductInfo info = cached.get();
			return new BarcodeLookupResult(true, barcode, "Produkt bekannt (gespeichert).",
					info.getName(), nullToEmpty(info.getBrand()), nullToEmpty(info.getImageUrl()));
		}

		BarcodeLookupResult result = safeLookup(() -> foodLookup.lookup(barcode), barcode);
		if (!result.found()) {
			result = safeLookup(() -> universalLookup.lookup(barcode), barcode);
		}

		if (result.found()) {
			remember(barcode, result.name(), result.brand(), result.imageUrl());
			return result;
		}
		return BarcodeLookupResult.notFound(barcode, "Kein Produkt gefunden");
	}

	/** Merkt sich Name, Marke und Bild zu einem Barcode, zum Beispiel wenn jemand das Produkt selbst anlegt. */
	public void remember(String barcode, String name, String brand, String imageUrl) {
		if (!StringUtils.hasText(barcode) || !StringUtils.hasText(name)) {
			return;
		}
		String key = barcode.trim();
		if (key.length() > 64) {
			return;
		}
		ProductInfo info = repository.findById(key).orElseGet(() -> new ProductInfo(key));
		info.setName(limit(name, 200));
		info.setBrand(limit(brand, 200));
		info.setImageUrl(limit(imageUrl, 500));
		info.setUpdatedAt(LocalDateTime.now());
		try {
			repository.save(info);
		} catch (RuntimeException e) {
			// Zwei gleichzeitige Scans desselben Produkts: der andere hat schon gespeichert, passt auch
		}
	}

	private BarcodeLookupResult safeLookup(java.util.function.Supplier<BarcodeLookupResult> call, String barcode) {
		try {
			BarcodeLookupResult result = call.get();
			return result != null ? result : BarcodeLookupResult.notFound(barcode, "Kein Produkt gefunden");
		} catch (RuntimeException e) {
			return BarcodeLookupResult.notFound(barcode, "Fehler beim Abrufen der Produktdaten");
		}
	}

	private static String limit(String value, int max) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
