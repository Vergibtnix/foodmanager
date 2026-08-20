package com.example.foodmanager.web;

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
	public String home(Model model) {
		model.addAttribute("summary", foodItemService.getDashboardSummary());
		return "index";
	}

	@GetMapping("/scan")
	public String scanPage() {
		return "scan";
	}
}

