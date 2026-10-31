package com.example.foodmanager.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.foodmanager.domain.Item;
import com.example.foodmanager.service.ItemService;

import jakarta.servlet.http.HttpSession;

/**
 * Schnelle Mengenänderung: "Verbraucht" und "+" um einen Schritt der Einheit, ohne das Formular zu öffnen.
 * Dazu Chargen (mehrere Ablaufdaten) und "Rückgängig".
 */
@Controller
public class ItemQuickActionController {

    private final ItemService itemService;
    private final UndoSupport undo;

    public ItemQuickActionController(ItemService itemService, UndoSupport undo) {
        this.itemService = itemService;
        this.undo = undo;
    }

    /** delta: Schritte (+/-). expiry (optional): Ablaufdatum der neuen Ware, legt bei Bedarf eine eigene Charge an. */
    @PostMapping("/items/{id}/adjust")
    public String adjust(
            @PathVariable Long id,
            @RequestParam int delta,
            @RequestParam(required = false) String expiry,
            @RequestParam(defaultValue = "/items") String next,
            HttpSession session,
            RedirectAttributes redirect
    ) {
        int safeDelta = Math.max(-100, Math.min(100, delta));
        ItemService.StockChange change = itemService.addStock(id, safeDelta, parseDate(expiry));
        Item item = change.item();

        String verb = safeDelta < 0 ? "verbraucht" : "aufgestockt";
        redirect.addFlashAttribute("successMessage",
                item.getName() + ": " + verb + ", jetzt " + item.getQuantityLabel() + ".");
        undo.offer(session, redirect, item.getId(), item.getVersion(), change.before());

        return "redirect:" + UndoSupport.safeTarget(next, "/items");
    }

    /** Von einer bestimmten Charge verbrauchen. steps = 0 entfernt die ganze Charge (z. B. weggeworfen). */
    @PostMapping("/items/{id}/batches/{batchId}/consume")
    public String consumeBatch(
            @PathVariable Long id,
            @PathVariable Long batchId,
            @RequestParam(defaultValue = "1") int steps,
            HttpSession session,
            RedirectAttributes redirect
    ) {
        ItemService.StockChange change = itemService.consumeBatch(id, batchId, Math.max(0, Math.min(100, steps)));
        Item item = change.item();

        redirect.addFlashAttribute("successMessage",
                item.getName() + ": " + (steps == 0 ? "Charge entfernt" : "verbraucht") + ", jetzt " + item.getQuantityLabel() + ".");
        undo.offer(session, redirect, item.getId(), item.getVersion(), change.before());

        return "redirect:/items/" + id;
    }

    /** Letzte eigene Änderung zurücknehmen (Mengenänderung oder Löschen). */
    @PostMapping("/undo")
    public String undo(
            @RequestParam String token,
            @RequestParam(defaultValue = "/items") String next,
            HttpSession session,
            RedirectAttributes redirect
    ) {
        var entry = undo.take(session, token);
        if (entry.isEmpty()) {
            redirect.addFlashAttribute("infoMessage", "Rückgängig ist nicht mehr möglich (zu lange her).");
            return "redirect:" + UndoSupport.safeTarget(next, "/items");
        }

        var e = entry.get();
        if (e.itemId() == null) {
            Item restored = itemService.restoreDeleted(e.before());
            redirect.addFlashAttribute("successMessage", restored.getName() + " ist wieder da.");
        } else if (itemService.undoChange(e.itemId(), e.versionAfter(), e.before())) {
            redirect.addFlashAttribute("successMessage", e.before().name() + ": Änderung rückgängig gemacht.");
        } else {
            redirect.addFlashAttribute("infoMessage",
                    e.before().name() + " wurde inzwischen von jemand anderem geändert. Rückgängig wurde nicht ausgeführt.");
        }
        return "redirect:" + UndoSupport.safeTarget(next, "/items");
    }

    private static LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
