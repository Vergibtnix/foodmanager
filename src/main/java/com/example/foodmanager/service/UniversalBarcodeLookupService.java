package com.example.foodmanager.service;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.foodmanager.web.BarcodeLookupResult;

@Service
public class UniversalBarcodeLookupService {

    private static final String API_KEY = "DEIN_API_KEY";
    private static final String API_URL = "https://api.barcodelookup.com/v3/products";

    public BarcodeLookupResult lookup(String barcode) {

        try {
            String url = UriComponentsBuilder.fromUri(URI.create(API_URL))
                    .queryParam("barcode", barcode)
                    .queryParam("key", API_KEY)
                    .toUriString();

            RestTemplate rest = new RestTemplate();
            Map<String, Object> response = rest.getForObject(url, Map.class);

            if (response == null || !response.containsKey("products")) {
                return BarcodeLookupResult.notFound(barcode, "Kein Produkt gefunden.");
            }

            List<Map<String, Object>> products =
                    (List<Map<String, Object>>) response.get("products");

            if (products == null || products.isEmpty()) {
                return BarcodeLookupResult.notFound(barcode, "Kein Produkt gefunden.");
            }

            Map<String, Object> p = products.get(0);
            if (p == null) {
                return BarcodeLookupResult.notFound(barcode, "Produktdaten unvollständig.");
            }

            String name = safe(p.get("title"));
            String brand = safe(p.get("brand"));
            String image = safe(p.get("image"));
            String category = safe(p.get("category"));

            if (name.isBlank() && brand.isBlank()) {
                return BarcodeLookupResult.notFound(barcode, "Produkt gefunden, aber keine verwertbaren Daten.");
            }

            return new BarcodeLookupResult(
                    true,
                    barcode,
                    "Produkt gefunden: " + (category.isBlank() ? "Unbekannt" : category),
                    name,
                    brand,
                    image
            );

        } catch (Exception e) {
            return BarcodeLookupResult.notFound(barcode, "Universal-Lookup nicht verfügbar.");
        }
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}

