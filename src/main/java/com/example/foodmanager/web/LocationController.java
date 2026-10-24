package com.example.foodmanager.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.foodmanager.domain.Item;
import com.example.foodmanager.service.ItemService;

@Controller
public class LocationController {

    private final ItemService itemService;

    public LocationController(ItemService itemService) {
        this.itemService = itemService;
    }

    /** Alle Lagerorte mit ihrem Inhalt, oder nur einer (?name=Keller), zum Beispiel über einen QR-Code. */
    @GetMapping("/locations")
    public String locations(@RequestParam(required = false) String name, Model model) {
        Map<String, List<Item>> groups = itemService.groupByLocation();

        String selected = StringUtils.hasText(name) ? name.trim() : null;
        if (selected != null) {
            Map<String, List<Item>> filtered = new LinkedHashMap<>();
            for (Map.Entry<String, List<Item>> entry : groups.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(selected)) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            }
            groups = filtered;
        }

        model.addAttribute("pageTitle", selected != null ? selected : "Lagerorte");
        model.addAttribute("selected", selected);
        model.addAttribute("groups", groups);
        return "locations";
    }
}
