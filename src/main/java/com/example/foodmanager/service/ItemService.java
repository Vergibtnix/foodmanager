package com.example.foodmanager.service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.foodmanager.domain.Item;
import com.example.foodmanager.repository.ItemRepository;
import com.example.foodmanager.web.ItemForm;
import com.example.foodmanager.web.DashboardSummary;

import org.springframework.http.HttpStatus;

@Service
@Transactional
public class ItemService {

	public static final String NO_LOCATION = "Ohne Lagerort";

	private final ItemRepository itemRepository;
	private final ImageStorageService imageStorageService;
	private final int expiringSoonDays;

	public ItemService(
			ItemRepository itemRepository,
			ImageStorageService imageStorageService,
			@Value("${foodmanager.expiring-soon-days:7}") int expiringSoonDays
	) {
		this.itemRepository = itemRepository;
		this.imageStorageService = imageStorageService;
		this.expiringSoonDays = expiringSoonDays;
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

	@Transactional(readOnly = true)
	public List<Item> findExpired() {
		LocalDate today = LocalDate.now();
		return itemRepository.findAll().stream()
				.filter(i -> i.getExpiryDate() != null && i.getExpiryDate().isBefore(today))
				.sorted(Comparator.comparing(Item::getExpiryDate))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<Item> findExpiringSoon() {
		LocalDate today = LocalDate.now();
		LocalDate until = today.plusDays(expiringSoonDays);
		return itemRepository.findAll().stream()
				.filter(i -> i.getExpiryDate() != null
						&& !i.getExpiryDate().isBefore(today)
						&& !i.getExpiryDate().isAfter(until))
				.sorted(Comparator.comparing(Item::getExpiryDate))
				.toList();
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

	/** Menge um delta ändern, nie unter 0. */
	public Item adjustQuantity(Long id, int delta) {
		Item item = getById(id);
		item.setQuantity(Math.max(0, quantityOf(item) + delta));
		return itemRepository.save(item);
	}

	/** Nach dem Einkaufen: Menge auf den Mindestbestand auffüllen. */
	public Item restockToMinimum(Long id) {
		Item item = getById(id);
		Integer min = item.getMinQuantity();
		if (min != null && quantityOf(item) < min) {
			item.setQuantity(min);
		}
		return itemRepository.save(item);
	}

	// ------------------------------------------------------------
	// Lagerorte
	// ------------------------------------------------------------

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
		return grouped;
	}

	@Transactional(readOnly = true)
	public List<String> findLocations() {
		return groupByLocation().keySet().stream()
				.filter(name -> !NO_LOCATION.equals(name))
				.toList();
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
				// Art (Getränk, Lebensmittel, Elektronik, Werkzeug, Medizin)
				.filter(i ->
						!StringUtils.hasText(type)
								|| type.equalsIgnoreCase(i.getType())
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

	@Transactional(readOnly = true)
	public Item getById(Long id) {
		return itemRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item nicht gefunden."));
	}

	public Item create(ItemForm form, MultipartFile image) throws IOException {
		Item item = new Item();
		applyForm(item, form, image, false);
		return itemRepository.save(item);
	}

	public Item update(Long id, ItemForm form, MultipartFile image) throws IOException {
		Item item = getById(id);
		applyForm(item, form, image, true);
		return itemRepository.save(item);
	}

	public void delete(Long id) {
		Item item = getById(id);
		imageStorageService.deleteIfExists(item.getImageFilename());
		itemRepository.delete(item);
	}

	// ------------------------------------------------------------
	// Formularverarbeitung
	// ------------------------------------------------------------

	private void applyForm(Item item, ItemForm form, MultipartFile image, boolean keepExistingImage) throws IOException {
		item.setName(form.getName().trim());
		item.setBrand(trimToNull(form.getBrand()));
		item.setBarcode(trimToNull(form.getBarcode()));

		item.setCategory(trimToNull(form.getCategory()));

		// Ablaufdatum optional
		item.setExpiryDate(form.getExpiryDate());

		item.setQuantity(form.getQuantity());

		// Lagerort
		item.setStorageLocation(trimToNull(form.getStorageLocation()));

		// Art (Getränk, Lebensmittel, Elektronik, Werkzeug, Medizin)
		item.setType(trimToNull(form.getType()));

		item.setNotes(trimToNull(form.getNotes()));
		item.setExternalImageUrl(trimToNull(form.getExternalImageUrl()));

		// neue Felder
		item.setCondition(trimToNull(form.getCondition()));
		item.setPurchaseDate(form.getPurchaseDate());
		item.setWarrantyUntil(form.getWarrantyUntil());
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

		LocalDate today = LocalDate.now();
		long totalItems = itemRepository.count();

		long belowMin = findLowStock().size();

		// Garantie endet in den nächsten 30 Tagen (bereits abgelaufene zählen nicht mit)
		long warrantySoon = itemRepository.findAll().stream()
				.filter(i -> i.getWarrantyUntil() != null
						&& !i.getWarrantyUntil().isBefore(today)
						&& i.getWarrantyUntil().isBefore(today.plusDays(30)))
				.count();

		long defective = itemRepository.findAll().stream()
				.filter(i -> "Defekt".equalsIgnoreCase(i.getCondition()))
				.count();

		List<Item> latest = findLatest(6);

		return new DashboardSummary(
				totalItems,
				belowMin,
				warrantySoon,
				defective,
				latest
		);
	}
}