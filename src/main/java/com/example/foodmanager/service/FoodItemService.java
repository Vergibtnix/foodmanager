package com.example.foodmanager.service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.foodmanager.domain.FoodItem;
import com.example.foodmanager.domain.ProductCategory;
import com.example.foodmanager.repository.FoodItemRepository;
import com.example.foodmanager.web.DashboardSummary;
import com.example.foodmanager.web.FoodItemForm;

import org.springframework.http.HttpStatus;

@Service
@Transactional
public class FoodItemService {

	public static final List<String> STORAGE_LOCATIONS = List.of(
		"Eiskasten",
		"Naschlade",
		"Tiefkühler klein",
		"TieferKühler Groß",
		"Gewuerzlade",
		"Nudellade",
		"Back Kasten",
		"Brot korb"
	);

	private final FoodItemRepository foodItemRepository;
	private final ImageStorageService imageStorageService;
	private final int expiringSoonDays;

	public FoodItemService(
		FoodItemRepository foodItemRepository,
		ImageStorageService imageStorageService,
		@Value("${foodmanager.expiring-soon-days:7}") int expiringSoonDays
	) {
		this.foodItemRepository = foodItemRepository;
		this.imageStorageService = imageStorageService;
		this.expiringSoonDays = expiringSoonDays;
	}

	@Transactional(readOnly = true)
	public DashboardSummary getDashboardSummary() {
		LocalDate today = LocalDate.now();
		return new DashboardSummary(
			foodItemRepository.count(),
			foodItemRepository.countByExpiryDateBefore(today),
			foodItemRepository.countByExpiryDateBetween(today, today.plusDays(expiringSoonDays)),
			foodItemRepository.findTop5ByOrderByExpiryDateAscCreatedAtDesc()
		);
	}

	@Transactional(readOnly = true)
	public List<FoodItem> findAll(String searchQuery) {
		if (!StringUtils.hasText(searchQuery)) {
			return foodItemRepository.findAllByOrderByExpiryDateAscCreatedAtDesc();
		}
		return foodItemRepository.search(searchQuery.trim());
	}

	@Transactional(readOnly = true)
	public List<FoodItem> filterItems(List<FoodItem> items, String location, ProductCategory category) {
		List<FoodItem> filtered = new ArrayList<>();
		for (FoodItem item : items) {
			if (StringUtils.hasText(location) && !location.equals(item.getStorageLocation())) {
				continue;
			}
			if (category != null && item.getCategory() != category) {
				continue;
			}
			filtered.add(item);
		}
		return filtered;
	}

	@Transactional(readOnly = true)
	public Map<String, Map<ProductCategory, List<FoodItem>>> groupByLocationAndCategory(List<FoodItem> items) {
		Map<String, Map<ProductCategory, List<FoodItem>>> grouped = new LinkedHashMap<>();
		for (FoodItem item : items) {
			String location = item.getStorageLocation();
			ProductCategory category = item.getCategory();
			grouped
				.computeIfAbsent(location, key -> new LinkedHashMap<>())
				.computeIfAbsent(category, key -> new java.util.ArrayList<>())
				.add(item);
		}

		Map<String, Map<ProductCategory, List<FoodItem>>> ordered = new LinkedHashMap<>();
		for (String location : STORAGE_LOCATIONS) {
			Map<ProductCategory, List<FoodItem>> categories = grouped.remove(location);
			if (categories != null) {
				ordered.put(location, orderCategories(categories));
			}
		}
		grouped.entrySet().stream()
			.sorted(Map.Entry.comparingByKey(Comparator.nullsLast(String::compareToIgnoreCase)))
			.forEach(entry -> ordered.put(entry.getKey(), orderCategories(entry.getValue())));

		return ordered;
	}

	private Map<ProductCategory, List<FoodItem>> orderCategories(Map<ProductCategory, List<FoodItem>> categories) {
		Map<ProductCategory, List<FoodItem>> ordered = new LinkedHashMap<>();
		for (ProductCategory category : ProductCategory.values()) {
			List<FoodItem> items = categories.get(category);
			if (items != null && !items.isEmpty()) {
				ordered.put(category, items);
			}
		}
		categories.entrySet().stream()
			.filter(entry -> !ordered.containsKey(entry.getKey()))
			.forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
		return ordered;
	}

	@Transactional(readOnly = true)
	public FoodItem getById(Long id) {
		return foodItemRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lebensmittel nicht gefunden."));
	}

	public FoodItem create(FoodItemForm form, MultipartFile image) throws IOException {
		FoodItem item = new FoodItem();
		applyForm(item, form, image, false);
		return foodItemRepository.save(item);
	}

	public FoodItem update(Long id, FoodItemForm form, MultipartFile image) throws IOException {
		FoodItem item = getById(id);
		applyForm(item, form, image, true);
		return foodItemRepository.save(item);
	}

	public void delete(Long id) {
		FoodItem item = getById(id);
		imageStorageService.deleteIfExists(item.getImageFilename());
		foodItemRepository.delete(item);
	}

	private void applyForm(FoodItem item, FoodItemForm form, MultipartFile image, boolean keepExistingImage) throws IOException {
		item.setName(form.getName().trim());
		item.setBrand(trimToNull(form.getBrand()));
		item.setBarcode(trimToNull(form.getBarcode()));
		item.setExpiryDate(form.getExpiryDate());
		item.setQuantity(form.getQuantity());
		item.setStorageLocation(normalizeStorageLocation(form.getStorageLocation(), item.getStorageLocation()));
		item.setCategory(form.getCategory() == null ? ProductCategory.SONSTIGES : form.getCategory());
		item.setNotes(trimToNull(form.getNotes()));
		item.setExternalImageUrl(trimToNull(form.getExternalImageUrl()));

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

	private String normalizeStorageLocation(String value, String existingLocation) {
		if (!StringUtils.hasText(value)) {
			return existingLocation == null || existingLocation.isBlank() ? STORAGE_LOCATIONS.get(0) : existingLocation.trim();
		}

		String trimmed = value.trim();
		if (STORAGE_LOCATIONS.contains(trimmed)) {
			return trimmed;
		}
		if (existingLocation != null && existingLocation.trim().equals(trimmed)) {
			return trimmed;
		}
		return STORAGE_LOCATIONS.get(0);
	}
}

