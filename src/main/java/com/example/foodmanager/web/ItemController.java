package com.example.foodmanager.web;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.example.foodmanager.service.ItemService;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;

@Controller
@RequestMapping("/items")
public class ItemController {

	private final ItemService itemService;

	public ItemController(ItemService itemService) {
		this.itemService = itemService;
	}

	// ------------------------------------------------------------
	// LISTE + SUCHE (Name, Marke, Barcode, Art, Lagerort)
	// ------------------------------------------------------------

	@GetMapping
	public String list(
			@RequestParam(name = "q", required = false) String query,
			@RequestParam(name = "type", required = false) String type,
			@RequestParam(name = "location", required = false) String location,
			Model model,
			Principal principal
	) {

		model.addAttribute("authenticated", principal != null);

		model.addAttribute("items", itemService.findAll(query, type, location));

		model.addAttribute("query", query);
		model.addAttribute("selectedType", type);
		model.addAttribute("selectedLocation", location);

		return "items/list";
	}

	// ------------------------------------------------------------
	// CREATE FORM
	// ------------------------------------------------------------

	@GetMapping("/new")
	public String createForm(
			@RequestParam(name = "barcode", required = false) String barcode,
			@RequestParam(name = "name", required = false) String name,
			@RequestParam(name = "brand", required = false) String brand,
			@RequestParam(name = "imageUrl", required = false) String imageUrl,
			Model model
	) {

		ItemForm form = new ItemForm();

		if (barcode != null) form.setBarcode(barcode);
		if (name != null) form.setName(name);
		if (brand != null) form.setBrand(brand);
		if (imageUrl != null) form.setExternalImageUrl(imageUrl);

		model.addAttribute("itemForm", form);
		model.addAttribute("pageTitle", "Neues Item erfassen");
		model.addAttribute("formAction", "/items");

		return "items/form";
	}

	// ------------------------------------------------------------
	// CREATE (mit Validierung)
	// ------------------------------------------------------------

	@PostMapping
	public String create(
			@Valid @ModelAttribute("itemForm") ItemForm form,
			BindingResult bindingResult,
			@RequestParam("image") MultipartFile image,
			Model model
	) throws Exception {

		if (bindingResult.hasErrors()) {
			model.addAttribute("pageTitle", "Neues Item erfassen");
			model.addAttribute("formAction", "/items");
			return "items/form";
		}

		itemService.create(form, image);
		return "redirect:/items";
	}

	// ------------------------------------------------------------
	// EDIT FORM
	// ------------------------------------------------------------

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {

		var item = itemService.getById(id);
		var form = ItemForm.fromEntity(item);

		model.addAttribute("item", item);
		model.addAttribute("itemForm", form);
		model.addAttribute("pageTitle", "Item bearbeiten");
		model.addAttribute("formAction", "/items/" + id);

		return "items/form";
	}

	// ------------------------------------------------------------
	// UPDATE (mit Validierung)
	// ------------------------------------------------------------

	@PostMapping("/{id}")
	public String update(
			@PathVariable Long id,
			@Valid @ModelAttribute("itemForm") ItemForm form,
			BindingResult bindingResult,
			@RequestParam("image") MultipartFile image,
			Model model
	) throws Exception {

		if (bindingResult.hasErrors()) {
			model.addAttribute("pageTitle", "Item bearbeiten");
			model.addAttribute("formAction", "/items/" + id);
			return "items/form";
		}

		itemService.update(id, form, image);
		return "redirect:/items";
	}

	// ------------------------------------------------------------
	// DELETE
	// ------------------------------------------------------------

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id) {
		itemService.delete(id);
		return "redirect:/items";
	}
}
