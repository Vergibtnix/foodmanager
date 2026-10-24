package com.example.foodmanager.web;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.foodmanager.service.ItemService;

@Controller
public class HomeController {

	private final ItemService itemService;

	public HomeController(ItemService itemService) {
		this.itemService = itemService;
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
		}

		return "index";
	}
}
