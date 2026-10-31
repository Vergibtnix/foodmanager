package com.example.foodmanager.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

@Entity
@Table(name = "items")
public class Item {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(length = 120)
	private String brand;

	@Column(length = 64)
	private String barcode;

	@Column(nullable = true)
	private LocalDate expiryDate;

	@Column(nullable = false)
	private Integer quantity = 1;

	/** Darf leer sein, damit ältere Datensätze ohne Einheit weiter funktionieren. Leer heißt Stück. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = true, length = 20)
	private QuantityUnit unit;

	@Column(nullable = true, length = 120)
	private String storageLocation;

	@Column(nullable = false, length = 120)
	private String category;

	@Column(length = 500)
	private String notes;

	@Column(length = 255)
	private String imageFilename;

	@Column(length = 500)
	private String externalImageUrl;

	private LocalDate purchaseDate;

	@Column(length = 40)
	private String condition;

	private Integer minQuantity;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	/** Zählt jede Änderung mit. So merkt die App, wenn zwei Personen dasselbe Item gleichzeitig bearbeiten. */
	@Version
	@Column(nullable = false, columnDefinition = "bigint default 0")
	private long version;

	/** Nur gefüllt, wenn es mehrere verschiedene Ablaufdaten gibt. Dann sind Menge und Ablaufdatum oben daraus berechnet. */
	@OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
	@BatchSize(size = 50)
	private List<ItemBatch> batches = new ArrayList<>();

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

	public QuantityUnit getUnit() {
		return unit != null ? unit : QuantityUnit.STUECK;
	}

	public void setUnit(QuantityUnit unit) {
		this.unit = unit;
	}

	/** Menge mit Einheit, zum Beispiel "500 g" oder "3 Stk.". */
	@Transient
	public String getQuantityLabel() {
		return getUnit().format(quantity != null ? quantity : 0);
	}

	/** Ein Schritt für "Verbraucht" und "+", zum Beispiel "1 Stk." oder "100 g". */
	@Transient
	public String getStepLabel() {
		return getUnit().format(getUnit().getStep());
	}

	@Transient
	public String getMinQuantityLabel() {
		return minQuantity != null ? getUnit().format(minQuantity) : null;
	}

	public String getStorageLocation() {
		return storageLocation;
	}

