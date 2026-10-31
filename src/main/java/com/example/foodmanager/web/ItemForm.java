package com.example.foodmanager.web;

import java.time.LocalDate;

import com.example.foodmanager.domain.Item;
import com.example.foodmanager.domain.QuantityUnit;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ItemForm {

	// Pflichtfelder
	@NotBlank(message = "Bitte einen Namen eingeben.")
	private String name;

	private String brand;

	private String barcode;

	private LocalDate expiryDate;

	@NotNull(message = "Bitte eine Menge eingeben.")
	@Min(value = 1, message = "Menge muss mindestens 1 sein.")
	private Integer quantity;

	private QuantityUnit unit = QuantityUnit.STUECK;

	@NotBlank(message = "Bitte einen Lagerort wählen!")
	private String storageLocation;

	@NotBlank(message = "Bitte eine Kategorie eingeben!")
	private String category;

	private String notes;

	private String externalImageUrl;

	// neue Felder
	private String condition;

	private LocalDate purchaseDate;

	private Integer minQuantity;

	/** Stand des Items beim Öffnen des Formulars, zum Erkennen gleichzeitiger Änderungen. */
	private Long version;

	/** Kommt das Formular aus der Einräum-Liste (Serien-Scan), wird der Eintrag nach dem Speichern entfernt. */
	private Long pendingScanId;

	// ------------------------------------------------------------
	// GETTER / SETTER
	// ------------------------------------------------------------

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

	public Long getPendingScanId() {
		return pendingScanId;
	}

	public void setPendingScanId(Long pendingScanId) {
		this.pendingScanId = pendingScanId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name != null ? name.trim() : "";
	}

	public String getBrand() {
		return brand;
	}

	public void setBrand(String brand) {
		this.brand = brand != null ? brand.trim() : "";
	}

	public String getBarcode() {
		return barcode;
	}

	public void setBarcode(String barcode) {
		this.barcode = barcode != null ? barcode.trim() : "";
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

	public QuantityUnit getUnit() {
		return unit;
	}

	public void setUnit(QuantityUnit unit) {
		this.unit = unit != null ? unit : QuantityUnit.STUECK;
	}

	public String getStorageLocation() {
		return storageLocation;
	}

	public void setStorageLocation(String storageLocation) {
		this.storageLocation = storageLocation != null ? storageLocation.trim() : "";
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		// Fallback: Wenn Universal‑API "Produkt gefunden: XYZ" liefert
		if (category != null && category.startsWith("Produkt gefunden: ")) {
			this.category = category.replace("Produkt gefunden: ", "").trim();
		} else {
			this.category = category != null ? category.trim() : "";
		}
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes != null ? notes.trim() : "";
	}

	public String getExternalImageUrl() {
		return externalImageUrl;
	}

	public void setExternalImageUrl(String externalImageUrl) {
		this.externalImageUrl = externalImageUrl != null ? externalImageUrl.trim() : "";
	}

	public String getCondition() {
		return condition;
	}

	public void setCondition(String condition) {
		this.condition = condition != null ? condition.trim() : "";
	}

	public LocalDate getPurchaseDate() {
		return purchaseDate;
	}

	public void setPurchaseDate(LocalDate purchaseDate) {
		this.purchaseDate = purchaseDate;
	}

	public Integer getMinQuantity() {
		return minQuantity;
	}

	public void setMinQuantity(Integer minQuantity) {
		this.minQuantity = minQuantity;
	}

	// ------------------------------------------------------------
	// FROM ENTITY
	// ------------------------------------------------------------

	public static ItemForm fromEntity(Item item) {
		ItemForm form = new ItemForm();

		form.setName(item.getName());
		form.setBrand(item.getBrand());
		form.setBarcode(item.getBarcode());
		form.setExpiryDate(item.getExpiryDate());
		form.setQuantity(item.getQuantity());
		form.setUnit(item.getUnit());
		form.setStorageLocation(item.getStorageLocation());
		form.setCategory(item.getCategory());
		form.setNotes(item.getNotes());
		form.setExternalImageUrl(item.getExternalImageUrl());

		form.setCondition(item.getCondition());
		form.setPurchaseDate(item.getPurchaseDate());
		form.setMinQuantity(item.getMinQuantity());
		form.setVersion(item.getVersion());

		return form;
	}

	// ------------------------------------------------------------
	// OPTIONAL: TYPE alias (falls du später "Art" statt Kategorie nutzt)
	// ------------------------------------------------------------
	public String getType() {
		return category;
	}
}
