package com.example.foodmanager.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.foodmanager.domain.Item;
import com.example.foodmanager.domain.PendingScan;
import com.example.foodmanager.repository.PendingScanRepository;
import com.example.foodmanager.web.BarcodeLookupResult;

/**
 * Serien-Scan beim Einräumen: Bekannte Produkte bekommen sofort +1 (mit Ablaufdatum aus dem Code, falls da),
 * unbekannte landen in der Liste "Noch einzuräumen" und werden später in einem Rutsch angelegt.
 */
@Service
public class SeriesScanService {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	/** Ergebnis eines Scans für die Anzeige auf dem Handy. status: "added", "pending" oder "error". */
	public record Outcome(String status, String message, Long itemId, Long pendingId, long pendingCount) {
	}

	/** Barcode und Ablaufdatum aus dem Roh-Inhalt eines Codes. */
	public record ParsedCode(String barcode, LocalDate expiryDate) {

		public static ParsedCode of(String raw) {
			if (raw == null) {
				return new ParsedCode("", null);
			}
			return Gs1Parser.parse(raw)
					.map(gs1 -> new ParsedCode(gs1.gtin(), gs1.expiryDate()))
					.orElseGet(() -> new ParsedCode(raw.replaceAll("[^0-9]", ""), null));
		}
	}

	private final ItemService itemService;
	private final ProductLookupService productLookup;
	private final PendingScanRepository pendingRepository;
	private final ApplicationEventPublisher events;

	public SeriesScanService(
			ItemService itemService,
			ProductLookupService productLookup,
			PendingScanRepository pendingRepository,
			ApplicationEventPublisher events
	) {
		this.itemService = itemService;
		this.productLookup = productLookup;
		this.pendingRepository = pendingRepository;
		this.events = events;
	}

	public Outcome scan(String rawCode) {
		ParsedCode code = ParsedCode.of(rawCode);
		if (code.barcode().isEmpty() || code.barcode().length() > 64) {
			return new Outcome("error", "Kein gültiger Barcode.", null, null, countPending());
		}

		Optional<Item> existing = itemService.findByBarcode(code.barcode());
		if (existing.isPresent()) {
			Item item = itemService.addStock(existing.get().getId(), 1, code.expiryDate()).item();
			String date = code.expiryDate() != null ? " (bis " + DATE.format(code.expiryDate()) + ")" : "";
			return new Outcome("added", item.getName() + ": +" + item.getStepLabel() + date + ", jetzt " + item.getQuantityLabel() + ".",
					item.getId(), null, countPending());
		}

		// Produktdaten außerhalb der Transaktion holen (kann ein paar Sekunden dauern)
		BarcodeLookupResult product = productLookup.lookup(code.barcode());
		PendingScan pending = addPending(code, product);
		String name = pending.getName() != null ? pending.getName() : "Unbekanntes Produkt (" + code.barcode() + ")";
		String times = pending.getCount() > 1 ? " (" + pending.getCount() + "×)" : "";
		return new Outcome("pending", name + times + ": neu, kommt auf die Liste „Noch einzuräumen“.", null, pending.getId(),
				countPending());
	}

	private PendingScan addPending(ParsedCode code, BarcodeLookupResult product) {
		PendingScan pending = pendingRepository.findFirstByBarcode(code.barcode()).orElse(null);
		if (pending != null) {
			pending.setCount(pending.getCount() + 1);
			if (pending.getExpiryDate() == null) {
				pending.setExpiryDate(code.expiryDate());
			}
		} else {
			pending = new PendingScan();
			pending.setBarcode(code.barcode());
			pending.setExpiryDate(code.expiryDate());
			if (product.found()) {
				pending.setName(blankToNull(product.name()));
				pending.setBrand(blankToNull(product.brand()));
				pending.setImageUrl(blankToNull(product.imageUrl()));
			}
		}
		pending = pendingRepository.save(pending);
		events.publishEvent(new DataChangedEvent(DataChangedEvent.PENDING));
		return pending;
	}

	@Transactional(readOnly = true)
	public List<PendingScan> findPending() {
		return pendingRepository.findAllByOrderByCreatedAtAsc();
	}

	@Transactional(readOnly = true)
	public long countPending() {
		return pendingRepository.count();
	}

	@Transactional(readOnly = true)
	public Optional<PendingScan> findPending(Long id) {
		return pendingRepository.findById(id);
	}

	@Transactional
	public void removePending(Long id) {
		if (id == null) {
			return;
		}
		pendingRepository.findById(id).ifPresent(pendingRepository::delete);
		events.publishEvent(new DataChangedEvent(DataChangedEvent.PENDING));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
