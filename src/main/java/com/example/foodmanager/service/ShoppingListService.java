package com.example.foodmanager.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.foodmanager.domain.Item;
import com.example.foodmanager.domain.ShoppingEntry;
import com.example.foodmanager.repository.ShoppingEntryRepository;

@Service
@Transactional
public class ShoppingListService {

    /** Vorschlag aus dem Mindestbestand: wie viel fehlt bis zum Mindestbestand (in der Einheit des Items). */
    public record Suggestion(Item item, int missing) {

        public String missingLabel() {
            return item.getUnit().format(missing);
        }
    }

    private final ShoppingEntryRepository repository;
    private final ItemService itemService;
    private final ApplicationEventPublisher events;

    public ShoppingListService(ShoppingEntryRepository repository, ItemService itemService, ApplicationEventPublisher events) {
        this.repository = repository;
        this.itemService = itemService;
        this.events = events;
    }

    private void changed() {
        events.publishEvent(new DataChangedEvent(DataChangedEvent.SHOPPING));
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
        changed();
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

    /**
     * Setzt den Eintrag auf erledigt oder offen. Bewusst kein Umschalten: tippen zwei Leute gleichzeitig
     * auf denselben Eintrag, bleibt er abgehakt, statt sich zurückzudrehen.
     */
    public void setDone(Long id, boolean done) {
        repository.findById(id).ifPresent(entry -> entry.setDone(done));
        changed();
    }

    public void delete(Long id) {
        repository.findById(id).ifPresent(repository::delete);
        changed();
    }

    public void clearDone() {
        repository.deleteAll(repository.findByDoneTrue());
        changed();
    }

    /** Vorschlag abhaken: Bestand auf den Mindestbestand auffüllen. */
    public Item markBought(Long itemId) {
        changed();
        return itemService.restockToMinimum(itemId);
    }
}
