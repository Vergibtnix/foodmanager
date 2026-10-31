package com.example.foodmanager.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Eine Charge eines Items: eine Teilmenge mit eigenem Ablaufdatum, zum Beispiel "2 Stk. bis 03.11.".
 * Chargen gibt es nur, wenn ein Item mehrere verschiedene Ablaufdaten hat. Sonst stehen Menge und
 * Ablaufdatum wie bisher direkt am Item.
 */
@Entity
@Table(name = "item_batch")
public class ItemBatch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false)
	private Item item;

	@Column(nullable = false)
	private int quantity;

	private LocalDate expiryDate;

	protected ItemBatch() {
	}

	ItemBatch(Item item, int quantity, LocalDate expiryDate) {
		this.item = item;
		this.quantity = quantity;
		this.expiryDate = expiryDate;
	}

	public Long getId() {
		return id;
	}

	public Item getItem() {
		return item;
	}

	public int getQuantity() {
		return quantity;
	}

	void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public LocalDate getExpiryDate() {
		return expiryDate;
	}

	@Transient
	public String getQuantityLabel() {
		return item.getUnit().format(quantity);
	}

	@Transient
	public boolean isExpired() {
		return expiryDate != null && expiryDate.isBefore(LocalDate.now());
	}

	@Transient
	public boolean isExpiringSoon() {
		return expiryDate != null && !isExpired() && !expiryDate.isAfter(LocalDate.now().plusDays(7));
	}
}
