package com.example.foodmanager.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.foodmanager.service.BarcodeLookupService;
import com.example.foodmanager.service.ItemService;
import com.example.foodmanager.service.UniversalBarcodeLookupService;

@Controller
public class ScanController {

    private final BarcodeLookupService foodLookup;
    private final UniversalBarcodeLookupService universalLookup;
    private final ItemService itemService;

    public ScanController(
            BarcodeLookupService foodLookup,
            UniversalBarcodeLookupService universalLookup,
            ItemService itemService
    ) {
        this.foodLookup = foodLookup;
        this.universalLookup = universalLookup;
        this.itemService = itemService;
    }

    @GetMapping("/scan")
    public String scan(Model model) {
        model.addAttribute("pageTitle", "Barcode scannen");
        return "scan/scan";
    }

    @GetMapping("/scan/result")
    public String scanResult(@RequestParam String barcode, Model model) {

        // Barcode bereinigen
        String cleaned = barcode.replaceAll("[^0-9]", "");

        if (cleaned.isEmpty()) {
            BarcodeLookupResult errorResult = new BarcodeLookupResult(
                    false,
                    barcode,
                    "Ungültiger Barcode – konnte nicht gelesen werden.",
                    "",
                    "",
                    ""
            );

            model.addAttribute("lookup", errorResult);
            model.addAttribute("itemForm", new ItemForm());
            return "scan/result";
        }

        // Lebensmittel-Lookup
        BarcodeLookupResult foodResult = safeLookup(foodLookup, cleaned);

        BarcodeLookupResult result;

        if (foodResult.found()) {
            result = foodResult;
        } else {
            // Universal-Lookup
            BarcodeLookupResult universalResult = safeLookup(universalLookup, cleaned);

            if (universalResult.found()) {
                result = universalResult;
            } else {
                // Fallback
                result = new BarcodeLookupResult(
                        false,
                        cleaned,
                        "Kein Produkt gefunden",
                        "",
                        "",
                        ""
                );
            }
        }

        // ItemForm füllen
        ItemForm form = new ItemForm();
        form.setBarcode(result.barcode());
        form.setName(result.name() != null ? result.name() : "Unbekanntes Produkt");
        form.setBrand(result.brand() != null ? result.brand() : "");
        form.setExternalImageUrl(result.imageUrl() != null ? result.imageUrl() : "");

        model.addAttribute("lookup", result);
        model.addAttribute("itemForm", form);

        // Gibt es diesen Barcode schon im Inventar? Dann Menge erhöhen statt doppelt anlegen.
        itemService.findByBarcode(cleaned).ifPresent(item -> model.addAttribute("existing", item));

        return "scan/result";
    }

    private BarcodeLookupResult safeLookup(Object service, String barcode) {
        try {
            if (service instanceof BarcodeLookupService lookupService) {
                return lookupService.lookup(barcode);
            }
            if (service instanceof UniversalBarcodeLookupService lookupService) {
                return lookupService.lookup(barcode);
            }
        } catch (Exception e) {
            return new BarcodeLookupResult(
                    false,
                    barcode,
                    "Fehler beim Abrufen der Produktdaten",
                    "",
                    "",
                    ""
            );
        }

        return new BarcodeLookupResult(
                false,
                barcode,
                "Unbekannter Fehler",
                "",
                "",
                ""
        );
    }
}
