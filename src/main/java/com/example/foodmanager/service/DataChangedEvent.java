package com.example.foodmanager.service;

/**
 * Etwas wurde geändert. Wird nach dem Speichern an alle offenen Seiten geschickt (LiveUpdateService).
 *
 * @param topic "items", "shopping" oder "pending"
 */
public record DataChangedEvent(String topic) {

	public static final String ITEMS = "items";
	public static final String SHOPPING = "shopping";
	public static final String PENDING = "pending";
}
