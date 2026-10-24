package com.example.foodmanager.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.foodmanager.domain.Item;
import com.example.foodmanager.domain.ShoppingEntry;
import com.example.foodmanager.repository.ShoppingEntryRepository;

@Service
@Transactional
public class ShoppingListService {

    /** Vorschlag aus dem Mindestbestand: wie viele Stück fehlen bis zum Mindestbestand. */
    public record Suggestion(Item item, int missing) {
    }

    private final ShoppingEntryRepository repository;
    private final ItemService itemService;

    public ShoppingListService(ShoppingEntryRepository repository, ItemService itemService) {
        this.repository = repository;
        this.itemService = itemService;
    }

    @Transactional(readOnly = true)
    public List<ShoppingEntry> findEntries() {
        return repository.findAllByOrderByDoneAscCreatedAtAsc();
    }

    @Transactional(readOnly = true)
    public List<Suggestion> findSuggestions() {
        return itemService.findLowStock().stream()
                .map(item -> new Suggestion(item, item.getMinQuantity() - ItemService.quantityOf(item)))
                .toList();
    }

    /** Legt einen Eintrag an. Gibt false zurück, wenn er leer oder schon offen vorhanden ist. */
    public boolean add(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            return false;
        }
        if (clean.length() > 200) {
            clean = clean.substring(0, 200);
        }

        final String candidate = clean;
        boolean exists = repository.findAll().stream()
                .anyMatch(e -> !e.isDone() && e.getName().equalsIgnoreCase(candidate));
        if (exists) {
            return false;
        }

        ShoppingEntry entry = new ShoppingEntry();
        entry.setName(clean);
        repository.save(entry);
        return true;
    }

    /** Fügt mehrere Einträge hinzu (zum Beispiel fehlende Rezept-Zutaten). Gibt die Anzahl neuer Einträge zurück. */
    public int addAll(List<String> names) {
        int added = 0;
        for (String name : names) {
            if (add(name)) {
                added++;
            }
        }
        return added;
    }

    public void toggle(Long id) {
        repository.findById(id).ifPresent(entry -> entry.setDone(!entry.isDone()));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public void clearDone() {
        repository.deleteAll(repository.findByDoneTrue());
    }

    /** Vorschlag abhaken: Bestand auf den Mindestbestand auffüllen. */
    public Item markBought(Long itemId) {
        return itemService.restockToMinimum(itemId);
    }
}