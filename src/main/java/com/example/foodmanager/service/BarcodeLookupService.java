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
		String barcode = normalizeBarcode(rawBarcode);
		if (!StringUtils.hasText(barcode)) {
			return BarcodeLookupResult.notFound("", "Bitte gib einen Barcode ein.");
		}

		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> response = restClient.get()
				.uri(uriBuilder -> uriBuilder.path("/api/v2/product/{barcode}.json").build(barcode))
				.retrieve()
				.body(Map.class);

			if (response == null || !Integer.valueOf(1).equals(response.get("status"))) {
				return BarcodeLookupResult.notFound(barcode, "Kein Produkt zu diesem Barcode gefunden.");
			}

			@SuppressWarnings("unchecked")
			Map<String, Object> product = (Map<String, Object>) response.get("product");
			if (product == null) {
				return BarcodeLookupResult.notFound(barcode, "Produktdaten konnten nicht gelesen werden.");
			}

			String name = firstNonBlank(product.get("product_name_de"), product.get("product_name"), product.get("generic_name"));
			String brand = firstNonBlank(product.get("brands"), product.get("brands_imported"));
			String imageUrl = firstNonBlank(product.get("image_front_url"), product.get("image_url"));

			if (!StringUtils.hasText(name) && !StringUtils.hasText(brand)) {
				return BarcodeLookupResult.notFound(barcode, "Barcode erkannt, aber keine verwertbaren Produktdaten gefunden.");
			}

			return new BarcodeLookupResult(true, barcode, nullToEmpty(name), nullToEmpty(brand), nullToEmpty(imageUrl), "Produkt gefunden.");
		}
		catch (Exception ex) {
			return BarcodeLookupResult.notFound(barcode, "Externer Lookup aktuell nicht verfügbar. Bitte Daten manuell ergänzen.");
		}
	}

	private String normalizeBarcode(String barcode) {
		return barcode == null ? "" : barcode.replaceAll("\\s+", "").trim();
	}

	private String firstNonBlank(Object... values) {
		for (Object value : values) {
			if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
				return stringValue.trim();
			}
		}
		return "";
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}

