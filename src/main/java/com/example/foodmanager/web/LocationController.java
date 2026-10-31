package com.example.foodmanager.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.foodmanager.domain.Item;
import com.example.foodmanager.domain.StorageLocations;
import com.example.foodmanager.service.ItemService;

@Controller
public class LocationController {

    private final ItemService itemService;

    public LocationController(ItemService itemService) {
        this.itemService = itemService;
    }

    /** Ein Lagerort mit seinem Inhalt, nach Kategorie gruppiert. */
    public record LocationView(String name, int count, long expired, long soon, Map<String, List<Item>> categories) {

        static LocationView of(String name, List<Item> items) {
            long expired = items.stream().filter(i -> i.getExpiryDate() != null && i.isExpired()).count();
            long soon = items.stream().filter(i -> i.getExpiryDate() != null && !i.isExpired() && i.isExpiringSoon()).count();
            return new LocationView(name, items.size(), expired, soon, ItemService.groupByCategory(items));
        }
    }

    /** Alle Lagerorte mit ihrem Inhalt, oder nur einer (?name=Keller), zum Beispiel über einen QR-Code. */
    @GetMapping("/locations")
    public String locations(@RequestParam(required = false) String name, Model model) {
        Map<String, List<Item>> groups = itemService.groupByLocation();

        // Anzahl je Lagerort für die Auswahl oben, alle festen Orte auch wenn leer
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String location : itemService.findLocations()) {
            counts.put(location, 0);
        }
        groups.forEach((location, items) -> counts.put(location, items.size()));

        String selected = null;
        if (StringUtils.hasText(name)) {
            String wanted = name.trim();
            selected = StorageLocations.migrate(wanted)
                    .or(() -> groups.keySet().stream().filter(k -> k.equalsIgnoreCase(wanted)).findFirst())
                    .orElse(wanted);
        }

        List<LocationView> views = new ArrayList<>();
        for (Map.Entry<String, List<Item>> entry : groups.entrySet()) {
            if (selected == null || entry.getKey().equalsIgnoreCase(selected)) {
                views.add(LocationView.of(entry.getKey(), entry.getValue()));
            }
        }

        model.addAttribute("pageTitle", selected != null ? selected : "Lagerorte");
        model.addAttribute("selected", selected);
        model.addAttribute("counts", counts);
        model.addAttribute("views", views);
        return "locations";
    }
}
