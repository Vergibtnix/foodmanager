package com.example.foodmanager.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Beim Serien-Scan erkanntes Produkt, das noch nicht im Inventar ist.
 * Wird später auf der Seite "Noch einzuräumen" mit Lagerort und Ablaufdatum angelegt.
 */
@Entity
@Table(name = "pending_scan")
public class PendingScan {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 64)
	private String barcode;

	@Column(length = 200)
	private String name;

	@Column(length = 200)
	private String brand;

	@Column(length = 500)
	private String imageUrl;

	private LocalDate expiryDate;

	/** Wie oft dasselbe Produkt gescannt wurde, wird beim Anlegen die Menge. */
	@Column(name = "scan_count", nullable = false)
	private int count = 1;

	@Column(nullable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	public Long getId() {
		return id;
	}

	public String getBarcode() {
		return barcode;
	}

	public void setBarcode(String barcode) {
		this.barcode = barcode;
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

	public LocalDate getExpiryDate() {
		return expiryDate;
	}

	public void setExpiryDate(LocalDate expiryDate) {
		this.expiryDate = expiryDate;
	}

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
