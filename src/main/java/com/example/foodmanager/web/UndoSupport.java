package com.example.foodmanager.web;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.foodmanager.service.ItemSnapshot;

import jakarta.servlet.http.HttpSession;

/**
 * Merkt sich pro Anmeldung (Sitzung) die letzte Änderung, damit sie rückgängig gemacht werden kann.
 * Jede Person hat ihr eigenes "Rückgängig", Änderungen anderer werden dabei nie überschrieben.
 */
@Component
public class UndoSupport {

	static final Duration WINDOW = Duration.ofMinutes(2);
	private static final String SESSION_KEY = "foodmanager.undo";

	/** itemId == null heißt: das Item wurde gelöscht und wird beim Rückgängig neu angelegt. */
	record Entry(String token, Long itemId, long versionAfter, ItemSnapshot before, Instant createdAt)
			implements Serializable {
	}

	/** Änderung merken und den "Rückgängig"-Knopf in der nächsten Seite einblenden. */
	public void offer(HttpSession session, RedirectAttributes redirect, Long itemId, long versionAfter, ItemSnapshot before) {
		String token = UUID.randomUUID().toString();
		session.setAttribute(SESSION_KEY, new Entry(token, itemId, versionAfter, before, Instant.now()));
		redirect.addFlashAttribute("undoToken", token);
	}

	/** Holt die gemerkte Änderung genau einmal, falls Token passt und sie nicht zu alt ist. */
	Optional<Entry> take(HttpSession session, String token) {
		Object value = session.getAttribute(SESSION_KEY);
		if (!(value instanceof Entry entry) || !entry.token().equals(token)) {
			return Optional.empty();
		}
		session.removeAttribute(SESSION_KEY);
		if (entry.createdAt().plus(WINDOW).isBefore(Instant.now())) {
			return Optional.empty();
		}
		return Optional.of(entry);
	}

	/** Nur Pfade innerhalb der App zulassen, keine fremden Adressen. */
	static String safeTarget(String next, String fallback) {
		if (next != null && next.startsWith("/") && !next.startsWith("//") && !next.contains("\\")) {
			return next;
		}
		return fallback;
	}
}
