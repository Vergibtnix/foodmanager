package com.example.foodmanager.domain;

public enum ProductCategory {

	GETRAENK("Getraenk"),
	ESSEN("Essen"),
	KOCHEN("Kochen & Backen"),
	SNACKS("Snacks"),
	KONSERVEN("Konserven"),
	MILCHPRODUKTE("Milchprodukte"),
	GEWUERZE("Gewuerze"),
	SONSTIGES("Sonstiges");

	private final String label;

	ProductCategory(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}

