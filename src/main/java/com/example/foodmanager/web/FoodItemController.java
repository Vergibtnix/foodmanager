package com.example.foodmanager.web;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.foodmanager.domain.FoodItem;
import com.example.foodmanager.domain.ProductCategory;
import com.example.foodmanager.service.FoodItemService;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/items")
public class FoodItemController {

	private final FoodItemService foodItemService;

	public FoodItemController(FoodItemService foodItemService) {
		this.foodItemService = foodItemService;
	}

	@GetMapping
	public String list(
		@RequestParam(name = "q", required = false) String query,
		@RequestParam(name = "location", required = false) String location,
		@RequestParam(name = "category", required = false) ProductCategory category,
		Model model
	) {
		List<FoodItem> items = foodItemService.findAll(query);
		items = foodItemService.filterItems(items, location, category);
		model.addAttribute("items", items);
		model.addAttribute("groupedItems", foodItemService.groupByLocationAndCategory(items));
		model.addAttribute("query", query == null ? "" : query);
		model.addAttribute("selectedLocation", location == null ? "" : location);
		model.addAttribute("selectedCategory", category);
		return "items/list";
	}

	@GetMapping("/new")
	public String createForm(
		@RequestParam(name = "barcode", required = false) String barcode,
		@RequestParam(name = "name", required = false) String name,
		@RequestParam(name = "brand", required = false) String brand,
		@RequestParam(name = "imageUrl", required = false) String imageUrl,
		Model model
	) {
		FoodItemForm form = new FoodItemForm();
		form.setBarcode(barcode);
		form.setName(name);
		form.setBrand(brand);
		form.setExternalImageUrl(imageUrl);
		model.addAttribute("foodItemForm", form);
		model.addAttribute("pageTitle", "Lebensmittel anlegen");
		model.addAttribute("formAction", "/items");
		return "items/form";
	}

	@PostMapping
	public String create(
		@Valid @ModelAttribute("foodItemForm") FoodItemForm form,
		BindingResult bindingResult,
		@RequestParam(name = "image", required = false) MultipartFile image,
		Model model,
		RedirectAttributes redirectAttributes
	) throws IOException {
		if (bindingResult.hasErrors()) {
			model.addAttribute("pageTitle", "Lebensmittel anlegen");
			model.addAttribute("formAction", "/items");
			return "items/form";
		}

		foodItemService.create(form, image);
		redirectAttributes.addFlashAttribute("successMessage", "Lebensmittel erfolgreich gespeichert.");
		return "redirect:/items";
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable Long id, Model model) {
		FoodItem item = foodItemService.getById(id);
		model.addAttribute("item", item);
		return "items/detail";
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		FoodItem item = foodItemService.getById(id);
		model.addAttribute("foodItemForm", FoodItemForm.fromEntity(item));
		model.addAttribute("item", item);
		model.addAttribute("pageTitle", "Lebensmittel bearbeiten");
		model.addAttribute("formAction", "/items/" + id);
		return "items/form";
	}

	@PostMapping("/{id}")
	public String update(
		@PathVariable Long id,
		@Valid @ModelAttribute("foodItemForm") FoodItemForm form,
		BindingResult bindingResult,
		@RequestParam(name = "image", required = false) MultipartFile image,
		Model model,
		RedirectAttributes redirectAttributes
	) throws IOException {
		FoodItem item = foodItemService.getById(id);
		if (bindingResult.hasErrors()) {
			model.addAttribute("item", item);
			model.addAttribute("pageTitle", "Lebensmittel bearbeiten");
			model.addAttribute("formAction", "/items/" + id);
			return "items/form";
		}

		foodItemService.update(id, form, image);
		redirectAttributes.addFlashAttribute("successMessage", "Lebensmittel aktualisiert.");
		return "redirect:/items";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
		foodItemService.delete(id);
		redirectAttributes.addFlashAttribute("successMessage", "Lebensmittel gelöscht.");
		return "redirect:/items";
	}

	@ModelAttribute("storageLocations")
	public List<String> storageLocations() {
		return FoodItemService.STORAGE_LOCATIONS;
	}

	@ModelAttribute("categories")
	public List<ProductCategory> categories() {
		return Arrays.asList(ProductCategory.values());
	}
}

