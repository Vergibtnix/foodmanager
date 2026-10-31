package com.example.foodmanager.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Gemerkte Produktdaten zu einem Barcode. Kommt aus OpenFoodFacts oder aus einem selbst angelegten Item.
 * Damit klappt ein zweiter Scan sofort, auch wenn der externe Dienst langsam oder nicht erreichbar ist.
 */
@Entity
@Table(name = "product_info")
public class ProductInfo {

	@Id
	@Column(length = 64)
	private String barcode;

	@Column(length = 200)
	private String name;

	@Column(length = 200)
	private String brand;

	@Column(length = 500)
	private String imageUrl;

	@Column(nullable = false)
	private LocalDateTime updatedAt = LocalDateTime.now();

	protected ProductInfo() {
	}

	public ProductInfo(String barcode) {
		this.barcode = barcode;
	}

	public String getBarcode() {
		return barcode;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getBrand() {
		return brand;
	}

	public void setBrand(String brand) {
		this.brand = brand;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public void setImageUrl(String imageUrl) {
		this.imageUrl = imageUrl;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
}
