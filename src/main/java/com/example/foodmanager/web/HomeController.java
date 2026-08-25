package com.example.foodmanager.web;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.foodmanager.service.FoodItemService;

@Controller
public class HomeController {

	private final FoodItemService foodItemService;

	public HomeController(FoodItemService foodItemService) {
		this.foodItemService = foodItemService;
	}

	@GetMapping("/")
	public String home(Model model, Principal principal) {
		boolean authenticated = principal != null;
		model.addAttribute("authenticated", authenticated);
		model.addAttribute("loginUrl", "/oauth2/authorization/keycloak");
		if (authenticated) {
			model.addAttribute("username", principal.getName());
			model.addAttribute("summary", foodItemService.getDashboardSummary());
		}
		return "index";
	}

	@GetMapping("/scan")
	public String scanPage() {
		return "scan";
	}
}

