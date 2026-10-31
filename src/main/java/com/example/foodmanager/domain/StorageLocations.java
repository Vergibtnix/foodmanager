package com.example.foodmanager.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Die festen Lagerorte. Formular, Suche, Lagerort-Seite und QR-Etiketten nutzen genau diese Liste. */
public final class StorageLocations {

	public static final List<String> ALL = List.of(
			"WC",
			"Eiskasten",
			"Tiefkühler Groß",
			"Tiefkühler Klein",
			"Veranda",
			"Badezimmer",
			"Abstellkammer",
			"Küche",
			"Keller"
	);

	/** Frühere Schreibweisen, die beim Start auf die neuen Namen umgestellt werden. */
	private static final Map<String, String> LEGACY = Map.of(
			"klo", "WC",
			"abstellraum", "Abstellkammer",
			"tiefkühler (groß)", "Tiefkühler Groß",
			"tiefkühler (klein)", "Tiefkühler Klein"
	);

	private StorageLocations() {
	}

	/** Liefert den Lagerort in der festen Schreibweise, egal ob groß oder klein geschrieben. */
	public static Optional<String> find(String value) {
		if (value == null) {
			return Optional.empty();
		}
		String wanted = value.trim();
		return ALL.stream().filter(loc -> loc.equalsIgnoreCase(wanted)).findFirst();
	}

	/** Wie {@link #find}, kennt aber auch die alten Namen (z. B. "Klo" wird "WC"). */
	public static Optional<String> migrate(String value) {
		if (value == null) {
			return Optional.empty();
		}
		String legacy = LEGACY.get(value.trim().toLowerCase());
		return legacy != null ? Optional.of(legacy) : find(value);
	}

	/** Position in der festen Liste, unbekannte Orte kommen ans Ende. */
	public static int order(String value) {
		int index = find(value).map(ALL::indexOf).orElse(-1);
		return index >= 0 ? index : ALL.size();
	}
}
