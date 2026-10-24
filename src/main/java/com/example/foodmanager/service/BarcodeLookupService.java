package com.example.foodmanager.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.example.foodmanager.web.BarcodeLookupResult;

@Service
public class BarcodeLookupService {

	private final RestClient restClient;

	public BarcodeLookupService() {
		this.restClient = RestClient.builder()
				.baseUrl("https://world.openfoodfacts.org")
				.build();
	}

	public BarcodeLookupResult lookup(String rawBarcode) {

		String barcode = normalize(rawBarcode);
		if (barcode.isEmpty()) {
			return BarcodeLookupResult.notFound("", "Bitte gib einen Barcode ein.");
		}

		try {
			Map<String, Object> response = restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/api/v2/product/{barcode}.json").build(barcode))
					.retrieve()
					.body(Map.class);

			if (response == null) {
				return BarcodeLookupResult.notFound(barcode, "Keine Antwort von OpenFoodFacts.");
			}

			Object statusObj = response.get("status");
			if (!(statusObj instanceof Number) || ((Number) statusObj).intValue() != 1) {
				return BarcodeLookupResult.notFound(barcode, "Kein Produkt zu diesem Barcode gefunden.");
			}

			Map<String, Object> product = safeMap(response.get("product"));
			if (product.isEmpty()) {
				return BarcodeLookupResult.notFound(barcode, "Produktdaten konnten nicht gelesen werden.");
			}

			String name = firstNonBlank(
					product.get("product_name_de"),
					product.get("product_name"),
					product.get("generic_name")
			);

			String brand = firstNonBlank(
					product.get("brands"),
					product.get("brands_imported")
			);

			String imageUrl = firstNonBlank(
					product.get("image_front_url"),
					product.get("image_url")
			);

			if (name.isBlank() && brand.isBlank()) {
				return BarcodeLookupResult.notFound(barcode, "Barcode erkannt, aber keine verwertbaren Produktdaten gefunden.");
			}

			return new BarcodeLookupResult(
					true,
					barcode,
					"Produkt gefunden.",
					name,
					brand,
					imageUrl
			);

		} catch (Exception ex) {
			return BarcodeLookupResult.notFound(barcode, "Externer Lookup aktuell nicht verfügbar.");
		}
	}

	private String normalize(String barcode) {
		return barcode == null ? "" : barcode.replaceAll("[^0-9]", "").trim();
	}

	private Map<String, Object> safeMap(Object obj) {
		return (obj instanceof Map<?, ?> m) ? (Map<String, Object>) m : Map.of();
	}

	private String firstNonBlank(Object... values) {
		for (Object v : values) {
			if (v instanceof String s && !s.isBlank()) {
				return s.trim();
			}
		}
		return "";
	}
}


