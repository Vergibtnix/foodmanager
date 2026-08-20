package com.example.foodmanager.web;

public record BarcodeDecodeResult(
	boolean detected,
	String barcode,
	String message
) {

	public static BarcodeDecodeResult notDetected(String message) {
		return new BarcodeDecodeResult(false, "", message);
	}
}
