package com.example.foodmanager.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.example.foodmanager.domain.FoodItem;
import com.example.foodmanager.domain.ProductCategory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class FoodItemForm {

	@NotBlank(message = "Bitte gib einen Namen ein.")
	@Size(max = 120, message = "Der Name darf maximal 120 Zeichen lang sein.")
	private String name;

	@Size(max = 120, message = "Die Marke darf maximal 120 Zeichen lang sein.")
	private String brand;

	@Size(max = 64, message = "Der Barcode darf maximal 64 Zeichen lang sein.")
	private String barcode;

	@NotNull(message = "Bitte wähle ein Ablaufdatum.")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate expiryDate;

	@NotNull(message = "Bitte gib eine Menge an.")
	@Min(value = 1, message = "Die Menge muss mindestens 1 sein.")
	private Integer quantity = 1;

	@NotBlank(message = "Bitte gib einen Lagerort an.")
	@Size(max = 120, message = "Der Lagerort darf maximal 120 Zeichen lang sein.")
	private String storageLocation = "Kühlschrank";

	@NotNull(message = "Bitte waehle eine Produktart.")
	private ProductCategory category = ProductCategory.SONSTIGES;

	@Size(max = 500, message = "Die Notizen dürfen maximal 500 Zeichen lang sein.")
	private String notes;

	@Size(max = 500, message = "Die Bild-URL darf maximal 500 Zeichen lang sein.")
	private String externalImageUrl;

	public static FoodItemForm fromEntity(FoodItem item) {
		FoodItemForm form = new FoodItemForm();
		form.setName(item.getName());
		form.setBrand(item.getBrand());
		form.setBarcode(item.getBarcode());
		form.setExpiryDate(item.getExpiryDate());
		form.setQuantity(item.getQuantity());
		form.setStorageLocation(item.getStorageLocation());
		form.setCategory(item.getCategory());
		form.setNotes(item.getNotes());
		form.setExternalImageUrl(item.getExternalImageUrl());
		return form;
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
		return category;
	}

	public void setCategory(ProductCategory category) {
		this.category = category;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public String getExternalImageUrl() {
		return externalImageUrl;
	}

	public void setExternalImageUrl(String externalImageUrl) {
		this.externalImageUrl = externalImageUrl;
	}
}

