package com.example.foodmanager.web;

import java.time.LocalDate;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.foodmanager.service.ItemService;
import com.example.foodmanager.service.LiveUpdateService;
import com.example.foodmanager.service.ProductLookupService;
import com.example.foodmanager.service.SeriesScanService;
import com.example.foodmanager.service.SeriesScanService.ParsedCode;

@Controller
public class ScanController {

    private final ProductLookupService productLookup;
    private final ItemService itemService;
    private final SeriesScanService seriesScanService;
    private final LiveUpdateService liveUpdates;

    public ScanController(
            ProductLookupService productLookup,
            ItemService itemService,
            SeriesScanService seriesScanService,
            LiveUpdateService liveUpdates
    ) {
        this.productLookup = productLookup;
        this.itemService = itemService;
        this.seriesScanService = seriesScanService;
        this.liveUpdates = liveUpdates;
    }

    @GetMapping("/scan")
    public String scan(Model model) {
        model.addAttribute("pageTitle", "Barcode scannen");
        model.addAttribute("pendingCount", seriesScanService.countPending());
        return "scan/scan";
    }

    @GetMapping("/scan/result")
    public String scanResult(@RequestParam String barcode, Model model) {

        // GS1-Code (DataMatrix/QR) enthält GTIN und evtl. Ablaufdatum, sonst normaler Barcode
        ParsedCode code = ParsedCode.of(barcode);
        String cleaned = code.barcode();
        LocalDate expiryDate = code.expiryDate();
        model.addAttribute("expiryDate", expiryDate);

        if (cleaned.isEmpty()) {
            model.addAttribute("lookup", BarcodeLookupResult.notFound(barcode,
                    "Ungültiger Barcode – konnte nicht gelesen werden."));
            model.addAttribute("itemForm", new ItemForm());
            return "scan/result";
        }

        // Erst eigener Speicher, dann OpenFoodFacts / Universal-Lookup
        BarcodeLookupResult result = productLookup.lookup(cleaned);

        // ItemForm füllen
        ItemForm form = new ItemForm();
        form.setBarcode(result.barcode());
        form.setName(result.found() ? result.name() : "Unbekanntes Produkt");
        form.setBrand(result.brand() != null ? result.brand() : "");
        form.setExternalImageUrl(result.imageUrl() != null ? result.imageUrl() : "");
        form.setExpiryDate(expiryDate);

        model.addAttribute("lookup", result.found() ? result
                : new BarcodeLookupResult(false, cleaned, "Kein Produkt gefunden", "", "", ""));
        model.addAttribute("itemForm", form);

        // Gibt es diesen Barcode schon im Inventar? Dann Menge erhöhen statt doppelt anlegen.
        itemService.findByBarcode(cleaned).ifPresent(item -> model.addAttribute("existing", item));

        return "scan/result";
    }

    // ------------------------------------------------------------
    // Serien-Scan beim Einräumen
    // ------------------------------------------------------------

    /** Ein Code aus dem Serien-Scan: bekannt = +1, unbekannt = auf die Einräum-Liste. Antwort als JSON. */
    @PostMapping("/scan/series")
    @ResponseBody
    public SeriesScanService.Outcome seriesScan(@RequestParam String code) {
        return seriesScanService.scan(code);
    }

    @GetMapping("/scan/inbox")
    public String inbox(Model model) {
        model.addAttribute("pageTitle", "Noch einzuräumen");
        model.addAttribute("pending", seriesScanService.findPending());
        return "scan/inbox";
    }

    @PostMapping("/scan/inbox/{id}/delete")
    public String removePending(@PathVariable Long id) {
        seriesScanService.removePending(id);
        return "redirect:/scan/inbox";
    }

    // ------------------------------------------------------------
    // Live-Aktualisierung (Einkaufsliste, Einräum-Liste)
    // ------------------------------------------------------------

    @GetMapping(path = "/live", produces = "text/event-stream")
    @ResponseBody
    public SseEmitter live() {
        return liveUpdates.subscribe();
    }
}
