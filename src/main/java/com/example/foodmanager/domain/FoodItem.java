package com.example.foodmanager.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "food_items")
public class FoodItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(length = 120)
	private String brand;

	@Column(length = 64)
	private String barcode;

	@Column(nullable = false)
	private LocalDate expiryDate;

	@Column(nullable = false)
	private Integer quantity = 1;

	@Column(nullable = false, length = 120)
	private String storageLocation = "Eiskasten";

	@Enumerated(EnumType.STRING)
	@Column(length = 40)
	private ProductCategory category = ProductCategory.SONSTIGES;

	@Column(length = 500)
	private String notes;

	@Column(length = 255)
	private String imageFilename;

	@Column(length = 500)
	private String externalImageUrl;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
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

	public String getBarcode() {
		return barcode;
	}

	public void setBarcode(String barcode) {
		this.barcode = barcode;
	}

	public LocalDate getExpiryDate() {
		return expiryDate;
	}

	public void setExpiryDate(LocalDate expiryDate) {
		this.expiryDate = expiryDate;
	}

	public Integer getQuantity() {
		return quantity;
	}

	public void setQuantity(Integer quantity) {
		this.quantity = quantity;
	}

	public String getStorageLocation() {
		return storageLocation;
	}

	public void setStorageLocation(String storageLocation) {
		this.storageLocation = storageLocation;
	}

	public ProductCategory getCategory() {
		return category == null ? ProductCategory.SONSTIGES : category;
	}

	public void setCategory(ProductCategory category) {
		this.category = category == null ? ProductCategory.SONSTIGES : category;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public String getImageFilename() {
		return imageFilename;
	}

	public void setImageFilename(String imageFilename) {
		this.imageFilename = imageFilename;
	}

	public String getExternalImageUrl() {
		return externalImageUrl;
	}

	public void setExternalImageUrl(String externalImageUrl) {
		this.externalImageUrl = externalImageUrl;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	@Transient
	public boolean isExpired() {
		return expiryDate != null && expiryDate.isBefore(LocalDate.now());
	}

	@Transient
	public boolean isExpiringSoon() {
		return expiryDate != null
			&& !isExpired()
			&& !expiryDate.isAfter(LocalDate.now().plusDays(7));
	}

	@Transient
	public long getDaysUntilExpiry() {
		if (expiryDate == null) {
			return Long.MAX_VALUE;
		}
		return ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
	}

	@Transient
	public String getDisplayImageUrl() {
		if (imageFilename != null && !imageFilename.isBlank()) {
			return "/images/" + imageFilename;
		}
		return externalImageUrl;
	}
}