	public void setStorageLocation(String storageLocation) {
		this.storageLocation = storageLocation;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
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

	public LocalDate getPurchaseDate() {
		return purchaseDate;
	}

	public void setPurchaseDate(LocalDate purchaseDate) {
		this.purchaseDate = purchaseDate;
	}

	public String getCondition() {
		return condition;
	}

	public void setCondition(String condition) {
		this.condition = condition;
	}

	public Integer getMinQuantity() {
		return minQuantity;
	}

	public void setMinQuantity(Integer minQuantity) {
		this.minQuantity = minQuantity;
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

	public long getVersion() {
		return version;
	}

	// ------------------------------------------------------------
	// Bestand mit Chargen
	// ------------------------------------------------------------

	/** Chargen, die früheste zuerst, ohne Datum zuletzt. Leer, wenn es nur ein Ablaufdatum gibt. */
	public List<ItemBatch> getBatches() {
		List<ItemBatch> sorted = new ArrayList<>(batches);
		sorted.sort(BY_EXPIRY);
		return sorted;
	}

	@Transient
	public boolean hasBatches() {
		return !batches.isEmpty();
	}

	private static final Comparator<ItemBatch> BY_EXPIRY =
			Comparator.comparing(ItemBatch::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder()));

	/**
	 * Menge dazugeben, zum Beispiel nach dem Einkaufen. Hat die neue Ware ein anderes Ablaufdatum als
	 * die vorhandene, entstehen getrennte Chargen. Ohne Datum wird einfach dazugezählt.
	 */
	public void addStock(int amount, LocalDate expiry) {
		if (amount <= 0) {
			return;
		}
		int current = quantity != null ? quantity : 0;

		if (batches.isEmpty()) {
			if (expiry == null || current == 0 || expiry.equals(expiryDate)) {
				quantity = current + amount;
				if (current == 0 && expiry != null) {
					expiryDate = expiry;
				}
				return;
			}
			// Bisher ein Datum, jetzt kommt ein zweites dazu: vorhandene Menge wird zur ersten Charge
			batches.add(new ItemBatch(this, current, expiryDate));
		}

		batches.stream()
				.filter(b -> Objects.equals(b.getExpiryDate(), expiry))
				.findFirst()
				.ifPresentOrElse(
						b -> b.setQuantity(b.getQuantity() + amount),
						() -> batches.add(new ItemBatch(this, amount, expiry)));
		normalizeBatches();
	}

	/** Menge verbrauchen, immer zuerst von der Charge, die am frühesten abläuft. Liefert die wirklich verbrauchte Menge. */
	public int consume(int amount) {
		if (amount <= 0) {
			return 0;
		}
		int current = quantity != null ? quantity : 0;

		if (batches.isEmpty()) {
			int taken = Math.min(current, amount);
			quantity = current - taken;
			return taken;
		}

		int remaining = amount;
		for (ItemBatch batch : getBatches()) {
			int taken = Math.min(batch.getQuantity(), remaining);
			batch.setQuantity(batch.getQuantity() - taken);
			remaining -= taken;
			if (remaining == 0) {
				break;
			}
		}
		normalizeBatches();
		return amount - remaining;
	}

	/** Menge einer bestimmten Charge verbrauchen (oder mit amount = Menge der Charge ganz entfernen). */
	public void consumeBatch(Long batchId, int amount) {
		batches.stream()
				.filter(b -> b.getId() != null && b.getId().equals(batchId))
				.findFirst()
				.ifPresent(b -> b.setQuantity(Math.max(0, b.getQuantity() - amount)));
		normalizeBatches();
	}

	/** Menge und Ablaufdatum direkt setzen (Formular). Vorhandene Chargen werden zu einer zusammengefasst. */
	public void replaceStock(Integer newQuantity, LocalDate newExpiry) {
		batches.clear();
		quantity = newQuantity;
		expiryDate = newExpiry;
	}

	/** Leere Chargen weg. Bleibt höchstens eine übrig, braucht es keine Chargen mehr. Sonst Menge und Datum neu berechnen. */
	private void normalizeBatches() {
		batches.removeIf(b -> b.getQuantity() <= 0);

		if (batches.size() <= 1) {
			if (batches.size() == 1) {
				ItemBatch only = batches.get(0);
				quantity = only.getQuantity();
				expiryDate = only.getExpiryDate();
			} else {
				quantity = 0;
			}
			batches.clear();
			return;
		}

		quantity = batches.stream().mapToInt(ItemBatch::getQuantity).sum();
		expiryDate = batches.stream()
				.map(ItemBatch::getExpiryDate)
				.filter(Objects::nonNull)
				.min(Comparator.naturalOrder())
				.orElse(null);
	}

	// ------------------------------------------------------------
	// Zustand merken und wiederherstellen (für "Rückgängig")
	// ------------------------------------------------------------

	public record BatchValue(int quantity, LocalDate expiryDate) implements Serializable {
	}

	/** Bestand als einfache Werte, ohne Bezug zur Datenbank. */
	public List<BatchValue> batchValues() {
		return new ArrayList<>(getBatches().stream().map(b -> new BatchValue(b.getQuantity(), b.getExpiryDate())).toList());
	}

	public void restoreStock(Integer savedQuantity, LocalDate savedExpiry, List<BatchValue> savedBatches) {
		batches.clear();
		savedBatches.forEach(b -> batches.add(new ItemBatch(this, b.quantity(), b.expiryDate())));
		quantity = savedQuantity;
		expiryDate = savedExpiry;
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

	public String getType() {
        return "";
    }

	public void setType(String s) {
	}
}
