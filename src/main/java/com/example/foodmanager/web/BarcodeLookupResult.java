package com.example.foodmanager.web;

public record BarcodeLookupResult(
	boolean found,
	String barcode,
	String name,
	String brand,
	String imageUrl,
	String message
) {

	public static BarcodeLookupResult notFound(String barcode, String message) {
		return new BarcodeLookupResult(false, barcode, "", "", "", message);
	}
}

