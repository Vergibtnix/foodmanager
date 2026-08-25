package com.example.foodmanager.domain;

public enum ProductCategory {

	GETRAENK("Getraenk", "drink"),
	ESSEN("Essen", "meal"),
	KOCHEN("Kochen & Backen", "cook"),
	SNACKS("Snacks", "snack"),
	KONSERVEN("Konserven", "can"),
	MILCHPRODUKTE("Milchprodukte", "dairy"),
	GEWUERZE("Gewuerze", "spice"),
	SONSTIGES("Sonstiges", "misc");

	private final String label;
	private final String cssClass;

	ProductCategory(String label, String cssClass) {
		this.label = label;
		this.cssClass = cssClass;
	}

	public String getLabel() {
		return label;
	}

	public String getCssClass() {
		return cssClass;
	}
}

