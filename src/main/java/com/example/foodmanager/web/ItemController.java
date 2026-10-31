package com.example.foodmanager.web;

import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import com.example.foodmanager.domain.StorageLocations;
import com.example.foodmanager.service.ItemChangedException;
import com.example.foodmanager.service.ItemService;
import com.example.foodmanager.service.ItemSnapshot;
import com.example.foodmanager.service.SeriesScanService;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;

@Controller
@RequestMapping("/items")
public class ItemController {

	private final ItemService itemService;
	private final SeriesScanService seriesScanService;
	private final UndoSupport undo;

	public ItemController(ItemService itemService, SeriesScanService seriesScanService, UndoSupport undo) {
		this.itemService = itemService;
		this.seriesScanService = seriesScanService;
		this.undo = undo;
	}

	// ------------------------------------------------------------
	// DETAIL (mit Chargen)
	// ------------------------------------------------------------

	@GetMapping("/{id}")
	public String detail(@PathVariable Long id, Model model) {
		model.addAttribute("item", itemService.getById(id));
		return "items/detail";
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
			@RequestParam(name = "expiry", required = false) String expiry,
			@RequestParam(name = "quantity", required = false) Integer quantity,
			@RequestParam(name = "pending", required = false) Long pendingScanId,
			Model model
	) {

		ItemForm form = new ItemForm();
		if (quantity != null && quantity > 0) form.setQuantity(quantity);
		form.setPendingScanId(pendingScanId);

		if (barcode != null) form.setBarcode(barcode);
		if (name != null) form.setName(name);
		if (brand != null) form.setBrand(brand);
		if (imageUrl != null) form.setExternalImageUrl(imageUrl);

		// Ablaufdatum aus GS1-Code; fehlt es oder ist es ungültig, bleibt das Feld zum manuellen Ausfüllen leer
		if (StringUtils.hasText(expiry)) {
			try {
				form.setExpiryDate(LocalDate.parse(expiry));
			} catch (DateTimeParseException e) {
				// ignorieren
			}
		}

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

		checkLocation(form, bindingResult);
		if (bindingResult.hasErrors()) {
			model.addAttribute("pageTitle", "Neues Item erfassen");
			model.addAttribute("formAction", "/items");
			return "items/form";
		}

		itemService.create(form, image);

		// Aus der Einräum-Liste angelegt: Eintrag weg und, falls noch etwas offen ist, zurück zur Liste
		if (form.getPendingScanId() != null) {
			seriesScanService.removePending(form.getPendingScanId());
			if (seriesScanService.countPending() > 0) {
				return "redirect:/scan/inbox";
			}
		}
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

		checkLocation(form, bindingResult);
		if (bindingResult.hasErrors()) {
			model.addAttribute("item", itemService.getById(id));
			model.addAttribute("pageTitle", "Item bearbeiten");
			model.addAttribute("formAction", "/items/" + id);
			return "items/form";
		}

		try {
			itemService.update(id, form, image);
		} catch (ItemChangedException | ObjectOptimisticLockingFailureException e) {
			// Jemand anderes war schneller: aktuellen Stand zeigen statt seine Änderung zu überschreiben
			var current = itemService.getById(id);
			model.addAttribute("item", current);
			model.addAttribute("itemForm", ItemForm.fromEntity(current));
			model.addAttribute("pageTitle", "Item bearbeiten");
			model.addAttribute("formAction", "/items/" + id);
			model.addAttribute("conflictMessage",
					"Jemand anderes hat dieses Item gerade geändert. Unten siehst du den neuen Stand. "
							+ "Bitte deine Änderung noch einmal eintragen und speichern.");
			return "items/form";
		}
		return "redirect:/items";
	}

	// ------------------------------------------------------------
	// DELETE
	// ------------------------------------------------------------

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, HttpSession session, RedirectAttributes redirect) {
		ItemSnapshot snapshot = itemService.delete(id);
		redirect.addFlashAttribute("successMessage", snapshot.name() + " wurde gelöscht.");
		undo.offer(session, redirect, null, 0, snapshot);
		return "redirect:/items";
	}

	// Nur die festen Lagerorte sind erlaubt. Leer meldet schon @NotBlank.
	private void checkLocation(ItemForm form, BindingResult bindingResult) {
		if (StringUtils.hasText(form.getStorageLocation())
				&& StorageLocations.find(form.getStorageLocation()).isEmpty()) {
			bindingResult.rejectValue("storageLocation", "invalid", "Bitte einen Lagerort aus der Liste wählen.");
		}
	}
}
