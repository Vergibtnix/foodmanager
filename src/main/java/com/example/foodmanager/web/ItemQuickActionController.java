package com.example.foodmanager.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.foodmanager.domain.Item;
import com.example.foodmanager.service.ItemService;

/** Schnelle Mengenänderung: "Verbraucht" (-1) und "+1", ohne das Formular zu öffnen. */
@Controller
public class ItemQuickActionController {

    private final ItemService itemService;

    public ItemQuickActionController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping("/items/{id}/adjust")
    public String adjust(
            @PathVariable Long id,
            @RequestParam int delta,
            @RequestParam(defaultValue = "/items") String next,
            RedirectAttributes redirect
    ) {
        int safeDelta = Math.max(-100, Math.min(100, delta));
        Item item = itemService.adjustQuantity(id, safeDelta);

        String verb = safeDelta < 0 ? "verbraucht" : "aufgestockt";
        redirect.addFlashAttribute("successMessage",
                item.getName() + ": " + verb + ", jetzt " + ItemService.quantityOf(item) + " Stück.");

        return "redirect:" + safeTarget(next);
    }

    /** Nur Pfade innerhalb der App zulassen, keine fremden Adressen. */
    private String safeTarget(String next) {
        if (next != null && next.startsWith("/") && !next.startsWith("//") && !next.contains("\\")) {
            return next;
        }
        return "/items";
    }
}
