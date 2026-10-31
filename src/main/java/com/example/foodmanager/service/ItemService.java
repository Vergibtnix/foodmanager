package com.example.foodmanager.service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.foodmanager.domain.Item;
import com.example.foodmanager.domain.StorageLocations;
import com.example.foodmanager.repository.ItemRepository;
import com.example.foodmanager.web.ItemForm;
import com.example.foodmanager.web.DashboardSummary;

import org.springframework.http.HttpStatus;

@Service
@Transactional
public class ItemService {

	public static final String NO_LOCATION = "Ohne Lagerort";

	/** So lange bleibt das Bild eines gelöschten Items noch liegen (für "Rückgängig"). */
	private static final Duration IMAGE_DELETE_DELAY = Duration.ofMinutes(5);

	/** Ergebnis einer Bestandsänderung: das Item danach und sein Zustand davor (für "Rückgängig"). */
	public record StockChange(Item item, ItemSnapshot before) {
	}

	private final ItemRepository itemRepository;
	private final ImageStorageService imageStorageService;
	private final ProductLookupService productLookupService;
	private final ApplicationEventPublisher events;
	private final int expiringSoonDays;

	public ItemService(
			ItemRepository itemRepository,
			ImageStorageService imageStorageService,
			ProductLookupService productLookupService,
			ApplicationEventPublisher events,
			@Value("${foodmanager.expiring-soon-days:7}") int expiringSoonDays
	) {
		this.itemRepository = itemRepository;
		this.imageStorageService = imageStorageService;
		this.productLookupService = productLookupService;
		this.events = events;
		this.expiringSoonDays = expiringSoonDays;
	}

	private void changed() {
		events.publishEvent(new DataChangedEvent(DataChangedEvent.ITEMS));
	}

	public int getExpiringSoonDays() {
		return expiringSoonDays;
	}

	/** Menge eines Items, null wird als 0 gezählt. */
	public static int quantityOf(Item item) {
		Integer q = item.getQuantity();
		return q == null ? 0 : q;
	}

	// ------------------------------------------------------------
	// Dashboard / Übersicht
	// ------------------------------------------------------------

	@Transactional(readOnly = true)
	public long countAll() {
		return itemRepository.count();
	}

	@Transactional(readOnly = true)
	public List<Item> findLatest(int limit) {
		return itemRepository.findAllByOrderByCreatedAtDesc()
				.stream()
				.limit(limit)
				.toList();
	}

	// ------------------------------------------------------------
	// Ablaufdaten
	// ------------------------------------------------------------

	@Transactional(readOnly = true)
	public long countExpired() {
		LocalDate today = LocalDate.now();
		return itemRepository.findAll().stream()
				.filter(i -> i.getExpiryDate() != null && i.getExpiryDate().isBefore(today))
				.count();
	}

	@Transactional(readOnly = true)
	public long countExpiringSoon() {
		LocalDate today = LocalDate.now();
		LocalDate until = today.plusDays(expiringSoonDays);
		return itemRepository.findAll().stream()
				.filter(i -> i.getExpiryDate() != null
						&& !i.getExpiryDate().isBefore(today)
						&& !i.getExpiryDate().isAfter(until))
				.count();
	}

	/** Abgelaufene und bald ablaufende Items, das älteste Datum zuerst. */
	@Transactional(readOnly = true)
	public List<Item> findDueSoon(int limit) {
		LocalDate until = LocalDate.now().plusDays(expiringSoonDays);
		return itemRepository.findAll().stream()
				.filter(i -> i.getExpiryDate() != null && !i.getExpiryDate().isAfter(until))
				.sorted(Comparator.comparing(Item::getExpiryDate))
				.limit(limit)
				.toList();
	}

