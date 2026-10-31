package com.example.foodmanager.web;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.foodmanager.service.ExpiryNotificationService;
import com.example.foodmanager.service.ItemService;
import com.example.foodmanager.service.RecipeService;
import com.example.foodmanager.service.SeriesScanService;

@Controller
public class HomeController {

	private final ItemService itemService;
	private final RecipeService recipeService;
	private final ExpiryNotificationService notificationService;
	private final SeriesScanService seriesScanService;

	public HomeController(
			ItemService itemService,
			RecipeService recipeService,
			ExpiryNotificationService notificationService,
			SeriesScanService seriesScanService
	) {
		this.itemService = itemService;
		this.recipeService = recipeService;
		this.notificationService = notificationService;
		this.seriesScanService = seriesScanService;
	}

	@GetMapping("/")
	public String home(Model model, Principal principal) {

		boolean authenticated = principal != null;
		model.addAttribute("authenticated", authenticated);
		model.addAttribute("loginUrl", "/oauth2/authorization/keycloak");

		// Nur Zahlen sind öffentlich, keine Namen, Bilder oder Lagerorte.
		model.addAttribute("summary", itemService.getDashboardSummary());
		model.addAttribute("expiredCount", itemService.countExpired());
		model.addAttribute("expiringSoonCount", itemService.countExpiringSoon());
		model.addAttribute("expiringSoonDays", itemService.getExpiringSoonDays());

		if (authenticated) {
			model.addAttribute("username", principal.getName());
			model.addAttribute("dueItems", itemService.findDueSoon(8));
			model.addAttribute("cookSuggestions", recipeService.suggest(3));
			model.addAttribute("notifyEnabled", notificationService.isEnabled());
			model.addAttribute("pendingCount", seriesScanService.countPending());
		}

		return "index";
	}
}
