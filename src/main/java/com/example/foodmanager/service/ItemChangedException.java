package com.example.foodmanager.service;

/** Jemand anderes hat das Item geändert, während das Formular offen war. */
public class ItemChangedException extends RuntimeException {

	public ItemChangedException() {
		super("Das Item wurde inzwischen von jemand anderem geändert.");
	}
}