	// ------------------------------------------------------------
	// Bestand: Mindestbestand, Menge ändern, Barcode-Suche
	// ------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<Item> findLowStock() {
		return itemRepository.findAll().stream()
				.filter(i -> i.getMinQuantity() != null && quantityOf(i) < i.getMinQuantity())
				.sorted(Comparator.comparing(Item::getName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	@Transactional(readOnly = true)
	public Optional<Item> findByBarcode(String barcode) {
		if (!StringUtils.hasText(barcode)) {
			return Optional.empty();
		}
		String wanted = barcode.trim();
		return itemRepository.findAll().stream()
				.filter(i -> i.getBarcode() != null && wanted.equals(i.getBarcode().trim()))
				.findFirst();
	}

	/**
	 * Menge um so viele Schritte der Einheit ändern (1 Stück, 100 g, 100 ml), nie unter 0.
	 * Verbrauchen nimmt zuerst die Charge, die am frühesten abläuft.
	 */
	public StockChange adjustQuantity(Long id, int steps) {
		return addStock(id, steps, null);
	}

	/**
	 * Wie adjustQuantity, aber neue Ware darf ein eigenes Ablaufdatum haben (zum Beispiel aus dem GS1-Code).
	 * Die Zeile wird gesperrt, damit zwei gleichzeitige Klicks beide zählen.
	 */
	public StockChange addStock(Long id, int steps, LocalDate expiry) {
		Item item = getForUpdate(id);
		ItemSnapshot before = ItemSnapshot.of(item);
		int amount = Math.abs(steps) * item.getUnit().getStep();
		if (steps > 0) {
			item.addStock(amount, expiry);
		} else {
			item.consume(amount);
		}
		itemRepository.saveAndFlush(item);
		changed();
		return new StockChange(item, before);
	}

	/** Von einer bestimmten Charge verbrauchen (steps = 0 heißt: die ganze Charge entfernen). */
	public StockChange consumeBatch(Long id, Long batchId, int steps) {
		Item item = getForUpdate(id);
		ItemSnapshot before = ItemSnapshot.of(item);
		int amount = steps <= 0 ? Integer.MAX_VALUE : steps * item.getUnit().getStep();
		item.consumeBatch(batchId, amount);
		itemRepository.saveAndFlush(item);
		changed();
		return new StockChange(item, before);
	}

	/** Nach dem Einkaufen: Menge auf den Mindestbestand auffüllen. */
	public Item restockToMinimum(Long id) {
		Item item = getForUpdate(id);
		Integer min = item.getMinQuantity();
		if (min != null && quantityOf(item) < min) {
			item.addStock(min - quantityOf(item), null);
		}
		changed();
		return itemRepository.save(item);
	}

	// ------------------------------------------------------------
	// Rückgängig
	// ------------------------------------------------------------

	/**
	 * Setzt den Bestand auf den gemerkten Stand zurück. Klappt nur, wenn seitdem niemand sonst
	 * etwas am Item geändert hat (sonst würde dessen Änderung überschrieben).
	 */
	public boolean undoChange(Long id, long expectedVersion, ItemSnapshot before) {
		Item item = itemRepository.findByIdForUpdate(id).orElse(null);
		if (item == null || item.getVersion() != expectedVersion) {
			return false;
		}
		item.restoreStock(before.quantity(), before.expiryDate(), before.batches());
		itemRepository.save(item);
		changed();
		return true;
	}

	/** Gelöschtes Item mit allen Werten neu anlegen. */
	public Item restoreDeleted(ItemSnapshot snapshot) {
		imageStorageService.cancelDelete(snapshot.imageFilename());
		Item item = itemRepository.save(snapshot.toNewItem());
		changed();
		return item;
	}

	// ------------------------------------------------------------
	// Lagerorte
	// ------------------------------------------------------------

	/** Items je Lagerort, in der Reihenfolge der festen Liste. Ältere, unbekannte Orte folgen danach. */
	@Transactional(readOnly = true)
	public Map<String, List<Item>> groupByLocation() {
		Map<String, List<Item>> grouped = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

		for (Item item : itemRepository.findAllByOrderByCreatedAtDesc()) {
			String location = StringUtils.hasText(item.getStorageLocation())
					? item.getStorageLocation().trim()
					: NO_LOCATION;
			grouped.computeIfAbsent(location, key -> new ArrayList<>()).add(item);
		}

		grouped.values().forEach(list ->
				list.sort(Comparator.comparing(Item::getName, String.CASE_INSENSITIVE_ORDER)));

		Map<String, List<Item>> ordered = new LinkedHashMap<>();
		grouped.keySet().stream()
				.sorted(Comparator.comparingInt((String loc) -> NO_LOCATION.equals(loc) ? Integer.MAX_VALUE : StorageLocations.order(loc)))
				.forEach(loc -> ordered.put(loc, grouped.get(loc)));
		return ordered;
	}

	/** Items nach Kategorie sortiert, Kategorien alphabetisch, ohne Kategorie zuletzt. */
	public static Map<String, List<Item>> groupByCategory(List<Item> items) {
		Map<String, List<Item>> grouped = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		List<Item> withoutCategory = new ArrayList<>();

		for (Item item : items) {
			if (StringUtils.hasText(item.getCategory())) {
				grouped.computeIfAbsent(item.getCategory().trim(), key -> new ArrayList<>()).add(item);
			} else {
				withoutCategory.add(item);
			}
		}

		Map<String, List<Item>> result = new LinkedHashMap<>(grouped);
		if (!withoutCategory.isEmpty()) {
			result.put("Ohne Kategorie", withoutCategory);
		}
		return result;
	}

	/** Alle festen Lagerorte, dazu ältere Orte, an denen noch Items liegen. */
	@Transactional(readOnly = true)
	public List<String> findLocations() {
		List<String> locations = new ArrayList<>(StorageLocations.ALL);
		groupByLocation().keySet().stream()
				.filter(name -> !NO_LOCATION.equals(name) && StorageLocations.find(name).isEmpty())
				.forEach(locations::add);
		return locations;
	}

	/** Stellt alte Schreibweisen (z. B. "Klo") auf die festen Namen um. Liefert die Zahl der geänderten Items. */
	public int migrateLegacyLocations() {
		int changed = 0;
		for (Item item : itemRepository.findAll()) {
			String current = item.getStorageLocation();
			String target = StorageLocations.migrate(current).orElse(null);
			if (target != null && !target.equals(current)) {
				item.setStorageLocation(target);
				changed++;
			}
		}
		return changed;
	}

	// ------------------------------------------------------------
	// Erweiterte Suche (Name, Marke, Barcode, Art, Lagerort)
	// ------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<Item> findAll(String q, String type, String location) {

		return itemRepository.findAllByOrderByCreatedAtDesc()
				.stream()
				// Textsuche
				.filter(i ->
						!StringUtils.hasText(q)
								|| i.getName().toLowerCase().contains(q.toLowerCase())
								|| (i.getBrand() != null && i.getBrand().toLowerCase().contains(q.toLowerCase()))
								|| (i.getBarcode() != null && i.getBarcode().contains(q))
				)
				// Art = Kategorie (Getränk, Lebensmittel, Elektronik, Werkzeug, Medizin)
				.filter(i ->
						!StringUtils.hasText(type)
								|| type.equalsIgnoreCase(i.getCategory())
				)
				// Lagerort (Eiskasten, Keller, Tiefkühler …)
				.filter(i ->
						!StringUtils.hasText(location)
								|| location.equalsIgnoreCase(i.getStorageLocation())
				)
				.toList();
	}

	// ------------------------------------------------------------
	// CRUD
	// ------------------------------------------------------------

	/** Item samt Chargen (die Seiten brauchen sie nach dem Ende der Transaktion). */
	@Transactional(readOnly = true)
	public Item getById(Long id) {
		Item item = itemRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item nicht gefunden."));
		item.getBatches(); // lädt die Chargen
		return item;
	}

	private Item getForUpdate(Long id) {
		return itemRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item nicht gefunden."));
	}

	public Item create(ItemForm form, MultipartFile image) throws IOException {
		Item item = new Item();
		applyForm(item, form, image, false);
		item.replaceStock(form.getQuantity(), form.getExpiryDate());
		productLookupService.remember(item.getBarcode(), item.getName(), item.getBrand(), item.getExternalImageUrl());
		changed();
		return itemRepository.save(item);
	}

	/**
	 * Speichert das Formular. Hat jemand anderes das Item seit dem Öffnen geändert, kommt
	 * ItemChangedException, statt dessen Änderung stillschweigend zu überschreiben.
	 */
	public Item update(Long id, ItemForm form, MultipartFile image) throws IOException {
		Item item = getById(id);
		if (form.getVersion() != null && form.getVersion() != item.getVersion()) {
			throw new ItemChangedException();
		}

		// Menge und Datum nur anfassen, wenn sie im Formular geändert wurden. Sonst bleiben die Chargen erhalten.
		boolean stockChanged = !Objects.equals(form.getQuantity(), item.getQuantity())
				|| !Objects.equals(form.getExpiryDate(), item.getExpiryDate());

		applyForm(item, form, image, true);
		if (stockChanged) {
			item.replaceStock(form.getQuantity(), form.getExpiryDate());
		}
		changed();
		return itemRepository.saveAndFlush(item);
	}

	/** Löscht das Item und liefert seine Werte, damit es per "Rückgängig" wieder angelegt werden kann. */
	public ItemSnapshot delete(Long id) {
		Item item = getById(id);
		ItemSnapshot snapshot = ItemSnapshot.of(item);
		imageStorageService.deleteLater(item.getImageFilename(), IMAGE_DELETE_DELAY);
		itemRepository.delete(item);
		changed();
		return snapshot;
	}

	// ------------------------------------------------------------
	// Formularverarbeitung
	// ------------------------------------------------------------

	private void applyForm(Item item, ItemForm form, MultipartFile image, boolean keepExistingImage) throws IOException {
		item.setName(form.getName().trim());
		item.setBrand(trimToNull(form.getBrand()));
		item.setBarcode(trimToNull(form.getBarcode()));

		item.setCategory(trimToNull(form.getCategory()));

		// Menge und Ablaufdatum setzen create/update über replaceStock (wegen der Chargen)
		item.setUnit(form.getUnit());

		// Lagerort in der festen Schreibweise speichern
		item.setStorageLocation(StorageLocations.find(form.getStorageLocation())
				.orElse(trimToNull(form.getStorageLocation())));

		// Art (Getränk, Lebensmittel, Elektronik, Werkzeug, Medizin)
		item.setType(trimToNull(form.getType()));

		item.setNotes(trimToNull(form.getNotes()));
		item.setExternalImageUrl(trimToNull(form.getExternalImageUrl()));

		// neue Felder
		item.setCondition(trimToNull(form.getCondition()));
		item.setPurchaseDate(form.getPurchaseDate());
		item.setMinQuantity(form.getMinQuantity());

		// Bild speichern
		if (image != null && !image.isEmpty()) {
			if (keepExistingImage) {
				imageStorageService.deleteIfExists(item.getImageFilename());
			}
			item.setImageFilename(imageStorageService.store(image));
		}
	}

	private String trimToNull(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim();
	}

	// ------------------------------------------------------------
	// Dashboard Summary
	// ------------------------------------------------------------

	@Transactional(readOnly = true)
	public DashboardSummary getDashboardSummary() {

		long totalItems = itemRepository.count();

		long belowMin = findLowStock().size();

		long defective = itemRepository.findAll().stream()
				.filter(i -> "Defekt".equalsIgnoreCase(i.getCondition()))
				.count();

		List<Item> latest = findLatest(6);

		return new DashboardSummary(
				totalItems,
				belowMin,
				defective,
				latest
		);
	}
}
